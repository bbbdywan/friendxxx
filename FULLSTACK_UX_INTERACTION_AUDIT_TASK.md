# 全站前后端 UX、交互逻辑与 AI 聊天修复任务书

> 日期：2026-08-12  
> 前端：`F:\baib\new-project-name`  
> 后端：`F:\baib\Java_Backend_Universal_Template-main\friendxxx`  
> 优先级：先修 P0 功能错误，再做 P1 交互一致性，最后处理 P2 视觉体验。  
> 本文基于源码审查、自动化测试和已部署服务器真实接口验证生成。

---

## 1. 验证结论

### 1.1 已验证事实

- 管理员账号可以真实登录，服务端返回 `userRole=1`。
- 管理列表当前有角色数据，角色 1 的 active version 和 draft base version 均为 1。
- 后端空角色创建请求正确返回 HTTP 400、`参数校验失败` 和六个字段的 `fieldErrors`。
- 真实连续两轮聊天中，第二轮能正确回答第一轮问过“在干嘛”，说明会话上下文多轮本身有效。
- 真实第一轮多气泡 SSE 错误：出现多个不配对的 `message_start`，正文泄露 `<message>`/`</message`，同一句被重复发送。
- 前端 26 个测试通过，生产构建成功。
- 本次定向后端测试 69 个通过、0 失败、2 skipped，`BUILD SUCCESS`。

### 1.2 为什么“测试全绿”但实际不能用

当前测试主要验证解析器最终返回的完整消息，没有验证：

- DeepSeek 每个真实 chunk 输入后，后端发出的完整 SSE 事件序列。
- `message_start/message_delta/message_end` 是否严格配对。
- 协议标签是否泄露。
- 完整消息是否被发送两遍。
- Spring 单例解析器同时处理两个会话时是否相互污染。
- 新建角色后同一个编辑器实例立即发布的组件状态变化。
- Vant Dialog/Popup 内错误提示是否可见。

因此旧报告中的“全部完成”不等于真实产品链路完成。

---

## 2. P0：AI 多气泡协议实际损坏

### 2.1 真实复现结果

第一轮“在干嘛”的真实 SSE 出现：

- 4 次 `message_start`。
- 只有 2 次 `message_end`。
- 正文包含 `<message>` 和残缺的 `</message`。
- “刚在发呆呢，被你抓包了～”等正文重复出现。

第二轮能够理解上文，因此需要区分：

- “上下文多轮对话”是正常的。
- “一轮回复拆成多个拟人气泡”的实现是错误的。

### 2.2 确定根因一：把带状态的解析器注册成 Spring 单例

`IncrementalMessageParser` 标记为 `@Component`，内部却持有：

```java
private final StringBuilder buffer;
private boolean sawAnyTag;
```

`AiChatOrchestrator` 注入的是同一个单例实例。不同用户、不同会话、正式聊天与预览会共同读写同一 buffer，存在严重串流和数据污染风险。

必须改为：

- `IncrementalMessageParser` 是无状态 factory，或普通类。
- 每次 `sendMessage()` 创建独立 `ParserSession`。
- Preview 每次请求同样创建独立 session。
- 禁止在 singleton service 内保存请求级可变状态。

### 2.3 确定根因二：未闭合阶段直接把协议缓冲发给用户

`consumeDelta()` 在消息未闭合时调用 `parser.current()`，这个值包含：

- `<message>` 开标签。
- 正文。
- 可能残缺的 `</message`。

随后代码把它作为 `message_delta` 发给前端。等闭合标签到达后，又调用 `emitOneMessage(msg)` 把完整正文重新发送一次，因此必然标签泄露和重复。

### 2.4 确定根因三：pending row 与完整 row 重复创建

收到未闭合内容时已经创建一条 generating Assistant 行；闭合后 `emitOneMessage()` 又无条件创建新行，而不是完成已有 pending row，导致：

- 一个逻辑气泡对应两条数据库记录。
- `message_start` 数量大于 `message_end`。
- 前端出现空气泡、重复气泡或永远 generating 的气泡。

### 2.5 确定根因四：4 条上限按单次 feed 计算

解析器的 `out.size() < MAX_MESSAGES` 只限制一次 `feed()` 返回的数量。消息分散在多个 chunk 时，每次 out 都从 0 开始，整轮可以超过 4 条。

### 2.6 正确重构方案

实现请求级 `MessageStreamParserSession`，对外返回明确的解析事件，而不是暴露 raw buffer：

```java
sealed interface ParseEvent {}
record MessageStarted(int index) implements ParseEvent {}
record ContentDelta(int index, String safeText) implements ParseEvent {}
record MessageCompleted(int index) implements ParseEvent {}
```

状态机要求：

1. 只有识别完整 `<message>` 开标签后才发 `message_start`。
2. 只把已确认不是标签前缀的正文字符作为 `message_delta`。
3. 对 `</message>` 保留最长可能前缀，确认完整后发 `message_end`，绝不把标签发出。
4. 一条消息只创建一行 DB；开始时 insert generating，闭合时 update completed。
5. 整轮累计计数，硬限制最多 4 条。
6. 无标签输出安全降级为一条流式消息。
7. 畸形标签降级时不得重复、不得丢字、不得泄露标签。
8. finish 后必须满足：每个 start 恰好有一个 end，或在取消/错误时有明确 partial/failed end。
9. Parser session 必须请求隔离，可并发执行。

### 2.7 可选的更稳方案

如果 XML 流式协议仍不稳定，可考虑：

- DeepSeek 一次生成完整 JSON：`{"messages":[...]}`，后端完整解析后以模拟打字逐气泡推送。优点是稳定；缺点是首气泡延迟更高。
- JSON Lines/Record Separator 增量协议，但仍需正确处理分隔符跨 chunk。
- 先做 50～100 次真实模型协议遵循率评测，再选协议。

“最快看见第一个 token”和“协议绝对稳定”需要权衡。当前产品更应优先保证不重复、不泄露标签和历史一致。

### 2.8 必须新增的测试

- 使用本次真实 chunk 轨迹做回归测试。
- 开标签逐字符切分、闭标签逐字符切分。
- 一个 chunk 多消息、一个消息多 chunk。
- start/end 数量严格相等。
- 任何 SSE data 不包含 `<message` 或 `</message`。
- 拼接所有 message_delta 后与模型逻辑正文完全一致，既不重复也不丢字。
- 两个会话并发交错 feed，内容互不污染。
- 正式聊天与预览并发互不污染。
- 数据库每个可见气泡恰好一行，无 generating 残留。
- 整轮最多 4 条，跨 feed 也生效。
- 取消发生在开标签、正文、闭标签和两气泡之间。

---

## 3. P0：新建角色保存后无法发布

### 3.1 确定根因

新建角色前：

```js
baseVersionNo.value = 0
```

后端创建成功后已经生成 published v1 和 baseVersionNo=1 的 draft。父组件 `onCreated(id)` 只执行：

```js
editingId.value = id
isNew.value = false
```

子组件没有重新执行 `loadDetail()`，因此本地仍保留 `baseVersionNo=0`。随后发布发送：

```json
{
  "expectedVersionNo": 0
}
```

而后端当前版本是 1，必然返回 HTTP 409。

### 3.2 同时存在的发布契约错误

前端把 `versionId` 永久硬编码为 1：

```js
versionId: 1
```

后端 DTO 又要求 `versionId` 必填，但 `AiCharacterVersionService.publish()` 完全不使用它。字段注释称它是版本 ID，服务注释却称草稿对应 ID，契约互相矛盾。

必须二选一：

- 推荐：发布使用 `draftId + expectedActiveVersionNo`，后端校验 draft 确属角色且未过期。
- 或删除无效 `versionId`，只保留明确命名的乐观锁字段。

禁止继续发送硬编码 1。

### 3.3 前端修复

新建成功后必须执行完整状态转换：

1. 后端创建响应直接返回：`characterId/draftId/activeVersionNo/draftBaseVersionNo`。
2. 子组件更新 characterId，并立即 `await loadDetail()`。
3. `baseVersionNo` 更新为服务端 1。
4. 将 `isNew` 状态切为编辑状态。
5. 发布按钮只在“已有 characterId、草稿已保存、没有未保存修改、未提交中”时启用。
6. 新建成功提示应明确：“角色已创建为草稿/默认停用，下一步可预览或发布”。

### 3.4 后端修复

- 创建、保存和发布响应返回明确的编辑状态 DTO，不返回不够用的列表 VO。
- 发布事务对角色行加锁或使用真正的 CAS/乐观锁。
- `(character_id, version_no)` 增加唯一约束。
- 发布校验 draftId、baseVersionNo、characterId 一致。
- 发布成功后更新草稿 base version，或明确创建新草稿；不能让下次保存立即冲突。
- 增加“创建后立即发布”真实数据库集成测试。

---

## 4. P0：发布弹窗与错误提示交互错误

### 4.1 Vant Dialog before-close 使用错误

当前：

```vue
<van-dialog :before-close="doPublish">
```

Vant 会在 confirm 和 cancel 时都调用 before-close，并传入 action。`doPublish()` 没有接收/判断 action，因此点击取消也可能执行发布请求。

正确处理：

```js
async function beforePublishClose(action) {
  if (action === 'cancel') return true
  return await submitPublish()
}
```

更推荐不用 before-close 承载核心业务：Dialog 内自定义 footer，确认按钮显式绑定 submit，loading 时禁用，成功才关闭。

### 4.2 关键错误不应只靠 Toast

角色创建/发布属于高价值操作，错误只显示 2 秒 Toast 不够，尤其页面有 Popup + Dialog 多层 overlay。用户反馈的“空白小弹窗”必须通过以下方式消除：

- 表单字段错误：字段下方持久显示，并滚动到第一处。
- 发布冲突：Dialog 内显示红色错误块和“刷新数据”按钮，不关闭 Dialog。
- 未知错误：Dialog 内显示 message + traceId + 复制按钮。
- Toast 只用于成功或轻量提示。
- 统一 Toast z-index 高于所有 Popup/Dialog；检查全局主题而不是只在 LoginPage 局部覆盖 `.van-toast`。
- 消息为空时禁止调用 showToast，fallback 必须非空。

### 4.3 字段错误读取方式错误

Axios 拦截器已经把原始 Axios Error 转换成 `ApiError`，字段错误位于：

```js
error.fieldErrors
```

编辑器仍然读取：

```js
e.response.data.data.fields
```

因此服务端虽然正确返回 fieldErrors，前端仍无法显示。应统一使用已有的：

```js
getApiFieldErrors(error)
```

不要让页面再次理解 Axios 原始结构。

---

## 5. P0：草稿预览当前会出现空白气泡

后端 Preview 已发送 `message_start/message_delta/message_end`，但 `AiCharacterEditor.runPreview()` 仍只注册：

- `onDelta`
- `onDone`
- `onError`

它没有注册多消息回调，所以新的 `message_delta` 不会写入 `aiMsg.content`，表现就是空白 AI 气泡。

必须：

- 预览与正式聊天复用同一个 `useAiMessageStream()` composable/store。
- 同样处理 message_start/delta/end、多气泡分组、取消和错误。
- 新角色尚未保存、characterId 为空时禁用预览，而不是请求 `/null/preview`。
- 预览失败显示明确错误，不允许空 catch。
- 预览输入期间禁用重复发送，支持停止生成。

---

## 6. P1：AI 聊天页交互与状态管理

### 6.1 抽取统一流状态

当前正式聊天和预览各写一套流逻辑，已产生协议漂移。抽取：

```text
useAiMessageStream
├── startTurn
├── handleMessageStart
├── handleMessageDelta
├── handleMessageEnd
├── handleUsage
├── handleDone
├── handleError
└── abort
```

正式聊天提供持久化信息，预览提供临时上下文，但事件消费必须相同。

### 6.2 不要扫描所有历史消息修改流状态

`onDone/onError` 当前遍历 `messages.value` 中所有 `isStreaming` 消息。应只操作当前 turn 的 message map，避免旧异常状态被错误改为 completed/failed。

### 6.3 第二轮发送体验

- done 后明确释放本地发送锁。
- 若流没有 done 但网络自然关闭，显示“连接中断，可重试”，不能永久禁用输入。
- 输入框内容在网络错误时可恢复。
- 重试复用相同 clientMessageId，用户主动新发才生成新 ID。
- 第二轮发送前检查上一轮是否还有服务端 generating；必要时提供恢复/终止接口。

### 6.4 会话恢复仍有静默失败

`restoreSession()` 外层 catch 直接忽略；会话失效时用户只看到空白/选择器。应区分：

- 会话不存在：清理本地 session，提示“原会话已失效，请重新选择角色”。
- 网络失败：保留 session，显示重试状态。
- 权限/登录失败：走统一认证流程。

### 6.5 自动滚动

当前每个 delta 都强制 scrollBottom，会抢走用户正在阅读的历史。应：

- 仅当用户接近底部时自动滚动。
- 用户上滑后暂停自动滚动，并显示“回到底部”浮钮。
- 对 delta 更新节流到每帧/50ms，减少 Vue 重渲染。

### 6.6 输入区域

- 使用 textarea/van-field autosize，而不是单行 input。
- 移动端回车发送行为清晰；桌面 Shift+Enter 换行。
- 适配 `env(safe-area-inset-bottom)` 和软键盘。
- 发送、停止、重试状态用图标和文字明确表达。

---

## 7. P1：AI 人设管理界面重构

### 7.1 当前移动端头部拥挤

编辑器头部同时放标题、模板、保存、发布四项小按钮，窄屏容易挤压。建议：

- 顶部只保留返回、标题、更多菜单。
- 底部固定操作栏：保存草稿、预览、发布。
- “从模板创建”放进首次空状态或更多菜单。
- 底栏限制在编辑 Popup 内，不使用 `left:0;right:0` 覆盖整个应用。

### 7.2 明确状态模型

页面顶部必须持续显示：

- 已保存/有未保存修改。
- 草稿基于 vN。
- 当前线上 vN。
- 已启用/停用。
- 保存中/发布中。

现在 `hasDraft` 只表示存在草稿，但每个角色一直都有草稿，信息价值有限。应显示“草稿是否相对线上有变更”。

### 7.3 防止丢失编辑内容

- 任何字段变化设置 dirty。
- 关闭 Popup、返回、切角色前确认。
- 保存成功后更新初始快照和版本号。
- 版本冲突时先展示 diff，不能直接刷新覆盖用户输入。

### 7.4 示例对话

- negative 示例当前模板内容其实像正例，需重新定义 negative 的语义：是不希望模型说的话，还是负向场景的正确回复。字段命名必须清晰。
- 支持拖拽排序。
- 显示总数与上限。
- 每条错误定位到具体卡片。
- 删除需要撤销或轻量确认。

### 7.5 头像

- 不要求用户手填 URL，提供上传/选择默认头像。
- 上传中、失败、裁剪、预览状态明确。
- 头像 404 在管理列表和聊天页都统一回退。

---

## 8. P1：全站错误、Toast、Dialog 规范

### 8.1 建立 Feedback Service

全站封装：

- `notifySuccess(message)`：短 Toast。
- `notifyError(error, fallback)`：非阻塞错误 Toast。
- `showOperationError({title,error,retry})`：关键操作持久 Dialog/Panel。
- `showFieldErrors(formRef, errors)`：表单定位。
- `confirmDestructive(...)`：删除/退出/覆盖。

不要让 19 个页面各自决定不同的错误展示方式。

### 8.2 Toast 视觉层级

当前只有 LoginPage 局部覆盖 `.van-toast { z-index:9999 }`，其他页面的 Toast 可能与 Popup/Dialog 同层。应在全局样式统一：

- overlay。
- popup。
- dialog。
- toast。
- image preview。

定义层级 token，禁止页面随意写 100、1000、9999、10000。

### 8.3 catch 规范

允许静默的 catch 仅限“用户主动取消 Dialog”。其他 catch 必须：

- 显示错误；或
- 设置页面错误状态并提供重试；或
- 明确记录为非阻塞后台错误。

`InteractionsPage.vue` 的空 catch、AI restore/onMounted 静默失败需要清理。

### 8.4 列表页不要只 Toast

首页、发现、搜索、互动、聊天列表、资料页加载失败时，应在内容区域显示：

- 错误原因。
- 重试按钮。
- 保留已有缓存数据。

Toast 一闪而过后留下空白页面是不合理设计。

---

## 9. P1：全站加载、空状态和提交逻辑

为所有异步页面统一四态：

```text
idle/loading/success/error
```

列表额外区分 empty。要求：

- 首屏 loading 使用 skeleton，不只一个 spinner。
- 空列表说明为什么为空，并提供下一步动作。
- error 与 empty 不能混淆。
- 重复提交按钮必须 loading + disabled。
- 请求结束在 finally 恢复状态。
- 页面卸载取消请求，避免旧响应覆盖新页面。
- 搜索、筛选、分页使用 request sequence/AbortController 防竞态。

重点页面：

- HomePage：多个独立数据源不要一个失败拖垮整页。
- DiscoverPage：筛选弹层关闭后保留/恢复状态，删除和更新做乐观 UI 回滚。
- SearchPage：切 Tab 时取消旧查询，区分首次搜索、无结果和加载更多失败。
- AdminPage：长表单/时间范围校验就地显示，批量操作有结果摘要。
- NewProfilePage/PostPage：多文件上传显示每文件状态，部分成功可重试失败项。
- ChatPage/ChatDetail：发送状态、重连状态、重复消息去重和失败重发统一。

---

## 10. P1：导航、权限与信息架构

- 路由 meta 增加 `requiresAdmin`，前端路由守卫提前阻止普通用户进入管理页面；后端 403 仍是最终防线。
- 管理入口不要只依赖首页一个隐藏按钮，可在个人设置中提供明确入口。
- AI 聊天应有会话列表/历史入口，而不是只依赖 localStorage 单一会话。
- 新开会话、切换角色、继续旧会话的区别明确。
- 删除图标目前实际含义是“新建会话”，应换成加号/新建图标，避免误导为删除历史。
- 所有返回操作统一使用路由策略，避免没有 history 时回退失效。

---

## 11. P2：视觉与移动端一致性

### 11.1 设计 token

统一颜色、圆角、阴影、间距、字号、层级、动效和安全区变量。当前大量页面自行定义近似但不一致的卡片与 fixed bar。

### 11.2 页面高度

- 避免无条件 `height:100%` 依赖不完整的父级高度链。
- 使用 `100dvh` 而不是移动端有地址栏问题的 `100vh`。
- 固定底栏加安全区和相应内容 padding。
- Popup 内 fixed 元素应相对容器设计，避免覆盖后台页面。

### 11.3 可访问性

- 图标按钮增加 aria-label。
- 点击区域至少 44×44px。
- 错误不仅依赖颜色。
- 文本对比度达标。
- 表单 label 与错误关联。
- 支持系统减少动画设置。

### 11.4 性能

- HomePage 主包/异步块较大，继续按功能拆分。
- 移除动态+静态重复引入 Pinia store 的构建警告。
- 长列表使用虚拟化或分页。
- 流式消息更新节流。
- 图片统一 lazy loading、尺寸占位和压缩参数。

---

## 12. 后端 API 与数据一致性优化

### 12.1 统一响应契约

- 成功码只保留一种，不再同时兼容 0/200。
- 业务 code 使用稳定机器码，HTTP status 表达协议语义。
- fieldErrors 固定使用一个字段名。
- 所有错误带 traceId。
- 登录账号密码错误应返回 400/401，而不是 HTTP 500。

### 12.2 幂等与状态

- AI turn 增加明确状态表或 turn 聚合，避免靠多行消息推断整轮状态。
- done 前确保所有 message 行终态完成。
- 重放验证 message_start/end 配对。
- 发布角色使用草稿版本/CAS，不接收无效字段。

### 12.3 可观测性

记录但不泄露隐私：

- traceId、conversationId、turnId、characterVersionId。
- 首 token、总耗时、气泡数、解析降级次数。
- 标签泄露保护计数。
- 生成失败阶段。
- 角色创建/保存/发布失败原因。

若检测到输出包含协议标签，服务端应阻断/清洗并记录指标，不能直接发给用户。

---

## 13. 测试体系升级

### 13.1 前端组件测试

至少增加：

- 新建成功后 baseVersionNo 从 0 更新到 1。
- 创建后立即发布成功。
- 发布 409 在 Dialog 内显示具体错误。
- cancel 不调用 publish。
- ApiError.fieldErrors 映射到字段。
- Toast 在 Popup/Dialog 上层且 message 非空。
- Preview 正确消费 message_* 多气泡事件。
- 新角色未保存时 Preview/Publish 禁用。
- AI 第二轮输入可发送。
- SSE start/end 不配对时前端安全失败，不留空白气泡。

### 13.2 后端集成测试

- 真实数据库创建→保存→发布→再次保存。
- Parser 请求隔离并发测试。
- 编排器完整事件序列测试，而非只测 parser 返回值。
- 标签泄露断言。
- DB 行数与可见气泡一一对应。
- 真实 DeepSeek 可选集成测试保存脱敏事件轨迹，作为回归 fixture。

### 13.3 E2E

使用 Playwright/Cypress 在预发布环境执行：

1. 管理员登录。
2. 从模板新建角色。
3. 保存。
4. 预览两轮。
5. 发布。
6. 启用。
7. 普通聊天选择新角色。
8. 连续问两轮。
9. 验证多个气泡无标签、无重复、刷新一致。
10. 回滚角色版本。

E2E 测试账号由环境变量注入，禁止写入仓库和报告。

---

## 14. 实施优先级与顺序

### P0，必须先完成

1. 重写多气泡 parser session 和 orchestrator 事件状态机。
2. 修复新建后 baseVersionNo/草稿状态刷新。
3. 删除硬编码 versionId，统一发布契约。
4. 修复 Dialog cancel/confirm 行为。
5. 使用 `getApiFieldErrors()` 正确显示字段错误。
6. 修复 Preview 的 message_* 消费和空 characterId。
7. 增加真实事件序列、组件发布流程测试。

### P1，核心体验

1. 抽取正式聊天/预览统一流 composable。
2. 建立全站 Feedback Service 和 z-index token。
3. 列表页错误态/重试、表单持久错误、dirty 防丢失。
4. AI 会话列表、切换角色、新建会话信息架构。
5. 全站 loading/empty/error 状态一致化。
6. 路由权限、导航和提交状态统一。

### P2，视觉和工程质量

1. 设计 token、移动端安全区、可访问性。
2. 大页面拆分、长列表和图片性能。
3. 完整 E2E 与行为评测。

---

## 15. 最终验收标准

以下全部成立才算完成：

1. 新建角色保存后可以直接预览和发布，无需关闭重开页面。
2. 发布失败在 Dialog 内显示具体原因和 traceId，不出现空白小框。
3. 点击发布 Dialog 的取消绝不发送请求。
4. 后端字段错误准确显示在对应字段。
5. 真实“在干嘛”回复可以自然拆 1～4 个气泡。
6. SSE 中 start/end 严格配对，协议标签泄露率为 0，正文重复率为 0。
7. 两个并发聊天和预览不会互相串内容。
8. 连续第二轮理解上文并正常发送；输入不会永久锁住。
9. 刷新后多气泡和角色资料一致，无 generating 残留。
10. 关键页面加载失败有持久错误和重试，不只是一闪而过的 Toast。
11. 只有 401 清登录，其他错误保留登录并显示具体原因。
12. 前后端单测、组件测试、真实数据库集成测试、生产构建和预发布 E2E 全部通过。

---

## 16. 交付物

编码模型完成后必须提交：

- 修复后的前后端代码和数据库迁移（如需要）。
- Parser/Orchestrator 事件协议说明。
- 角色编辑状态机和发布接口契约。
- 全站反馈、加载、空状态和错误展示规范。
- 新增的单元、组件、集成和 E2E 测试。
- `FULLSTACK_UX_INTERACTION_AUDIT_REPORT.md`，必须包含：
  - 每个 P0 根因与修改文件。
  - 创建→保存→预览→发布真实证据。
  - 两轮聊天的完整事件类型统计。
  - 标签泄露/重复/空白气泡检查。
  - 测试数量、构建结果和未完成项。

不得仅凭“测试全绿”宣布完成，必须提供真实界面或 E2E 证据以及服务器 SSE 事件序列。


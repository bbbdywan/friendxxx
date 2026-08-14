# AI Chat 角色选择、新建角色与拟人化多消息联合优化任务书

> 日期：2026-08-12  
> 后端目录：`F:\baib\Java_Backend_Universal_Template-main\friendxxx`  
> 前端目录：`F:\baib\new-project-name`  
> 目标：修复角色选择展示、新建角色失败和聊天单气泡限制，并完成一轮前后端质量优化。本文是交给编码模型直接执行的任务书，不是概念建议。

---

## 1. 最终产品效果

完成后必须达到：

1. AI 助手进入角色选择页时，每个角色只显示圆形头像和昵称，不显示简介、身份 Prompt 或其他人设详情。
2. 头像正常加载；为空或加载失败时显示统一默认头像，不出现截图中的细长破图。
3. 管理员能够成功新建角色；缺少字段或数据库未迁移时，界面显示明确原因，而不是笼统的“保存失败”。
4. 用户发一条消息后，AI 可以像真人即时通讯一样连续发送 1～4 个独立气泡，而不是所有内容挤在一个长气泡里。
5. 多气泡的拆分由模型表达意图和后端协议共同决定，禁止按 Token、网络 chunk、每个标点或简单换行粗暴拆分。
6. 多气泡需要正确流式展示、逐条落库、刷新恢复、幂等重放、取消处理和历史分页。
7. 修改人设后仍可热发布，无需重新部署前后端。

---

## 2. 已确认的实际问题与根因

### P0-1：角色选择卡展示完整人设且没有头像字段

后端 `AiCharacterController.list()` 当前把：

```java
.description(c.getIdentityPrompt())
```

返回给普通角色列表。完整身份 Prompt 被当成公开简介，前端 `AiChatPage.vue` 又直接渲染：

```vue
<div class="role-desc">{{ c.description }}</div>
```

同时 `CharacterVO` 只有 `id/name/description`，没有 `avatarUrl`。因此前端取到的 `c.avatarUrl` 永远为空，只能依赖默认图；默认图不存在或加载失败时就会出现破图。

### P0-2：新建角色失败信息被吞掉

前端新建表单只预检查 `name` 和 `boundaryPrompt`，而后端还要求以下字段全部非空：

- `identityPrompt`
- `personalityPrompt`
- `speakingStylePrompt`
- `interactionRulesPrompt`
- `boundaryPrompt`

前端捕获异常后只显示“保存失败”，没有展示 `error.response.data.message`，导致真实的 Bean Validation、SQL 列不存在、JSON 或权限错误无法判断。

另外必须核查目标数据库是否已经实际执行 V6/V7。若尚未增加 `description/avatar_url/active_version_id/draft_id` 或未创建版本、草稿、审计表，`POST /admin/ai/characters` 必然失败。

### P0-3：现有协议只能表达一条 Assistant 消息

当前后端每轮只预创建一条 `AiMessage assistantMessage`，所有 DeepSeek `delta` 都追加进同一 `fullContent`，最后只更新这一行。前端同样只创建一个 `aiMsg` 并持续追加内容。因此现有设计无论 Prompt 怎么写，都只能形成一个消息气泡。

### P1：聊天恢复与分页缺陷

- `restoreSession()` 只调用 `listConversations(1, 1)`，然后从最多一条记录中寻找本地会话，容易恢复不到角色名称和头像。
- 首次加载历史消息后没有初始化 `cursor`，`loadOlder()` 以 `!cursor` 直接返回，历史上拉分页可能失效。
- `done.messageId` 没有写回临时 Assistant 消息，刷新前后的 ID 衔接和去重不完整。
- 本地只保存 `conversationId`，没有可靠恢复 `characterId`；会话 VO 也没有直接携带角色名称/头像。
- 请求错误大量被统一转成“创建失败/保存失败/加载失败”，缺少可诊断信息。

### P1：错误响应协议混用

业务接口成功使用 `Result`（成功码 200），全局异常使用 `BaseResponse`；`BusinessException(400/409)` 默认只写 JSON code，HTTP Status 可能仍为 200。前端一部分按 `res.code` 判断，一部分按 Axios HTTP 错误判断，行为不一致。

---

## 3. 第一阶段：修复角色选择 UI 与接口

### 3.1 后端公开角色 DTO

将 `CharacterVO` 改成最小公开数据：

```json
{
  "id": 1,
  "name": "小鹿",
  "avatarUrl": "https://..."
}
```

要求：

- 增加 `avatarUrl`。
- 普通 `/ai/characters` 不返回五段 Prompt、示例对话、版本信息和完整 description。
- `AiCharacterController` 从主记录读取 `name/avatarUrl`；如名称和头像允许随已发布版本变化，则统一通过角色版本服务读取 active 版本，不能返回草稿。
- 只返回 `enabled=1` 且存在可用已发布版本的角色。
- 返回顺序稳定，建议增加 `sort_order`；本次如不迁移，可先按 `id ASC`。

### 3.2 前端角色选择器

删除 `.role-desc`，卡片只保留：

- 56～64px 圆形头像。
- 居中或紧邻头像的昵称。
- 点击态/选中态和轻微按压反馈。
- 多角色时使用紧凑网格或横向头像列表；移动端建议 3～4 列头像网格，不再使用占满宽度的长卡片。

头像要求：

- `van-image` 使用 `fit="cover"`。
- 同时处理空 URL 和图片 `error` 事件，切换本地确定存在的默认头像。
- 默认资源放在前端 `public` 或由 import 管理，构建后必须验证 URL 存在。
- 昵称最多两行，超长省略；不允许简介撑高布局。

### 3.3 验收

- 一个角色和多个角色两种状态均检查。
- 头像 URL 正常、为空、404 三种情况均不出现破图。
- 网络响应中不再出现完整 `identityPrompt`。
- 截图宽度对应的移动端页面只展示头像和“小鹿”等昵称。

---

## 4. 第二阶段：修复新建角色

### 4.1 先复现并记录真实错误

编码模型必须用浏览器网络面板和后端日志实际调用一次：

```http
POST /admin/ai/characters
```

记录 HTTP 状态、响应 body、根异常和失败 SQL。禁止在没有证据时只修改前端 Toast 就声称修复。

### 4.2 数据库预检查

确认 V3～V7 的结构已部署，至少校验：

- `ai_character` 存在 `description/avatar_url/active_version_id/draft_id`。
- `ai_character_version`、`ai_character_draft`、`ai_character_audit` 存在。
- MySQL JSON 列可以接收前端示例对话。
- 数据库账号对这些表有 SELECT/INSERT/UPDATE 权限。

如环境未迁移，给出明确启动检查或管理页错误提示“数据库 AI 人设迁移未完成”，不要只返回系统内部错误。

### 4.3 前端表单校验

保存前检查所有后端必填字段，并在对应字段下显示错误：

- 名称。
- 身份设定。
- 性格设定。
- 语言风格。
- 互动规则。
- 安全边界。
- 每条示例的 `type/user/reply`。

要求：

- 第一处错误自动滚动并聚焦。
- 显示字段剩余字数和总字符预算。
- 新建角色提供“从模板创建”：预填安全底线以外的合理示例内容，避免面对五个空白大文本框。
- `exampleDialogues` 子项在后端 DTO 上加 `@Valid`，确保嵌套字段验证实际执行。
- 防止重复点击：提交期间按钮 loading + disabled。

### 4.4 错误透传

统一封装 `getApiErrorMessage(error, fallback)`：

1. 优先读取 `error.response?.data?.message`。
2. 其次读取正常 HTTP 200 但业务失败的 `res.message`。
3. 最后才使用 fallback。

至少区分：401、403、字段校验失败、409 版本冲突、数据库迁移缺失、网络超时和未知错误。

### 4.5 后端错误协议

建议统一 `Result`/`BaseResponse` 为一种结构，并通过 `ResponseEntity` 或 `@ResponseStatus` 返回真实 HTTP 状态：

- 参数校验：400。
- 未登录：401。
- 无管理员权限：403。
- 不存在：404。
- 乐观锁冲突：409。
- 限流：429。
- 未知服务端错误：500。

增加 `MethodArgumentNotValidException` 专项处理，返回字段错误列表；日志保留 traceId，但响应不泄露 SQL、密钥和完整 Prompt。

### 4.6 创建事务测试

增加集成测试，不只 Mock Mapper：

- 合法角色创建成功，主表、版本 1、草稿、审计四处一致。
- 任一步失败时事务整体回滚，不留半个角色。
- 五段 Prompt 任一为空时返回 400 和字段名。
- 未迁移数据库时给出可识别错误。
- 普通用户 403，管理员成功。

---

## 5. 第三阶段：拟人化多消息回复

## 5.1 产品规则

AI 每轮根据内容决定发几条消息：

- 简单回答通常 1 条。
- 闲聊、惊喜、情绪表达可自然拆成 2～3 条。
- 复杂但适合即时通讯的回应最多 4 条。
- 每条应是完整的表达单元，不能把一句话从中间切开。
- 禁止固定每次都拆多条，否则会显得刻意且增加费用。
- 高风险安全回复可保持单条完整输出。

示例：

```text
用户：在干嘛

AI 气泡 1：刚刚在发呆
AI 气泡 2：然后就被你抓到了～
AI 气泡 3：你呢，突然想起我啦？
```

## 5.2 不允许的实现

- 不按 DeepSeek 网络 `delta` 创建气泡，delta 只是传输分片。
- 不按每个句号、逗号或换行无脑拆分。
- 不让模型输出 JSON 数组后等整段完成才展示，否则失去流式体验。
- 不只在前端视觉拆分、数据库仍存一条且无法稳定恢复。
- 不通过连续调用 DeepSeek 生成每个气泡；一轮应以一次主模型调用完成，控制成本和上下文一致性。

## 5.3 推荐协议：流式消息分隔标记

让模型在一轮文本流中使用仅供协议解析的低碰撞边界，例如：

```text
<message>刚刚在发呆</message><message>然后就被你抓到了～</message>
```

但不能把原始标记直接显示或落库。实现一个跨 chunk 的增量解析状态机，把 DeepSeek 流转换为业务 SSE：

| SSE 类型 | data | 说明 |
|---|---|---|
| `start` | `turnId` | 本轮开始 |
| `message_start` | `messageId/index` | 新气泡开始 |
| `message_delta` | `messageId/index/content` | 当前气泡增量 |
| `message_end` | `messageId/index/status` | 当前气泡完成 |
| `usage` | Token 用量 | 整轮用量，只发一次 |
| `done` | `turnId/messageIds` | 整轮完成 |
| `error` | code/message | 整轮错误 |

兼容期可继续接受旧 `delta`，但新版前后端应优先使用 `message_*`。

解析器必须处理：

- `<message>`/`</message>` 被拆在多个 DeepSeek chunk 中。
- 一个 chunk 内包含多个完整消息。
- 模型遗漏开始标记、遗漏结束标记、输出普通文本或输出超过 4 条。
- 标记内正文包含换行和中文 UTF-8。
- 空消息、纯空白消息自动丢弃。
- 异常格式降级为单条气泡，禁止整轮丢失。

如果 DeepSeek 当前模型对 XML 标记稳定性不足，可改用 ASCII record separator 或 JSON Lines；选择前必须用 `ai-eval` 做至少 50 轮结构遵循率测试。最终以“可增量解析、可容错、不泄露标记”为准。

## 5.4 Prompt 调整

在 `PersonaPromptAssembler` 增加“消息节奏协议”层，但业务人设可以配置倾向参数：

- `multiMessageEnabled`。
- `maxMessagesPerTurn`，默认 3，硬上限 4。
- `messageSplitStyle`：`natural/compact/single`。
- `interMessageDelayMsMin/Max` 仅作为前端展示节奏配置，不要求服务端 sleep。

核心规则必须由平台固化，管理员 Prompt 不能要求无限拆分。Few-shot 同时加入单气泡和多气泡例子，让模型学会“该拆才拆”。

## 5.5 数据库模型

推荐新增一次迁移，例如 `V8__ai_multi_message_turn.sql`：

### `ai_message` 增加

- `turn_id VARCHAR(64)`：一轮用户请求及其多个 Assistant 消息的共同标识。
- `message_index INT NOT NULL DEFAULT 0`：同一轮内顺序。
- `character_version_id BIGINT NULL`：记录生成时使用的人设版本，便于复现。

索引与约束：

- `INDEX(conversation_id, turn_id, message_index)`。
- Assistant 消息的 `(turn_id, message_index)` 应唯一；需兼顾 User 消息，可采用角色列联合唯一或只对业务层约束。
- `reply_to_message_id` 对同轮多个 Assistant 行都指向同一个 User 消息。

不要把多个气泡保存成一个 JSON 字符串；每个可见气泡是一条 `ai_message`，这样历史、取消和失败状态才能准确表达。

## 5.6 后端编排状态机

改造 `AiChatOrchestrator`：

1. 保存 User 消息并生成 `turnId`。
2. 不再预创建唯一 Assistant 行；在解析到第一个非空 `message_start` 时创建第 0 条 Assistant 行，状态 `generating`。
3. `message_delta` 只更新内存缓冲和发 SSE，不要每 Token 写数据库。
4. `message_end` 时将该气泡一次更新为 `completed`，再开启下一条。
5. 整轮结束后发送一次 usage/done，并将 Token 用量记录在第一条或单独 turn 表；语义必须写入接口文档。
6. 取消时：已结束气泡保持 completed；当前有内容气泡标 partial；当前无内容气泡标 cancelled 或不落库。
7. 上游错误时：已完成气泡保留；当前气泡 failed/partial；整轮 error。
8. 后台摘要、记忆和关系任务应接收所有 Assistant 气泡按顺序拼接后的逻辑正文，但保留合理分隔，且只调度一次。

## 5.7 幂等重放

当前 `clientMessageId` 唯一约束仍用于识别重复 User 请求。重复请求时：

- 根据 User message ID 查询所有 `reply_to_message_id` 对应的 Assistant 行。
- 按 `message_index` 顺序重放每个 `message_start -> message_delta -> message_end`。
- 最后发送 done，携带所有 messageIds。
- 禁止只返回“最近一条 Assistant”。
- 重放不能再次触发 DeepSeek、记忆或关系后台任务。

## 5.8 前端渲染

前端以 `turnId + messageId/index` 管理流状态：

- 收到 `message_start` 创建一个新的 Assistant 气泡。
- 收到 `message_delta` 只追加到对应气泡。
- 收到 `message_end` 固化当前气泡。
- 多个气泡可以在服务端内容已经到达后按 150～450ms 的轻微随机视觉延迟依次出现，但不得阻塞网络读取，也不要延迟过长。
- 用户点击停止后保留已经完成和当前 partial 气泡。
- 切换会话/卸载页面清理 AbortController 和延时队列。
- 页面刷新后按数据库多行原样恢复。

视觉上连续 AI 气泡应组成一组：

- 同一轮只有第一条显示头像，或使用紧凑间距。
- 同组气泡间距 4～8px，不同轮 14～18px。
- 气泡宽度随内容，不应每条占满屏。
- 不显示技术状态词；partial 可用低调图标或“已中断”。

---

## 6. 第四阶段：本轮扫描发现的其他优化

### P0/P1 必修

1. 修复 `restoreSession()`：后端增加 `GET /ai/conversations/{id}`，返回会话及 `characterId/name/avatarUrl`；前端不再用 `listConversations(1,1)` 猜测。
2. 初始化历史游标：首屏历史加载后从最旧一条生成服务端定义的 nextCursor；更推荐后端响应 `{items,nextCursor,hasMore}`，不要由前端拼 `createTime,id`。
3. `done` 时用服务端 messageId 替换临时 ID；多气泡后按 message_end 分别替换。
4. 前端 `onError` 不应只改状态而完全静默，需要展示可理解的错误并提供重试。
5. 创建会话时保存 `characterId`，但角色名称头像应以服务端恢复结果为准。
6. 统一管理员角色常量，清理前端仍存在的 `userRole === 3` 与后端 `ADMIN_ROLE=1` 冲突。

### P1 建议

1. 为 AI 角色提供头像上传接口，复用现有 OSS 上传能力；管理台不应只要求手填 URL。
2. 头像 URL 做协议和域名校验，避免 `javascript:`、内网探测地址和不安全混合内容。
3. `PersonaPromptAssembler` 当前固化互动规则，却未拼接角色自己的 `interactionRulesPrompt` 与 `boundaryPrompt`；应在不可覆盖的平台安全底线之后，显式加入角色互动规则和角色附加边界。
4. 预览服务不要复制一套与正式聊天不同的 Prompt 拼装逻辑；应复用统一 assembler 和多消息解析器，否则“预览效果”和“上线效果”会漂移。
5. 当前 `AiPreviewService` 注入了 `PersonaPromptAssembler` 但实际自行拼接 Prompt，应消除这处死依赖/重复实现。
6. 对 Prompt、记忆和最近消息做统一 Token 预算，而不仅是字符截断，避免人设变长后挤掉最近对话。
7. 为角色列表、会话详情、历史消息和管理接口补 OpenAPI 示例与前端类型定义，减少字段漂移。

### P2 体验优化

1. 角色切换前若已有会话，给出“继续原会话/与新角色开始聊天”明确选择。
2. 聊天头部显示角色头像和昵称，不显示“生成中”技术标签；使用自然的输入中动画即可。
3. 输入区适配安全区、软键盘和移动端回车行为；Enter 发送、Shift+Enter 换行仅在桌面启用。
4. 自动滚动仅在用户位于底部时执行，用户上滑后显示“回到底部”按钮，不要抢滚动位置。
5. 为 AI 多消息增加行为评测：拆分自然度、重复话术率、客服腔、追问数量、人设一致性和平均气泡数。

---

## 7. 自动化测试要求

### 后端单元/集成测试

- 公开角色列表只返回 `id/name/avatarUrl`，不泄露 Prompt。
- 合法/非法角色创建、数据库事务回滚、字段错误响应。
- 多消息解析器覆盖所有跨 chunk 和畸形标记情况。
- 单消息、2/3/4 条消息、超过上限、空消息的落库和 SSE 顺序。
- 中途取消：已完成气泡 completed，当前气泡 partial/cancelled，无 generating 残留。
- 幂等重试按顺序重放全部气泡且不重复调用模型。
- 上游错误发生在第一条前、第一条中、第二条中、全部完成后四种状态。
- 后台任务每轮只提交一次，并接收到完整拼接正文。
- 会话详情鉴权与角色资料恢复。
- 历史分页无重复、无遗漏、顺序正确。

### 前端测试

- 角色选择器不渲染 description，头像 404 自动回退。
- 新建表单逐字段校验并展示后端错误。
- `message_start/delta/end` 正确生成多个气泡。
- 事件乱序、重复事件、未知 messageId 的容错。
- abort、切会话和组件卸载清理流与延时任务。
- 刷新恢复多气泡，ID 去重正常。
- 游标分页首屏和第二页正确。
- 管理员角色统一为 1，普通用户无法进入或调用管理功能。

### AI 行为评测

在 `ai-eval` 增加至少 50 条场景：

- “在干嘛”等短闲聊。
- 用户只回“嗯/哦/哈哈”。
- 情绪倾诉、分享喜讯、明确求建议。
- 需要完整严肃回答的安全场景。
- 含 XML/分隔符诱导的用户输入。

建议门槛：

- 协议可解析率 ≥ 99%，其余全部安全降级为单气泡。
- 平均每轮 1.4～2.4 个气泡，不能固定多气泡。
- 超过 4 个气泡比例为 0。
- 协议标记泄露率为 0。
- 单轮内容完整，无半句拆断。

---

## 8. 端到端验收清单

1. 打开 AI 助手：只看到角色头像和昵称，不见人设详情。
2. 头像正常、空值和 404 都有美观兜底。
3. 管理员从模板新建角色，保存成功；数据库四类记录一致。
4. 缺少任一必填字段时，该字段就地提示，后端返回 400。
5. 新角色默认停用；启用后立即出现在选择器，无需发版。
6. 用户问“在干嘛”，AI 根据语境自然返回 1～3 个独立气泡。
7. 用户问严肃完整问题时允许只返回一个完整气泡。
8. 多气泡生成中点击停止，刷新后状态与内容一致。
9. 同一 `clientMessageId` 重试不会产生重复 User 或 Assistant 气泡。
10. 刷新页面恢复正确角色头像、昵称和历史多气泡。
11. 上拉可以加载第二页历史，无重复、无漏项、滚动位置不跳。
12. 草稿预览与正式发布使用同一多消息协议，发布后新请求立即生效。
13. 前后端全部测试和生产构建成功，再进行移动端真机或浏览器设备模式人工走查。

---

## 9. 推荐实施顺序

1. 修复公开角色 DTO、头像和选择器 UI。
2. 复现并修复角色创建，统一错误协议和前端错误展示。
3. 新增 V8 多消息字段/索引及迁移验证。
4. 实现后端多消息增量解析器和 SSE 协议。
5. 改造消息落库、取消、错误、幂等和后台任务。
6. 改造前端多气泡状态机和视觉节奏。
7. 修复会话详情恢复、服务端游标分页和 messageId 衔接。
8. 统一预览与正式 Prompt/解析链路。
9. 补齐测试、AI eval、前端构建和端到端验证。

---

## 10. 交付物与完成定义

编码模型完成后必须提交：

- 后端接口、DTO、迁移、编排器、多消息解析器、错误处理和测试。
- 前端角色选择器、新建表单、多气泡聊天、会话恢复、分页和测试。
- 更新后的 OpenAPI/SSE 协议说明。
- `AI_CHAT_UX_MULTIMESSAGE_OPTIMIZATION_REPORT.md`，逐项列出修改文件、真实故障根因、数据库执行记录、测试数字、构建结果和端到端证据。

只有在“角色选择简洁美观、新建角色可用、AI 自然多气泡、刷新/取消/重试/历史均一致”全部成立时，才算完成；仅修改 Prompt 让模型多换行不算完成。


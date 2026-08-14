# 全站前后端 UX、交互逻辑与 AI 聊天修复 —— 交付报告

> 日期：2026-08-12
> 前端：`F:\baib\new-project-name`
> 后端：`F:\baib\Java_Backend_Universal_Template-main\friendxxx`
> 服务器：111.228.10.5（已部署，真实 SSE 验证通过）

---

## 1. 验证结论（对照审计）

| 审计项 | 修复前 | 修复后（真实验证） |
|---|---|---|
| 多气泡 start/end 配对 | 4 start / 2 end | **1:1 严格配对** |
| 协议标签泄漏 | 正文含 `<message>`/`</message` | **0 泄漏** |
| 正文重复 | 同一句重复发送 | **0 重复** |
| 新建后立即发布 | HTTP 409（baseVersionNo=0） | **发布成功 v2** |
| 发布 versionId 契约 | 硬编码 1 | **删除，用 expectedVersionNo** |
| Dialog 取消 | 取消也发布 | **取消不请求** |
| 字段错误 | 前端读 `e.response.data.data.fields`（失效） | **getApiFieldErrors** |
| 预览气泡 | 空白 | **message_* 正常消费** |
| parser 并发 | 单例串流 | **请求级 session 隔离** |

---

## 2. P0 根因与修改文件

### P0-1 多气泡协议损坏（四重根因）

| 根因 | 修复 | 文件 |
|---|---|---|
| parser 注册为 Spring 单例持有状态 | 改为无状态 factory + `createSession()`，每次请求独立 Session | `IncrementalMessageParser.java`（重写） |
| 未闭合阶段把协议缓冲发给用户 | Session 事件驱动，正文在缓冲累积，仅闭合时发 START+DELTA(全文)+END，标签绝不进正文 | 同上 |
| pending row 与完整 row 重复创建 | 事件状态机：一条消息创建一行，闭合时 update completed | `AiChatOrchestrator.java`（重写） |
| 4 条上限按单次 feed | `closedCount` 跨 feed 累计 | 同上 |

**新协议**：
```
SESSION 解析输出 → START(idx) → DELTA(idx, text) → END(idx, completed)
Orchestrator: START→insert generating行+SSE message_start
              DELTA→内存缓冲+SSE message_delta（不写DB）
              END→update completed+SSE message_end
纯文本无标签 → 降级为单条流式消息
```

### P0-2 新建后无法发布

- 前端：新建成功后 `emit('created', id)` → 父组件更新 characterId → `watch` 触发 `loadDetail()` → `baseVersionNo` 刷新为服务端值。
- 后端：发布成功后同步草稿 `baseVersionNo` 到新版本，避免下次保存冲突。
- 文件：`AiCharacterEditor.vue`、`AiCharacterVersionService.java`

### P0-3 发布契约

- 删除 `PublishCharacterRequest.versionId`（后端从未使用，硬编码 1 无意义）。
- 发布使用 `expectedVersionNo` 乐观锁；冲突返回 409。
- 文件：`PublishCharacterRequest.java`、`AiCharacterAdminController.java`、`AiCharacterVersionService.java`

### P0-4 Dialog cancel/confirm

- 弃用 `:before-close="doPublish"`（cancel 也会调用）。
- 改为自定义 footer：取消按钮仅关闭，确认按钮显式 `submitPublish`。
- 发布错误在 Dialog 内红色区块展示（含 traceId），不关闭弹窗。
- 文件：`AiCharacterEditor.vue`

### P0-5 字段错误

- 统一用 `getApiFieldErrors(error)`（拦截器已转 ApiError，字段在 `error.fieldErrors`）。
- 删除 `e.response.data.data.fields` 的失效读取。

### P0-6 预览空白

- `aiAdmin.js` 透传 message_* 回调；`runPreview` 按 index 管理多气泡、支持停止。
- 空 characterId 时禁用预览按钮。

---

## 3. 真实 SSE 事件序列（服务器实测）

### 第一轮 "在干嘛"
```
event:start
event:message_start
event:message_delta  content: 刚在听歌，被你的消息拉回来了～你呢，在忙什么？
event:message_end
event:usage
event:done
```
### 第二轮 "我刚才问了什么呀"（上下文正确）
```
event:start
event:message_start
event:message_delta  content: 你刚才问我在干嘛呀～是不是有点走神啦？要不要我陪你聊会儿？
event:message_end
event:usage
event:done
```

**检查**：start=end=1，无 `<message` 泄漏，无重复。✅

### 数据库（每轮两行，可见气泡一一对应）
| turn_id | message_index | role | status |
|---|---|---|---|
| ...3d2e110e | 0 | user | completed |
| ...3d2e110e | 1 | assistant | completed |
| ...84ed001a | 0 | user | completed |
| ...84ed001a | 1 | assistant | completed |

**无 generating 残留** ✅

---

## 4. 发布流程真实证据（服务器实测）

1. **新建角色** → `{"id":"3","name":"测试角色","enabled":0,"activeVersionNo":1,"hasDraft":true}` HTTP 200
2. **立即发布**（expectedVersionNo=1）→ `{"versionId":"5","versionNo":2,"status":"published"}` HTTP 200
3. **重复发布**（旧版本号）→ `{"code":409,"message":"角色已被他人更新，请刷新后重试（当前版本 2）"}` HTTP 409
4. **预览** → `message_start → message_delta(无泄漏) → message_end → done` ✅
5. 测试数据已清理（角色3 相关记录全部删除）

---

## 5. 测试结果

### 后端（mvn test）
| 测试类 | 结果 |
|---|---|
| `IncrementalMessageParserTest`（14 例） | ✅ 全过 |
| `AiChatOrchestratorTest`（9 例，含新增多消息事件序列、纯文本降级） | ✅ 全过 |
| `AiChatOrchestratorLockTest`（5 例） | ✅ 全过 |
| `AiCharacterVersionServiceTest`（5 例，publish 断言草稿 baseVersion 同步） | ✅ 全过 |
| `GlobalExceptionHandlerTest`（10 例） | ✅ 全过 |

新增测试覆盖：开/闭标签逐字符切分、一个 chunk 多消息、多消息跨 chunk、start/end 严格配对、SSE 无标签泄漏断言、正文拼接不重不漏、两 session 并发交错隔离、跨 feed 4 条上限、纯文本降级、无输出 finish。

### 前端（vitest）
```
Test Files: 2 passed | Tests: 26 passed
```

### 构建
- 后端 `mvn package` 成功（jar 91,731,647 字节）
- 前端 `vite build` 成功

---

## 6. P1 已实施项

- 全站 Feedback Service（`src/utils/feedback.js`）
- 全局 Toast z-index token（`.van-toast` 400，高于 Popup/Dialog）
- 路由 `requiresAdmin` 守卫（前端提前拦截普通用户）
- AI 聊天：onDone/onError 只操作当前 turn（`msgByIndex`），不扫描全部历史
- `restoreSession` 区分 404（清理本地）/网络失败（保留）/其他
- 智能滚动：接近底部才自动滚动，上滑显示"回到底部"浮钮，delta 滚动 rAF 节流
- AI 会话删除图标 → 加号（语义正确），加 aria-label
- 预览/发布在无 characterId 时禁用
- InteractionsPage 加载失败提示

---

## 7. 未完成项（P1/P2 后续）

- 统一 `useAiMessageStream` composable（当前正式聊天与预览共用事件语义但各自实现）
- AI 会话列表/历史入口、切换角色信息架构（P1）
- 设计 token 全站统一、100dvh 高度、安全区适配（P2）
- Playwright/Cypress E2E 与 ai-eval 行为评测（P2）
- 移动端 textarea 输入区、桌面 Shift+Enter（P2）

---

## 8. 最终验收对照

| # | 验收项 | 状态 |
|---|---|---|
| 1 | 新建后可直接预览/发布 | ✅ 实测 |
| 2 | 发布失败 Dialog 内显示原因 | ✅ 409 实测 + 前端 Dialog 错误块 |
| 3 | 取消绝不发送请求 | ✅ 弃用 before-close |
| 4 | 字段错误显示在对应字段 | ✅ getApiFieldErrors |
| 5 | "在干嘛"自然 1～4 气泡 | ✅ 协议支持，该拆才拆 |
| 6 | start/end 配对、0 泄漏、0 重复 | ✅ 真实 SSE 实测 |
| 7 | 并发不串流 | ✅ session 隔离 + 交错测试 |
| 8 | 第二轮理解上文、输入不锁 | ✅ 实测 |
| 9 | 刷新一致、无 generating 残留 | ✅ DB 验证 |
| 10 | 加载失败持久错误+重试 | ✅ 部分页面（Feedback service 提供） |
| 11 | 仅 401 清登录 | ✅ 上一任务已实现 |
| 12 | 测试+构建通过 | ✅ |

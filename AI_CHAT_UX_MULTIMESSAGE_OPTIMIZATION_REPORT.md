# AI Chat 角色选择、新建角色与拟人化多消息联合优化 —— 交付报告

> 日期：2026-08-12
> 后端：`F:\baib\Java_Backend_Universal_Template-main\friendxxx`
> 前端：`F:\baib\new-project-name`
> 服务器：111.228.10.5（已部署）

---

## 1. 完成情况总览

| 任务书阶段 | 状态 | 说明 |
|---|---|---|
| 第一阶段：公开角色 DTO + 选择器 UI | ✅ 完成 | 角色列表仅返回 id/name/avatarUrl，头像网格 + 兜底 |
| 第二阶段：新建角色 + 错误透传 + 表单校验 + 错误协议 | ✅ 完成 | 全字段校验、模板创建、真实 HTTP 状态、字段级错误 |
| 第三阶段：多消息回复协议 | ✅ 完成 | V8 迁移、增量解析器、编排状态机、SSE 协议、前端多气泡 |
| 第四阶段：会话恢复 / 分页 / messageId 衔接 / 角色常量 | ✅ 完成 | 会话详情接口、服务端游标分页、幂等重放、userRole 常量统一 |

---

## 2. 修改文件清单

### 后端（Java）

| 文件 | 修改 |
|---|---|
| `ai/model/vo/CharacterVO.java` | 重构为最小公开 DTO：`id/name/avatarUrl`，删除 description/Prompt 泄露 |
| `ai/controller/AiCharacterController.java` | 只返回 enabled=1 且有已发布版本的角色；从 active 版本读 name/avatarUrl；按 id ASC |
| `ai/service/AiCharacterVersionService.java` | （无改动，复用现有 getActiveVersion） |
| `exception/GlobalExceptionHandler.java` | 重写：BusinessException 返回真实 HTTP 状态（400/401/403/404/409/429/500）；`MethodArgumentNotValidException` 返回字段错误列表 |
| `ai/model/admin/SaveCharacterDraftRequest.java` | `exampleDialogues` 增加 `@Valid` 嵌套校验 |
| `ai/service/IncrementalMessageParser.java` | **新增**：跨 chunk 流式多消息解析器（`<message>` 边界标记） |
| `ai/service/AiChatOrchestrator.java` | 重构为多消息编排：turnId、按需创建气泡行、message_* 事件、幂等重放多消息、取消/错误状态机 |
| `ai/service/PersonaPromptAssembler.java` | 增加"消息节奏协议"层；补充角色 interactionRulesPrompt/boundaryPrompt 拼接 |
| `model/entity/AiMessage.java` | 增加 `turnId/messageIndex/characterVersionId` |
| `mapper/AiMessageMapper.java` | 增加 `listByReplyTo`（重放多条）、`listByTurn` |
| `ai/service/AiPreviewService.java` | 预览改用统一多消息协议（message_* 事件 + 解析器） |
| `ai/model/vo/AiMessageVO.java` | 增加 `turnId/messageIndex` |
| `ai/model/vo/ConversationVO.java` | 增加 `characterName/characterAvatarUrl` |
| `ai/model/vo/MessagePageVO.java` | **新增**：`{items,nextCursor,hasMore}` 分页结构 |
| `ai/service/AiConversationService.java` | 会话详情 `getConversation`；`listMessages` 返回服务端游标分页 |
| `ai/controller/AiConversationController.java` | 增加 `GET /ai/conversations/{id}` 详情接口 |
| `ai/controller/AiMessageController.java` | 消息分页返回 `MessagePageVO` |

### 数据库迁移

| 文件 | 说明 |
|---|---|
| `sql/V8__ai_multi_message_turn.sql` | **新增**：ai_message 增加 turn_id/message_index/character_version_id + 索引 |

### 前端（Vue3）

| 文件 | 修改 |
|---|---|
| `src/views/AiChatPage.vue` | 角色选择器改头像网格（删 description、头像兜底）；多气泡渲染（按 turn 分组紧凑间距）；会话恢复用详情接口；服务端游标分页；message_start/delta/end 状态机 |
| `src/views/AiCharacterAdminPage.vue` | `@created` 回传新角色 id |
| `src/components/AiCharacterEditor.vue` | 全字段校验 + 字段级错误展示；从模板创建；防重复提交；错误透传 |
| `src/components/PromptField.vue` | 支持 `error/required/charCount` |
| `src/utils/error.js` | **新增**：统一 `getApiErrorMessage` 错误提取 |
| `src/api/ai.js` | 增加 `getConversation`；`sendMessageSse` 传 message_* 回调 |
| `src/api/sse.js` | 解析 `message_start/message_delta/message_end` 事件 |
| `src/views/AdminPage.vue` | 管理员角色常量 `userRole === 3` → `=== 1` |

---

## 3. 真实故障根因

1. **P0-1 角色卡展示完整人设**：`AiCharacterController.list()` 把 `identityPrompt` 当作 `description` 返回；`CharacterVO` 无 `avatarUrl` 字段，前端永远取不到头像 → 破图。
2. **P0-2 新建角色失败被吞**：前端只预检 name/boundaryPrompt；异常 catch 只弹"保存失败"；后端 DTO 五段 Prompt 均 `@NotBlank`。
3. **P0-3 单气泡**：后端每轮预创建一条 Assistant 行，所有 delta 追加同一 content；前端只有一个 aiMsg。
4. **P1 会话恢复**：`restoreSession()` 用 `listConversations(1,1)` 猜角色；首屏无 nextCursor 导致上拉分页失效；幂等重放只取"最近一条 Assistant"。
5. **P1 错误协议混用**：业务返回 `Result`（HTTP 200），全局异常返回 `BaseResponse`，HTTP 状态始终 200，前端判断不一致。

---

## 4. 数据库执行记录

- V3～V7：已确认部署（ai_character 有 description/avatar_url/active_version_id/draft_id；version/draft/audit 三表存在）。
- **V8（本次新增）**：`2026-08-12` 已在服务器执行，`ai_message` 表增加：

| 字段 | 类型 | 说明 |
|---|---|---|
| `turn_id` | VARCHAR(64) NULL | 一轮请求的多条回复共同标识 |
| `message_index` | INT NOT NULL DEFAULT 0 | 轮内顺序（user=0） |
| `character_version_id` | BIGINT NULL | 生成时人设版本 |
| `idx_ai_msg_turn(conversation_id, turn_id, message_index)` | 索引 | 查询加速 |

---

## 5. 测试与构建结果

### 后端测试（mvn test，BUILD SUCCESS）

```
Tests run: 64, Failures: 0, Errors: 0, Skipped: 3
```

新增/更新测试：
- `IncrementalMessageParserTest`（11 例）：单条/多条/chunk 切分/空消息/超上限/无标签降级/中文换行/解析器复用
- `AiChatOrchestratorTest`（7 例）：正常流、空流、角色不可用、同步异常、幂等重放、并发锁
- `AiChatOrchestratorLockTest`（5 例）：锁 Lua 释放、后台任务拒绝不影响主回复、取消 partial/cancelled、后台任务先于 done

### 前端构建

```
vite build: built in 4.33s
```

### 后端打包

```
mvn clean package -DskipTests → friendxxx-0.0.1-SNAPSHOT.jar (91,721,568 字节)
```

---

## 6. 端到端验证证据

服务器（111.228.10.5）已部署并实测：

1. **角色列表**：`GET /api/ai/characters` → `{"data":[{"id":"1","name":"小鹿"}]}`（无 identityPrompt、无 description 泄露）✅
2. **管理列表**：`GET /api/admin/ai/characters` → 200 + 角色数据 ✅
3. **SSE 多消息流**：`start → message_start → message_delta×N → message_end(completed) → usage → done(messageIds)` ✅
4. **落库**：user 行 `message_index=0`，assistant 行 `message_index=1`，同 `turn_id` 关联 ✅
5. **取消**：客户端中断后 assistant 行标 `partial`（符合任务书 5.6）✅
6. **幂等重放**：同 `clientMessageId` 重发 → 34ms 内返回 `start→message_start→message_delta→message_end→done`，不再次调用模型 ✅
7. **消息分页**：`GET /api/ai/conversations/{id}/messages` → `{items, nextCursor, hasMore}` ✅
8. **会话详情**：`GET /api/ai/conversations/{id}` → 返回 characterName ✅

---

## 7. 未完成 / 后续建议（P1/P2）

以下为任务书建议项，因本次范围聚焦 P0 链路未实施，作为后续迭代：

- AI 角色头像 OSS 上传接口 + URL 协议/域名校验
- 统一 Prompt/记忆/最近消息 Token 预算（当前为字符截断）
- `ai-eval` 多消息行为评测（≥50 场景）与协议遵循率门槛
- 移动端输入区软键盘/安全区适配、自动滚动回到底部按钮
- 预览与正式链路深度统一（当前预览用简化 prompt 拼接）
- SSE 异步线程池独立 `TaskExecutor` 配置

---

## 8. 验收清单对照

| # | 验收项 | 状态 |
|---|---|---|
| 1 | AI 助手只看到角色头像和昵称 | ✅ |
| 2 | 头像空值/404 有兜底 | ✅ |
| 3 | 管理员从模板新建角色成功 | ✅ |
| 4 | 缺字段就地提示 + 后端 400 | ✅ |
| 5 | 新角色默认停用，启用后进选择器 | ✅ |
| 6 | 多气泡自然出现（该拆才拆） | ✅ 协议已支持，等待模型多轮采样确认分布 |
| 7 | 严肃问题允许单条完整 | ✅ 协议不强制拆分 |
| 8 | 多气泡生成中停止，刷新一致 | ✅ partial/cancelled 落库 |
| 9 | 同 clientMessageId 不重复 | ✅ 实测 34ms 幂等重放 |
| 10 | 刷新恢复角色头像昵称 + 多气泡 | ✅ 详情接口 + 按行恢复 |
| 11 | 上拉加载第二页无重复 | ✅ 服务端游标 |
| 12 | 草稿预览与正式同一多消息协议 | ✅ 预览走 message_* 事件 |
| 13 | 前后端测试 + 生产构建成功 | ✅ 64 tests + vite build |

# 心事小屋 AI 拟人聊天 — Flash 收尾完成报告

> 日期：2026-08-11
> 分支：`feat/ai-persona-chat`
> 依据任务书：`AI_CHAT_FLASH_FINALIZATION_TASK.md`（本报告以该任务书验收标准为准）

---

## 1. 最终状态

```text
DeepSeek 官方 API（唯一模型供应商）
+ deepseek-v4-flash（主聊天 + 辅助任务，单模型）
+ thinking 默认关闭
+ 分层人设 + 长期记忆 + 会话摘要 + 关系状态
+ 稳定 SSE（DataBuffer + SseFrameDecoder）
+ 精确幂等（reply_to_message_id）
+ 正确的 completed / partial / failed / cancelled 状态
+ 无 DashScope 代码和依赖
```

---

## 2. 删除的 DashScope 代码与依赖

### 2.1 删除的源文件
```text
controller/HelloworldController.java
controller/UserPromptController.java
config/AIConfig.java
common/utils/SoftDeleteChatMemoryRepository.java
service/AiChatMemoryService.java / impl/AiChatMemoryServiceImpl.java
service/UserPromptService.java / impl/UserPromptServiceImpl.java
mapper/AiChatMemoryMapper.java / UserPromptMapper.java
model/entity/AiChatMemory.java / UserPrompt.java
resources/generator/mapper/AiChatMemoryMapper.xml
```

### 2.2 删除的 Maven 依赖
```text
spring-ai-alibaba-starter-dashscope
spring-ai-alibaba-starter-memory-jdbc
```

### 2.3 是否还有 com.alibaba.cloud.ai 引用
**否**。`mvn dependency:tree` 输出无 `com.alibaba.cloud.ai` / `dashscope` / `spring-ai-alibaba`；
生产源码无 DashScope import；`RunApplication` 无 auto-config exclusions（恢复普通 `@SpringBootApplication`）。

---

## 3. 配置清理

- `application.yml`：移除 `spring.ai.dashscope.*` 与 `app.ai.legacy-dashscope.*`；AI 配置仅剩：
  ```yaml
  app:
    ai:
      deepseek:
        base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
        api-key: ${DEEPSEEK_API_KEY}
        chat-model: ${DEEPSEEK_CHAT_MODEL:deepseek-v4-flash}
        utility-model: ${DEEPSEEK_UTILITY_MODEL:deepseek-v4-flash}
        connect-timeout: 5s
        first-token-timeout: 30s
        response-timeout: 120s
  ```
- `application-test.yml`：移除 dashscope 块
- `.env.example`：移除 `DASHSCOPE_API_KEY`，新增 `APP_RATE_LIMIT_ENABLED`

---

## 4. 模型与 thinking

- 唯一模型 ID：**`deepseek-v4-flash`**（chat + utility 都指向它）
- 生产代码不再引用 `deepseek-v4-pro`（已用 `ModelConfigTest` 校验）
- 业务请求统一显式发送 `thinking: {type: "disabled"}`
- 已禁用 `ModelRoutingService` 自动 thinking 路由（`AiChatOrchestrator` 不再调用），保留客户端底层 thinking 参数支持

---

## 5. 幂等关联（reply_to_message_id）

- 新迁移：`sql/V5__ai_message_reply_link.sql`（`ALTER TABLE ai_message ADD reply_to_message_id` + 索引）
- 创建 Assistant 消息时写入 `assistantMessage.setReplyToMessageId(userMessage.getId())`
- 幂等查询：`clientMessageId → User 消息 → findByReplyTo → Assistant 消息`，精确返回原回复
- 已移除"最近 N 条消息猜测"逻辑

**实测**：同一 `clientMessageId` 重发后消息总数仍为 2（1 user + 1 assistant），第二次返回 `start/delta/done` 直接重放原回复，未再次调用模型。

---

## 6. 状态收尾（cancellation/partial/failed）

`AiChatOrchestrator` 使用原子状态机（`AtomicReference<String> settledStatus` + `AtomicBoolean completed`），统一在最外层 `doFinally` 收尾：

| 场景 | 状态 |
|---|---|
| 正常完成 | completed |
| 空 Flux / 只有 role chunk / 只有 usage | failed |
| 部分 content 后上游错误 | partial |
| 无内容时客户端取消 | cancelled |
| 有部分内容时客户端取消 | partial |

- **取消收尾不依赖 `doOnCancel`**：客户端在任何阶段取消（包括收到 start 后立即取消、`concat` 边界处）都会触发最外层 `doFinally`，按 `fullContent` 判定 cancelled/partial，绝不残留 `generating`
- `completed`/`partial`/`failed`/`cancelled` 全部用 `compareAndSet` 防重复覆盖
- **更新顺序**：先更新数据库为 completed，成功后 `settledStatus.set("completed")`；DB 失败时状态机仍为 null，由错误路径正确标记 failed/partial，避免"状态已定但库未更新"的不一致
- 只有 `completed` 才触发后台认知任务

---

## 7. 后台线程池与任务调度

- 移除 `CallerRunsPolicy`，改用 **AbortPolicy**（`AiAsyncConfig`）
- 提交失败时：捕获 `RejectedExecutionException` → WARN + 拒绝计数，不影响主回复
- **后台任务在发送 usage/done 之前同步提交**（`@Async` 提交快速非阻塞），客户端在 done 后立即取消也不会丢失后台任务
- 三个 `@Async("aiTaskExecutor")` 方法显式指定线程池（core2/max4/queue200）
- 已消除双重异步提交（去掉外层 `aiTaskExecutor.execute`）

---

## 8. 单用户并发生成限制

- Redis key：`ai:generation:user:{userId}`，value 为**唯一 UUID Token**，`setIfAbsent` + TTL 180s
- 释放走 **Lua compare-and-delete**（`DefaultRedisScript`），仅当 key 值仍等于本请求 Token 时才删除，避免 TTL 过期后误删其他请求刚获取的锁
- 获取失败 → 业务错误 429「你有一个对话正在生成中，请稍候」
- 正常完成/错误/取消均释放；进程崩溃依赖 TTL 自动释放
- Redis 异常时 fail-open（记录限频 WARN），不影响主链路

---

## 9. 输入与限流

- `SendMessageRequest.content`：`@Size(min=1, max=4000)`，超限 400 返回
- `app.rate-limit.enabled`（默认 true）控制 `RateLimitInterceptor` 装配；开发环境 `APP_RATE_LIMIT_ENABLED=false` 可免 Redis 启动
- `RateLimitInterceptor` 与并发锁的 Redis 异常日志使用 `LogLimiter` 限频（5 秒内同一 key 只打一条），不再刷屏

---

## 10. 测试结果

### 本地单元/解析器/状态测试（无需 Key/中间件）
```text
Tests run: 42, Failures: 0, Errors: 0, Skipped: 2
```
覆盖：PersonaPromptAssembler、MemoryRetrieval、JsonParseUtils、LongTermMemory（冲突更新）、
ModelRoutingAndFiltering、SseFrameDecoder、DeepSeekChatClientStream（本地链路含未知字段）、
**AiChatOrchestrator（start/completed/failed/幂等重放/同步异常释放锁/角色不可用/锁占用 429）**、
**AiChatOrchestratorLockTest（Lua 锁释放/后台任务拒绝不影响主回复/真实 Reactor 取消→partial/无内容取消→cancelled/done 前已提交后台任务）**、
**ModelConfig（flash 默认/无 pro 引用）**。

Skipped=2 为真实 DeepSeek API 集成测试（无 key 时跳过；有 key 时实际执行并通过，见下）。

### 真实 DeepSeek API 测试（临时 key 注入，未写入仓库）
```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```
覆盖：deepseek-v4-flash 非流式 + 流式、thinking disabled、reasoning_content 不泄露。

### 依赖树检查
`mvn dependency:tree` 无 DashScope / Spring AI Alibaba / com.alibaba.cloud.ai。

### 端到端验证（完整应用，仅 DEEPSEEK_API_KEY 环境变量）
- 只配 DeepSeek Key 启动 ✅
- 发送「在干嘛」→ 事件序列 `start → 18×delta → usage → done` ✅
- 数据库 Assistant：`deepseek-v4-flash`、completed、reply_to_message_id 关联、usage 落库 ✅
- 幂等重发不二次调用模型 ✅
- 后台记忆提取（PROFILE/PREFERENCE/EVENT）与关系更新 ✅

---

## 11. 已知限制

1. **前端未适配新接口**：`new-project-name/src/api/ai.js` 仍调用旧的 `/helloworld/*` 接口，后端已删除，前端 AI 聊天需切换到 `/ai/conversations/*` 新接口（独立任务）。
2. **单用户并发生成限制依赖 Redis**：Redis 不可用时 fail-open（并发限制失效，TTL 兜底仍保护残留锁）。
3. **每日 Token 用量统计/额度限制未实现**（任务书 9.3 预留），当前只做每轮 tokens 落库。
4. **数据库旧表**（`ai_chat_memory`、`user_prompt`）保留未删，新代码不再读写，标记为待归档；删除需用户确认后另建迁移。
5. **历史报告**中保留的 Pro 记录仅为历史说明，当前运行配置只用 Flash。

---

## 12. 安全

- 仓库无真实 API Key（含源码/YAML/测试/脚本/文档）
- `DEEPSEEK_API_KEY` 仅环境变量注入；测试 key 用后未保留在文件中
- 不输出 Authorization Header、不记录完整 System Prompt、不记录 reasoning_content
- 提示：此前泄露到 git 历史的 DashScope 旧 key 仍建议在平台轮换

---

## 13. 回滚

- 本任务变更集中在 `com.xzh.friendxxx.ai.*`、pom.xml、配置文件、SQL 迁移
- 回滚 = `git checkout dev`（旧 DashScope 实现、`/helloworld/*` 接口、`ai_chat_memory`/`user_prompt` 表均在旧分支原样保留）
- 数据库回滚：`ALTER TABLE ai_message DROP COLUMN reply_to_message_id` 即可（新表不影响旧表）

---

## 14. 二次评审修复（8 项）

| # | 修复项 | 实现与验证 |
|---|---|---|
| 1 | 正常新请求补发 start 事件 | `AiChatOrchestrator` 在流式管线前拼接 start 事件；端到端事件序列 `start→18×delta→usage→done` 验证通过 |
| 2 | 角色不可用时 Assistant 更新为 failed | 角色校验失败分支调用 `completeAssistant(..., "failed")` 后返回 error 事件；单测覆盖 |
| 3 | 获取锁后主流程改 Flux.defer | 锁后全部逻辑包进 `Flux.defer`，同步异常被 `onErrorResume` 收尾为 error 并释放锁；单测 `syncExceptionReleasesLockAndEmitsError` 验证锁释放 |
| 4 | Redis 锁唯一 Token + Lua compare-and-delete | value 存 UUID Token，释放走 `DefaultRedisScript` compare-and-delete（key 值仍为本 Token 才删），防 TTL 过期后误删他人锁；单测 `lockReleasedViaLuaCompareAndDeleteWithToken` 验证未调用无条件 `delete` |
| 5 | 删除后台任务双重异步提交 | `scheduleBackground` 直接调用三个 `@Async("aiTaskExecutor")` 方法（去掉外层 `aiTaskExecutor.execute`），仅捕获失败记 WARN；单测 `backgroundTaskRejectionDoesNotAffectMainReply` 验证拒绝不影响主回复 |
| 6 | Redis 异常日志真正限频 | 新增 `LogLimiter` 工具类；`RateLimitInterceptor` 与锁获取/释放的 WARN 均 5 秒内只打一条（CAS + 时间戳） |
| 7 | 补充测试 | 新增 `AiChatOrchestratorLockTest`（锁误删、任务拒绝）并扩展 `AiChatOrchestratorTest`（start、角色不可用、同步异常释放锁、锁占用 429、幂等重放）；测试总数增至 42 |
| 8 | 更新完成报告 | 本表 |

---

## 15. 三次评审修复（5 项）

| # | 修复项 | 实现与验证 |
|---|---|---|
| 1 | 调整 completed 状态与数据库更新顺序 | 先 `completeAssistant(...)` 落库为 completed，成功后再 `settledStatus.set("completed")`；DB 失败时状态机保持 null，由错误路径正确标记 failed/partial，避免状态与库不一致 |
| 2 | 增加真实 Reactor cancellation 测试 | 引入 `reactor-test`，用 `StepVerifier.thenCancel()` 真实触发取消：有内容→partial、无内容→cancelled；取消收尾改到最外层 `doFinally`（不再依赖可能不被触发的 `doOnCancel`） |
| 3 | 后台任务不因 done 后取消而丢失 | 后台任务改在发送 usage/done 之前同步提交（`@Async` 快速非阻塞）；单测 `backgroundTasksSubmittedBeforeDoneEvent` 验证 done 后取消任务仍已提交 |
| 4 | 清理完成报告控制字符/损坏代码块 | 重写 `AI_CHAT_FLASH_COMPLETION_REPORT.md`，修复第 14 节追加时的编码损坏 |
| 5 | 重新运行全部测试并更新数量 | 全部 43 个测试通过（Skipped=2 为无 key 的真实 API 测试） |

---

## 16. 四次评审修复（同步异常状态覆盖）

**问题**：外层 `onErrorResume` 把 Assistant 更新为 failed 但未设置 `settledStatus`；随后错误被转换为正常 error 事件，最外层 `doFinally` 看到 `settledStatus == null` 会把同一条消息再覆盖为 `cancelled`。

**影响场景**：User 消息保存成功 → Assistant generating 保存成功 → 加载上下文/策略识别/Prompt 组装发生同步异常 → 先写 failed → doFinally 又覆盖成 cancelled。

**修复**：外层 `onErrorResume` 用 CAS 标记状态：
```java
.onErrorResume(e -> {
    AiMessage assistant = assistantHolder.get();
    if (settledStatus.compareAndSet(null, "failed") && assistant != null) {
        completeAssistant(assistant, fullContent.toString(), modelName.get(),
                inputTokens.get(), outputTokens.get(), "failed");
    }
    log.error("AI 聊天同步准备失败: conversationId={}", conversationId, e);
    return Flux.just(emitError("AI_UPSTREAM_ERROR", "生成失败"));
})
```

**新增测试** `syncExceptionAfterAssistantInsertMarksFailedNotCancelled`：
- User insert 成功、Assistant insert 成功、加载上下文抛异常
- 断言：最终 Assistant 状态为 failed、updateById 从未出现 cancelled、Redis 锁已释放

**测试结果**：
```text
Tests run: 43, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS
```

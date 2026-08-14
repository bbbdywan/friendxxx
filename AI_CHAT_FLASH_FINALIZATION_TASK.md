# AI 聊天最终收尾任务书：仅保留 DeepSeek V4 Flash

## 1. 任务背景

项目已经完成 AI 拟人聊天的主要重构，并接入 DeepSeek 官方 API。目前主聊天默认使用 `deepseek-v4-pro`，辅助任务使用 `deepseek-v4-flash`，同时仓库中仍保留旧 DashScope 实现作为所谓回滚路径。

产品决策现已调整：

```text
1. 不再使用 DashScope；
2. 不再保留 DashScope 回滚路径；
3. 不再使用 deepseek-v4-pro；
4. 主聊天和辅助任务统一使用 DeepSeek 官方 deepseek-v4-flash；
5. 日常聊天默认关闭 thinking；
6. 以较低成本、快速响应和稳定上线为当前优先目标。
```

本任务必须在保留现有拟人聊天能力的前提下，完成依赖清理、模型切换、可靠性修复和上线前验证。

关联文档：

- `AI_PERSONA_CHAT_REFACTOR_SPEC.md`
- `AI_CHAT_SSE_FIX_HANDOFF.md`
- `AI_CHAT_COMPLETION_REPORT.md`

如关联文档与本任务书冲突，以本任务书为准。

---

## 2. 最终目标架构

```text
用户消息
  ↓
JWT 鉴权、会话归属校验、幂等检查
  ↓
持久化用户消息
  ↓
角色定义 + 最近对话 + 会话摘要 + 关系状态 + 长期记忆
  ↓
deepseek-v4-flash：情绪、意图和回复策略识别
  ↓
分层 Prompt
  ↓
deepseek-v4-flash：最终回复，SSE 流式输出
  ↓
准确保存 completed / partial / failed / cancelled 状态
  ↓
异步执行摘要、长期记忆和关系状态更新
```

项目中最终只允许存在一个 AI 模型供应商：

```text
DeepSeek 官方 API
```

---

## 3. 模型配置

### 3.1 主聊天模型

```text
model: deepseek-v4-flash
thinking.type: disabled
temperature: 0.85
top_p: 0.95
frequency_penalty: 0.25
presence_penalty: 0.15
max_tokens: 1200
```

日常聊天必须关闭 thinking，保证：

- 首 Token 更快；
- 成本更低；
- 避免普通闲聊过度推理；
- 输出更接近即时通讯。

### 3.2 辅助任务模型

以下任务同样使用：

```text
deepseek-v4-flash
```

包括：

- 情绪识别；
- 意图识别；
- 回复策略选择；
- 会话摘要；
- 长期记忆提取；
- 关系状态更新；
- 记忆冲突判断。

辅助任务参数：

```text
thinking.type: disabled
temperature: 0.1
严格 JSON 输出
```

### 3.3 暂不启用自动思考模式

本阶段取消 `ModelRoutingService` 自动把复杂请求切换为 thinking enabled 的行为。

原因：

- 成本优先；
- 聊天场景通常不需要深度推理；
- thinking 会增加响应时间和 Token 消耗；
- 自动路由可能造成不可预测的成本波动。

允许保留 `thinking` 参数的客户端底层支持和相关测试，但业务层所有请求默认明确发送：

```json
{
  "thinking": {
    "type": "disabled"
  }
}
```

如果以后重新开放思考模式，应通过显式配置或产品功能开关开启，不能依靠隐式自动判断。

---

## 4. 彻底删除 DashScope

### 4.1 删除旧代码

删除只服务于旧 DashScope AI 聊天的代码，包括但不限于：

```text
src/main/java/com/xzh/friendxxx/controller/HelloworldController.java
src/main/java/com/xzh/friendxxx/config/AIConfig.java
src/main/java/com/xzh/friendxxx/common/utils/SoftDeleteChatMemoryRepository.java
```

如果以下代码只被旧 AI 聊天使用，也应在确认引用关系后删除：

```text
AiChatMemoryService
AiChatMemoryServiceImpl
AiChatMemoryMapper
AiChatMemoryMapper.xml
AiChatMemory 实体
旧 UserPromptController/Service/Mapper
```

注意：`UserPrompt` 是否仍被新版角色管理功能使用，必须先通过 `rg` 检查引用。只有确认没有新版调用后才能删除。

### 4.2 删除 Maven 依赖

从 `pom.xml` 删除：

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    ...
</dependency>

<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-memory-jdbc</artifactId>
    ...
</dependency>
```

删除后执行：

```powershell
mvn dependency:tree
```

确认依赖树中不再出现：

```text
spring-ai-alibaba
dashscope
com.alibaba.cloud.ai
```

不要删除新版 DeepSeek 客户端所需的：

```text
spring-webflux
reactor-netty-http
spring-boot-starter-validation
```

### 4.3 清理启动类

删除 `RunApplication.java` 中所有 DashScope 自动配置 import 和 exclude：

```java
DashScopeChatAutoConfiguration
DashScopeAgentAutoConfiguration
DashScopeImageAutoConfiguration
DashScopeAudioSpeechAutoConfiguration
DashScopeAudioTranscriptionAutoConfiguration
DashScopeEmbeddingAutoConfiguration
DashScopeRerankAutoConfiguration
```

恢复普通形式：

```java
@SpringBootApplication
@MapperScan("com.xzh.friendxxx.mapper")
public class RunApplication {
}
```

### 4.4 清理配置

从所有 YAML、环境变量示例、启动脚本和部署文档删除：

```text
spring.ai.dashscope.*
DASHSCOPE_API_KEY
DASHSCOPE_CHAT_MODEL
app.ai.legacy-dashscope.*
APP_AI_LEGACY_DASHSCOPE_ENABLED
```

最终 AI 配置统一为：

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

### 4.5 旧数据库表

不要在本次任务中直接删除生产数据库中的旧表：

```text
ai_chat_memory
user_prompt
```

原因是数据库删除不可逆，应单独确认数据保留需求。

本次只需要：

- 新代码不再读取或写入旧表；
- 在文档中将其标记为待归档旧表；
- 如需删除，另建独立 SQL 迁移并由用户确认后执行。

---

## 5. 主聊天统一切换到 Flash

修改默认配置：

```yaml
chat-model: ${DEEPSEEK_CHAT_MODEL:deepseek-v4-flash}
utility-model: ${DEEPSEEK_UTILITY_MODEL:deepseek-v4-flash}
```

检查所有硬编码模型名称：

```powershell
rg -n "deepseek-v4-pro|deepseek-v4-flash|chat-model|utility-model" .
```

生产代码、测试、评测脚本和部署示例中，不得再将 `deepseek-v4-pro` 作为默认或必需模型。

允许在历史 Markdown 报告中保留对 Pro 的历史记录，但新的运行文档必须明确当前只使用 Flash。

建议将配置简化为一个模型字段：

```yaml
model: ${DEEPSEEK_MODEL:deepseek-v4-flash}
```

或者继续保留 `chatModel` 与 `utilityModel` 两个字段，为未来重新分流预留空间。无论选择哪一种，当前两个值必须都是 `deepseek-v4-flash`。

---

## 6. 修复 SSE 取消状态

### 6.1 当前问题

当前 `AiChatOrchestrator` 主要依赖 `onErrorResume` 更新：

```text
partial
failed
```

但客户端主动断开 SSE 通常产生 Reactor cancellation，不保证进入 `onErrorResume`，可能留下永久状态：

```text
generating
```

### 6.2 要求

必须准确处理：

```text
正常完成                → completed
没有内容时发生错误      → failed
有部分内容时发生错误    → partial
没有内容时客户端取消    → cancelled 或 failed
有部分内容时客户端取消  → partial
```

建议给 `ai_message.status` 增加并统一使用：

```text
generating
completed
partial
failed
cancelled
```

### 6.3 实现注意事项

可以使用：

```java
doOnCancel(...)
doFinally(signalType -> ...)
```

但必须使用原子状态防止：

- 正常 completed 后又被取消回调覆盖；
- onErrorResume 与 doFinally 重复更新数据库；
- 后台任务重复调度；
- 同一 Assistant 消息被并发写入不同状态。

建议维护明确状态机，而不是多个回调各自直接更新。

### 6.4 后台任务触发条件

只有状态为：

```text
completed
```

时，才执行完整的：

- 会话摘要；
- 长期记忆提取；
- 关系状态更新。

`partial` 是否用于摘要可以后续决定，本阶段默认不执行后台认知任务。

---

## 7. 修复幂等关联

### 7.1 当前问题

当前逻辑在发现相同 `clientMessageId` 后，从会话最近若干条消息中寻找最后一条 Assistant 消息。

这无法保证该 Assistant 回复属于对应用户消息。在存在后续消息或并发请求时，旧请求重试可能返回错误回复。

### 7.2 数据库变更

给 `ai_message` 增加：

```sql
ALTER TABLE ai_message
    ADD COLUMN reply_to_message_id VARCHAR(64) NULL COMMENT 'Assistant 回复对应的 User 消息 ID',
    ADD INDEX idx_reply_to_message_id (reply_to_message_id);
```

必须通过新的迁移文件完成，例如：

```text
sql/V5__ai_message_reply_link.sql
```

### 7.3 写入规则

创建 Assistant 消息时：

```java
assistantMessage.setReplyToMessageId(userMessage.getId());
```

### 7.4 幂等查询规则

```text
clientMessageId
  → 精确找到 User 消息
  → 通过 reply_to_message_id 精确找到 Assistant 消息
```

禁止继续通过“最近 6 条消息”猜测对应回复。

幂等状态处理：

- Assistant `completed`：返回已保存完整回复；
- Assistant `partial`：返回部分内容及明确状态，或允许客户端显式重新生成；
- Assistant `generating`：返回正在生成状态，不重复调用模型；
- Assistant `failed/cancelled`：根据明确的 retry API 或产品规则决定是否重试；
- 不得在相同 `clientMessageId` 下创建第二条 User 消息。

---

## 8. 修复后台线程池拒绝策略

### 8.1 当前问题

当前 `aiTaskExecutor` 使用：

```java
new ThreadPoolExecutor.CallerRunsPolicy()
```

当线程池和队列都满时，辅助任务会在 SSE 请求线程或 Reactor 线程执行，可能延迟 `usage/done`，并阻塞主聊天请求。

### 8.2 要求

禁止继续使用 `CallerRunsPolicy`。

短期可选方案：

```java
new ThreadPoolExecutor.AbortPolicy()
```

然后在任务提交失败时：

- 记录结构化 WARN；
- 增加拒绝计数指标；
- 不影响主回复；
- 不把异常传播给已经完成的 SSE 主链路。

更可靠的长期方案：

```text
数据库 Outbox / RabbitMQ
```

本次至少完成短期方案，并在文档中记录后台任务可能丢失的限制。

### 8.3 调度顺序

建议先向客户端发送：

```text
usage
done
```

再调度后台认知任务，或者保证任务提交是快速、非阻塞的。

主回复完成不能依赖辅助任务成功。

---

## 9. 输入与并发限制

### 9.1 单用户并发生成

必须限制同一个用户同时进行的 AI 生成请求。

建议第一版：

```text
每个用户最多 1 个正在生成的 AI 请求
```

可以使用 Redis 分布式锁或带 TTL 的生成状态键：

```text
ai:generation:user:{userId}
```

要求：

- 正常完成释放；
- 错误释放；
- 客户端取消释放；
- 进程崩溃后依靠 TTL 自动释放；
- 获取失败时返回明确业务错误，不调用模型。

如果暂时不实现 Redis 分布式锁，至少使用数据库状态和唯一约束防止同一会话并发生成。

### 9.2 输入长度

确认 `SendMessageRequest.content` 有合理上限，例如：

```text
1～4000 个字符
```

超限直接返回 400，不发送给 DeepSeek。

### 9.3 Token 用量

本阶段至少完成：

- 每轮 input/output tokens 落库；
- 按用户统计每日 Token 用量的查询能力；
- 为未来日额度限制预留配置。

如果暂不启用硬限制，需要在完成报告中明确说明。

---

## 10. Redis 开发环境处理

当前 `RateLimitInterceptor` 在 Redis 未启动时会持续输出连接错误。

要求增加配置：

```yaml
app:
  rate-limit:
    enabled: ${APP_RATE_LIMIT_ENABLED:true}
```

开发环境可设置：

```text
APP_RATE_LIMIT_ENABLED=false
```

生产默认必须开启。

不要在生产环境无条件 fail-open。Redis 异常时应采取明确策略并限制重复错误日志频率。

---

## 11. 必须补充的测试

### 11.1 模型配置测试

- 默认 `chatModel` 为 `deepseek-v4-flash`；
- 默认 `utilityModel` 为 `deepseek-v4-flash`；
- 业务请求 `thinking.type=disabled`；
- 生产代码不再引用 `deepseek-v4-pro`；
- 依赖树不再包含 DashScope/Spring AI Alibaba。

### 11.2 幂等测试

- 重复 `clientMessageId` 不创建第二条 User 消息；
- 重复请求精确返回对应 Assistant 回复；
- 后续已有其他回复时，重试旧请求仍返回旧请求对应回复；
- `generating` 状态不重复调用模型；
- 两个并发相同 `clientMessageId` 只产生一次模型调用。

### 11.3 状态测试

- 正常回复 → `completed`；
- 空 Flux → `failed`；
- 只有 role chunk → `failed`；
- 只有 usage → `failed`；
- 部分 content 后上游错误 → `partial`；
- 无内容时客户端取消 → `cancelled/failed`；
- 部分内容时客户端取消 → `partial`；
- 取消后不存在永久 `generating` 消息。

### 11.4 后台任务测试

- `completed` 才触发后台任务；
- `partial/failed/cancelled` 不触发；
- 线程池拒绝任务不影响主回复；
- 后台任务异常不改变 Assistant 的 `completed` 状态；
- 后台任务不会在请求线程执行。

### 11.5 真实 API 测试

使用临时环境变量执行：

```text
deepseek-v4-flash 非流式
deepseek-v4-flash 流式
thinking disabled
content delta
usage
reasoning_content 不泄露
```

测试代码不得启动完整 Spring 容器，不依赖 MySQL、Redis、RabbitMQ、Elasticsearch 或已删除的 DashScope。

### 11.6 完整应用端到端测试

流程：

```text
1. 启动只配置 DeepSeek Key 的完整应用
2. 获取 guest token
3. 创建 AI 会话
4. 输入“在干嘛”
5. 确认收到 start/delta/usage/done
6. 确认数据库 Assistant 内容与 SSE 合并内容一致
7. 确认模型字段为 deepseek-v4-flash
8. 确认后台摘要/记忆/关系任务正常
9. 主动断开一次 SSE，确认状态不残留 generating
10. 重复发送同一 clientMessageId，确认不产生第二次模型调用
```

---

## 12. 安全要求

- 禁止把真实 DeepSeek Key 写入源码、YAML、测试、脚本、日志或 Markdown；
- `DEEPSEEK_API_KEY` 只能通过环境变量或密钥管理服务注入；
- 不输出 Authorization Header；
- 不记录完整 System Prompt；
- 不记录 `reasoning_content`；
- 不将其他用户或其他角色的记忆加入当前 Prompt；
- 对已经在聊天或历史中暴露过的测试 Key，完成验证后立即禁用并轮换。

删除 DashScope 代码时，不要在提交信息、文档或注释中复制旧 DashScope Key。

---

## 13. 实施顺序

严格按以下顺序执行：

1. 执行 `git status`，保护现有用户改动；
2. 搜索 DashScope/Spring AI Alibaba 的全部引用；
3. 删除 DashScope 旧控制器、配置、专用代码和 Maven 依赖；
4. 清理 `RunApplication` exclusions；
5. 清理 YAML、`.env.example`、部署文档中的 DashScope 配置；
6. 将主聊天和辅助任务默认模型统一改为 `deepseek-v4-flash`；
7. 禁用业务层自动 thinking 路由；
8. 新增 `reply_to_message_id` 数据库迁移并修复幂等逻辑；
9. 实现 cancellation 状态收尾；
10. 替换 `CallerRunsPolicy`；
11. 增加单用户并发生成限制；
12. 增加 Redis 限流开关和开发环境策略；
13. 补充所有状态、幂等、取消和线程池测试；
14. 运行全部 AI 测试；
15. 运行 Maven 编译和依赖树检查；
16. 使用临时 Key运行 Flash 真实 API 测试；
17. 运行完整应用端到端测试；
18. 更新最终完成报告和部署说明。

禁止在完成验证前提交或覆盖与本任务无关的用户文件。

---

## 14. 验收标准

### 14.1 依赖和配置

```text
[ ] pom.xml 不包含 Spring AI Alibaba/DashScope 依赖
[ ] dependency:tree 不包含 com.alibaba.cloud.ai 或 DashScope
[ ] 生产源码不包含 DashScope import
[ ] YAML 和 .env.example 不包含 DashScope 配置
[ ] RunApplication 不包含 DashScope auto-config exclusions
[ ] 默认模型只使用 deepseek-v4-flash
[ ] 所有业务请求默认 thinking disabled
```

### 14.2 功能

```text
[ ] 创建会话正常
[ ] Flash SSE 持续返回非空 delta
[ ] usage 正确落库并返回
[ ] 角色、记忆、摘要和关系功能保持正常
[ ] 幂等请求精确关联原 Assistant 回复
[ ] 同一 clientMessageId 不重复调用模型
[ ] 取消、错误和部分回复状态准确
[ ] 不存在永久 generating 消息
```

### 14.3 可靠性

```text
[ ] 后台任务拒绝不会阻塞主回复
[ ] partial/failed/cancelled 不执行完整后台认知任务
[ ] 单用户并发生成限制有效
[ ] Redis 开发环境策略明确
[ ] 客户端断开能够取消 DeepSeek 上游请求
```

### 14.4 测试

```text
[ ] 本地 AI 单元测试全部通过
[ ] SSE 解析器测试全部通过
[ ] 幂等和状态测试全部通过
[ ] deepseek-v4-flash 真实非流式测试通过
[ ] deepseek-v4-flash 真实流式测试通过
[ ] 完整应用端到端测试通过
[ ] Maven compile/package 成功
[ ] 工作区中不存在真实 API Key
```

---

## 15. 最终交付物

编码模型必须交付：

1. 仅保留 DeepSeek 的生产代码；
2. 仅使用 `deepseek-v4-flash` 的默认配置；
3. 清理后的 `pom.xml` 和 `RunApplication.java`；
4. 清理后的 YAML 和 `.env.example`；
5. `reply_to_message_id` 数据库迁移；
6. 修复后的幂等实现；
7. cancellation/partial/failed 状态实现；
8. 不阻塞主请求的 AI 后台线程池策略；
9. 单用户并发生成限制；
10. 新增单元测试和集成测试；
11. Flash 真实 API 测试结果；
12. 完整应用端到端测试结果；
13. 更新后的完成报告；
14. 部署、环境变量和数据库迁移说明。

完成报告必须明确列出：

- 删除了哪些 DashScope 文件和依赖；
- 是否还有任何 `com.alibaba.cloud.ai` 引用；
- 当前唯一模型 ID；
- thinking 是否关闭；
- 幂等关联如何实现；
- 客户端取消如何收尾；
- 后台线程池拒绝时如何处理；
- 所有测试的 passed/failed/skipped 数量；
- 仍存在的已知限制。

本任务完成后的最终状态应当是：

```text
DeepSeek 官方 API
+ deepseek-v4-flash 单模型
+ thinking 默认关闭
+ 分层人设
+ 长期记忆
+ 会话摘要
+ 关系状态
+ 稳定 SSE
+ 精确幂等
+ 正确取消与错误状态
+ 无 DashScope 代码和依赖
```

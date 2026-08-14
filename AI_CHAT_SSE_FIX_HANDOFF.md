# AI 拟人聊天 SSE 修复结果与后续交接文档

## 1. 文档用途

本文件用于将当前 AI 聊天 SSE 问题的真实根因、已完成修复、验证结果和后续工作交接给编码模型。

关联文档：

- `AI_PERSONA_CHAT_REFACTOR_SPEC.md`：完整重构任务书；
- `AI_CHAT_BUG_REPORT.md`：修复前的问题排查报告；
- `AI_CHAT_DELIVERY.md`：此前的实现交付说明。

项目环境：

```text
Spring Boot 3.2.12
Java 17
MyBatis-Plus 3.5.9
Spring WebFlux WebClient
DeepSeek 官方 API
```

---

## 2. 原始问题

新接口：

```http
POST /api/ai/conversations/{conversationId}/messages
Accept: text/event-stream
```

调用 DeepSeek 后，请求能够完成，但客户端只收到 `usage` 和 `done`，没有任何有效 `delta`：

```text
event: usage
data: {"inputTokens":0,"outputTokens":0}

event: done
data: {"messageId":"..."}
```

应用指标同时显示：

```text
inputTokens=0
outputTokens=0
ttf=0ms
status=completed
```

这意味着上游请求完成了，但所有 DeepSeek 内容分片和 usage 都没有成功进入编排器。

---

## 3. 已确认的真实根因

真实 DeepSeek V4 Flash 流式 API 返回的分片除了项目 DTO 已声明的字段，还包含：

```json
{
  "id": "...",
  "object": "chat.completion.chunk",
  "created": 1786446784,
  "model": "deepseek-v4-flash",
  "system_fingerprint": "...",
  "choices": [
    {
      "index": 0,
      "delta": {
        "role": "assistant",
        "content": ""
      },
      "logprobs": null,
      "finish_reason": null
    }
  ]
}
```

原 `DeepSeekStreamChunk` 没有声明：

```text
object
created
system_fingerprint
logprobs
```

同时 DTO 没有配置忽略未知字段，因此 Jackson 抛出：

```text
UnrecognizedPropertyException:
Unrecognized field "object" (class DeepSeekStreamChunk)
```

旧版 `parseSseLine()` 捕获 `JsonProcessingException` 后直接返回 `null`，导致所有响应分片被静默丢弃。最终表现为：

```text
没有 delta
没有 usage
没有明显上游异常
空字符串却被保存为 completed
```

因此，问题的核心不是 DeepSeek 没有返回内容，也不是模型或 API Key 错误，而是：

```text
响应 DTO 不允许未知字段
+ 解析异常被静默吞掉
= 所有 SSE 分片丢失
```

---

## 4. 已完成的修复

### 4.1 DeepSeek 响应 DTO 前向兼容

文件：

```text
src/main/java/com/xzh/friendxxx/ai/client/DeepSeekStreamChunk.java
```

以下类型已经增加：

```java
@JsonIgnoreProperties(ignoreUnknown = true)
```

包括：

- `DeepSeekStreamChunk`；
- `DeepSeekStreamChunk.Choice`；
- `DeepSeekStreamChunk.Delta`；
- `DeepSeekStreamChunk.Usage`。

这样 DeepSeek 后续增加非关键响应字段时，不会导致整条聊天流失效。

### 4.2 改为原始 DataBuffer 解析 SSE

文件：

```text
src/main/java/com/xzh/friendxxx/ai/client/DeepSeekChatClient.java
src/main/java/com/xzh/friendxxx/ai/client/SseFrameDecoder.java
```

不再使用：

```java
bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
```

当前实现使用：

```java
bodyToFlux(DataBuffer.class)
```

然后通过有状态 `SseFrameDecoder` 解析 SSE frame。

解析器支持：

- 一个 SSE frame 跨多个 DataBuffer；
- 一个 DataBuffer 包含多个 SSE frame；
- `\n\n` 分隔符；
- `\r\n\r\n` 分隔符；
- `data:` 行；
- 多行 `data:`；
- `: keepalive` 注释/心跳；
- `data: [DONE]`；
- DataBuffer 使用后的释放。

不能将实现退回为对单个 DataBuffer 简单执行：

```java
text.split("\n\n")
```

因为 TCP/DataBuffer 边界不等于 SSE 事件边界。

### 4.3 JSON 解析失败不再静默吞掉

旧实现遇到无法解析的 JSON 时记录 WARN 并返回 `null`，容易再次制造“请求成功但内容为空”的假象。

当前实现会抛出 `DeepSeekApiException`，使编排器进入失败处理并保留明确日志。

禁止恢复静默丢弃行为。

### 4.4 修正首内容 Token 超时

DeepSeek 第一个流式分片通常只有：

```json
{
  "delta": {
    "role": "assistant",
    "content": ""
  }
}
```

旧代码把“第一个 SSE 元素”当作“首 Token”，导致收到 role-only 分片后立即取消首 Token 超时。

当前逻辑等待第一个非空 `content`，并在每次重试时重新建立解析器和首内容 deadline。

### 4.5 扩展网络异常重试识别

当前重试识别包括：

- `DeepSeekApiException` 中可重试的状态；
- `IOException`；
- `TimeoutException`；
- `WebClientRequestException`；
- 递归检查异常 cause。

已经向下游发出有效内容后，不允许自动重新生成整段回复。

### 4.6 修正 usage 处理顺序

文件：

```text
src/main/java/com/xzh/friendxxx/ai/service/AiChatOrchestrator.java
```

现在每个 chunk 会先采集：

- 模型名；
- prompt token；
- completion token；

然后再判断是否产生 `delta` 事件。

### 4.7 空回复不再标记成功

流正常结束但 `fullContent` 为空时，现在会抛出错误：

```text
DeepSeek 流结束但未生成任何 content
```

编排器随后将 Assistant 消息标记为：

```text
failed
```

而不是：

```text
completed
```

空回复不会继续触发正常的摘要、长期记忆和关系更新流程。

---

## 5. 测试修复

### 5.1 原真实集成测试的问题

文件：

```text
src/test/java/com/xzh/friendxxx/ai/client/DeepSeekChatClientIntegrationTest.java
```

原测试标注了：

```java
@SpringBootTest
```

这会启动整个应用上下文，并触发遗留 DashScope 自动配置。没有 DashScope Key 时，测试在真正调用 DeepSeek 之前就会因为以下原因失败：

```text
DashScope API key must be set
```

该测试只需要手动构造 `DeepSeekChatClient`，不需要 Spring 容器，因此已经移除 `@SpringBootTest`。

当前真实 API 测试：

- 有 `DEEPSEEK_API_KEY` 时真实执行；
- 没有该环境变量时明确显示 skipped；
- 不依赖数据库、Redis、RabbitMQ、Elasticsearch 或 DashScope。

### 5.2 新增 SSE Frame 解析器测试

文件：

```text
src/test/java/com/xzh/friendxxx/ai/client/SseFrameDecoderTest.java
```

覆盖：

- JSON 跨网络分片；
- 单网络分片包含多个事件；
- `[DONE]`；
- CRLF；
- 心跳注释；
- 多行 data。

### 5.3 新增 WebClient 完整链路测试

文件：

```text
src/test/java/com/xzh/friendxxx/ai/client/DeepSeekChatClientStreamTest.java
```

使用本地 Reactor Netty HTTP Server 模拟 DeepSeek，覆盖：

```text
WebClient
→ DataBuffer
→ 跨分片 SSE
→ 未知响应字段
→ JSON DTO
→ 中文 content delta
→ usage
→ [DONE]
```

模拟响应中特意包含：

```text
object
created
logprobs
```

用于防止本次未知字段问题回归。

---

## 6. 已完成的真实 DeepSeek 官方 API 验证

使用临时环境变量：

```text
DEEPSEEK_API_KEY
```

对 DeepSeek 官方 API 运行了以下测试：

```text
DeepSeek V4 Pro 非流式调用
DeepSeek V4 Flash 流式调用
关闭 thinking 模式
content delta 累积
reasoning_content 不泄露
```

最终结果：

```text
Tests run: 6
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

其中包括：

```text
DeepSeekChatClientIntegrationTest：2 个真实官方 API 测试通过
DeepSeekChatClientStreamTest：1 个本地 WebClient 完整链路测试通过
SseFrameDecoderTest：3 个解析器测试通过
```

测试 Key 仅临时注入 Maven 子进程，没有写入源码、YAML、测试文件或 Git。

不要将任何真实 Key 添加到本文件或项目配置中。

---

## 7. 当前关键文件

| 文件 | 状态与用途 |
|---|---|
| `src/main/java/com/xzh/friendxxx/ai/client/DeepSeekChatClient.java` | 已修复，原始 DataBuffer SSE 客户端 |
| `src/main/java/com/xzh/friendxxx/ai/client/SseFrameDecoder.java` | 新增，有状态 SSE frame 解析器 |
| `src/main/java/com/xzh/friendxxx/ai/client/DeepSeekStreamChunk.java` | 已修复，忽略未知响应字段 |
| `src/main/java/com/xzh/friendxxx/ai/service/AiChatOrchestrator.java` | 已修复，usage 与空回复处理 |
| `src/test/java/com/xzh/friendxxx/ai/client/SseFrameDecoderTest.java` | 新增，解析器单测 |
| `src/test/java/com/xzh/friendxxx/ai/client/DeepSeekChatClientStreamTest.java` | 新增，本地完整流式链路测试 |
| `src/test/java/com/xzh/friendxxx/ai/client/DeepSeekChatClientIntegrationTest.java` | 已修复，真实官方 API 测试 |

---

## 8. 编码模型接下来要做的工作

### 8.1 启动完整应用做端到端验证

需要准备：

```text
DEEPSEEK_API_KEY
MySQL
Redis（或开发环境禁用相关限流）
必要的数据库迁移
```

验证流程：

```text
1. POST /api/auth/guest 获取 token
2. POST /api/ai/conversations 创建会话
3. POST /api/ai/conversations/{id}/messages 发送消息
4. 确认持续收到 delta
5. 确认 usage 非零
6. 确认 done 正常
7. 查询 ai_message，确认 Assistant 内容完整且状态 completed
8. 确认后台摘要、记忆和关系更新只在回复成功后执行
```

建议测试输入：

```text
在干嘛
```

预期不是固定文案，而是：

- 至少返回一个非空 delta；
- 最终回复自然、简短；
- Assistant 数据库内容与客户端合并内容一致；
- input/output tokens 大于 0（以官方响应实际提供为准）。

### 8.2 确认客户端断开取消上游请求

使用 curl 或前端主动中断 SSE，确认：

- WebClient 上游订阅被取消；
- Assistant 消息状态为 `partial` 或 `failed`；
- 不继续消耗完整输出 Token；
- 不错误执行完整回复后的后台任务。

### 8.3 补充空响应测试

建议为 `AiChatOrchestrator` 增加测试：

- 上游返回空 Flux；
- 上游只有 role-only chunk；
- 上游只有 usage，没有 content；
- 上游产生部分 content 后报错。

预期状态：

```text
无 content → failed
有部分 content 后失败 → partial
正常 content → completed
```

### 8.4 检查异步执行器

现有日志曾提示：

```text
SimpleAsyncTaskExecutor 不适用于生产
存在多个 TaskExecutor bean
没有名为 taskExecutor 的默认执行器
```

应配置专门的 AI 后台任务执行器，例如：

```text
aiTaskExecutor
```

并在摘要、记忆和关系服务的 `@Async` 上显式指定。

线程池需要：

- 有界队列；
- 明确核心线程数和最大线程数；
- 拒绝策略；
- 线程名前缀；
- 关闭时等待任务完成；
- 监控活跃线程、队列长度和拒绝次数。

### 8.5 处理 Redis 未启动时的开发体验

当前 `RateLimitInterceptor` 在本地 Redis 未启动时会持续输出连接异常。

编码模型应选择一种明确策略：

1. 开发环境提供 Redis；
2. 开发 Profile 可关闭 Redis 限流；
3. Redis 异常时采取受控降级，并限制日志频率。

生产环境不能无条件 fail-open。

### 8.6 检查遗留 DashScope 启动依赖

虽然任务书要求暂时保留旧 DashScope 实现作为回滚路径，但旧 `HelloworldController` 会要求 DashScope ChatClient Bean，导致没有 DashScope Key 时完整 Spring 容器无法启动。

需要实现条件装配，例如：

```text
app.ai.legacy-dashscope.enabled=false
```

只有显式开启旧实现时，才加载：

- 旧 `HelloworldController`；
- DashScope ChatModel；
- 旧 Memory Advisor。

默认应允许仅配置 DeepSeek Key 就正常启动新聊天模块。

注意：在新版端到端验证成功之前，不要直接删除旧实现。

---

## 9. 建议执行的测试命令

无真实 Key的本地测试：

```powershell
mvn "-Dtest=com.xzh.friendxxx.ai.**.*Test" test
```

真实 DeepSeek API 测试：

```powershell
$env:DEEPSEEK_API_KEY="通过安全方式注入"
mvn "-Dtest=DeepSeekChatClientIntegrationTest" test
Remove-Item Env:DEEPSEEK_API_KEY
```

禁止把真实 Key直接写进：

- `application.yml`；
- `application-dev.yml`；
- 测试源码；
- Maven 命令脚本；
- Git 提交；
- Markdown 文档。

---

## 10. 验收标准

后续编码完成后，至少满足：

```text
[ ] 完整应用只配置 DeepSeek Key即可启动
[ ] “在干嘛”等输入能够持续收到非空 SSE delta
[ ] 最终数据库回复与 SSE 合并结果一致
[ ] usage 能正确保存和返回
[ ] 空响应不会被标记 completed
[ ] 部分响应失败被标记 partial
[ ] 客户端断开能取消 DeepSeek 请求
[ ] 摘要、记忆和关系后台任务不阻塞主回复
[ ] AI 后台任务使用明确的有界线程池
[ ] 跨用户、跨角色、跨会话数据不会串联
[ ] 本地 AI 测试全部通过
[ ] 真实 DeepSeek V4 Pro/Flash 测试通过
[ ] 项目中不存在真实 API Key
```

---

## 11. 给编码模型的重要提醒

当前仓库存在较多未提交修改。本次继续工作时必须：

- 先执行 `git status`；
- 不使用 `git reset --hard`；
- 不用 checkout 覆盖用户改动；
- 不删除本次已经通过真实 API 验证的 SSE 修复；
- 不将 `SseFrameDecoder` 简化为无状态 split；
- 不恢复 JSON 解析异常静默丢弃；
- 不把真实 API Key 写入仓库；
- 每完成一个阶段都运行相关测试。

本次 SSE 内容丢失问题已经通过真实 DeepSeek 官方 API 验证修复。后续重点应转向完整应用端到端验证、遗留 DashScope 条件装配、异步执行器和异常状态测试，而不是再次重写已经通过验证的流解析器。

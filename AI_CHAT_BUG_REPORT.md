# 心事小屋 AI 拟人聊天重构 — 进展与问题报告

> 给高级模型看的排查文档。记录：已完成的工作、当前卡住的 bug、已做的排查、以及需要你判断的点。

---

## 1. 背景与目标

对后端项目 `F:\baib\Java_Backend_Universal_Template-main\friendxxx`（Spring Boot 3.2.12 + Java 17 + MyBatis-Plus 3.5.9）的 AI 聊天进行重构，任务书是仓库里的 `AI_PERSONA_CHAT_REFACTOR_SPEC.md`。

核心目标：
- 弃用 DashScope，改用 **DeepSeek 官方 API**（`deepseek-v4-pro` 生成 + `deepseek-v4-flash` 辅助认知任务）
- 分层人设 Prompt、情绪/意图/策略识别、长期记忆、会话摘要、关系状态
- 新的 `POST /ai/conversations/{id}/messages` 返回 **SSE 流式**响应
- 旧 DashScope 实现保留（回滚路径）

**当前卡点：新聊天接口的 SSE 流式内容（delta 事件）为空，但 usage/done 事件正常。** 请求确实到达了 DeepSeek（total 耗时 1564ms），也收到了流，但内容增量全部丢失。

---

## 2. 已完成并通过验证的部分

- ✅ DeepSeek 客户端（`com.xzh.friendxxx.ai.client.DeepSeekChatClient`）：非流式、流式、thinking 参数、超时、重试、reasoning 过滤
- ✅ 5 张新表（`ai_character / ai_conversation / ai_message / ai_memory / ai_relationship_state`）已建并插入种子角色
- ✅ 编排层：`AiChatOrchestrator`（鉴权→记忆→策略→Prompt→V4 Pro 流式→持久化→后台任务）
- ✅ 记忆/摘要/关系/策略识别等服务已实现，18 个单元测试通过，编译通过
- ✅ 接口链路：`POST /api/auth/guest` 拿 token → `POST /api/ai/conversations` 建会话 → `POST /api/ai/conversations/{id}/messages` 返回 200

---

## 3. 当前 Bug：SSE 内容 delta 丢失（内容为空）

### 3.1 现象

用 curl/Invoke-WebRequest 直连 DeepSeek 官方 API 是**正常的**：

```json
data: {"id":"...","choices":[{"index":0,"delta":{"role":"assistant","content":""},...}]}
data: {"id":"...","choices":[{"index":0,"delta":{"content":"今天"},"finish_reason":null}]}
data: {"id":"...","choices":[{"index":0,"delta":{"content":"面试"},"finish_reason":null}]}
...
data: {"id":"...","choices":[{"index":0,"delta":{"content":""},"finish_reason":"stop"}],"usage":{...}}
data: [DONE]
```

但经过项目的 `DeepSeekChatClient.stream()` 转发后，**每个 chunk 的 contentDelta() 返回 null**，`AiChatOrchestrator` 里 `fullContent` 一直是空，SSE 只输出：

```
event: usage   data: {"type":"usage","data":{"outputTokens":0,"inputTokens":0}}
event: done    data: {"type":"done","data":{"messageId":"..."}}
```

注意：**inputTokens/outputTokens 也是 0**，说明最后带 `usage` 的那个 chunk 也没有被解析出来 —— 但 `done` 事件却正常触发了（说明流走完了）。

### 3.2 应用日志（关键线索）

```
INFO  ... c.x.f.ai.service.AiChatOrchestrator : [ai-metrics] model=deepseek-v4-pro, thinking=false,
      inputTokens=0, outputTokens=0, ttf=0ms, total=1564ms, status=completed
```

- `model=deepseek-v4-pro`（正确）
- `ttf=0ms` → **说明 `firstTokenAt[0]` 从未被赋值** → 印证 `contentDelta()` 一直返回 null/空
- `total=1564ms` → 请求确实发起并完成了
- 没有任何"忽略无法解析的 SSE 数据行" WARN（说明不是 JSON 解析失败，而是 **chunk 对象本身 contentDelta 为 null**，或 **压根没收到 chunk**）

### 3.3 代码现状

客户端解析链路（`DeepSeekChatClient.stream()`）：

```java
Flux<DeepSeekStreamChunk> source = webClient.post()
        .uri(CHAT_PATH)
        .bodyValue(request)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .retrieve()
        .onStatus(HttpStatusCode::isError, this::mapError)
        .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
        .mapNotNull(ServerSentEvent::data)          // 取 data 字符串
        .mapNotNull(this::parseSseLine)              // 反序列化为 DeepSeekStreamChunk
        .doOnNext(chunk -> { if (chunk.contentDelta() != null) emitted.set(true); });
```

`DeepSeekStreamChunk.contentDelta()`：

```java
public String contentDelta() {
    if (choices == null || choices.isEmpty() || choices.get(0).delta == null) {
        return null;
    }
    return choices.get(0).delta.content;
}
```

### 3.4 已做的排查与排除项

1. **不是 API Key / 模型名问题**：直连 curl 同一套参数（`thinking.type=disabled`，temperature 0.85 等）返回正常 content。
2. **不是超时问题**：`ttf=0` + `total=1564ms` + 无 TimeoutException 日志。
3. **不是 JSON 解析失败**：无 WARN。
4. **不是 orchestrator 的 flatMap 写法**：之前用 `.map(...)` 返回 null 曾报 `FluxMap: mapper returned a null value`，改成 `.flatMap(...)` + `Flux.empty()` 后 NPE 消失，但内容仍为空。
5. **不是 SSE 事件名问题**：`done`/`usage` 事件都能发出，说明 `Flux.concat(events, tail)` 里 tail 正常执行了，`events` 部分是空 Flux。

### 3.5 最可能的嫌疑（需要你判断）

**嫌疑 A：`bodyToFlux(ServerSentEvent<String>)` 中 `data()` 为 null 或为空字符串，被 `mapNotNull` 过滤掉了。**

DeepSeek 返回的 chunk 里，第一个分片是 `delta.role=assistant`（content 为空），后续分片才有 content。如果 Spring 的 `ServerSentEventHttpMessageReader` 对 `data:` 的解析有问题（例如把 `data: {json}` 整体作为一个事件、或 data 返回 null），那么所有 `mapNotNull(ServerSentEvent::data)` 都会把数据丢掉，最终流是空的 → 完全符合现象（无 WARN、无内容、无 usage、有 done）。

**嫌疑 B：`parseSseLine` 后 chunk 有值，但 `DeepSeekStreamChunk` 字段反序列化失败（字段名不匹配）。**

`DeepSeekStreamChunk` 用 Lombok `@Data`，字段名 `choices`/`delta`/`content`，与 JSON 一致。但 `DeepSeekRequest.Thinking` 等嵌套类用了 `@Builder` + `@NoArgsConstructor`，若反序列化时缺默认构造器可能报错——但那样会抛异常而非静默空。概率较低。

**嫌疑 C：DeepSeek 官方对 `ServerSentEvent` 规范的非标准实现**（如缺少 `event:` 行、多个 `data:` 合并、`retry:` 字段等），导致 Spring 的 SSE reader 不识别。

### 3.6 需要你重点回答

1. **最可能是哪个嫌疑？** 特别是：Spring 5/6 的 `bodyToFlux(ServerSentEvent<String>)` 在处理 DeepSeek 这种 `data: {...}\n\n` 流时是否可靠？有没有已知坑？
2. **最稳妥的替代实现**：是否应放弃 `ServerSentEvent` 类型，改成 `bodyToFlux(DataBuffer)` / `bodyToFlux(String)` + 手动按行解析 SSE（拆 `data:` 前缀、处理 `[DONE]`）？给出推荐代码。
3. **如何验证**：在客户端加一个 `.doOnNext` 打印 `ServerSentEvent::data` 的原始值，确认 data 是否为 null。这个验证方向对不对？
4. 若采用手动解析，重试逻辑（`retryWhen` 只在未发出内容时重试）和首 token 超时逻辑是否保留在 Flux 链上即可？

---

## 4. 相关文件路径

| 文件 | 说明 |
|------|------|
| `src/main/java/com/xzh/friendxxx/ai/client/DeepSeekChatClient.java` | **Bug 所在**，SSE 解析 |
| `src/main/java/com/xzh/friendxxx/ai/client/DeepSeekStreamChunk.java` | SSE 分片 DTO，`contentDelta()` |
| `src/main/java/com/xzh/friendxxx/ai/client/DeepSeekRequest.java` | 请求 DTO |
| `src/main/java/com/xzh/friendxxx/ai/service/AiChatOrchestrator.java` | 编排器，消费 `stream()` |
| `src/main/java/com/xzh/friendxxx/ai/service/AiUtilityService.java` | Flash 辅助任务（非流式，此链路正常） |
| `AI_PERSONA_CHAT_REFACTOR_SPEC.md` | 任务书 |
| `AI_CHAT_DELIVERY.md` | 交付说明（含已实现内容） |

---

## 5. 其他已知小问题（可稍后处理）

1. `RateLimitInterceptor` 依赖 Redis，本地 Redis 未启动时会刷 `RedisConnectionException` 日志（不影响本次 AI 链路验证）。
2. Spring 日志提示 `SimpleAsyncTaskExecutor` 不适用于生产（异步任务默认执行器），后续可配置 `taskExecutor`。
3. `@Async` 后台任务日志提示多个 `TaskExecutor` bean（`webSocketMessageExecutor` / `taskScheduler`）且无名为 `taskExecutor` 的，需要指定。

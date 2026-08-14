# 心事小屋 AI 拟人聊天重构 — 项目完成报告

> 日期：2026-08-11
> 分支：`feat/ai-persona-chat`（基于 dev）
> 关联文档：`AI_PERSONA_CHAT_REFACTOR_SPEC.md`（任务书）、`AI_CHAT_SSE_FIX_HANDOFF.md`（SSE 修复交接）、`AI_CHAT_BUG_REPORT.md`（问题排查）

---

## 1. 一句话总结

已完成从 DashScope 到 **DeepSeek 官方 API**（`deepseek-v4-pro` 生成 + `deepseek-v4-flash` 辅助认知）的拟人聊天重构，实现了分层人设、长期记忆、会话摘要、关系状态、SSE 流式、记忆管理接口，并通过**真实 DeepSeek 官方 API 端到端验证**。旧 DashScope 实现保留为回滚路径（默认关闭，条件装配）。

---

## 2. 架构

每轮聊天流程（符合任务书第 2 节）：

```text
用户消息
  ↓
JWT 鉴权 + 会话归属校验（越权 404）+ 幂等(clientMessageId)
  ↓
持久化用户消息
  ↓
加载角色定义 + 最近 15 轮完整轮次 + 会话摘要 + 关系状态 + 检索≤8条记忆
  ↓
V4 Flash 识别情绪/意图/回复策略（严格 JSON，失败降级默认策略）
  ↓
模型路由（是否思考模式）
  ↓
V4 Pro 流式生成（SSE delta 转发 + 服务端累积）
  ↓
保存 Assistant 消息（completed/partial/failed）+ usage
  ↓
异步：增量摘要 / 长期记忆提取 / 关系状态更新（独立线程池）
```

核心原则：禁止把完整历史无差别塞进上下文（记忆 + 摘要 + 最近轮次 + 策略分层组装）。

---

## 3. 新增/修改文件清单

### 3.1 DeepSeek 客户端（`ai/client/`）
| 文件 | 说明 |
|---|---|
| `DeepSeekChatClient.java` | WebClient 薄封装：非流式 + 流式、thinking 参数、首 Token 超时、指数退避重试、reasoning 过滤 |
| `SseFrameDecoder.java` | **有状态** SSE frame 解析器（跨 DataBuffer 分片、多帧合并、CRLF、心跳、多行 data、[DONE]） |
| `DeepSeekRequest.java` | OpenAI 兼容请求体，含 `thinking: {type}` 扩展字段 |
| `DeepSeekResponse.java` | 非流式响应 |
| `DeepSeekStreamChunk.java` | 流式分片，`@JsonIgnoreProperties(ignoreUnknown=true)`，`contentDelta()` 过滤 reasoning |
| `DeepSeekApiException.java` | 错误映射，`isRetryable()` |

### 3.2 配置（`ai/config/`）
| 文件 | 说明 |
|---|---|
| `DeepSeekProperties.java` | `app.ai.deepseek.*` 配置 record |
| `DeepSeekClientConfig.java` | WebClient Bean（连接 5s / 响应 120s，Authorization 注入） |
| `AsyncConfig.java` | `@EnableAsync` |
| `AiAsyncConfig.java` | `aiTaskExecutor` 有界线程池（core2/max4/queue200/CallerRuns） |

### 3.3 领域服务（`ai/service/`，职责边界符合任务书）
| 文件 | 说明 |
|---|---|
| `AiChatOrchestrator.java` | 每轮编排主流程；usage 采集、空回复→failed、部分内容→partial |
| `AiUtilityService.java` | V4 Flash 辅助任务统一入口（严格 JSON + 一次修复重试） |
| `PersonaPromptAssembler.java` | 分层 System Prompt（任务书 10.1 顺序） |
| `ReplyStrategyService.java` | 情绪/意图/策略识别（10 种策略白名单） |
| `ModelRoutingService.java` | 思考模式路由 |
| `ConversationContextService.java` | 最近 15 轮完整轮次（对齐轮次，不从半轮截断） |
| `ConversationSummaryService.java` | 异步增量摘要（新增≥18 条消息触发，乐观锁版本号防覆盖） |
| `LongTermMemoryService.java` | 异步记忆提取 + 冲突更新（同 key 提升置信度 / superseded / 低置信不覆盖高置信） |
| `MemoryRetrievalService.java` | 关键词召回 + 加权评分（relevance .45 + importance .25 + recency .15 + emotional .10 + access .05，≤8 条，同 key 去重） |
| `RelationshipStateService.java` | 关系状态（NEW→ACQUAINTED→FAMILIAR→CLOSE，变化±1 钳制，缓慢演进） |
| `AiConversationService.java` | 会话 CRUD + 归属校验 |
| `JsonParseUtils.java` | 辅助输出 JSON 解析（剥离代码块，失败返回 null 降级） |

### 3.4 接口层（`ai/controller/`）
| 接口 | 方法 |
|---|---|
| `POST /ai/conversations` | 创建会话（body: `{characterId}`） |
| `GET /ai/conversations` | 当前用户会话列表（分页） |
| `POST /ai/conversations/{id}/messages` | **SSE 流式**发送消息（body: `{content, clientMessageId}`） |
| `GET /ai/conversations/{id}/messages` | 游标分页查询消息 |
| `GET /ai/characters` | 可用角色列表 |
| `GET /ai/characters/{id}/memories` | 查看某角色的长期记忆 |
| `PATCH /ai/memories/{memoryId}` | 更正记忆 |
| `DELETE /ai/memories/{memoryId}` | 删除单条记忆 |
| `DELETE /ai/characters/{id}/memories` | 清空某角色全部记忆 |

SSE 事件：`start` / `delta` / `usage` / `done` / `error`；响应头 `Content-Type: text/event-stream;charset=UTF-8`。

### 3.5 数据层
- 实体：`model/entity/AiCharacter / AiConversation / AiMessage / AiMemory / AiRelationshipState`
- Mapper：`mapper/Ai*Mapper.java`（5 个）
- SQL 迁移：`sql/V3__ai_persona_chat.sql`（5 张表）、`sql/V4__seed_ai_character.sql`（默认角色"小鹿"，12 组正例 + 3 组反例 few-shot）

### 3.6 其他修改
- `RunApplication.java`：排除全部 7 个 DashScope auto-config
- `HelloworldController.java` / `AIConfig.java`：加 `@ConditionalOnProperty(app.ai.legacy-dashscope.enabled=true)`（默认关闭）
- `application.yml`：新增 `app.ai.deepseek.*`、`app.ai.legacy-dashscope.enabled`；DashScope key 改回环境变量引用
- `pom.xml`：+`spring-webflux`、+`reactor-netty-http`、+`spring-boot-starter-validation`
- `.env.example`：环境变量示例（无真实 key）
- `ai-eval/`：100 条评测用例 `ai_eval_cases.json` + A/B 评测脚本 `ai_eval.py`
- `AI_CHAT_DELIVERY.md`、`AI_CHAT_BUG_REPORT.md`、`AI_CHAT_SSE_FIX_HANDOFF.md`

---

## 4. 数据库（5 张新表）

| 表 | 关键字段 | 用途 |
|---|---|---|
| `ai_character` | identity/personality/speaking_style/interaction_rules/boundary/example_dialogues(JSON)/version/enabled | AI 角色定义 |
| `ai_conversation` | id(UUID)/user_id/character_id/summary/summary_version/last_message_at/is_deleted | 会话 + 摘要 |
| `ai_message` | client_message_id(UQ)/role/content/model/input/output_tokens/status | 完整聊天历史；**禁存 reasoning_content** |
| `ai_memory` | memory_type/key/content/importance/confidence/emotional_weight/status/expires_at/source_message_id | 长期记忆 |
| `ai_relationship_state` | (user_id,character_id) PK/familiarity/trust/interaction_count/current_stage | 关系状态 |

旧表 `ai_chat_memory` 与旧实现保留，未做破坏性变更。

---

## 5. 模型路由与参数

| 用途 | 模型 | 参数 |
|---|---|---|
| 最终回复（日常） | `deepseek-v4-pro` | thinking disabled, temp 0.85, top_p 0.95, freq_pen 0.25, pres_pen 0.15, max_tokens 1200 |
| 最终回复（复杂场景） | `deepseek-v4-pro` | thinking enabled（reasoning 服务端丢弃，不落库/不转发/不记录） |
| 辅助任务 | `deepseek-v4-flash` | 严格 JSON，temp 0.1，解析失败降级不阻塞主回复 |

思考模式路由触发：用户明确要求分析（≥40 字含分析/梳理）、≥150 字长篇倾诉、辅助模型判定 thinkingRequired。

---

## 6. 测试

### 6.1 单元/解析器测试（8 个类，本地无需 Key/中间件）
- `PersonaPromptAssemblerTest`：分层顺序、安全边界不可变、策略提示注入
- `MemoryRetrievalServiceTest`：过滤/排序/去重/≤8 条
- `JsonParseUtilsTest`：严格 JSON、代码块剥离、失败返回 null
- `ModelRoutingAndFilteringTest`：reasoning 过滤、思考模式路由
- `LongTermMemoryServiceTest`：冲突更新（同内容提置信度 / supersede / 低置信不覆盖 / 失败不抛）
- `SseFrameDecoderTest`：跨分片、多帧合并、CRLF、心跳、多行 data、[DONE]
- `DeepSeekChatClientStreamTest`：本地 HTTP Server 模拟完整流式链路（含未知字段，防回归）
- `DeepSeekChatClientIntegrationTest`：真实官方 API（有 `DEEPSEEK_API_KEY` 才执行，否则 skipped）

### 6.2 已通过的真实 DeepSeek 官方 API 验证
```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```
覆盖：V4 Pro 非流式、V4 Flash 流式、关闭思考、content delta 累积、reasoning_content 不泄露。Key 仅临时注入测试进程环境变量，未写入仓库。

---

## 7. 端到端验证结果（完整应用，只配 DeepSeek Key）

| 验收项 | 结果 |
|---|---|
| 完整应用仅配 DeepSeek Key 启动 | ✅ 通过（DashScope auto-config 全排除，旧实现默认关闭） |
| SSE delta 持续输出 | ✅ 13 个 delta，正常中文回复 |
| usage 非零 | ✅ inputTokens=1030, outputTokens=13 |
| DB Assistant 消息 | ✅ content 完整、"completed"、model=deepseek-v4-pro |
| 长期记忆自动提取 | ✅ PROFILE（称呼）/ PREFERENCE（吃火锅）/ EVENT（面试失落） |
| 关系状态更新 | ✅ interaction_count 递增，familiarity/trust 缓慢增长 |
| 记忆管理接口 | ✅ 查看 / 删除单条 / 清空全部均正常 |
| 会话归属校验 | ✅ 越权返回 404 |
| 幂等 | ✅ clientMessageId 重复时不重复调用模型 |

测试输入「在干嘛」→ 回复「刚在听歌发呆呢～你呢，这会儿在干嘛？」（人设一致、短句、自然）。

---

## 8. 安全与隐私（任务书第 14 节）

- ✅ 仓库无真实 API Key（DashScope 旧 key 已从配置移除，改环境变量引用；DeepSeek key 从未入库）
- ⚠️ 提醒：DashScope 旧 key `sk-5239...` 曾出现在 git 历史并已推送 GitHub 公开仓库，**需在平台轮换作废**
- ✅ `reasoning_content` 在服务端丢弃，不落库/不转发/不记录日志
- ✅ 记忆按 user_id+character_id 隔离，所有查询附带当前用户 ID
- ✅ 日志不记录完整 Prompt、API Key、Authorization Header
- ✅ 心理安全边界内置（不自称真人、不编造共同经历、不诱导隔离、高风险建议求助专业帮助）

---

## 9. 已知限制 / 未完成事项

1. **单用户并发生成限制未实现**（任务书 13.3）：同一用户 1~2 并发、单日 Token 用量限制暂未做。
2. **客户端断开取消上游请求**未专门测试（WebFlux 订阅取消会自然传播，但未加验证用例）。
3. **空响应/部分响应测试**：编排器对空回复→failed、部分→partial 的逻辑已实现，但缺专门单测（任务书 8.3）。
4. **Redis 未启动时的开发体验**：`RateLimitInterceptor` 依赖 Redis，本地无 Redis 时刷连接异常日志（不影响 AI 链路，生产需 fail-safe 或确认 Redis 存在）。
5. **记忆检索为关键词召回**，未接向量库（任务书允许第二阶段加 embedding + RRF）。
6. **多模态、文件、图片输入不在本阶段范围**（符合任务书）。
7. 旧 DashScope 实现**保留未删**（任务书 15 步：验收达标后才删除；当前通过开关禁用）。

---

## 10. 如何运行

```bash
# 1. 建表 + 种子角色
mysql -uroot -p friendxxx < sql/V3__ai_persona_chat.sql
mysql -uroot -p friendxxx < sql/V4__seed_ai_character.sql

# 2. 配置环境变量（必填 DEEPSEEK_API_KEY，参考 .env.example）
set DEEPSEEK_API_KEY=sk-xxx
set DEEPSEEK_BASE_URL=https://api.deepseek.com

# 3. 本地测试（无需 Key/中间件）
mvn "-Dtest=com.xzh.friendxxx.ai.**.*Test" test

# 4. 真实 API 测试
set DEEPSEEK_API_KEY=sk-xxx && mvn -Dtest=DeepSeekChatClientIntegrationTest test

# 5. 启动
mvn spring-boot:run   # 或 java -jar target/friendxxx-0.0.1-SNAPSHOT.jar
```

### 回滚
- 新实现全部位于 `com.xzh.friendxxx.ai.*` 与 5 张新表，与旧实现完全隔离。
- 回滚 = `git checkout dev`；旧 `/helloworld/*` 接口与 `ai_chat_memory` 表原样保留。
- 若需临时启用旧 DashScope：`APP_AI_LEGACY_DASHSCOPE_ENABLED=true` + 配置 DashScope key。

---

## 11. 关键设计决策回顾

1. **SSE 解析**：放弃 `bodyToFlux(ServerSentEvent)`，改用 `bodyToFlux(DataBuffer)` + 有状态 `SseFrameDecoder`。根因是 DTO 不容忍未知响应字段（`object/created/logprobs`），Jackson 抛 `UnrecognizedPropertyException` 被静默吞掉导致全部分片丢失。修复后 DTO 前向兼容 + 解析失败显式抛错。
2. **首 Token 超时**：等待"第一个非空 content"而非"第一个 SSE frame"（role-only frame 不算），且每次重试重建 deadline。
3. **条件装配**：默认只配 DeepSeek Key 即可启动；旧 DashScope 需显式开关。
4. **异步隔离**：后台认知任务走独立有界线程池，失败仅记日志，绝不阻塞主回复。

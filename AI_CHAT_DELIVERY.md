# AI 拟人聊天重构交付说明

按 `AI_PERSONA_CHAT_REFACTOR_SPEC.md` 实施。本文件说明：改了什么、怎么配置、怎么迁移、怎么测试、怎么回滚、已知限制。

## 1. 修改了哪些文件

### 新增（DeepSeek 客户端）
| 文件 | 说明 |
|------|------|
| `ai/client/DeepSeekChatClient.java` | WebClient 薄封装：非流式/流式、thinking 控制、超时、重试、reasoning 过滤 |
| `ai/client/DeepSeekRequest.java` | OpenAI 兼容请求体，含 thinking 扩展字段 |
| `ai/client/DeepSeekResponse.java` | 非流式响应 |
| `ai/client/DeepSeekStreamChunk.java` | SSE 分片，`contentDelta()` 已过滤 reasoning_content |
| `ai/client/DeepSeekApiException.java` | 错误映射，isRetryable() |
| `ai/config/DeepSeekProperties.java` | `app.ai.deepseek.*` 配置对象 |
| `ai/config/DeepSeekClientConfig.java` | WebClient Bean（连接超时 5s / 响应超时 120s） |
| `ai/config/AsyncConfig.java` | 开启 @Async（摘要/记忆/关系后台任务） |

### 新增（编排与领域服务）
| 文件 | 说明 |
|------|------|
| `ai/service/AiChatOrchestrator.java` | 每轮聊天编排：鉴权→记忆→策略→Prompt→V4 Pro 流式→持久化→后台任务 |
| `ai/service/AiUtilityService.java` | V4 Flash 辅助任务统一入口（严格 JSON + 一次修复重试） |
| `ai/service/PersonaPromptAssembler.java` | 分层 System Prompt 组装（规格 10.1 顺序） |
| `ai/service/ReplyStrategyService.java` | 情绪/意图/回复策略识别 |
| `ai/service/ModelRoutingService.java` | 思考模式路由 |
| `ai/service/ConversationContextService.java` | 最近 15 轮完整轮次 + 会话摘要 |
| `ai/service/ConversationSummaryService.java` | 异步增量摘要（版本号防覆盖） |
| `ai/service/LongTermMemoryService.java` | 异步记忆提取 + 冲突更新 |
| `ai/service/MemoryRetrievalService.java` | 关键词召回 + 加权评分（规格 7.1 公式） |
| `ai/service/RelationshipStateService.java` | 关系状态（NEW→ACQUAINTED→FAMILIAR→CLOSE，缓慢变化） |
| `ai/service/AiConversationService.java` | 会话 CRUD + 归属校验（越权 404） |
| `ai/service/JsonParseUtils.java` | 严格 JSON 解析（剥离代码块，失败返回 null） |

### 新增（接口层）
| 文件 | 说明 |
|------|------|
| `ai/controller/AiConversationController.java` | POST/GET `/ai/conversations` |
| `ai/controller/AiMessageController.java` | POST SSE 发送消息、游标分页查询 |
| `ai/controller/AiMemoryController.java` | 记忆查看/更正/删除/清空 |
| `ai/controller/AiCharacterController.java` | 角色列表 |

### 新增（数据层）
- `model/entity/AiCharacter.java` / `AiConversation.java` / `AiMessage.java` / `AiMemory.java` / `AiRelationshipState.java`
- `mapper/Ai*Mapper.java`（5 个）

### 新增（测试与评测）
- `src/test/.../ai/service/*Test.java`（5 个测试类，18 个用例）
- `ai-eval/ai_eval_cases.json`（100 条固定评测用例）
- `ai-eval/ai_eval.py`（A/B 评测采集脚本）

### 修改
- `pom.xml`：+spring-webflux、+reactor-netty-http、+spring-boot-starter-validation
- `application.yml`：+`app.ai.deepseek.*`；移除 DashScope 硬编码 Key（改回 `${DASHSCOPE_API_KEY:}`）

### 保留（规格第 15 步：验收前禁止删除）
- 旧 `HelloworldController.java`、`AIConfig.java`、`spring-ai-alibaba-starter-dashscope` 依赖、`ai_chat_memory` 表

## 2. 新增了哪些表
见 `sql/V3__ai_persona_chat.sql`（5 张表）与 `sql/V4__seed_ai_character.sql`（默认角色种子）。

## 3. 如何配置 DEEPSEEK_API_KEY
1. 在 DeepSeek 开放平台创建 API Key。
2. 通过环境变量注入，禁止写进仓库：
   - Windows（临时）：`set DEEPSEEK_API_KEY=sk-xxx` 或在 IDE Run Configuration 配置。
   - 生产：通过部署平台的密钥管理注入。
3. 参考 `.env.example`。

## 4. 如何运行数据库迁移
```bash
mysql -uroot -p friendxxx < sql/V3__ai_persona_chat.sql
mysql -uroot -p friendxxx < sql/V4__seed_ai_character.sql
```

## 5. 如何运行测试
```bash
# 单元测试
mvn test -Dtest="PersonaPromptAssemblerTest,MemoryRetrievalServiceTest,JsonParseUtilsTest,ModelRoutingAndFilteringTest,LongTermMemoryServiceTest"

# 集成测试（需 DEEPSEEK_API_KEY）
mvn test -Dtest="DeepSeekChatClientIntegrationTest"

# A/B 评测
python ai-eval/ai_eval.py --base http://localhost:8080/api --token <jwt> --character-id 1
```

## 6. 如何回滚到旧实现
- 新实现全部位于 `com.xzh.friendxxx.ai.*` 与 5 张新表，与旧实现完全隔离。
- 回滚 = 关闭新接口或回退到旧分支：
  ```bash
  git checkout dev   # 回到重构前分支
  ```
- 旧 `HelloworldController`（/helloworld/*）与 `ai_chat_memory` 表原样保留，随时可用。
- 数据库回滚：仅删除 5 张新表即可，不影响旧表。

## 7. 已知限制
- 记忆检索第一版未接入向量库（规格允许第二阶段加 embedding + RRF）。
- 单用户并发生成限制暂未实现（规格 13.3 待补）。
- 单日 Token 用量限制未实现（规格 13.3 待补）。
- 关系数值变化幅度依赖 Flash 建议，已做 ±1 钳制。
- 实测延迟取决于服务器与 DeepSeek API 网络，需按规格压测确定阈值。

## 8. 2026-08-11 端到端验证结果
- [x] 完整应用只配置 DeepSeek Key 即可启动（DashScope auto-config 已全部排除，旧实现通过 app.ai.legacy-dashscope.enabled=true 条件装配）
- [x] SSE delta 持续输出（13 个 delta，正常中文回复）
- [x] usage 非零（input=1030, output=13）
- [x] 数据库 Assistant 消息 content 完整、status=completed
- [x] 长期记忆自动提取（PROFILE/PREFERENCE/EVENT）
- [x] 关系状态缓慢更新（familiarity/trust/interaction_count）
- [x] 记忆查看/删除接口正常
- [x] 异步任务线程池 aiTaskExecutor 已配置


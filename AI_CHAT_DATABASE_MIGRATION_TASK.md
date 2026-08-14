# AI Chat 数据库迁移任务书

> 目标：在不破坏现有业务数据的前提下部署 AI 聊天表结构，并为“前端可管理、可发布、可回滚的人设”增加版本化存储。

## 1. 已有迁移

必须按顺序执行并登记：

1. `sql/V3__ai_persona_chat.sql`：创建 `ai_character`、`ai_conversation`、`ai_message`、`ai_memory`、`ai_relationship_state`。
2. `sql/V4__seed_ai_character.sql`：写入默认角色“小鹿”。
3. `sql/V5__ai_message_reply_link.sql`：为 Assistant 消息增加 `reply_to_message_id`。

这些脚本当前不是由 Flyway 自动管理的目录结构，编码模型必须先确认目标环境是否已有迁移工具/迁移历史表，禁止凭文件名猜测已经执行。

## 2. 新增 V6：人设版本化

新增 `sql/V6__ai_character_versioning.sql`。推荐保留 `ai_character` 作为稳定角色主表，把可发布内容迁移到版本表，避免直接覆盖线上人设且无法回滚。

### 2.1 `ai_character` 主表调整

建议新增：

- `description VARCHAR(500)`：角色列表公开简介，不能再直接把完整 `identity_prompt` 当简介返回。
- `avatar_url VARCHAR(1000)`：角色头像。
- `active_version_id BIGINT NULL`：当前已发布版本。
- `draft_version_id BIGINT NULL`：当前草稿版本，可选。
- `publish_time DATETIME NULL`。
- `created_by BIGINT NULL`、`updated_by BIGINT NULL`。
- `version INT` 保留作乐观锁字段或逐步淘汰，但必须明确语义。

### 2.2 新建 `ai_character_version`

至少包含：

| 字段 | 说明 |
|---|---|
| `id BIGINT` | 主键 |
| `character_id BIGINT` | 所属角色 |
| `version_no INT` | 角色内递增版本号，与 `character_id` 建唯一键 |
| `status VARCHAR(20)` | `DRAFT/PUBLISHED/ARCHIVED` |
| `identity_prompt TEXT` | 身份层 |
| `personality_prompt TEXT` | 性格层 |
| `speaking_style_prompt TEXT` | 语言风格层 |
| `interaction_rules_prompt TEXT` | 互动规则层 |
| `boundary_prompt TEXT` | 安全边界层 |
| `example_dialogues JSON` | 正反例对话 |
| `change_note VARCHAR(500)` | 修改说明 |
| `created_by BIGINT` | 操作者 |
| `create_time/update_time/publish_time` | 审计时间 |

增加 `UNIQUE(character_id, version_no)`、`INDEX(character_id, status)`。外键是否启用遵循项目现有规范；即使不用物理外键，服务层也必须维护引用完整性。

### 2.3 数据回填

1. 对每条现有 `ai_character` 创建版本 1，原五段 prompt 和 `example_dialogues` 原样复制。
2. 将版本 1 标为 `PUBLISHED`，写回 `active_version_id`。
3. 回填后校验记录数、JSON 合法性、必填 prompt 非空。
4. 此阶段不要删除主表旧 prompt 列，先保持双读兼容作为回滚路径；完成应用发布和稳定观察后再单独申请清理迁移。

## 3. 数据一致性规则

- 一个角色只能有一个当前 `active_version_id`。
- 草稿保存不得影响线上聊天；只有发布事务提交后，新会话请求才读取新版本。
- 发布必须使用单一数据库事务：锁定角色、校验草稿、归档旧版本、发布新版本、切换 `active_version_id`、写审计记录。
- 回滚不是覆盖数据，而是基于旧版本复制出一个新的发布版本，保证历史可追踪。
- 已开始的请求可继续使用它加载到的版本；后续请求读取最新发布版本。
- 禁止把 DeepSeek API Key 或其他 Secret 存入这些表。

## 4. 迁移执行流程

1. 全量备份并验证备份可恢复。
2. 在与生产结构一致的预发布数据库演练 V3→V6。
3. 执行前查询目标表、列、索引和种子数据状态，记录基线。
4. V3/V4 使用幂等语句；V5/V6 若目标列已存在必须停止核查，不能盲目重复执行。
5. 执行结构迁移与回填，运行校验 SQL。
6. 发布兼容新旧结构的后端。
7. 验证聊天、角色读取、草稿保存、发布和回滚。
8. 观察稳定后再讨论归档旧表 `ai_chat_memory`、`user_prompt`，本任务不得删除它们。

## 5. 必须提供的校验 SQL

编码模型需在报告中提供并执行至少以下检查：

- 五张业务表及 V5 字段/索引存在。
- 每个启用角色都有 `active_version_id`。
- `active_version_id` 指向同角色的 `PUBLISHED` 版本。
- 不存在重复 `(character_id, version_no)`。
- 所有已发布版本五段 prompt 非空，`example_dialogues` 为合法 JSON 或 NULL。
- 消息、会话、记忆、关系表迁移前后行数未意外减少。
- 默认角色“小鹿”可被后端读取并完成一次聊天。

## 6. 回滚方案

- 迁移前保留可恢复备份。
- 应用回滚时继续从 `ai_character` 旧 prompt 列读取；V6 首次发布期间保持这些列同步且不删除。
- 不通过 DROP TABLE/DROP COLUMN 回滚。
- 若 V6 应用发布失败，切回旧应用并将流量恢复；新增版本表和列可以暂时保留，不影响旧代码。

## 7. 自动化测试要求

- V6 在空库执行成功。
- V3→V6 顺序执行成功。
- 带现有角色数据的升级成功且内容无损。
- 发布事务并发测试：两个管理员同时发布时只有一个成功，另一个收到版本冲突。
- 草稿不影响聊天、发布立即生效、旧版本可回滚。
- 全部 43 个现有 AI 测试继续通过。

## 8. 交付物与完成定义

- `sql/V6__ai_character_versioning.sql`（以及项目采用迁移框架后的等价正式迁移）。
- 数据库迁移/回滚操作手册。
- `AI_CHAT_DATABASE_MIGRATION_REPORT.md`，包含预检查、备份位置说明、执行记录、校验结果、性能影响和回滚演练结果。
- 不丢数据、可回滚、线上聊天成功、人设版本一致性测试全部通过后才算完成。


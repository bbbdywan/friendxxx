# AI Chat 部署配置任务书

> 目标：把已完成的 DeepSeek V4 Flash AI 聊天安全部署到目标环境。本任务只处理运行环境配置，不修改人设内容，也不把任何密钥暴露给前端。

## 1. 当前基线

- 后端：Spring Boot / Java 17。
- 唯一 AI 模型：`deepseek-v4-flash`，主聊天与辅助任务均使用该模型。
- 配置前缀：`app.ai.deepseek`。
- 配置来源：`src/main/resources/application.yml` 中的环境变量占位符。
- AI 模块测试基线：43 个测试，0 失败，2 个真实 API 测试默认跳过。
- 前端不得直接调用 DeepSeek，`DEEPSEEK_API_KEY` 只能存在于服务端。

## 2. 必须提供的生产环境变量

| 环境变量 | 必填 | 建议值/说明 |
|---|---:|---|
| `DEEPSEEK_API_KEY` | 是 | 从 Secret Manager、容器 Secret 或 CI/CD Secret 注入，禁止写入仓库、镜像和前端 |
| `DEEPSEEK_BASE_URL` | 是 | `https://api.deepseek.com` |
| `DEEPSEEK_CHAT_MODEL` | 是 | `deepseek-v4-flash` |
| `DEEPSEEK_UTILITY_MODEL` | 是 | `deepseek-v4-flash` |
| `JWT_SECRET` | 是 | 独立生产密钥，至少 32 字节，不得使用默认值 |
| `APP_CORS_ALLOWED_ORIGINS` | 是 | 生产前端域名白名单，多个来源按项目现有解析格式填写 |
| `APP_RATE_LIMIT_ENABLED` | 是 | `true` |
| 数据库连接变量 | 是 | 按现有生产配置注入，使用最小权限账号 |
| Redis 连接变量 | 是 | 必须可用；AI 请求锁和并发控制依赖 Redis |

阿里云 OSS 等非 AI 配置沿用项目现有生产部署方式，不在本任务中重构。

## 3. 编码模型执行项

1. 检查 `application-prod.yml`、启动脚本和部署目录，确保所有生产参数均通过环境变量或 Secret 注入。
2. 更新 `.env.example`：只保留无效示例值或空值，不得出现真实 Key。
3. 增加启动时配置校验：生产环境下 `DEEPSEEK_API_KEY` 为空、模型不是 `deepseek-v4-flash`、JWT 仍为默认值时应快速失败并给出明确日志。
4. 日志中禁止打印 API Key、Authorization 请求头、完整 system prompt、长期记忆和用户完整聊天正文。
5. 保持当前超时基线：连接 5 秒、首 Token 30 秒、总响应 120 秒；如调整必须记录理由。
6. 配置生产 CORS，只允许正式前端域名，不使用 `*` 与凭证组合。
7. 配置健康检查：数据库、Redis 必须纳入 readiness；DeepSeek 不应在每次健康检查中真实扣费调用。
8. 给出 Docker/Systemd/现有部署方式对应的变量清单和回滚步骤，不把 Secret 烘焙进镜像。

## 4. 安全硬性要求

- 浏览器、前端构建变量、接口响应、Swagger 示例中都不得出现 DeepSeek Key。
- 管理后台只能编辑“业务人设配置”，不能读取或修改 API Key。
- 若此前测试 Key 曾在聊天、日志或仓库出现，上线前必须在 DeepSeek 控制台吊销并创建新 Key。
- 生产数据库和 Redis 不开放公网，或至少使用网络白名单、TLS/强密码和最小权限。

## 5. 验收步骤

```bash
mvn "-Dtest=com.xzh.friendxxx.ai.**.*Test" test
```

随后在预发布环境验证：

1. 未配置 Key 时应用拒绝以生产模式启动。
2. 正确配置后应用启动成功，数据库和 Redis readiness 正常。
3. 登录后创建会话并发送“在干嘛”，SSE 顺序为 `start -> delta... -> usage -> done`。
4. 数据库 Assistant 消息状态为 `completed`，模型为 `deepseek-v4-flash`。
5. 浏览器网络面板和服务日志中均不存在 DeepSeek Key。
6. 限流、重复 `clientMessageId`、客户端取消 SSE 均符合现有测试约定。

## 6. 交付物

- 更新后的 `.env.example`、生产配置/部署脚本（如确需修改）。
- `AI_CHAT_DEPLOYMENT_CONFIGURATION_REPORT.md`，逐项记录变量名、注入位置、验证结果和回滚方法，但不得记录变量真实值。

## 7. 完成定义

- 生产 Secret 安全注入且无泄漏。
- 生产环境只能使用 DeepSeek V4 Flash。
- 数据库、Redis、CORS、JWT、限流配置经过预发布验证。
- AI 模块全部自动化测试通过，真实端到端流式聊天通过。


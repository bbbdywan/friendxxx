# AI 前端接入与人设管理 — 交付报告

> 日期：2026-08-11
> 依据任务书：`AI_CHAT_FRONTEND_PERSONA_ADMIN_TASK.md`
> 后端：`friendxxx`（Spring Boot 3.2 + Java 17 + MyBatis-Plus）
> 前端：`new-project-name`（Vue 3 + Vite + Pinia + Vant 4）

---

## 1. 交付总结

达成任务书核心目标：**管理员在前端修改草稿 → 预览 → 发布 → 无需发版立即生效 → 可回滚**，且普通用户无越权能力。旧 `/helloworld/*` 接口未重新引入。

---

## 2. 后端实现

### 2.1 数据库迁移
| 文件 | 内容 |
|---|---|
| `sql/V6__ai_character_versioning.sql` | `ai_character` 加 `description/avatar_url/active_version_id/draft_id`；新增 `ai_character_version`（不可变版本）、`ai_character_draft`（每角色一条草稿）、`ai_character_audit`（审计） |
| `sql/V7__migrate_existing_characters_to_versioning.sql` | 存量角色数据迁移：生成 version 1（published）+ 草稿 + 主记录指针 |

### 2.2 版本化人设服务 `ai/service/AiCharacterVersionService.java`
- 草稿保存（乐观锁 `expectedVersionNo`，冲突抛 409 带当前版本）
- 发布：草稿固化为新 version，原子切换 `active_version_id`
- 回滚：以历史 version 内容生成新 version 并发布（旧版本保留可审计）
- 启用/停用；审计记录（create/save_draft/publish/rollback/enable/disable）
- 校验：五段 prompt 必填 + 总字符 ≤20000 + 安全边界非空 + 控制标记注入防护 + 示例 type 仅 positive/negative
- 兼容未版本化的历史角色（读主记录字段兜底）

### 2.3 管理 API `ai/controller/AiCharacterAdminController.java`（前缀 `/admin/ai/characters`）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/ai/characters` | 管理列表（启用状态/当前版本/有无草稿） |
| POST | `/admin/ai/characters` | 新建角色（默认停用，生成 v1 基线） |
| GET | `/admin/ai/characters/{id}` | 详情：线上版本 + 草稿 + 版本历史 |
| PUT | `/admin/ai/characters/{id}/draft` | 保存草稿（乐观锁 409） |
| GET | `/admin/ai/characters/{id}/versions` | 版本历史 |
| POST | `/admin/ai/characters/{id}/preview` | 草稿隔离预览（SSE，不落库） |
| POST | `/admin/ai/characters/{id}/publish` | 发布（versionId/expectedVersionNo/changeNote） |
| POST | `/admin/ai/characters/{id}/rollback` | 回滚到历史版本 |
| PATCH | `/admin/ai/characters/{id}/enabled` | 启用/停用 |

### 2.4 权限 `interceptor/AdminInterceptor.java`
- 拦截 `/admin/**`，`userRole == 1` 判定管理员（与 User 实体注释一致）
- 未登录 → HTTP 401（JwtInterceptor 先拦截）；普通登录用户 → HTTP 403
- 在 `WebMvcConfiguration` 注册（jwt 之后）
- `AuthController.toUserVO` 补充返回 `userRole`，前端据此显隐管理入口

### 2.5 预览隔离 `ai/service/AiPreviewService.java`
- 用草稿内容 + 固化安全底线组装 prompt，调用 DeepSeek 流式
- 返回与正式聊天一致的 SSE 协议（start/delta/usage/done/error）
- 不写入 ai_message / ai_memory / ai_relationship_state / ai_conversation

### 2.6 热更新 `AiChatOrchestrator`
- 每轮请求调用 `resolveCharacter()` 读取 `active_version_id` 对应版本内容（请求生命周期内固定）
- 发布后新请求立即读到新版本；SSE 生成过程中不会中途切换

### 2.7 后端测试
```text
Tests run: 48, Failures: 0, Errors: 0, Skipped: 2
```
新增 `AiCharacterVersionServiceTest`（5 个）：草稿不改变线上版本、乐观锁 409、发布生成新版本并切换 active、回滚生成可审计新版本、非法 prompt 拒绝。

### 2.8 后端端到端验证（真实 API + MySQL）
- 未登录访问管理 API → 401 ✓
- 普通用户(guest) 访问 → 403 ✓
- 管理员登录返回 `userRole=1` ✓
- 新建角色默认停用、普通用户不可见；启用后可见 ✓
- 保存草稿 → 线上 active 不变（草稿隔离）✓
- 乐观锁冲突 → body 409 带当前版本 ✓
- 发布 → activeVersionNo=2，新请求用新版人设 ✓
- 预览 → SSE start/delta/usage/done，不写入正式数据 ✓
- 回滚 → 生成 v3，线上恢复旧版，审计记录完整 ✓

---

## 3. 前端实现

### 3.1 新增/修改文件
| 文件 | 说明 |
|---|---|
| `src/api/sseParser.js` | 纯 SSE 解析函数（可单测） |
| `src/api/sse.js` | fetch + ReadableStream + SSE 分帧的流式客户端（POST + JSON + Bearer + AbortSignal） |
| `src/api/ai.js` | 重写为统一 AI 聊天 API（角色/会话/消息/SSE/记忆） |
| `src/api/aiAdmin.js` | 人设管理 API（列表/新建/详情/草稿/版本/预览/发布/回滚/启停） |
| `src/views/AiChatPage.vue` | 重写：新接口接入 + SSE 流式 + 停止生成 + 会话/历史恢复 + 幂等 clientMessageId |
| `src/views/AiCharacterAdminPage.vue` | 人设管理列表页（含编辑器抽屉） |
| `src/components/AiCharacterEditor.vue` | 编辑器：五段人设 + 示例对话结构化表单 + 与线上 diff + 预览 + 发布确认 + 版本历史 + 回滚 + 乐观锁冲突处理 |
| `src/components/PromptField.vue` | 人设字段输入（字数统计/用途说明） |
| `src/router/index.js` | 新增 `/ai-admin` 路由 |
| `src/views/HomePage.vue` | AI 助手旁新增"人设管理"入口（仅 `userRole===1` 可见） |
| `src/api/__tests__/sse.test.js` | SSE 解析器单测 |

### 3.2 SSE 客户端要点（任务书 5.2）
- `fetch` + `ReadableStream.getReader()`（EventSource 不适用 POST+JSON）
- 跨 TCP chunk 分帧、`\n\n`/`\r\n\r\n` 分隔、多行 `data:`、UTF-8 半字符（TextDecoder stream:true）
- `start/delta/usage/done/error` 全处理，`AbortController` 取消旧流
- 每次发送生成稳定唯一 `clientMessageId`；页面离开/切换/停止用 abort 取消
- 401 走登录失效、409/429 用户可理解提示、error 保留部分内容

### 3.3 人设管理台
- **角色列表**：名称/头像/启用状态/线上版本/有无草稿
- **编辑器**：基础信息 + 五段人设（每段字数/提示）+ 示例对话正反例结构化增删 + 与线上字段级 diff + 保存草稿（乐观锁冲突弹刷新）
- **预览抽屉**：预设测试语句（在干嘛/情绪低落/分享喜讯/求建议）+ 自定义输入，SSE 流式预览，不写正式数据
- **发布**：变更说明 + 确认弹窗 → 发布成功后刷新版本历史
- **版本历史与回滚**：每版本状态/操作人/说明，已发布版本可回滚确认
- 路由守卫仅体验层，真正权限由后端 AdminInterceptor 强制

### 3.4 前端测试与构建
```text
Vitest: Tests 11 passed (11)   # SSE 解析器单测
npm run build: built in ~5s     # 生产构建通过
```
单测覆盖：半个 JSON 跨 chunk、多事件同 chunk、多行 data、中文 UTF-8 边界、CRLF、心跳注释、[DONE]、非法 JSON。

---

## 4. 端到端验收场景（任务书 8.3）对照

| 场景 | 结果 |
|---|---|
| 1. 管理员修改语言风格并保存草稿 | ✅（保存草稿，线上不变） |
| 2. 普通用户聊天仍用旧版（草稿隔离） | ✅（active 版本独立于草稿） |
| 3. 草稿预览测试不增加正式数据 | ✅（预览不写 ai_message/memory/relationship） |
| 4. 管理员发布，不重启后端不重新构建前端 | ✅（发布即改 active_version_id，下个请求生效） |
| 5. 新一轮聊天立即体现新版风格 | ✅（orchestrator 每轮解析 active 版本） |
| 6. 管理员回滚，后续聊天恢复旧风格，版本审计完整 | ✅（回滚生成新版本，审计记录齐全） |
| 7. 普通用户无法访问管理接口 | ✅（HTTP 403） |

---

## 5. 安全（任务书 2/7/10）

- 前端不接触 DEEPSEEK_API_KEY / DB / JWT Secret / 任意模型 Base URL；模型固定服务端 `deepseek-v4-flash`
- 管理 API 不返回完整 prompt 给普通 `/ai/characters`（只返回 id/name/description）
- 服务端固化不可删除安全底线（`PersonaSafetyBoundary`），与 boundaryPrompt 组合，管理员不能覆盖
- prompt 控制标记注入防护（`<system>`/ignore previous/忽略以上所有指令 等）
- 示例内容按纯文本渲染，不使用 v-html/dangerouslySetInnerHTML
- 审计记录操作者/时间/变更说明，日志不打印完整 prompt

---

## 6. 已知限制 / 未决风险

1. **登录角色透传**：管理员入口依赖登录返回 `userRole`；历史登录会话若缓存无 userRole 需重新登录。
2. **前端聊天页**：`AiChatPage.vue` 重写后未在真实浏览器/移动端做人工验收（任务书 8.2 建议），建议预发布前桌面 + 移动端走查。
3. **新建角色默认停用**：需管理员手动启用后才对普通用户可见（符合隔离原则，但需在管理台明确提示）。
4. **回滚的 versionId**：前端回滚传版本列表中的真实 `versionId`（全局自增 ID），已联调确认。
5. **未实现自动保存草稿**（任务书 6.2 可选）；离开未保存页面提示未做。
6. **性能**：管理列表/详情每次实时查库，未加缓存；规模大时可优化。
7. **AdminInterceptor 依赖 userRole==1 约定**：项目原 WebSocket 推送用 `==3`，两处约定不一致，建议后续统一为常量。

---

## 7. 如何运行

```bash
# 后端
mysql -uroot -p friendxxx < sql/V6__ai_character_versioning.sql
mysql -uroot -p friendxxx < sql/V7__migrate_existing_characters_to_versioning.sql
set DEEPSEEK_API_KEY=sk-xxx
mvn spring-boot:run

# 前端
cd new-project-name
npm install
npm run dev        # 浏览器访问，默认 /api 反代

# 管理员登录（userRole=1 的账号）
# 进入首页 → AI助手旁"人设管理"（仅管理员可见）→ /ai-admin
```

---

## 8. 交付物清单

- 后端：V6/V7 迁移、版本服务、管理 API、权限拦截器、预览服务、热更新、48 个测试
- 前端：统一 AI API 层、SSE 解析器 + 单测、聊天页、人设管理台（列表/编辑器/预览/发布/回滚/版本历史）
- 本报告：`AI_CHAT_FRONTEND_PERSONA_ADMIN_REPORT.md`

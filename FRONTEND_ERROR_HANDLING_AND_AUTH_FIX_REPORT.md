# 全站前端错误展示与登录状态修复 —— 交付报告

> 日期：2026-08-12
> 前端：`F:\baib\new-project-name`
> 后端：`F:\baib\Java_Backend_Universal_Template-main\friendxxx`
> 服务器：111.228.10.5（已部署）

---

## 1. 完成情况总览

| 任务书项 | 状态 |
|---|---|
| 统一 ApiError + 解析器 + 认证失效去重 | ✅ |
| 重写 Axios 拦截器，删除 50000 误判 | ✅ |
| 修复 Pinia checkLoginStatus() | ✅ |
| 重写 SSE/fetch 错误链路 | ✅ |
| 后端统一错误结构 / 机器码 / traceId / JWT / Admin | ✅ |
| 全站页面 catch 改造 | ✅ |
| 前后端自动化测试 | ✅ |
| 部署 + 端到端验收 | ✅ |

---

## 2. 根因与修复对照

### 2.1 500 被误判为体验账户过期（核心）

**根因**：`request.js` 拦截器 `code===50000 && message==='系统内部错误'` → 清 Token + 弹"体验账户已过期" + 跳登录。`stores/user.js` 的 `checkLoginStatus()` 有同样逻辑。

**修复**：
- 删除两处 `50000` 推断。
- 新建 `src/utils/apiError.js`：统一 `ApiError`（status/code/message/fieldErrors/traceId/details），`isAuthError()` 仅当 `status===401` 或认证机器码。
- 新建 `src/utils/authExpired.js`：认证失效去重锁（并发 401 只弹一次、只跳一次）。
- `request.js` 只对 `isAuthError()` / `isGuestExpired()` 触发统一登出；403/404/409/429/500/网络错误一律不清 Token。
- 登录接口（`/auth/login`、`/auth/guest`）401 属于业务失败，排除在全局登出之外。

### 2.2 页面 catch 吞掉真实错误

**根因**：大量 `catch { showToast('加载失败') }`。

**修复**：`getApiErrorMessage(error, fallback)` 全站采用（内部走 `apiErrorMessage` → `toApiError`），改造页面：
AI 聊天、AI 人设管理、首页（点赞/评论）、我的动态、提示词设置、聊天列表/详情、互动通知、发布动态、搜索、用户资料、个人设置、标签编辑、管理后台。

### 2.3 SSE 错误不可结构化

**根因**：`sse.js` 非 2xx 时 `throw new Error('HTTP 500: {整段JSON}')`，调用方 `message.includes('401')`。

**修复**：
- 非 2xx 按 Content-Type 解析 JSON/文本为 `ApiError`。
- SSE `error` 业务事件转结构化 `ApiError`。
- `AbortError` 与网络错误区分。
- 流无 `done/error` 异常 EOF → `SSE_STREAM_INTERRUPTED`。
- AiChatPage 改用 `e.status`/`e.isAuthError()` 判断，删除 `includes('401')`。

---

## 3. 修改文件清单

### 前端

| 文件 | 说明 |
|---|---|
| `src/utils/apiError.js` | 新增：ApiError + 解析器（JSON/文本/HTML/网络/Abort） |
| `src/utils/authExpired.js` | 新增：认证失效去重 + 跳登录 |
| `src/utils/error.js` | 重写：`getApiErrorMessage`/`getApiFieldErrors` 走 ApiError |
| `src/api/request.js` | 重写：删除 50000 误判；规范化 ApiError；仅 401 触发登出；登录接口排除 |
| `src/api/sse.js` | 重写：结构化错误、SSE 业务 error、流中断检测 |
| `src/stores/user.js` | `checkLoginStatus`：仅 401 清状态；非 401 保持并抛出 |
| `src/router/index.js` | 守卫：非 401 错误保持登录不跳转 |
| `src/views/AiChatPage.vue` | SSE catch 结构化判断；onError toast |
| `src/views/AiCharacterAdminPage.vue` | catch 错误透传 |
| `src/components/AiCharacterEditor.vue` | 加载/发布/回滚 catch 透传 |
| `src/views/HomePage.vue` | 点赞/评论/详情 catch 透传 |
| `src/views/MyMomentsPage.vue` | 加载/点赞/评论/删除 catch 透传 |
| `src/views/PromptPage.vue` | 加载/保存/设置 catch 透传 |
| `src/views/ChatPage.vue` | 列表/删除 catch 透传 |
| `src/views/ChatDetail.vue` | 历史记录 catch 透传 |
| `src/views/InteractionsPage.vue` | 已读 catch 透传 |
| `src/views/DiscoverPage.vue` | 动态/删除/更新 catch 透传 |
| `src/views/SearchPage.vue` | 搜索 catch 透传 |
| `src/views/TagsEditPage.vue` | 保存 catch 透传 |
| `src/views/PostPage.vue` | 上传/发布/详情 catch 透传 |
| `src/views/NewProfilePage.vue` | 获取/保存/上传/退出 catch 透传 |
| `src/views/UserProfile.vue` | 资料 catch 透传 |
| `src/views/AdminPage.vue` | 列表/停用 catch 透传 |

### 后端

| 文件 | 说明 |
|---|---|
| `exception/ErrorResponse.java` | 新增：统一错误体 {code,message,data,traceId} |
| `exception/GlobalExceptionHandler.java` | 重写：真实 HTTP 状态；参数校验 fieldErrors；未知 500 生成 12 位 traceId；DeepSeek 稳定机器码（AI_AUTH_FAILED/AI_RATE_LIMITED/AI_UPSTREAM_UNAVAILABLE） |
| `interceptor/JwtInterceptor.java` | 未登录/无效/过期 → HTTP 401 + `{"code":"TOKEN_EXPIRED",...}` |
| `interceptor/AdminInterceptor.java` | 权限不足 → HTTP 403 + 统一 JSON |
| `ai/service/AiChatOrchestrator.java` | Broken pipe/客户端断流识别 → DEBUG 不刷 ERROR |

---

## 4. 后端错误协议（统一结构）

```json
{
  "code": "AI_UPSTREAM_TIMEOUT" | 400 | "TOKEN_EXPIRED" | "SYSTEM_ERROR",
  "message": "面向用户的安全文案",
  "data": { "fieldErrors": { "identityPrompt": "..." } } | null,
  "traceId": "01J..." | null
}
```

| 场景 | HTTP | code |
|---|---|---|
| 参数校验 | 400 | 400 + fieldErrors |
| 未登录/Token 无效/过期 | 401 | TOKEN_EXPIRED |
| 无权限 | 403 | 403 |
| 不存在 | 404 | 404 |
| 乐观锁冲突 | 409 | 409 |
| 限流/生成中 | 429 | 429 |
| AI 认证失败 | 502 | AI_AUTH_FAILED |
| AI 限流 | 502 | AI_RATE_LIMITED |
| AI 上游不可用 | 502 | AI_UPSTREAM_UNAVAILABLE |
| 未知异常 | 500 | SYSTEM_ERROR + traceId |

---

## 5. 测试结果

### 前端（vitest）

```
Test Files: 2 passed | Tests: 26 passed
```

`src/utils/__tests__/apiError.test.js` 覆盖：
- 401 是认证失效、清 Token、只触发一次
- 403/404/409/429/500 不是认证失效
- 业务 50000"系统内部错误"**不再**判定体验到期
- `GUEST_ACCOUNT_EXPIRED` 才判定体验到期
- 参数校验 fieldErrors 解析
- 网络错误 / AbortError / HTML 错误页 / 纯文本截断

### 后端（mvn test）

```
Tests run: 74, Failures: 0, Errors: 0, Skipped: 3 — BUILD SUCCESS
```

新增 `GlobalExceptionHandlerTest`（10 例）：
- BusinessException 400/409/429 → 对应 HTTP
- 参数校验 → fieldErrors
- 未知异常 → 500 + traceId(12位) + 不泄露 SQL/密钥
- DeepSeek 401/429/503 → AI_AUTH_FAILED/AI_RATE_LIMITED/AI_UPSTREAM_UNAVAILABLE

---

## 6. 端到端验收证据（服务器实测）

| 场景 | 实测结果 | Token |
|---|---|---|
| 无 Token 访问 /auth/me | HTTP 401 `{"code":"TOKEN_EXPIRED","message":"登录已过期，请重新登录"}` | 清并跳登录 |
| 无效 Token | HTTP 401 同上 | 清并跳登录 |
| 新建角色缺字段 | HTTP 400 + `fieldErrors` 五段全列 | 保留 |
| 不存在的会话 | HTTP 404 | 保留 |
| 管理员管理列表 | HTTP 200 | 保留 |
| 错误密码登录 | HTTP 500 + `{"code":100002,"message":"账号或密码错误"}`（前端已排除登录接口全局登出，显示真实原因） | 保留 |
| 客户端截断 SSE | 消息标 partial，**generating 残留 = 0** | 保留 |
| 全表 generating 残留 | 0 条 | — |

---

## 7. 说明与后续建议

1. **登录失败返回 HTTP 500（业务码 100002）而非 401**：由后端 UserService 抛出 BusinessException(100002) 导致。前端已排除登录接口全局登出并显示真实文案，功能正确；如需 401 语义需侵入 UserService，建议后续迭代。
2. **Tomcat 容器层 Broken pipe ERROR**：SSE 客户端断开时 `dispatcherServlet` 层仍会打印 ERROR（Tomcat 标准行为）。业务编排器已降级为 DEBUG，消息状态收尾正确（无 generating 残留）。如需完全消除容器层日志，可后续配置 ErrorReportValve/日志过滤器。
3. **体验账户到期**：后端暂无 `GUEST_ACCOUNT_EXPIRED` 业务码，前端已预留处理逻辑；待后端返回该码即可自动显示"体验账户已到期"。

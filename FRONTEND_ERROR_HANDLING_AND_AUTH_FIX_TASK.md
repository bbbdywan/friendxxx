# 全站前端错误展示与登录状态修复任务书

> 日期：2026-08-12  
> 前端：`F:\baib\new-project-name`  
> 后端：`F:\baib\Java_Backend_Universal_Template-main\friendxxx`  
> 优先级：P0  
> 目标：每个请求失败时尽可能显示真实、具体、可操作的原因；只有真正的认证失效才清除登录并跳转登录页。禁止再把任意系统错误显示成“体验账户已过期”。

---

## 1. 已确认根因

### 1.1 Axios 全局拦截器错误映射

`F:\baib\new-project-name\src\api\request.js` 当前包含：

```js
if (response.data.code === 50000 && response.data.message === '系统内部错误') {
  handleUserExpired()
}
```

`50000/系统内部错误` 代表服务端未知异常，不代表 Token 过期。该逻辑会把数据库错误、空指针、配置错误、第三方服务错误等全部误判为体验账户到期，并执行：

- 删除 `userInfo`。
- 删除 `accessToken`。
- 弹出“体验账户已过期”。
- 强制跳转 `/login`。

这是当前问题的直接根因。

### 1.2 Pinia Store 存在第二套相同误判

`src/stores/user.js` 的 `checkLoginStatus()` 仍使用：

```js
if (response.code === 50000 && response.message === '系统内部错误') {
  // 用户过期
}
```

因此只修 `request.js` 不够。两个位置都必须删除这一推断。

### 1.3 页面 catch 大量吞掉真实错误

多个页面仍写成：

```js
catch (e) {
  showToast('加载失败')
}
```

即使后端已经返回明确 message，页面也不会展示。项目虽已有 `src/utils/error.js`，但尚未全站采用。

### 1.4 SSE/fetch 错误不可结构化

`src/api/sse.js` 对非 2xx 响应执行：

```js
throw new Error(`HTTP ${response.status}: ${errBody}`)
```

这会把 JSON 错误体塞进字符串，调用方只能用 `message.includes('401')` 等脆弱方式判断，无法可靠读取 `status/code/message/fieldErrors/traceId`。

---

## 2. 正确的错误分类

| 场景 | 前端行为 | 是否清登录 |
|---|---|---:|
| HTTP 400 / 参数校验 | 显示后端具体 message；表单显示字段错误 | 否 |
| HTTP 401 / Token 无效或过期 | 提示登录失效，清理认证状态，跳转登录 | 是 |
| HTTP 403 | 显示“无权限”或后端具体原因，停留当前登录状态 | 否 |
| HTTP 404 | 显示资源/会话不存在的具体原因 | 否 |
| HTTP 409 | 显示版本冲突/状态冲突并提供刷新操作 | 否 |
| HTTP 429 | 显示限流或生成中原因 | 否 |
| AI 上游错误/超时 | 显示“AI 服务超时/繁忙”等具体原因，允许重试 | 否 |
| HTTP 500 未知异常 | 开发显示安全错误详情；生产显示 message + traceId | 否 |
| 网络断开/超时 | 显示网络异常或请求超时 | 否 |
| AbortController 主动取消 | 显示“已停止”或静默处理 | 否 |
| SSE 客户端主动断开/Broken pipe | 客户端按取消处理；服务端不误报业务失败 | 否 |

核心规则：**只有明确的 HTTP 401 或明确的认证错误码可以触发退出登录。禁止根据 500、错误文案或接口失败次数推断登录过期。**

---

## 3. 前端统一错误对象

新增或完善统一类型 `ApiError`，建议字段：

```js
class ApiError extends Error {
  status
  code
  message
  fieldErrors
  traceId
  details
  cause
}
```

统一解析以下响应：

```json
{
  "code": 400,
  "message": "参数校验失败",
  "data": {
    "fieldErrors": {
      "identityPrompt": "身份设定不能为空"
    }
  },
  "traceId": "..."
}
```

同时兼容项目历史接口的：

- `Result {code,message,data}`。
- `BaseResponse {code,message,data}`。
- 纯文本响应。
- Nginx/代理 HTML 错误页。
- 无响应的网络错误。

不得把完整 HTML 或超长服务器响应直接弹给用户；最多截取安全文本，并保留原始错误供控制台调试。

---

## 4. 重构 Axios 拦截器

修改 `src/api/request.js`：

1. 完全删除 `code===50000 && message==='系统内部错误' -> handleUserExpired()`。
2. 删除“体验账户已过期”与 `handleUserExpired` 的全局硬编码。
3. 正常 HTTP 2xx 但业务 `code` 非成功时，应统一 `Promise.reject(new ApiError(...))`，不要让每个页面重复检查 `res.code`。
4. HTTP 非 2xx 时解析响应 body 为 `ApiError`。
5. 只有 `status===401` 或后端约定的认证错误码触发统一认证失效流程。
6. 403、500、网络错误不得删除 Token。
7. 拦截器不应对所有错误自动弹 Toast；它负责规范化错误，页面/业务层决定展示，避免同一错误弹两次。
8. 认证失效处理需要去重锁：多个并发请求同时返回 401 时，只弹一次提示、只跳转一次。

建议统一成功码集合仅在迁移兼容期支持 `0/200`；逐步收敛为一种成功码。

---

## 5. 重构认证状态管理

修改 `src/stores/user.js`：

- 删除 `50000/系统内部错误 == 用户过期` 的判断。
- `checkLoginStatus()`：
  - 成功则更新用户。
  - 401 才清除 `userInfo/token/localStorage` 并返回 false。
  - 403/404/500/网络错误保持当前登录状态并向上抛出，交由调用方显示真实原因。
- 抽取唯一的 `clearAuthState()`，退出登录和 401 共用。
- 不要在解析用户资料失败、业务接口 500 时清除 accessToken。
- 路由守卫只根据当前认证状态和真正的 401 决定跳转。

“体验用户到期”如果确实存在，应由后端返回独立且明确的业务错误，例如：

```json
{
  "code": "GUEST_ACCOUNT_EXPIRED",
  "message": "体验账户已到期"
}
```

只有该错误才能显示体验账户到期文案；普通用户 Token 过期只显示“登录已过期”。

---

## 6. 重构 SSE/fetch 错误链路

修改 `src/api/sse.js`：

1. 非 2xx 时根据 `Content-Type` 解析 JSON 或文本，抛出结构化 `ApiError`。
2. 不再抛出 `HTTP 500: {整段 JSON}` 字符串。
3. SSE `error` 业务事件也转换为结构化错误，保留：
   - `code`
   - `message`
   - `traceId`
   - 已生成的部分内容
4. 401 交给统一认证失效处理；403/409/429/500 不退出。
5. `AbortError` 与真正网络错误区分。
6. 若流在没有 `done/error` 的情况下异常结束，返回明确的 `SSE_STREAM_INTERRUPTED`，保留 partial 内容并允许重试。
7. Broken pipe 通常是浏览器/curl 主动取消导致，前端只应把自己的 abort 视为正常停止。

AI 聊天页不得再使用：

```js
e.message.includes('401')
```

改为检查 `e.status` 或 `e.code`。

---

## 7. 全站页面改造

扫描所有 `catch`、失败分支和 `showToast('加载失败')`，逐步改成：

```js
catch (error) {
  showToast(getApiErrorMessage(error, '加载失败'))
}
```

优先覆盖所有当前业务页面：

- AI 聊天与 AI 人设管理。
- 首页动态、评论、点赞。
- 用户资料与设置。
- 发布动态、图片上传。
- 搜索。
- 私聊、群聊、互动通知。
- 管理后台。

表单错误优先显示在字段旁，Toast 只显示摘要。未知错误建议提供可复制的 traceId：

```text
创建角色失败：数据库迁移未完成
错误编号：a8f3...
```

不要把以下敏感信息展示给用户：

- Java 堆栈。
- SQL 语句和数据库连接信息。
- DeepSeek/OSS/JWT Key。
- 文件系统绝对路径。
- 完整 Prompt、记忆和聊天隐私。

---

## 8. 后端错误响应完善

当前新版 `GlobalExceptionHandler` 已开始返回真实 HTTP 状态，应继续统一：

1. 所有 Controller/Interceptor 使用一致 JSON 结构和 `Content-Type`。
2. JWT 拦截器：未登录/Token 无效/Token 过期统一 HTTP 401，但可用不同机器码区分。
3. AdminInterceptor：HTTP 403，不返回 200 + code 403。
4. Bean Validation：HTTP 400，并返回 `fieldErrors`。
5. BusinessException：映射到 400/401/403/404/409/429。
6. DeepSeek 异常：使用稳定机器码，例如：
   - `AI_AUTH_FAILED`
   - `AI_RATE_LIMITED`
   - `AI_UPSTREAM_TIMEOUT`
   - `AI_UPSTREAM_UNAVAILABLE`
   - `AI_STREAM_INTERRUPTED`
7. 未知异常：HTTP 500，生成 traceId；日志记录 traceId + 完整异常，响应只返回安全摘要。
8. 不用异常原始 `getMessage()` 直接对外，除非确认内容安全。

建议响应统一为：

```json
{
  "code": "AI_UPSTREAM_TIMEOUT",
  "message": "AI 服务响应超时，请稍后重试",
  "data": null,
  "traceId": "01J..."
}
```

---

## 9. Broken pipe 日志规则

本次 curl 使用 `head -c`、`--max-time` 或 timeout 提前结束 SSE，会导致服务器继续写响应时出现 Broken pipe。这是客户端主动断开，不是业务功能失败。

要求：

- 识别 `Broken pipe`、`ClientAbortException`、`AbortedException` 等客户端断流异常。
- 对已确认的客户端取消最多记 DEBUG/INFO，不打印 ERROR 堆栈污染日志。
- 仍执行现有取消收尾：部分气泡 partial，无内容 cancelled，释放 Redis 锁。
- 不能全局吞掉所有 IOException；连接建立、上游网络错误仍需正常告警。
- 自动化测试验证取消后没有 `generating` 残留。

---

## 10. 开发与生产环境的错误详情

为了兼顾调试与安全：

### 开发/测试环境

- 前端可显示后端提供的安全 `details`。
- 控制台打印结构化错误对象。
- 可显示请求 URL、HTTP 状态、机器码和 traceId。
- 仍不得打印 Token、Authorization 和密钥。

### 生产环境

- 业务错误显示具体可操作文案。
- 未知 500 显示安全摘要和 traceId。
- 完整堆栈仅在服务器日志/监控平台，通过 traceId 查询。

不要通过把生产堆栈直接传到浏览器来实现“方便调试”。

---

## 11. 自动化测试

### 前端单元测试

至少覆盖：

1. HTTP 400 显示后端字段原因，不清 Token。
2. HTTP 401 清 Token，只触发一次登录失效处理。
3. HTTP 403 显示无权限，不清 Token、不跳登录。
4. HTTP 404/409/429 显示具体 message，不清 Token。
5. HTTP 500 `系统内部错误` 不再显示体验用户过期，不清 Token。
6. 网络超时显示网络/超时错误。
7. 业务 HTTP 200 但 code 非成功时抛结构化 ApiError。
8. 多个并发 401 只弹一次、跳转一次。
9. SSE 400/401/403/409/429/500 JSON body 正确解析。
10. SSE 主动 abort 不弹系统错误，异常中断保留 partial。
11. `GUEST_ACCOUNT_EXPIRED` 才显示“体验账户已到期”。

建议使用 Axios Mock Adapter/MSW 或项目现有测试方案，不调用真实服务器。

### 后端测试

- GlobalExceptionHandler 各异常返回真实 HTTP 状态和统一结构。
- JWT 无效/过期为 401，权限不足为 403。
- 参数校验返回字段错误。
- 未知异常响应含 traceId，且不泄露堆栈/SQL。
- 客户端取消 SSE 正确收尾，Broken pipe 不改变消息业务状态。

---

## 12. 手工验收矩阵

依次人为制造并检查：

| 操作 | 预期显示 | 登录状态 |
|---|---|---|
| 新建角色缺少性格设定 | “性格设定不能为空” | 保留 |
| 发布旧版本 | “版本冲突，请刷新” | 保留 |
| 普通用户进管理页 | “无权限访问” | 保留 |
| DeepSeek Key 错误 | “AI 服务认证失败，请检查服务端配置” | 保留 |
| DeepSeek 超时 | “AI 服务响应超时，请稍后重试” | 保留 |
| 数据库异常 | “系统错误，traceId=...” | 保留 |
| Token 过期 | “登录已过期，请重新登录” | 清除并跳登录 |
| 体验账号业务到期 | “体验账户已到期” | 按产品规则处理 |
| curl 主动截断 SSE | 客户端停止，服务器正常取消收尾 | 保留 |

任何 403/409/429/500 导致“体验账户已过期”或强制跳登录，都判定验收失败。

---

## 13. 实施顺序

1. 新增统一 `ApiError`、解析器和认证失效去重处理。
2. 重写 Axios 响应拦截器，删除 50000 过期误判。
3. 修复 Pinia `checkLoginStatus()`。
4. 重写 SSE/fetch 非 2xx 与业务 error 事件解析。
5. 统一后端错误结构、HTTP 状态、机器码和 traceId。
6. 批量替换页面中吞错误的 catch，优先 AI 与核心页面。
7. 增加前后端测试。
8. 按手工矩阵验证，更新生产包。

---

## 14. 交付物与完成定义

完成后提交：

- 前端统一错误模块、Axios/SSE/Pinia 改造及页面接入。
- 后端统一错误协议、认证状态和 traceId 支持。
- 前后端自动化测试。
- `FRONTEND_ERROR_HANDLING_AND_AUTH_FIX_REPORT.md`，记录每类错误的实际响应、界面显示、Token 是否保留、测试数量和构建结果。

只有满足以下条件才算完成：

- 500 系统错误绝不再被误报为体验用户到期。
- 仅真正 401 清除登录并跳转。
- 每个可预期错误显示后端具体、安全、可操作的原因。
- 未知错误提供 traceId，服务器日志可据此定位。
- Axios、Pinia、SSE 和页面 catch 使用同一套错误语义。


# JWT到Session迁移指南

## 概述

本项目已从JWT认证完全迁移到基于Session的认证方式。以下是主要变更和使用说明。

## 主要变更

### 1. 移除的组件
- `JwtTokenInterceptor` - JWT令牌拦截器
- `JwtUtil` - JWT工具类
- `JwtProperties` - JWT配置类
- `JwtClaimsConstant` - JWT声明常量
- JWT相关依赖（jjwt-api, jjwt-impl, jjwt-jackson）

### 2. 新增的组件
- `SessionInterceptor` - Session校验拦截器
- `SessionConstant` - Session常量定义
- `SessionConfig` - Session配置类
- `TestController` - Session测试控制器

### 3. 修改的组件
- `UserController` - 登录/登出逻辑改为Session方式
- `UserVO` - 移除token字段
- `WebMvcConfiguration` - 替换拦截器
- 配置文件 - 移除JWT相关配置

## 认证流程

### 登录流程
1. 用户提交登录信息到 `/user/login`
2. 验证用户名密码
3. 创建Session并存储用户信息
4. 返回用户信息（不包含token）

### 认证流程
1. 客户端请求携带Session Cookie
2. `SessionInterceptor` 拦截请求
3. 验证Session是否存在且包含用户信息
4. 将用户ID存入ThreadLocal供业务使用

### 登出流程
1. 用户请求 `/user/logout`
2. 销毁当前Session
3. 返回登出成功信息

## API变更

### 登录接口
```http
POST /api/user/login
Content-Type: application/json

{
  "userAccount": "your_account",
  "userpassword": "your_password"
}
```

**响应变更：**
- ✅ 移除了 `token` 字段
- ✅ 自动设置Session Cookie

### 新增登出接口
```http
POST /api/user/logout
```

### 新增当前用户信息接口
```http
GET /api/user/current
```

## 前端适配指南

### 1. 移除Token处理
```javascript
// 删除这些代码
localStorage.setItem('token', response.data.token);
headers: { 'Authorization': `Bearer ${token}` }
```

### 2. 启用Cookie支持
```javascript
// axios配置
axios.defaults.withCredentials = true;

// fetch配置
fetch(url, {
  credentials: 'include'
});
```

### 3. 登录处理
```javascript
// 登录成功后不需要手动存储token
const login = async (credentials) => {
  const response = await axios.post('/api/user/login', credentials);
  // Session Cookie会自动设置，无需手动处理
  return response.data;
};
```

### 4. 登出处理
```javascript
const logout = async () => {
  await axios.post('/api/user/logout');
  // Session会在服务端销毁
};
```

## 配置说明

### Session配置
- **存储方式**: Redis
- **超时时间**: 30分钟（1800秒）
- **Cookie名称**: FRIENDXXX_SESSION
- **Cookie属性**: HttpOnly, SameSite=Lax

### 拦截器配置
**放行路径：**
- `/user/login` - 登录接口
- `/user/logout` - 登出接口
- `/test/**` - 测试接口
- Swagger文档相关路径

## 测试接口

### 获取Session信息
```http
GET /api/test/session-info
```

### 创建测试Session
```http
POST /api/test/create-session
```

## 优势

1. **安全性提升**
   - Session ID相对JWT更难伪造
   - 服务端可立即撤销Session
   - 不会在客户端暴露用户信息

2. **实现简化**
   - 无需手动管理token生成和验证
   - Spring Session提供开箱即用的功能
   - 减少了代码复杂度

3. **会话管理**
   - 支持实时踢出用户
   - 可控制并发会话数量
   - 更好的会话状态管理

## 注意事项

1. **跨域配置**
   - 确保CORS配置允许携带Cookie (`allowCredentials: true`)
   - 前端请求必须设置 `withCredentials: true`

2. **Redis依赖**
   - Session存储依赖Redis，确保Redis服务正常运行
   - 生产环境建议配置Redis集群

3. **Cookie安全**
   - 生产环境建议启用HTTPS并设置 `Secure` Cookie
   - 合理设置Cookie的Domain和Path

## 故障排除

### 常见问题

1. **Session丢失**
   - 检查Redis连接
   - 确认Cookie设置正确
   - 验证跨域配置

2. **认证失败**
   - 确认前端发送Cookie
   - 检查Session是否过期
   - 验证拦截器配置

3. **跨域问题**
   - 确保CORS配置正确
   - 前端设置withCredentials
   - 检查域名配置

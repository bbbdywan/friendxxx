# FriendXXX - 通用Java后端框架
一个基于Spring Boot 3.x的通用Java后端初始化框架，集成了用户管理、AI聊天、社交动态、实时通信等功能模块，为快速开发提供完整的基础架构。

## ✨前后端(已使用docker部署上线)
www.mandx.xyz 
mandx.xyz
体验用户仅填写昵称即可登录.
<img width="378" height="820" alt="image" src="https://github.com/user-attachments/assets/adee229c-ece2-42f5-826a-81008783ef1d" />
<img width="378" height="820" alt="image" src="https://github.com/user-attachments/assets/4ea49f5d-8a21-42bd-9242-525e4d8332be" />

### 🔐 认证授权
- **Session认证**：基于Redis的分布式Session管理
- **安全拦截**：自动拦截未授权请求
- **多端登录**：支持普通用户和HR用户登录

### 🤖 AI集成
- **阿里云通义千问**：集成DashScope AI服务
- **智能对话**：支持上下文记忆的AI聊天
- **流式响应**：实时流式AI对话体验
- **个性化角色**：可配置AI助手人设

### 👥 用户系统
- **用户管理**：完整的用户CRUD操作
- **个人资料**：头像、标签、个人信息管理
- **用户匹配**：基于标签的用户推荐

### 📱 社交功能
- **动态发布**：支持图片上传的社交动态
- **限时动态**：基于RabbitMQ的定时删除
- **动态管理**：查看、删除个人动态

### 💬 实时通信
- **WebSocket**：实时消息推送
- **聊天记录**：持久化聊天消息存储
- **在线状态**：用户在线状态管理

### 🚀 技术架构
- **缓存优化**：Redis缓存提升性能
- **消息队列**：RabbitMQ异步处理
- **文件存储**：阿里云OSS对象存储
- **API文档**：Swagger/Knife4j自动生成

## 🛠️ 技术栈

### 后端框架
- **Spring Boot 3.2.12** - 核心框架
- **Spring Session** - 分布式Session管理
- **MyBatis Plus 3.5.9** - ORM框架
- **Spring AI** - AI集成框架

### 数据存储
- **MySQL** - 主数据库
- **Redis** - 缓存和Session存储
- **H2** - 测试数据库

### 消息队列
- **RabbitMQ** - 异步消息处理
- **延时队列** - 定时任务处理

### AI服务
- **阿里云通义千问** - 大语言模型
- **Spring AI Alibaba** - AI集成组件

### 其他组件
- **Druid** - 数据库连接池
- **Knife4j** - API文档工具
- **WebSocket** - 实时通信
- **阿里云OSS** - 文件存储

## 📦 快速开始

### 环境要求
- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- RabbitMQ 3.8+

### 安装步骤

1. **克隆项目**
```bash
git clone https://github.com/bbbdywan/friendxxx.git
cd friendxxx
```

2. **配置数据库**
```sql
CREATE DATABASE friendxxx CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. **配置文件**
创建 `src/main/resources/application-dev.yml`：
```yaml
your:
  datasource:
    host: localhost
    port: 3306
    database: friendxxx
    username: your_username
    password: your_password
  redis:
    host: localhost
    port: 6379
    database: 0
  alioss:
    access-key-id: your_access_key
    access-key-secret: your_secret_key
    bucket-name: your_bucket
    endpoint: your_endpoint

spring:
  ai:
    dashscope:
      api-key: your_dashscope_api_key
```

4. **启动服务**
```bash
mvn spring-boot:run
```

5. **访问应用**
- API文档：http://localhost:8080/api/doc.html
- 健康检查：http://localhost:8080/api/health/check

## 📚 API文档

### 用户管理
```http
POST /api/user/login          # 用户登录
POST /api/user/logout         # 用户登出
GET  /api/user/current        # 获取当前用户
GET  /api/user/profile        # 获取用户资料
POST /api/user/update         # 更新用户信息
```

### AI聊天
```http
GET  /api/helloworld/simple/chat    # 简单AI对话
GET  /api/helloworld/stream/chat    # 流式AI对话
GET  /api/helloworld/getmessagelist # 获取聊天记录
```

### 社交动态
```http
POST /api/stausup/newpost      # 发布动态
GET  /api/stausup/getstup      # 获取动态列表
POST /api/stausup/update/image # 上传图片
```

### WebSocket通信
```javascript
// 连接WebSocket
const ws = new WebSocket('ws://localhost:8080/api/websocket/{userId}');
```

## 🏗️ 项目结构

```
src/main/java/com/xzh/friendxxx/
├── config/              # 配置类
│   ├── AIConfig.java           # AI配置
│   ├── SessionConfig.java      # Session配置
│   └── WebMvcConfiguration.java # Web配置
├── controller/          # 控制器层
│   ├── UserController.java     # 用户管理
│   ├── HelloworldController.java # AI聊天
│   ├── StausUPController.java   # 社交动态
│   └── WebSocketController.java # WebSocket
├── service/            # 服务层
├── model/              # 数据模型
│   ├── entity/         # 实体类
│   ├── dto/           # 数据传输对象
│   └── vo/            # 视图对象
├── common/             # 通用组件
│   ├── utils/         # 工具类
│   └── context/       # 上下文管理
└── interceptor/        # 拦截器
```

## 🔧 核心功能详解

### Session认证机制
项目已从JWT完全迁移到Session认证，提供更安全的用户会话管理：

- **分布式Session**：基于Redis存储，支持集群部署
- **自动过期**：30分钟无操作自动过期
- **安全Cookie**：HttpOnly、SameSite保护

### AI聊天系统
集成阿里云通义千问，提供智能对话功能：

- **上下文记忆**：基于数据库的对话历史管理
- **个性化设定**：可配置AI助手角色和说话风格
- **流式输出**：逐字输出提升用户体验

### 社交动态系统
完整的社交功能实现：

- **图片上传**：集成阿里云OSS
- **限时动态**：基于RabbitMQ延时队列自动删除
- **动态管理**：发布、查看、删除操作

## 🚀 部署指南

### Docker部署
```dockerfile
FROM openjdk:17-jdk-slim
COPY target/friendxxx-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### 生产环境配置
- 启用HTTPS
- 配置Redis集群
- 设置数据库连接池
- 配置日志级别

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 开源协议

本项目基于 [Apache License 2.0](LICENSE) 开源协议。

## 📞 联系方式

- 项目维护者：白白白
- 邮箱：2740475903@qq.com
- 项目地址：[https://github.com/your-username/friendxxx](https://github.com/bbbdywan/friendxxx)

## 🙏 致谢

感谢以下开源项目的支持：
- Spring Boot
- Spring AI
- MyBatis Plus
- 阿里云通义千问

---

⭐ 如果这个项目对你有帮助，请给个Star支持一下！

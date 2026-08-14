# 心事小屋本地基础设施部署说明

## 当前结果

- 通过 `deploy/docker-compose.infrastructure.yml` 启动：
  - Redis 7.4 → `127.0.0.1:6379`
  - RabbitMQ 4（含管理后台） → `5672` / 管理台 `15672`
  - Nacos 2.4.3 standalone → `8848` / gRPC `9848`
  - Elasticsearch 8.15.3（dev 关闭安全） → `9200`
- 本机已自带 MySQL（PID 7308/9596 监听 3306），所以没有额外用 Compose 起容器，请用宿主 MySQL。

如果将来想完全容器化，把 `services.mysql` 一节的端口改成 `${MYSQL_PORT:-13306}:3306` 后再 `docker compose up -d mysql` 即可。

## 当前本机验证

```powershell
docker ps --format "{{.Names}}\t{{.Ports}}"
curl http://127.0.0.1:8848/nacos
curl http://127.0.0.1:9200
curl http://127.0.0.1:15672
```

预期：四个容器正常 Up（Nacos、ES、Redis、RabbitMQ），且 `curl` 返回 200/JSON。

## 调整单体配置以接入新版 MySQL

`application-dev.yml` 中 `DB_URL` 当前是 `jdbc:mysql://localhost:3306/friendxxx`。如果宿主 MySQL 没有同名数据库，先：

```sql
CREATE DATABASE friendxxx DEFAULT CHARACTER SET utf8mb4;
```

然后按需要导入已有 DDL 或重新启动单体。

## 网关

构建并启动：

```powershell
cd F:\baib\Java_Backend_Universal_Template-main\friendxxx\microservices
mvn -q -DskipTests package -pl gateway-service
java -jar gateway-service/target/gateway-service.jar
```

网关默认监听 `9000`，把所有 `/api/**` 转发到单体 `http://127.0.0.1:8080`。

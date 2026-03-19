# MySQL 慢查询日志配置（任务21）

## 配置说明

慢查询日志用于记录执行时间超过指定阈值的 SQL 语句，帮助发现性能瓶颈。

## 配置步骤

### 方式1：修改 MySQL 配置文件（推荐，永久生效）

1. 找到 MySQL 配置文件（通常是 `/etc/my.cnf` 或 `/etc/mysql/my.cnf`）

2. 在 `[mysqld]` 部分添加以下配置：

```ini
[mysqld]
# 开启慢查询日志
slow_query_log = 1

# 慢查询日志文件路径
slow_query_log_file = /var/log/mysql/mysql-slow.log

# 慢查询阈值（秒），超过1秒的查询会被记录
long_query_time = 1

# 记录没有使用索引的查询
log_queries_not_using_indexes = 1

# 限制每分钟记录的未使用索引的查询数量（避免日志过大）
log_throttle_queries_not_using_indexes = 10
```

3. 重启 MySQL 服务：
```bash
# CentOS/RHEL
sudo systemctl restart mysqld

# Ubuntu/Debian
sudo systemctl restart mysql

# Docker
docker restart mysql容器名
```

### 方式2：动态设置（临时生效，重启后失效）

连接到 MySQL 后执行以下命令：

```sql
-- 开启慢查询日志
SET GLOBAL slow_query_log = 'ON';

-- 设置慢查询阈值为1秒
SET GLOBAL long_query_time = 1;

-- 设置慢查询日志文件路径
SET GLOBAL slow_query_log_file = '/var/log/mysql/mysql-slow.log';

-- 记录没有使用索引的查询
SET GLOBAL log_queries_not_using_indexes = 'ON';
```

## 查看慢查询日志配置

```sql
-- 查看慢查询日志是否开启
SHOW VARIABLES LIKE 'slow_query_log';

-- 查看慢查询阈值
SHOW VARIABLES LIKE 'long_query_time';

-- 查看慢查询日志文件路径
SHOW VARIABLES LIKE 'slow_query_log_file';

-- 查看是否记录未使用索引的查询
SHOW VARIABLES LIKE 'log_queries_not_using_indexes';

-- 查看慢查询数量
SHOW GLOBAL STATUS LIKE 'Slow_queries';
```

## 分析慢查询日志

### 方式1：直接查看日志文件

```bash
# 查看最近的慢查询
tail -n 100 /var/log/mysql/mysql-slow.log

# 实时监控慢查询
tail -f /var/log/mysql/mysql-slow.log
```

### 方式2：使用 mysqldumpslow 工具（推荐）

```bash
# 显示最慢的10条查询
mysqldumpslow -s t -t 10 /var/log/mysql/mysql-slow.log

# 显示访问次数最多的10条查询
mysqldumpslow -s c -t 10 /var/log/mysql/mysql-slow.log

# 显示平均执行时间最长的10条查询
mysqldumpslow -s at -t 10 /var/log/mysql/mysql-slow.log

# 显示返回记录数最多的10条查询
mysqldumpslow -s r -t 10 /var/log/mysql/mysql-slow.log
```

### 方式3：使用 pt-query-digest 工具（功能最强大）

```bash
# 安装 percona-toolkit
# CentOS/RHEL
sudo yum install percona-toolkit

# Ubuntu/Debian
sudo apt-get install percona-toolkit

# 分析慢查询日志
pt-query-digest /var/log/mysql/mysql-slow.log > slow_query_report.txt
```

## Docker 环境配置

如果使用 Docker Compose 部署 MySQL，在 `docker-compose.yml` 中添加：

```yaml
services:
  mysql:
    image: mysql:8.0
    command:
      - --slow_query_log=1
      - --slow_query_log_file=/var/log/mysql/mysql-slow.log
      - --long_query_time=1
      - --log_queries_not_using_indexes=1
    volumes:
      - ./mysql-logs:/var/log/mysql
```

## 常见慢查询优化建议

### 1. 缺少索引
```sql
-- 问题：SELECT * FROM user WHERE username = 'xxx'
-- 解决：添加索引
ALTER TABLE user ADD INDEX idx_username (username);
```

### 2. 深分页
```sql
-- 问题：SELECT * FROM user LIMIT 10000, 10
-- 解决：使用游标分页或限制最大页数
SELECT * FROM user WHERE id > last_id LIMIT 10;
```

### 3. 全表扫描
```sql
-- 问题：SELECT * FROM chat_message WHERE content LIKE '%关键词%'
-- 解决：使用全文索引或 Elasticsearch
```

### 4. 未使用索引
```sql
-- 问题：SELECT * FROM user WHERE YEAR(create_time) = 2024
-- 解决：避免在索引列上使用函数
SELECT * FROM user WHERE create_time >= '2024-01-01' AND create_time < '2025-01-01';
```

## 注意事项

1. **日志文件大小**：慢查询日志会持续增长，建议定期清理或轮转
2. **性能影响**：开启慢查询日志会有轻微的性能影响（约1-2%）
3. **阈值设置**：根据业务需求调整 `long_query_time`，建议设置为 1-2 秒
4. **定期分析**：建议每周分析一次慢查询日志，优化慢查询

## 日志轮转配置

创建 `/etc/logrotate.d/mysql-slow` 文件：

```
/var/log/mysql/mysql-slow.log {
    daily
    rotate 7
    missingok
    compress
    delaycompress
    notifempty
    create 640 mysql mysql
    sharedscripts
    postrotate
        /usr/bin/mysql -e 'SELECT @@global.slow_query_log INTO @sq_log_save; SET GLOBAL slow_query_log=OFF; SELECT SLEEP(5); FLUSH SLOW LOGS; SELECT SLEEP(10); SET GLOBAL slow_query_log=@sq_log_save;'
    endscript
}
```

## 监控指标

定期检查以下指标：

```sql
-- 慢查询总数
SHOW GLOBAL STATUS LIKE 'Slow_queries';

-- 查询总数
SHOW GLOBAL STATUS LIKE 'Questions';

-- 慢查询比例
SELECT
    (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Slow_queries') /
    (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Questions') * 100
    AS slow_query_percentage;
```

## 预期效果

- 发现执行时间超过1秒的 SQL 语句
- 识别未使用索引的查询
- 为数据库优化提供数据支持
- 提升整体查询性能 5-10 倍

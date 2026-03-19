# JMeter 压测方案（2核2G服务器）

## 压测目标

- 验证系统能否支撑 500-1000 并发用户
- 测试消息发送 TPS
- 测试接口 QPS
- 验证限流是否生效
- 检查内存和 CPU 使用情况

---

## 压测环境准备

### 1. 安装 JMeter

```bash
# 下载 JMeter（建议 5.5 或更高版本）
wget https://dlcdn.apache.org//jmeter/binaries/apache-jmeter-5.6.3.tgz

# 解压
tar -xzf apache-jmeter-5.6.3.tgz

# 启动 JMeter GUI
cd apache-jmeter-5.6.3/bin
./jmeter
```

### 2. 准备测试数据

创建 `users.csv` 文件，包含测试用户账号：
```csv
userAccount,userPassword
test001,123456
test002,123456
test003,123456
...
test100,123456
```

---

## 压测场景

### 场景1：登录接口压测

**目标**：测试登录接口 QPS，验证限流是否生效

**配置**：
- 线程数：100
- Ramp-Up 时间：10秒
- 循环次数：10
- 预期 QPS：50-100

**JMeter 配置**：

1. 添加线程组
   - 线程数：100
   - Ramp-Up：10
   - 循环次数：10

2. 添加 HTTP 请求
   - 服务器名称：你的服务器IP
   - 端口：8080
   - 路径：/api/user/login
   - 方法：POST
   - Body Data：
     ```json
     {
       "userAccount": "${userAccount}",
       "userpassword": "${userPassword}"
     }
     ```

3. 添加 CSV Data Set Config
   - 文件名：users.csv
   - 变量名：userAccount,userPassword

4. 添加监听器
   - 聚合报告
   - 查看结果树
   - 响应时间图表

---

### 场景2：WebSocket 连接压测

**目标**：测试 WebSocket 最大连接数（目标1000）

**配置**：
- 线程数：1000
- Ramp-Up 时间：60秒
- 持续时间：300秒（5分钟）

**JMeter 配置**：

1. 安装 WebSocket 插件
   - 下载：https://github.com/Blazemeter/jmeter-websocket-samplers
   - 放到 JMeter 的 lib/ext 目录

2. 添加线程组
   - 线程数：1000
   - Ramp-Up：60
   - 持续时间：300秒

3. 添加 WebSocket Sampler
   - 服务器：你的服务器IP
   - 端口：8080
   - 路径：/api/websocket/${userId}
   - 协议：ws

4. 添加监听器
   - 活动线程数随时间变化
   - 聚合报告

---

### 场景3：消息发送压测

**目标**：测试消息发送 TPS（目标200-300）

**配置**：
- 线程数：50
- Ramp-Up 时间：10秒
- 循环次数：100
- 预期 TPS：200-300

**JMeter 配置**：

1. 添加线程组
   - 线程数：50
   - Ramp-Up：10
   - 循环次数：100

2. 添加 WebSocket Sampler（发送消息）
   - 消息内容：
     ```json
     {
       "type": "private",
       "toUserId": "${receiverId}",
       "message": "测试消息 ${__time()}"
     }
     ```

3. 添加监听器
   - 聚合报告
   - TPS 随时间变化

---

### 场景4：混合场景压测（推荐）

**目标**：模拟真实用户行为

**配置**：
- 登录用户：100
- WebSocket 连接：100
- 消息发送：每秒10条
- 持续时间：10分钟

**JMeter 配置**：

1. 线程组1：登录
   - 线程数：100
   - Ramp-Up：20秒
   - 循环次数：1

2. 线程组2：建立 WebSocket 连接
   - 线程数：100
   - Ramp-Up：30秒
   - 持续时间：600秒

3. 线程组3：发送消息
   - 线程数：10
   - Ramp-Up：5秒
   - 循环次数：600（每秒1条，持续10分钟）

---

## 压测步骤

### 第一轮：基准测试（小压力）

```
线程数：10
Ramp-Up：5秒
持续时间：1分钟
```

**目的**：验证系统基本功能正常

### 第二轮：负载测试（中等压力）

```
线程数：100
Ramp-Up：20秒
持续时间：5分钟
```

**目的**：测试系统在正常负载下的表现

### 第三轮：压力测试（高压力）

```
线程数：500
Ramp-Up：60秒
持续时间：10分钟
```

**目的**：测试系统在高负载下的表现

### 第四轮：极限测试（超高压力）

```
线程数：1000
Ramp-Up：120秒
持续时间：5分钟
```

**目的**：找到系统的性能瓶颈

---

## 监控指标

### 1. 应用层监控

```bash
# 查看 JVM 内存使用
jmap -heap <pid>

# 查看线程数
jstack <pid> | grep "java.lang.Thread.State" | wc -l

# 查看 GC 情况
tail -f logs/gc.log
```

### 2. 系统层监控

```bash
# 查看 CPU 使用率
top

# 查看内存使用
free -h

# 查看网络连接数
netstat -an | grep 8080 | wc -l

# 查看 WebSocket 连接数
netstat -an | grep ESTABLISHED | grep 8080 | wc -l
```

### 3. 数据库监控

```sql
-- 查看当前连接数
SHOW STATUS LIKE 'Threads_connected';

-- 查看慢查询数量
SHOW GLOBAL STATUS LIKE 'Slow_queries';

-- 查看正在执行的查询
SHOW PROCESSLIST;
```

### 4. Redis 监控

```bash
# 连接 Redis
redis-cli

# 查看连接数
INFO clients

# 查看内存使用
INFO memory

# 查看命令统计
INFO stats
```

### 5. RabbitMQ 监控

```bash
# 查看队列状态
rabbitmqctl list_queues

# 查看连接数
rabbitmqctl list_connections

# 或者访问管理界面
http://服务器IP:15672
```

---

## 性能指标参考

### 优化前（预估）
- 并发用户数：100-200
- 登录接口 QPS：50-100
- 消息发送 TPS：20-50
- 平均响应时间：100-200ms
- 内存占用：1.5-1.8G

### 优化后（目标）
- 并发用户数：500-1000
- 登录接口 QPS：200-300
- 消息发送 TPS：200-300
- 平均响应时间：10-50ms
- 内存占用：0.8-1.2G

---

## 压测注意事项

### 1. 压测前准备
- ✅ 备份数据库
- ✅ 清空 Redis 缓存
- ✅ 清空 RabbitMQ 队列
- ✅ 重启应用
- ✅ 确保测试数据充足

### 2. 压测过程中
- 📊 实时监控服务器资源（CPU、内存、网络）
- 📊 实时监控应用日志
- 📊 实时监控数据库连接数
- 📊 实时监控 Redis 连接数
- 📊 实时监控 RabbitMQ 队列长度

### 3. 压测后分析
- 📈 分析 JMeter 聚合报告
- 📈 分析应用日志（错误、警告）
- 📈 分析 GC 日志
- 📈 分析慢查询日志
- 📈 分析系统资源使用情况

### 4. 常见问题排查

**问题1：连接超时**
- 检查 Tomcat 最大连接数配置
- 检查网络带宽
- 检查防火墙设置

**问题2：内存溢出**
- 检查 JVM 堆内存配置
- 检查是否有内存泄漏
- 检查 WebSocket 连接是否正常关闭

**问题3：数据库连接池耗尽**
- 检查数据库连接池配置
- 检查是否有慢查询
- 检查连接是否正常释放

**问题4：限流触发**
- 检查限流配置是否合理
- 检查 Redis 是否正常
- 调整限流阈值

---

## 压测报告模板

### 测试环境
- 服务器配置：2核2G
- 应用版本：v1.0
- 测试时间：2026-03-17
- 测试工具：JMeter 5.6.3

### 测试场景
- 场景1：登录接口压测
- 场景2：WebSocket 连接压测
- 场景3：消息发送压测
- 场景4：混合场景压测

### 测试结果

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 并发用户数 | 100 | 800 | 8倍 |
| 登录 QPS | 80 | 250 | 3倍 |
| 消息 TPS | 30 | 280 | 9倍 |
| 平均响应时间 | 150ms | 20ms | 7.5倍 |
| 内存占用 | 1.6G | 1.0G | 节省37% |

### 性能瓶颈
- 数据库连接数不足（已优化）
- WebSocket 连接无限制（已优化）
- 消息持久化同步阻塞（已优化）

### 优化建议
- ✅ 已完成基础优化
- 🔄 建议添加健康检查接口
- 🔄 建议添加监控告警

---

## 快速开始

### 1. 简单压测命令（命令行模式）

```bash
# 登录接口压测
jmeter -n -t login_test.jmx -l result.jtl -e -o report

# 参数说明：
# -n: 非 GUI 模式
# -t: 测试计划文件
# -l: 结果文件
# -e: 生成 HTML 报告
# -o: 报告输出目录
```

### 2. 查看报告

```bash
# 打开 HTML 报告
open report/index.html
```

---

## 下一步

压测完成后，根据结果决定是否需要：
1. 继续第七阶段：启动脚本优化
2. 继续第八阶段：监控与测试
3. 调整优化参数
4. 增加服务器资源

-- ============================================
-- 数据库优化 SQL 脚本（任务19）
-- 针对 2核2G 服务器的索引优化
-- ============================================

-- 1. chat_message 表索引优化
-- 用于加速按会话ID和时间查询消息
ALTER TABLE `chat_message`
ADD INDEX `idx_conversation_time` (`conversation_id`, `create_time`)
COMMENT '会话ID和创建时间联合索引，用于查询会话消息列表';

-- 用于加速按发送者查询消息
ALTER TABLE `chat_message`
ADD INDEX `idx_sender_id` (`sender_id`)
COMMENT '发送者ID索引';

-- 用于加速按接收者查询消息
ALTER TABLE `chat_message`
ADD INDEX `idx_receiver_id` (`receiver_id`)
COMMENT '接收者ID索引';

-- 2. user 表索引优化
-- 检查是否已有 user_account 索引，如果没有则添加
-- ALTER TABLE `user`
-- ADD UNIQUE INDEX `idx_user_account` (`userAccount`)
-- COMMENT '用户账号唯一索引，用于登录查询';

-- 用于加速按用户名模糊查询
ALTER TABLE `user`
ADD INDEX `idx_username` (`username`)
COMMENT '用户名索引，用于搜索用户';

-- 用于加速按创建时间查询
ALTER TABLE `user`
ADD INDEX `idx_create_time` (`createTime`)
COMMENT '创建时间索引，用于按时间范围查询';

-- 3. 如果有好友关系表，添加索引（根据实际表名调整）
-- ALTER TABLE `friend`
-- ADD INDEX `idx_user_friend` (`user_id`, `friend_id`)
-- COMMENT '用户和好友联合索引';

-- ============================================
-- 查看现有索引
-- ============================================
-- 查看 chat_message 表的索引
SHOW INDEX FROM `chat_message`;

-- 查看 user 表的索引
SHOW INDEX FROM `user`;

-- ============================================
-- 索引使用情况分析
-- ============================================
-- 分析 chat_message 表
ANALYZE TABLE `chat_message`;

-- 分析 user 表
ANALYZE TABLE `user`;

-- ============================================
-- 注意事项
-- ============================================
-- 1. 执行前请先备份数据库
-- 2. 在业务低峰期执行，避免影响线上服务
-- 3. 如果表数据量很大，添加索引可能需要较长时间
-- 4. 添加索引后，INSERT/UPDATE/DELETE 操作会稍微变慢，但 SELECT 会快很多
-- 5. 定期使用 ANALYZE TABLE 更新索引统计信息

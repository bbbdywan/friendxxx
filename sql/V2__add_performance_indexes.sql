-- ============================================
-- V2: 性能索引迁移 - 全链路优化
-- 针对 2核2G 服务器
-- ============================================

-- 1. social_post 表
ALTER TABLE `social_post` ADD INDEX `idx_user_id` (`user_id`) COMMENT '按用户查动态';
ALTER TABLE `social_post` ADD INDEX `idx_is_deleted` (`is_deleted`) COMMENT '过滤已删除';
ALTER TABLE `social_post` ADD INDEX `idx_sup_ttl` (`sup_ttl`(100)) COMMENT '限时动态过期查询(前缀索引)';
ALTER TABLE `social_post` ADD INDEX `idx_create_time` (`create_time`) COMMENT '按时间排序';

-- 2. user 表
ALTER TABLE `user` ADD INDEX `idx_gender_age` (`gender`, `age`) COMMENT '推荐系统筛选';
ALTER TABLE `user` ADD INDEX `idx_tags` (`tags`(100)) COMMENT '按标签筛选用户(前缀索引)';

-- 3. ai_chat_memory 表
ALTER TABLE `ai_chat_memory` ADD INDEX `idx_conversation_deleted` (`conversation_id`, `is_deleted`) COMMENT '会话消息查询';

-- 4. group_member 表
ALTER TABLE `group_member` ADD INDEX `idx_group_user` (`group_id`, `user_id`) COMMENT '群成员查询';
ALTER TABLE `group_member` ADD INDEX `idx_user_id` (`user_id`) COMMENT '用户所在群查询';

-- 5. group_chat 表
ALTER TABLE `group_chat` ADD INDEX `idx_creator_id` (`creator_id`) COMMENT '创建者查询';

-- 6. comment 表 (已有的 post_id 索引确认)
-- comment 表的 post_id 索引已在 V0 中创建，跳过

-- 分析表更新统计
ANALYZE TABLE `social_post`;
ANALYZE TABLE `user`;
ANALYZE TABLE `ai_chat_memory`;
ANALYZE TABLE `group_member`;
ANALYZE TABLE `group_chat`;

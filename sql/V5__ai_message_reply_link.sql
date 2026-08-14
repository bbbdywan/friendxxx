-- ============================================================
-- V5__ai_message_reply_link.sql
-- AI 消息表新增 reply_to_message_id：Assistant 回复精确关联对应 User 消息
-- 用于幂等重试时精确返回原回复，禁止通过"最近 N 条消息"猜测。
-- ============================================================

ALTER TABLE ai_message
    ADD COLUMN reply_to_message_id VARCHAR(64) NULL COMMENT 'Assistant 回复对应的 User 消息 ID',
    ADD INDEX idx_reply_to_message_id (reply_to_message_id);

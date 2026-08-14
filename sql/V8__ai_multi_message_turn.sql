-- ============================================================
-- V8__ai_multi_message_turn.sql
-- AI 多消息回复：一轮用户请求可对应多条独立 Assistant 消息气泡。
--
-- ai_message 新增：
--   turn_id                本轮用户请求及其多条 Assistant 回复的共同标识
--   message_index          同一轮内消息顺序（0 起；User 消息恒为 0）
--   character_version_id   生成该消息时使用的人设版本 ID（便于复现）
--
-- 兼容：老数据 turn_id=null、message_index=0，现有查询不受影响。
-- ============================================================

ALTER TABLE ai_message
    ADD COLUMN turn_id VARCHAR(64) NULL COMMENT '一轮用户请求及其多条回复的共同标识',
    ADD COLUMN message_index INT NOT NULL DEFAULT 0 COMMENT '同一轮内消息顺序，0 起',
    ADD COLUMN character_version_id BIGINT NULL COMMENT '生成时使用的人设版本ID';

-- 查询加速：按会话查找整轮消息
ALTER TABLE ai_message
    ADD INDEX idx_ai_msg_turn (conversation_id, turn_id, message_index);

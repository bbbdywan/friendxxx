-- 聊天消息未读数表
CREATE TABLE IF NOT EXISTS `unread_count` (
    `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id`         BIGINT   NOT NULL                COMMENT '消息接收者ID',
    `conversation_id` VARCHAR(64) NOT NULL             COMMENT '会话ID',
    `unread`          INT      NOT NULL DEFAULT 0      COMMENT '未读消息数',
    `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_conversation` (`user_id`, `conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天未读数表';

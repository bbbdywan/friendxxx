-- 互动消息表
CREATE TABLE IF NOT EXISTS `user_notification` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `to_user_id`    BIGINT       NOT NULL                COMMENT '接收通知的用户ID（动态作者）',
    `from_user_id`  BIGINT       NOT NULL                COMMENT '触发通知的用户ID',
    `from_nickname` VARCHAR(64)  NOT NULL                COMMENT '触发通知的用户昵称',
    `type`          VARCHAR(16)  NOT NULL                COMMENT '通知类型：like=点赞 comment=评论',
    `post_id`       BIGINT       NOT NULL                COMMENT '相关动态ID',
    `content`       VARCHAR(500) DEFAULT NULL            COMMENT '附加内容（评论内容）',
    `is_read`       TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已读：0=未读 1=已读',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常 1=删除',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_to_user_id` (`to_user_id`, `is_deleted`, `create_time`),
    INDEX `idx_unread`     (`to_user_id`, `is_read`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='互动消息表';

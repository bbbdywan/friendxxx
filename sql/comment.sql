-- 评论表
CREATE TABLE IF NOT EXISTS `comment` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `post_id`     BIGINT       NOT NULL                COMMENT '动态ID',
    `user_id`     BIGINT       NOT NULL                COMMENT '评论用户ID',
    `nickname`    VARCHAR(64)  NOT NULL                COMMENT '评论用户昵称',
    `avatar_url`  VARCHAR(512) DEFAULT NULL            COMMENT '评论用户头像',
    `content`     VARCHAR(500) NOT NULL                COMMENT '评论内容',
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除 0=正常 1=删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_post_id` (`post_id`, `is_deleted`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

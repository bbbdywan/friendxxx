CREATE TABLE `user_prompt` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     BIGINT       NOT NULL                COMMENT '用户ID',
  `title`       VARCHAR(255) DEFAULT NULL            COMMENT '提示词标题',
  `content`     TEXT         NOT NULL                COMMENT '提示词内容',
  `is_active`   TINYINT      NOT NULL DEFAULT 1      COMMENT '是否当前使用 0-否 1-是',
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否删除 0-否 1-是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自定义提示词表';

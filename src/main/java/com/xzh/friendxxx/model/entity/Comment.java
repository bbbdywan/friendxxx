package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 评论表
 * @TableName comment
 */
@TableName(value = "comment")
@Data
public class Comment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "post_id")
    private Long postId;

    @TableField(value = "user_id")
    private Long userId;

    @TableField(value = "nickname")
    private String nickname;

    @TableField(value = "avatar_url")
    private String avatarUrl;

    @TableField(value = "content")
    private String content;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer isDeleted;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;
}

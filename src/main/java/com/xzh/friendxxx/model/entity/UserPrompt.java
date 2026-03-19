package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.util.Date;
import lombok.Data;

/**
 * @TableName user_prompt
 */
@TableName(value = "user_prompt")
@Data
public class UserPrompt {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "user_id")
    private Long userId;

    @TableField(value = "title")
    private String title;

    @TableField(value = "content")
    private String content;

    @TableField(value = "is_active")
    private Integer isActive;

    @TableField(value = "is_deleted")
    @TableLogic
    private Integer isDeleted;

    @TableField(value = "create_time")
    private Date createTime;

    @TableField(value = "update_time")
    private Date updateTime;
}

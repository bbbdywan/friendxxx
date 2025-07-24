package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.util.Date;
import lombok.Data;

/**
 * 群聊
 * @TableName group_chat
 */
@TableName(value ="group_chat")
@Data
public class GroupChat {
    /**
     * 群聊ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 群名称
     */
    @TableField(value = "group_name")
    private String group_name;

    /**
     * 群头像
     */
    @TableField(value = "avatar_url")
    private String avatar_url;

    /**
     * 创建者ID
     */
    @TableField(value = "creator_id")
    private Long creator_id;

    /**
     * 成员数量
     */
    @TableField(value = "member_count")
    private Integer member_count;

    /**
     * 群简介
     */
    @TableField(value = "introduction")
    private String introduction;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date create_time;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private Date update_time;

    /**
     * 是否删除
     */
    @TableLogic
    @TableField(value = "is_delete")
    private Integer is_delete;
}
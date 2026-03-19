package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.util.Date;
import java.util.List;

import com.xzh.friendxxx.Handler.ListToJsonTypeHandler;
import com.xzh.friendxxx.Handler.MoodTypeHandler;
import lombok.Data;

/**
 * 用户动态表
 * @TableName social_post
 */
@TableName(value ="social_post")
@Data
public class SocialPost {
    /**
     * 动态ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 用户昵称
     */
    @TableField(value = "nickname")
    private String nickname;

    /**
     * 文字内容
     */
    @TableField(value = "content")
    private String content;

    /**
     * 图片数组
     */
    @TableField(value = "image_list",typeHandler= ListToJsonTypeHandler.class    )
    private List<String> imageList;

    /**
     * 点赞数
     */
    @TableField(value = "like_count")
    private Integer likeCount;

    /**
     * 是否已删除
     */
    @TableLogic
    @TableField(value = "is_deleted")
    private Integer isDeleted;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 最后更新时间
     */
    @TableField(value = "update_time")
    private Date updateTime;

    @TableField(value = "mood",typeHandler = MoodTypeHandler.class)
    private List<Mood> mood;

    @TableField(value = "avatar_url")
    private String avatarUrl;

    @TableField(value = "sup_ttl")
    private String supTtl;

    @TableField(value = "delete_ttl")
    private String deleteTtl;
}
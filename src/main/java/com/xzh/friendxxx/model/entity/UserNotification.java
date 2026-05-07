package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 互动消息表
 * @TableName user_notification
 */
@TableName(value = "user_notification")
@Data
public class UserNotification {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 通知接收者（动态作者） */
    @TableField(value = "to_user_id")
    private Long toUserId;

    /** 触发通知的用户（点赞者/评论者） */
    @TableField(value = "from_user_id")
    private Long fromUserId;

    /** 触发通知的用户昵称 */
    @TableField(value = "from_nickname")
    private String fromNickname;

    /** 通知类型：like / comment */
    @TableField(value = "type")
    private String type;

    /** 相关动态ID */
    @TableField(value = "post_id")
    private Long postId;

    /** 附加内容（评论时为评论内容） */
    @TableField(value = "content")
    private String content;

    /** 是否已读：0=未读，1=已读 */
    @TableField(value = "is_read")
    private Integer isRead;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer isDeleted;

    @TableField(value = "create_time")
    private Date createTime;
}

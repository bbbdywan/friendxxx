package com.xzh.friendxxx.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 系统通知 MQ 消息体
 * type: "like" 或 "comment"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage implements Serializable {

    /** 通知类型：like / comment */
    private String type;

    /** 触发通知的用户ID（点赞者/评论者） */
    private Long fromUserId;

    /** 触发通知的用户昵称 */
    private String fromNickname;

    /** 动态接收通知的用户ID（动态作者） */
    private Long toUserId;

    /** 相关动态ID */
    private Long postId;

    /** 附加内容（评论 type 时填评论内容） */
    private String content;

    /** 时间戳 */
    private Long timestamp;
}

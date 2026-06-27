package com.xzh.friendxxx.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SenderVO implements Serializable {
    @Schema(description = "发送者ID")
    private Long senderId;

    @Schema(description = "接收者ID")
    private String receiverId;
    
    @Schema(description = "发送内容")
    private String content;

    @Schema(description = "发送时间")
    private Date createTime;

    @Schema(description = "未读消息数")
    private Long unreadCount;

    @Schema(description = "会话ID")
    private String conversationId;

    @Schema(description = "聊天对象用户ID")
    private Long chatUserId;

    @Schema(description = "聊天对象昵称")
    private String chatUserName;

    @Schema(description = "聊天对象头像")
    private String chatUserAvatar;
}

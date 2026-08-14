package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 会话表 ai_conversation
 */
@TableName("ai_conversation")
@Data
public class AiConversation {

    @TableId(value = "id", type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String id;

    @TableField("user_id")
    private Long userId;

    @TableField("character_id")
    private Long characterId;

    private String title;

    @TableField("conversation_summary")
    private String conversationSummary;

    @TableField("summary_version")
    private Integer summaryVersion;

    @TableField("last_message_at")
    private Date lastMessageAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}

package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 消息表 ai_message（完整聊天历史；禁止保存 reasoning_content）
 */
@TableName("ai_message")
@Data
public class AiMessage {

    @TableId(value = "id", type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String id;

    @TableField("client_message_id")
    private String clientMessageId;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("reply_to_message_id")
    private String replyToMessageId;

    @TableField("turn_id")
    private String turnId;

    @TableField("message_index")
    private Integer messageIndex;

    @TableField("character_version_id")
    private Long characterVersionId;

    @TableField("user_id")
    private Long userId;

    private String role;

    private String content;

    private String model;

    @TableField("input_tokens")
    private Integer inputTokens;

    @TableField("output_tokens")
    private Integer outputTokens;

    private String status;

    @TableField("create_time")
    private Date createTime;
}

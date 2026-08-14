package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 长期记忆表 ai_memory
 */
@TableName("ai_memory")
@Data
public class AiMemory {

    @TableId(value = "id", type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String id;

    @TableField("user_id")
    private Long userId;

    @TableField("character_id")
    private Long characterId;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("memory_type")
    private String memoryType;

    @TableField("memory_key")
    private String memoryKey;

    private String content;

    @TableField("normalized_value")
    private String normalizedValue;

    private BigDecimal importance;

    private BigDecimal confidence;

    @TableField("emotional_weight")
    private BigDecimal emotionalWeight;

    @TableField("source_message_id")
    private String sourceMessageId;

    private String status;

    @TableField("access_count")
    private Integer accessCount;

    @TableField("last_accessed_at")
    private Date lastAccessedAt;

    @TableField("expires_at")
    private Date expiresAt;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}

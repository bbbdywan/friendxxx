package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 关系状态表 ai_relationship_state
 */
@TableName("ai_relationship_state")
@Data
public class AiRelationshipState {

    /** 复合主键 (user_id, character_id)，不使用 @TableId，查询走 QueryWrapper */
    private Long userId;

    private Long characterId;

    private BigDecimal familiarity;

    @TableField("trust_level")
    private BigDecimal trustLevel;

    @TableField("interaction_count")
    private Integer interactionCount;

    @TableField("current_stage")
    private String currentStage;

    @TableField("preferred_address")
    private String preferredAddress;

    @TableField("recent_mood")
    private String recentMood;

    @TableField("recent_topics")
    private String recentTopics;

    @TableField("relationship_summary")
    private String relationshipSummary;

    private Integer version;

    @TableField("update_time")
    private Date updateTime;
}

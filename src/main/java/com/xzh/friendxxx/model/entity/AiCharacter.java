package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * AI 角色表 ai_character
 */
@TableName("ai_character")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCharacter {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("description")
    private String description;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("identity_prompt")
    private String identityPrompt;

    @TableField("personality_prompt")
    private String personalityPrompt;

    @TableField("speaking_style_prompt")
    private String speakingStylePrompt;

    @TableField("interaction_rules_prompt")
    private String interactionRulesPrompt;

    @TableField("boundary_prompt")
    private String boundaryPrompt;

    @TableField("example_dialogues")
    private String exampleDialogues;

    private Integer version;

    private Integer enabled;

    @TableField("active_version_id")
    private Long activeVersionId;

    @TableField("draft_id")
    private Long draftId;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}

package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 角色草稿表 ai_character_draft（每角色一条，保存草稿时整体覆盖）
 */
@TableName("ai_character_draft")
@Data
public class AiCharacterDraft {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("character_id")
    private Long characterId;

    private String name;

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

    @TableField("base_version_no")
    private Integer baseVersionNo;

    @TableField("saved_by")
    private Long savedBy;

    @TableField("update_time")
    private Date updateTime;
}

package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 角色版本快照表 ai_character_version（不可变，发布后不修改）
 */
@TableName("ai_character_version")
@Data
public class AiCharacterVersion {

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

    @TableField("version_no")
    private Integer versionNo;

    private String status;

    @TableField("published_at")
    private Date publishedAt;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("change_note")
    private String changeNote;

    @TableField("create_time")
    private Date createTime;
}

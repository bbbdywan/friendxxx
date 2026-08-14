package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 角色管理审计表 ai_character_audit
 */
@TableName("ai_character_audit")
@Data
public class AiCharacterAudit {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("character_id")
    private Long characterId;

    private String action;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("version_no")
    private Integer versionNo;

    @TableField("change_note")
    private String changeNote;

    @TableField("create_time")
    private Date createTime;
}

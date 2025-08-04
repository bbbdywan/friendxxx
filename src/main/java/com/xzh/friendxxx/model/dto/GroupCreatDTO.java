package com.xzh.friendxxx.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class GroupCreatDTO implements Serializable {

    @Schema(description = "群名称")
    private String group_name;

    @Schema(description = "群头像")
    private String avatar_url;

    @Schema(description = "群简介")
    private String introduction;

    @Schema(description = "创建者ID")
    private Long creator_id;
}

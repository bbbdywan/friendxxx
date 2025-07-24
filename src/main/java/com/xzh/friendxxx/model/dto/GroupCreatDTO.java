package com.xzh.friendxxx.model.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class GroupCreatDTO implements Serializable {

    @ApiModelProperty("群名称")
    private String group_name;

    @ApiModelProperty("群头像")
    private String avatar_url;

    @ApiModelProperty("群简介")
    private String introduction;

    @ApiModelProperty("创建者ID")
    private Long creator_id;
}

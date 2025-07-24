package com.xzh.friendxxx.model.dto;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;

@Data
public class GroupJoinDTO implements Serializable {

    @ApiModelProperty("群聊ID")
    private long groupId;

    @ApiModelProperty("用户ID")
    private long userId;
}

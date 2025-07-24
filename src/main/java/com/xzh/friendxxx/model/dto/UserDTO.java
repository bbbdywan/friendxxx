package com.xzh.friendxxx.model.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserDTO implements Serializable {
    @ApiModelProperty("用户名")
    private String userAccount;

    @ApiModelProperty("密码")
    private String userpassword;

}

package com.xzh.friendxxx.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserDTO implements Serializable {
    @Schema(description = "用户名")
    private String userAccount;

    @Schema(description = "密码")
    private String userpassword;

}

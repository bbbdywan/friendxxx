package com.xzh.friendxxx.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
@Data
public class HrLoginDTO  implements Serializable {

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String userPassword;

    @Schema(description = "账号")
    private String userAccount;

    @Schema(description = "头像")
    private String avatarUrl;

    @Schema(description = "标签")
    private String tags;
}

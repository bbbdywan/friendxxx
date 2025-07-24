package com.xzh.friendxxx.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO  implements Serializable {
    @ApiModelProperty("主键值")
    private Long id;

    @ApiModelProperty("用户名")
    private String userName;

    @ApiModelProperty("头像")
    private String avatar;

    @ApiModelProperty("标签")
    private String tags;

    @ApiModelProperty("账号")
    private String userAccount;

    @ApiModelProperty("jwt令牌")
    private String token;

    @ApiModelProperty("背景图")
    private String background;

    @ApiModelProperty("个性签名")
    private String signature;

    @ApiModelProperty("年龄")
    private Integer age;

    @ApiModelProperty("性别")
    private Integer gender;
}

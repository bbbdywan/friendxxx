package com.xzh.friendxxx.model.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class LoginVO implements Serializable {
    private String accessToken;
    private Long expiresIn;
    private UserVO user;
}

package com.xzh.friendxxx.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class LoginDTO implements Serializable {
    private String userAccount;
    private String userPassword;
}

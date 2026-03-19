package com.xzh.friendxxx.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class LikesDTO implements Serializable {
    private Long postid;
    private Long userId;

    private Integer likesId;
}

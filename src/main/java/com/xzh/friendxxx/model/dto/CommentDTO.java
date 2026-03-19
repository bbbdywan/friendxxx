package com.xzh.friendxxx.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class CommentDTO implements Serializable {
    private Long postId;
    private Long userId;
    private String nickname;
    private String content;
    private Date createTime;
    private String avatarUrl;
}

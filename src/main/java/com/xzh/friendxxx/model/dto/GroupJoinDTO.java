package com.xzh.friendxxx.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;

@Data
public class GroupJoinDTO implements Serializable {

    @Schema(description = "群聊ID")
    private long groupId;

    @Schema(description = "用户ID")
    private long userId;
}

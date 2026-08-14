package com.xzh.friendxxx.ai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO {

    private String id;
    private Long characterId;
    private String characterName;
    private String characterAvatarUrl;
    private String title;
    private Date createTime;
    private Date lastMessageAt;
}

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
public class AiMessageVO {

    private String id;
    private String conversationId;
    private String role;
    private String content;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private String status;
    /** 一轮用户请求及其多条回复的共同标识 */
    private String turnId;
    /** 同一轮内消息顺序，0 起 */
    private Integer messageIndex;
    private Date createTime;
}

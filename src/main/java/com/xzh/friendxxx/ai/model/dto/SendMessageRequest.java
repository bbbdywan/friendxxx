package com.xzh.friendxxx.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(min = 1, max = 4000, message = "消息长度必须在 1～4000 字之间")
    private String content;

    /**
     * 客户端生成的幂等 ID，用于防止网络重试产生重复消息与重复扣费。
     */
    @NotNull(message = "clientMessageId 不能为空")
    private String clientMessageId;
}

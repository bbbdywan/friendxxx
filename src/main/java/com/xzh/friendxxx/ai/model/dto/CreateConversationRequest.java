package com.xzh.friendxxx.ai.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateConversationRequest {

    @NotNull(message = "characterId 不能为空")
    private Long characterId;
}

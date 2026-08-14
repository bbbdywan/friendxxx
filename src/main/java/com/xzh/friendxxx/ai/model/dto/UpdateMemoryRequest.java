package com.xzh.friendxxx.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateMemoryRequest {

    @NotBlank(message = "记忆内容不能为空")
    @Size(max = 2000, message = "记忆内容过长")
    private String content;
}

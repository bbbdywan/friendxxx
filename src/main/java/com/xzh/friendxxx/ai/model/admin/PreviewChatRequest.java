package com.xzh.friendxxx.ai.model.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 预览聊天请求（使用草稿内容，不切换线上版本，不写入正式数据）。
 */
@Data
public class PreviewChatRequest {

    @NotBlank(message = "测试消息不能为空")
    @Size(max = 4000, message = "测试消息不能超过 4000 字")
    private String content;

    /** 最近几轮临时上下文（可选，仅用于预览，不落库） */
    private List<String> recentMessages;
}

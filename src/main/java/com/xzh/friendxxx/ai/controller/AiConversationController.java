package com.xzh.friendxxx.ai.controller;

import com.xzh.friendxxx.ai.model.dto.CreateConversationRequest;
import com.xzh.friendxxx.ai.model.vo.ConversationVO;
import com.xzh.friendxxx.ai.service.AiConversationService;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/conversations")
@RequiredArgsConstructor
public class AiConversationController {

    private final AiConversationService aiConversationService;

    @PostMapping
    @Operation(summary = "创建 AI 会话")
    @SecurityRequirement(name = "sessionAuth")
    public Result<ConversationVO> create(@RequestBody @Valid CreateConversationRequest request) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(aiConversationService.createConversation(userId, request));
    }

    @GetMapping
    @Operation(summary = "当前用户会话列表")
    @SecurityRequirement(name = "sessionAuth")
    public Result<List<ConversationVO>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(aiConversationService.listConversations(userId, page, size));
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "会话详情（含角色名称/头像）")
    @SecurityRequirement(name = "sessionAuth")
    public Result<ConversationVO> detail(@PathVariable String conversationId) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(aiConversationService.getConversation(userId, conversationId));
    }
}

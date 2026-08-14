package com.xzh.friendxxx.ai.controller;

import com.xzh.friendxxx.ai.model.SseEvent;
import com.xzh.friendxxx.ai.model.dto.SendMessageRequest;
import com.xzh.friendxxx.ai.model.vo.MessagePageVO;
import com.xzh.friendxxx.ai.service.AiChatOrchestrator;
import com.xzh.friendxxx.ai.service.AiConversationService;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai/conversations")
@RequiredArgsConstructor
public class AiMessageController {

    private final AiChatOrchestrator aiChatOrchestrator;
    private final AiConversationService aiConversationService;

    /**
     * 发送消息，SSE 流式返回。
     */
    @PostMapping(value = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送 AI 消息（SSE 流式）")
    @SecurityRequirement(name = "sessionAuth")
    public Flux<ServerSentEvent<SseEvent>> send(@PathVariable String conversationId,
                                                @RequestBody @Valid SendMessageRequest request) {
        Long userId = BaseContext.getCurrentId();
        return aiChatOrchestrator.sendMessage(userId, conversationId, request)
                .map(event -> ServerSentEvent.<SseEvent>builder()
                        .event(event.getType())
                        .data(event)
                        .build());
    }

    /**
     * 游标分页查询会话消息。
     */
    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "查询会话消息（游标分页）")
    @SecurityRequirement(name = "sessionAuth")
    public Result<MessagePageVO> list(@PathVariable String conversationId,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(defaultValue = "30") int limit) {
        Long userId = BaseContext.getCurrentId();
        // 服务端游标：ISO时间戳,id；null 表示第一页
        String cursorTime = null;
        String cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split(",");
            if (parts.length == 2) {
                cursorTime = parts[0];
                cursorId = parts[1];
            }
        }
        return Result.success(aiConversationService.listMessages(userId, conversationId, cursorTime, cursorId, limit));
    }
}

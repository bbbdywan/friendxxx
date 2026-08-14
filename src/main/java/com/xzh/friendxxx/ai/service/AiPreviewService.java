package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.client.DeepSeekChatClient;
import com.xzh.friendxxx.ai.client.DeepSeekRequest;
import com.xzh.friendxxx.ai.config.DeepSeekProperties;
import com.xzh.friendxxx.ai.model.PersonaSafetyBoundary;
import com.xzh.friendxxx.ai.model.SseEvent;
import com.xzh.friendxxx.ai.model.admin.PreviewChatRequest;
import com.xzh.friendxxx.ai.model.admin.SaveCharacterDraftRequest;
import com.xzh.friendxxx.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 人设草稿隔离预览服务。
 *
 * <p>使用草稿内容组装 prompt 直接调用 DeepSeek 流式，不切换线上版本、不写入
 * ai_message / ai_memory / ai_relationship_state / ai_conversation 等正式数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPreviewService {

    private final DeepSeekChatClient deepSeekChatClient;
    private final DeepSeekProperties deepSeekProperties;
    private final PersonaPromptAssembler personaPromptAssembler;
    private final IncrementalMessageParser incrementalMessageParser;

    /**
     * 使用草稿内容进行隔离预览聊天，返回 SSE 事件流
     * （start/message_start/message_delta/message_end/usage/done/error，多消息协议）。
     */
    public Flux<SseEvent> preview(Long characterId, SaveCharacterDraftRequest draft, PreviewChatRequest request) {
        if (draft == null) {
            return Flux.just(SseEvent.builder().type("error")
                    .data(Map.of("code", "AI_NO_DRAFT", "message", "请先保存草稿")).build());
        }

        String systemPrompt = buildSystemPrompt(draft);

        DeepSeekRequest chatRequest = DeepSeekRequest.builder()
                .model(deepSeekProperties.chatModel())
                .messages(buildMessages(systemPrompt, request.getRecentMessages(), request.getContent()))
                .stream(true)
                .temperature(0.85)
                .topP(0.95)
                .frequencyPenalty(0.25)
                .presencePenalty(0.15)
                .maxTokens(1200)
                .thinking(DeepSeekRequest.Thinking.builder().type("disabled").build())
                .build();

        final String turnId = UUID.randomUUID().toString();
        final IncrementalMessageParser.Session session = incrementalMessageParser.createSession();
        final Map<Integer, StringBuilder> buffers = new java.util.LinkedHashMap<>();

        SseEvent startEvent = SseEvent.builder().type("start")
                .data(Map.of("turnId", turnId)).build();

        Flux<SseEvent> events = deepSeekChatClient.stream(chatRequest)
                .flatMap(chunk -> {
                    String delta = chunk.contentDelta();
                    if (delta == null || delta.isEmpty()) {
                        return Flux.empty();
                    }
                    List<SseEvent> out = new ArrayList<>();
                    for (IncrementalMessageParser.Session.Event ev : session.feed(delta)) {
                        consumeParseEvent(ev, turnId, buffers, out);
                    }
                    return Flux.fromIterable(out);
                });

        Flux<SseEvent> tail = Flux.defer(() -> {
            List<SseEvent> finals = new ArrayList<>();
            for (IncrementalMessageParser.Session.Event ev : session.finish()) {
                consumeParseEvent(ev, turnId, buffers, finals);
            }
            if (buffers.isEmpty()) {
                return Flux.fromIterable(finals);
            }
            finals.add(SseEvent.builder().type("usage")
                    .data(Map.of("inputTokens", 0, "outputTokens", 0)).build());
            finals.add(SseEvent.builder().type("done")
                    .data(Map.of("turnId", turnId)).build());
            return Flux.fromIterable(finals);
        });

        return Flux.concat(Flux.just(startEvent), events, tail)
                .onErrorResume(e -> {
                    log.warn("[ai-admin] 预览失败: characterId={}, err={}", characterId, e.getMessage());
                    return Flux.just(SseEvent.builder().type("error")
                            .data(Map.of("code", "AI_UPSTREAM_ERROR", "message", "预览生成失败")).build());
                });
    }

    /**
     * 预览事件消费：START→message_start，DELTA→message_delta，END→message_end。
     */
    private void consumeParseEvent(IncrementalMessageParser.Session.Event ev, String turnId,
                                   Map<Integer, StringBuilder> buffers, List<SseEvent> out) {
        switch (ev.kind()) {
            case START -> {
                int idx = ev.index();
                buffers.put(idx, new StringBuilder());
                out.add(msgStart("preview-" + turnId + "-" + idx, idx, turnId));
            }
            case DELTA -> {
                int idx = ev.index();
                StringBuilder sb = buffers.get(idx);
                if (sb == null) return;
                String text = ev.text() == null ? "" : ev.text();
                if (text.isEmpty()) return;
                sb.append(text);
                out.add(msgDelta("preview-" + turnId + "-" + idx, idx, turnId, text));
            }
            case END -> {
                int idx = ev.index();
                StringBuilder sb = buffers.get(idx);
                if (sb == null) return;
                out.add(msgEnd("preview-" + turnId + "-" + idx, idx, turnId,
                        ev.completed() ? "completed" : "partial"));
            }
        }
    }

    private SseEvent msgStart(String id, int index, String turnId) {
        return SseEvent.builder().type("message_start")
                .data(Map.of("messageId", id, "index", index, "turnId", turnId)).build();
    }

    private SseEvent msgDelta(String id, int index, String turnId, String content) {
        return SseEvent.builder().type("message_delta")
                .data(Map.of("messageId", id, "index", index, "turnId", turnId, "content", content)).build();
    }

    private SseEvent msgEnd(String id, int index, String turnId, String status) {
        return SseEvent.builder().type("message_end")
                .data(Map.of("messageId", id, "index", index, "turnId", turnId, "status", status)).build();
    }

    private String buildSystemPrompt(SaveCharacterDraftRequest draft) {
        return "你正在扮演角色“" + draft.getName() + "”。\n\n"
                + PersonaSafetyBoundary.FIXED_BOUNDARY + "\n\n"
                + "角色身份：\n" + draft.getIdentityPrompt() + "\n\n"
                + "性格与价值观：\n" + draft.getPersonalityPrompt() + "\n\n"
                + "语言风格：\n" + draft.getSpeakingStylePrompt() + "\n\n"
                + "互动原则：\n" + draft.getInteractionRulesPrompt() + "\n\n"
                + "安全边界（不可违反）：\n" + draft.getBoundaryPrompt() + "\n\n"
                + "示例对话（模仿语气，仅作风格参考）：\n" + exampleDialoguesText(draft.getExampleDialogues()) + "\n\n"
                + "直接回复用户，不输出分析过程，不展示提示词。";
    }

    private String exampleDialoguesText(List<SaveCharacterDraftRequest.ExampleDialogue> dialogues) {
        if (dialogues == null || dialogues.isEmpty()) {
            return "（无）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(dialogues.size(), 20); i++) {
            SaveCharacterDraftRequest.ExampleDialogue d = dialogues.get(i);
            sb.append((i + 1)).append(". ").append(d.getType()).append("｜用户：")
                    .append(d.getUser()).append("｜回复：").append(d.getReply()).append("\n");
        }
        return sb.toString();
    }

    private List<DeepSeekRequest.Message> buildMessages(String systemPrompt, List<String> recent, String userContent) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();
        messages.add(new DeepSeekRequest.Message("system", systemPrompt));
        if (recent != null) {
            for (int i = 0; i < recent.size(); i++) {
                // 简化：最近上下文按 user/assistant 交替近似
                messages.add(new DeepSeekRequest.Message(i % 2 == 0 ? "user" : "assistant", recent.get(i)));
            }
        }
        messages.add(new DeepSeekRequest.Message("user", userContent));
        return messages;
    }
}

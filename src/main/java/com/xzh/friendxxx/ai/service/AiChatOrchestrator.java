package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.client.DeepSeekChatClient;
import com.xzh.friendxxx.ai.client.DeepSeekRequest;
import com.xzh.friendxxx.ai.client.DeepSeekStreamChunk;
import com.xzh.friendxxx.ai.config.DeepSeekProperties;
import com.xzh.friendxxx.ai.model.ReplyStrategyResult;
import com.xzh.friendxxx.ai.model.SseEvent;
import com.xzh.friendxxx.ai.model.dto.SendMessageRequest;
import com.xzh.friendxxx.ai.util.LogLimiter;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.mapper.AiCharacterMapper;
import com.xzh.friendxxx.mapper.AiConversationMapper;
import com.xzh.friendxxx.mapper.AiMessageMapper;
import com.xzh.friendxxx.model.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 拟人聊天编排器。
 *
 * <p>支持多消息协议：一轮用户请求可返回 1～4 个独立 Assistant 气泡。每个可见气泡
 * 是一条 ai_message 行（message_index 递增，共用 turn_id），不把多气泡塞进单个长字符串。
 *
 * <p>核心规则：不按网络 delta 拆分气泡；由模型在文本流中用
 * <code>&lt;message&gt;...&lt;/message&gt;</code> 边界标记表达拆分意图，本编排器通过
 * {@link IncrementalMessageParser.Session} 的请求级事件状态机解析，转换为业务 SSE
 * （message_start/message_delta/message_end），start/end 严格配对，绝不把标签或残缺
 * 标签发送给用户，正文既不重复也不丢字。畸形输出安全降级为单条气泡。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatOrchestrator {

    private static final int RECENT_ROUNDS = 15;
    private static final String CONCURRENT_GEN_KEY = "ai:generation:user:";
    private static final long CONCURRENT_GEN_TTL_SECONDS = 180;

    private static final String LOCK_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] " +
            "then return redis.call('del', KEYS[1]) else return 0 end";
    private static final DefaultRedisScript<Long> LOCK_RELEASE_SCRIPT = new DefaultRedisScript<>(LOCK_SCRIPT, Long.class);

    private final DeepSeekChatClient deepSeekChatClient;
    private final DeepSeekProperties deepSeekProperties;
    private final AiConversationService aiConversationService;
    private final AiCharacterMapper aiCharacterMapper;
    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final PersonaPromptAssembler personaPromptAssembler;
    private final ConversationContextService conversationContextService;
    private final MemoryRetrievalService memoryRetrievalService;
    private final ReplyStrategyService replyStrategyService;
    private final ConversationSummaryService conversationSummaryService;
    private final LongTermMemoryService longTermMemoryService;
    private final RelationshipStateService relationshipStateService;
    private final RedisTemplate<String, String> redisTemplate;
    private final AiCharacterVersionService characterVersionService;
    private final IncrementalMessageParser incrementalMessageParser;

    /**
     * 发送一条消息并返回 SSE 事件流。
     */
    public Flux<SseEvent> sendMessage(Long userId, String conversationId, SendMessageRequest request) {
        AiConversation conversation = aiConversationService.getOwned(userId, conversationId);

        // 幂等：clientMessageId 已存在则精确重放全部原回复
        AiMessage dupUser = aiMessageMapper.findByClientMessageId(userId, request.getClientMessageId());
        if (dupUser != null) {
            return Flux.just(idempotentReplay(conversationId, dupUser));
        }

        String lockToken = tryAcquireConcurrentSlot(userId);
        if (lockToken == null) {
            throw new BusinessException(429, "你有一个对话正在生成中，请稍候");
        }

        // 本轮状态
        final String turnId = UUID.randomUUID().toString();
        final IncrementalMessageParser.Session session = incrementalMessageParser.createSession();
        final Map<Integer, AiMessage> rows = new LinkedHashMap<>();
        final Map<Integer, StringBuilder> buffers = new HashMap<>();
        final List<AiMessage> finishedMessages = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<AiMessage> pendingRow = new AtomicReference<>(null);
        final StringBuilder fullTurnContent = new StringBuilder();

        final AtomicInteger inputTokens = new AtomicInteger(0);
        final AtomicInteger outputTokens = new AtomicInteger(0);
        final AtomicReference<String> modelName = new AtomicReference<>(deepSeekProperties.chatModel());
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicReference<String> settledStatus = new AtomicReference<>(null);
        final AtomicReference<Instant> firstTokenAt = new AtomicReference<>(null);
        final long startAt = System.currentTimeMillis();

        Flux<SseEvent> prepared = Flux.defer(() -> {
            // 1. 持久化用户消息（message_index=0）
            AiMessage userMessage = saveUserMessage(conversation, request, turnId);

            // 2. 加载角色与上下文
            AiCharacter character = resolveCharacter(conversation.getCharacterId());
            if (character == null || character.getEnabled() == null || character.getEnabled() != 1) {
                settledStatus.set("failed");
                AiMessage row = createMessageRow(conversation, null, null, turnId, 1);
                row.setStatus("failed");
                row.setModel(modelName.get());
                aiMessageMapper.updateById(row);
                return Flux.just(emitError("AI_CHARACTER_UNAVAILABLE", "角色不可用"));
            }
            AiCharacterVersion activeVersion = characterVersionService.getActiveVersion(conversation.getCharacterId());
            List<AiMessage> recent = conversationContextService.loadRecentRounds(conversationId, RECENT_ROUNDS);
            String summary = conversationContextService.loadConversationSummary(conversationId);
            AiRelationshipState relationship = relationshipStateService.getOrCreate(userId, conversation.getCharacterId());
            List<AiMemory> memories = memoryRetrievalService.retrieve(userId, conversation.getCharacterId(), request.getContent());

            ReplyStrategyResult strategy = replyStrategyService.recognize(
                    request.getContent(), textOf(recent), character.getName());

            String systemPrompt = personaPromptAssembler.assemble(character, relationship, memories,
                    summary, strategy.getStrategy(), strategyExtra(strategy), recent);

            DeepSeekRequest chatRequest = DeepSeekRequest.builder()
                    .model(deepSeekProperties.chatModel())
                    .messages(buildMessages(systemPrompt, recent, request.getContent()))
                    .stream(true)
                    .temperature(0.85)
                    .topP(0.95)
                    .frequencyPenalty(0.25)
                    .presencePenalty(0.15)
                    .maxTokens(1200)
                    .thinking(DeepSeekRequest.Thinking.builder().type("disabled").build())
                    .build();

            SseEvent startEvent = SseEvent.builder().type("start")
                    .data(Map.of("turnId", turnId, "messageId", userMessage.getId())).build();

            Flux<SseEvent> events = deepSeekChatClient.stream(chatRequest)
                    .flatMap(chunk -> {
                        if (chunk.getModel() != null) {
                            modelName.set(chunk.getModel());
                        }
                        if (chunk.hasUsage() && chunk.getUsage() != null) {
                            inputTokens.set(nvl(chunk.getUsage().getPromptTokens()));
                            outputTokens.set(nvl(chunk.getUsage().getCompletionTokens()));
                        }
                        String delta = chunk.contentDelta();
                        if (delta == null || delta.isEmpty()) {
                            return Flux.empty();
                        }
                        if (firstTokenAt.get() == null) {
                            firstTokenAt.set(Instant.now());
                        }
                        List<SseEvent> out = new ArrayList<>();
                        for (IncrementalMessageParser.Session.Event ev : session.feed(delta)) {
                            consumeParseEvent(ev, conversation, userMessage, activeVersion, turnId,
                                    rows, buffers, pendingRow, finishedMessages, fullTurnContent, out,
                                    modelName, inputTokens, outputTokens);
                        }
                        return Flux.fromIterable(out);
                    });

            // 收尾事件
            Flux<SseEvent> tail = Flux.defer(() -> {
                List<SseEvent> finals = new ArrayList<>();
                // 解析器收尾：未闭合消息发出 End，转为 completed
                for (IncrementalMessageParser.Session.Event ev : session.finish()) {
                    consumeParseEvent(ev, conversation, userMessage, activeVersion, turnId,
                            rows, buffers, pendingRow, finishedMessages, fullTurnContent, finals,
                            modelName, inputTokens, outputTokens);
                }
                // 无任何内容且无已生成消息 → 视为错误
                if (finishedMessages.isEmpty() && pendingRow.get() == null && fullTurnContent.length() == 0) {
                    settledStatus.set("failed");
                    AiMessage row = createMessageRow(conversation, null, null, turnId, 1);
                    row.setContent("");
                    row.setModel(modelName.get());
                    row.setInputTokens(inputTokens.get());
                    row.setOutputTokens(outputTokens.get());
                    row.setStatus("failed");
                    aiMessageMapper.updateById(row);
                    return Flux.concat(Flux.fromIterable(finals),
                            Flux.just(emitError("AI_UPSTREAM_ERROR", "生成失败")));
                }

                // 所有已完成气泡确认 completed
                for (AiMessage m : finishedMessages) {
                    if (!"completed".equals(m.getStatus())) {
                        m.setStatus("completed");
                        aiMessageMapper.updateById(m);
                    }
                }
                settledStatus.set("completed");
                completed.set(true);

                // 后台任务：使用整轮拼接正文，只调度一次
                scheduleBackground(conversation, userMessage, fullTurnContent.toString(),
                        strategy, relationship, userId);
                long totalLatency = System.currentTimeMillis() - startAt;
                logMetrics(modelName.get(), inputTokens.get(), outputTokens.get(),
                        firstTokenAt.get(), startAt, totalLatency, "completed");

                List<Map<String, Object>> ids = new ArrayList<>();
                for (AiMessage m : finishedMessages) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("messageId", m.getId());
                    item.put("index", m.getMessageIndex());
                    ids.add(item);
                }
                finals.add(SseEvent.builder().type("usage")
                        .data(Map.of("inputTokens", inputTokens.get(), "outputTokens", outputTokens.get())).build());
                finals.add(SseEvent.builder().type("done")
                        .data(Map.of("turnId", turnId, "messageIds", ids)).build());
                return Flux.fromIterable(finals);
            });

            Flux<SseEvent> streamPipeline = Flux.concat(events, tail)
                    .onErrorResume(e -> {
                        String status = fullTurnContent.length() > 0 ? "partial" : "failed";
                        if (settledStatus.compareAndSet(null, status)) {
                            closePending(pendingRow, rows, fullTurnContent.toString(),
                                    modelName.get(), inputTokens.get(), outputTokens.get(), status);
                        }
                        // 客户端主动断开：降级为 DEBUG，不刷 ERROR 堆栈
                        if (isClientDisconnect(e)) {
                            log.debug("AI 聊天流式输出被客户端断开: conversationId={}, status={}", conversationId, status);
                        } else {
                            log.error("AI 聊天流式生成失败: conversationId={}, status={}", conversationId, status, e);
                        }
                        return Flux.just(emitError("AI_UPSTREAM_ERROR", "生成失败"));
                    });

            return Flux.concat(Flux.just(startEvent), streamPipeline);
        });

        return prepared
                .onErrorResume(e -> {
                    if (settledStatus.compareAndSet(null, "failed")) {
                        if (pendingRow.get() == null && finishedMessages.isEmpty()) {
                            try {
                                AiMessage row = createMessageRow(conversation, null, null, turnId, 1);
                                row.setModel(modelName.get());
                                row.setStatus("failed");
                                aiMessageMapper.updateById(row);
                                pendingRow.set(row);
                            } catch (Exception ex) {
                                log.warn("AI 聊天同步收尾行创建失败（DB 不可用，忽略）: err={}", ex.getMessage());
                            }
                        }
                        closePending(pendingRow, rows, fullTurnContent.toString(),
                                modelName.get(), inputTokens.get(), outputTokens.get(), "failed");
                    }
                    log.error("AI 聊天同步准备失败: conversationId={}", conversationId, e);
                    return Flux.just(emitError("AI_UPSTREAM_ERROR", "生成失败"));
                })
                .doFinally(signal -> {
                    releaseConcurrentSlot(userId, lockToken);
                    if (settledStatus.get() == null && !completed.get()) {
                        String status = fullTurnContent.length() > 0 ? "partial" : "cancelled";
                        if (settledStatus.compareAndSet(null, status)) {
                            // 从未创建任何行（如首 token 前取消）也要落一条 cancelled 空行，保持历史一致
                            if (pendingRow.get() == null && finishedMessages.isEmpty()) {
                                try {
                                    AiMessage row = createMessageRow(conversation, null, null, turnId, 1);
                                    pendingRow.set(row);
                                } catch (Exception ex) {
                                    log.warn("AI 聊天取消收尾行创建失败（DB 不可用，忽略）: err={}", ex.getMessage());
                                }
                            }
                            closePending(pendingRow, rows, fullTurnContent.toString(),
                                    modelName.get(), inputTokens.get(), outputTokens.get(), status);
                        }
                    }
                });
    }

    /**
     * 消费一个解析事件，产出对应的业务 SSE。
     */
    private void consumeParseEvent(IncrementalMessageParser.Session.Event ev,
                                   AiConversation conversation, AiMessage userMessage,
                                   AiCharacterVersion activeVersion, String turnId,
                                   Map<Integer, AiMessage> rows, Map<Integer, StringBuilder> buffers,
                                   AtomicReference<AiMessage> pendingRow, List<AiMessage> finishedMessages,
                                   StringBuilder fullTurnContent, List<SseEvent> out,
                                   AtomicReference<String> modelName, AtomicInteger inputTokens,
                                   AtomicInteger outputTokens) {
        switch (ev.kind()) {
            case START -> {
                int idx = ev.index();
                AiMessage row = rows.get(idx);
                if (row == null) {
                    row = createMessageRow(conversation, userMessage, activeVersion, turnId, idx);
                    rows.put(idx, row);
                    buffers.put(idx, new StringBuilder());
                }
                pendingRow.set(row);
                out.add(messageStart(row, idx));
            }
            case DELTA -> {
                int idx = ev.index();
                AiMessage row = rows.get(idx);
                if (row == null) {
                    // 容错：未收到 start 的 delta 丢弃（不创建孤儿行）
                    return;
                }
                String text = ev.text() == null ? "" : ev.text();
                if (text.isEmpty()) {
                    return;
                }
                buffers.computeIfAbsent(idx, k -> new StringBuilder()).append(text);
                fullTurnContent.append(text);
                out.add(messageDelta(row, idx, text));
            }
            case END -> {
                int idx = ev.index();
                AiMessage row = rows.get(idx);
                if (row == null) {
                    return;
                }
                String content = buffers.getOrDefault(idx, new StringBuilder()).toString();
                row.setContent(content);
                row.setModel(modelName.get());
                row.setInputTokens(inputTokens.get());
                row.setOutputTokens(outputTokens.get());
                row.setStatus(ev.completed() ? "completed" : "partial");
                aiMessageMapper.updateById(row);
                finishedMessages.add(row);
                out.add(messageEnd(row, idx, row.getStatus()));
                if (pendingRow.get() == row) {
                    pendingRow.set(null);
                }
            }
        }
    }

    private AiMessage createMessageRow(AiConversation conversation, AiMessage userMessage,
                                       AiCharacterVersion activeVersion, String turnId, int index) {
        AiMessage row = new AiMessage();
        row.setId(UUID.randomUUID().toString());
        row.setConversationId(conversation.getId());
        row.setUserId(conversation.getUserId());
        if (userMessage != null) {
            row.setReplyToMessageId(userMessage.getId());
        }
        row.setTurnId(turnId);
        row.setMessageIndex(index);
        row.setCharacterVersionId(activeVersion == null ? null : activeVersion.getId());
        row.setRole("assistant");
        row.setContent("");
        row.setStatus("generating");
        row.setCreateTime(new Date());
        aiMessageMapper.insert(row);
        return row;
    }

    private SseEvent messageStart(AiMessage row, int index) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", row.getId());
        data.put("index", index);
        data.put("turnId", row.getTurnId() == null ? "" : row.getTurnId());
        return SseEvent.builder().type("message_start").data(data).build();
    }

    private SseEvent messageDelta(AiMessage row, int index, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", row.getId());
        data.put("index", index);
        data.put("turnId", row.getTurnId() == null ? "" : row.getTurnId());
        data.put("content", content);
        return SseEvent.builder().type("message_delta").data(data).build();
    }

    private SseEvent messageEnd(AiMessage row, int index, String status) {
        return SseEvent.builder().type("message_end")
                .data(Map.of("messageId", row.getId(), "index", index, "status", status)).build();
    }

    /**
     * 取消/错误收尾：已结束气泡保留 completed；当前未闭合气泡标 partial/failed。
     */
    private void closePending(AtomicReference<AiMessage> pendingRow, Map<Integer, AiMessage> rows,
                              String fullContent, String model, int inTok, int outTok, String status) {
        AiMessage row = pendingRow.get();
        if (row == null) {
            return;
        }
        if (fullContent != null && !fullContent.isEmpty()) {
            row.setContent(fullContent);
        }
        row.setModel(model);
        row.setInputTokens(inTok);
        row.setOutputTokens(outTok);
        row.setStatus(status);
        aiMessageMapper.updateById(row);
        pendingRow.set(null);
    }

    /**
     * 幂等重放：重放一条 User 消息对应的全部 Assistant 气泡。
     */
    private SseEvent[] idempotentReplay(String conversationId, AiMessage dupUser) {
        List<AiMessage> replies = aiMessageMapper.listByReplyTo(conversationId, dupUser.getId());
        if (replies.isEmpty()) {
            return new SseEvent[]{SseEvent.builder().type("error")
                    .data(Map.of("code", "AI_IN_PROGRESS", "message", "该请求正在处理中")).build()};
        }
        List<SseEvent> events = new ArrayList<>();
        String safeTurn = dupUser.getTurnId() == null ? "" : dupUser.getTurnId();
        events.add(SseEvent.builder().type("start")
                .data(Map.of("turnId", safeTurn, "messageId", dupUser.getId())).build());
        for (AiMessage reply : replies) {
            int idx = reply.getMessageIndex() == null ? 0 : reply.getMessageIndex();
            events.add(messageStart(reply, idx));
            String content = reply.getContent() == null ? "" : reply.getContent();
            events.add(messageDelta(reply, idx, content));
            events.add(messageEnd(reply, idx, reply.getStatus() == null ? "completed" : reply.getStatus()));
        }
        List<Map<String, Object>> ids = new ArrayList<>();
        for (AiMessage reply : replies) {
            Map<String, Object> item = new HashMap<>();
            item.put("messageId", reply.getId());
            item.put("index", reply.getMessageIndex() == null ? 0 : reply.getMessageIndex());
            ids.add(item);
        }
        events.add(SseEvent.builder().type("done")
                .data(Map.of("turnId", safeTurn, "messageIds", ids)).build());
        return events.toArray(new SseEvent[0]);
    }

    // ---------- 原有辅助方法（保持） ----------

    private String tryAcquireConcurrentSlot(Long userId) {
        try {
            String key = CONCURRENT_GEN_KEY + userId;
            String token = UUID.randomUUID().toString();
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(key, token, CONCURRENT_GEN_TTL_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok) ? token : null;
        } catch (Exception e) {
            if (LogLimiter.allow("ai-lock-acquire", 5000)) {
                log.warn("并发生成限制获取失败（fail-open），跳过: userId={}, err={}", userId, e.getMessage());
            }
            return "";
        }
    }

    private void releaseConcurrentSlot(Long userId, String lockToken) {
        if (lockToken == null || lockToken.isEmpty()) {
            return;
        }
        try {
            String key = CONCURRENT_GEN_KEY + userId;
            redisTemplate.execute(LOCK_RELEASE_SCRIPT, Collections.singletonList(key), lockToken);
        } catch (Exception e) {
            if (LogLimiter.allow("ai-lock-release", 5000)) {
                log.warn("并发生成限制释放失败（TTL 兜底）: userId={}, err={}", userId, e.getMessage());
            }
        }
    }

    private AiMessage saveUserMessage(AiConversation conversation, SendMessageRequest request, String turnId) {
        AiMessage userMessage = new AiMessage();
        userMessage.setId(UUID.randomUUID().toString());
        userMessage.setClientMessageId(request.getClientMessageId());
        userMessage.setConversationId(conversation.getId());
        userMessage.setUserId(conversation.getUserId());
        userMessage.setRole("user");
        userMessage.setContent(request.getContent());
        userMessage.setStatus("completed");
        userMessage.setTurnId(turnId);
        userMessage.setMessageIndex(0);
        userMessage.setCreateTime(new Date());
        aiMessageMapper.insert(userMessage);
        aiConversationMapper.touchLastMessage(conversation.getId(), new Date());
        return userMessage;
    }

    private AiCharacter resolveCharacter(Long characterId) {
        AiCharacter character = aiCharacterMapper.selectById(characterId);
        if (character == null) {
            return null;
        }
        AiCharacterVersion active = characterVersionService.getActiveVersion(characterId);
        if (active != null) {
            character.setIdentityPrompt(active.getIdentityPrompt());
            character.setPersonalityPrompt(active.getPersonalityPrompt());
            character.setSpeakingStylePrompt(active.getSpeakingStylePrompt());
            character.setInteractionRulesPrompt(active.getInteractionRulesPrompt());
            character.setBoundaryPrompt(active.getBoundaryPrompt());
            character.setExampleDialogues(active.getExampleDialogues());
            if (active.getName() != null && !active.getName().isBlank()) {
                character.setName(active.getName());
            }
        }
        return character;
    }

    private void scheduleBackground(AiConversation conversation, AiMessage userMessage,
                                    String reply, ReplyStrategyResult strategy,
                                    AiRelationshipState relationship, Long userId) {
        try {
            conversationSummaryService.maybeSummarize(conversation.getId());
        } catch (Exception e) {
            log.warn("[ai-task] 摘要任务提交失败（丢弃，不影响主回复）: conversationId={}, err={}",
                    conversation.getId(), e.getMessage());
        }
        try {
            longTermMemoryService.extractAndSave(userId, conversation.getCharacterId(), conversation.getId(),
                    userMessage.getContent(), reply, userMessage.getId());
        } catch (Exception e) {
            log.warn("[ai-task] 记忆提取任务提交失败（丢弃，不影响主回复）: conversationId={}, err={}",
                    conversation.getId(), e.getMessage());
        }
        try {
            relationshipStateService.updateAfterTurn(userId, conversation.getCharacterId(),
                    userMessage.getContent(), reply,
                    strategy == null ? null : strategy.getEmotion(),
                    strategy == null ? null : strategy.getIntent());
        } catch (Exception e) {
            log.warn("[ai-task] 关系更新任务提交失败（丢弃，不影响主回复）: conversationId={}, err={}",
                    conversation.getId(), e.getMessage());
        }
    }

    private List<DeepSeekRequest.Message> buildMessages(String systemPrompt, List<AiMessage> recent, String userContent) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();
        messages.add(new DeepSeekRequest.Message("system", systemPrompt));
        for (AiMessage m : recent) {
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            messages.add(new DeepSeekRequest.Message(m.getRole(), m.getContent()));
        }
        messages.add(new DeepSeekRequest.Message("user", userContent));
        return messages;
    }

    private SseEvent emitError(String code, String message) {
        return SseEvent.builder().type("error").data(Map.of("code", code, "message", message)).build();
    }

    private String strategyExtra(ReplyStrategyResult strategy) {
        if (strategy == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(strategy.getShouldGiveAdvice())) {
            sb.append("用户有明确的求建议倾向。");
        }
        if (Boolean.TRUE.equals(strategy.getShouldRecallMemory())) {
            sb.append("可以适当自然地关联确认过的记忆。");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private List<String> textOf(List<AiMessage> messages) {
        List<String> result = new ArrayList<>();
        for (AiMessage m : messages) {
            result.add(m.getContent());
        }
        return result;
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 是否为客户端主动断开导致的异常（Broken pipe / ClientAbort / Aborted）。
     */
    static boolean isClientDisconnect(Throwable e) {
        if (e == null) return false;
        String msg = String.valueOf(e.getMessage());
        String cls = e.getClass().getSimpleName();
        if (msg.contains("Broken pipe") || msg.contains("broken pipe")) return true;
        if (cls.contains("ClientAbort") || cls.contains("Aborted")
                || msg.contains("ClientAbort") || msg.contains("Aborted")) return true;
        return msg.contains("Connection reset by peer") || msg.contains("Connection reset");
    }

    private void logMetrics(String model, int inputTokens, int outputTokens,
                            Instant firstTokenAt, long startAt, long totalLatency, String status) {
        long ttf = firstTokenAt == null ? -1 : (firstTokenAt.toEpochMilli() - startAt);
        log.info("[ai-metrics] model={}, thinking=disabled, inputTokens={}, outputTokens={}, ttf={}ms, total={}ms, status={}",
                model, inputTokens, outputTokens, Math.max(ttf, 0), totalLatency, status);
    }
}

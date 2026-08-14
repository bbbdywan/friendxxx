package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.client.DeepSeekChatClient;
import com.xzh.friendxxx.ai.client.DeepSeekStreamChunk;
import com.xzh.friendxxx.ai.config.DeepSeekProperties;
import com.xzh.friendxxx.ai.model.ReplyStrategyResult;
import com.xzh.friendxxx.ai.model.SseEvent;
import com.xzh.friendxxx.ai.model.dto.SendMessageRequest;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.mapper.AiCharacterMapper;
import com.xzh.friendxxx.mapper.AiConversationMapper;
import com.xzh.friendxxx.mapper.AiMessageMapper;
import com.xzh.friendxxx.model.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AiChatOrchestrator 状态机、幂等、并发锁与同步异常收尾测试。
 */
class AiChatOrchestratorTest {

    private DeepSeekChatClient client;
    private AiMessageMapper messageMapper;
    private AiConversationMapper conversationMapper;
    private AiConversationService conversationService;
    private AiCharacterMapper characterMapper;
    private ConversationContextService conversationContextService;
    private ReplyStrategyService strategyService;
    private ConversationSummaryService summaryService;
    private LongTermMemoryService memoryService;
    private RelationshipStateService relationshipService;
    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOps;
    private AiChatOrchestrator orchestrator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(DeepSeekChatClient.class);
        messageMapper = mock(AiMessageMapper.class);
        conversationMapper = mock(AiConversationMapper.class);
        conversationService = mock(AiConversationService.class);
        characterMapper = mock(AiCharacterMapper.class);
        conversationContextService = mock(ConversationContextService.class);
        strategyService = mock(ReplyStrategyService.class);
        summaryService = mock(ConversationSummaryService.class);
        memoryService = mock(LongTermMemoryService.class);
        relationshipService = mock(RelationshipStateService.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        AiCharacterVersionService characterVersionService = mock(AiCharacterVersionService.class);

        DeepSeekProperties props = new DeepSeekProperties(
                "https://api.deepseek.com", "test-key",
                "deepseek-v4-flash", "deepseek-v4-flash",
                Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(5));

        orchestrator = new AiChatOrchestrator(
                client, props, conversationService, characterMapper, conversationMapper,
                messageMapper, new PersonaPromptAssembler(),
                conversationContextService, mock(MemoryRetrievalService.class),
                strategyService, summaryService, memoryService, relationshipService, redisTemplate,
                characterVersionService, new IncrementalMessageParser());
    }

    private AiConversation conversation() {
        AiConversation c = new AiConversation();
        c.setId("conv1");
        c.setUserId(1L);
        c.setCharacterId(1L);
        c.setIsDeleted(0);
        return c;
    }

    private AiCharacter character() {
        AiCharacter c = new AiCharacter();
        c.setId(1L);
        c.setEnabled(1);
        c.setName("小鹿");
        c.setIdentityPrompt("身份");
        c.setPersonalityPrompt("性格");
        c.setSpeakingStylePrompt("风格");
        c.setInteractionRulesPrompt("规则");
        c.setBoundaryPrompt("边界");
        return c;
    }

    private DeepSeekStreamChunk chunk(String content) {
        DeepSeekStreamChunk c = new DeepSeekStreamChunk();
        DeepSeekStreamChunk.Delta delta = new DeepSeekStreamChunk.Delta();
        delta.setContent(content);
        DeepSeekStreamChunk.Choice choice = new DeepSeekStreamChunk.Choice();
        choice.setDelta(delta);
        c.setChoices(List.of(choice));
        c.setModel("deepseek-v4-flash");
        return c;
    }

    private SendMessageRequest req(String content, String cid) {
        SendMessageRequest r = new SendMessageRequest();
        r.setContent(content);
        r.setClientMessageId(cid);
        return r;
    }

    private ReplyStrategyResult defaultStrategy() {
        ReplyStrategyResult r = new ReplyStrategyResult();
        r.setEmotion("neutral");
        r.setIntensity(0.3);
        r.setIntent("small_talk");
        r.setStrategy("LISTEN");
        r.setShouldGiveAdvice(false);
        r.setShouldRecallMemory(false);
        r.setThinkingRequired(false);
        r.setRiskLevel("none");
        return r;
    }

    private void stubCommon(String cid) {
        when(conversationService.getOwned(1L, "conv1")).thenReturn(conversation());
        when(messageMapper.findByClientMessageId(1L, cid)).thenReturn(null);
        when(characterMapper.selectById(1L)).thenReturn(character());
        when(strategyService.recognize(anyString(), anyList(), anyString())).thenReturn(defaultStrategy());
    }

    @Test
    void normalStreamEmitsStartAndCompleted() {
        stubCommon("cid-1");
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(client.stream(any())).thenReturn(Flux.just(chunk("你好"), chunk("呀")));

        List<SseEvent> events = orchestrator.sendMessage(1L, "conv1", req("你好", "cid-1"))
                .collectList().block(Duration.ofSeconds(10));
        assertNotNull(events);

        // 1. 正常新请求补发 start
        assertTrue(events.stream().anyMatch(e -> "start".equals(e.getType())),
                "正常请求应补发 start 事件");
        // message_delta / done
        assertTrue(events.stream().anyMatch(e -> "message_delta".equals(e.getType())));
        assertTrue(events.stream().anyMatch(e -> "done".equals(e.getType())));

        // Assistant 最终 completed
        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper, atLeastOnce()).updateById(captor.capture());
        AiMessage last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("completed", last.getStatus());
        assertEquals("你好呀", last.getContent());
        assertNotNull(last.getReplyToMessageId());

        // 锁最终释放（Lua 删除）
        verify(redisTemplate).execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), any(Object[].class));
    }

    @Test
    void emptyStreamMarksFailed() {
        stubCommon("cid-2");
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(client.stream(any())).thenReturn(Flux.empty());

        List<SseEvent> events = orchestrator.sendMessage(1L, "conv1", req("你好", "cid-2"))
                .collectList().block(Duration.ofSeconds(10));
        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> "error".equals(e.getType())));

        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper, atLeastOnce()).updateById(captor.capture());
        AiMessage last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("failed", last.getStatus());
    }

    @Test
    void characterUnavailableMarksAssistantFailedAndEmitsError() {
        when(conversationService.getOwned(1L, "conv1")).thenReturn(conversation());
        when(messageMapper.findByClientMessageId(1L, "cid-3")).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        // 角色不存在或未启用
        when(characterMapper.selectById(1L)).thenReturn(null);

        List<SseEvent> events = orchestrator.sendMessage(1L, "conv1", req("你好", "cid-3"))
                .collectList().block(Duration.ofSeconds(10));
        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> "error".equals(e.getType())
                && "AI_CHARACTER_UNAVAILABLE".equals(((Map<?, ?>) e.getData()).get("code"))));

        // Assistant 消息被更新为 failed
        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());

        // 未调用模型
        verify(client, never()).stream(any());
    }

    @Test
    void syncExceptionReleasesLockAndEmitsError() {
        stubCommon("cid-4");
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        // 保存用户消息抛异常 → 模拟同步阶段 DB 异常
        doThrow(new RuntimeException("db down")).when(messageMapper).insert(any(AiMessage.class));

        List<SseEvent> events = orchestrator.sendMessage(1L, "conv1", req("你好", "cid-4"))
                .collectList().block(Duration.ofSeconds(10));

        // 同步异常被收尾为 error 事件，不向调用方崩溃传播
        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> "error".equals(e.getType())));

        // 锁必须被 Lua 删除（同步异常也要释放）
        verify(redisTemplate).execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), any(Object[].class));
    }

    @Test
    void syncExceptionAfterAssistantInsertMarksFailedNotCancelled() {
        // User insert 成功、Assistant insert 成功，但加载上下文抛同步异常
        when(conversationService.getOwned(1L, "conv1")).thenReturn(conversation());
        when(messageMapper.findByClientMessageId(1L, "cid-6")).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(characterMapper.selectById(1L)).thenReturn(character());
        // 加载最近对话时抛异常（此时 assistant 已插入成功）
        doThrow(new RuntimeException("context load failed"))
                .when(conversationContextService).loadRecentRounds(anyString(), anyInt());

        List<SseEvent> events = orchestrator.sendMessage(1L, "conv1", req("你好", "cid-6"))
                .collectList().block(Duration.ofSeconds(10));
        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> "error".equals(e.getType())));

        // Assistant 消息应被更新为 failed，且不得出现 cancelled
        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper, atLeastOnce()).updateById(captor.capture());
        List<String> statuses = captor.getAllValues().stream().map(AiMessage::getStatus).toList();
        assertTrue(statuses.contains("failed"), "Assistant 应为 failed，实际: " + statuses);
        assertFalse(statuses.contains("cancelled"), "同步异常不得被改写为 cancelled: " + statuses);

        // 锁必须释放
        verify(redisTemplate).execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), any(Object[].class));
    }

    @Test
    void lockAcquireFailureThrowsBusinessException() {
        when(conversationService.getOwned(1L, "conv1")).thenReturn(conversation());
        when(messageMapper.findByClientMessageId(1L, "cid-5")).thenReturn(null);
        // 并发锁已被占用
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        assertThrows(BusinessException.class, () -> orchestrator.sendMessage(1L, "conv1", req("你好", "cid-5"))
                .collectList().block(Duration.ofSeconds(10)));
        verify(client, never()).stream(any());
    }

    @Test
    void duplicateClientMessageIdReplaysExactReplyWithoutModelCall() {
        AiMessage userMsg = new AiMessage();
        userMsg.setId("user-1");
        userMsg.setClientMessageId("cid-dup");
        userMsg.setRole("user");
        userMsg.setContent("你好");

        AiMessage reply = new AiMessage();
        reply.setId("assistant-1");
        reply.setRole("assistant");
        reply.setReplyToMessageId("user-1");
        reply.setContent("之前保存的回复");

        when(conversationService.getOwned(1L, "conv1")).thenReturn(conversation());
        when(messageMapper.findByClientMessageId(1L, "cid-dup")).thenReturn(userMsg);
        when(messageMapper.listByReplyTo("conv1", "user-1")).thenReturn(List.of(reply));

        List<SseEvent> events = orchestrator.sendMessage(1L, "conv1", req("你好", "cid-dup"))
                .collectList().block(Duration.ofSeconds(10));
        assertNotNull(events);
        verify(client, never()).stream(any());
        verify(messageMapper, never()).insert(any(AiMessage.class));
        assertTrue(events.stream().anyMatch(e -> "message_delta".equals(e.getType())
                && "之前保存的回复".equals(((Map<?, ?>) e.getData()).get("content"))));
    }

    @Test
    void 多消息事件序列startEnd严格配对且标签不泄漏() {
        stubCommon("cid-multi");
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        // 真实 chunk 轨迹：标签被切分 + 多消息
        when(client.stream(any())).thenReturn(Flux.just(
                chunk("<messag"),
                chunk("e>刚刚在发呆</mess"),
                chunk("age>"),
                chunk("<message>然后就被你抓到了～</message>")));

        List<SseEvent> events = orchestrator.sendMessage(1L, "conv1", req("在干嘛", "cid-multi"))
                .collectList().block(Duration.ofSeconds(10));
        assertNotNull(events);

        // 只关心 message_* 事件序列
        List<SseEvent> msgEvents = events.stream()
                .filter(e -> e.getType().startsWith("message_"))
                .toList();
        long starts = msgEvents.stream().filter(e -> "message_start".equals(e.getType())).count();
        long ends = msgEvents.stream().filter(e -> "message_end".equals(e.getType())).count();
        assertEquals(starts, ends, "start/end 数量必须严格相等");

        // 任意 SSE data 不得包含协议标签
        for (SseEvent e : msgEvents) {
            String data = String.valueOf(e.getData());
            assertFalse(data.contains("<message"), "SSE 泄漏开标签: " + data);
            assertFalse(data.contains("</message"), "SSE 泄漏闭标签: " + data);
        }

        // 拼接 message_delta = 两条消息正文，不重复不丢字
        StringBuilder joined = new StringBuilder();
        for (SseEvent e : msgEvents) {
            if ("message_delta".equals(e.getType())) {
                joined.append(((Map<?, ?>) e.getData()).get("content"));
            }
        }
        assertEquals("刚刚在发呆然后就被你抓到了～", joined.toString());
    }

    @Test
    void 纯文本降级为单条气泡() {
        stubCommon("cid-plain");
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(client.stream(any())).thenReturn(Flux.just(chunk("就"), chunk("一句话")));

        List<SseEvent> events = orchestrator.sendMessage(1L, "conv1", req("在干嘛", "cid-plain"))
                .collectList().block(Duration.ofSeconds(10));
        assertNotNull(events);
        long starts = events.stream().filter(e -> "message_start".equals(e.getType())).count();
        long ends = events.stream().filter(e -> "message_end".equals(e.getType())).count();
        assertEquals(1, starts);
        assertEquals(1, ends);
        // done 携带 messageIds
        assertTrue(events.stream().anyMatch(e -> "done".equals(e.getType())));
    }
}

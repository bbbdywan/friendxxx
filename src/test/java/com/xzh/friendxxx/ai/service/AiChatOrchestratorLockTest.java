package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.client.DeepSeekChatClient;
import com.xzh.friendxxx.ai.client.DeepSeekStreamChunk;
import com.xzh.friendxxx.ai.config.DeepSeekProperties;
import com.xzh.friendxxx.ai.model.ReplyStrategyResult;
import com.xzh.friendxxx.ai.model.SseEvent;
import com.xzh.friendxxx.ai.model.dto.SendMessageRequest;
import com.xzh.friendxxx.mapper.AiCharacterMapper;
import com.xzh.friendxxx.mapper.AiConversationMapper;
import com.xzh.friendxxx.mapper.AiMessageMapper;
import com.xzh.friendxxx.model.entity.AiCharacter;
import com.xzh.friendxxx.model.entity.AiConversation;
import com.xzh.friendxxx.model.entity.AiMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 锁误删保护与后台任务拒绝不影响主回复的测试。
 */
class AiChatOrchestratorLockTest {

    private DeepSeekChatClient client;
    private AiMessageMapper messageMapper;
    private AiConversationMapper conversationMapper;
    private AiConversationService conversationService;
    private AiCharacterMapper characterMapper;
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
        strategyService = mock(ReplyStrategyService.class);
        summaryService = mock(ConversationSummaryService.class);
        memoryService = mock(LongTermMemoryService.class);
        relationshipService = mock(RelationshipStateService.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        AiCharacterVersionService characterVersionService = mock(AiCharacterVersionService.class);

        DeepSeekProperties props = new DeepSeekProperties(
                "https://api.deepseek.com", "test-key",
                "deepseek-v4-flash", "deepseek-v4-flash",
                Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(5));

        orchestrator = new AiChatOrchestrator(
                client, props, conversationService, characterMapper, conversationMapper,
                messageMapper, new PersonaPromptAssembler(),
                mock(ConversationContextService.class), mock(MemoryRetrievalService.class),
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

    private SendMessageRequest req(String cid) {
        SendMessageRequest r = new SendMessageRequest();
        r.setContent("你好");
        r.setClientMessageId(cid);
        return r;
    }

    private void stubCommon(String cid) {
        when(conversationService.getOwned(1L, "conv1")).thenReturn(conversation());
        when(messageMapper.findByClientMessageId(1L, cid)).thenReturn(null);
        when(characterMapper.selectById(1L)).thenReturn(character());
        ReplyStrategyResult s = new ReplyStrategyResult();
        s.setEmotion("neutral");
        s.setIntensity(0.3);
        s.setIntent("small_talk");
        s.setStrategy("LISTEN");
        s.setShouldGiveAdvice(false);
        s.setShouldRecallMemory(false);
        s.setThinkingRequired(false);
        s.setRiskLevel("none");
        when(strategyService.recognize(anyString(), anyList(), anyString())).thenReturn(s);
    }

    private DeepSeekStreamChunk chunk(String content) {
        DeepSeekStreamChunk c = new DeepSeekStreamChunk();
        DeepSeekStreamChunk.Delta delta = new DeepSeekStreamChunk.Delta();
        delta.setContent(content);
        DeepSeekStreamChunk.Choice choice = new DeepSeekStreamChunk.Choice();
        choice.setDelta(delta);
        c.setChoices(List.of(choice));
        return c;
    }

    @Test
    void lockReleasedViaLuaCompareAndDeleteWithToken() {
        stubCommon("cid-lock");
        when(client.stream(any())).thenReturn(Flux.just(chunk("回复")));

        orchestrator.sendMessage(1L, "conv1", req("cid-lock"))
                .collectList().block(Duration.ofSeconds(10));

        // 释放走 Lua compare-and-delete：execute(script, [key], [token])，而非无条件 delete
        verify(redisTemplate, times(1))
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void backgroundTaskRejectionDoesNotAffectMainReply() {
        stubCommon("cid-rej");
        when(client.stream(any())).thenReturn(Flux.just(chunk("回复内容")));

        // 三个 @Async 后台任务提交全部抛 RejectedExecutionException
        doThrow(new RejectedExecutionException("queue full"))
                .when(summaryService).maybeSummarize(anyString());
        doThrow(new RejectedExecutionException("queue full"))
                .when(memoryService).extractAndSave(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString());
        doThrow(new RejectedExecutionException("queue full"))
                .when(relationshipService).updateAfterTurn(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString());

        List<SseEvent> events = orchestrator.sendMessage(1L, "conv1", req("cid-rej"))
                .collectList().block(Duration.ofSeconds(10));
        assertNotNull(events);

        // 主回复不受影响：completed 状态 + done 事件
        assertTrue(events.stream().anyMatch(e -> "done".equals(e.getType())));
        assertTrue(events.stream().noneMatch(e -> "error".equals(e.getType())));

        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper, atLeastOnce()).updateById(captor.capture());
        assertEquals("completed", captor.getAllValues().get(captor.getAllValues().size() - 1).getStatus());
    }

    @Test
    void realCancellationWithPartialContentMarksPartial() {
        stubCommon("cid-cancel-partial");
        when(client.stream(any())).thenReturn(
                Flux.concat(Flux.just(chunk("部分内容")), Flux.never()));

        StepVerifier.create(orchestrator.sendMessage(1L, "conv1", req("cid-cancel-partial")))
                .expectNextMatches(e -> "start".equals(e.getType()))
                .expectNextMatches(e -> "message_start".equals(e.getType()))
                .expectNextMatches(e -> "message_delta".equals(e.getType()))
                .thenCancel()
                .verify();

        // 客户端取消后，已输出部分内容 → partial（取消传播异步，用 timeout 等待）
        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper, timeout(3000)).updateById(captor.capture());
        assertEquals("partial", captor.getValue().getStatus());
    }

    @Test
    void realCancellationWithoutContentMarksCancelled() {
        stubCommon("cid-cancel-none");
        when(client.stream(any())).thenReturn(Flux.never());

        StepVerifier.create(orchestrator.sendMessage(1L, "conv1", req("cid-cancel-none")))
                .expectNextMatches(e -> "start".equals(e.getType()))
                .thenCancel()
                .verify();

        // 无内容取消 → cancelled（不会残留 generating）；取消传播为异步，用 timeout 等待
        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper, timeout(3000)).updateById(captor.capture());
        assertEquals("cancelled", captor.getValue().getStatus());
    }

    @Test
    void backgroundTasksSubmittedBeforeDoneEvent() {
        stubCommon("cid-bg");
        when(client.stream(any())).thenReturn(Flux.just(chunk("回复内容")));

        // 上游流完成后（到达 done）客户端立即取消，后台任务仍应已提交
        StepVerifier.create(orchestrator.sendMessage(1L, "conv1", req("cid-bg")))
                .expectNextMatches(e -> "start".equals(e.getType()))
                .expectNextMatches(e -> "message_start".equals(e.getType()))
                .expectNextMatches(e -> "message_delta".equals(e.getType()))
                .expectNextMatches(e -> "message_end".equals(e.getType()))
                .expectNextMatches(e -> "usage".equals(e.getType()))
                .expectNextMatches(e -> "done".equals(e.getType()))
                .thenCancel()
                .verify();

        // done 之前已同步提交后台任务（即使客户端随后取消也不丢失）
        verify(summaryService, atLeastOnce()).maybeSummarize(anyString());
        verify(memoryService, atLeastOnce()).extractAndSave(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString());
        verify(relationshipService, atLeastOnce()).updateAfterTurn(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }
}

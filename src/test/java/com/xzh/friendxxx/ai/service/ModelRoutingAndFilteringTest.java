package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.client.DeepSeekStreamChunk;
import com.xzh.friendxxx.ai.model.ReplyStrategyResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * reasoning_content 过滤与模型路由测试。
 */
class ModelRoutingAndFilteringTest {

    @Test
    void contentDeltaExcludesReasoning() {
        DeepSeekStreamChunk chunk = new DeepSeekStreamChunk();
        DeepSeekStreamChunk.Delta delta = new DeepSeekStreamChunk.Delta();
        delta.setContent("回复内容");
        delta.setReasoningContent("这是内部推理");
        DeepSeekStreamChunk.Choice choice = new DeepSeekStreamChunk.Choice();
        choice.setDelta(delta);
        chunk.setChoices(List.of(choice));

        assertEquals("回复内容", chunk.contentDelta());
        // reasoning_content 位于 delta 中，但 contentDelta() 只返回 content
        assertNotEquals("这是内部推理", chunk.contentDelta());
    }

    @Test
    void contentDeltaNullWhenNoChoices() {
        DeepSeekStreamChunk chunk = new DeepSeekStreamChunk();
        chunk.setChoices(List.of());
        assertNull(chunk.contentDelta());
    }

    @Test
    void thinkingDisabledByDefaultForCasualChat() {
        ModelRoutingService service = new ModelRoutingService();
        ReplyStrategyResult strategy = strategy(false);
        assertFalse(service.requireThinking(strategy, "今天天气不错呀", 10));
    }

    @Test
    void thinkingEnabledWhenUserAsksForAnalysis() {
        ModelRoutingService service = new ModelRoutingService();
        ReplyStrategyResult strategy = strategy(false);
        assertTrue(service.requireThinking(strategy,
                "请你帮我仔细分析一下我和女朋友之间的感情问题到底出在哪里，我们最近总是吵架，我觉得可能是沟通的问题但也可能是我太敏感了", 10));
    }

    @Test
    void thinkingEnabledWhenStrategyRequires() {
        ModelRoutingService service = new ModelRoutingService();
        ReplyStrategyResult strategy = strategy(true);
        assertTrue(service.requireThinking(strategy, "你好", 2));
    }

    private ReplyStrategyResult strategy(boolean thinking) {
        ReplyStrategyResult r = new ReplyStrategyResult();
        r.setEmotion("neutral");
        r.setIntensity(0.3);
        r.setIntent("small_talk");
        r.setStrategy("LISTEN");
        r.setShouldGiveAdvice(false);
        r.setShouldRecallMemory(false);
        r.setThinkingRequired(thinking);
        r.setRiskLevel("none");
        return r;
    }
}

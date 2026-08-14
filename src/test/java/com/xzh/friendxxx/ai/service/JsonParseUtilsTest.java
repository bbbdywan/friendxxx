package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.model.MemoryExtractionResult;
import com.xzh.friendxxx.ai.model.ReplyStrategyResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 辅助输出 JSON 解析与降级测试。
 */
class JsonParseUtilsTest {

    @Test
    void parsesStrictJson() {
        String raw = "{\"emotion\":\"happy\",\"intensity\":0.8,\"intent\":\"sharing_good_news\",\"strategy\":\"CELEBRATE\",\"shouldGiveAdvice\":false,\"shouldRecallMemory\":true,\"thinkingRequired\":false,\"riskLevel\":\"none\"}";
        ReplyStrategyResult r = JsonParseUtils.parse(raw, ReplyStrategyResult.class);
        assertNotNull(r);
        assertEquals("happy", r.getEmotion());
        assertEquals("CELEBRATE", r.getStrategy());
    }

    @Test
    void parsesContentWrappedInCodeFence() {
        String raw = "```json\n{\"emotion\":\"sad\",\"intensity\":0.6,\"intent\":\"venting\",\"strategy\":\"LISTEN\",\"shouldGiveAdvice\":false,\"shouldRecallMemory\":false,\"thinkingRequired\":false,\"riskLevel\":\"none\"}\n```";
        ReplyStrategyResult r = JsonParseUtils.parse(raw, ReplyStrategyResult.class);
        assertNotNull(r);
        assertEquals("LISTEN", r.getStrategy());
    }

    @Test
    void parsesWithLeadingText() {
        String raw = "好的，分析如下：\n{\"emotion\":\"sad\",\"intensity\":0.5,\"intent\":\"seeking_emotional_support\",\"strategy\":\"VALIDATE_THEN_GENTLY_ASK\",\"shouldGiveAdvice\":false,\"shouldRecallMemory\":false,\"thinkingRequired\":false,\"riskLevel\":\"none\"}";
        ReplyStrategyResult r = JsonParseUtils.parse(raw, ReplyStrategyResult.class);
        assertNotNull(r);
        assertEquals("VALIDATE_THEN_GENTLY_ASK", r.getStrategy());
    }

    @Test
    void returnsNullOnInvalidJson() {
        assertNull(JsonParseUtils.parse("这不是JSON", ReplyStrategyResult.class));
        assertNull(JsonParseUtils.parse(null, ReplyStrategyResult.class));
        assertNull(JsonParseUtils.parse("", ReplyStrategyResult.class));
    }

    @Test
    void memoryExtractionParses() {
        String raw = "{\"memories\":[{\"type\":\"EVENT\",\"key\":\"job_interview\",\"content\":\"用户下周面试\",\"importance\":0.8,\"confidence\":0.9}]}";
        MemoryExtractionResult r = JsonParseUtils.parse(raw, MemoryExtractionResult.class);
        assertNotNull(r);
        assertEquals(1, r.getMemories().size());
        assertEquals("EVENT", r.getMemories().get(0).getType());
    }
}

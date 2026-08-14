package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.mapper.AiMemoryMapper;
import com.xzh.friendxxx.model.entity.AiMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 长期记忆过滤与排序测试。
 */
class MemoryRetrievalServiceTest {

    private AiMemoryMapper mapper;
    private MemoryRetrievalService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AiMemoryMapper.class);
        service = new MemoryRetrievalService(mapper);
    }

    private AiMemory memory(String key, String content, String type,
                            BigDecimal importance, BigDecimal confidence,
                            BigDecimal emotional, Date updateTime) {
        AiMemory m = new AiMemory();
        m.setMemoryKey(key);
        m.setContent(content);
        m.setMemoryType(type);
        m.setImportance(importance);
        m.setConfidence(confidence);
        m.setEmotionalWeight(emotional);
        m.setUpdateTime(updateTime);
        m.setAccessCount(0);
        return m;
    }

    @Test
    void onlyReturnsActiveOwnedMemories() {
        List<AiMemory> active = List.of(
                memory("job", "用户下周参加后端开发面试", "EVENT", new BigDecimal("0.9"), new BigDecimal("0.98"),
                        new BigDecimal("0.6"), new Date()));
        when(mapper.listActive(1L, 1L)).thenReturn(active);

        List<AiMemory> result = service.retrieve(1L, 1L, "面试");
        assertEquals(1, result.size());
        assertEquals("job", result.get(0).getMemoryKey());
    }

    @Test
    void expiredMemoriesNotReturned() {
        // listActive 只返回未过期，模拟空结果
        when(mapper.listActive(1L, 1L)).thenReturn(List.of());
        List<AiMemory> result = service.retrieve(1L, 1L, "面试");
        assertTrue(result.isEmpty());
    }

    @Test
    void noResultWhenMapperEmpty() {
        when(mapper.listActive(2L, 9L)).thenReturn(List.of());
        assertTrue(service.retrieve(2L, 9L, "anything").isEmpty());
    }

    @Test
    void maxEightResultsAndDedupByKey() {
        List<AiMemory> many = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            AiMemory m = memory("key" + (i % 5), "内容包含共同关键词面试 " + i, "EVENT",
                    new BigDecimal("0.5"), new BigDecimal("0.9"), BigDecimal.ZERO, new Date());
            m.setMemoryKey("dup" + (i % 5));
            many.add(m);
        }
        when(mapper.listActive(1L, 1L)).thenReturn(many);
        List<AiMemory> result = service.retrieve(1L, 1L, "面试");
        assertTrue(result.size() <= 8);
    }

    @Test
    void relevantMemoryRanksHigherThanUnrelated() {
        AiMemory relevant = memory("a", "用户喜欢吃火锅", "PREFERENCE", new BigDecimal("0.5"),
                new BigDecimal("0.9"), BigDecimal.ZERO, new Date());
        AiMemory unrelated = memory("b", "用户养了一只猫叫咪咪", "EVENT", new BigDecimal("0.95"),
                new BigDecimal("0.95"), BigDecimal.ZERO, new Date());
        when(mapper.listActive(1L, 1L)).thenReturn(List.of(unrelated, relevant));

        List<AiMemory> result = service.retrieve(1L, 1L, "火锅");
        assertEquals("a", result.get(0).getMemoryKey());
    }
}

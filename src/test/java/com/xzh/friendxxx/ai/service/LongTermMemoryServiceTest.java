package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.model.MemoryExtractionResult;
import com.xzh.friendxxx.mapper.AiMemoryMapper;
import com.xzh.friendxxx.model.entity.AiMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 长期记忆提取与冲突更新测试。
 */
class LongTermMemoryServiceTest {

    private AiUtilityService utility;
    private AiMemoryMapper mapper;
    private LongTermMemoryService service;

    @BeforeEach
    void setUp() {
        utility = mock(AiUtilityService.class);
        mapper = mock(AiMemoryMapper.class);
        service = new LongTermMemoryService(utility, mapper);
    }

    @Test
    void sameKeySameContentRaisesConfidence() {
        MemoryExtractionResult.MemoryItem item = new MemoryExtractionResult.MemoryItem();
        item.setType("EVENT");
        item.setKey("job_interview");
        item.setContent("用户下周参加后端开发面试");
        item.setImportance(new BigDecimal("0.8"));
        item.setConfidence(new BigDecimal("0.9"));
        item.setEmotionalWeight(new BigDecimal("0.5"));
        MemoryExtractionResult result = new MemoryExtractionResult();
        result.setMemories(List.of(item));

        AiMemory existing = new AiMemory();
        existing.setId("m1");
        existing.setStatus("active");
        existing.setMemoryKey("job_interview");
        existing.setContent("用户下周参加后端开发面试");
        existing.setConfidence(new BigDecimal("0.9"));

        when(utility.callUtilityStrict(anyString(), anyString(), eq(MemoryExtractionResult.class))).thenReturn(result);
        when(mapper.findByKey(1L, 1L, "job_interview")).thenReturn(List.of(existing));

        service.extractAndSave(1L, 1L, "c1", "我下周面试", "祝你好运！", "msg1");

        verify(mapper, never()).insert(any(AiMemory.class));
        // 内容一致时只更新置信度（0.9 + 0.05 = 0.95），不新建
        verify(mapper, times(1)).updateById(argThat((AiMemory m) ->
                m.getConfidence().compareTo(new BigDecimal("0.95")) == 0));    }

    @Test
    void conflictingNewInfoSupersedesOld() {
        MemoryExtractionResult.MemoryItem item = new MemoryExtractionResult.MemoryItem();
        item.setType("PREFERENCE");
        item.setKey("food_preference");
        item.setContent("用户现在不吃辣了");
        item.setImportance(new BigDecimal("0.7"));
        item.setConfidence(new BigDecimal("0.95"));
        MemoryExtractionResult result = new MemoryExtractionResult();
        result.setMemories(List.of(item));

        AiMemory existing = new AiMemory();
        existing.setId("m1");
        existing.setStatus("active");
        existing.setMemoryKey("food_preference");
        existing.setContent("用户喜欢吃辣");
        existing.setConfidence(new BigDecimal("0.6"));

        when(utility.callUtilityStrict(anyString(), anyString(), eq(MemoryExtractionResult.class))).thenReturn(result);
        when(mapper.findByKey(1L, 1L, "food_preference")).thenReturn(List.of(existing));

        service.extractAndSave(1L, 1L, "c1", "我现在不吃辣了", "好的，记住了", "msg2");

        // 旧记录标记 superseded
        verify(mapper, times(1)).supersedeByKey(eq(1L), eq(1L), eq("food_preference"));
        // 新记录插入
        verify(mapper, times(1)).insert(argThat((AiMemory m) ->
                "用户现在不吃辣了".equals(m.getContent())
                        && "active".equals(m.getStatus())
                        && "msg2".equals(m.getSourceMessageId())));
    }

    @Test
    void uncertainNewInfoDoesNotOverrideHighConfidenceOld() {
        MemoryExtractionResult.MemoryItem item = new MemoryExtractionResult.MemoryItem();
        item.setType("PROFILE");
        item.setKey("nickname");
        item.setContent("用户叫阿豪");
        item.setImportance(new BigDecimal("0.6"));
        item.setConfidence(new BigDecimal("0.4")); // 低置信度新信息
        MemoryExtractionResult result = new MemoryExtractionResult();
        result.setMemories(List.of(item));

        AiMemory existing = new AiMemory();
        existing.setId("m1");
        existing.setStatus("active");
        existing.setMemoryKey("nickname");
        existing.setContent("用户叫小明");
        existing.setConfidence(new BigDecimal("0.98"));

        when(utility.callUtilityStrict(anyString(), anyString(), eq(MemoryExtractionResult.class))).thenReturn(result);
        when(mapper.findByKey(1L, 1L, "nickname")).thenReturn(List.of(existing));

        service.extractAndSave(1L, 1L, "c1", "其实我叫阿豪", "好的阿豪", "msg3");

        // 不确定新信息不能覆盖高置信度旧信息：不 supersede、不 insert
        verify(mapper, never()).supersedeByKey(anyLong(), anyLong(), anyString());
        verify(mapper, never()).insert(any(AiMemory.class));
    }

    @Test
    void failedExtractionDoesNotThrow() {
        when(utility.callUtilityStrict(anyString(), anyString(), eq(MemoryExtractionResult.class)))
                .thenThrow(new RuntimeException("upstream down"));
        // 不抛异常
        service.extractAndSave(1L, 1L, "c1", "hello", "hi", "msg4");
        verify(mapper, never()).insert(any(AiMemory.class));
    }
}

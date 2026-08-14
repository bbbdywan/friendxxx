package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.model.MemoryExtractionResult;
import com.xzh.friendxxx.ai.model.MemoryType;
import com.xzh.friendxxx.mapper.AiMemoryMapper;
import com.xzh.friendxxx.model.entity.AiMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 长期记忆提取与冲突处理（V4 Flash，异步执行，失败不影响主回复）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final AiUtilityService aiUtilityService;
    private final AiMemoryMapper aiMemoryMapper;

    private static final String SYSTEM_PROMPT = """
            你是记忆提取助手。从对话中提取值得长期记住的用户信息。
            只提取：用户明确说过的事实、未来可能被再次提及的事件、稳定偏好、长期目标、用户指定的称呼、明确表达的沟通边界、对后续聊天有价值的共同经历。
            禁止提取：模型推测或脑补的事实、临时闲聊、敏感凭据（密码/Token/证件号/银行卡）、未经确认的疾病诊断、编造的事件。
            必须只输出 JSON，结构：
            {
              "memories": [
                {
                  "type": "PROFILE|PREFERENCE|RELATIONSHIP|EVENT|GOAL|SHARED|BOUNDARY",
                  "key": "简短英文或拼音键名，如 job_interview_2026_08",
                  "content": "记忆内容",
                  "normalizedValue": "规范化值",
                  "importance": 0到1之间的数字,
                  "confidence": 0到1之间的数字,
                  "emotionalWeight": 0到1之间的数字,
                  "expiresAt": "ISO8601时间或null"
                }
              ]
            }
            如果本轮没有值得记住的信息，输出 {"memories":[]}。
            """;

    /**
     * 异步提取并保存记忆。失败仅记录日志。
     */
    @Async("aiTaskExecutor")
    public void extractAndSave(Long userId, Long characterId, String conversationId,
                               String userMessage, String assistantMessage, String sourceMessageId) {
        try {
            String userPrompt = "用户说：" + userMessage + "\n角色回复：" + assistantMessage;
            MemoryExtractionResult result = aiUtilityService.callUtilityStrict(SYSTEM_PROMPT, userPrompt, MemoryExtractionResult.class);
            if (result == null || result.getMemories() == null || result.getMemories().isEmpty()) {
                return;
            }
            for (MemoryExtractionResult.MemoryItem item : result.getMemories()) {
                if (!MemoryType.isAllowed(item.getType()) || item.getContent() == null || item.getContent().isBlank()) {
                    continue;
                }
                String key = item.getKey() == null ? uuid() : item.getKey();
                BigDecimal importance = clamp(item.getImportance());
                BigDecimal confidence = clamp(item.getConfidence());
                BigDecimal emotional = clamp(item.getEmotionalWeight());

                List<AiMemory> existing = aiMemoryMapper.findByKey(userId, characterId, key);
                if (!existing.isEmpty()) {
                    AiMemory latest = existing.get(existing.size() - 1);
                    if ("active".equals(latest.getStatus()) && latest.getContent() != null
                            && latest.getContent().equals(item.getContent())) {
                        // 内容一致：提升置信度并刷新时间
                        BigDecimal mergedConfidence = min(BigDecimal.ONE,
                                latest.getConfidence() == null ? confidence : latest.getConfidence().add(new BigDecimal("0.05")));
                        latest.setConfidence(mergedConfidence);
                        latest.setContent(item.getContent());
                        latest.setSourceMessageId(sourceMessageId);
                        latest.setUpdateTime(new Date());
                        aiMemoryMapper.updateById(latest);
                        continue;
                    }
                    if (confidence.compareTo(latest.getConfidence() == null ? BigDecimal.ZERO : latest.getConfidence()) >= 0) {
                        // 新信息置信度足够：旧记录标记 superseded
                        aiMemoryMapper.supersedeByKey(userId, characterId, key);
                    } else {
                        // 不确定的新信息不能覆盖高置信度旧信息
                        continue;
                    }
                }

                AiMemory memory = new AiMemory();
                memory.setId(uuid());
                memory.setUserId(userId);
                memory.setCharacterId(characterId);
                memory.setConversationId(conversationId);
                memory.setMemoryType(item.getType());
                memory.setMemoryKey(key);
                memory.setContent(item.getContent());
                memory.setNormalizedValue(item.getNormalizedValue());
                memory.setImportance(importance);
                memory.setConfidence(confidence);
                memory.setEmotionalWeight(emotional);
                memory.setSourceMessageId(sourceMessageId);
                memory.setStatus("active");
                memory.setAccessCount(0);
                memory.setExpiresAt(parseExpiry(item.getExpiresAt()));
                memory.setCreateTime(new Date());
                memory.setUpdateTime(new Date());
                aiMemoryMapper.insert(memory);
            }
            log.debug("记忆提取完成: userId={}, characterId={}, count={}", userId, characterId, result.getMemories().size());
        } catch (Exception e) {
            log.warn("长期记忆提取失败，不影响主回复: {}", e.getMessage());
        }
    }

    private Date parseExpiry(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return null;
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(expiresAt, FMT);
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            return null;
        }
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static BigDecimal clamp(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (v.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return v;
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}

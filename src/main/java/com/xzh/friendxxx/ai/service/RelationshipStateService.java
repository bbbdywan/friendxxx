package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.model.RelationshipUpdateResult;
import com.xzh.friendxxx.mapper.AiRelationshipStateMapper;
import com.xzh.friendxxx.model.entity.AiRelationshipState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 关系状态服务。关系只能缓慢变化，不能因单轮对话突然跳跃。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipStateService {

    private static final List<String> STAGES = Arrays.asList("NEW", "ACQUAINTED", "FAMILIAR", "CLOSE");
    private static final BigDecimal MAX_FAMILIARITY = new BigDecimal("100");

    private final AiUtilityService aiUtilityService;
    private final AiRelationshipStateMapper relationshipStateMapper;

    private static final String SYSTEM_PROMPT = """
            你是关系状态分析助手。根据本轮对话，给出关系数值的微小变化建议。
            关系只能缓慢变化，familiarityDelta 和 trustDelta 的绝对值不应超过 1。
            必须只输出 JSON：{
              "familiarityDelta": 数字,
              "trustDelta": 数字,
              "preferredAddress": "用户偏好的称呼，没有则为null",
              "recentMood": "用户最近情绪，如happy/sad，没有则为null",
              "summary": "一句话关系小结"
            }
            """;

    /**
     * 读取关系状态，不存在则返回 NEW 阶段默认值。
     */
    public AiRelationshipState getOrCreate(Long userId, Long characterId) {
        AiRelationshipState state = relationshipStateMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiRelationshipState>()
                .eq("user_id", userId)
                .eq("character_id", characterId));
        if (state == null) {
            state = new AiRelationshipState();
            state.setUserId(userId);
            state.setCharacterId(characterId);
            state.setFamiliarity(BigDecimal.ZERO);
            state.setTrustLevel(BigDecimal.ZERO);
            state.setInteractionCount(0);
            state.setCurrentStage("NEW");
            state.setVersion(0);
        }
        return state;
    }

    /**
     * 异步更新关系状态。失败仅记录日志。
     */
    @Async("aiTaskExecutor")
    public void updateAfterTurn(Long userId, Long characterId, String userMessage,
                                String assistantMessage, String emotion, String intent) {
        try {
            AiRelationshipState state = getOrCreate(userId, characterId);
            int interactions = state.getInteractionCount() == null ? 0 : state.getInteractionCount();
            String prompt = "用户：" + userMessage + "\n角色：" + assistantMessage
                    + "\n已识别情绪：" + emotion + "，意图：" + intent;
            RelationshipUpdateResult result = aiUtilityService.callUtilityStrict(SYSTEM_PROMPT, prompt, RelationshipUpdateResult.class);
            if (result == null || !result.valid()) {
                // 降级：直接递增互动次数
                incrementInteractions(userId, characterId, interactions);
                return;
            }
            BigDecimal familiarity = state.getFamiliarity() == null ? BigDecimal.ZERO : state.getFamiliarity();
            BigDecimal trust = state.getTrustLevel() == null ? BigDecimal.ZERO : state.getTrustLevel();
            BigDecimal newFamiliarity = clamp(familiarity.add(result.getFamiliarityDelta()));
            BigDecimal newTrust = clamp(trust.add(result.getTrustDelta()));

            String stage = computeStage(newFamiliarity);
            String preferredAddress = result.getPreferredAddress() == null || result.getPreferredAddress().isBlank()
                    ? state.getPreferredAddress() : result.getPreferredAddress();
            String recentMood = result.getRecentMood() == null || result.getRecentMood().isBlank()
                    ? state.getRecentMood() : result.getRecentMood();
            String summary = result.getSummary() == null ? state.getRelationshipSummary() : result.getSummary();

            relationshipStateMapper.upsert(userId, characterId, newFamiliarity, newTrust,
                    interactions + 1, stage, preferredAddress, recentMood, null, summary,
                    state.getVersion() == null ? 0 : state.getVersion());
        } catch (Exception e) {
            log.warn("关系状态更新失败，不影响主回复: {}", e.getMessage());
        }
    }

    private void incrementInteractions(Long userId, Long characterId, int interactions) {
        AiRelationshipState state = getOrCreate(userId, characterId);
        relationshipStateMapper.upsert(userId, characterId,
                state.getFamiliarity() == null ? BigDecimal.ZERO : state.getFamiliarity(),
                state.getTrustLevel() == null ? BigDecimal.ZERO : state.getTrustLevel(),
                interactions + 1,
                state.getCurrentStage() == null ? "NEW" : state.getCurrentStage(),
                state.getPreferredAddress(), state.getRecentMood(), null, state.getRelationshipSummary(),
                state.getVersion() == null ? 0 : state.getVersion());
    }

    private static String computeStage(BigDecimal familiarity) {
        double f = familiarity.doubleValue();
        if (f >= 60) {
            return "CLOSE";
        } else if (f >= 35) {
            return "FAMILIAR";
        } else if (f >= 10) {
            return "ACQUAINTED";
        }
        return "NEW";
    }

    private static BigDecimal clamp(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (v.compareTo(MAX_FAMILIARITY) > 0) {
            return MAX_FAMILIARITY;
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}

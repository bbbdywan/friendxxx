package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.model.ReplyStrategyResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 情绪、意图与回复策略识别（V4 Flash，严格 JSON）。
 * 失败时降级为默认策略，绝不影响主回复。
 */
@Service
@RequiredArgsConstructor
public class ReplyStrategyService {

    private final AiUtilityService aiUtilityService;

    private static final String SYSTEM_PROMPT = """
            你是一个对话分析助手。阅读用户最近的消息，判断用户当前的情绪、意图和适合的回复策略。
            必须只输出 JSON，不要包含任何其它文字或解释。
            JSON 结构如下：
            {
              "emotion": "字符串，如 happy/sad/angry/disappointed/neutral/anxious/lonely/celebrating",
              "intensity": 0到1之间的数字，
              "intent": "字符串，如 seeking_emotional_support/seeking_advice/sharing_good_news/small_talk/venting/clarify/conflict",
              "strategy": "必须是以下之一: LISTEN, VALIDATE, ASK, PLAYFUL, CELEBRATE, GENTLE_ADVICE, DIRECT_ADVICE, CLARIFY, DEESCALATE, VALIDATE_THEN_GENTLY_ASK",
              "shouldGiveAdvice": true或false,
              "shouldRecallMemory": true或false,
              "thinkingRequired": true或false,
              "riskLevel": "none 或 high_risk(自伤/自杀等信号)"
            }
            注意：用户只在明确求助时才应该给建议；普通倾诉优先 LISTEN/VALIDATE。
            """;

    public ReplyStrategyResult recognize(String userMessage, List<String> recentMessages, String characterName) {
        StringBuilder sb = new StringBuilder("角色：").append(characterName).append("\n");
        sb.append("用户最新消息：").append(userMessage).append("\n");
        if (recentMessages != null && !recentMessages.isEmpty()) {
            sb.append("之前几条消息：\n");
            int start = Math.max(0, recentMessages.size() - 6);
            for (int i = start; i < recentMessages.size(); i++) {
                sb.append("- ").append(recentMessages.get(i)).append("\n");
            }
        }
        ReplyStrategyResult result = aiUtilityService.callUtilityStrict(SYSTEM_PROMPT, sb.toString(), ReplyStrategyResult.class);
        if (result == null || !result.valid() || !com.xzh.friendxxx.ai.model.ReplyStrategy.isAllowed(result.getStrategy())) {
            return defaultStrategy();
        }
        return result;
    }

    private ReplyStrategyResult defaultStrategy() {
        ReplyStrategyResult r = new ReplyStrategyResult();
        r.setEmotion("neutral");
        r.setIntensity(0.3);
        r.setIntent("small_talk");
        r.setStrategy(com.xzh.friendxxx.ai.model.ReplyStrategy.LISTEN);
        r.setShouldGiveAdvice(false);
        r.setShouldRecallMemory(false);
        r.setThinkingRequired(false);
        r.setRiskLevel("none");
        return r;
    }
}

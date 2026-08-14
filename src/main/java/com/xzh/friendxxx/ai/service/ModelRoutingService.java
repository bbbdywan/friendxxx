package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.model.ReplyStrategyResult;
import org.springframework.stereotype.Service;

/**
 * 模型路由：判断当前轮次使用普通模式还是思考模式。
 */
@Service
public class ModelRoutingService {

    /**
     * 判断是否需要思考模式。
     *
     * @param strategy 辅助模型识别结果（可能为 null，此时降级为普通模式）
     * @param userMessage 用户消息
     * @param recentMessagesCount 最近消息条数
     */
    public boolean requireThinking(ReplyStrategyResult strategy, String userMessage, int recentMessagesCount) {
        if (strategy != null && Boolean.TRUE.equals(strategy.getThinkingRequired())) {
            return true;
        }
        if (userMessage != null) {
            String msg = userMessage.trim();
            // 用户明确要求认真分析 / 梳理关系
            if (msg.length() >= 40 && (msg.contains("分析") || msg.contains("梳理")
                    || msg.contains("帮我理理") || msg.contains("建议我"))) {
                return true;
            }
            // 长篇倾诉且包含多个事件/人物，需要梳理因果关系
            if (msg.length() >= 150) {
                return true;
            }
        }
        return false;
    }

    /**
     * 复杂情感冲突场景。
     */
    public boolean isHighConflict(String intent) {
        return "emotional_conflict".equalsIgnoreCase(intent)
                || "relationship_conflict".equalsIgnoreCase(intent);
    }
}

package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.mapper.AiConversationMapper;
import com.xzh.friendxxx.mapper.AiMessageMapper;
import com.xzh.friendxxx.model.entity.AiConversation;
import com.xzh.friendxxx.model.entity.AiMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话上下文加载：最近 12～20 个完整对话轮次 + 会话摘要。
 * 完整轮次 = UserMessage + AssistantMessage，禁止从半个轮次截断。
 */
@Service
@RequiredArgsConstructor
public class ConversationContextService {

    private final AiMessageMapper aiMessageMapper;
    private final AiConversationMapper aiConversationMapper;

    /**
     * 加载最近指定数量的完整轮次（默认 15 轮 = 30 条消息，向上取整对齐轮次）。
     */
    public List<AiMessage> loadRecentRounds(String conversationId, int rounds) {
        int targetMessages = rounds * 2;
        List<AiMessage> messages = aiMessageMapper.recentMessages(conversationId, targetMessages);
        if (messages.isEmpty()) {
            return messages;
        }
        // 对齐完整轮次：如果最后一条是 user（没有对应 assistant），去掉该 user 消息
        List<AiMessage> result = new ArrayList<>(messages);
        if (!result.isEmpty() && "user".equals(result.get(result.size() - 1).getRole())) {
            result.remove(result.size() - 1);
        }
        // 同时保证开头是 user 或 assistant 对齐：若以 assistant 开头，去掉它（避免半个轮次开头）
        if (!result.isEmpty() && "assistant".equals(result.get(0).getRole())) {
            result.remove(0);
        }
        return result;
    }

    public String loadConversationSummary(String conversationId) {
        AiConversation conversation = aiConversationMapper.selectById(conversationId);
        return conversation == null ? null : conversation.getConversationSummary();
    }
}

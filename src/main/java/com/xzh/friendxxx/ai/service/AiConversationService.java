package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.model.dto.CreateConversationRequest;
import com.xzh.friendxxx.ai.model.vo.AiMessageVO;
import com.xzh.friendxxx.ai.model.vo.ConversationVO;
import com.xzh.friendxxx.ai.model.vo.MessagePageVO;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.mapper.AiCharacterMapper;
import com.xzh.friendxxx.mapper.AiConversationMapper;
import com.xzh.friendxxx.mapper.AiMessageMapper;
import com.xzh.friendxxx.model.entity.AiCharacter;
import com.xzh.friendxxx.model.entity.AiConversation;
import com.xzh.friendxxx.model.entity.AiMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 会话与消息查询服务。所有读取都强制校验会话归属。
 */
@Service
@RequiredArgsConstructor
public class AiConversationService {

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final AiCharacterMapper aiCharacterMapper;

    public ConversationVO createConversation(Long userId, CreateConversationRequest request) {
        AiCharacter character = aiCharacterMapper.selectById(request.getCharacterId());
        if (character == null || character.getEnabled() == null || character.getEnabled() != 1) {
            throw new BusinessException(400, "角色不存在或未启用");
        }
        AiConversation conversation = new AiConversation();
        conversation.setId(UUID.randomUUID().toString());
        conversation.setUserId(userId);
        conversation.setCharacterId(request.getCharacterId());
        conversation.setSummaryVersion(0);
        conversation.setIsDeleted(0);
        conversation.setCreateTime(new Date());
        conversation.setUpdateTime(new Date());
        aiConversationMapper.insert(conversation);
        return toVO(conversation);
    }

    public List<ConversationVO> listConversations(Long userId, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        List<AiConversation> list = aiConversationMapper.listByUser(userId, offset, Math.min(size, 50));
        List<ConversationVO> result = new ArrayList<>();
        for (AiConversation c : list) {
            result.add(toVO(c));
        }
        return result;
    }

    /**
     * 会话详情：返回会话及角色资料（名称/头像），用于会话恢复。
     */
    public ConversationVO getConversation(Long userId, String conversationId) {
        AiConversation conversation = getOwned(userId, conversationId);
        return toVO(conversation);
    }

    public AiConversation getOwned(Long userId, String conversationId) {
        AiConversation conversation = aiConversationMapper.getOwned(conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在或无权访问");
        }
        return conversation;
    }

    public MessagePageVO listMessages(Long userId, String conversationId,
                                      String cursorTime, String cursorId, int limit) {
        getOwned(userId, conversationId);
        int safeLimit = Math.min(limit, 50);
        Date time = cursorTime == null ? null : java.sql.Timestamp.valueOf(cursorTime.replace('T', ' '));
        List<AiMessage> messages = aiMessageMapper.listByCursor(conversationId, time, cursorId, safeLimit + 1);
        boolean hasMore = messages.size() > safeLimit;
        if (hasMore) {
            messages = messages.subList(0, safeLimit);
        }
        List<AiMessageVO> items = new ArrayList<>();
        for (AiMessage m : messages) {
            items.add(toMessageVO(m));
        }
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            AiMessageVO oldest = items.get(items.size() - 1);
            nextCursor = oldest.getCreateTime().toInstant().toString() + "," + oldest.getId();
        }
        return MessagePageVO.builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    private ConversationVO toVO(AiConversation c) {
        AiCharacter character = c.getCharacterId() == null ? null : aiCharacterMapper.selectById(c.getCharacterId());
        return ConversationVO.builder()
                .id(c.getId())
                .characterId(c.getCharacterId())
                .characterName(character == null ? null : character.getName())
                .characterAvatarUrl(character == null ? null : character.getAvatarUrl())
                .title(c.getTitle())
                .createTime(c.getCreateTime())
                .lastMessageAt(c.getLastMessageAt())
                .build();
    }

    private AiMessageVO toMessageVO(AiMessage m) {
        return AiMessageVO.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .role(m.getRole())
                .content(m.getContent())
                .model(m.getModel())
                .inputTokens(m.getInputTokens())
                .outputTokens(m.getOutputTokens())
                .status(m.getStatus())
                .turnId(m.getTurnId())
                .messageIndex(m.getMessageIndex())
                .createTime(m.getCreateTime())
                .build();
    }
}

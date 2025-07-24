package com.xzh.friendxxx.service;

import com.xzh.friendxxx.model.entity.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author bb
* @description 针对表【chat_message】的数据库操作Service
* @createDate 2025-07-21 22:25:42
*/
public interface ChatMessageService extends IService<ChatMessage> {
    
    /**
     * 保存聊天消息
     */
    void saveChatMessage(Long senderId, Long receiverId, String content, String type, String conversationId);

    List<ChatMessage> getMessage(String conversationId);
}

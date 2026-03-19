package com.xzh.friendxxx.service;

import com.xzh.friendxxx.model.entity.AiChatMemory;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author bb
* @description 针对表【ai_chat_memory】的数据库操作Service
* @createDate 2025-07-28 13:55:58
*/
public interface AiChatMemoryService extends IService<AiChatMemory> {

    void deleteById(String conversationId);

    List<AiChatMemory> getMessageList(String conversationId);
}

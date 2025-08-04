package com.xzh.friendxxx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzh.friendxxx.model.entity.AiChatMemory;
import com.xzh.friendxxx.service.AiChatMemoryService;
import com.xzh.friendxxx.mapper.AiChatMemoryMapper;
import org.apache.ibatis.annotations.Delete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author bb
* @description 针对表【ai_chat_memory】的数据库操作Service实现
* @createDate 2025-07-28 13:55:58
*/
@Service
public class AiChatMemoryServiceImpl extends ServiceImpl<AiChatMemoryMapper, AiChatMemory>
    implements AiChatMemoryService{


    @Autowired
    private AiChatMemoryMapper aiChatMemoryMapper;
    @Override
    public void deleteById(String conversationId) {
        aiChatMemoryMapper.deleteMSGById(conversationId);
    }

    @Override
    public List<AiChatMemory> getMessageList(String conversationId) {
        List<AiChatMemory> LIST=  aiChatMemoryMapper.getMessageList(conversationId);
        return LIST;
    }
}





package com.xzh.friendxxx.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzh.friendxxx.model.entity.ChatMessage;
import com.xzh.friendxxx.model.vo.SenderVO;
import com.xzh.friendxxx.service.ChatMessageService;
import com.xzh.friendxxx.mapper.ChatMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
* @author bb
* @description 针对表【chat_message】的数据库操作Service实现
* @createDate 2025-07-21 22:25:42
*/
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
    implements ChatMessageService{

    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    RedisTemplate redisTemplate;
    @Override
    public void saveChatMessage(Long senderId, Long receiverId, String content, String type, String conversationId) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(senderId);
        chatMessage.setReceiverId(receiverId);
        chatMessage.setContent(content);
        chatMessage.setType(type);
        chatMessage.setCreateTime(new Date());
        chatMessage.setConversationId(conversationId);
        
        this.save(chatMessage);
    }

    @Override
    public List<ChatMessage> getMessage(String conversationId) {
        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        List<ChatMessage> messageList = chatMessageMapper.selectList(wrapper.eq("conversation_id", conversationId));
        return messageList;
    }

    @Override
    public List<SenderVO> getuser(long userId) {
        List<SenderVO> list = chatMessageMapper.getuserID(userId);
        return list;
    }

    @Override
    public void deletemsg(String conversationId) {
        chatMessageMapper.deletemsg(conversationId);
    }

    public void saveChatMessageRedis(ChatMessage chatMessage) {
        String messageKey="message:list_"+chatMessage.getConversationId();
        redisTemplate.opsForList().rightPush(messageKey, JSON.toJSONString(chatMessage));
        redisTemplate.expire(messageKey, 30, TimeUnit.MINUTES);
        redisTemplate.expire("message:user_"+chatMessage.getReceiverId(), 30, TimeUnit.MINUTES);
    }
}

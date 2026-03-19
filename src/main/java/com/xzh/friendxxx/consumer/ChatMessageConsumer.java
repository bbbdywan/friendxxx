package com.xzh.friendxxx.consumer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.xzh.friendxxx.config.MQConfig;
import com.xzh.friendxxx.model.entity.ChatMessage;
import com.xzh.friendxxx.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 聊天消息消费者（任务11）
 * 异步处理消息持久化，提升消息发送速度
 */
@Component
@Slf4j
public class ChatMessageConsumer {

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    @Qualifier("redisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 监听聊天消息队列，异步写入数据库
     */
    @RabbitListener(queues = MQConfig.QUEUE_CHAT_MESSAGE)
    public void handleChatMessage(String message) {
        try {
            log.info("收到聊天消息: {}", message);

            // 解析消息
            JSONObject jsonObject = JSON.parseObject(message);
            Long senderId = jsonObject.getLong("senderId");
            Long receiverId = jsonObject.getLong("receiverId");
            String content = jsonObject.getString("content");
            String type = jsonObject.getString("type");
            String conversationId = jsonObject.getString("conversationId");

            // 异步写入数据库
            chatMessageService.saveChatMessage(senderId, receiverId, content, type, conversationId);
            log.info("消息已保存到数据库: 发送者={}, 接收者={}, 类型={}, 会话ID={}",
                    senderId, receiverId, type, conversationId);

            // 更新 Redis 缓存
            updateRedisCache(senderId, receiverId, content, type, conversationId);

        } catch (Exception e) {
            log.error("处理聊天消息失败: {}", message, e);
            // 这里可以添加重试逻辑或者将失败消息存入死信队列
            throw new RuntimeException("消息处理失败", e);
        }
    }

    /**
     * 更新 Redis 缓存
     */
    private void updateRedisCache(Long senderId, Long receiverId, String content,
                                   String type, String conversationId) {
        try {
            // 创建 ChatMessage 对象
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSenderId(senderId);
            chatMessage.setReceiverId(receiverId);
            chatMessage.setContent(content);
            chatMessage.setType(type);
            chatMessage.setCreateTime(new java.util.Date());
            chatMessage.setConversationId(conversationId);

            // 更新 Redis 缓存（限制最多100条消息）
            String redisKey = "message:list_" + conversationId;
            redisTemplate.opsForList().rightPush(redisKey, JSON.toJSONString(chatMessage));

            // 裁剪列表，只保留最近100条消息
            redisTemplate.opsForList().trim(redisKey, -100, -1);

            // 设置过期时间为7天
            redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);

            log.info("Redis缓存已更新: 会话ID={}", conversationId);
        } catch (Exception e) {
            log.error("更新Redis缓存失败", e);
        }
    }
}

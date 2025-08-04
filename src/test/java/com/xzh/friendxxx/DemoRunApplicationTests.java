package com.xzh.friendxxx;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xzh.friendxxx.config.MQConfig;
import com.xzh.friendxxx.model.entity.ChatMessage;
import com.xzh.friendxxx.model.entity.GroupChat;
import com.xzh.friendxxx.model.entity.SocialPost;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.model.vo.SenderVO;
import com.xzh.friendxxx.service.ChatMessageService;
import com.xzh.friendxxx.service.GroupChatService;
import com.xzh.friendxxx.service.SocialPostService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.xzh.friendxxx.service.UserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;

@SpringBootTest(properties = {"websocket.enabled=false"})
class DemoRunApplicationTests {

    @Autowired
    UserService userService;
    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    SocialPostService socialPostService;
    @Autowired
    GroupChatService groupChatService;

    @Autowired
    ChatMessageService chatMessageService;

    @Autowired
    RabbitTemplate rabbitTemplate;
    @Test
    void contextLoads() {
        System.out.println("发送消息:我是一个延迟消息，开始时间："+System.currentTimeMillis());
        rabbitTemplate.convertAndSend(
                MQConfig.EXCHNAGE_DELAY,
                MQConfig.ROUTINGKEY_QUEUE_ORDER,
                "我是一个延迟消息",
                message -> {
                    // 设置过期时间（比如 5 分钟 = 300000 毫秒）
                    message.getMessageProperties().setExpiration("10000");
                    return message;
                }
        );

    }


}

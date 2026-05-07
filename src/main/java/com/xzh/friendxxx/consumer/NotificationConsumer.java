package com.xzh.friendxxx.consumer;

import com.alibaba.fastjson2.JSON;
import com.xzh.friendxxx.config.MQConfig;
import com.xzh.friendxxx.model.dto.NotificationMessage;
import com.xzh.friendxxx.model.entity.UserNotification;
import com.xzh.friendxxx.service.UserNotificationService;
import com.xzh.friendxxx.websocket.websocketserver.server.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class NotificationConsumer {

    public static final String OFFLINE_NOTIFY_KEY = "notify:offline:";
    private static final int MAX_OFFLINE_SIZE = 200;
    private static final long OFFLINE_EXPIRE_DAYS = 7;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserNotificationService userNotificationService;

    @RabbitListener(queues = MQConfig.QUEUE_NOTIFICATION)
    public void handleNotification(String message) {
        try {
            NotificationMessage notify = JSON.parseObject(message, NotificationMessage.class);
            log.info("收到通知消息: type={}, toUserId={}, fromUserId={}",
                    notify.getType(), notify.getToUserId(), notify.getFromUserId());

            // 1. 持久化到数据库
            UserNotification record = new UserNotification();
            record.setToUserId(notify.getToUserId());
            record.setFromUserId(notify.getFromUserId());
            record.setFromNickname(notify.getFromNickname());
            record.setType(notify.getType());
            record.setPostId(notify.getPostId());
            record.setContent(notify.getContent());
            userNotificationService.saveNotification(record);

            // 2. 构建 WebSocket 推送消息
            com.alibaba.fastjson2.JSONObject wsMsg = new com.alibaba.fastjson2.JSONObject();
            wsMsg.put("type", "notification");
            wsMsg.put("notifyType", notify.getType());
            wsMsg.put("fromUserId", notify.getFromUserId());
            wsMsg.put("fromNickname", notify.getFromNickname());
            wsMsg.put("postId", notify.getPostId());
            wsMsg.put("content", notify.getContent());
            wsMsg.put("timestamp", notify.getTimestamp());
            String wsPayload = wsMsg.toJSONString();

            // 3. 在线则实时推送，离线则存 Redis
            String toUserId = String.valueOf(notify.getToUserId());
            if (WebSocketServer.getOnlineUsers().containsKey(toUserId)) {
                WebSocketServer.sendInfo(wsPayload, toUserId);
                log.info("通知已实时推送给用户: {}", toUserId);
            } else {
                String key = OFFLINE_NOTIFY_KEY + toUserId;
                stringRedisTemplate.opsForList().rightPush(key, wsPayload);
                stringRedisTemplate.opsForList().trim(key, -MAX_OFFLINE_SIZE, -1);
                stringRedisTemplate.expire(key, OFFLINE_EXPIRE_DAYS, TimeUnit.DAYS);
                log.info("用户{}离线，通知已存入 Redis", toUserId);
            }
        } catch (Exception e) {
            log.error("处理通知消息失败: {}", message, e);
            throw new RuntimeException("通知消息处理失败", e);
        }
    }
}

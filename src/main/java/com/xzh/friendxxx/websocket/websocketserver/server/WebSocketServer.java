package com.xzh.friendxxx.websocket.websocketserver.server;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xzh.friendxxx.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ServerEndpoint("/websocket/{userId}")
@Component
@Slf4j
public class WebSocketServer {

    private static ChatMessageService chatMessageService;

    @Autowired
    RedisTemplate redisTemplate;
    @Autowired
    public void setChatMessageService(ChatMessageService chatMessageService) {
        WebSocketServer.chatMessageService = chatMessageService;
    }

    /** 静态变量，用来记录当前在线连接数 */
    private static final AtomicInteger onlineCount = new AtomicInteger(0);
    
    /** 线程安全的Map，用来存放每个客户端对应的WebSocket对象 */
    private static final ConcurrentHashMap<String, WebSocketServer> webSocketMap = new ConcurrentHashMap<>();
    
    /** 与某个客户端的连接会话 */
    private Session session;
    
    /** 接收userId */
    private String userId = "";

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        this.session = session;
        this.userId = userId;
        
        if (webSocketMap.containsKey(userId)) {
            webSocketMap.remove(userId);
            webSocketMap.put(userId, this);
        } else {
            webSocketMap.put(userId, this);
            onlineCount.incrementAndGet();
        }

        log.info("用户连接:{}, 当前在线人数为:{}", userId, getOnlineCount());

        try {
            sendMessage("连接成功");
        } catch (IOException e) {
            log.error("用户:{}, 网络异常!", userId, e);
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        if (webSocketMap.containsKey(userId)) {
            webSocketMap.remove(userId);
            onlineCount.decrementAndGet();
        }
        log.info("用户退出:{}, 当前在线人数为:{}", userId, getOnlineCount());
    }

    /**
     * 收到客户端消息后调用的方法
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("用户消息:{}, 报文:{}", userId, message);
        
        if (StringUtils.isNotBlank(message)) {
            try {
                JSONObject jsonObject = JSON.parseObject(message);
                String messageType = jsonObject.getString("type");
                String toUserId = jsonObject.getString("toUserId");
                String messageContent = jsonObject.getString("message");
                
                // 构建转发消息
                JSONObject forwardMessage = new JSONObject();
                forwardMessage.put("fromUserId", this.userId);
                forwardMessage.put("message", messageContent);
                forwardMessage.put("timestamp", System.currentTimeMillis());
                forwardMessage.put("type", messageType);
                
                if ("private".equals(messageType) && StringUtils.isNotBlank(toUserId)) {
                    // 保存私聊消息到数据库
                    saveChatMessage(Long.valueOf(this.userId), Long.valueOf(toUserId), messageContent, "private");
                    
                    // 私聊消息转发
                    sendInfo(forwardMessage.toJSONString(), toUserId);
                    
                    // 给发送者确认消息
                    JSONObject ackMessage = new JSONObject();
                    ackMessage.put("type", "ack");
                    ackMessage.put("status", "sent");
                    ackMessage.put("toUserId", toUserId);
                    sendMessage(ackMessage.toJSONString());
                    
                } else if ("group".equals(messageType)) {
                    // 保存群聊消息到数据库
                    saveChatMessage(Long.valueOf(this.userId), null, messageContent, "group");
                    
                    // 群聊消息转发
                    sendAllMessage(forwardMessage.toJSONString());
                } else {
                    // 默认群发
                    saveChatMessage(Long.valueOf(this.userId), null, messageContent, "group");
                    sendAllMessage(forwardMessage.toJSONString());
                }
                
            } catch (Exception e) {
                log.error("消息处理异常", e);
                sendErrorMessage("消息格式错误");
            }
        }
    }

    /**
     * 发生错误时调用
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("用户错误:{}, 原因:{}", this.userId, error.getMessage(), error);
    }

    /**
     * 实现服务器主动推送
     */
    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }

    /**
     * 群发自定义消息
     */
    public void sendAllMessage(String message) throws IOException {
        if (StringUtils.isBlank(message)) {
            log.warn("尝试发送空消息，已忽略");
            return;
        }
        
        for (String userId : webSocketMap.keySet()) {
            try {
                WebSocketServer webSocketServer = webSocketMap.get(userId);
                if (webSocketServer != null && webSocketServer.session.isOpen()) {
                    webSocketServer.sendMessage(message);
                }
            } catch (IOException e) {
                log.error("发送消息给用户{}失败", userId, e);
            }
        }
    }

    /**
     * 发送消息给指定用户，支持离线消息存储
     */
    public static void sendInfo(String message, String userId) {
        log.info("发送消息到:{}, 报文:{}", userId, message);
        
        if (StringUtils.isNotBlank(userId) && webSocketMap.containsKey(userId)) {
            try {
                WebSocketServer webSocketServer = webSocketMap.get(userId);
                if (webSocketServer != null && webSocketServer.session.isOpen()) {
                    webSocketServer.sendMessage(message);
                    log.info("消息发送成功到用户:{}", userId);
                } else {
                    log.warn("用户{}连接已断开，消息发送失败", userId);
                    // 这里可以存储离线消息到数据库
                    storeOfflineMessage(userId, message);
                }
            } catch (IOException e) {
                log.error("发送消息给用户{}失败", userId, e);
                storeOfflineMessage(userId, message);
            }
        } else {
            log.error("用户{}, 不在线！", userId);
            // 存储离线消息
            storeOfflineMessage(userId, message);
        }
    }

    /**
     * 存储离线消息（可以扩展到数据库）
     */
    private static void storeOfflineMessage(String userId, String message) {
        // TODO: 实现离线消息存储到数据库
        log.info("存储离线消息给用户:{}, 消息:{}", userId, message);
    }

    /**
     * 获取在线用户列表
     */
    public static ConcurrentHashMap<String, WebSocketServer> getOnlineUsers() {
        return webSocketMap;
    }

    public static int getOnlineCount() {
        return onlineCount.get();
    }

    /**
     * 发送错误消息给当前用户
     */
    private void sendErrorMessage(String errorMsg) {
        try {
            JSONObject errorMessage = new JSONObject();
            errorMessage.put("type", "error");
            errorMessage.put("message", errorMsg);
            errorMessage.put("timestamp", System.currentTimeMillis());
            sendMessage(errorMessage.toJSONString());
        } catch (IOException e) {
            log.error("发送错误消息失败", e);
        }
    }

    /**
     * 保存聊天消息到数据库
     */
    private void saveChatMessage(Long senderId, Long receiverId, String content, String type) {
        try {
            // 生成会话ID
            String conversationId = generateConversationId(senderId, receiverId, type);
            
            if (chatMessageService != null) {
                chatMessageService.saveChatMessage(senderId, receiverId, content, type, conversationId);

                log.info("消息已保存到数据库: 发送者={}, 接收者={}, 类型={}", senderId, receiverId, type);

            } else {
                log.warn("ChatMessageService未注入，无法保存消息");
            }
        } catch (Exception e) {
            log.error("保存消息到数据库失败", e);
        }
    }

    /**
     * 生成会话ID
     */
    private String generateConversationId(Long senderId, Long receiverId, String type) {
        if ("private".equals(type) && receiverId != null) {
            // 私聊：使用较小的ID在前，确保同一对话的会话ID一致
            Long minId = Math.min(senderId, receiverId);
            Long maxId = Math.max(senderId, receiverId);
            return "private_" + minId + "_" + maxId;
        } else {
            // 群聊：使用固定的群聊ID
            return "group_chat";
        }
    }


}

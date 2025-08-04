package com.xzh.friendxxx.websocket.websocketserver.server;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.xzh.friendxxx.model.entity.ChatMessage;
import com.xzh.friendxxx.service.ChatMessageService;
import com.xzh.friendxxx.service.GroupMemberService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ServerEndpoint("/websocket/{userId}")
@Component
@Slf4j
public class WebSocketServer {

    private static ChatMessageService chatMessageService;
    private static RedisTemplate<String, String> redisTemplate;
    @Autowired
    public void setChatMessageService(ChatMessageService chatMessageService) {
        WebSocketServer.chatMessageService = chatMessageService;
    }

    @Autowired
    @Qualifier("redisTemplate")
    public void setRedisTemplate(RedisTemplate<String, String> redisTemplate) {
        WebSocketServer.redisTemplate = redisTemplate;
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
        if (StringUtils.isNotBlank(message)) {
            try {
                JSONObject jsonObject = JSON.parseObject(message);
                String messageType = jsonObject.getString("type");
                String messageContent = jsonObject.getString("message");

                if ("group".equals(messageType)) {
                    Long groupId = jsonObject.getLong("groupId");
                    // 群聊消息处理
                    sendGroupMessage(Long.valueOf(this.userId), groupId, messageContent);
                } else if ("private".equals(messageType)) {
                    String toUserId = jsonObject.getString("toUserId");
                    // 私聊消息处理
                    sendPrivateMessage(Long.valueOf(this.userId), Long.valueOf(toUserId), messageContent);
                }
                // ... 其他消息类型处理
            } catch (Exception e) {
                log.error("消息处理异常", e);
            }
        }
    }

    private void sendGroupMessage(Long senderId, Long groupId, String content) {
        try {
            // 1. 保存群聊消息到数据库并更新Redis缓存
            String conversationId = "group_" + groupId;
            saveChatMessageAndCache(senderId, null, content, "group", conversationId);

            // 2. 获取群成员列表
            List<Long> memberIds = getGroupMembers(groupId);

            // 3. 构建转发消息
            JSONObject forwardMessage = new JSONObject();
            forwardMessage.put("fromUserId", senderId);
            forwardMessage.put("groupId", groupId);
            forwardMessage.put("message", content);
            forwardMessage.put("timestamp", System.currentTimeMillis());
            forwardMessage.put("type", "group");

            // 4. 发送给所有群成员
            for (Long memberId : memberIds) {
                sendInfo(forwardMessage.toJSONString(), String.valueOf(memberId));
            }

        } catch (Exception e) {
            log.error("群聊消息发送失败", e);
        }
    }

    private void sendPrivateMessage(Long senderId, Long receiverId, String content) {
        try {
            // 1. 生成会话ID
            String conversationId = generateConversationId(senderId, receiverId, "private");

            // 2. 保存私聊消息到数据库并更新Redis缓存
            saveChatMessageAndCache(senderId, receiverId, content, "private", conversationId);

            // 3. 构建转发消息
            JSONObject forwardMessage = new JSONObject();
            forwardMessage.put("fromUserId", senderId);
            forwardMessage.put("toUserId", receiverId);
            forwardMessage.put("message", content);
            forwardMessage.put("timestamp", System.currentTimeMillis());
            forwardMessage.put("type", "private");
            forwardMessage.put("conversationId", conversationId);

            // 4. 发送给接收者
            sendInfo(forwardMessage.toJSONString(), String.valueOf(receiverId));

            log.info("私聊消息发送成功: 发送者={}, 接收者={}, 内容={}", senderId, receiverId, content);

        } catch (Exception e) {
            log.error("私聊消息发送失败: 发送者={}, 接收者={}", senderId, receiverId, e);
            // 发送错误消息给发送者
            sendErrorMessage("消息发送失败: " + e.getMessage());
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
     * 保存聊天消息到数据库并更新Redis缓存
     */
    private void saveChatMessageAndCache(Long senderId, Long receiverId, String content, String type, String conversationId) {
        try {
            if (chatMessageService != null) {
                // 1. 保存到数据库
                chatMessageService.saveChatMessage(senderId, receiverId, content, type, conversationId);
                log.info("消息已保存到数据库: 发送者={}, 接收者={}, 类型={}", senderId, receiverId, type);

                // 2. 创建ChatMessage对象用于Redis缓存
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setSenderId(senderId);
                chatMessage.setReceiverId(receiverId);
                chatMessage.setContent(content);
                chatMessage.setType(type);
                chatMessage.setCreateTime(new java.util.Date());
                chatMessage.setConversationId(conversationId);

                // 3. 更新Redis缓存
                if (redisTemplate != null) {
                    redisTemplate.opsForList().rightPush("message:list_" + conversationId,
                        JSON.toJSONString(chatMessage));
                    log.info("消息已更新到Redis缓存: 会话ID={}", conversationId);
                } else {
                    log.warn("RedisTemplate未注入，无法更新缓存");
                }
            } else {
                log.warn("ChatMessageService未注入，无法保存消息");
            }
        } catch (Exception e) {
            log.error("保存消息到数据库或更新Redis缓存失败", e);
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

    private static GroupMemberService groupMemberService;

    @Autowired
    public void setGroupMemberService(GroupMemberService groupMemberService) {
        WebSocketServer.groupMemberService = groupMemberService;
    }

    private List<Long> getGroupMembers(Long groupId) {
        if (groupMemberService != null) {
            return groupMemberService.getGroupMemberIds(groupId);
        }
        log.warn("GroupMemberService未注入，返回空列表");
        return new ArrayList<>();
    }
}

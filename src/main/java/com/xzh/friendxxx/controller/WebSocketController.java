package com.xzh.friendxxx.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.model.entity.ChatMessage;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.model.vo.MessageVO;
import com.xzh.friendxxx.model.vo.SenderVO;
import com.xzh.friendxxx.service.ChatMessageService;
import com.xzh.friendxxx.service.UserService;
import com.xzh.friendxxx.websocket.websocketserver.server.WebSocketServer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/websocket")
@Tag(name = "WebSocket管理", description = "WebSocket相关接口")
public class WebSocketController {

    @Autowired
    private UserService userService;
    private final ChatMessageService chatMessageService;

    @Autowired
    RedisTemplate<String,String> redisTemplate;

    public WebSocketController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    @GetMapping("/online-users")
    @Operation(summary = "获取在线用户列表")
    public Result<Map<String, Object>> getOnlineUsers() {
        Map<String, Object> result = new HashMap<>();
        Set<String> onlineUserIds = WebSocketServer.getOnlineUsers().keySet();
        result.put("onlineUsers", onlineUserIds);
        result.put("onlineCount", WebSocketServer.getOnlineCount());
        return Result.success(result);
    }

    @GetMapping("/getmessage")
    @Operation(summary = "获取用户聊天记录")
    public Result<MessageVO> getusermessage(@RequestParam("UserId2") long UserId2) {
        if (UserId2 <= 0) {
            return Result.error(400, "聊天用户参数错误");
        }
        User chatUser = userService.getById(UserId2);
        if (chatUser == null) {
            return Result.error(404, "聊天用户不存在");
        }

        long UserId1 = BaseContext.getCurrentId();
        Long minId = Math.min(UserId1, UserId2);
        Long maxId = Math.max(UserId1, UserId2);
        String conversationId = "private_" + minId + "_" + maxId;
        List<ChatMessage> messageList = new ArrayList<>();
        List<String> range = redisTemplate.opsForList().range("message:list_" + conversationId,0,-1);
        if (range != null && !range.isEmpty()) {
            messageList = range.stream()
                    .map(json -> {
                        try {
                            return JSON.parseObject(json, ChatMessage.class);
                        } catch (Exception e) {
                            log.warn("忽略无法解析的聊天缓存记录");
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        if (messageList.isEmpty()) {
            messageList = chatMessageService.getMessage(conversationId);
            String cacheKey = "message:list_" + conversationId;
            redisTemplate.delete(cacheKey);
            if (messageList != null && !messageList.isEmpty()) {
                List<String> cachedMessages = messageList.stream()
                        .map(JSON::toJSONString)
                        .collect(Collectors.toList());
                redisTemplate.opsForList().rightPushAll(cacheKey, cachedMessages);
                redisTemplate.opsForList().trim(cacheKey, -100, -1);
                redisTemplate.expire(cacheKey, 7, TimeUnit.DAYS);
            }
        }

        JSONObject userInfo = new JSONObject();
        userInfo.put("avatar", chatUser.getAvatarUrl());
        userInfo.put("userName", chatUser.getUsername());
        redisTemplate.opsForValue().set("message:user_" + UserId2,
                userInfo.toJSONString(), 30, TimeUnit.MINUTES);

        return Result.success(MessageVO.builder()
                .messageList(messageList)
                .avatar(chatUser.getAvatarUrl())
                .userName(chatUser.getUsername())
                .build());
    }

    @PostMapping("/send-message")
    @Operation(summary = "服务端主动发送消息")
    public Result<String> sendMessage(@RequestParam("toUserId") String toUserId,
                                    @RequestParam("message") String message) {
        try {
            User currentUser = userService.getById(BaseContext.getCurrentId());
            if (currentUser == null || !Integer.valueOf(3).equals(currentUser.getUserRole())) {
                return Result.error(403, "仅管理员可使用服务端主动推送接口");
            }
            if (StringUtils.isBlank(toUserId) || StringUtils.isBlank(message) || message.length() > 2_000) {
                return Result.error(400, "推送参数错误");
            }
            WebSocketServer.sendInfo(message, toUserId);
            return Result.success("消息发送成功");
        } catch (Exception e) {
            log.error("服务端主动推送失败", e);
            return Result.error(500, "消息发送失败");
        }
    }

    @GetMapping("/messagelist")
    @Operation(summary = "获取用户最近聊天记录对象")
    public Result<List<SenderVO>> getsender() {
        long UserId = BaseContext.getCurrentId();
        List<SenderVO>  usermessage =  chatMessageService.getuser(UserId);
        return Result.success(usermessage);
    }

    @GetMapping("/deletemessage")
    @Operation(summary = "删除聊天记录")
    public Result<String> deletemessage(@RequestParam(value = "conversationId", name = "conversationId") String conversationId) {
        if (!isCurrentUserConversation(conversationId)) {
            return Result.error(403, "无权删除该会话");
        }
        redisTemplate.delete("message:list_" + conversationId);
        chatMessageService.deletemsg(conversationId);
        return Result.success("删除成功");
    }

    @PutMapping("/clearUnread")
    @Operation(summary = "清除会话未读数", description = "用户打开聊天窗口时调用，将该会话未读数清零")
    public Result<Void> clearUnread(@RequestParam Long chatUserId) {
        Long userId = BaseContext.getCurrentId();
        Long min = Math.min(userId, chatUserId);
        Long max = Math.max(userId, chatUserId);
        String conversationId = "private_" + min + "_" + max;
        chatMessageService.clearUnread(userId, conversationId);
        return Result.success(null);
    }

    private boolean isCurrentUserConversation(String conversationId) {
        if (conversationId == null || !conversationId.matches("private_\\d+_\\d+")) {
            return false;
        }
        String[] parts = conversationId.split("_");
        String currentUserId = BaseContext.getCurrentId().toString();
        return currentUserId.equals(parts[1]) || currentUserId.equals(parts[2]);
    }
}

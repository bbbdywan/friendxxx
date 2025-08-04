package com.xzh.friendxxx.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.exception.ErrorCode;
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
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.awt.geom.QuadCurve2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Objects;

import org.slf4j.LoggerFactory;
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
    public Result<MessageVO> getusermessage(@RequestParam("UserId1") long UserId1,
                                            @RequestParam("UserId2") long UserId2) {
        Long minId = Math.min(UserId1, UserId2);
        Long maxId = Math.max(UserId1, UserId2);
        String conversationId = "private_" + minId + "_" + maxId;
        String avatar="";
        String userName="";
        String usermessage="";
        List<String> range = redisTemplate.opsForList().range("message:list_" + conversationId,0,-1);
        if (range != null && !range.isEmpty()) {  // 修改：非空时才处理
            usermessage = redisTemplate.opsForValue().get("message:user_"+UserId2);
            if (usermessage != null) {
                JSONObject userInfo = JSON.parseObject(usermessage);
                avatar = userInfo.getString("avatar");
                userName = userInfo.getString("userName");
                List<ChatMessage> messageList = range.stream()
                        .map(json -> {
                            try {
                                return JSON.parseObject(json, ChatMessage.class);
                            } catch (Exception e) {
                                log.error("解析消息JSON失败: {}", json, e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                return Result.success(MessageVO.builder().messageList(messageList).avatar(avatar).userName(userName).build());
            }
        }
        //如果为空
       // List<String> messagelist = redisTemplate.opsForList().range("message:list" + "private_" + minId + "_" + maxId, 0, -1);// 正常返回 JSON 字符串
        List<ChatMessage> chatMessageONEC = chatMessageService.getMessage(conversationId);
         avatar=userService.getById(UserId2).getAvatarUrl();
         userName=userService.getById(UserId2).getUsername();

        JSONObject userInfo = new JSONObject();
        userInfo.put("avatar", avatar);
        userInfo.put("userName", userName);

        MessageVO build = MessageVO.builder().messageList(chatMessageONEC).avatar(avatar).userName(userName).build();
        //redisTemplate.opsForValue().set("message:list"+"private_"+minId+"_"+maxId, JSON.toJSONString(build), 30, TimeUnit.MINUTES);
        for (ChatMessage message : chatMessageONEC) {
            redisTemplate.opsForList().rightPush("message:list_" + conversationId, JSON.toJSONString(message));
        }

        redisTemplate.opsForValue().set("message:user_"+UserId2,userInfo.toJSONString());
        return Result.success(build);
    }

    @PostMapping("/send-message")
    @Operation(summary = "服务端主动发送消息")
    public Result<String> sendMessage(@RequestParam("toUserId") String toUserId,
                                    @RequestParam("message") String message) {
        try {
            WebSocketServer.sendInfo(message, toUserId);
            return Result.success("消息发送成功");
        } catch (Exception e) {
            return Result.error(500, "消息发送失败: " + e.getMessage());
        }
    }

    @GetMapping("/messagelist")
    @Operation(summary = "获取用户最近聊天记录对象")
    public Result<List<SenderVO>> getsender(@RequestParam("UserId") long UserId) {
        List<SenderVO>  usermessage =  chatMessageService.getuser(UserId);
        return Result.success(usermessage);
    }

    @GetMapping("/deletemessage")
    @Operation(summary = "删除聊天记录")
    public Result<String> deletemessage(    @RequestParam(value = "conversationId", name = "conversationId") String conversationId) {
        redisTemplate.delete("message:list_" + conversationId);
        chatMessageService.deletemsg(conversationId);
        return Result.success("删除成功");
    }
}
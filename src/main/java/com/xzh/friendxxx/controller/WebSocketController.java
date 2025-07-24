package com.xzh.friendxxx.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.exception.ErrorCode;
import com.xzh.friendxxx.model.entity.ChatMessage;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.model.vo.MessageVO;
import com.xzh.friendxxx.service.ChatMessageService;
import com.xzh.friendxxx.service.UserService;
import com.xzh.friendxxx.websocket.websocketserver.server.WebSocketServer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

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
@Api(tags = "WebSocket管理", description = "WebSocket相关接口")
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
    @ApiOperation(value = "获取在线用户列表")
    public Result<Map<String, Object>> getOnlineUsers() {
        Map<String, Object> result = new HashMap<>();
        Set<String> onlineUserIds = WebSocketServer.getOnlineUsers().keySet();
        result.put("onlineUsers", onlineUserIds);
        result.put("onlineCount", WebSocketServer.getOnlineCount());
        return Result.success(result);
    }

    @GetMapping("/getmessage")
    @ApiOperation(value = "获取用户聊天记录")
    public Result<MessageVO> getusermessage(@RequestParam long UserId1,
                                            @RequestParam long UserId2) {
        Long minId = Math.min(UserId1, UserId2);
        Long maxId = Math.max(UserId1, UserId2);
        String conversationId = "private_" + minId + "_" + maxId;
        String avatar="";
        String userName="";
        String usermessage="";
        List<String> range = redisTemplate.opsForList().range("message:list_" + conversationId,0,-1);
        if (range != null && !range.isEmpty()) {  // 修改：非空时才处理
            usermessage = redisTemplate.opsForValue().get("message:user_"+UserId1);
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

        redisTemplate.opsForValue().set("message:user_"+UserId1,userInfo.toJSONString());
        return Result.success(build);
    }

    @PostMapping("/send-message")
    @ApiOperation(value = "服务端主动发送消息")
    public Result<String> sendMessage(@RequestParam String toUserId, 
                                    @RequestParam String message) {
        try {
            WebSocketServer.sendInfo(message, toUserId);
            return Result.success("消息发送成功");
        } catch (Exception e) {
            return Result.error(500, "消息发送失败: " + e.getMessage());
        }
    }
}
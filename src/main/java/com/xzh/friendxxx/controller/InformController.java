package com.xzh.friendxxx.controller;

import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.model.entity.UserNotification;
import com.xzh.friendxxx.service.UserNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inform")
@Tag(name = "通知管理模块", description = "提供通知相关的接口")
public class InformController {

    @Autowired
    private UserNotificationService userNotificationService;

    @GetMapping("/list")
    @Operation(summary = "获取互动消息列表", description = "分页获取当前用户收到的所有点赞/评论通知")
    public Result<List<UserNotification>> list(@RequestParam Long userId,
                                               @RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(userNotificationService.listByUserId(userId, page, size));
    }

    @GetMapping("/unread")
    @Operation(summary = "获取未读消息数量")
    public Result<Long> unread(@RequestParam Long userId) {
        return Result.success(userNotificationService.countUnread(userId));
    }

    @PutMapping("/readAll")
    @Operation(summary = "全部标记已读")
    public Result<Void> readAll(@RequestParam Long userId) {
        userNotificationService.readAll(userId);
        return Result.success(null);
    }

    @PutMapping("/readOne")
    @Operation(summary = "单条标记已读")
    public Result<Void> readOne(@RequestParam Long id, @RequestParam Long userId) {
        userNotificationService.readOne(id, userId);
        return Result.success(null);
    }
}

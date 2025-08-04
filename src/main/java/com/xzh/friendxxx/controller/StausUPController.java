package com.xzh.friendxxx.controller;


import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.config.MQConfig;
import com.xzh.friendxxx.model.entity.SocialPost;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.service.SocialPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/stausup")
@Tag(name = "动态管理模块", description = "提供动态相关的接口")
@Component
public class StausUPController {

    @Autowired
    private SocialPostService socialPostService;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @PostMapping("/newpost")
    @Operation(summary = "发布动态", description = "发布动态接口")
    public Result<Integer> newpost(@RequestBody SocialPost socialPost) {

      socialPostService.save(socialPost);
      if(socialPost.getDeleteTtl()!=null){
        rabbitTemplate.convertAndSend(
                MQConfig.EXCHNAGE_DELAY,
                MQConfig.ROUTINGKEY_QUEUE_ORDER,
                "限时时间唯一标识-"+socialPost.getSupTtl(),
                message -> {
                    // 设置过期时间（比如 5 分钟 = 300000 毫秒）
                    message.getMessageProperties().setExpiration(socialPost.getDeleteTtl());
                    return message;
                }

        );}
        return Result.success(1);
    }

    @PostMapping("/update/image")
    @Operation(summary = "用户上传图片", description = "用户上传图片接口")
    public Result<String> updateImage(@RequestParam("file") MultipartFile file) {
        //上传头像并获取URL
        String avatarUrl = socialPostService.uploadAvatar(file);

        return Result.success(avatarUrl);
    }

    @GetMapping("/getstup")
    @Operation(summary = "获取动态", description = "获取动态接口")
    public Result<List<SocialPost>> getstup() {
        List<SocialPost> list = socialPostService.list();
        return Result.success(list);
    }

    @GetMapping("/getuserup")
    @Operation(summary = "获取当前用户动态", description = "获取当前用户动态接口")
    public Result<List<SocialPost>> getuserup(@RequestParam("userId") Long userId) {
        List<SocialPost> list= socialPostService.getlist(userId);
        return Result.success(list);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除动态", description = "删除动态接口")
    public Result<String> delete(@RequestParam("id") Long id) {
         if(socialPostService.removeById(id))
            return Result.success("删除成功");
        return Result.success("删除失败");
    }

    @RabbitListener(queues = MQConfig.QUEUE_DELAY)
    public void handler(String message){
        try {
            String[] split = message.split("-");
            if (split.length >= 2) {
                String deleteTtl = split[1];
                socialPostService.remove(deleteTtl);
            } else {
                System.err.println("消息格式错误，期望格式: 'prefix-deleteTtl'，实际: " + message);
            }
        } catch (Exception e) {
            System.err.println("处理延迟消息失败: " + message + ", 错误: " + e.getMessage());
        }
    }
}

package com.xzh.friendxxx.controller;


//import com.xzh.friendxxx.Repository.EsPostRepository;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.config.MQConfig;
import com.xzh.friendxxx.model.dto.CommentDTO;
import com.xzh.friendxxx.model.dto.LikesDTO;
import com.xzh.friendxxx.model.dto.UpSociaPost;
import com.xzh.friendxxx.model.entity.SocialPost;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.service.SocialPostService;
import com.xzh.friendxxx.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;


import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stausup")
@Tag(name = "动态管理模块", description = "提供动态相关的接口")
@Slf4j
public class StausUPController {


    @Autowired
    private SocialPostService socialPostService;

    @Autowired
    private UserService userService;

    @Autowired
    private RabbitTemplate rabbitTemplate;



    @PostMapping("/newpost")
    @Operation(summary = "发布动态", description = "发布动态接口")
    @Transactional
    public Result<Integer> newpost(@RequestBody SocialPost socialPost) {
        if (socialPost == null) {
            return Result.error("动态内容不能为空");
        }
        boolean hasContent = socialPost.getContent() != null && !socialPost.getContent().isBlank();
        boolean hasImages = socialPost.getImageList() != null && !socialPost.getImageList().isEmpty();
        if (!hasContent && !hasImages) {
            return Result.error("文字和图片不能同时为空");
        }

        if (socialPost.getDeleteTtl() != null && !socialPost.getDeleteTtl().isBlank()) {
            try {
                long ttlMillis = Long.parseLong(socialPost.getDeleteTtl());
                if (ttlMillis < 1_000 || ttlMillis > 7L * 24 * 60 * 60 * 1_000) {
                    return Result.error("动态有效期必须在1秒到7天之间");
                }
                socialPost.setDeleteTtl(Long.toString(ttlMillis));
                socialPost.setSupTtl(UUID.randomUUID().toString());
            } catch (NumberFormatException e) {
                return Result.error("动态有效期格式错误");
            }
        } else {
            socialPost.setDeleteTtl(null);
            socialPost.setSupTtl(null);
        }

        Long currentUserId = BaseContext.getCurrentId();
        User currentUser = userService.getById(currentUserId);
        if (currentUser == null) {
            return Result.error("用户不存在");
        }
        socialPost.setId(null);
        socialPost.setUserId(currentUserId);
        socialPost.setNickname(currentUser.getUsername());
        socialPost.setAvatarUrl(currentUser.getAvatarUrl());
        socialPost.setLikeCount(0);
        socialPost.setIsDeleted(0);
        if (!socialPostService.save(socialPost)) {
            return Result.error("发布失败");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (socialPost.getDeleteTtl() != null) {
                    try {
                        rabbitTemplate.convertAndSend(
                                MQConfig.EXCHNAGE_DELAY,
                                MQConfig.ROUTINGKEY_QUEUE_ORDER,
                                "限时时间唯一标识-" + socialPost.getSupTtl(),
                                message -> {
                                    message.getMessageProperties().setExpiration(socialPost.getDeleteTtl());
                                    return message;
                                }
                        );
                    } catch (Exception e) {
                        log.error("限时动态删除任务发送失败: postId={}", socialPost.getId(), e);
                    }
                }
            }
        });

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

    @PostMapping("/likes")
    @Operation(summary = "点赞动态", description = "点赞动态接口")
    public Result<Integer>likesup(@RequestBody LikesDTO likesDTO) {
        if (likesDTO == null || likesDTO.getPostid() == null || likesDTO.getLikesId() == null) {
            return Result.error("参数错误");
        }
        likesDTO.setUserId(BaseContext.getCurrentId());
        int count=socialPostService.updateLikescount(likesDTO);
        return Result.success(count);
    }

    @GetMapping("/getcurrentup")
    public Result<SocialPost> getonlyup(@RequestParam("id") Long id) {
        SocialPost byId = socialPostService.getByCurrentId(id);
        if (byId == null) {
            return Result.error(404, "动态不存在");
        }
        return  Result.success(byId);
    }
    @PostMapping("/upcurrentstatus")
    @Operation(summary = "修改当前用户动态", description = "修改用户动态接口")
    public Result updatesocia(@RequestBody UpSociaPost upSociaPost)
    {
        if (upSociaPost == null || upSociaPost.getId() == null) {
            return Result.error("参数错误");
        }
        SocialPost existing = socialPostService.getById(upSociaPost.getId());
        if (existing == null || !BaseContext.getCurrentId().equals(existing.getUserId())) {
            return Result.error("无权修改该动态");
        }

        int i = socialPostService.updatecurrentpost(upSociaPost);
        if(i>0) {
            return Result.success(i);
        }
        return Result.error("修改失败");
    }

    @PostMapping("/comment")
    @Operation(summary = "评论动态", description = "评论接口")
    public Result<Integer> addComment(@RequestBody CommentDTO commentDTO) {
        if (commentDTO.getPostId() == null || commentDTO.getContent() == null || commentDTO.getContent().isBlank()) {
            return Result.error("参数错误");
        }
        Long currentUserId = BaseContext.getCurrentId();
        User currentUser = userService.getById(currentUserId);
        if (currentUser == null || socialPostService.getById(commentDTO.getPostId()) == null) {
            return Result.error(404, "用户或动态不存在");
        }
        commentDTO.setUserId(currentUserId);
        commentDTO.setNickname(currentUser.getUsername());
        commentDTO.setAvatarUrl(currentUser.getAvatarUrl());
        socialPostService.addComment(commentDTO);
        return Result.success(1);
    }

    @GetMapping("/comments/{postId}")
    @Operation(summary = "查看动态评论", description = "查看动态评论接口")
    public Result<List<CommentDTO>> getComments(@PathVariable Long postId) {
        List<CommentDTO> comments = socialPostService.getComments(postId);
        return Result.success(comments);
    }

    @GetMapping("/getuserup")
    @Operation(summary = "获取当前用户动态", description = "获取当前用户动态接口")
    public Result<List<SocialPost>> getuserup(@RequestParam("userId") Long userId) {
        List<SocialPost> list= socialPostService.getlist(userId);
        return Result.success(list);
    }

    @Transactional
    @DeleteMapping("/delete")
    @Operation(summary = "删除动态", description = "删除动态接口")
    public Result<String> delete(@RequestParam("id") Long id) {
         SocialPost existing = socialPostService.getById(id);
         if (existing == null || !BaseContext.getCurrentId().equals(existing.getUserId())) {
             return Result.error("无权删除该动态");
         }
         if(socialPostService.removeById(id)){
            return Result.success("删除成功");}
        return Result.error("删除失败");
    }

    @RabbitListener(queues = MQConfig.QUEUE_DELAY)
    public void handler(String message){
        try {
            String[] split = message.split("-", 2);
            if (split.length >= 2) {
                String deleteTtl = split[1];
                socialPostService.remove(deleteTtl);
            } else {
                log.warn("动态延迟删除消息格式错误");
            }
        } catch (Exception e) {
            log.error("处理动态延迟删除消息失败", e);
            throw new IllegalStateException("处理动态延迟删除消息失败", e);
        }
    }
}

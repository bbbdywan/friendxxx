package com.xzh.friendxxx.controller;

import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.model.dto.LikesDTO;
import com.xzh.friendxxx.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inform")
@Tag(name = "通知管理模块", description = "提供通知相关的接口")
@Component
public class InformController {

    @Autowired
    private LikeService likeService;

    @PostMapping("/likes")
    @Operation(summary = "点赞", description = "用户点赞接口")
    public Result<Integer> likes(@RequestParam LikesDTO dto) {
        //评论和点赞,用redis去存
        if (dto.getPostid() == null || dto.getUserId() == null) {
            return Result.error("参数错误");
        }

        Integer likeCount = likeService.handleLike(dto);

        return Result.success(likeCount);
    }
}

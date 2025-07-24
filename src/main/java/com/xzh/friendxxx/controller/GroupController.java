package com.xzh.friendxxx.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.exception.ErrorCode;
import com.xzh.friendxxx.model.dto.GroupCreatDTO;
import com.xzh.friendxxx.model.dto.GroupJoinDTO;
import com.xzh.friendxxx.model.entity.GroupChat;
import com.xzh.friendxxx.service.GroupChatService;
import com.xzh.friendxxx.service.GroupMemberService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/group")
@Api(tags = "群聊管理模块", description = "提供群聊相关的接口")
public class GroupController {


    @Autowired
    private GroupChatService groupChatService;

    @Autowired
    private GroupMemberService groupMemberService;


    @PostMapping("/create")
    @ApiOperation(value = "创建群聊", notes = "创建群聊接口")
    public Result<Integer> createGroup(@RequestBody GroupCreatDTO groupCreatDTO) {
        QueryWrapper<GroupChat> groupName = new QueryWrapper<>();
        groupName.eq("group_name", groupCreatDTO.getGroup_name());
        if(groupChatService.getOne(groupName)!=null)
            return Result.error(ErrorCode.GROUP_ERROR.getCode(), ErrorCode.GROUP_ERROR.getMessage());
        int save = groupChatService.save(groupCreatDTO);
        if(save==0)
            return Result.error(ErrorCode.GROUP_ERROR.getCode(), ErrorCode.GROUP_ERROR.getMessage());
        return Result.success(save);
    }

    @PostMapping("/join")
    @ApiOperation(value = "加入群聊", notes = "加入群聊接口")
    public Result<Integer> joninGroup(@RequestBody GroupJoinDTO groupJoinDTO) {
        QueryWrapper<GroupChat> wrapper = new QueryWrapper<>();
        wrapper.eq("id", groupJoinDTO.getGroupId());
        if(groupChatService.getOne(wrapper)==null)
            return Result.error(ErrorCode.GROUP_ERROR.getCode(), ErrorCode.GROUP_ERROR.getMessage());
        int save = groupChatService.saveuser(groupJoinDTO);
        if(save==0)
            return Result.error(ErrorCode.GROUP_ERROR.getCode(), ErrorCode.GROUP_ERROR.getMessage());
        return Result.success(save);
    }
}
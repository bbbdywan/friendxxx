package com.xzh.friendxxx.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.exception.ErrorCode;
import com.xzh.friendxxx.model.dto.GroupCreatDTO;
import com.xzh.friendxxx.model.dto.GroupJoinDTO;
import com.xzh.friendxxx.model.entity.GroupChat;
import com.xzh.friendxxx.model.vo.GroupListVO;
import com.xzh.friendxxx.service.GroupChatService;
import com.xzh.friendxxx.service.GroupMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/group")
@Tag(name = "群聊管理模块", description = "提供群聊相关的接口")
public class GroupController {


    @Autowired
    private GroupChatService groupChatService;

    @Autowired
    private GroupMemberService groupMemberService;


    @PostMapping("/create")
    @Operation(summary = "创建群聊", description = "创建群聊接口")
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
    @Operation(summary = "加入群聊", description = "加入群聊接口")
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

    @GetMapping("/grouplist")
    @Operation(summary = "展示群聊列表", description = "展示群聊列表接口")
    public Result<List<GroupListVO>> getGroupList() {
        List<GroupListVO>  grouplist=  groupChatService.getlist();
        return Result.success(grouplist);
    }
}
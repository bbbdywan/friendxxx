package com.xzh.friendxxx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.exception.ErrorCode;
import com.xzh.friendxxx.mapper.GroupMemberMapper;
import com.xzh.friendxxx.model.dto.GroupCreatDTO;
import com.xzh.friendxxx.model.dto.GroupJoinDTO;
import com.xzh.friendxxx.model.entity.GroupChat;
import com.xzh.friendxxx.model.entity.GroupMember;
import com.xzh.friendxxx.model.vo.GroupListVO;
import com.xzh.friendxxx.service.GroupChatService;
import com.xzh.friendxxx.mapper.GroupChatMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
* @author bb
* @description 针对表【group_chat(群聊)】的数据库操作Service实现
* @createDate 2025-07-24 10:40:27
*/
@Service
public class GroupChatServiceImpl extends ServiceImpl<GroupChatMapper, GroupChat>
    implements GroupChatService{

    @Autowired
    private GroupMemberMapper groupMemberMapper;

    @Autowired
    private GroupChatMapper groupChatMapper;

    @Override
    @Transactional
    public int save(GroupCreatDTO groupCreatDTO) {
        GroupChat groupChat = new GroupChat();
        groupChat.setGroupName(groupCreatDTO.getGroup_name());
        groupChat.setAvatarUrl(groupCreatDTO.getAvatar_url());
        groupChat.setCreatorId(groupCreatDTO.getCreator_id());
        groupChat.setMemberCount(1);
        groupChat.setIntroduction(groupCreatDTO.getIntroduction());
        groupChat.setCreateTime(new Date());
        groupChat.setUpdateTime(new Date());
        groupChat.setIsDelete(0);
        if(this.save(groupChat)) {
            GroupMember groupMember = new GroupMember();
            groupMember.setGroupId(groupChat.getId());
            groupMember.setUserId(groupCreatDTO.getCreator_id());
            groupMember.setRole("owner");
            groupMember.setJoinTime(new Date());
            groupMember.setIsMuted(0);
            groupMember.setIsDeleted(0);
            if (groupMemberMapper.insert(groupMember) != 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建群主成员关系失败");
            }
            return 1;
        }
        return 0;

    }

    @Override
    @Transactional
    public int saveuser(GroupJoinDTO groupJoinDTO) {
        LambdaQueryWrapper<GroupMember> memberQuery = new LambdaQueryWrapper<>();
        memberQuery.eq(GroupMember::getGroupId, groupJoinDTO.getGroupId())
                .eq(GroupMember::getUserId, groupJoinDTO.getUserId());
        List<GroupMember> existingMembers = groupMemberMapper.selectList(memberQuery);
        GroupMember existing = existingMembers.stream()
                .filter(member -> Integer.valueOf(0).equals(member.getIsDeleted()))
                .findFirst()
                .orElse(existingMembers.isEmpty() ? null : existingMembers.get(0));
        if (existing != null && Integer.valueOf(0).equals(existing.getIsDeleted())) {
            return 1;
        }

        if (existing != null) {
            existing.setIsDeleted(0);
            existing.setRole("member");
            existing.setJoinTime(new Date());
            existing.setIsMuted(0);
            if (groupMemberMapper.updateById(existing) != 1) {
                return 0;
            }
        } else {
            GroupMember groupMember = new GroupMember();
            groupMember.setGroupId(groupJoinDTO.getGroupId());
            groupMember.setUserId(groupJoinDTO.getUserId());
            groupMember.setRole("member");
            groupMember.setJoinTime(new Date());
            groupMember.setIsMuted(0);
            groupMember.setIsDeleted(0);
            if (groupMemberMapper.insert(groupMember) != 1) {
                return 0;
            }
        }

        boolean countUpdated = this.lambdaUpdate()
                .setSql("member_count = COALESCE(member_count, 0) + 1")
                .eq(GroupChat::getId, groupJoinDTO.getGroupId())
                .update();
        if (!countUpdated) {
            throw new BusinessException(ErrorCode.GROUP_ERROR, "更新群成员数失败");
        }
        return 1;
    }

    @Override
    public List<GroupListVO> getlist() {
        QueryWrapper<GroupChat> query = new QueryWrapper<>();
        List<GroupChat> list = groupChatMapper.selectList(query);
        List<GroupListVO> groupListVO = new ArrayList<>();
        for (GroupChat groupChat : list) {
            GroupListVO build = GroupListVO.builder().group_id(groupChat.getId()).group_name(groupChat.getGroupName()).avatar_url(groupChat.getAvatarUrl()).introduction(groupChat.getIntroduction()).isMember(false).build();
            groupListVO.add(build);
        }
        long userid= BaseContext.getCurrentId();
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getUserId, userid)
                .eq(GroupMember::getIsDeleted, 0)
                .select(GroupMember::getGroupId);
        List<GroupMember> groupMembers = groupMemberMapper.selectList(wrapper);
        Set<Long> groupIds = new HashSet<>();

        if (groupMembers != null && !groupMembers.isEmpty()) {
            groupIds = groupMembers.stream()
                    .map(GroupMember::getGroupId)
                    .collect(Collectors.toSet());
        }

        for (GroupListVO vo : groupListVO) {
            if(groupIds.contains(vo.getGroup_id())){
                vo.setMember(true);
            }
        }
        return groupListVO;
    }


}





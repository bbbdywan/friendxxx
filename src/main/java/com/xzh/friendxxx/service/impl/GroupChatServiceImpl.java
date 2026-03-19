package com.xzh.friendxxx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzh.friendxxx.common.context.BaseContext;
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
            QueryWrapper<GroupChat> wrapper = new QueryWrapper<>();
            wrapper.eq("group_name", groupCreatDTO.getGroup_name());
            GroupChat group = groupChatMapper.selectOne(wrapper);
            groupMember.setGroupId(group.getId());
            groupMember.setUserId(groupCreatDTO.getCreator_id());
            groupMember.setRole("owner");
            groupMember.setJoinTime(new Date());
            groupMember.setIsMuted(0);
            groupMember.setIsDeleted(0);
            groupMemberMapper.insert(groupMember);
            return 1;
        }
        return 0;

    }

    @Override
    public int saveuser(GroupJoinDTO groupJoinDTO) {
        GroupMember groupMember = new GroupMember();
        groupMember.setGroupId(groupJoinDTO.getGroupId());
        groupMember.setUserId(groupJoinDTO.getUserId());
        groupMember.setRole("member");
        groupMember.setJoinTime(new Date());
        groupMember.setIsMuted(0);
        groupMember.setIsDeleted(0);
        if(groupMemberMapper.insert(groupMember)==1)
            return 1;
        return 0;
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





package com.xzh.friendxxx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzh.friendxxx.mapper.GroupMemberMapper;
import com.xzh.friendxxx.model.dto.GroupCreatDTO;
import com.xzh.friendxxx.model.dto.GroupJoinDTO;
import com.xzh.friendxxx.model.entity.GroupChat;
import com.xzh.friendxxx.model.entity.GroupMember;
import com.xzh.friendxxx.service.GroupChatService;
import com.xzh.friendxxx.mapper.GroupChatMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

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
        groupChat.setGroup_name(groupCreatDTO.getGroup_name());
        groupChat.setAvatar_url(groupCreatDTO.getAvatar_url());
        groupChat.setCreator_id(groupCreatDTO.getCreator_id());
        groupChat.setMember_count(1);
        groupChat.setIntroduction(groupCreatDTO.getIntroduction());
        groupChat.setCreate_time(new Date());
        groupChat.setUpdate_time(new Date());
        groupChat.setIs_delete(0);
        if(this.save(groupChat)) {
            GroupMember groupMember = new GroupMember();
            QueryWrapper<GroupChat> wrapper = new QueryWrapper<>();
            wrapper.eq("group_name", groupCreatDTO.getGroup_name());
            GroupChat group = groupChatMapper.selectOne(wrapper);
            groupMember.setGroup_id(group.getId());
            groupMember.setUser_id(groupCreatDTO.getCreator_id());
            groupMember.setRole("owner");
            groupMember.setJoin_time(new Date());
            groupMember.setIs_muted(0);
            groupMember.setIs_deleted(0);
            groupMemberMapper.insert(groupMember);
            return 1;
        }
        return 0;

    }

    @Override
    public int saveuser(GroupJoinDTO groupJoinDTO) {
        GroupMember groupMember = new GroupMember();
        groupMember.setGroup_id(groupJoinDTO.getGroupId());
        groupMember.setUser_id(groupJoinDTO.getUserId());
        groupMember.setRole("member");
        groupMember.setJoin_time(new Date());
        groupMember.setIs_muted(0);
        groupMember.setIs_deleted(0);
        if(groupMemberMapper.insert(groupMember)==1)
            return 1;
        return 0;
    }
}





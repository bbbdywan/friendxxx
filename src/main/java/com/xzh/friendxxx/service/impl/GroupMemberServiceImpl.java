package com.xzh.friendxxx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzh.friendxxx.model.entity.GroupMember;
import com.xzh.friendxxx.service.GroupMemberService;
import com.xzh.friendxxx.mapper.GroupMemberMapper;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author bb
* @description 针对表【group_member(群成员关系表)】的数据库操作Service实现
* @createDate 2025-07-24 11:05:20
*/
@Service
public class GroupMemberServiceImpl extends ServiceImpl<GroupMemberMapper, GroupMember>
    implements GroupMemberService {

    @Override
    public List<Long> getGroupMemberIds(Long groupId) {
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getGroupId, groupId)
               .eq(GroupMember::getIsDeleted, 0)
               .select(GroupMember::getUserId);
        
        return this.list(wrapper).stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toList());
    }

}

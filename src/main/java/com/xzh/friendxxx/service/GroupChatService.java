package com.xzh.friendxxx.service;

import com.xzh.friendxxx.model.dto.GroupCreatDTO;
import com.xzh.friendxxx.model.dto.GroupJoinDTO;
import com.xzh.friendxxx.model.entity.GroupChat;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author bb
* @description 针对表【group_chat(群聊)】的数据库操作Service
* @createDate 2025-07-24 10:40:28
*/
public interface GroupChatService extends IService<GroupChat> {

    int save(GroupCreatDTO groupCreatDTO);

    int saveuser(GroupJoinDTO groupJoinDTO);
}

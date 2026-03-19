package com.xzh.friendxxx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzh.friendxxx.model.entity.UserPrompt;
import com.xzh.friendxxx.service.UserPromptService;
import com.xzh.friendxxx.mapper.UserPromptMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @description 针对表【user_prompt】的数据库操作Service实现
 */
@Service
public class UserPromptServiceImpl extends ServiceImpl<UserPromptMapper, UserPrompt>
        implements UserPromptService {

    @Autowired
    private UserPromptMapper userPromptMapper;

    @Override
    public UserPrompt getActivePrompt(Long userId) {
        return userPromptMapper.getActivePrompt(userId);
    }

    @Override
    public List<UserPrompt> listByUserId(Long userId) {
        return userPromptMapper.listByUserId(userId);
    }

    @Override
    public boolean savePrompt(UserPrompt prompt) {
        if (prompt.getId() != null) {
            return updateById(prompt);
        }
        return save(prompt);
    }

    @Override
    public boolean deletePrompt(Long id) {
        return removeById(id);
    }

    @Override
    @Transactional
    public boolean setActive(Long userId, Long promptId) {
        // 先将该用户所有提示词设为非激活
        userPromptMapper.deactivateAll(userId);
        // 再将指定提示词设为激活
        userPromptMapper.activateById(promptId);
        return true;
    }
}

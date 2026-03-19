package com.xzh.friendxxx.service;

import com.xzh.friendxxx.model.entity.UserPrompt;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @description 针对表【user_prompt】的数据库操作Service
 */
public interface UserPromptService extends IService<UserPrompt> {

    UserPrompt getActivePrompt(Long userId);

    List<UserPrompt> listByUserId(Long userId);

    boolean savePrompt(UserPrompt prompt);

    boolean deletePrompt(Long id);

    boolean setActive(Long userId, Long promptId);
}

package com.xzh.friendxxx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzh.friendxxx.mapper.UserNotificationMapper;
import com.xzh.friendxxx.model.entity.UserNotification;
import com.xzh.friendxxx.service.UserNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class UserNotificationServiceImpl extends ServiceImpl<UserNotificationMapper, UserNotification>
        implements UserNotificationService {

    @Autowired
    private UserNotificationMapper userNotificationMapper;

    @Override
    public void saveNotification(UserNotification notification) {
        notification.setIsRead(0);
        notification.setCreateTime(new Date());
        userNotificationMapper.insert(notification);
    }

    @Override
    public List<UserNotification> listByUserId(Long userId, Integer page, Integer size) {
        int offset = (page - 1) * size;
        return userNotificationMapper.selectByUserId(userId, offset, size);
    }

    @Override
    public Long countUnread(Long userId) {
        return userNotificationMapper.countUnread(userId);
    }

    @Override
    public void readAll(Long userId) {
        userNotificationMapper.readAll(userId);
    }

    @Override
    public void readOne(Long id, Long userId) {
        userNotificationMapper.readOne(id, userId);
    }
}

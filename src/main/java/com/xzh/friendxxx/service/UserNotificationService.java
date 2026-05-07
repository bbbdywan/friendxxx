package com.xzh.friendxxx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xzh.friendxxx.model.entity.UserNotification;

import java.util.List;

public interface UserNotificationService extends IService<UserNotification> {

    /** 保存一条通知 */
    void saveNotification(UserNotification notification);

    /** 分页查询用户通知列表 */
    List<UserNotification> listByUserId(Long userId, Integer page, Integer size);

    /** 查询未读数量 */
    Long countUnread(Long userId);

    /** 标记全部已读 */
    void readAll(Long userId);

    /** 标记单条已读 */
    void readOne(Long id, Long userId);
}

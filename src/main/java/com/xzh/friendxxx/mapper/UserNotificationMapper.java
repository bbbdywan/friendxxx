package com.xzh.friendxxx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzh.friendxxx.model.entity.UserNotification;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface UserNotificationMapper extends BaseMapper<UserNotification> {

    @Select("SELECT * FROM user_notification WHERE to_user_id = #{userId} AND is_deleted = 0 ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<UserNotification> selectByUserId(@Param("userId") Long userId,
                                          @Param("offset") Integer offset,
                                          @Param("size") Integer size);

    @Select("SELECT COUNT(*) FROM user_notification WHERE to_user_id = #{userId} AND is_read = 0 AND is_deleted = 0")
    Long countUnread(@Param("userId") Long userId);

    @Update("UPDATE user_notification SET is_read = 1 WHERE to_user_id = #{userId} AND is_deleted = 0")
    void readAll(@Param("userId") Long userId);

    @Update("UPDATE user_notification SET is_read = 1 WHERE id = #{id} AND to_user_id = #{userId}")
    void readOne(@Param("id") Long id, @Param("userId") Long userId);
}

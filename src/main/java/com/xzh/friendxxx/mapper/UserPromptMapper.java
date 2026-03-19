package com.xzh.friendxxx.mapper;

import com.xzh.friendxxx.model.entity.UserPrompt;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @description 针对表【user_prompt】的数据库操作Mapper
 */
public interface UserPromptMapper extends BaseMapper<UserPrompt> {

    @Select("SELECT * FROM user_prompt WHERE user_id = #{userId} AND is_active = 1 AND is_deleted = 0 LIMIT 1")
    UserPrompt getActivePrompt(@Param("userId") Long userId);

    @Select("SELECT * FROM user_prompt WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY create_time DESC")
    List<UserPrompt> listByUserId(@Param("userId") Long userId);

    @Update("UPDATE user_prompt SET is_active = 0 WHERE user_id = #{userId} AND is_deleted = 0")
    void deactivateAll(@Param("userId") Long userId);

    @Update("UPDATE user_prompt SET is_active = 1 WHERE id = #{id} AND is_deleted = 0")
    void activateById(@Param("id") Long id);
}

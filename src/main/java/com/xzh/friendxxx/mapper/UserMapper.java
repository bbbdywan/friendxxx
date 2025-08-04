package com.xzh.friendxxx.mapper;

import com.xzh.friendxxx.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
* @author bb
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2025-07-17 17:48:09
* @Entity com.xzh.friendxxx.model.entity.User
*/
public interface UserMapper extends BaseMapper<User> {

    @Select("select id from user where userAccount = #{userAccount}")
    long getid(@Param("userAccount") String userAccount);

    @Update("update user set isDelete = 1 where userAccount = #{account}")
    void deleteByAccount(@Param("account") String account);

    @Select("select username,id,userAccount,gender,phone,email,avatarUrl,tags,background,signature,age,height,profession,education,zodiac,hometown,relationship_status from user where isDelete = 0 ")
    List<User> getuser();
}





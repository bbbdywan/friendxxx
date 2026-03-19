package com.xzh.friendxxx.mapper;

import com.xzh.friendxxx.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
* @author bb
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2025-07-17 17:48:09
* @Entity com.xzh.friendxxx.model.entity.User
*/
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("select id from user where userAccount = #{userAccount}")
    long getid(@Param("userAccount") String userAccount);

    @Update("update user set isDelete = 1 where userAccount = #{account}")
    void deleteByAccount(@Param("account") String account);

    @Select("select username,id,userAccount,gender,phone,email,avatarUrl,tags,background,signature,age,height,profession,education,zodiac,hometown,relationship_status from user where isDelete = 0 ")
    List<User> getuser();

    @Select("select username,id,userAccount,gender,phone,email,avatarUrl,tags,background,signature,age,height,profession,education,zodiac,hometown,relationship_status from user where isDelete = 0 limit #{offset},#{limit}")
    List<User> select(@Param("offset") Integer offset,@Param("limit") Integer limit);

    @Select("<script>" +
            "SELECT * FROM user " +
            "WHERE 1=1 " +
            "<if test='username != null and username != &quot;&quot;'> " +
            "  AND username LIKE CONCAT('%', #{username}, '%') " +
            "</if>" +
            "<if test='createTimeBegin != null'> " +
            "  AND createTime &gt;= #{createTimeBegin} " +
            "</if>" +
            "<if test='createTimeEnd != null'> " +
            "  AND createTime &lt;= #{createTimeEnd} " +
            "</if>" +
            "ORDER BY createTime DESC " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<User> selectUserByCondition(@Param("username") String username,
                                     @Param("createTimeBegin") LocalDateTime createTimeBegin,
                                     @Param("createTimeEnd") LocalDateTime createTimeEnd,
                                     @Param("offset") long offset,
                                     @Param("pageSize") long pageSize);

    @Select("<script>" +
            "SELECT COUNT(*) FROM user " +
            "WHERE 1=1 " +
            "<if test='username != null and username != &quot;&quot;'> " +
            "  AND username LIKE CONCAT('%', #{username}, '%') " +
            "</if>" +
            "<if test='createTimeBegin != null'> " +
            "  AND createTime &gt;= #{createTimeBegin} " +
            "</if>" +
            "<if test='createTimeEnd != null'> " +
            "  AND createTime &lt;= #{createTimeEnd} " +
            "</if>" +
            "</script>")
    long countUserByCondition(@Param("username") String username,
                              @Param("createTimeBegin") LocalDateTime createTimeBegin,
                              @Param("createTimeEnd") LocalDateTime createTimeEnd);
    // @Select("select * from user where isDelete = 0 ")

}





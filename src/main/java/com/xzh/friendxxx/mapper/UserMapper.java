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
            "<choose>" +
            "<when test='username != null and username != &quot;&quot; and username.length() >= 2'>" +
            "  AND MATCH(username) AGAINST(#{username} IN BOOLEAN MODE) " +
            "</when>" +
            "<when test='username != null and username != &quot;&quot;'>" +
            "  AND username LIKE CONCAT('%', #{username}, '%') " +
            "</when>" +
            "</choose>" +
            "<if test='createTimeBegin != null'> " +
            "  AND createTime &gt;= #{createTimeBegin} " +
            "</if>" +
            "<if test='createTimeEnd != null'> " +
            "  AND createTime &lt;= #{createTimeEnd} " +
            "</if>" +
            "<choose>" +
            "<when test='username != null and username != &quot;&quot; and username.length() >= 2'>" +
            "ORDER BY MATCH(username) AGAINST(#{username} IN BOOLEAN MODE) DESC " +
            "</when>" +
            "<otherwise>" +
            "ORDER BY createTime DESC " +
            "</otherwise>" +
            "</choose>" +
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
            "<choose>" +
            "<when test='username != null and username != &quot;&quot; and username.length() >= 2'>" +
            "  AND MATCH(username) AGAINST(#{username} IN BOOLEAN MODE) " +
            "</when>" +
            "<when test='username != null and username != &quot;&quot;'>" +
            "  AND username LIKE CONCAT('%', #{username}, '%') " +
            "</when>" +
            "</choose>" +
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
    @Select("<script>" +
            "SELECT id, username, avatarUrl, tags, age, gender, zodiac, height, " +
            "profession, education, hometown, signature " +
            "FROM user WHERE isDelete = 0 AND id != #{userId} " +
            "<if test='gender != null'> AND gender = #{gender} </if>" +
            "<if test='ageMin != null'> AND age &gt;= #{ageMin} </if>" +
            "<if test='ageMax != null'> AND age &lt;= #{ageMax} </if>" +
            "<if test='hometown != null and hometown != \"\"'> AND hometown = #{hometown} </if>" +
            "LIMIT #{limit}" +
            "</script>")
    List<User> selectCandidates(@Param("userId") Long userId,
                                @Param("gender") Integer gender,
                                @Param("ageMin") Integer ageMin,
                                @Param("ageMax") Integer ageMax,
                                @Param("hometown") String hometown,
                                @Param("limit") int limit);

}





package com.xzh.friendxxx.mapper;

import com.xzh.friendxxx.model.entity.SocialPost;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
* @author bb
* @description 针对表【social_post(用户动态表)】的数据库操作Mapper
* @createDate 2025-07-25 22:31:35
* @Entity com.xzh.friendxxx.model.entity.SocialPost
*/
public interface SocialPostMapper extends BaseMapper<SocialPost> {

    // 使用XML中定义的ResultMap来确保TypeHandler生效
    @Select("SELECT id,user_id,nickname,content,image_list,like_count,is_deleted,create_time,update_time,mood,avatar_url FROM social_post where is_deleted=0")
    @ResultMap("BaseResultMap")
    List<SocialPost> selectAllWithTypeHandler();

    // 根据TTL删除过期的动态 - 使用XML实现
    int removebyttl(String deleteTtl);

    @Select("select id,user_id,nickname,content,image_list,create_time,update_time,mood,avatar_url from social_post where user_id = #{userId} and is_deleted=0")
    @ResultMap("BaseResultMap")
    List<SocialPost> getlist(Long userId);
}





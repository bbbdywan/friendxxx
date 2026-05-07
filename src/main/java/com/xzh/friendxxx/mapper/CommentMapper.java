package com.xzh.friendxxx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzh.friendxxx.model.entity.Comment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CommentMapper extends BaseMapper<Comment> {

    @Select("SELECT id, post_id, user_id, nickname, avatar_url, content, create_time " +
            "FROM comment WHERE post_id = #{postId} AND is_deleted = 0 ORDER BY create_time ASC")
    List<Comment> selectByPostId(@Param("postId") Long postId);

    @Select("SELECT COUNT(*) FROM comment WHERE post_id = #{postId} AND is_deleted = 0")
    Long countByPostId(@Param("postId") Long postId);
}

package com.xzh.friendxxx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzh.friendxxx.model.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {

    /**
     * 用户会话列表（按最后消息时间倒序）。
     */
    @Select("SELECT * FROM ai_conversation " +
            "WHERE user_id = #{userId} AND is_deleted = 0 " +
            "ORDER BY COALESCE(last_message_at, create_time) DESC " +
            "LIMIT #{offset}, #{limit}")
    List<AiConversation> listByUser(@Param("userId") Long userId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /**
     * 归属校验：必须同时满足 user_id 与未删除。
     */
    @Select("SELECT * FROM ai_conversation " +
            "WHERE id = #{id} AND user_id = #{userId} AND is_deleted = 0")
    AiConversation getOwned(@Param("id") String id, @Param("userId") Long userId);

    /**
     * 乐观锁更新摘要，避免并发覆盖。
     */
    @Update("UPDATE ai_conversation " +
            "SET conversation_summary = #{summary}, summary_version = summary_version + 1 " +
            "WHERE id = #{id} AND summary_version = #{expectVersion}")
    int updateSummaryIfVersion(@Param("id") String id,
                               @Param("summary") String summary,
                               @Param("expectVersion") int expectVersion);

    @Update("UPDATE ai_conversation SET last_message_at = #{time} WHERE id = #{id}")
    int touchLastMessage(@Param("id") String id, @Param("time") Date time);
}

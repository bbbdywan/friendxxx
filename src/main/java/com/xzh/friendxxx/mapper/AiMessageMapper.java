package com.xzh.friendxxx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzh.friendxxx.model.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {

    /**
     * 幂等校验：同一用户同一 clientMessageId。
     */
    @Select("SELECT * FROM ai_message " +
            "WHERE user_id = #{userId} AND client_message_id = #{clientMessageId} LIMIT 1")
    AiMessage findByClientMessageId(@Param("userId") Long userId,
                                    @Param("clientMessageId") String clientMessageId);

    /**
     * 通过 reply_to_message_id 精确找到对应 Assistant 回复。
     */
    @Select("SELECT * FROM ai_message " +
            "WHERE conversation_id = #{conversationId} " +
            "AND reply_to_message_id = #{replyToMessageId} " +
            "AND role = 'assistant' LIMIT 1")
    AiMessage findByReplyTo(@Param("conversationId") String conversationId,
                            @Param("replyToMessageId") String replyToMessageId);

    /**
     * 幂等重放：某条 User 消息对应的全部 Assistant 消息，按 message_index 升序。
     */
    @Select("SELECT * FROM ai_message " +
            "WHERE conversation_id = #{conversationId} " +
            "AND reply_to_message_id = #{replyToMessageId} " +
            "AND role = 'assistant' " +
            "ORDER BY message_index ASC, create_time ASC, id ASC")
    List<AiMessage> listByReplyTo(@Param("conversationId") String conversationId,
                                  @Param("replyToMessageId") String replyToMessageId);

    /**
     * 通过 turnId 查询一轮内全部 Assistant 消息（幂等重放/后台任务拼接用）。
     */
    @Select("SELECT * FROM ai_message " +
            "WHERE conversation_id = #{conversationId} " +
            "AND turn_id = #{turnId} " +
            "AND role = 'assistant' " +
            "ORDER BY message_index ASC, create_time ASC, id ASC")
    List<AiMessage> listByTurn(@Param("conversationId") String conversationId,
                               @Param("turnId") String turnId);

    /**
     * 游标分页查询会话消息，按 (create_time, id) 稳定排序，旧→新。
     * cursor 为 null 时返回第一页。
     */
    @Select("<script>" +
            "SELECT * FROM ai_message WHERE conversation_id = #{conversationId} " +
            "<if test='cursorTime != null'>" +
            "  AND (create_time &gt; #{cursorTime} " +
            "       OR (create_time = #{cursorTime} AND id &gt; #{cursorId}))" +
            "</if>" +
            " ORDER BY create_time ASC, id ASC LIMIT #{limit}" +
            "</script>")
    List<AiMessage> listByCursor(@Param("conversationId") String conversationId,
                                 @Param("cursorTime") Date cursorTime,
                                 @Param("cursorId") String cursorId,
                                 @Param("limit") int limit);

    /**
     * 加载最近 N 条消息（旧→新），用于组装完整对话轮次。
     */
    @Select("SELECT * FROM (SELECT * FROM ai_message " +
            "WHERE conversation_id = #{conversationId} ORDER BY create_time DESC, id DESC LIMIT #{limit}) t " +
            "ORDER BY create_time ASC, id ASC")
    List<AiMessage> recentMessages(@Param("conversationId") String conversationId,
                                   @Param("limit") int limit);
}

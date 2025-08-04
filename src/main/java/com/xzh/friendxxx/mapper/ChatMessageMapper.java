package com.xzh.friendxxx.mapper;

import com.xzh.friendxxx.model.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzh.friendxxx.model.vo.SenderVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author bb
* @description 针对表【chat_message】的数据库操作Mapper
* @createDate 2025-07-21 22:25:42
* @Entity com.xzh.friendxxx.model.entity.ChatMessage
*/
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {


    @Select("SELECT DISTINCT sender_id,receiver_id,content,create_time from chat_message where id in (Select MAX(id) from chat_message where receiver_id = #{userId} or sender_id=#{userId} GROUP BY conversation_id )")
    List<SenderVO> getuserID(@Param("userId") long userId);

    @Delete("delete  from chat_message where conversation_id = #{conversationId}")
    void deletemsg(@Param("conversationId") String conversationId);
}





package com.xzh.friendxxx.mapper;

import com.xzh.friendxxx.model.entity.AiChatMemory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
* @author bb
* @description 针对表【ai_chat_memory】的数据库操作Mapper
* @createDate 2025-07-28 13:55:58
* @Entity com.xzh.friendxxx.model.entity.AiChatMemory
*/
public interface AiChatMemoryMapper extends BaseMapper<AiChatMemory> {

   @Update("UPDATE ai_chat_memory SET is_deleted = 1 WHERE conversation_id = #{conversationId}")
    void deleteMSGById(@Param("conversationId") String conversationId);

    @Select("SELECT * FROM ai_chat_memory WHERE conversation_id = #{conversationId} and is_deleted = 0")
    List<AiChatMemory> getMessageList(String conversationId);
}





package com.xzh.friendxxx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzh.friendxxx.model.entity.AiMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiMemoryMapper extends BaseMapper<AiMemory> {

    /**
     * 检索某用户与某角色下、状态 active、未过期的记忆。
     */
    @Select("SELECT * FROM ai_memory " +
            "WHERE user_id = #{userId} AND character_id = #{characterId} " +
            "AND status = 'active' " +
            "AND (expires_at IS NULL OR expires_at > NOW()) " +
            "ORDER BY importance DESC, update_time DESC")
    List<AiMemory> listActive(@Param("userId") Long userId,
                              @Param("characterId") Long characterId);

    /**
     * 按记忆 key 查询某用户某角色的记录（含非 active）。
     */
    @Select("SELECT * FROM ai_memory " +
            "WHERE user_id = #{userId} AND character_id = #{characterId} AND memory_key = #{memoryKey}")
    List<AiMemory> findByKey(@Param("userId") Long userId,
                            @Param("characterId") Long characterId,
                            @Param("memoryKey") String memoryKey);

    @Update("UPDATE ai_memory SET status = 'superseded' " +
            "WHERE user_id = #{userId} AND character_id = #{characterId} AND memory_key = #{memoryKey} " +
            "AND status = 'active'")
    int supersedeByKey(@Param("userId") Long userId,
                       @Param("characterId") Long characterId,
                       @Param("memoryKey") String memoryKey);
}

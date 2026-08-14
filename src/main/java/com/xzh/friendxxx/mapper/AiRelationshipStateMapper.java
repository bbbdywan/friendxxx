package com.xzh.friendxxx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzh.friendxxx.model.entity.AiRelationshipState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiRelationshipStateMapper extends BaseMapper<AiRelationshipState> {

    /**
     * 幂等 upsert：存在则更新数值，不存在则插入。
     */
    @Insert("INSERT INTO ai_relationship_state " +
            "(user_id, character_id, familiarity, trust_level, interaction_count, current_stage, " +
            " preferred_address, recent_mood, recent_topics, relationship_summary, version, update_time) " +
            "VALUES (#{userId}, #{characterId}, #{familiarity}, #{trustLevel}, #{interactionCount}, #{currentStage}, " +
            " #{preferredAddress}, #{recentMood}, #{recentTopics}, #{relationshipSummary}, #{version}, NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "familiarity = VALUES(familiarity), trust_level = VALUES(trust_level), " +
            "interaction_count = VALUES(interaction_count), current_stage = VALUES(current_stage), " +
            "preferred_address = VALUES(preferred_address), recent_mood = VALUES(recent_mood), " +
            "recent_topics = VALUES(recent_topics), relationship_summary = VALUES(relationship_summary), " +
            "version = version + 1, update_time = NOW()")
    int upsert(@Param("userId") Long userId,
               @Param("characterId") Long characterId,
               @Param("familiarity") java.math.BigDecimal familiarity,
               @Param("trustLevel") java.math.BigDecimal trustLevel,
               @Param("interactionCount") int interactionCount,
               @Param("currentStage") String currentStage,
               @Param("preferredAddress") String preferredAddress,
               @Param("recentMood") String recentMood,
               @Param("recentTopics") String recentTopics,
               @Param("relationshipSummary") String relationshipSummary,
               @Param("version") int version);
}

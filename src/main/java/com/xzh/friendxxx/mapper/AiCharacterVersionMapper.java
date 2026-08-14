package com.xzh.friendxxx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzh.friendxxx.model.entity.AiCharacterVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiCharacterVersionMapper extends BaseMapper<AiCharacterVersion> {

    @Select("SELECT * FROM ai_character_version WHERE character_id = #{characterId} " +
            "ORDER BY version_no DESC LIMIT 1")
    AiCharacterVersion findLatest(@Param("characterId") Long characterId);

    @Select("SELECT * FROM ai_character_version WHERE character_id = #{characterId} " +
            "AND status = 'published' ORDER BY version_no DESC LIMIT 1")
    AiCharacterVersion findLatestPublished(@Param("characterId") Long characterId);
}

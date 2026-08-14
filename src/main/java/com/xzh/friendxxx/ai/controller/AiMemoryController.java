package com.xzh.friendxxx.ai.controller;

import com.xzh.friendxxx.ai.model.dto.UpdateMemoryRequest;
import com.xzh.friendxxx.ai.model.vo.AiMemoryVO;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.mapper.AiMemoryMapper;
import com.xzh.friendxxx.model.entity.AiMemory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiMemoryController {

    private final AiMemoryMapper aiMemoryMapper;

    @GetMapping("/characters/{characterId}/memories")
    @Operation(summary = "查看某角色记住的用户长期记忆")
    @SecurityRequirement(name = "sessionAuth")
    public Result<List<AiMemoryVO>> list(@PathVariable Long characterId) {
        Long userId = BaseContext.getCurrentId();
        List<AiMemory> memories = aiMemoryMapper.listActive(userId, characterId);
        List<AiMemoryVO> result = new ArrayList<>();
        for (AiMemory m : memories) {
            result.add(toVO(m));
        }
        return Result.success(result);
    }

    @PatchMapping("/memories/{memoryId}")
    @Operation(summary = "更正一条记忆")
    @SecurityRequirement(name = "sessionAuth")
    public Result<AiMemoryVO> update(@PathVariable String memoryId,
                                     @RequestBody @Valid UpdateMemoryRequest request) {
        Long userId = BaseContext.getCurrentId();
        AiMemory memory = ownedMemory(memoryId, userId);
        memory.setContent(request.getContent());
        memory.setUpdateTime(new Date());
        aiMemoryMapper.updateById(memory);
        return Result.success(toVO(memory));
    }

    @DeleteMapping("/memories/{memoryId}")
    @Operation(summary = "删除一条记忆")
    @SecurityRequirement(name = "sessionAuth")
    public Result<String> delete(@PathVariable String memoryId) {
        Long userId = BaseContext.getCurrentId();
        AiMemory memory = ownedMemory(memoryId, userId);
        memory.setStatus("deleted");
        memory.setUpdateTime(new Date());
        aiMemoryMapper.updateById(memory);
        return Result.success("删除成功");
    }

    @DeleteMapping("/characters/{characterId}/memories")
    @Operation(summary = "清空与某角色相关的全部长期记忆")
    @SecurityRequirement(name = "sessionAuth")
    public Result<String> clearAll(@PathVariable Long characterId) {
        Long userId = BaseContext.getCurrentId();
        List<AiMemory> memories = aiMemoryMapper.selectList(new LambdaQueryWrapper<AiMemory>()
                .eq(AiMemory::getUserId, userId)
                .eq(AiMemory::getCharacterId, characterId)
                .eq(AiMemory::getStatus, "active"));
        for (AiMemory m : memories) {
            m.setStatus("deleted");
            m.setUpdateTime(new Date());
            aiMemoryMapper.updateById(m);
        }
        return Result.success("已清空");
    }

    private AiMemory ownedMemory(String memoryId, Long userId) {
        AiMemory memory = aiMemoryMapper.selectById(memoryId);
        if (memory == null || !userId.equals(memory.getUserId())) {
            throw new BusinessException(404, "记忆不存在或无权操作");
        }
        return memory;
    }

    private AiMemoryVO toVO(AiMemory m) {
        return AiMemoryVO.builder()
                .id(m.getId())
                .characterId(m.getCharacterId())
                .memoryType(m.getMemoryType())
                .memoryKey(m.getMemoryKey())
                .content(m.getContent())
                .normalizedValue(m.getNormalizedValue())
                .importance(m.getImportance())
                .confidence(m.getConfidence())
                .createTime(m.getCreateTime())
                .updateTime(m.getUpdateTime())
                .build();
    }
}

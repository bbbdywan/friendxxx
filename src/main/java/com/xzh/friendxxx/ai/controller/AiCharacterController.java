package com.xzh.friendxxx.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzh.friendxxx.ai.model.vo.CharacterVO;
import com.xzh.friendxxx.ai.service.AiCharacterVersionService;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.mapper.AiCharacterMapper;
import com.xzh.friendxxx.model.entity.AiCharacter;
import com.xzh.friendxxx.model.entity.AiCharacterVersion;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ai/characters")
@RequiredArgsConstructor
public class AiCharacterController {

    private final AiCharacterMapper aiCharacterMapper;
    private final AiCharacterVersionService characterVersionService;

    @GetMapping
    @Operation(summary = "可用 AI 角色列表")
    public Result<List<CharacterVO>> list() {
        List<AiCharacter> characters = aiCharacterMapper.selectList(
                new LambdaQueryWrapper<AiCharacter>()
                        .eq(AiCharacter::getEnabled, 1)
                        .orderByAsc(AiCharacter::getId));
        List<CharacterVO> result = new ArrayList<>();
        for (AiCharacter c : characters) {
            AiCharacterVersion active = characterVersionService.getActiveVersion(c.getId());
            if (active == null) {
                // 角色已启用但无已发布版本：不对外暴露
                continue;
            }
            result.add(CharacterVO.builder()
                    .id(c.getId())
                    .name(firstText(active.getName(), c.getName()))
                    .avatarUrl(firstText(active.getAvatarUrl(), c.getAvatarUrl()))
                    .build());
        }
        return Result.success(result);
    }

    private String firstText(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}

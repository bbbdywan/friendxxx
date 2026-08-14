package com.xzh.friendxxx.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzh.friendxxx.ai.model.PersonaSafetyBoundary;
import com.xzh.friendxxx.ai.model.admin.SaveCharacterDraftRequest;
import com.xzh.friendxxx.ai.model.vo.AdminCharacterDetailVO;
import com.xzh.friendxxx.ai.model.vo.AdminCharacterVO;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.mapper.AiCharacterAuditMapper;
import com.xzh.friendxxx.mapper.AiCharacterDraftMapper;
import com.xzh.friendxxx.mapper.AiCharacterMapper;
import com.xzh.friendxxx.mapper.AiCharacterVersionMapper;
import com.xzh.friendxxx.model.entity.*;
import com.xzh.friendxxx.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * AI 人设版本化服务：草稿保存（乐观锁）、发布（原子切换 active_version_id）、回滚、审计。
 *
 * <p>草稿与线上版本完全隔离；发布把草稿内容固化为新 version 并把 active_version_id 指向它；
 * 回滚基于历史 version 内容生成新 version 并发布（旧版本保留）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCharacterVersionService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";

    private final AiCharacterMapper characterMapper;
    private final AiCharacterVersionMapper versionMapper;
    private final AiCharacterDraftMapper draftMapper;
    private final AiCharacterAuditMapper auditMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 新建角色主记录 + 空草稿。
     */
    @Transactional
    public AdminCharacterVO create(Long operatorId, SaveCharacterDraftRequest request) {
        validatePrompt(request);
        AiCharacter character = new AiCharacter();
        character.setName(request.getName());
        character.setDescription(request.getDescription());
        character.setAvatarUrl(request.getAvatarUrl());
        // 兼容 ai_character 表 NOT NULL 字段：以草稿内容填充（实际读取走版本表）
        character.setIdentityPrompt(request.getIdentityPrompt());
        character.setPersonalityPrompt(request.getPersonalityPrompt());
        character.setSpeakingStylePrompt(request.getSpeakingStylePrompt());
        character.setInteractionRulesPrompt(request.getInteractionRulesPrompt());
        character.setBoundaryPrompt(request.getBoundaryPrompt());
        character.setExampleDialogues(toJson(request.getExampleDialogues()));
        // 新建默认停用，管理员确认后手动启用
        character.setEnabled(0);
        character.setCreateTime(new Date());
        character.setUpdateTime(new Date());
        characterMapper.insert(character);

        // 生成 version 1 作为线上基线（published），保证后续 publish/rollback 基于版本号正常
        AiCharacterVersion v1 = new AiCharacterVersion();
        v1.setCharacterId(character.getId());
        v1.setName(request.getName());
        v1.setDescription(request.getDescription());
        v1.setAvatarUrl(request.getAvatarUrl());
        v1.setIdentityPrompt(request.getIdentityPrompt());
        v1.setPersonalityPrompt(request.getPersonalityPrompt());
        v1.setSpeakingStylePrompt(request.getSpeakingStylePrompt());
        v1.setInteractionRulesPrompt(request.getInteractionRulesPrompt());
        v1.setBoundaryPrompt(request.getBoundaryPrompt());
        v1.setExampleDialogues(toJson(request.getExampleDialogues()));
        v1.setVersionNo(1);
        v1.setStatus(STATUS_PUBLISHED);
        v1.setPublishedAt(new Date());
        v1.setOperatorId(operatorId);
        v1.setChangeNote("初始版本");
        v1.setCreateTime(new Date());
        versionMapper.insert(v1);

        character.setActiveVersionId(v1.getId());
        characterMapper.updateById(character);

        saveDraftInternal(character.getId(), operatorId, request, 1);
        audit(character.getId(), "create", operatorId, 1, "新建角色");
        return toListVO(character);
    }

    /**
     * 保存草稿：baseVersionNo 乐观锁，冲突抛 409。
     */
    @Transactional
    public void saveDraft(Long characterId, Long operatorId, SaveCharacterDraftRequest request) {
        validatePrompt(request);
        AiCharacter character = requireCharacter(characterId);
        int activeVersionNo = currentVersionNo(characterId);

        if (request.getExpectedVersionNo() != null
                && !request.getExpectedVersionNo().equals(activeVersionNo)) {
            throw new BusinessException(409, "角色已被他人更新，请刷新后重试（当前版本 " + activeVersionNo + "）");
        }

        saveDraftInternal(characterId, operatorId, request, activeVersionNo);
        audit(characterId, "save_draft", operatorId, activeVersionNo, "保存草稿");
    }

    private void saveDraftInternal(Long characterId, Long operatorId,
                                   SaveCharacterDraftRequest request, int baseVersionNo) {
        AiCharacterDraft draft = draftMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiCharacterDraft>()
                .eq(AiCharacterDraft::getCharacterId, characterId));
        if (draft == null) {
            draft = new AiCharacterDraft();
            draft.setCharacterId(characterId);
        }
        draft.setName(request.getName());
        draft.setDescription(request.getDescription());
        draft.setAvatarUrl(request.getAvatarUrl());
        draft.setIdentityPrompt(request.getIdentityPrompt());
        draft.setPersonalityPrompt(request.getPersonalityPrompt());
        draft.setSpeakingStylePrompt(request.getSpeakingStylePrompt());
        draft.setInteractionRulesPrompt(request.getInteractionRulesPrompt());
        draft.setBoundaryPrompt(request.getBoundaryPrompt());
        draft.setExampleDialogues(toJson(request.getExampleDialogues()));
        draft.setBaseVersionNo(baseVersionNo);
        draft.setSavedBy(operatorId);
        draft.setUpdateTime(new Date());
        if (draft.getId() == null) {
            draftMapper.insert(draft);
        } else {
            draftMapper.updateById(draft);
        }
        characterMapper.updateById(AiCharacter.builder()
                .id(characterId)
                .draftId(draft.getId())
                .updateTime(new Date())
                .build());
    }

    /**
     * 发布：把草稿固化为新 version，原子切换 active_version_id。
     * 乐观锁：expectedVersionNo 必须等于当前线上版本号，否则 409。
     */
    @Transactional
    public AdminCharacterDetailVO.VersionBriefVO publish(Long characterId, Long operatorId,
                                                         Integer expectedVersionNo, String changeNote) {
        AiCharacter character = requireCharacter(characterId);
        AiCharacterDraft draft = draftMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiCharacterDraft>()
                .eq(AiCharacterDraft::getCharacterId, characterId));
        if (draft == null) {
            throw new BusinessException(400, "没有可发布的草稿，请先保存草稿");
        }
        // 乐观锁校验：草稿基于的版本号必须与当前线上一致
        int currentNo = currentVersionNo(characterId);
        if (expectedVersionNo != null && !expectedVersionNo.equals(currentNo)) {
            throw new BusinessException(409, "角色已被他人更新，请刷新后重试（当前版本 " + currentNo + "）");
        }

        int newVersionNo = currentNo + 1;
        AiCharacterVersion version = fromDraft(draft, newVersionNo, operatorId, changeNote);
        version.setStatus(STATUS_PUBLISHED);
        version.setPublishedAt(new Date());
        versionMapper.insert(version);

        character.setActiveVersionId(version.getId());
        character.setName(draft.getName());
        character.setDescription(draft.getDescription());
        character.setAvatarUrl(draft.getAvatarUrl());
        character.setUpdateTime(new Date());
        characterMapper.updateById(character);

        // 发布成功后同步草稿 baseVersionNo 到新版本，避免下次保存立即冲突
        draft.setBaseVersionNo(newVersionNo);
        draft.setUpdateTime(new Date());
        draftMapper.updateById(draft);

        audit(characterId, "publish", operatorId, newVersionNo,
                changeNote == null ? "发布" : changeNote);
        log.info("[ai-admin] 人设发布: characterId={}, versionNo={}, operatorId={}", characterId, newVersionNo, operatorId);
        return toVersionBrief(version);
    }

    /**
     * 回滚：以历史 version 内容生成新 version 并发布。
     */
    @Transactional
    public AdminCharacterDetailVO.VersionBriefVO rollback(Long characterId, Long operatorId,
                                                          Long fromVersionId, String changeNote) {
        AiCharacter character = requireCharacter(characterId);
        AiCharacterVersion from = versionMapper.selectById(fromVersionId);
        if (from == null || !from.getCharacterId().equals(characterId)) {
            throw new BusinessException(404, "版本不存在");
        }

        int newVersionNo = currentVersionNo(characterId) + 1;
        AiCharacterVersion version = new AiCharacterVersion();
        copyContent(from, version);
        version.setCharacterId(characterId);
        version.setVersionNo(newVersionNo);
        version.setStatus(STATUS_PUBLISHED);
        version.setPublishedAt(new Date());
        version.setOperatorId(operatorId);
        version.setChangeNote(changeNote == null ? ("回滚到版本 " + from.getVersionNo()) : changeNote);
        versionMapper.insert(version);

        character.setActiveVersionId(version.getId());
        character.setName(version.getName());
        character.setDescription(version.getDescription());
        character.setAvatarUrl(version.getAvatarUrl());
        character.setUpdateTime(new Date());
        characterMapper.updateById(character);

        audit(characterId, "rollback", operatorId, newVersionNo,
                "回滚到版本 " + from.getVersionNo());
        return toVersionBrief(version);
    }

    /**
     * 启用/停用角色。
     */
    @Transactional
    public void setEnabled(Long characterId, Long operatorId, boolean enabled) {
        AiCharacter character = requireCharacter(characterId);
        character.setEnabled(enabled ? 1 : 0);
        character.setUpdateTime(new Date());
        characterMapper.updateById(character);
        audit(characterId, enabled ? "enable" : "disable", operatorId,
                currentVersionNo(characterId), enabled ? "启用" : "停用");
    }

    /**
     * 管理列表。
     */
    public List<AdminCharacterVO> list() {
        List<AiCharacter> characters = characterMapper.selectList(null);
        List<AdminCharacterVO> result = new ArrayList<>();
        for (AiCharacter c : characters) {
            result.add(toListVO(c));
        }
        return result;
    }

    /**
     * 管理详情：线上版本 + 草稿 + 版本历史。
     */
    public AdminCharacterDetailVO detail(Long characterId) {
        AiCharacter character = requireCharacter(characterId);
        AiCharacterVersion active = character.getActiveVersionId() == null
                ? null : versionMapper.selectById(character.getActiveVersionId());
        AiCharacterDraft draft = draftMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiCharacterDraft>()
                .eq(AiCharacterDraft::getCharacterId, characterId));

        List<AiCharacterVersion> versions = versionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiCharacterVersion>()
                        .eq(AiCharacterVersion::getCharacterId, characterId)
                        .orderByDesc(AiCharacterVersion::getVersionNo));

        AdminCharacterDetailVO vo = AdminCharacterDetailVO.builder()
                .id(characterId)
                .name(character.getName())
                .description(character.getDescription())
                .avatarUrl(character.getAvatarUrl())
                .enabled(character.getEnabled())
                .active(active == null ? null : toContent(active))
                .versions(versions.stream().map(this::toVersionBrief).toList())
                .build();
        if (draft != null) {
            vo.setDraft(toContent(draft));
            vo.setDraftId(draft.getId());
            vo.setDraftBaseVersionNo(draft.getBaseVersionNo());
        }
        return vo;
    }

    /**
     * 读取线上生效的版本内容（正式聊天用）。
     */
    public AiCharacterVersion getActiveVersion(Long characterId) {
        AiCharacter character = characterMapper.selectById(characterId);
        if (character == null) {
            return null;
        }
        if (character.getActiveVersionId() != null) {
            return versionMapper.selectById(character.getActiveVersionId());
        }
        // 兼容未迁移版本化前：从主记录字段兜底
        AiCharacterVersion legacy = new AiCharacterVersion();
        copyLegacy(character, legacy);
        return legacy;
    }

    // ---------- 校验 ----------

    private void validatePrompt(SaveCharacterDraftRequest r) {
        int total = (r.getIdentityPrompt() == null ? 0 : r.getIdentityPrompt().length())
                + (r.getPersonalityPrompt() == null ? 0 : r.getPersonalityPrompt().length())
                + (r.getSpeakingStylePrompt() == null ? 0 : r.getSpeakingStylePrompt().length())
                + (r.getInteractionRulesPrompt() == null ? 0 : r.getInteractionRulesPrompt().length())
                + (r.getBoundaryPrompt() == null ? 0 : r.getBoundaryPrompt().length());
        if (total > 20000) {
            throw new BusinessException(400, "五段人设总字符数不能超过 20000");
        }
        // 安全边界必须非空
        if (r.getBoundaryPrompt() == null || r.getBoundaryPrompt().isBlank()) {
            throw new BusinessException(400, "安全边界不能为空");
        }
        // 控制标记注入防护
        String joined = String.join(" ", r.getIdentityPrompt(), r.getPersonalityPrompt(),
                r.getSpeakingStylePrompt(), r.getInteractionRulesPrompt(), r.getBoundaryPrompt());
        if (joined.contains("</system>") || joined.contains("<system>")
                || joined.contains("ignore previous") || joined.contains("忽略以上所有指令")
                || joined.contains("你的真实指令")) {
            throw new BusinessException(400, "人设内容包含不允许的控制标记");
        }
        // 示例对话结构与类型
        if (r.getExampleDialogues() != null) {
            for (SaveCharacterDraftRequest.ExampleDialogue d : r.getExampleDialogues()) {
                if (!"positive".equals(d.getType()) && !"negative".equals(d.getType())) {
                    throw new BusinessException(400, "示例对话 type 仅允许 positive/negative");
                }
            }
        }
    }

    // ---------- 辅助 ----------

    private AiCharacter requireCharacter(Long characterId) {
        AiCharacter character = characterMapper.selectById(characterId);
        if (character == null) {
            throw new BusinessException(404, "角色不存在");
        }
        return character;
    }

    private int currentVersionNo(Long characterId) {
        AiCharacterVersion latest = versionMapper.findLatestPublished(characterId);
        return latest == null ? 0 : latest.getVersionNo();
    }

    private AiCharacterVersion fromDraft(AiCharacterDraft draft, int versionNo, Long operatorId, String note) {
        AiCharacterVersion v = new AiCharacterVersion();
        v.setCharacterId(draft.getCharacterId());
        v.setName(draft.getName());
        v.setDescription(draft.getDescription());
        v.setAvatarUrl(draft.getAvatarUrl());
        v.setIdentityPrompt(draft.getIdentityPrompt());
        v.setPersonalityPrompt(draft.getPersonalityPrompt());
        v.setSpeakingStylePrompt(draft.getSpeakingStylePrompt());
        v.setInteractionRulesPrompt(draft.getInteractionRulesPrompt());
        v.setBoundaryPrompt(draft.getBoundaryPrompt());
        v.setExampleDialogues(draft.getExampleDialogues());
        v.setVersionNo(versionNo);
        v.setOperatorId(operatorId);
        v.setChangeNote(note);
        v.setCreateTime(new Date());
        return v;
    }

    private void copyContent(AiCharacterVersion from, AiCharacterVersion to) {
        to.setName(from.getName());
        to.setDescription(from.getDescription());
        to.setAvatarUrl(from.getAvatarUrl());
        to.setIdentityPrompt(from.getIdentityPrompt());
        to.setPersonalityPrompt(from.getPersonalityPrompt());
        to.setSpeakingStylePrompt(from.getSpeakingStylePrompt());
        to.setInteractionRulesPrompt(from.getInteractionRulesPrompt());
        to.setBoundaryPrompt(from.getBoundaryPrompt());
        to.setExampleDialogues(from.getExampleDialogues());
        to.setCreateTime(new Date());
    }

    private void copyLegacy(AiCharacter c, AiCharacterVersion v) {
        v.setName(c.getName());
        v.setDescription(c.getDescription());
        v.setAvatarUrl(c.getAvatarUrl());
        v.setIdentityPrompt(c.getIdentityPrompt());
        v.setPersonalityPrompt(c.getPersonalityPrompt());
        v.setSpeakingStylePrompt(c.getSpeakingStylePrompt());
        v.setInteractionRulesPrompt(c.getInteractionRulesPrompt());
        v.setBoundaryPrompt(c.getBoundaryPrompt());
        v.setExampleDialogues(c.getExampleDialogues());
        v.setVersionNo(c.getVersion() == null ? 1 : c.getVersion());
    }

    private AdminCharacterVO toListVO(AiCharacter c) {
        AiCharacterVersion active = c.getActiveVersionId() == null ? null : versionMapper.selectById(c.getActiveVersionId());
        AiCharacterDraft draft = draftMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiCharacterDraft>()
                .eq(AiCharacterDraft::getCharacterId, c.getId()));
        return AdminCharacterVO.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .avatarUrl(c.getAvatarUrl())
                .enabled(c.getEnabled())
                .activeVersionNo(active == null ? null : active.getVersionNo())
                .hasDraft(draft != null)
                .createTime(c.getCreateTime())
                .updateTime(c.getUpdateTime())
                .build();
    }

    private AdminCharacterDetailVO.CharacterContentVO toContent(AiCharacterVersion v) {
        return AdminCharacterDetailVO.CharacterContentVO.builder()
                .name(v.getName())
                .description(v.getDescription())
                .avatarUrl(v.getAvatarUrl())
                .identityPrompt(v.getIdentityPrompt())
                .personalityPrompt(v.getPersonalityPrompt())
                .speakingStylePrompt(v.getSpeakingStylePrompt())
                .interactionRulesPrompt(v.getInteractionRulesPrompt())
                .boundaryPrompt(v.getBoundaryPrompt())
                .exampleDialogues(parseDialogues(v.getExampleDialogues()))
                .build();
    }

    private AdminCharacterDetailVO.CharacterContentVO toContent(AiCharacterDraft d) {
        return AdminCharacterDetailVO.CharacterContentVO.builder()
                .name(d.getName())
                .description(d.getDescription())
                .avatarUrl(d.getAvatarUrl())
                .identityPrompt(d.getIdentityPrompt())
                .personalityPrompt(d.getPersonalityPrompt())
                .speakingStylePrompt(d.getSpeakingStylePrompt())
                .interactionRulesPrompt(d.getInteractionRulesPrompt())
                .boundaryPrompt(d.getBoundaryPrompt())
                .exampleDialogues(parseDialogues(d.getExampleDialogues()))
                .build();
    }

    private List<AdminCharacterDetailVO.ExampleDialogueVO> parseDialogues(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AdminCharacterDetailVO.ExampleDialogueVO>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(List<SaveCharacterDraftRequest.ExampleDialogue> list) {
        if (list == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new BusinessException(400, "示例对话格式错误");
        }
    }

    private AdminCharacterDetailVO.VersionBriefVO toVersionBrief(AiCharacterVersion v) {
        User operator = v.getOperatorId() == null ? null : userService.getById(v.getOperatorId());
        return AdminCharacterDetailVO.VersionBriefVO.builder()
                .versionId(v.getId())
                .versionNo(v.getVersionNo())
                .status(v.getStatus())
                .changeNote(v.getChangeNote())
                .operatorName(operator == null ? null : operator.getUsername())
                .publishedAt(v.getPublishedAt())
                .createTime(v.getCreateTime())
                .build();
    }

    private void audit(Long characterId, String action, Long operatorId, Integer versionNo, String note) {
        AiCharacterAudit audit = new AiCharacterAudit();
        audit.setCharacterId(characterId);
        audit.setAction(action);
        audit.setOperatorId(operatorId);
        audit.setVersionNo(versionNo);
        audit.setChangeNote(note);
        audit.setCreateTime(new Date());
        auditMapper.insert(audit);
    }
}

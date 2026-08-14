package com.xzh.friendxxx.ai.controller;

import com.xzh.friendxxx.ai.model.SseEvent;
import com.xzh.friendxxx.ai.model.admin.PreviewChatRequest;
import com.xzh.friendxxx.ai.model.admin.SaveCharacterDraftRequest;
import com.xzh.friendxxx.ai.model.admin.PublishCharacterRequest;
import com.xzh.friendxxx.ai.model.vo.AdminCharacterDetailVO;
import com.xzh.friendxxx.ai.model.vo.AdminCharacterVO;
import com.xzh.friendxxx.ai.service.AiCharacterVersionService;
import com.xzh.friendxxx.ai.service.AiPreviewService;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI 人设管理接口（管理员专用，路径 /admin/** 由 AdminInterceptor 拦截）。
 */
@RestController
@RequestMapping("/admin/ai/characters")
@RequiredArgsConstructor
public class AiCharacterAdminController {

    private final AiCharacterVersionService versionService;
    private final AiPreviewService previewService;

    @GetMapping
    @Operation(summary = "人设管理列表")
    public Result<List<AdminCharacterVO>> list() {
        return Result.success(versionService.list());
    }

    @PostMapping
    @Operation(summary = "新建角色")
    public Result<AdminCharacterVO> create(@RequestBody @Valid SaveCharacterDraftRequest request) {
        return Result.success(versionService.create(operatorId(), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "角色详情（线上版本+草稿+版本历史）")
    public Result<AdminCharacterDetailVO> detail(@PathVariable Long id) {
        return Result.success(versionService.detail(id));
    }

    @PutMapping("/{id}/draft")
    @Operation(summary = "保存草稿（乐观锁）")
    public Result<String> saveDraft(@PathVariable Long id,
                                    @RequestBody @Valid SaveCharacterDraftRequest request) {
        versionService.saveDraft(id, operatorId(), request);
        return Result.success("草稿已保存");
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "版本历史")
    public Result<List<AdminCharacterDetailVO.VersionBriefVO>> versions(@PathVariable Long id) {
        AdminCharacterDetailVO detail = versionService.detail(id);
        return Result.success(detail.getVersions());
    }

    @PostMapping(value = "/{id}/preview", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "草稿隔离预览聊天（SSE，不落库）")
    public Flux<ServerSentEvent<SseEvent>> preview(@PathVariable Long id,
                                                   @RequestBody @Valid PreviewChatRequest request) {
        AdminCharacterDetailVO detail = versionService.detail(id);
        AdminCharacterDetailVO.CharacterContentVO draft = detail.getDraft();
        SaveCharacterDraftRequest draftReq = toDraftRequest(draft, detail.getDraftBaseVersionNo());
        return previewService.preview(id, draftReq, request)
                .map(event -> ServerSentEvent.<SseEvent>builder()
                        .event(event.getType())
                        .data(event)
                        .build());
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布草稿（原子切换线上版本）")
    public Result<AdminCharacterDetailVO.VersionBriefVO> publish(@PathVariable Long id,
                                                                 @RequestBody @Valid PublishCharacterRequest request) {
        return Result.success(versionService.publish(id, operatorId(),
                request.getExpectedVersionNo(), request.getChangeNote()));
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "回滚到指定历史版本")
    public Result<AdminCharacterDetailVO.VersionBriefVO> rollback(@PathVariable Long id,
                                                                  @RequestBody Map<String, Object> body) {
        Long versionId = Long.valueOf(String.valueOf(body.get("versionId")));
        String changeNote = body.get("changeNote") == null ? null : String.valueOf(body.get("changeNote"));
        return Result.success(versionService.rollback(id, operatorId(), versionId, changeNote));
    }

    @PatchMapping("/{id}/enabled")
    @Operation(summary = "启用/停用角色")
    public Result<String> setEnabled(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        versionService.setEnabled(id, operatorId(), enabled);
        return Result.success(enabled ? "已启用" : "已停用");
    }

    private Long operatorId() {
        return BaseContext.getCurrentId();
    }

    private SaveCharacterDraftRequest toDraftRequest(AdminCharacterDetailVO.CharacterContentVO draft, Integer baseNo) {
        SaveCharacterDraftRequest r = new SaveCharacterDraftRequest();
        if (draft == null) {
            throw new RuntimeException("角色没有草稿，请先保存草稿再预览");
        }
        r.setName(draft.getName());
        r.setDescription(draft.getDescription());
        r.setAvatarUrl(draft.getAvatarUrl());
        r.setIdentityPrompt(draft.getIdentityPrompt());
        r.setPersonalityPrompt(draft.getPersonalityPrompt());
        r.setSpeakingStylePrompt(draft.getSpeakingStylePrompt());
        r.setInteractionRulesPrompt(draft.getInteractionRulesPrompt());
        r.setBoundaryPrompt(draft.getBoundaryPrompt());
        if (draft.getExampleDialogues() != null) {
            List<SaveCharacterDraftRequest.ExampleDialogue> dialogues = draft.getExampleDialogues().stream()
                    .map(d -> {
                        SaveCharacterDraftRequest.ExampleDialogue e = new SaveCharacterDraftRequest.ExampleDialogue();
                        e.setType(d.getType());
                        e.setUser(d.getUser());
                        e.setReply(d.getReply());
                        return e;
                    }).toList();
            r.setExampleDialogues(dialogues);
        }
        r.setExpectedVersionNo(baseNo);
        return r;
    }
}

package com.xzh.friendxxx.ai.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 角色管理详情：线上版本 + 草稿摘要 + 版本历史摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminCharacterDetailVO {

    private Long id;
    private String name;
    private String description;
    private String avatarUrl;
    private Integer enabled;
    /** 线上生效版本（含五段 prompt 与示例） */
    private CharacterContentVO active;
    /** 当前草稿（null 表示无草稿） */
    private CharacterContentVO draft;
    /** 当前草稿 ID（发布/冲突定位用） */
    private Long draftId;
    /** 草稿基于的线上版本号（乐观锁） */
    private Integer draftBaseVersionNo;
    private List<VersionBriefVO> versions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CharacterContentVO {
        private String name;
        private String description;
        private String avatarUrl;
        private String identityPrompt;
        private String personalityPrompt;
        private String speakingStylePrompt;
        private String interactionRulesPrompt;
        private String boundaryPrompt;
        private List<ExampleDialogueVO> exampleDialogues;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExampleDialogueVO {
        private String type;
        private String user;
        private String reply;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VersionBriefVO {
        private Long versionId;
        private Integer versionNo;
        private String status;
        private String changeNote;
        private String operatorName;
        private java.util.Date publishedAt;
        private java.util.Date createTime;
    }
}

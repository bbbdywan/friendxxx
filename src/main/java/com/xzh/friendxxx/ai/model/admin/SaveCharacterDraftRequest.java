package com.xzh.friendxxx.ai.model.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 保存人设草稿 DTO。五段 prompt 必填并有长度限制，示例对话结构校验。
 */
@Data
public class SaveCharacterDraftRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称不能超过 100 字")
    private String name;

    @Size(max = 500, message = "简介不能超过 500 字")
    private String description;

    @Size(max = 500, message = "头像地址不合法")
    private String avatarUrl;

    @NotBlank(message = "身份设定不能为空")
    @Size(max = 5000, message = "身份设定不能超过 5000 字")
    private String identityPrompt;

    @NotBlank(message = "性格设定不能为空")
    @Size(max = 5000, message = "性格设定不能超过 5000 字")
    private String personalityPrompt;

    @NotBlank(message = "语言风格不能为空")
    @Size(max = 5000, message = "语言风格不能超过 5000 字")
    private String speakingStylePrompt;

    @NotBlank(message = "互动规则不能为空")
    @Size(max = 5000, message = "互动规则不能超过 5000 字")
    private String interactionRulesPrompt;

    @NotBlank(message = "安全边界不能为空")
    @Size(max = 5000, message = "安全边界不能超过 5000 字")
    private String boundaryPrompt;

    @Valid
    @Size(max = 50, message = "示例对话不能超过 50 组")
    private List<ExampleDialogue> exampleDialogues;

    /** 乐观锁：草稿基于的线上版本号 */
    private Integer expectedVersionNo;

    @Data
    public static class ExampleDialogue {
        @NotBlank(message = "示例类型不能为空")
        private String type;
        @NotBlank(message = "示例用户输入不能为空")
        @Size(max = 2000, message = "示例用户输入不能超过 2000 字")
        private String user;
        @NotBlank(message = "示例回复不能为空")
        @Size(max = 2000, message = "示例回复不能超过 2000 字")
        private String reply;
    }
}

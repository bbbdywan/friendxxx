package com.xzh.friendxxx.ai.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 角色公开列表 DTO。仅暴露最小公开信息，绝不返回五段 Prompt、示例对话或版本数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CharacterVO {

    private Long id;
    private String name;
    private String avatarUrl;
}

package com.xzh.friendxxx.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * V4 Flash 输出的关系状态更新建议。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RelationshipUpdateResult {

    private BigDecimal familiarityDelta;

    private BigDecimal trustDelta;

    @JsonProperty("preferredAddress")
    private String preferredAddress;

    @JsonProperty("recentMood")
    private String recentMood;

    private String summary;

    public boolean valid() {
        return familiarityDelta != null && trustDelta != null;
    }
}

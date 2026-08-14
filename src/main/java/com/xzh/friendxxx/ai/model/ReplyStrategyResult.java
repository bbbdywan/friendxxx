package com.xzh.friendxxx.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * V4 Flash 输出的情绪/意图/回复策略识别结果。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReplyStrategyResult {

    private String emotion;

    private Double intensity;

    private String intent;

    private String strategy;

    @JsonProperty("shouldGiveAdvice")
    private Boolean shouldGiveAdvice;

    @JsonProperty("shouldRecallMemory")
    private Boolean shouldRecallMemory;

    @JsonProperty("thinkingRequired")
    private Boolean thinkingRequired;

    private String riskLevel;

    public boolean valid() {
        return strategy != null && !strategy.isBlank()
                && emotion != null && !emotion.isBlank()
                && intensity != null
                && thinkingRequired != null;
    }
}

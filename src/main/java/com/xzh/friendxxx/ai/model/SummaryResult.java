package com.xzh.friendxxx.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * V4 Flash 输出的会话摘要结果。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummaryResult {

    private String summary;

    @JsonProperty("importantTopics")
    private java.util.List<String> importantTopics;

    public boolean valid() {
        return summary != null && !summary.isBlank();
    }
}

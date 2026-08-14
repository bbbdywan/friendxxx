package com.xzh.friendxxx.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * V4 Flash 输出的长期记忆提取结果。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryExtractionResult {

    private List<MemoryItem> memories = new ArrayList<>();

    public boolean valid() {
        return memories != null;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MemoryItem {
        private String type;
        private String key;
        private String content;
        @JsonProperty("normalizedValue")
        private String normalizedValue;
        private BigDecimal importance;
        private BigDecimal confidence;
        @JsonProperty("emotionalWeight")
        private BigDecimal emotionalWeight;
        @JsonProperty("expiresAt")
        private String expiresAt;
    }
}

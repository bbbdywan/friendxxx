package com.xzh.friendxxx.ai.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DeepSeek 非流式 Chat 响应。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeepSeekResponse {

    private String id;

    private String model;

    private List<Choice> choices;

    private Usage usage;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Choice {
        private Integer index;
        private Message message;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        private String role;
        private String content;
        @JsonProperty("reasoning_content")
        private String reasoningContent;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
        @JsonProperty("prompt_cache_hit_tokens")
        private Integer promptCacheHitTokens;
    }
}

package com.xzh.friendxxx.ai.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DeepSeek 流式响应中的一个 SSE 数据分片。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeepSeekStreamChunk {

    private String id;

    private String model;

    private List<Choice> choices;

    private Usage usage;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Integer index;
        private Delta delta;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {
        private String role;
        private String content;
        /**
         * 思考模式的推理内容。服务端必须丢弃，禁止透传、保存或记录。
         */
        @JsonProperty("reasoning_content")
        private String reasoningContent;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
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

    /**
     * 是否携带当前 token 用量（流式末尾的 usage 分片）。
     */
    public boolean hasUsage() {
        return usage != null;
    }

    /**
     * 获取该分片中的内容增量，去掉 reasoning_content。
     */
    public String contentDelta() {
        if (choices == null || choices.isEmpty() || choices.get(0).delta == null) {
            return null;
        }
        return choices.get(0).delta.content;
    }

    public String finishReason() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).finishReason;
    }
}

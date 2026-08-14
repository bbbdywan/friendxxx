package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.client.DeepSeekChatClient;
import com.xzh.friendxxx.ai.client.DeepSeekRequest;
import com.xzh.friendxxx.ai.client.DeepSeekResponse;
import com.xzh.friendxxx.ai.config.DeepSeekProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 辅助任务统一调用入口。全部使用 V4 Flash、关闭思考、严格 JSON 输出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUtilityService {

    private final DeepSeekChatClient deepSeekChatClient;
    private final DeepSeekProperties properties;

    /**
     * 调用 Flash 执行辅助任务，返回原始文本。
     */
    public String callUtility(String systemPrompt, String userPrompt) {
        DeepSeekRequest request = DeepSeekRequest.builder()
                .model(properties.utilityModel())
                .messages(List.of(
                        new DeepSeekRequest.Message("system", systemPrompt),
                        new DeepSeekRequest.Message("user", userPrompt)))
                .temperature(0.1)
                .topP(0.3)
                .maxTokens(1024)
                .thinking(DeepSeekRequest.Thinking.builder().type("disabled").build())
                .build();
        DeepSeekResponse response = deepSeekChatClient.chat(request);
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        String content = response.getChoices().get(0).getMessage().getContent();
        return content == null || content.isBlank() ? null : content;
    }

    /**
     * 辅助任务失败时只记录状态，返回 null，不影响主回复。
     */
    public <T> T callUtilityStrict(String systemPrompt, String userPrompt, Class<T> resultType) {
        try {
            String raw = callUtility(systemPrompt, userPrompt);
            T result = raw == null ? null : JsonParseUtils.parse(raw, resultType);
            if (result != null) {
                return result;
            }
            // 允许一次修复重试
            String retry = callUtility(systemPrompt + "\n请严格只输出 JSON，不要包含任何其它文字。", userPrompt);
            return retry == null ? null : JsonParseUtils.parse(retry, resultType);
        } catch (Exception e) {
            log.warn("辅助任务执行失败，已降级: {}", e.getMessage());
            return null;
        }
    }
}

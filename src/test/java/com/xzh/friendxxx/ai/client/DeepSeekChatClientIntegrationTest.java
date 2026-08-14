package com.xzh.friendxxx.ai.client;

import com.xzh.friendxxx.ai.config.DeepSeekClientConfig;
import com.xzh.friendxxx.ai.config.DeepSeekProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepSeek 官方 API 集成测试（规格第 4 步）。
 *
 * <p>需要环境变量 DEEPSEEK_API_KEY 才运行。验证：
 * deepseek-v4-flash 非流式、流式、关闭思考模式、reasoning_content 过滤。
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekChatClientIntegrationTest {

    private DeepSeekChatClient client() {
        DeepSeekProperties properties = new DeepSeekProperties(
                "https://api.deepseek.com",
                System.getenv("DEEPSEEK_API_KEY"),
                "deepseek-v4-flash",
                "deepseek-v4-flash",
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofSeconds(120));
        return new DeepSeekChatClient(
                new DeepSeekClientConfig().deepSeekWebClient(properties), properties);
    }

    private DeepSeekRequest baseRequest(String model) {
        return DeepSeekRequest.builder()
                .model(model)
                .messages(List.of(
                        new DeepSeekRequest.Message("system", "你是一个简短的测试助手，回答不超过20个字。"),
                        new DeepSeekRequest.Message("user", "你好，请介绍下自己")))
                .stream(false)
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(200)
                .thinking(DeepSeekRequest.Thinking.builder().type("disabled").build())
                .build();
    }

    @Test
    void v4FlashNonStreaming() {
        DeepSeekChatClient c = client();
        DeepSeekResponse resp = c.chat(baseRequest("deepseek-v4-flash"));
        assertNotNull(resp);
        assertNotNull(resp.getChoices());
        assertFalse(resp.getChoices().isEmpty());
        assertNotNull(resp.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void v4FlashStreamingWithoutReasoningLeak() {
        DeepSeekChatClient c = client();
        DeepSeekRequest req = baseRequest("deepseek-v4-flash");
        req.setStream(true);
        StringBuilder content = new StringBuilder();
        Flux<DeepSeekStreamChunk> flux = c.stream(req)
                .doOnNext(chunk -> {
                    String delta = chunk.contentDelta();
                    if (delta != null) {
                        content.append(delta);
                    }
                });
        flux.blockLast(Duration.ofSeconds(60));
        assertFalse(content.toString().isBlank());
        assertFalse(content.toString().contains("reasoning"));
    }
}

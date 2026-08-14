package com.xzh.friendxxx.ai.client;

import com.xzh.friendxxx.ai.config.DeepSeekClientConfig;
import com.xzh.friendxxx.ai.config.DeepSeekProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeepSeekChatClientStreamTest {

    private DisposableServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.disposeNow();
        }
    }

    @Test
    void decodesDeepSeekSseThroughWebClientIncludingUsage() {
        String body = "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"deepseek-v4-flash\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\"},"
                + "\"logprobs\":null,\"finish_reason\":null}]}\n\n"
                + "data: {\"id\":\"1\",\"model\":\"deepseek-v4-flash\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\\u4eca\\u5929\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"id\":\"1\",\"model\":\"deepseek-v4-flash\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\\u8f9b\\u82e6\\u4e86\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"id\":\"1\",\"model\":\"deepseek-v4-flash\","
                + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":21,\"completion_tokens\":4,\"total_tokens\":25}}\n\n"
                + "data: [DONE]\n\n";

        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.post("/chat/completions", (request, response) -> response
                        .header("Content-Type", "text/event-stream")
                        .sendString(reactor.core.publisher.Flux.just(
                                body.substring(0, 47),
                                body.substring(47, 173),
                                body.substring(173)))))
                .bindNow();

        DeepSeekProperties properties = new DeepSeekProperties(
                "http://127.0.0.1:" + server.port(), "test-key",
                "deepseek-v4-flash", "deepseek-v4-flash",
                Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(5));
        DeepSeekChatClient client = new DeepSeekChatClient(
                new DeepSeekClientConfig().deepSeekWebClient(properties), properties);
        DeepSeekRequest request = DeepSeekRequest.builder()
                .model("deepseek-v4-flash")
                .messages(List.of(new DeepSeekRequest.Message("user", "test")))
                .thinking(new DeepSeekRequest.Thinking("disabled"))
                .build();

        List<DeepSeekStreamChunk> chunks = client.stream(request).collectList().block(Duration.ofSeconds(5));

        String content = chunks.stream()
                .map(DeepSeekStreamChunk::contentDelta)
                .filter(value -> value != null && !value.isEmpty())
                .reduce("", String::concat);
        DeepSeekStreamChunk usageChunk = chunks.stream()
                .filter(DeepSeekStreamChunk::hasUsage)
                .findFirst()
                .orElseThrow();

        assertEquals("今天辛苦了", content);
        assertEquals(21, usageChunk.getUsage().getPromptTokens());
        assertEquals(4, usageChunk.getUsage().getCompletionTokens());
    }
}

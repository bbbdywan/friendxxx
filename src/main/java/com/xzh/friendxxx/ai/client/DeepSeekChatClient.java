package com.xzh.friendxxx.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzh.friendxxx.ai.config.DeepSeekProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Spring WebClient 的 DeepSeek 官方 API 薄封装客户端。
 *
 * <p>能力：非流式 chat、流式 SSE、thinking 参数透传、首 Token 超时与总超时、
 * 指数退避重试、reasoning_content 过滤、错误码映射。
 */
@Slf4j
public class DeepSeekChatClient {

    private static final String CHAT_PATH = "/chat/completions";
    private static final int MAX_RETRIES = 2;

    private final WebClient webClient;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekChatClient(WebClient webClient, DeepSeekProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    public DeepSeekProperties properties() {
        return properties;
    }

    /**
     * 非流式调用。
     */
    public DeepSeekResponse chat(DeepSeekRequest request) {
        request.setStream(false);
        try {
            return webClient.post()
                    .uri(CHAT_PATH)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::mapError)
                    .bodyToMono(DeepSeekResponse.class)
                    .timeout(properties.responseTimeout())
                    .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(500))
                            .filter(this::isRetryable)
                            .onRetryExhaustedThrow((spec, sig) -> sig.failure()))
                    .block();
        } catch (Exception e) {
            throw translate(e);
        }
    }

    /**
     * 流式调用。返回的 Flux 会在建立连接/首包阶段对可重试错误进行指数退避重试，
     * 一旦已经向外发出内容增量，则不再重试。
     */
    public Flux<DeepSeekStreamChunk> stream(DeepSeekRequest request) {
        request.setStream(true);
        return Flux.defer(() -> {
            AtomicBoolean contentEmitted = new AtomicBoolean(false);
            AtomicLong firstContentDeadline = new AtomicLong();

            Flux<DeepSeekStreamChunk> source = Flux.defer(() -> {
                        firstContentDeadline.set(System.nanoTime() + properties.firstTokenTimeout().toNanos());
                        return rawStream(request);
                    })
                    .doOnNext(chunk -> {
                        String delta = chunk.contentDelta();
                        if (delta != null && !delta.isEmpty()) {
                            contentEmitted.set(true);
                        }
                    });

            return source
                    // Keep waiting for actual content, not merely the initial role-only SSE frame.
                    .timeout(Mono.delay(properties.firstTokenTimeout()),
                            ignored -> contentEmitted.get()
                                    ? Flux.never()
                                    : Mono.delay(remaining(firstContentDeadline.get())))
                    .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(500))
                            .filter(e -> !contentEmitted.get() && isRetryable(e))
                            .onRetryExhaustedThrow((spec, sig) -> sig.failure()));
        });
    }

    private Flux<DeepSeekStreamChunk> rawStream(DeepSeekRequest request) {
        SseFrameDecoder decoder = new SseFrameDecoder();
        return webClient.post()
                .uri(CHAT_PATH)
                .bodyValue(request)
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToFlux(DataBuffer.class)
                .concatMapIterable(buffer -> decodeBuffer(buffer, decoder))
                .takeUntil("[DONE]"::equals)
                .filter(data -> !"[DONE]".equals(data))
                .map(this::parseSseData);
    }

    private Iterable<String> decodeBuffer(DataBuffer buffer, SseFrameDecoder decoder) {
        try {
            return decoder.accept(buffer.toString(StandardCharsets.UTF_8));
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private Duration remaining(long deadlineNanos) {
        long nanos = Math.max(1, deadlineNanos - System.nanoTime());
        return Duration.ofNanos(nanos);
    }

    private Mono<? extends Throwable> mapError(org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .map(body -> new DeepSeekApiException(
                        response.statusCode().value(), "DeepSeek API 调用失败", body));
    }

    private boolean isRetryable(Throwable e) {
        if (e instanceof DeepSeekApiException api) {
            return api.isRetryable();
        }
        if (e instanceof IOException || e instanceof TimeoutException || e instanceof WebClientRequestException) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause != null && cause != e && isRetryable(cause);
    }

    private RuntimeException translate(Exception e) {
        if (e instanceof RuntimeException re) {
            return re;
        }
        return new DeepSeekApiException(0, "DeepSeek 调用异常", null, e);
    }

    private DeepSeekStreamChunk parseSseData(String data) {
        try {
            return objectMapper.readValue(data, DeepSeekStreamChunk.class);
        } catch (JsonProcessingException e) {
            String sample = data.length() > 300 ? data.substring(0, 300) : data;
            throw new DeepSeekApiException(0, "DeepSeek SSE 数据解析失败", sample, e);
        }
    }
}

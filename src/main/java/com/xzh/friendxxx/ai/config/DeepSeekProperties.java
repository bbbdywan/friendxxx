package com.xzh.friendxxx.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * DeepSeek 官方 API 客户端配置。
 *
 * <p>配置前缀 app.ai.deepseek，禁止在仓库中提供任何真实或可用的默认 API Key。
 */
@ConfigurationProperties(prefix = "app.ai.deepseek")
public record DeepSeekProperties(
        String baseUrl,
        String apiKey,
        String chatModel,
        String utilityModel,
        Duration connectTimeout,
        Duration firstTokenTimeout,
        Duration responseTimeout
) {

    public boolean apiKeyPresent() {
        return apiKey != null && !apiKey.isBlank();
    }
}

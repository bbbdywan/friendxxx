package com.xzh.friendxxx.ai.config;

import com.xzh.friendxxx.ai.client.DeepSeekChatClient;
import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * DeepSeek 客户端装配。使用 WebClient 薄封装，不依赖 DashScope 专用客户端。
 */
@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class DeepSeekClientConfig {

    @Bean
    public WebClient deepSeekWebClient(DeepSeekProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.connectTimeout().toMillis())
                .responseTimeout(properties.responseTimeout());

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs()
                                .maxInMemorySize(4 * 1024 * 1024))
                        .build())
                .build();
    }

    @Bean
    public DeepSeekChatClient deepSeekChatClient(WebClient deepSeekWebClient, DeepSeekProperties properties) {
        return new DeepSeekChatClient(deepSeekWebClient, properties);
    }
}

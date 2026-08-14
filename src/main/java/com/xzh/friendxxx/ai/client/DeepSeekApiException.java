package com.xzh.friendxxx.ai.client;

import lombok.Getter;

/**
 * DeepSeek API 调用异常。errorBody 不包含 Authorization Header。
 */
@Getter
public class DeepSeekApiException extends RuntimeException {

    private final int statusCode;
    private final String errorBody;

    public DeepSeekApiException(int statusCode, String message, String errorBody) {
        super(message);
        this.statusCode = statusCode;
        this.errorBody = errorBody;
    }

    public DeepSeekApiException(int statusCode, String message, String errorBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorBody = errorBody;
    }

    public boolean isRetryable() {
        // 429 限流、5xx 服务端错误、连接类异常可重试
        return statusCode == 429 || statusCode >= 500;
    }
}

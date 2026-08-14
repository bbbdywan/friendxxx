package com.xzh.friendxxx.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 统一错误响应体。
 *
 * <p>结构：{code, message, data, traceId}
 * - code：业务码或机器码（数字或字符串）
 * - message：面向用户的展示文案
 * - data：可选，字段错误等明细
 * - traceId：未知 500 提供，便于按日志定位；不泄露堆栈/SQL/密钥
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private Object code;
    private String message;
    private Map<String, Object> data;
    private String traceId;

    public static ErrorResponse of(Object code, String message) {
        return ErrorResponse.builder().code(code).message(message).build();
    }

    public static ErrorResponse of(Object code, String message, Map<String, Object> data) {
        return ErrorResponse.builder().code(code).message(message).data(data).build();
    }

    public static ErrorResponse of(Object code, String message, Map<String, Object> data, String traceId) {
        return ErrorResponse.builder().code(code).message(message).data(data).traceId(traceId).build();
    }
}

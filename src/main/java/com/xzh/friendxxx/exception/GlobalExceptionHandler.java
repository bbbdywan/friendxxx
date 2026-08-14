package com.xzh.friendxxx.exception;

import com.xzh.friendxxx.ai.client.DeepSeekApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 全局异常处理器。
 *
 * <p>统一返回 ErrorResponse 结构（code/message/data/traceId）并通过 ResponseEntity
 * 透出真实 HTTP 状态。未知 500 生成 traceId，响应只返回安全摘要；完整堆栈在服务器日志。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        int code = e.getCode();
        if (code >= 500) {
            log.error("BusinessException code={}, msg={}", code, e.getMessage(), e);
        } else {
            log.warn("BusinessException code={}, msg={}", code, e.getMessage());
        }
        return ResponseEntity.status(toHttpStatus(code))
                .body(ErrorResponse.of(code, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fieldErrors", fields);
        log.warn("参数校验失败: {}", fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "参数校验失败", data));
    }

    @ExceptionHandler(DeepSeekApiException.class)
    public ResponseEntity<ErrorResponse> handleDeepSeek(DeepSeekApiException e) {
        int status = e.getStatusCode();
        // 映射稳定机器码
        if (status == 401 || status == 403) {
            log.warn("AI 服务认证失败: status={}, msg={}", status, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ErrorResponse.of("AI_AUTH_FAILED", "AI 服务认证失败，请检查服务端配置"));
        }
        if (status == 429) {
            log.warn("AI 服务限流: status=429, msg={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ErrorResponse.of("AI_RATE_LIMITED", "AI 服务繁忙，请稍后重试"));
        }
        if (status >= 500) {
            log.warn("AI 上游不可用: status={}, msg={}", status, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ErrorResponse.of("AI_UPSTREAM_UNAVAILABLE", "AI 服务暂时不可用，请稍后重试"));
        }
        log.warn("AI 上游错误: status={}, msg={}", status, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("AI_UPSTREAM_ERROR", "AI 服务响应异常，请稍后重试"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        String traceId = shortTraceId(UUID.randomUUID().toString());
        log.error("未处理运行时异常 traceId={}", traceId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("SYSTEM_ERROR", "系统错误，请稍后重试", null, traceId));
    }

    private int toHttpStatus(int code) {
        if (code >= 400 && code < 600) {
            return code;
        }
        if (code >= 100 && code < 400) {
            // 业务成功码(如 200)不应走到异常分支；防御性返回 400
            return 400;
        }
        return 500;
    }

    private String shortTraceId(String uuid) {
        return uuid.replace("-", "").substring(0, 12);
    }
}

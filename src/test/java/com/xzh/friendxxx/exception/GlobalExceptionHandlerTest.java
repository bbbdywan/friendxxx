package com.xzh.friendxxx.exception;

import com.xzh.friendxxx.ai.client.DeepSeekApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全局异常处理器：HTTP 状态、统一结构、字段错误、traceId、AI 机器码。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessException400ReturnsHttp400() {
        ResponseEntity<ErrorResponse> resp = handler.handleBusinessException(new BusinessException(400, "安全边界不能为空"));
        assertEquals(400, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(400, resp.getBody().getCode());
        assertEquals("安全边界不能为空", resp.getBody().getMessage());
    }

    @Test
    void businessException409ReturnsHttp409() {
        ResponseEntity<ErrorResponse> resp = handler.handleBusinessException(new BusinessException(409, "版本冲突，请刷新"));
        assertEquals(409, resp.getStatusCode().value());
        assertEquals(409, resp.getBody().getCode());
    }

    @Test
    void businessException429ReturnsHttp429() {
        ResponseEntity<ErrorResponse> resp = handler.handleBusinessException(new BusinessException(429, "请求过于频繁"));
        assertEquals(429, resp.getStatusCode().value());
    }

    @Test
    void validationReturnsFieldErrors() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "dto");
        binding.addError(new FieldError("dto", "identityPrompt", "身份设定不能为空"));
        binding.addError(new FieldError("dto", "personalityPrompt", "性格设定不能为空"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<ErrorResponse> resp = handler.handleValidation(ex);
        assertEquals(400, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> fieldErrors =
                (java.util.Map<String, Object>) resp.getBody().getData().get("fieldErrors");
        assertEquals("身份设定不能为空", fieldErrors.get("identityPrompt"));
        assertEquals("性格设定不能为空", fieldErrors.get("personalityPrompt"));
    }

    @Test
    void unknownExceptionReturns500WithTraceIdAndNoStack() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleRuntimeException(new RuntimeException("db connection refused: jdbc:mysql://secret"));
        assertEquals(500, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("SYSTEM_ERROR", resp.getBody().getCode());
        assertNotNull(resp.getBody().getTraceId());
        assertFalse(resp.getBody().getMessage().contains("db connection"));
        assertFalse(resp.getBody().getMessage().contains("jdbc"));
        assertFalse(resp.getBody().getMessage().contains("secret"));
    }

    @Test
    void deepSeekAuthFailureMapsToAiAuthFailed() {
        ResponseEntity<ErrorResponse> resp = handler.handleDeepSeek(new DeepSeekApiException(401, "invalid key", "{}"));
        assertEquals(502, resp.getStatusCode().value());
        assertEquals("AI_AUTH_FAILED", resp.getBody().getCode());
    }

    @Test
    void deepSeekRateLimitedMapsToAiRateLimited() {
        ResponseEntity<ErrorResponse> resp = handler.handleDeepSeek(new DeepSeekApiException(429, "rate limited", "{}"));
        assertEquals("AI_RATE_LIMITED", resp.getBody().getCode());
    }

    @Test
    void deepSeekServerErrorMapsToAiUpstreamUnavailable() {
        ResponseEntity<ErrorResponse> resp = handler.handleDeepSeek(new DeepSeekApiException(503, "overloaded", "{}"));
        assertEquals("AI_UPSTREAM_UNAVAILABLE", resp.getBody().getCode());
    }

    @Test
    void traceIdFormatIsShortHex() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleRuntimeException(new RuntimeException("boom"));
        String traceId = resp.getBody().getTraceId();
        assertEquals(12, traceId.length());
    }

    @Test
    void validationNullData() {
        // 保证 data 为空时不 NPE
        ArrayList<FieldError> errors = new ArrayList<>();
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "dto");
        errors.forEach(binding::addError);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);
        ResponseEntity<ErrorResponse> resp = handler.handleValidation(ex);
        assertEquals(400, resp.getStatusCode().value());
    }
}

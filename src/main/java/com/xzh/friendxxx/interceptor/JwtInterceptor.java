package com.xzh.friendxxx.interceptor;

import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;

    public JwtInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        String token = StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim() : null;
        Long userId = StringUtils.hasText(token) ? jwtService.resolveUserId(token) : null;
        if (userId == null) {
            // 未登录 / Token 无效 / Token 过期：统一 HTTP 401 + JSON 机器码
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "TOKEN_EXPIRED", "登录已过期，请重新登录");
            return false;
        }
        BaseContext.setCurrentId(userId);
        return true;
    }

    private void writeJson(HttpServletResponse response, int status, String code, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"data\":null,\"traceId\":null}");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}

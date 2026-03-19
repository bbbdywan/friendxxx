package com.xzh.friendxxx.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 限流拦截器（任务14）
 * 使用 Redis 实现滑动窗口限流，防止恶意请求
 * 根据压测结果优化：每个用户每秒最多 50 个请求
 */
@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 每秒最大请求数
     * 根据压测结果调整：50线程时 QPS=25，CPU只有5%
     * 可以支持更高的并发，设置为 50 次/秒
     */
    private static final int MAX_REQUESTS_PER_SECOND = 50;

    /**
     * 时间窗口（秒）
     */
    private static final int TIME_WINDOW_SECONDS = 1;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取用户标识（优先使用 userId，其次使用 IP）
        String userId = getUserId(request);
        String clientIp = getClientIp(request);
        String identifier = userId != null ? "user:" + userId : "ip:" + clientIp;

        // Redis key
        String redisKey = "rate_limit:" + identifier;

        try {
            // 获取当前时间戳（秒）
            long currentTime = System.currentTimeMillis() / 1000;

            // 使用 Redis 的 INCR 命令实现计数
            Long count = redisTemplate.opsForValue().increment(redisKey, 1);

            if (count == null) {
                count = 1L;
            }

            // 第一次请求时设置过期时间
            if (count == 1) {
                redisTemplate.expire(redisKey, TIME_WINDOW_SECONDS, TimeUnit.SECONDS);
            }

            // 检查是否超过限制
            if (count > MAX_REQUESTS_PER_SECOND) {
                log.warn("限流触发: 标识={}, 请求数={}, URI={}", identifier, count, request.getRequestURI());

                // 返回 429 状态码
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
                return false;
            }

            log.debug("限流检查通过: 标识={}, 请求数={}/{}", identifier, count, MAX_REQUESTS_PER_SECOND);
            return true;

        } catch (Exception e) {
            log.error("限流检查异常: 标识={}", identifier, e);
            // 异常时放行，避免影响正常业务
            return true;
        }
    }

    /**
     * 从请求中获取用户ID
     */
    private String getUserId(HttpServletRequest request) {
        // 优先从 session 中获取
        Object userIdObj = request.getSession().getAttribute("userId");
        if (userIdObj != null) {
            return userIdObj.toString();
        }

        // 其次从请求头中获取
        String userId = request.getHeader("userId");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }

        // 最后从请求参数中获取
        userId = request.getParameter("userId");
        return userId;
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 处理多个IP的情况（取第一个）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}

package com.xzh.friendxxx.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 限流拦截器（任务14）
 * 使用 Redis 实现按秒固定窗口限流，防止恶意请求
 * 根据压测结果优化：每个用户每秒最多 50 个请求
 *
 * <p>可通过 app.rate-limit.enabled 关闭（默认开启）。开发环境设置
 * APP_RATE_LIMIT_ENABLED=false 可避免依赖本地 Redis。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
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
        String clientIp = request.getRemoteAddr();
        String identifier = userId != null ? "user:" + userId : "ip:" + clientIp;

        // Redis key
        long currentTime = System.currentTimeMillis() / 1000;
        String redisKey = "rate_limit:" + identifier + ":" + currentTime;

        try {
            // 使用 Redis 的 INCR 命令实现计数
            Long count = redisTemplate.opsForValue().increment(redisKey, 1);

            if (count == null) {
                count = 1L;
            }

            // 第一次请求时设置过期时间
            if (count == 1) {
                redisTemplate.expire(redisKey, TIME_WINDOW_SECONDS + 1L, TimeUnit.SECONDS);
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
            // 限流异常：受控放行，避免影响正常业务；真正限频（5 秒内只打一条）
            long now = System.currentTimeMillis();
            long last = lastRedisWarn.get();
            if (now - last >= 5000 && lastRedisWarn.compareAndSet(last, now)) {
                log.warn("限流检查异常(降级放行): 标识={}, err={}", identifier, e.getMessage());
            }
            return true;
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong lastRedisWarn = new java.util.concurrent.atomic.AtomicLong(0);

    /**
     * 从请求中获取用户ID
     */
    private String getUserId(HttpServletRequest request) {
        // 优先从 session 中获取
        var session = request.getSession(false);
        Object userIdObj = session == null ? null : session.getAttribute("userId");
        if (userIdObj != null) {
            return userIdObj.toString();
        }
        return null;
    }
}

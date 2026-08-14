package com.xzh.friendxxx.ai.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志限频工具：同一 key 在指定时间间隔内只放行一次，用于抑制 Redis 异常等重复日志。
 */
public final class LogLimiter {

    private static final ConcurrentMap<String, AtomicLong> LAST_PRINT = new ConcurrentHashMap<>();

    private LogLimiter() {
    }

    /**
     * 判断当前是否允许打印（intervalMillis 内同一 key 只放行一次）。
     */
    public static boolean allow(String key, long intervalMillis) {
        long now = System.currentTimeMillis();
        AtomicLong last = LAST_PRINT.computeIfAbsent(key, k -> new AtomicLong(0));
        long prev = last.get();
        return now - prev >= intervalMillis && last.compareAndSet(prev, now);
    }
}

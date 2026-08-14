package com.xzh.friendxxx.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 后台任务线程池：摘要、长期记忆、关系状态更新。
 *
 * <p>有界队列 + AbortPolicy：线程池和队列满时提交任务直接抛 RejectedExecutionException，
 * 由调用方捕获并记录 WARN + 拒绝计数，绝不在 SSE 请求线程执行（禁止 CallerRunsPolicy）。
 */
@Configuration
public class AiAsyncConfig {

    public static final String EXECUTOR_BEAN_NAME = "aiTaskExecutor";

    /** 拒绝次数指标，供监控使用。 */
    private static final AtomicLong REJECTION_COUNT = new AtomicLong(0);

    @Bean(name = EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-task-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.setRejectedExecutionHandler((r, e) -> {
            REJECTION_COUNT.incrementAndGet();
            throw new RejectedExecutionException("AI 后台任务队列已满，任务被拒绝");
        });
        executor.initialize();
        return executor;
    }

    /** 获取累计拒绝次数。 */
    public static long rejectionCount() {
        return REJECTION_COUNT.get();
    }
}

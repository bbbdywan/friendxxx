package com.xzh.friendxxx;

import java.util.concurrent.*;

public class ThreadPoolManager {
    public static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            4, // corePoolSize：核心线程数
            8, // maximumPoolSize：最大线程数
            60L, TimeUnit.SECONDS, // keepAliveTime：线程最大空闲时间
            new LinkedBlockingQueue<>(1000), // 阻塞队列
            Executors.defaultThreadFactory(), // 默认线程工厂
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
    );
}

package com.xzh.friendxxx.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 启用异步支持（摘要、长期记忆、关系状态等后台任务）。
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}

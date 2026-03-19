package com.xzh.friendxxx.config;

import com.xzh.friendxxx.interceptor.RateLimitInterceptor;
import com.xzh.friendxxx.interceptor.SessionInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

/**
 * 配置类，注册web层相关组件
 */
@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Resource
    private SessionInterceptor sessionInterceptor;

    /**
     * 任务15：注入限流拦截器
     */
    @Resource
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");

        // 任务15：注册限流拦截器（优先级最高，先执行限流检查）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 排除登录、注册接口（避免影响正常登录）
                        "/user/login",
                        "/user/logout",
                        "/user/register",
                        "/user/hrlogin",
                        "/api/user/login",
                        "/api/user/logout",
                        "/api/user/register",
                        "/api/user/hrlogin",
                        // 排除健康检查接口
                        "/health/**",
                        // 排除静态资源
                        "/doc.html",
                        "/doc.html/**",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v2/api-docs/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/favicon.ico",
                        "/knife4j/**",
                        "/openapi/**",
                        "/swagger-config",
                        "/api-docs/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/fonts/**"
                );

        // 注册 Session 拦截器
        registry.addInterceptor(sessionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/helloworld/simple/chat",
                    "/helloworld/simple/chat",
                        "/api/helloworld/stream/chat",
                        "/helloworld/stream/chat",
                        "/user/login",
                    "/user/logout",
                    "/user/hrlogin",
                    "/api/user/login",  // 添加带context-path的路径
                    "/api/user/logout", // 添加带context-path的路径
                        "/api/user/hrlogin",
                    "/test/**",
                    "/health/**",
                    // PDF生成接口
                    "/pdf/**",
                    // Knife4j OpenAPI 3 相关路径
                    "/doc.html",
                    "/doc.html/**",
                    "/webjars/**",
                    "/swagger-resources/**",
                    "/v2/api-docs/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/favicon.ico",
                    "/knife4j/**",
                    // OpenAPI 3 新增路径
                    "/openapi/**",
                    "/swagger-config",
                    "/api-docs/**",
                    "/api-docs",
                    "/swagger-ui/index.html",
                    "/swagger-ui/swagger-ui-bundle.js",
                    "/swagger-ui/swagger-ui-standalone-preset.js",
                    "/swagger-ui/swagger-ui.css",
                    // 静态资源
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/fonts/**"
                );
    }
}

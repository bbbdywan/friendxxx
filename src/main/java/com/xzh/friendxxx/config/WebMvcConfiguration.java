package com.xzh.friendxxx.config;

import com.xzh.friendxxx.interceptor.AdminInterceptor;
import com.xzh.friendxxx.interceptor.RateLimitInterceptor;
import com.xzh.friendxxx.interceptor.JwtInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private JwtInterceptor jwtInterceptor;

    @Resource
    private AdminInterceptor adminInterceptor;

    /**
     * 任务15：注入限流拦截器（app.rate-limit.enabled 关闭时该 Bean 不存在）
     */
    @Autowired(required = false)
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");

        // 任务15：注册限流拦截器（优先级最高，先执行限流检查；开启时才注册）
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 排除登录、注册接口（避免影响正常登录）
                        "/mock/HVSService",
                        "/iptv/queryMac",
                        "/user/register",
                        "/auth/login",
                        "/auth/guest",
                        "/search/news",
                        "/error",
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
        }

        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/mock/HVSService",
                        "/iptv/queryMac",
                        "/auth/login",
                        "/auth/guest",
                        "/search/news",
                        "/user/register",
                        "/error",
                        "/health/**",
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
                        "/api-docs",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/fonts/**"
                );

        // 管理员权限拦截器：仅 /admin/**（JwtInterceptor 已先校验登录）
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**");

    }
}

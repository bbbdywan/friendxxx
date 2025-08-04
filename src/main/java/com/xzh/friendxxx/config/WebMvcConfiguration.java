package com.xzh.friendxxx.config;

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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(sessionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/user/login",
                    "/user/logout",
                    "/user/hrlogin",
                    "/api/user/login",  // 添加带context-path的路径
                    "/api/user/logout", // 添加带context-path的路径
                        "/api/user/hrlogin",
                    "/test/**",
                    "/health/**",
                    "/helloworld/**",
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

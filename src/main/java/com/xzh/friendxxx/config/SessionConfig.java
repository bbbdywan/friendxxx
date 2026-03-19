package com.xzh.friendxxx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Session配置类
 * @author ForeverGreenDam
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30分钟
public class SessionConfig {

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        
        // 设置Cookie名称
        serializer.setCookieName("FRIENDXXX_SESSION");
        
        // 设置Cookie路径
        serializer.setCookiePath("/");
        
        // 设置域名（可选，用于跨子域）
        // serializer.setDomainName("localhost");
        
        // 设置HttpOnly，防止XSS攻击
        serializer.setUseHttpOnlyCookie(true);
        
        // 设置Secure（HTTPS环境下启用）
        serializer.setUseSecureCookie(false); // 开发环境设为false，生产环境建议设为true
        
        // 设置SameSite属性
        serializer.setSameSite("Lax");
        
        return serializer;
    }
}

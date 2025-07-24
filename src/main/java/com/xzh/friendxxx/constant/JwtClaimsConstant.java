package com.xzh.friendxxx.constant;

/**
 * JWT声明常量接口
 * @author ForeverGreenDam
 */
public interface JwtClaimsConstant {
    /**
     * 用户ID
     */
    String USER_ID = "id";
    
    /**
     * 用户名
     */
    String USER_NAME = "username";
    
    /**
     * 用户角色
     */
    String USER_ROLE = "role";
    
    /**
     * 过期时间
     */
    String EXPIRATION = "exp";
    
    /**
     * 发行时间
     */
    String ISSUED_AT = "iat";
}


package com.xzh.friendxxx.interceptor;

import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.constant.SessionConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Session校验拦截器
 * @author ForeverGreenDam
 */
@Component
@Slf4j
public class SessionInterceptor implements HandlerInterceptor {

    /**
     * 校验Session
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("进入Session校验拦截器...");
        
        // 判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            // 当前拦截到的不是动态方法，直接放行
            return true;
        }

        // 获取Session
        HttpSession session = request.getSession(false);
        
        // 校验Session是否存在以及是否包含用户信息
        if (session == null || session.getAttribute(SessionConstant.USER_ID) == null) {
            log.info("Session校验失败，用户未登录");
            // 返回401状态码
            response.setStatus(401);
            return false;
        }

        try {
            // 从Session中获取用户ID
            Long userId = (Long) session.getAttribute(SessionConstant.USER_ID);
            log.info("Session校验成功，当前用户id：{}", userId);
            
            // 将当前用户id存入ThreadLocal
            BaseContext.setCurrentId(userId);
            
            // 通过，放行
            return true;
        } catch (Exception ex) {
            log.error("Session校验异常", ex);
            // 不通过，响应401状态码
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理ThreadLocal，避免内存泄漏
        BaseContext.removeCurrentId();
    }
}

package com.xzh.friendxxx.interceptor;

import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器。拦截 /admin/** 路径。
 *
 * <p>约定：userRole == 1 视为管理员（User 实体注释 0=普通/1=管理员）。
 * 普通登录用户访问返回 HTTP 403；未登录由 JwtInterceptor 先拦截返回 401。
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    /** 管理员角色值（与 User.userRole 注释一致） */
    public static final Integer ADMIN_ROLE = 1;

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        User user = userService.getById(userId);
        if (user == null || !ADMIN_ROLE.equals(user.getUserRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\",\"data\":null,\"traceId\":null}");
            return false;
        }
        return true;
    }
}

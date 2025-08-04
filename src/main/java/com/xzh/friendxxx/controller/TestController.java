package com.xzh.friendxxx.controller;

import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.constant.SessionConstant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 测试控制器
 * @author ForeverGreenDam
 */
@RestController
@RequestMapping("/test")
@Tag(name = "测试接口", description = "用于测试Session功能的接口")
@CrossOrigin(origins = "http://localhost:5173")
public class TestController {

    @GetMapping("/session-info")
    @Operation(summary = "获取Session信息", description = "测试Session是否正常工作")
    public Result<Object> getSessionInfo(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        
        if (session == null) {
            return Result.success("没有活跃的Session");
        }
        
        return Result.success("Session ID: " + session.getId() + 
                            ", 用户ID: " + session.getAttribute(SessionConstant.USER_ID) +
                            ", 创建时间: " + new java.util.Date(session.getCreationTime()) +
                            ", 最后访问时间: " + new java.util.Date(session.getLastAccessedTime()));
    }

    @PostMapping("/create-session")
    @Operation(summary = "创建测试Session", description = "创建一个测试用的Session")
    public Result<String> createTestSession(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionConstant.USER_ID, 999L);
        session.setAttribute(SessionConstant.USER_NAME, "测试用户");
        session.setAttribute(SessionConstant.USER_ACCOUNT, "test");
        
        return Result.success("测试Session已创建，Session ID: " + session.getId());
    }
}

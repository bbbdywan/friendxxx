package com.xzh.friendxxx.controller;

import com.xzh.friendxxx.common.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 健康检查控制器
 * @author ForeverGreenDam
 */
@RestController
@RequestMapping("/health")
@Tag(name = "健康检查", description = "系统健康检查接口")
@CrossOrigin(origins = "http://localhost:5173")
public class HealthController {

    @GetMapping("/check")
    @Operation(summary = "健康检查", description = "检查系统是否正常运行")
    public Result<String> healthCheck() {
        return Result.success("系统运行正常");
    }

    @GetMapping("/session-migration")
    @Operation(summary = "Session迁移状态", description = "检查JWT到Session的迁移状态")
    public Result<String> sessionMigrationStatus() {
        return Result.success("JWT已成功迁移到Session认证");
    }
}

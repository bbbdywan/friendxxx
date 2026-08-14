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
public class HealthController {

    @GetMapping("/check")
    @Operation(summary = "健康检查", description = "检查系统是否正常运行")
    public Result<String> healthCheck() {
        return Result.success("系统运行正常");
    }

}

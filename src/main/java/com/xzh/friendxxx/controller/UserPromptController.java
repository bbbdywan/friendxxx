package com.xzh.friendxxx.controller;

import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.model.entity.UserPrompt;
import com.xzh.friendxxx.service.UserPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/prompt")
public class UserPromptController {

    @Autowired
    private UserPromptService userPromptService;

    @PostMapping("/save")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "保存/更新自定义提示词")
    public Result<String> savePrompt(@RequestBody UserPrompt prompt) {
        Long userId = BaseContext.getCurrentId();
        prompt.setUserId(userId);
        boolean success = userPromptService.savePrompt(prompt);
        return success ? Result.success("保存成功") : Result.error("保存失败");
    }

    @GetMapping("/list")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "获取当前用户所有提示词")
    public Result<List<UserPrompt>> list() {
        Long userId = BaseContext.getCurrentId();
        List<UserPrompt> list = userPromptService.listByUserId(userId);
        return Result.success(list);
    }

    @GetMapping("/active")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "获取当前激活的提示词")
    public Result<UserPrompt> getActive() {
        Long userId = BaseContext.getCurrentId();
        UserPrompt prompt = userPromptService.getActivePrompt(userId);
        return Result.success(prompt);
    }

    @PostMapping("/setActive/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "设置某个提示词为当前使用")
    public Result<String> setActive(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        boolean success = userPromptService.setActive(userId, id);
        return success ? Result.success("设置成功") : Result.error("设置失败");
    }

    @DeleteMapping("/delete/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "删除提示词")
    public Result<String> delete(@PathVariable Long id) {
        boolean success = userPromptService.deletePrompt(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }
}

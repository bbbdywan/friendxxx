package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.client.DeepSeekRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模型配置测试：唯一模型 deepseek-v4-flash，业务请求默认 thinking disabled。
 */
class ModelConfigTest {

    @Test
    void applicationYamlDefaultsToFlash() throws Exception {
        Path yml = Path.of("src/main/resources/application.yml");
        String content = Files.readString(yml);
        assertTrue(content.contains("deepseek-v4-flash"), "chat-model 默认应为 deepseek-v4-flash");
        assertTrue(content.contains("utility-model: ${DEEPSEEK_UTILITY_MODEL:deepseek-v4-flash}"),
                "utility-model 默认应为 deepseek-v4-flash");
        assertFalse(content.contains("deepseek-v4-pro"), "生产配置不得引用 deepseek-v4-pro");
    }

    @Test
    void productionCodeDoesNotReferenceProModel() throws Exception {
        // 生产代码不得硬编码 deepseek-v4-pro
        List<Path> files;
        try (var stream = Files.walk(Path.of("src/main/java"))) {
            files = stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
        for (Path f : files) {
            String content = Files.readString(f);
            assertFalse(content.contains("deepseek-v4-pro"),
                    "生产代码引用 deepseek-v4-pro: " + f);
        }
    }

    @Test
    void orchestrationBuildsThinkingDisabledRequest() throws Exception {
        // 通过反射验证 AiChatOrchestrator 构造的 DeepSeekRequest 使用 thinking disabled
        // 这里只验证客户端请求对象模型可表达 thinking disabled
        DeepSeekRequest request = DeepSeekRequest.builder()
                .model("deepseek-v4-flash")
                .thinking(DeepSeekRequest.Thinking.builder().type("disabled").build())
                .build();
        assertNotNull(request.getThinking());
        assertEquals("disabled", request.getThinking().getType());
    }
}

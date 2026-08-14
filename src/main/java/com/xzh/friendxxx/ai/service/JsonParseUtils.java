package com.xzh.friendxxx.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 辅助任务 JSON 解析工具：剥离可能的 markdown 代码块后解析。
 * 解析失败返回 null，调用方负责降级。
 */
@Slf4j
public final class JsonParseUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonParseUtils() {
    }

    public static <T> T parse(String raw, Class<T> type) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String candidate = stripCodeFence(raw.trim());
        try {
            return MAPPER.readValue(candidate, type);
        } catch (JsonProcessingException e) {
            // 尝试提取第一个 { ... } 块
            int start = candidate.indexOf('{');
            int end = candidate.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return MAPPER.readValue(candidate.substring(start, end + 1), type);
                } catch (JsonProcessingException ex) {
                    log.warn("辅助输出 JSON 解析失败: {}", raw.substring(0, Math.min(raw.length(), 200)));
                    return null;
                }
            }
            log.warn("辅助输出 JSON 解析失败: {}", raw.substring(0, Math.min(raw.length(), 200)));
            return null;
        }
    }

    private static String stripCodeFence(String raw) {
        if (raw.startsWith("```")) {
            int firstNewline = raw.indexOf('\n');
            int lastFence = raw.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return raw.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return raw;
    }
}

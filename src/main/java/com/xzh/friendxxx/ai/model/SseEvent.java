package com.xzh.friendxxx.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 事件负载模型（对应规格 11.3）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SseEvent {

    private String type;
    private Object data;
}

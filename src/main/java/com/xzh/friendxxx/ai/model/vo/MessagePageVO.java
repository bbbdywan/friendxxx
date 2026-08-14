package com.xzh.friendxxx.ai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 消息游标分页响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePageVO {

    private List<AiMessageVO> items;
    /** 下一页游标；null 表示没有更多 */
    private String nextCursor;
    private boolean hasMore;
}

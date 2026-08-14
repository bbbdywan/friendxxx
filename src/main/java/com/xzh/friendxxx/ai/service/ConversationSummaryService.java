package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.ai.model.SummaryResult;
import com.xzh.friendxxx.mapper.AiConversationMapper;
import com.xzh.friendxxx.mapper.AiMessageMapper;
import com.xzh.friendxxx.model.entity.AiConversation;
import com.xzh.friendxxx.model.entity.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话摘要（V4 Flash）。新增 16～24 条消息后异步生成增量摘要，使用版本号防覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private static final int SUMMARY_THRESHOLD = 18;

    private final AiUtilityService aiUtilityService;
    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;

    private static final String SYSTEM_PROMPT = """
            你是对话摘要助手。把最近一段对话压缩成简洁的中文摘要。
            摘要必须包含：讨论过的重要事情、用户明确表达的情绪、尚未解决的问题、已经做出的决定、后续值得追问的事情、角色承诺过的事情。
            不得加入原对话不存在的事实，不把猜测写成事实，使用第三人称、紧凑表达，控制长度在 200 字以内。
            必须只输出 JSON：{"summary":"摘要文本"}
            """;

    /**
     * 触发摘要检查（异步）。
     */
    @Async("aiTaskExecutor")
    public void maybeSummarize(String conversationId) {
        try {
            AiConversation conversation = aiConversationMapper.selectById(conversationId);
            if (conversation == null || conversation.getIsDeleted() != null && conversation.getIsDeleted() == 1) {
                return;
            }
            // 统计上次摘要后新增消息数
            int expectVersion = conversation.getSummaryVersion() == null ? 0 : conversation.getSummaryVersion();
            List<AiMessage> since = aiMessageMapper.listByCursor(
                    conversationId, conversation.getUpdateTime(), null, SUMMARY_THRESHOLD + 1);
            if (since == null || since.size() < SUMMARY_THRESHOLD) {
                return;
            }
            List<AiMessage> recent = aiMessageMapper.recentMessages(conversationId, 40);
            if (recent.isEmpty()) {
                return;
            }
            StringBuilder sb = new StringBuilder("之前摘要：\n");
            if (conversation.getConversationSummary() != null && !conversation.getConversationSummary().isBlank()) {
                sb.append(conversation.getConversationSummary()).append("\n");
            }
            sb.append("新增对话：\n");
            for (AiMessage m : recent) {
                sb.append("user".equals(m.getRole()) ? "用户" : "角色").append("：")
                        .append(m.getContent() == null ? "" : m.getContent().replace('\n', ' ')).append("\n");
            }
            SummaryResult result = aiUtilityService.callUtilityStrict(SYSTEM_PROMPT, sb.toString(), SummaryResult.class);
            if (result != null && result.valid()) {
                String summary = result.getSummary();
                if (conversation.getConversationSummary() != null && !conversation.getConversationSummary().isBlank()) {
                    summary = conversation.getConversationSummary() + "\n" + summary;
                    if (summary.length() > 2000) {
                        summary = summary.substring(summary.length() - 2000);
                    }
                }
                int updated = aiConversationMapper.updateSummaryIfVersion(conversationId, summary, expectVersion);
                if (updated > 0) {
                    log.info("会话摘要已更新: conversationId={}", conversationId);
                }
            }
        } catch (Exception e) {
            log.warn("会话摘要更新失败，不影响主流程: {}", e.getMessage());
        }
    }
}

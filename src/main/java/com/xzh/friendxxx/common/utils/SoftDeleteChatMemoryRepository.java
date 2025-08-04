package com.xzh.friendxxx.common.utils;

import com.alibaba.cloud.ai.memory.jdbc.JdbcChatMemoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;

public class SoftDeleteChatMemoryRepository extends JdbcChatMemoryRepository {
    // 自己写构造函数为 public
    public SoftDeleteChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    protected String getAddSql() {
        return "INSERT INTO ai_chat_memory (conversation_id, content, type, timestamp, is_deleted) VALUES (?, ?, ?, ?, 0)";
    }

    @Override
    protected String getGetSql() {
        return "SELECT content, type FROM ai_chat_memory WHERE conversation_id = ? AND is_deleted = 0 ORDER BY timestamp";
    }

    @Override
    protected String hasTableSql(String tableName) {
        return String.format(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '%s'", tableName);
    }

    @Override
    protected String createTableSql(String tableName) {
        return String.format("""
            CREATE TABLE %s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                conversation_id VARCHAR(256) NOT NULL,
                content LONGTEXT NOT NULL,
                type VARCHAR(100) NOT NULL,
                timestamp TIMESTAMP NOT NULL,
                is_deleted TINYINT(1) DEFAULT 0,
                CONSTRAINT chk_message_type CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
            )
            """, tableName);
    }
}

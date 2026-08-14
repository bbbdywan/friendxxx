-- ============================================================
-- V3__ai_persona_chat.sql
-- AI 拟人聊天重构：新建 5 张表（角色/会话/消息/长期记忆/关系状态）
-- 旧表 ai_chat_memory 与旧 DashScope 实现保留，作为回滚路径，本迁移不做破坏。
-- 执行方式：mysql -uroot -p friendxxx < sql/V3__ai_persona_chat.sql
-- ============================================================

-- 3.1 AI 角色表
CREATE TABLE IF NOT EXISTS ai_character (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    identity_prompt TEXT NOT NULL,
    personality_prompt TEXT NOT NULL,
    speaking_style_prompt TEXT NOT NULL,
    interaction_rules_prompt TEXT NOT NULL,
    boundary_prompt TEXT NOT NULL,
    example_dialogues JSON NULL,
    version INT NOT NULL DEFAULT 1,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3.2 AI 会话表
CREATE TABLE IF NOT EXISTS ai_conversation (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    title VARCHAR(255),
    conversation_summary MEDIUMTEXT,
    summary_version INT NOT NULL DEFAULT 0,
    last_message_at DATETIME,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_character (user_id, character_id),
    INDEX idx_user_last_message (user_id, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3.3 AI 消息表（完整聊天历史；禁止保存 reasoning_content）
CREATE TABLE IF NOT EXISTS ai_message (
    id VARCHAR(64) PRIMARY KEY,
    client_message_id VARCHAR(64),
    conversation_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    model VARCHAR(100),
    input_tokens INT,
    output_tokens INT,
    status VARCHAR(20) NOT NULL DEFAULT 'completed',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_client_message (user_id, client_message_id),
    INDEX idx_conversation_time (conversation_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3.4 长期记忆表
CREATE TABLE IF NOT EXISTS ai_memory (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    conversation_id VARCHAR(64),
    memory_type VARCHAR(30) NOT NULL,
    memory_key VARCHAR(150),
    content TEXT NOT NULL,
    normalized_value TEXT,
    importance DECIMAL(4,3) NOT NULL,
    confidence DECIMAL(4,3) NOT NULL,
    emotional_weight DECIMAL(4,3) NOT NULL DEFAULT 0,
    source_message_id VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    access_count INT NOT NULL DEFAULT 0,
    last_accessed_at DATETIME,
    expires_at DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_memory_owner (user_id, character_id, status),
    INDEX idx_memory_key (user_id, character_id, memory_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3.5 关系状态表
CREATE TABLE IF NOT EXISTS ai_relationship_state (
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    familiarity DECIMAL(5,2) NOT NULL DEFAULT 0,
    trust_level DECIMAL(5,2) NOT NULL DEFAULT 0,
    interaction_count INT NOT NULL DEFAULT 0,
    current_stage VARCHAR(30) NOT NULL DEFAULT 'NEW',
    preferred_address VARCHAR(100),
    recent_mood VARCHAR(50),
    recent_topics JSON,
    relationship_summary TEXT,
    version INT NOT NULL DEFAULT 0,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, character_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

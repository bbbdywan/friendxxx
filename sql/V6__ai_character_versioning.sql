-- ============================================================
-- V6__ai_character_versioning.sql
-- AI 人设版本化发布：草稿与线上版本隔离，发布原子切换，可回滚可审计。
--
-- 设计：
--   ai_character            角色主记录（元数据：名称/头像/启用状态/当前发布版本/草稿引用）
--   ai_character_version    不可变版本快照（每次保存草稿/发布都生成一个 version）
--   ai_character_draft      当前草稿（唯一，含未发布内容）
--   ai_character_audit      管理操作审计（保存/发布/回滚/启停）
--
-- 发布语义：将 draft 内容固化为新的 version 行，并把 ai_character.active_version_id 原子指向它。
-- 回滚语义：以历史 version 内容生成新的 version 行并发布（旧版本不删除，可审计）。
-- ============================================================

-- 1. 角色主记录：补充展示元数据与版本指针
ALTER TABLE ai_character
    ADD COLUMN description VARCHAR(500) NULL COMMENT '公开简介',
    ADD COLUMN avatar_url VARCHAR(500) NULL COMMENT '头像',
    ADD COLUMN active_version_id BIGINT NULL COMMENT '当前线上生效的版本ID',
    ADD COLUMN draft_id BIGINT NULL COMMENT '当前草稿ID';

-- 2. 版本快照表（不可变，发布后不修改）
CREATE TABLE IF NOT EXISTS ai_character_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    avatar_url VARCHAR(500),
    identity_prompt TEXT NOT NULL,
    personality_prompt TEXT NOT NULL,
    speaking_style_prompt TEXT NOT NULL,
    interaction_rules_prompt TEXT NOT NULL,
    boundary_prompt TEXT NOT NULL,
    example_dialogues JSON NULL,
    version_no INT NOT NULL DEFAULT 1 COMMENT '该角色的版本序号',
    status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/published',
    published_at DATETIME NULL,
    operator_id BIGINT NULL,
    change_note VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_char_version (character_id, version_no),
    INDEX idx_char_status (character_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 草稿表（每角色一条，保存草稿时整体覆盖）
CREATE TABLE IF NOT EXISTS ai_character_draft (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    avatar_url VARCHAR(500),
    identity_prompt TEXT NOT NULL,
    personality_prompt TEXT NOT NULL,
    speaking_style_prompt TEXT NOT NULL,
    interaction_rules_prompt TEXT NOT NULL,
    boundary_prompt TEXT NOT NULL,
    example_dialogues JSON NULL,
    base_version_no INT NOT NULL DEFAULT 0 COMMENT '草稿基于的线上版本号（乐观锁）',
    saved_by BIGINT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_character_draft (character_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 审计表
CREATE TABLE IF NOT EXISTS ai_character_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL COMMENT 'create/save_draft/publish/rollback/enable/disable',
    operator_id BIGINT NOT NULL,
    version_no INT,
    change_note VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_character (character_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

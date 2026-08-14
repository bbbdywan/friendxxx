-- ============================================================
-- V7__migrate_existing_characters_to_versioning.sql
-- 把已有的 ai_character 主记录（未版本化）迁移为：
--   1. 生成 version 1（status=published）作为线上基线
--   2. 生成草稿（与 version 1 相同内容）
--   3. 主记录 active_version_id/draft_id 指向它们
-- 幂等：已存在 version 的角色跳过。
-- ============================================================

INSERT INTO ai_character_version
    (character_id, name, description, avatar_url, identity_prompt, personality_prompt,
     speaking_style_prompt, interaction_rules_prompt, boundary_prompt, example_dialogues,
     version_no, status, published_at, operator_id, change_note, create_time)
SELECT c.id, c.name, c.description, c.avatar_url, c.identity_prompt, c.personality_prompt,
       c.speaking_style_prompt, c.interaction_rules_prompt, c.boundary_prompt, c.example_dialogues,
       1, 'published', NOW(), NULL, '版本化初始化', NOW()
FROM ai_character c
WHERE c.id NOT IN (SELECT DISTINCT character_id FROM ai_character_version);

INSERT INTO ai_character_draft
    (character_id, name, description, avatar_url, identity_prompt, personality_prompt,
     speaking_style_prompt, interaction_rules_prompt, boundary_prompt, example_dialogues,
     base_version_no, saved_by, update_time)
SELECT c.id, c.name, c.description, c.avatar_url, c.identity_prompt, c.personality_prompt,
       c.speaking_style_prompt, c.interaction_rules_prompt, c.boundary_prompt, c.example_dialogues,
       1, NULL, NOW()
FROM ai_character c
WHERE c.id NOT IN (SELECT DISTINCT character_id FROM ai_character_draft);

UPDATE ai_character c
JOIN ai_character_version v ON v.character_id = c.id AND v.version_no = 1
JOIN ai_character_draft d ON d.character_id = c.id
SET c.active_version_id = v.id, c.draft_id = d.id
WHERE c.active_version_id IS NULL;

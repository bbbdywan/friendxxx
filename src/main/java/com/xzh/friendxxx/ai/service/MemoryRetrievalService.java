package com.xzh.friendxxx.ai.service;

import com.xzh.friendxxx.mapper.AiMemoryMapper;
import com.xzh.friendxxx.model.entity.AiMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 长期记忆检索与评分。
 *
 * <p>第一版不引入向量库：结构化过滤 + 关键词召回 + 加权排序。
 * finalScore = relevance*0.45 + importance*0.25 + recency*0.15 + emotionalWeight*0.10 + accessBonus*0.05
 */
@Service
@RequiredArgsConstructor
public class MemoryRetrievalService {

    private static final int MAX_RESULTS = 8;

    private final AiMemoryMapper aiMemoryMapper;

    /**
     * 检索与用户当前消息相关的记忆。最多返回 8 条，同 memory_key 最多一条。
     */
    public List<AiMemory> retrieve(Long userId, Long characterId, String query) {
        List<AiMemory> active = aiMemoryMapper.listActive(userId, characterId);
        if (active.isEmpty()) {
            return List.of();
        }

        Set<String> keywords = extractKeywords(query);
        long now = Instant.now().toEpochMilli();

        List<Scored> scored = new ArrayList<>();
        for (AiMemory m : active) {
            double relevance = relevanceScore(m, keywords);
            if (relevance <= 0) {
                continue;
            }
            double importance = m.getImportance() == null ? 0.5 : m.getImportance().doubleValue();
            double emotional = m.getEmotionalWeight() == null ? 0 : m.getEmotionalWeight().doubleValue();
            double recency = recencyScore(m.getUpdateTime() == null ? m.getCreateTime() : m.getUpdateTime(), now);
            double accessBonus = Math.min(1.0, (m.getAccessCount() == null ? 0 : m.getAccessCount()) * 0.05);
            double finalScore = relevance * 0.45 + importance * 0.25 + recency * 0.15
                    + emotional * 0.10 + accessBonus * 0.05;
            scored.add(new Scored(m, finalScore));
        }

        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());

        List<AiMemory> result = new ArrayList<>();
        Set<String> seenKeys = ConcurrentHashMap.newKeySet();
        for (Scored s : scored) {
            String key = s.memory.getMemoryKey();
            if (key != null && !seenKeys.add(key)) {
                continue;
            }
            result.add(s.memory);
            if (result.size() >= MAX_RESULTS) {
                break;
            }
        }
        return result;
    }

    /**
     * 将关键词召回应用到内存中的候选集（按 key 合并）。
     */
    public List<AiMemory> filterByKeywords(List<AiMemory> candidates, String query) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }
        Set<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return candidates.size() > MAX_RESULTS ? candidates.subList(0, MAX_RESULTS) : candidates;
        }
        List<AiMemory> hit = new ArrayList<>();
        for (AiMemory m : candidates) {
            String content = m.getContent() == null ? "" : m.getContent();
            for (String kw : keywords) {
                if (content.contains(kw)) {
                    hit.add(m);
                    break;
                }
            }
        }
        return hit.isEmpty() ? (candidates.size() > MAX_RESULTS ? candidates.subList(0, MAX_RESULTS) : candidates)
                : (hit.size() > MAX_RESULTS ? hit.subList(0, MAX_RESULTS) : hit);
    }

    private double relevanceScore(AiMemory m, Set<String> keywords) {
        if (keywords.isEmpty()) {
            return 0.3; // 无关键词时给基础分，但只召回重要性足够高的
        }
        String content = m.getContent() == null ? "" : m.getContent();
        String key = m.getMemoryKey() == null ? "" : m.getMemoryKey();
        double score = 0;
        for (String kw : keywords) {
            if (content.contains(kw)) {
                score += 0.5;
            }
            if (key.contains(kw)) {
                score += 0.3;
            }
        }
        // PROFILE/GOAL/BOUNDARY 类稳定信息给基础加分，便于跨会话召回
        if ("PROFILE".equals(m.getMemoryType()) || "GOAL".equals(m.getMemoryType())
                || "BOUNDARY".equals(m.getMemoryType())) {
            score += 0.2;
        }
        return score;
    }

    private double recencyScore(java.util.Date date, long now) {
        if (date == null) {
            return 0.3;
        }
        long days = ChronoUnit.DAYS.between(date.toInstant(), Instant.ofEpochMilli(now));
        if (days <= 7) {
            return 1.0;
        } else if (days <= 30) {
            return 0.7;
        } else if (days <= 90) {
            return 0.4;
        }
        return 0.2;
    }

    private Set<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        // 简单分词：去除常见停用词后按 2~6 字符窗口切分，避免过短噪音
        String cleaned = query.replaceAll("[，。！？、,.!?\\s]", " ").trim();
        java.util.TreeSet<String> words = new java.util.TreeSet<>();
        for (String seg : cleaned.split(" ")) {
            if (seg.length() >= 2 && seg.length() <= 12) {
                words.add(seg);
            }
        }
        return words;
    }

    private record Scored(AiMemory memory, double score) {
    }
}

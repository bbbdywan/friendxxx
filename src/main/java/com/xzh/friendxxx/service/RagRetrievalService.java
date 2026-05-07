package com.xzh.friendxxx.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch.ElasticsearchClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final EmbeddingModel embeddingModel;
    private final ElasticsearchClient elasticsearchClient;

    private static final String INDEX = "social_posts_vector";
    private static final float SIMILARITY_THRESHOLD = 0.75f;

    /**
     * 加权混合检索：个人库3条 + 公共库2条
     * @param query  用户问题
     * @param userId 当前用户ID
     * @return 拼接好的上下文字符串，可直接注入Prompt
     */
    public String retrieveContext(String query, Long userId) {
        try {
            float[] queryVector = embeddingModel.embed(query);

            List<String> personal = knnSearch(queryVector, userId, true, 3);
            List<String> common = knnSearch(queryVector, userId, false, 2);

            if (personal.isEmpty() && common.isEmpty()) {
                return "";
            }

            StringBuilder ctx = new StringBuilder("\n\n【相关背景信息】\n");
            personal.forEach(p -> ctx.append("- ").append(p).append("\n"));
            common.forEach(c -> ctx.append("- ").append(c).append("\n"));
            ctx.append("【背景信息结束】\n请结合以上背景信息回答用户的问题。\n\n");
            return ctx.toString();
        } catch (Exception e) {
            log.warn("RAG检索失败，降级为无上下文模式: {}", e.getMessage());
            return "";
        }
    }

    private List<String> knnSearch(float[] vector, Long userId, boolean personalOnly, int topK) {
        List<String> results = new ArrayList<>();
        try {
            List<Float> vectorList = new ArrayList<>();
            for (float v : vector) vectorList.add(v);

            KnnQuery.Builder knnBuilder = new KnnQuery.Builder()
                    .field("embedding")
                    .queryVector(vectorList)
                    .numCandidates(50)
                    .k(topK);

            if (personalOnly && userId != null) {
                // 个人库：只搜当前用户
                knnBuilder.filter(Query.of(q -> q
                        .term(t -> t.field("userId").value(userId.toString()))
                ));
            } else if (!personalOnly && userId != null) {
                // 公共库：排除当前用户，只搜其他人的动态
                knnBuilder.filter(Query.of(q -> q
                        .bool(b -> b
                                .mustNot(mn -> mn
                                        .term(t -> t.field("userId").value(userId.toString()))
                                )
                        )
                ));
            }

            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX)
                    .knn(knnBuilder.build())
                    .source(src -> src.filter(f -> f.includes("content")))
            );

            SearchResponse<Object> response = elasticsearchClient.search(request, Object.class);

            for (Hit<Object> hit : response.hits().hits()) {
                // 相似度过滤：低于阈值不注入
                if (hit.score() != null && hit.score() < SIMILARITY_THRESHOLD) continue;
                if (hit.source() instanceof java.util.Map<?, ?> map) {
                    Object content = map.get("content");
                    if (content != null) results.add(content.toString());
                }
            }
        } catch (Exception e) {
            log.warn("kNN检索异常: {}", e.getMessage());
        }
        return results;
    }
}

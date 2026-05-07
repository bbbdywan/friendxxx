package com.xzh.friendxxx.consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.xzh.friendxxx.config.MQConfig;
import com.xzh.friendxxx.model.entity.PostVectorDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostEmbeddingConsumer {

    private final EmbeddingModel embeddingModel;
    private final ElasticsearchClient elasticsearchClient;

    @RabbitListener(queues = MQConfig.QUEUE_POST_EMBEDDING)
    public void handlePostEmbedding(String message) {
        try {
            JSONObject json = JSON.parseObject(message);
            Long postId = json.getLong("postId");
            Long userId = json.getLong("userId");
            String content = json.getString("content");

            if (content == null || content.isBlank()) return;

            // 调用 DashScope text-embedding-v3 向量化
            float[] vector = embeddingModel.embed(content);

            PostVectorDoc doc = new PostVectorDoc();
            doc.setId(UUID.randomUUID().toString());
            doc.setPostId(postId);
            doc.setUserId(userId);
            doc.setContent(content);
            doc.setEmbedding(vector);
            doc.setCreatedAt(new Date());

            elasticsearchClient.index(IndexRequest.of(i -> i
                    .index("social_posts_vector")
                    .id(doc.getId())
                    .document(doc)
            ));

            log.info("动态向量化成功: postId={}, userId={}", postId, userId);
        } catch (Exception e) {
            log.error("动态向量化失败: {}", message, e);
            throw new RuntimeException("Embedding处理失败", e);
        }
    }
}

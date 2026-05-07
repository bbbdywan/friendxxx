package com.xzh.friendxxx.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.util.Date;

@Data
@Document(indexName = "social_posts_vector")
public class PostVectorDoc {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private Long postId;

    @Field(type = FieldType.Keyword)
    private Long userId;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String content;

    // dense_vector 字段通过 @Mapping 在建索引时指定，实体类直接映射为 float[]
    private float[] embedding;

    @Field(type = FieldType.Date)
    private Date createdAt;
}

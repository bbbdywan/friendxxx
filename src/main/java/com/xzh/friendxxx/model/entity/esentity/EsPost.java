package com.xzh.friendxxx.model.entity.esentity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Document(indexName = "social_post")
public class EsPost {
    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Text, analyzer = "ik_max_word") // 用户昵称全文检索
    private String nickname;

    @Field(type = FieldType.Text, analyzer = "ik_max_word") // 文字内容全文检索
    private String content;

    @Field(type = FieldType.Text) // 存 JSON 字符串
    private String imageList;

    @Field(type = FieldType.Integer)
    private Integer likeCount;

    @Field(type = FieldType.Integer)
    private Integer isDeleted;

    @Field(type = FieldType.Date)
    private LocalDateTime createTime;

    @Field(type = FieldType.Date)
    private LocalDateTime updateTime;

    @Field(type = FieldType.Text) // 心情存 JSON
    private String mood;

    @Field(type = FieldType.Keyword)
    private String avatarUrl;

    @Field(type = FieldType.Keyword)
    private String supTtl;

    @Field(type = FieldType.Keyword)
    private String deleteTtl;
}

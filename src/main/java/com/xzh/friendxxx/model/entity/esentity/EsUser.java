package com.xzh.friendxxx.model.entity.esentity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Date;
@Data
@Document(indexName = "user_index_v3")
public class EsUser {
    @Id
    private Long id;
    private String username;
    private String userAccount;
    private String avatarUrl;
    private Integer gender;
    private String phone;
    private String email;
    private Integer userStatus;
    private Date createTime;
    private Date updateTime;
    private Integer isDelete;
    private Integer userRole;
    private String planetCode;
    private String tags;
    private String background;
    private String signature;
    private Integer age;
    private Integer height;
    private String profession;
    private String education;
    private String zodiac;
    private String hometown;
    private Integer relationshipStatus;
    // 省略getter/setter
}

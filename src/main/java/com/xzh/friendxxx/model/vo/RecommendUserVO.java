package com.xzh.friendxxx.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendUserVO {
    private Long id;
    private String userName;
    private String avatar;
    private String tags;
    private Integer age;
    private Integer gender;
    private String zodiac;
    private Integer height;
    private String profession;
    private String education;
    private String hometown;
    private String signature;
    /** 匹配分数 0~100 */
    private Integer matchScore;
}

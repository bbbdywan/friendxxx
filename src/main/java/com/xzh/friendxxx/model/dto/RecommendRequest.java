package com.xzh.friendxxx.model.dto;

import lombok.Data;

@Data
public class RecommendRequest {
    /** 当前用户ID（必填） */
    private Long userId;
    /** 返回数量，默认10 */
    private Integer limit = 10;
    /** 性别筛选：0-男 1-女 null-不限 */
    private Integer gender;
    /** 年龄下限 */
    private Integer ageMin;
    /** 年龄上限 */
    private Integer ageMax;
    /** 家乡筛选（可选，如"北京"） */
    private String hometown;
}

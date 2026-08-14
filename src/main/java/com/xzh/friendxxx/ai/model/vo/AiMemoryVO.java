package com.xzh.friendxxx.ai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMemoryVO {

    private String id;
    private Long characterId;
    private String memoryType;
    private String memoryKey;
    private String content;
    private String normalizedValue;
    private BigDecimal importance;
    private BigDecimal confidence;
    private Date createTime;
    private Date updateTime;
}

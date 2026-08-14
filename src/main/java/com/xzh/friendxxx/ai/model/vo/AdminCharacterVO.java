package com.xzh.friendxxx.ai.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminCharacterVO {

    private Long id;
    private String name;
    private String description;
    private String avatarUrl;
    private Integer enabled;
    /** 当前线上生效版本号 */
    private Integer activeVersionNo;
    /** 是否有未发布草稿 */
    private Boolean hasDraft;
    private Date createTime;
    private Date updateTime;
    /** 最近修改人 */
    private String lastOperatorName;
}

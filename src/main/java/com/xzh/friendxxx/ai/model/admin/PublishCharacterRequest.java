package com.xzh.friendxxx.ai.model.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布人设草稿请求。
 */
@Data
public class PublishCharacterRequest {

    /** 乐观锁：期望的线上版本号，防止覆盖他人发布 */
    private Integer expectedVersionNo;

    @Size(max = 500, message = "变更说明不能超过 500 字")
    private String changeNote;
}

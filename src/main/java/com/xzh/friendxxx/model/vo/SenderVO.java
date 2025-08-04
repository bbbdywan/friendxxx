package com.xzh.friendxxx.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SenderVO implements Serializable {
    @Schema(description = "发送者ID")
    private Long senderId;

    @Schema(description = "接收者ID")
    private String receiverId;
    @Schema(description = "发送内容")
    private String content;

    @Schema(description = "发送时间")
    private Date createTime;
}

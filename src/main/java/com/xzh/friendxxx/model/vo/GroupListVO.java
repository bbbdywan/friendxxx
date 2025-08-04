package com.xzh.friendxxx.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupListVO implements Serializable {

    @Schema(description = "群聊ID")
    private long group_id;

    @Schema(description = "群聊名称")
    private String group_name;

    @Schema(description = "群聊头像")
    private String avatar_url;

    @Schema(description = "群聊介绍")
    private String introduction;

    @Schema(description = "是否加入群聊")
    private boolean isMember;
}

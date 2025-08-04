package com.xzh.friendxxx.model.vo;
import com.xzh.friendxxx.model.entity.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO implements Serializable {

    @Schema(description = "消息列表")
    private List<ChatMessage> messageList;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "用户名")
    private String userName;
}

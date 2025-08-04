package com.xzh.friendxxx.model.vo;

import com.baomidou.mybatisplus.annotation.TableField;
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
public class UserVO  implements Serializable {
    @Schema(description = "主键值")
    private Long id;

    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "标签")
    private String tags;

    @Schema(description = "账号")
    private String userAccount;

    @Schema(description = "背景图")
    private String background;

    @Schema(description = "个性签名")
    private String signature;

    @Schema(description = "年龄")
    private Integer age;

    @Schema(description = "性别")
    private Integer gender;

    @Schema(description = "星座")
    private String zodiac;

    /**
     * 身高（cm）
     */
    @Schema(description = "身高")
    private Integer height;

    /**
     * 职业
     */
    @Schema(description = "职业")
    private String profession;

    /**
     * 学历
     */
    @Schema(description = "学历")
    private String education;

    /**
     * 家乡
     */
    @Schema(description = "家乡")
    private String hometown;

    /**
     * 感情状态 0:单身 1:热恋中 2:已婚
     */

    @Schema(description = "感情状态")
    private Integer relationshipStatus;
}

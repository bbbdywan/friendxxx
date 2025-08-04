package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户
 * @TableName user
 */
@TableName(value ="user")
@Data
@Schema(description = "用户信息")
public class User {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "用户ID")
    private Long id;

    /**
     * 用户昵称
     */
    @TableField("username")
    @Schema(description = "用户名")
    private String username;

    /**
     * 账号
     */
    @TableField("userAccount")
    @Schema(description = "用户账号")
    private String userAccount;

    /**
     * 用户头像
     */
    @TableField("avatarUrl")
    @Schema(description = "头像URL")
    private String avatarUrl;

    /**
     * 性别
     */
    @TableField("gender")
    private Integer gender;

    /**
     * 密码
     */
    @TableField("userPassword")
    private String userPassword;

    /**
     * 电话
     */
    @TableField("phone")
    private String phone;

    /**
     * 邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 状态 0 - 正常
     */
    @TableField("userStatus")
    private Integer userStatus;

    /**
     * 创建时间
     */
    @TableField("createTime")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField("updateTime")
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    @TableField("isDelete")
    private Integer isDelete;

    /**
     * 用户角色 0 - 普通用户 1 - 管理员
     */
    @TableField("userRole")
    private Integer userRole;

    /**
     * 星球编号
     */
    @TableField("planetCode")
    private String planetCode;

    /**
     * 标签列表
     */
    @TableField("tags")
    private String tags;

    /**
     * 背景图片
     */
    @TableField("background")
    private String background;

    /**
     * 个性签名
     */
    @TableField("signature")
    private String signature;

    /**
     * 年龄
     */
    @TableField("age")
    private Integer age;

    /**
     * 身高（cm）
     */
    @TableField("height")
    private Integer height;

    /**
     * 职业
     */
    @TableField("profession")
    private String profession;

    /**
     * 学历
     */
    @TableField("education")
    private String education;

    /**
     * 星座
     */
    @TableField("zodiac")
    private String zodiac;

    /**
     * 家乡
     */
    @TableField("hometown")
    private String hometown;

    /**
     * 感情状态 0:单身 1:热恋中 2:已婚
     */
    @TableField("relationship_status")
    private Integer relationshipStatus;
}


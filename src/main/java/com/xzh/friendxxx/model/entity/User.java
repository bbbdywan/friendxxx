package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 用户
 * @TableName user
 */
@TableName(value ="user")
@Data
@ApiModel("用户信息")
public class User {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    @ApiModelProperty("用户ID")
    private Long id;

    /**
     * 用户昵称
     */
    @TableField("username")
    @ApiModelProperty("用户名")
    private String username;

    /**
     * 账号
     */
    @TableField("userAccount")
    @ApiModelProperty("用户账号")
    private String userAccount;

    /**
     * 用户头像
     */
    @TableField("avatarUrl")
    @ApiModelProperty("头像URL")
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
}


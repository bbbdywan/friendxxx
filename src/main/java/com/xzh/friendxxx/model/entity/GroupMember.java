package com.xzh.friendxxx.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 群成员关系表
 * @TableName group_member
 */
@TableName(value ="group_member")
@Data
public class GroupMember {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 群聊ID
     */
    @TableField(value = "group_id")
    private Long groupId;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 群内角色: owner/admin/member
     */
    @TableField(value = "role")
    private String role;

    /**
     * 加入时间
     */
    @TableField(value = "join_time")
    private Date joinTime;

    /**
     * 是否被禁言
     */
    @TableField(value = "is_muted")
    private Integer isMuted;

    /**
     * 是否已退出群聊
     */
    @TableField(value = "is_deleted")
    private Integer isDeleted;
}
package com.xzh.friendxxx.exception;

import lombok.Getter;

/**
 * 通用错误码
 * @author ForeverGreenDam
 */
@Getter
public enum ErrorCode {
    // 通用错误码
    SUCCESS(0,"ok"),
    PARAMS_ERROR(40000,"请求参数错误"),
    NOT_LOGIN_ERROR(40100,"未登录"),
    NOT_AUTH_ERROR(40101,"无权限访问"),
    NOT_FOUND_ERROR(40400,"请求数据不存在"),
    FORBIDDEN_ERROR(40300,"禁止访问"),
    SYSTEM_ERROR(50000,"系统内部错误"),
    GET_MESSAGE_ERROR(501,"获取聊天记录失败"),
    OPERATION_ERROR(50001,"操作失败"),
    GROUP_ERROR(50002,"群组操作失败");
    /**
     * 错误码
     */
    private final Integer code;
    /**
     * 错误信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

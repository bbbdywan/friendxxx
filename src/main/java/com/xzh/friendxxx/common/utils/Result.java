package com.xzh.friendxxx.common.utils;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果类
 * @param <T>
 */
@Data
public class Result<T> implements Serializable {

    private Integer code; // 状态码，200 表示成功，其他为失败
    private String message; // 返回信息
    private T data; // 返回数据

    /**
     * 成功时返回数据
     * @param data
     * @param <T>
     * @return
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    /**
     * 成功时返回数据和自定义消息
     * @param message
     * @param data
     * @param <T>
     * @return
     */
    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    /**
     * 失败时返回错误信息
     * @param message
     * @param <T>
     * @return
     */
    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    /**
     * 自定义状态码和错误信息
     * @param code
     * @param message
     * @param <T>
     * @return
     */
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}


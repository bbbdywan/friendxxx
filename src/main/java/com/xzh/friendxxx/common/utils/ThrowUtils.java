package com.xzh.friendxxx.common.utils;

import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.exception.ErrorCode;

/**
 * 异常处理工具类
 * @author ForeverGreenDam
 */
public class ThrowUtils {
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition,new BusinessException(errorCode));
    }
    public static void throwIf(boolean condition,ErrorCode errorCode,String message) {
        throwIf(condition,new BusinessException(errorCode,message));
    }
}

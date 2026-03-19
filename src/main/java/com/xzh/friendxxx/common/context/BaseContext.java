package com.xzh.friendxxx.common.context;

/**
 * BaseContext is a utility class that provides a thread-local storage for the current user ID.
 * @author ForeverGreenDam
 */
public class BaseContext {

    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }

}

package com.xzh.friendxxx.ai.model;

/**
 * 长期记忆类型常量。
 */
public final class MemoryType {

    public static final String PROFILE = "PROFILE";
    public static final String PREFERENCE = "PREFERENCE";
    public static final String RELATIONSHIP = "RELATIONSHIP";
    public static final String EVENT = "EVENT";
    public static final String GOAL = "GOAL";
    public static final String SHARED = "SHARED";
    public static final String BOUNDARY = "BOUNDARY";

    private MemoryType() {
    }

    public static boolean isAllowed(String type) {
        return PROFILE.equals(type) || PREFERENCE.equals(type) || RELATIONSHIP.equals(type)
                || EVENT.equals(type) || GOAL.equals(type) || SHARED.equals(type) || BOUNDARY.equals(type);
    }
}

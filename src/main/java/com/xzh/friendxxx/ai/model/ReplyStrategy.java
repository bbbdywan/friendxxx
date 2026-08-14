package com.xzh.friendxxx.ai.model;

/**
 * 允许的回复策略常量（对应规格 9.2）。
 */
public final class ReplyStrategy {

    public static final String LISTEN = "LISTEN";
    public static final String VALIDATE = "VALIDATE";
    public static final String ASK = "ASK";
    public static final String PLAYFUL = "PLAYFUL";
    public static final String CELEBRATE = "CELEBRATE";
    public static final String GENTLE_ADVICE = "GENTLE_ADVICE";
    public static final String DIRECT_ADVICE = "DIRECT_ADVICE";
    public static final String CLARIFY = "CLARIFY";
    public static final String DEESCALATE = "DEESCALATE";
    public static final String VALIDATE_THEN_GENTLY_ASK = "VALIDATE_THEN_GENTLY_ASK";

    private ReplyStrategy() {
    }

    public static boolean isAllowed(String strategy) {
        return strategy != null && (LISTEN.equals(strategy) || VALIDATE.equals(strategy) || ASK.equals(strategy)
                || PLAYFUL.equals(strategy) || CELEBRATE.equals(strategy) || GENTLE_ADVICE.equals(strategy)
                || DIRECT_ADVICE.equals(strategy) || CLARIFY.equals(strategy) || DEESCALATE.equals(strategy)
                || VALIDATE_THEN_GENTLY_ASK.equals(strategy));
    }
}

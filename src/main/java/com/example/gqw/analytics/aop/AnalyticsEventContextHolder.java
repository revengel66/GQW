package com.example.gqw.analytics.aop;

public final class AnalyticsEventContextHolder {

    private static final ThreadLocal<AnalyticsEventContext> CONTEXT = new ThreadLocal<>();

    private AnalyticsEventContextHolder() {
    }

    public static AnalyticsEventContext get() {
        return CONTEXT.get();
    }

    public static void set(AnalyticsEventContext context) {
        CONTEXT.set(context);
    }

    public static Long currentStageId() {
        AnalyticsEventContext context = CONTEXT.get();
        if (context == null) {
            return null;
        }
        return context.currentStageId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

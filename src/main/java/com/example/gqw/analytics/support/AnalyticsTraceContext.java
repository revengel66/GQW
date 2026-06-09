package com.example.gqw.analytics.support;

public final class AnalyticsTraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_REQUEST_ATTRIBUTE = "traceId";
    public static final String REQUEST_STARTED_AT_ATTRIBUTE = "requestStartedAt";
    public static final String REQUEST_DURATION_MS_ATTRIBUTE = "requestDurationMs";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private AnalyticsTraceContext() {
    }
}

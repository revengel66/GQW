package com.example.gqw.analytics.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record FrontendAnalyticsIngestRequest(
    List<FrontendEventPayload> events
) {

    public record FrontendEventPayload(
        String code,
        String parentEventUid,
        String moduleCode,
        String pagePath,
        String requestPath,
        String httpMethod,
        String traceId,
        Integer statusCode,
        Boolean error,
        String errorMessage,
        Map<String, BigDecimal> metricsNum,
        Map<String, String> metricsText
    ) {
    }
}

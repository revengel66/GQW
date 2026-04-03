package com.example.gqw.analytics.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface AnalyticsTrackingApi {

    UUID startEvent(String eventTypeCode, Long userId, String sessionId, String requestPath, String httpMethod, String traceId);

    void addAttribute(UUID eventUid, String attributeTypeCode, String value);

    void addAttributeJson(UUID eventUid, String attributeTypeCode, String valueJson);

    Long startStage(UUID eventUid, String stageTypeCode, int stageOrder);

    void recordMetricNum(Long stageId, String metricTypeCode, BigDecimal value, String unit);

    void recordMetricText(Long stageId, String metricTypeCode, String value, String unit);

    void finishStageSuccess(Long stageId);

    void finishStageError(Long stageId, String errorMessage);

    void finishEventSuccess(UUID eventUid, Integer statusCode);

    void finishEventError(UUID eventUid, Integer statusCode, String errorMessage);
}


package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsStage;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsTrackerFacade implements AnalyticsTrackingApi {

    private final AnalyticsEventService eventService;
    private final AnalyticsStageService stageService;
    private final AnalyticsEventAttributeService eventAttributeService;
    private final AnalyticsStageMetricService stageMetricService;

    public AnalyticsTrackerFacade(
        AnalyticsEventService eventService,
        AnalyticsStageService stageService,
        AnalyticsEventAttributeService eventAttributeService,
        AnalyticsStageMetricService stageMetricService
    ) {
        this.eventService = eventService;
        this.stageService = stageService;
        this.eventAttributeService = eventAttributeService;
        this.stageMetricService = stageMetricService;
    }

    @Override
    @Transactional
    public UUID startEvent(String eventTypeCode, Long userId, String sessionId, String requestPath, String httpMethod, String traceId) {
        AnalyticsEvent event = eventService.createEvent(eventTypeCode, userId, sessionId, requestPath, httpMethod, traceId);
        return event.getEventUid();
    }

    @Override
    @Transactional
    public void addAttribute(UUID eventUid, String attributeTypeCode, String value) {
        AnalyticsEvent event = eventService.findByEventUid(eventUid);
        eventAttributeService.addTextAttribute(event.getId(), attributeTypeCode, value);
    }

    @Override
    @Transactional
    public void addAttributeJson(UUID eventUid, String attributeTypeCode, String valueJson) {
        AnalyticsEvent event = eventService.findByEventUid(eventUid);
        eventAttributeService.addJsonAttribute(event.getId(), attributeTypeCode, valueJson);
    }

    @Override
    @Transactional
    public Long startStage(UUID eventUid, String stageTypeCode, int stageOrder) {
        AnalyticsEvent event = eventService.findByEventUid(eventUid);
        AnalyticsStage stage = stageService.createStage(event, stageTypeCode, stageOrder);
        return stage.getId();
    }

    @Override
    @Transactional
    public void recordMetricNum(Long stageId, String metricTypeCode, BigDecimal value, String unit) {
        stageMetricService.recordMetricNum(stageId, metricTypeCode, value, unit);
    }

    @Override
    @Transactional
    public void recordMetricText(Long stageId, String metricTypeCode, String value, String unit) {
        stageMetricService.recordMetricText(stageId, metricTypeCode, value, unit);
    }

    @Override
    @Transactional
    public void finishStageSuccess(Long stageId) {
        stageService.finishStageSuccess(stageId);
    }

    @Override
    @Transactional
    public void finishStageError(Long stageId, String errorMessage) {
        stageService.finishStageError(stageId, errorMessage);
    }

    @Override
    @Transactional
    public void finishEventSuccess(UUID eventUid, Integer statusCode) {
        eventService.finishEventSuccess(eventUid, statusCode);
    }

    @Override
    @Transactional
    public void finishEventError(UUID eventUid, Integer statusCode, String errorMessage) {
        eventService.finishEventError(eventUid, statusCode, errorMessage);
    }
}


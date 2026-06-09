package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsTrackerFacade implements AnalyticsTrackingApi {

    private final AnalyticsEventService eventService;
    private final AnalyticsStageService stageService;
    private final AnalyticsEventAttributeService eventAttributeService;
    private final AnalyticsStageMetricService stageMetricService;
    private final AnalyticsInstrumentationPolicy instrumentationPolicy;
    private final AnalyticsAsyncWriteBuffer asyncWriteBuffer;
    private final AnalyticsEventSnapshotBuffer snapshotBuffer;

    public AnalyticsTrackerFacade(
        AnalyticsEventService eventService,
        AnalyticsStageService stageService,
        AnalyticsEventAttributeService eventAttributeService,
        AnalyticsStageMetricService stageMetricService,
        AnalyticsInstrumentationPolicy instrumentationPolicy,
        AnalyticsAsyncWriteBuffer asyncWriteBuffer,
        AnalyticsEventSnapshotBuffer snapshotBuffer
    ) {
        this.eventService = eventService;
        this.stageService = stageService;
        this.eventAttributeService = eventAttributeService;
        this.stageMetricService = stageMetricService;
        this.instrumentationPolicy = instrumentationPolicy;
        this.asyncWriteBuffer = asyncWriteBuffer;
        this.snapshotBuffer = snapshotBuffer;
    }

    @Override
    public boolean isSnapshotEnabled() {
        return snapshotBuffer.isEnabled();
    }

    @Override
    public UUID startEvent(String eventTypeCode, Long userId, String sessionId, String requestPath, String httpMethod, String traceId) {
        if (!instrumentationPolicy.isEnabled()) {
            return null;
        }
        if (snapshotBuffer.isEnabled()) {
            return snapshotBuffer.startEvent(eventTypeCode, userId, sessionId, requestPath, httpMethod, traceId);
        }
        if (asyncWriteBuffer.isEnabled()) {
            return asyncWriteBuffer.startEvent(eventTypeCode, userId, sessionId, requestPath, httpMethod, traceId);
        }
        AnalyticsEvent event = eventService.createEvent(eventTypeCode, userId, sessionId, requestPath, httpMethod, traceId);
        return event.getEventUid();
    }

    @Override
    public void setEventStartedAtIfEarlier(UUID eventUid, Instant startedAt) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            snapshotBuffer.setEventStartedAtIfEarlier(eventUid, startedAt);
            return;
        }
        if (asyncWriteBuffer.isEnabled()) {
            asyncWriteBuffer.setEventStartedAtIfEarlier(eventUid, startedAt);
            return;
        }
        eventService.setStartedAtIfEarlier(eventUid, startedAt);
    }

    @Override
    public void extendEventDurationIfLater(UUID eventUid, Instant endedAtCandidate) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            snapshotBuffer.extendEventDurationIfLater(eventUid, endedAtCandidate);
            return;
        }
        if (asyncWriteBuffer.isEnabled()) {
            asyncWriteBuffer.extendEventDurationIfLater(eventUid, endedAtCandidate);
            return;
        }
        eventService.extendEventDurationIfLater(eventUid, endedAtCandidate);
    }

    @Override
    public String resolveEventModuleCode(UUID eventUid) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return null;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            return snapshotBuffer.resolveEventModuleCode(eventUid);
        }
        if (asyncWriteBuffer.isEnabled()) {
            return null;
        }
        AnalyticsEvent event = eventService.findByEventUid(eventUid);
        return event.getModuleCode();
    }

    @Override
    public void addAttribute(UUID eventUid, String attributeTypeCode, String value) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            snapshotBuffer.addAttribute(eventUid, attributeTypeCode, value);
            return;
        }
        if (asyncWriteBuffer.isEnabled()) {
            asyncWriteBuffer.addAttribute(eventUid, attributeTypeCode, value);
            return;
        }
        AnalyticsEvent event = eventService.findByEventUid(eventUid);
        eventAttributeService.addTextAttribute(event.getId(), attributeTypeCode, value);
    }

    @Override
    public void addAttributeJson(UUID eventUid, String attributeTypeCode, String valueJson) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            snapshotBuffer.addAttributeJson(eventUid, attributeTypeCode, valueJson);
            return;
        }
        if (asyncWriteBuffer.isEnabled()) {
            asyncWriteBuffer.addAttributeJson(eventUid, attributeTypeCode, valueJson);
            return;
        }
        AnalyticsEvent event = eventService.findByEventUid(eventUid);
        eventAttributeService.addJsonAttribute(event.getId(), attributeTypeCode, valueJson);
    }

    @Override
    public Long startStage(UUID eventUid, String stageTypeCode, int stageOrder) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return null;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            return snapshotBuffer.startStage(eventUid, stageTypeCode, stageOrder);
        }
        if (asyncWriteBuffer.isEnabled()) {
            return asyncWriteBuffer.startStage(eventUid, stageTypeCode, stageOrder);
        }
        AnalyticsEvent event = eventService.findByEventUid(eventUid);
        AnalyticsStage stage = stageService.createStage(event, stageTypeCode, stageOrder);
        return stage.getId();
    }

    @Override
    public void recordMetricNum(Long stageId, String metricTypeCode, BigDecimal value, String unit) {
        if (!instrumentationPolicy.isEnabled() || stageId == null) {
            return;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            snapshotBuffer.recordMetricNum(stageId, metricTypeCode, value, unit);
            return;
        }
        if (asyncWriteBuffer.isEnabled()) {
            asyncWriteBuffer.recordMetricNum(stageId, metricTypeCode, value, unit);
            return;
        }
        stageMetricService.recordMetricNum(stageId, metricTypeCode, value, unit);
    }

    @Override
    public void recordMetricText(Long stageId, String metricTypeCode, String value, String unit) {
        if (!instrumentationPolicy.isEnabled() || stageId == null) {
            return;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            snapshotBuffer.recordMetricText(stageId, metricTypeCode, value, unit);
            return;
        }
        if (asyncWriteBuffer.isEnabled()) {
            asyncWriteBuffer.recordMetricText(stageId, metricTypeCode, value, unit);
            return;
        }
        stageMetricService.recordMetricText(stageId, metricTypeCode, value, unit);
    }

    @Override
    public void finishStageSuccess(Long stageId) {
        if (!instrumentationPolicy.isEnabled() || stageId == null) {
            return;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            snapshotBuffer.finishStageSuccess(stageId);
            return;
        }
        if (asyncWriteBuffer.isEnabled()) {
            asyncWriteBuffer.finishStageSuccess(stageId);
            return;
        }
        stageService.finishStageSuccess(stageId);
    }

    @Override
    public void finishStageError(Long stageId, String errorMessage) {
        if (!instrumentationPolicy.isEnabled() || stageId == null) {
            return;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            snapshotBuffer.finishStageError(stageId, errorMessage);
            return;
        }
        if (asyncWriteBuffer.isEnabled()) {
            asyncWriteBuffer.finishStageError(stageId, errorMessage);
            return;
        }
        stageService.finishStageError(stageId, errorMessage);
    }

    @Override
    public void markStageLogWindow(Long stageId, Instant logStartedAt, Instant logEndedAt) {
        if (!instrumentationPolicy.isEnabled() || stageId == null) {
            return;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            snapshotBuffer.markStageLogWindow(stageId, logStartedAt, logEndedAt);
            return;
        }
        if (asyncWriteBuffer.isEnabled()) {
            asyncWriteBuffer.markStageLogWindow(stageId, logStartedAt, logEndedAt);
            return;
        }
        stageService.markStageLogWindow(stageId, logStartedAt, logEndedAt);
    }

    @Override
    public void finishEventSuccess(UUID eventUid, Integer statusCode) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            snapshotBuffer.finishEventSuccess(eventUid, statusCode);
            return;
        }
        if (asyncWriteBuffer.isEnabled()) {
            asyncWriteBuffer.finishEventSuccess(eventUid, statusCode);
            return;
        }
        eventService.finishEventSuccess(eventUid, statusCode);
    }

    @Override
    public void finishEventError(UUID eventUid, Integer statusCode, String errorMessage) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        if (snapshotBuffer.isEnabled() && snapshotBuffer.hasCurrentSnapshot()) {
            snapshotBuffer.finishEventError(eventUid, statusCode, errorMessage);
            return;
        }
        if (asyncWriteBuffer.isEnabled()) {
            asyncWriteBuffer.finishEventError(eventUid, statusCode, errorMessage);
            return;
        }
        eventService.finishEventError(eventUid, statusCode, errorMessage);
    }
}


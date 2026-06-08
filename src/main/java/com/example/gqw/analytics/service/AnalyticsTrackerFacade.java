package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsTrackerFacade implements AnalyticsTrackingApi {

    private final AnalyticsEventService eventService;
    private final AnalyticsStageService stageService;
    private final AnalyticsEventAttributeService eventAttributeService;
    private final AnalyticsStageMetricService stageMetricService;
    private final AnalyticsInstrumentationPolicy instrumentationPolicy;

    public AnalyticsTrackerFacade(
        AnalyticsEventService eventService,
        AnalyticsStageService stageService,
        AnalyticsEventAttributeService eventAttributeService,
        AnalyticsStageMetricService stageMetricService,
        AnalyticsInstrumentationPolicy instrumentationPolicy
    ) {
        this.eventService = eventService;
        this.stageService = stageService;
        this.eventAttributeService = eventAttributeService;
        this.stageMetricService = stageMetricService;
        this.instrumentationPolicy = instrumentationPolicy;
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public UUID startEvent(String eventTypeCode, Long userId, String sessionId, String requestPath, String httpMethod, String traceId) {
        if (!instrumentationPolicy.isEnabled()) {
            return null;
        }
        AnalyticsEvent event = eventService.createEvent(eventTypeCode, userId, sessionId, requestPath, httpMethod, traceId);
        return event.getEventUid();
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void setEventStartedAtIfEarlier(UUID eventUid, Instant startedAt) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        eventService.setStartedAtIfEarlier(eventUid, startedAt);
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void extendEventDurationIfLater(UUID eventUid, Instant endedAtCandidate) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        eventService.extendEventDurationIfLater(eventUid, endedAtCandidate);
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public String resolveEventModuleCode(UUID eventUid) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return null;
        }
        AnalyticsEvent event = eventService.findByEventUid(eventUid);
        return event.getModuleCode();
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void addAttribute(UUID eventUid, String attributeTypeCode, String value) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        AnalyticsEvent event = eventService.findByEventUid(eventUid);
        eventAttributeService.addTextAttribute(event.getId(), attributeTypeCode, value);
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void addAttributeJson(UUID eventUid, String attributeTypeCode, String valueJson) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        AnalyticsEvent event = eventService.findByEventUid(eventUid);
        eventAttributeService.addJsonAttribute(event.getId(), attributeTypeCode, valueJson);
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public Long startStage(UUID eventUid, String stageTypeCode, int stageOrder) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return null;
        }
        AnalyticsEvent event = eventService.findByEventUid(eventUid);
        AnalyticsStage stage = stageService.createStage(event, stageTypeCode, stageOrder);
        return stage.getId();
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void recordMetricNum(Long stageId, String metricTypeCode, BigDecimal value, String unit) {
        if (!instrumentationPolicy.isEnabled() || stageId == null) {
            return;
        }
        stageMetricService.recordMetricNum(stageId, metricTypeCode, value, unit);
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void recordMetricText(Long stageId, String metricTypeCode, String value, String unit) {
        if (!instrumentationPolicy.isEnabled() || stageId == null) {
            return;
        }
        stageMetricService.recordMetricText(stageId, metricTypeCode, value, unit);
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void finishStageSuccess(Long stageId) {
        if (!instrumentationPolicy.isEnabled() || stageId == null) {
            return;
        }
        stageService.finishStageSuccess(stageId);
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void finishStageError(Long stageId, String errorMessage) {
        if (!instrumentationPolicy.isEnabled() || stageId == null) {
            return;
        }
        stageService.finishStageError(stageId, errorMessage);
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void markStageLogWindow(Long stageId, Instant logStartedAt, Instant logEndedAt) {
        if (!instrumentationPolicy.isEnabled() || stageId == null) {
            return;
        }
        stageService.markStageLogWindow(stageId, logStartedAt, logEndedAt);
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void finishEventSuccess(UUID eventUid, Integer statusCode) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        eventService.finishEventSuccess(eventUid, statusCode);
    }

    @Override
    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void finishEventError(UUID eventUid, Integer statusCode, String errorMessage) {
        if (!instrumentationPolicy.isEnabled() || eventUid == null) {
            return;
        }
        eventService.finishEventError(eventUid, statusCode, errorMessage);
    }
}


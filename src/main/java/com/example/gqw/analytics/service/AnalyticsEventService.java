package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.aop.AnalyticsEventAspect;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.analytics.repository.EventTypeRepository;
import com.example.gqw.analytics.repository.ModuleTypeRepository;
import com.example.gqw.analytics.entity.ModuleType;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsEventService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventService.class);

    private final AnalyticsEventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final ModuleTypeRepository moduleTypeRepository;
    private final AnalyticsCodeResolverService codeResolverService;
    private final AnalyticsSystemEventClassifier systemEventClassifier;
    private final AnalyticsLoggingPolicy loggingPolicy;
    private final AnalyticsStrictWarningEventService strictWarningEventService;

    public AnalyticsEventService(
        AnalyticsEventRepository eventRepository,
        EventTypeRepository eventTypeRepository,
        ModuleTypeRepository moduleTypeRepository,
        AnalyticsCodeResolverService codeResolverService,
        AnalyticsSystemEventClassifier systemEventClassifier,
        AnalyticsLoggingPolicy loggingPolicy,
        AnalyticsStrictWarningEventService strictWarningEventService
    ) {
        this.eventRepository = eventRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.moduleTypeRepository = moduleTypeRepository;
        this.codeResolverService = codeResolverService;
        this.systemEventClassifier = systemEventClassifier;
        this.loggingPolicy = loggingPolicy;
        this.strictWarningEventService = strictWarningEventService;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public AnalyticsEvent createEvent(
        String eventTypeCode,
        Long userId,
        String sessionId,
        String requestPath,
        String httpMethod,
        String traceId
    ) {
        return createEvent(UUID.randomUUID(), eventTypeCode, userId, sessionId, requestPath, httpMethod, traceId);
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public AnalyticsEvent createEvent(
        UUID eventUid,
        String eventTypeCode,
        Long userId,
        String sessionId,
        String requestPath,
        String httpMethod,
        String traceId
    ) {
        return createEvent(eventUid, eventTypeCode, userId, sessionId, requestPath, httpMethod, traceId, Instant.now());
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public AnalyticsEvent createEvent(
        UUID eventUid,
        String eventTypeCode,
        Long userId,
        String sessionId,
        String requestPath,
        String httpMethod,
        String traceId,
        Instant startedAt
    ) {
        String resolvedCode = codeResolverService.resolveEventTypeCode(eventTypeCode);
        var type = eventTypeRepository.findById(resolvedCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + resolvedCode));
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            throw new IllegalArgumentException("Inactive event type: " + resolvedCode);
        }
        markSystemEventTypeIfNeeded(type, requestPath, null);
        alignEventTypeModuleIfNeeded(type, resolvedCode);

        AnalyticsEvent event = new AnalyticsEvent();
        event.setEventUid(eventUid == null ? UUID.randomUUID() : eventUid);
        event.setEventTypeCode(resolvedCode);
        String moduleCode = normalizeModuleCode(type.getModuleCode());
        validateModuleType(resolvedCode, moduleCode);
        event.setModuleCode(moduleCode);
        event.setUserId(userId);
        event.setSessionId(sessionId);
        event.setRequestPath(requestPath);
        event.setHttpMethod(httpMethod);
        event.setTraceId(traceId);
        event.setStartedAt(startedAt == null ? Instant.now() : startedAt);
        event.setIsError(false);
        return eventRepository.save(event);
    }

    private void validateModuleType(String eventTypeCode, String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) {
            logDictionaryMismatch(moduleCode, eventTypeCode, "Blank module type");
            throw new IllegalArgumentException("Blank module type");
        }
        ModuleType moduleType = moduleTypeRepository.findById(moduleCode).orElse(null);
        if (moduleType == null) {
            logDictionaryMismatch(moduleCode, eventTypeCode, "Unknown module type");
            throw new IllegalArgumentException("Unknown module type: " + moduleCode);
        }
        if (!Boolean.TRUE.equals(moduleType.getIsActive())) {
            logDictionaryMismatch(moduleCode, eventTypeCode, "Inactive module type");
            throw new IllegalArgumentException("Inactive module type: " + moduleCode);
        }
    }

    private void logDictionaryMismatch(String moduleCode, String eventTypeCode, String reason) {
        if (loggingPolicy.isStrictWarningsEnabled()) {
            log.warn(
                "Analytics dictionary mismatch: module={} eventType={} reason={}",
                moduleCode,
                eventTypeCode,
                reason
            );
        }
        strictWarningEventService.record(
            "module",
            moduleCode,
            reason,
            AnalyticsEventService.class.getSimpleName(),
            "validateModuleType",
            null,
            null,
            null
        );
    }

    private void markSystemEventTypeIfNeeded(EventType type, String requestPath, Integer statusCode) {
        if (type == null || Boolean.TRUE.equals(type.getIsSystem())) {
            return;
        }
        if (!systemEventClassifier.isSystemEvent(type.getCode(), type.getName(), requestPath, statusCode)) {
            return;
        }
        type.setIsSystem(true);
        eventTypeRepository.save(type);
    }

    private void alignEventTypeModuleIfNeeded(EventType type, String eventTypeCode) {
        if (type == null || eventTypeCode == null || eventTypeCode.isBlank()) {
            return;
        }
        if (!eventTypeCode.toUpperCase().startsWith("ADMIN_") && !eventTypeCode.toUpperCase().startsWith("SHOP_")) {
            return;
        }
        String targetModuleCode = resolvePreferredModuleCode(eventTypeCode);
        if (targetModuleCode == null || targetModuleCode.isBlank()) {
            return;
        }
        if (!moduleExists(targetModuleCode)) {
            return;
        }
        String currentModuleCode = type.getModuleCode();
        if (targetModuleCode.equalsIgnoreCase(currentModuleCode)) {
            return;
        }
        type.setModuleCode(targetModuleCode);
        eventTypeRepository.save(type);
        eventRepository.bulkUpdateModuleCodeByEventTypeCode(eventTypeCode, targetModuleCode);
    }

    private String inferModuleCode(String code) {
        return EventType.DEFAULT_MODULE_CODE;
    }

    private String resolvePreferredModuleCode(String code) {
        String mdcModuleCode = normalizeModuleCode(MDC.get(AnalyticsEventAspect.APP_MODULE_MDC_KEY));
        if (mdcModuleCode != null && moduleExists(mdcModuleCode)) {
            return mdcModuleCode;
        }
        return inferModuleCode(code);
    }

    private String humanizeCode(String code) {
        if (code == null || code.isBlank()) {
            return "Событие";
        }
        String normalized = code.trim().replaceAll("_+", "_");
        String[] parts = normalized.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            String lower = part.toLowerCase();
            result.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                result.append(lower.substring(1));
            }
        }
        return result.isEmpty() ? normalized : result.toString();
    }

    private boolean moduleExists(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return moduleTypeRepository.findById(code)
            .map(ModuleType::getIsActive)
            .filter(Boolean.TRUE::equals)
            .isPresent();
    }

    private String normalizeModuleCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public AnalyticsEvent findByEventUid(UUID eventUid) {
        return eventRepository.findByEventUid(eventUid)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventUid));
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void setStartedAtIfEarlier(UUID eventUid, Instant startedAt) {
        if (startedAt == null) {
            return;
        }
        AnalyticsEvent event = findByEventUid(eventUid);
        Instant currentStartedAt = event.getStartedAt();
        if (currentStartedAt == null || startedAt.isBefore(currentStartedAt)) {
            event.setStartedAt(startedAt);
            if (event.getEndedAt() != null) {
                event.setDurationMs((int) Duration.between(event.getStartedAt(), event.getEndedAt()).toMillis());
            }
            eventRepository.save(event);
        }
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void extendEventDurationIfLater(UUID eventUid, Instant endedAtCandidate) {
        if (endedAtCandidate == null) {
            return;
        }
        AnalyticsEvent event = findByEventUid(eventUid);
        if (event.getStartedAt() == null) {
            return;
        }
        Instant currentEndedAt = event.getEndedAt();
        if (currentEndedAt != null && !endedAtCandidate.isAfter(currentEndedAt)) {
            return;
        }
        event.setEndedAt(endedAtCandidate);
        event.setDurationMs((int) Duration.between(event.getStartedAt(), endedAtCandidate).toMillis());
        eventRepository.save(event);
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void finishEventSuccess(UUID eventUid, Integer statusCode) {
        finishEventSuccessAt(eventUid, statusCode, Instant.now());
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void finishEventSuccessAt(UUID eventUid, Integer statusCode, Instant endedAt) {
        AnalyticsEvent event = findByEventUid(eventUid);
        Instant finishedAt = endedAt == null ? Instant.now() : endedAt;
        event.setEndedAt(finishedAt);
        event.setStatusCode(statusCode);
        event.setIsError(false);
        event.setDurationMs((int) Duration.between(event.getStartedAt(), finishedAt).toMillis());
        markStoredEventTypeIfNeeded(event);
        eventRepository.save(event);
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void finishEventError(UUID eventUid, Integer statusCode, String errorMessage) {
        finishEventErrorAt(eventUid, statusCode, errorMessage, Instant.now());
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void finishEventErrorAt(UUID eventUid, Integer statusCode, String errorMessage, Instant endedAt) {
        AnalyticsEvent event = findByEventUid(eventUid);
        Instant finishedAt = endedAt == null ? Instant.now() : endedAt;
        event.setEndedAt(finishedAt);
        event.setStatusCode(statusCode);
        event.setIsError(true);
        event.setErrorMessage(errorMessage);
        event.setDurationMs((int) Duration.between(event.getStartedAt(), finishedAt).toMillis());
        markStoredEventTypeIfNeeded(event);
        eventRepository.save(event);
        log.warn(
            "Analytics error event: uid='{}', code='{}', module='{}', status={}, traceId='{}', path='{}', method='{}', message='{}'",
            event.getEventUid(),
            event.getEventTypeCode(),
            event.getModuleCode(),
            event.getStatusCode(),
            event.getTraceId(),
            event.getRequestPath(),
            event.getHttpMethod(),
            errorMessage == null ? "" : errorMessage
        );
    }

    private void markStoredEventTypeIfNeeded(AnalyticsEvent event) {
        if (event == null || event.getEventTypeCode() == null || event.getEventTypeCode().isBlank()) {
            return;
        }
        eventTypeRepository.findById(event.getEventTypeCode())
            .ifPresent(type -> markSystemEventTypeIfNeeded(type, event.getRequestPath(), event.getStatusCode()));
    }
}


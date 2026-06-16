package com.example.gqw.analytics.service;

import com.example.gqw.analytics.config.AnalyticsStrictWarningDictionaryConfig;
import com.example.gqw.analytics.aop.AnalyticsEventAspect;
import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.repository.AnalyticsEventAttributeRepository;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.analytics.support.AnalyticsTraceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsStrictWarningEventService {

    private static final int MAX_TEXT_LENGTH = 1900;
    private static final Logger log = LoggerFactory.getLogger(AnalyticsStrictWarningEventService.class);

    private final AnalyticsEventRepository eventRepository;
    private final AnalyticsEventAttributeRepository attributeRepository;
    private final AnalyticsRuntimeSettingsService runtimeSettingsService;
    private final AnalyticsInstrumentationPolicy instrumentationPolicy;

    public AnalyticsStrictWarningEventService(
        AnalyticsEventRepository eventRepository,
        AnalyticsEventAttributeRepository attributeRepository,
        AnalyticsRuntimeSettingsService runtimeSettingsService,
        AnalyticsInstrumentationPolicy instrumentationPolicy
    ) {
        this.eventRepository = eventRepository;
        this.attributeRepository = attributeRepository;
        this.runtimeSettingsService = runtimeSettingsService;
        this.instrumentationPolicy = instrumentationPolicy;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void record(
        String warningType,
        String code,
        String reason,
        String sourceClass,
        String sourceMethod,
        String path,
        String traceId,
        String eventUid,
        Long stageId
    ) {
        if (!instrumentationPolicy.isEnabled()
            || !runtimeSettingsService.getBoolean(
                AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_ENABLED,
                true
            )
            || !runtimeSettingsService.getBoolean(
                AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_STRICT_WARNINGS_ENABLED,
                true
            )) {
            return;
        }
        try {
            AnalyticsEvent event = new AnalyticsEvent();
            event.setEventUid(UUID.randomUUID());
            event.setEventTypeCode(AnalyticsStrictWarningDictionaryConfig.STRICT_WARNING_EVENT_CODE);
            event.setModuleCode(EventType.DEFAULT_MODULE_CODE);
            event.setRequestPath(blankToNull(path));
            event.setHttpMethod("INTERNAL");
            event.setTraceId(blankToNull(traceId));
            event.setStatusCode(0);
            event.setIsError(true);
            event.setErrorMessage(truncate(reason));
            event.setStartedAt(Instant.now());
            event.setEndedAt(event.getStartedAt());
            event.setDurationMs(0);
            AnalyticsEvent saved = eventRepository.save(event);
            attributeRepository.saveAll(attributes(saved.getId(), warningType, code, reason, sourceClass, sourceMethod, eventUid, stageId));
            logWarning(saved, warningType, code, reason, sourceClass, sourceMethod);
        } catch (RuntimeException ignored) {
            // Strict diagnostics must not create a second failure path inside instrumentation.
        }
    }

    private static void logWarning(
        AnalyticsEvent event,
        String warningType,
        String code,
        String reason,
        String sourceClass,
        String sourceMethod
    ) {
        String previousEventUid = MDC.get(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY);
        String previousModule = MDC.get(AnalyticsEventAspect.APP_MODULE_MDC_KEY);
        String previousTraceId = MDC.get(AnalyticsTraceContext.TRACE_ID_MDC_KEY);
        try {
            MDC.put(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY, event.getEventUid().toString());
            MDC.put(AnalyticsEventAspect.APP_MODULE_MDC_KEY, EventType.DEFAULT_MODULE_CODE);
            putOrRemove(AnalyticsTraceContext.TRACE_ID_MDC_KEY, event.getTraceId());
            log.warn(
                "Analytics strict warning: type={}, code={}, source={}, reason={}",
                warningType,
                code,
                source(sourceClass, sourceMethod),
                truncate(reason)
            );
        } finally {
            putOrRemove(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY, previousEventUid);
            putOrRemove(AnalyticsEventAspect.APP_MODULE_MDC_KEY, previousModule);
            putOrRemove(AnalyticsTraceContext.TRACE_ID_MDC_KEY, previousTraceId);
        }
    }

    private static void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    public void record(
        String warningType,
        String code,
        String reason,
        String sourceClass,
        String sourceMethod,
        String path,
        String eventUid,
        Long stageId
    ) {
        record(warningType, code, reason, sourceClass, sourceMethod, path, safeMdc(AnalyticsTraceContext.TRACE_ID_MDC_KEY), eventUid, stageId);
    }

    private static List<AnalyticsEventAttribute> attributes(
        Long eventId,
        String warningType,
        String code,
        String reason,
        String sourceClass,
        String sourceMethod,
        String eventUid,
        Long stageId
    ) {
        List<AnalyticsEventAttribute> attributes = new ArrayList<>();
        add(attributes, eventId, AnalyticsStrictWarningDictionaryConfig.ATTR_WARNING_TYPE, warningType);
        add(attributes, eventId, AnalyticsStrictWarningDictionaryConfig.ATTR_WARNING_CODE, code);
        add(attributes, eventId, AnalyticsStrictWarningDictionaryConfig.ATTR_WARNING_REASON, reason);
        add(attributes, eventId, AnalyticsStrictWarningDictionaryConfig.ATTR_WARNING_SOURCE, source(sourceClass, sourceMethod));
        add(attributes, eventId, AnalyticsStrictWarningDictionaryConfig.ATTR_WARNING_EVENT_UID, eventUid);
        add(attributes, eventId, AnalyticsStrictWarningDictionaryConfig.ATTR_WARNING_STAGE_ID, stageId == null ? null : String.valueOf(stageId));
        return attributes;
    }

    private static void add(List<AnalyticsEventAttribute> attributes, Long eventId, String code, String value) {
        String normalized = blankToNull(value);
        if (eventId == null || normalized == null) {
            return;
        }
        AnalyticsEventAttribute attribute = new AnalyticsEventAttribute();
        attribute.setEventId(eventId);
        attribute.setAttributeTypeCode(code);
        attribute.setAttrValue(truncate(normalized));
        attribute.setCreatedAt(Instant.now());
        attributes.add(attribute);
    }

    private static String source(String sourceClass, String sourceMethod) {
        String cls = blankToNull(sourceClass);
        String method = blankToNull(sourceMethod);
        if (cls == null) {
            return method;
        }
        if (method == null) {
            return cls;
        }
        return cls + "." + method;
    }

    private static String safeMdc(String key) {
        String value = MDC.get(key);
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= MAX_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TEXT_LENGTH - 3) + "...";
    }
}

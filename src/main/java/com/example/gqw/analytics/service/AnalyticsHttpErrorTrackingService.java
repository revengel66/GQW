package com.example.gqw.analytics.service;

import com.example.gqw.analytics.aop.AnalyticsEventAspect;
import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.analytics.support.AnalyticsTraceContext;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@Service
public class AnalyticsHttpErrorTrackingService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsHttpErrorTrackingService.class);
    public static final String ERROR_EVENT_TRACKED_REQUEST_ATTRIBUTE = "analyticsErrorEventTracked";
    public static final String ERROR_THROWABLE_REQUEST_ATTRIBUTE = "analyticsErrorThrowable";
    public static final String ERROR_LOG_WINDOW_STARTED_AT_REQUEST_ATTRIBUTE = "analyticsErrorLogWindowStartedAt";
    public static final String ERROR_LOG_WINDOW_ENDED_AT_REQUEST_ATTRIBUTE = "analyticsErrorLogWindowEndedAt";

    private static final String FALLBACK_ERROR_EVENT_CODE = "HTTP_REQUEST_ERROR";
    private static final long DEDUP_WINDOW_MS = 15_000L;
    private static final int MAX_PENDING_FALLBACK_ERRORS = 2_000;
    private static final int MAX_PENDING_FLUSH_PER_CYCLE = 100;
    private static final long PENDING_RETRY_BASE_MS = 2_000L;
    private static final long PENDING_RETRY_MAX_MS = 60_000L;

    private static final Map<String, Long> RECENT_FALLBACK_ERRORS = new ConcurrentHashMap<>();
    private static final Map<String, PendingFallbackError> PENDING_FALLBACK_ERRORS = new ConcurrentHashMap<>();
    private static final Deque<String> PENDING_FALLBACK_ORDER = new ConcurrentLinkedDeque<>();
    private static final Set<String> PENDING_FLUSH_IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private final AnalyticsInstrumentationPolicy instrumentationPolicy;
    private final AnalyticsScheduledJobsPolicy scheduledJobsPolicy;
    private final AnalyticsTrackingApi analyticsTrackingApi;

    public AnalyticsHttpErrorTrackingService(
        AnalyticsInstrumentationPolicy instrumentationPolicy,
        AnalyticsScheduledJobsPolicy scheduledJobsPolicy,
        AnalyticsTrackingApi analyticsTrackingApi
    ) {
        this.instrumentationPolicy = instrumentationPolicy;
        this.scheduledJobsPolicy = scheduledJobsPolicy;
        this.analyticsTrackingApi = analyticsTrackingApi;
    }

    public void trackIfMissing(HttpServletRequest request, int statusCode, Throwable throwable) {
        if (!instrumentationPolicy.isEnabled()) {
            return;
        }
        if (request == null || statusCode < 400) {
            return;
        }

        flushPendingFallbackErrorsInternal(false);

        String requestPath = resolveRequestPath(request);
        if (shouldIgnorePath(requestPath)) {
            return;
        }
        if (Boolean.TRUE.equals(request.getAttribute(ERROR_EVENT_TRACKED_REQUEST_ATTRIBUTE))) {
            return;
        }

        UUID existingEventUid = parseEventUid(request.getAttribute(AnalyticsEventAspect.ANALYTICS_EVENT_UID_REQUEST_ATTRIBUTE));
        if (existingEventUid != null) {
            boolean finalized = finalizeExistingEventAsError(request, existingEventUid, statusCode, throwable);
            if (finalized) {
                request.setAttribute(ERROR_EVENT_TRACKED_REQUEST_ATTRIBUTE, Boolean.TRUE);
                return;
            }
        }

        FallbackErrorPayload payload = buildPayload(request, statusCode, throwable);
        refreshPendingPayload(payload);
        if (isRecentlyTracked(payload.traceId(), payload.requestPath(), payload.requestMethod(), payload.statusCode())) {
            return;
        }
        boolean persisted = persistFallbackError(payload, request, true, "request");
        if (persisted) {
            request.setAttribute(ERROR_EVENT_TRACKED_REQUEST_ATTRIBUTE, Boolean.TRUE);
        }
    }

    @Scheduled(fixedDelayString = "${app.analytics.http-fallback.retry-delay-ms:5000}")
    public void flushPendingFallbackErrorsScheduled() {
        if (!scheduledJobsPolicy.canRun("http-fallback-error-flush")) {
            return;
        }
        if (!instrumentationPolicy.isEnabled()) {
            return;
        }
        flushPendingFallbackErrorsInternal(true);
    }

    private void flushPendingFallbackErrorsInternal(boolean scheduled) {
        if (PENDING_FALLBACK_ERRORS.isEmpty()) {
            return;
        }
        int processed = 0;
        Instant now = Instant.now();
        for (String key : PENDING_FALLBACK_ORDER) {
            if (processed >= MAX_PENDING_FLUSH_PER_CYCLE) {
                break;
            }
            PendingFallbackError pending = PENDING_FALLBACK_ERRORS.get(key);
            if (pending == null) {
                continue;
            }
            if (!pending.readyForRetry(now)) {
                continue;
            }
            if (!PENDING_FLUSH_IN_PROGRESS.add(key)) {
                continue;
            }
            processed++;
            try {
                boolean persisted = persistFallbackError(pending.payload(), null, false, "retry");
                if (persisted) {
                    removePending(key);
                    log.info(
                        "Отложенная HTTP-ошибка успешно зафиксирована: key='{}', traceId='{}', path='{}', status={}, attempts={}",
                        key,
                        pending.payload().traceId(),
                        pending.payload().requestPath(),
                        pending.payload().statusCode(),
                        pending.attempts()
                    );
                } else {
                    pending.markFailed(now);
                }
            } finally {
                PENDING_FLUSH_IN_PROGRESS.remove(key);
            }
        }
        if (scheduled && processed > 0) {
            log.debug("Flush отложенных HTTP-ошибок: попыток={}", processed);
        }
    }

    private boolean persistFallbackError(
        FallbackErrorPayload payload,
        HttpServletRequest request,
        boolean enqueueOnFailure,
        String source
    ) {
        UUID eventUid = payload.existingEventUid();
        Long controllerStageId = null;
        String previousEventUid = MDC.get(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY);
        String previousAppModule = MDC.get(AnalyticsEventAspect.APP_MODULE_MDC_KEY);
        boolean mdcEnriched = false;

        try {
            if (eventUid != null && persistIntoExistingEvent(eventUid, payload, request, source)) {
                return true;
            }
            if (payload.moduleCode() != null && !payload.moduleCode().isBlank()) {
                MDC.put(AnalyticsEventAspect.APP_MODULE_MDC_KEY, payload.moduleCode());
            }
            eventUid = analyticsTrackingApi.startEvent(
                payload.eventCode(),
                payload.userId(),
                payload.sessionId(),
                payload.requestPath(),
                payload.requestMethod(),
                payload.traceId()
            );
            if (payload.requestStartedAt() != null) {
                analyticsTrackingApi.setEventStartedAtIfEarlier(eventUid, payload.requestStartedAt());
            }
            try {
                MDC.put(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY, eventUid.toString());
                String resolvedModule = analyticsTrackingApi.resolveEventModuleCode(eventUid);
                if (resolvedModule != null && !resolvedModule.isBlank()) {
                    MDC.put(AnalyticsEventAspect.APP_MODULE_MDC_KEY, resolvedModule);
                }
                mdcEnriched = true;
            } catch (RuntimeException ignored) {
                // Optional MDC enrichment.
            }

            controllerStageId = analyticsTrackingApi.startStage(eventUid, "CONTROLLER", 1);
            analyticsTrackingApi.recordMetricText(
                controllerStageId,
                "ERROR_CODE",
                payload.eventCode() + "_HTTP_" + payload.statusCode(),
                null
            );
            try {
                analyticsTrackingApi.recordMetricText(controllerStageId, "ERROR_CLASS", payload.errorClass(), null);
            } catch (RuntimeException ignored) {
                // Optional metric for backward compatibility with old dictionaries.
            }
            analyticsTrackingApi.finishStageError(controllerStageId, payload.errorMessage());
            analyticsTrackingApi.markStageLogWindow(
                controllerStageId,
                payload.logWindowStartedAt(),
                payload.logWindowEndedAt()
            );

            attachSyntheticFrontendStageIfNeeded(eventUid, payload);

            analyticsTrackingApi.finishEventError(eventUid, payload.statusCode(), payload.errorMessage());
            analyticsTrackingApi.extendEventDurationIfLater(eventUid, payload.logWindowEndedAt());

            if (request != null) {
                request.setAttribute(AnalyticsEventAspect.ANALYTICS_EVENT_UID_REQUEST_ATTRIBUTE, eventUid.toString());
                request.setAttribute(ERROR_EVENT_TRACKED_REQUEST_ATTRIBUTE, Boolean.TRUE);
            }

            log.warn(
                "Fallback HTTP-ошибка зафиксирована ({}): status={}, class={}, eventCode='{}', path='{}', method='{}', handler='{}', error='{}', rootCause='{}', params='{}', query='{}', userAgent='{}', traceId='{}', eventUid='{}'",
                source,
                payload.statusCode(),
                payload.errorClass(),
                payload.eventCode(),
                payload.requestPath(),
                payload.requestMethod(),
                payload.handlerSignature(),
                payload.errorMessage(),
                payload.rootCauseMessage(),
                payload.requestParams(),
                payload.queryString(),
                payload.userAgent(),
                payload.traceId(),
                eventUid
            );
            return true;
        } catch (RuntimeException ex) {
            log.warn(
                "Не удалось зафиксировать fallback HTTP-ошибку ({}): status={}, code='{}', path='{}', traceId='{}', reason='{}'",
                source,
                payload.statusCode(),
                payload.eventCode(),
                payload.requestPath(),
                payload.traceId(),
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
            );
            if (controllerStageId != null) {
                try {
                    analyticsTrackingApi.markStageLogWindow(
                        controllerStageId,
                        payload.logWindowStartedAt(),
                        payload.logWindowEndedAt()
                    );
                } catch (RuntimeException ignored) {
                    // Analytics must never break business flow.
                }
            }
            if (enqueueOnFailure) {
                enqueuePending(payload, ex);
            }
            return false;
        } finally {
            if (mdcEnriched) {
                if (previousEventUid == null || previousEventUid.isBlank()) {
                    MDC.remove(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY);
                } else {
                    MDC.put(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY, previousEventUid);
                }
            }
            if (previousAppModule == null || previousAppModule.isBlank()) {
                MDC.remove(AnalyticsEventAspect.APP_MODULE_MDC_KEY);
            } else {
                MDC.put(AnalyticsEventAspect.APP_MODULE_MDC_KEY, previousAppModule);
            }
        }
    }

    private void attachSyntheticFrontendStageIfNeeded(UUID eventUid, FallbackErrorPayload payload) {
        if (eventUid == null || !payload.syntheticFrontendStageNeeded()) {
            return;
        }
        Long frontendStageId = null;
        try {
            frontendStageId = analyticsTrackingApi.startStage(eventUid, "FRONTEND", 2);
            BigDecimal requestDurationMs = payload.requestDurationMs() == null
                ? null
                : BigDecimal.valueOf(payload.requestDurationMs());
            if (requestDurationMs != null && requestDurationMs.signum() > 0) {
                analyticsTrackingApi.recordMetricNum(frontendStageId, "FRONTEND_TTFB_MS", requestDurationMs, "ms");
                analyticsTrackingApi.recordMetricNum(frontendStageId, "FRONTEND_DOM_CONTENT_LOADED_MS", requestDurationMs, "ms");
            }
            analyticsTrackingApi.recordMetricText(frontendStageId, "FRONTEND_PAGE_URL", payload.requestPath(), null);
            analyticsTrackingApi.recordMetricText(frontendStageId, "FRONTEND_NAV_TYPE", "error-fallback", null);
            analyticsTrackingApi.finishStageError(frontendStageId, "Frontend fallback error view");
            analyticsTrackingApi.markStageLogWindow(
                frontendStageId,
                payload.logWindowStartedAt(),
                payload.logWindowEndedAt()
            );
        } catch (RuntimeException ex) {
            if (frontendStageId != null) {
                try {
                    analyticsTrackingApi.markStageLogWindow(
                        frontendStageId,
                        payload.logWindowStartedAt(),
                        payload.logWindowEndedAt()
                    );
                } catch (RuntimeException ignored) {
                    // Optional fallback stage; ignore.
                }
            }
        }
    }

    private void enqueuePending(FallbackErrorPayload payload, RuntimeException reason) {
        String key = payload.pendingKey();
        PendingFallbackError existing = PENDING_FALLBACK_ERRORS.get(key);
        String reasonText = reason.getMessage() == null ? reason.getClass().getSimpleName() : reason.getMessage();
        if (existing != null) {
            existing.mergePayload(payload);
            existing.touch(reasonText);
            return;
        }
        PendingFallbackError created = new PendingFallbackError(payload, Instant.now(), reasonText);
        PendingFallbackError previous = PENDING_FALLBACK_ERRORS.putIfAbsent(key, created);
        if (previous != null) {
            previous.mergePayload(payload);
            previous.touch(reasonText);
            return;
        }
        PENDING_FALLBACK_ORDER.addLast(key);
        trimPendingQueue();
        log.warn(
            "Fallback HTTP-ошибка отложена до восстановления БД: key='{}', status={}, path='{}', traceId='{}'",
            key,
            payload.statusCode(),
            payload.requestPath(),
            payload.traceId()
        );
    }

    private void trimPendingQueue() {
        while (PENDING_FALLBACK_ORDER.size() > MAX_PENDING_FALLBACK_ERRORS) {
            String droppedKey = PENDING_FALLBACK_ORDER.pollFirst();
            if (droppedKey == null) {
                break;
            }
            PENDING_FALLBACK_ERRORS.remove(droppedKey);
        }
    }

    private void removePending(String key) {
        PENDING_FALLBACK_ERRORS.remove(key);
        PENDING_FALLBACK_ORDER.removeFirstOccurrence(key);
    }

    private void refreshPendingPayload(FallbackErrorPayload payload) {
        if (payload == null) {
            return;
        }
        PendingFallbackError existing = PENDING_FALLBACK_ERRORS.get(payload.pendingKey());
        if (existing == null) {
            return;
        }
        existing.mergePayload(payload);
    }

    private FallbackErrorPayload buildPayload(HttpServletRequest request, int statusCode, Throwable throwable) {
        String requestPath = resolveRequestPath(request);
        String requestMethod = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase(Locale.ROOT);
        String traceId = resolveTraceId(request);
        String eventCode = resolveEventCode(request);
        String handlerSignature = resolveHandlerSignature(request);
        Long userId = null;
        String sessionId = request.getSession(false) != null ? request.getSession(false).getId() : null;
        String errorMessage = safeMessage(throwable, statusCode);
        String rootCauseMessage = rootCauseMessage(throwable);
        String errorClass = ErrorClassClassifier.classify(statusCode, errorMessage, throwable);
        Instant requestStartedAt = resolveInstantAttribute(request, AnalyticsTraceContext.REQUEST_STARTED_AT_ATTRIBUTE, null);
        Instant logWindowStartedAt = resolveInstantAttribute(
            request,
            ERROR_LOG_WINDOW_STARTED_AT_REQUEST_ATTRIBUTE,
            Instant.now()
        );
        Instant logWindowEndedAt = resolveInstantAttribute(
            request,
            ERROR_LOG_WINDOW_ENDED_AT_REQUEST_ATTRIBUTE,
            Instant.now()
        );
        Long requestDurationMs = resolveRequestDurationMs(request);
        String moduleCode = resolveModuleCodeByPath(requestPath);
        boolean syntheticFrontendStageNeeded = statusCode >= 500 && isPageRequest(requestPath, requestMethod);
        String pendingKey = buildPendingKey(traceId, requestPath, requestMethod, statusCode);
        UUID existingEventUid = parseEventUid(request.getAttribute(AnalyticsEventAspect.ANALYTICS_EVENT_UID_REQUEST_ATTRIBUTE));
        return new FallbackErrorPayload(
            pendingKey,
            existingEventUid,
            eventCode,
            statusCode,
            requestPath,
            requestMethod,
            handlerSignature,
            traceId,
            userId,
            sessionId,
            errorMessage,
            rootCauseMessage,
            errorClass,
            safeValue(request.getQueryString()),
            safeValue(request.getHeader("User-Agent")),
            compactRequestParameters(request),
            requestStartedAt,
            logWindowStartedAt,
            logWindowEndedAt,
            requestDurationMs,
            moduleCode,
            syntheticFrontendStageNeeded
        );
    }

    private boolean persistIntoExistingEvent(
        UUID existingEventUid,
        FallbackErrorPayload payload,
        HttpServletRequest request,
        String source
    ) {
        try {
            if (payload.requestStartedAt() != null) {
                analyticsTrackingApi.setEventStartedAtIfEarlier(existingEventUid, payload.requestStartedAt());
            }
            analyticsTrackingApi.finishEventError(existingEventUid, payload.statusCode(), payload.errorMessage());
            analyticsTrackingApi.extendEventDurationIfLater(existingEventUid, payload.logWindowEndedAt());
            if (request != null) {
                request.setAttribute(AnalyticsEventAspect.ANALYTICS_EVENT_UID_REQUEST_ATTRIBUTE, existingEventUid.toString());
                request.setAttribute(ERROR_EVENT_TRACKED_REQUEST_ATTRIBUTE, Boolean.TRUE);
            }
            log.info(
                "Существующее analytics-событие дофиксировано как ошибка ({}): eventUid='{}', status={}, path='{}', traceId='{}'",
                source,
                existingEventUid,
                payload.statusCode(),
                payload.requestPath(),
                payload.traceId()
            );
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Long resolveRequestDurationMs(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(AnalyticsTraceContext.REQUEST_DURATION_MS_ATTRIBUTE);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String resolveModuleCodeByPath(String requestPath) {
        return "DEFAULT";
    }

    private boolean isPageRequest(String requestPath, String requestMethod) {
        if (requestPath == null || requestPath.isBlank()) {
            return false;
        }
        if (!"GET".equalsIgnoreCase(requestMethod)) {
            return false;
        }
        String path = requestPath.toLowerCase(Locale.ROOT);
        return !path.startsWith("/api/");
    }

    private String buildPendingKey(String traceId, String requestPath, String requestMethod, int statusCode) {
        return (traceId == null ? "" : traceId)
            + "|"
            + (requestPath == null ? "" : requestPath)
            + "|"
            + (requestMethod == null ? "" : requestMethod)
            + "|"
            + statusCode;
    }

    private boolean finalizeExistingEventAsError(
        HttpServletRequest request,
        UUID existingEventUid,
        int statusCode,
        Throwable throwable
    ) {
        String requestPath = resolveRequestPath(request);
        String traceId = resolveTraceId(request);
        String errorMessage = safeMessage(throwable, statusCode);
        try {
            Instant requestStartedAt = resolveInstantAttribute(request, AnalyticsTraceContext.REQUEST_STARTED_AT_ATTRIBUTE, null);
            if (requestStartedAt != null) {
                analyticsTrackingApi.setEventStartedAtIfEarlier(existingEventUid, requestStartedAt);
            }
            analyticsTrackingApi.finishEventError(existingEventUid, statusCode, errorMessage);
            Instant logWindowEndedAt = resolveInstantAttribute(
                request,
                ERROR_LOG_WINDOW_ENDED_AT_REQUEST_ATTRIBUTE,
                Instant.now()
            );
            analyticsTrackingApi.extendEventDurationIfLater(existingEventUid, logWindowEndedAt);
            log.info(
                "Существующее analytics-событие завершено как ошибка: eventUid='{}', status={}, path='{}', traceId='{}'",
                existingEventUid,
                statusCode,
                requestPath,
                traceId
            );
            return true;
        } catch (RuntimeException ex) {
            log.warn(
                "Не удалось завершить существующее analytics-событие как ошибку: eventUid='{}', status={}, path='{}', traceId='{}', reason='{}'",
                existingEventUid,
                statusCode,
                requestPath,
                traceId,
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
            );
            return false;
        }
    }

    private boolean isRecentlyTracked(String traceId, String path, String method, int statusCode) {
        String key = buildPendingKey(traceId, path, method, statusCode);
        long now = System.currentTimeMillis();
        RECENT_FALLBACK_ERRORS.entrySet().removeIf(entry -> now - entry.getValue() > DEDUP_WINDOW_MS);
        Long previous = RECENT_FALLBACK_ERRORS.putIfAbsent(key, now);
        if (previous == null) {
            return false;
        }
        if (now - previous <= DEDUP_WINDOW_MS) {
            return true;
        }
        RECENT_FALLBACK_ERRORS.put(key, now);
        return false;
    }

    private String resolveEventCode(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod handlerMethod) {
            TrackAnalyticsEvent trackAnalyticsEvent = AnnotationUtils.findAnnotation(
                handlerMethod.getMethod(),
                TrackAnalyticsEvent.class
            );
            if (trackAnalyticsEvent != null && trackAnalyticsEvent.code() != null && !trackAnalyticsEvent.code().isBlank()) {
                return trackAnalyticsEvent.code().trim();
            }
        }
        String inferred = inferEventCodeFromPath(request);
        if (inferred != null) {
            return inferred;
        }
        return FALLBACK_ERROR_EVENT_CODE;
    }

    private String inferEventCodeFromPath(HttpServletRequest request) {
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        String path = resolveRequestPath(request);
        if ("GET".equals(method) && "/admin/products".equals(path)) {
            return "PRODUCT_LIST_VIEW";
        }
        if ("GET".equals(method) && "/admin/categories".equals(path)) {
            return "CATEGORY_LIST_VIEW";
        }
        if ("GET".equals(method) && "/admin/reviews".equals(path)) {
            return "REVIEW_LIST_VIEW";
        }
        if ("GET".equals(method) && "/admin/support".equals(path)) {
            return "SUPPORT_LIST_VIEW";
        }
        if ("GET".equals(method) && "/".equals(path)) {
            return "HOME_VIEW";
        }
        if ("POST".equals(method) && "/admin/products".equals(path)) {
            return "PRODUCT_CREATE";
        }
        if ("POST".equals(method) && path.matches("^/admin/products/\\d+$")) {
            return "PRODUCT_UPDATE";
        }
        if ("POST".equals(method) && "/admin/categories".equals(path)) {
            return "CATEGORY_CREATE";
        }
        if ("POST".equals(method) && path.matches("^/admin/categories/\\d+$")) {
            return "CATEGORY_UPDATE";
        }
        return null;
    }

    private boolean shouldIgnorePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return "/api/analytics/frontend/ingest".equals(path)
            || "/api/cart/count".equals(path)
            || "/api/wishlist/count".equals(path);
    }

    private UUID parseEventUid(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String text = String.valueOf(rawValue).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String resolveRequestPath(HttpServletRequest request) {
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (originalPath instanceof String text && !text.isBlank()) {
            return text;
        }
        String requestUri = request.getRequestURI();
        return requestUri == null ? "" : requestUri;
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object traceAttr = request.getAttribute(AnalyticsTraceContext.TRACE_ID_REQUEST_ATTRIBUTE);
        if (traceAttr instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String header = request.getHeader(AnalyticsTraceContext.TRACE_ID_HEADER);
        return header == null ? "" : header;
    }

    private String safeMessage(Throwable throwable, int statusCode) {
        if (throwable == null) {
            return "HTTP " + statusCode;
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + oneLine(message, 500);
    }

    private Instant resolveInstantAttribute(HttpServletRequest request, String attribute, Instant fallback) {
        if (request == null || attribute == null || attribute.isBlank()) {
            return fallback;
        }
        Object value = request.getAttribute(attribute);
        if (value instanceof Instant instant) {
            return instant;
        }
        return fallback;
    }

    private String resolveHandlerSignature(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return "-";
        }
        String parameterTypes = Arrays.stream(handlerMethod.getMethod().getParameterTypes())
            .map(Class::getSimpleName)
            .reduce((left, right) -> left + "," + right)
            .orElse("");
        return handlerMethod.getBeanType().getSimpleName()
            + "."
            + handlerMethod.getMethod().getName()
            + "("
            + parameterTypes
            + ")";
    }

    private String rootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "-";
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": " + oneLine(message, 500);
    }

    private String compactRequestParameters(HttpServletRequest request) {
        if (request == null || request.getParameterMap().isEmpty()) {
            return "-";
        }
        Map<String, String> compact = new LinkedHashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (isSensitiveParameter(name)) {
                compact.put(name, "***");
                continue;
            }
            String[] values = request.getParameterValues(name);
            if (values == null || values.length == 0) {
                compact.put(name, "");
                continue;
            }
            String joined = Arrays.stream(values).map(value -> value == null ? "null" : oneLine(value, 120))
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
            compact.put(name, joined);
        }
        return oneLine(compact.toString(), 700);
    }

    private boolean isSensitiveParameter(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
            || normalized.contains("passwd")
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("auth");
    }

    private String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return oneLine(value, 400);
    }

    private String oneLine(String value, int maxLength) {
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private record FallbackErrorPayload(
        String pendingKey,
        UUID existingEventUid,
        String eventCode,
        int statusCode,
        String requestPath,
        String requestMethod,
        String handlerSignature,
        String traceId,
        Long userId,
        String sessionId,
        String errorMessage,
        String rootCauseMessage,
        String errorClass,
        String queryString,
        String userAgent,
        String requestParams,
        Instant requestStartedAt,
        Instant logWindowStartedAt,
        Instant logWindowEndedAt,
        Long requestDurationMs,
        String moduleCode,
        boolean syntheticFrontendStageNeeded
    ) {
    }

    private static final class PendingFallbackError {
        private volatile FallbackErrorPayload payload;
        private volatile Instant nextRetryAt;
        private volatile Instant lastFailureAt;
        private volatile String lastFailureReason;
        private volatile int attempts;

        private PendingFallbackError(FallbackErrorPayload payload, Instant now, String failureReason) {
            this.payload = payload;
            this.lastFailureAt = now;
            this.lastFailureReason = failureReason;
            this.attempts = 1;
            this.nextRetryAt = now.plusMillis(PENDING_RETRY_BASE_MS);
        }

        private FallbackErrorPayload payload() {
            return payload;
        }

        private int attempts() {
            return attempts;
        }

        private boolean readyForRetry(Instant now) {
            return now != null && (nextRetryAt == null || !now.isBefore(nextRetryAt));
        }

        private void touch(String failureReason) {
            markFailed(Instant.now());
            this.lastFailureReason = failureReason;
        }

        private void mergePayload(FallbackErrorPayload candidate) {
            if (candidate == null) {
                return;
            }
            FallbackErrorPayload current = this.payload;
            if (current == null) {
                this.payload = candidate;
                return;
            }
            Instant currentLogEnd = current.logWindowEndedAt();
            Instant candidateLogEnd = candidate.logWindowEndedAt();
            boolean shouldReplace = currentLogEnd == null
                || (candidateLogEnd != null && candidateLogEnd.isAfter(currentLogEnd));
            if (!shouldReplace) {
                Long currentDuration = current.requestDurationMs();
                Long candidateDuration = candidate.requestDurationMs();
                shouldReplace = currentDuration == null
                    || (candidateDuration != null && candidateDuration > currentDuration);
            }
            if (shouldReplace) {
                this.payload = candidate;
            }
        }

        private void markFailed(Instant failedAt) {
            this.attempts = Math.min(this.attempts + 1, 30);
            this.lastFailureAt = failedAt;
            long multiplier = 1L << Math.min(this.attempts - 1, 10);
            long delay = Math.min(PENDING_RETRY_BASE_MS * multiplier, PENDING_RETRY_MAX_MS);
            this.nextRetryAt = failedAt.plusMillis(delay);
        }
    }
}

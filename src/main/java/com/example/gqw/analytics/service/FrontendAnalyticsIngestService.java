package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.analytics.repository.AnalyticsStageRepository;
import com.example.gqw.analytics.web.dto.FrontendAnalyticsIngestRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class FrontendAnalyticsIngestService {

    private static final int MAX_TEXT_LENGTH = 2048;
    private static final String FRONTEND_STAGE_CODE = "FRONTEND";
    private static final int TRACE_CANDIDATES_LIMIT = 20;
    private static final int PATH_CANDIDATES_LIMIT = 10;
    private static final int TRACE_SEARCH_WINDOW_SECONDS = 180;
    private static final int PATH_SEARCH_WINDOW_SECONDS = 180;
    private static final int TRACE_LINK_MIN_SCORE = 55;
    private static final int FRONTEND_SUPPRESS_ON_SERVER_ERROR_WINDOW_SECONDS = 180;
    private static final int FRONTEND_PARENT_RETRY_FAST_ATTEMPTS = 2;
    private static final int FRONTEND_PARENT_RETRY_FAST_DELAY_MS = 80;
    private static final int FRONTEND_PARENT_RETRY_PAGE_ATTEMPTS = 4;
    private static final int FRONTEND_PARENT_RETRY_PAGE_DELAY_MS = 80;
    private static final long FRONTEND_ELAPSED_MIN_MS = 1L;
    private static final long FRONTEND_ELAPSED_MAX_MS = 120_000L;
    private static final String FRONTEND_PAGE_LOAD_CODE = "FRONTEND_PAGE_LOAD";
    private static final String FRONTEND_WEB_VITALS_CODE = "FRONTEND_WEB_VITALS";

    private final AnalyticsTrackingApi analyticsTrackingApi;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final AnalyticsStageRepository analyticsStageRepository;
    private final AnalyticsStrictWarningEventService strictWarningEventService;

    public FrontendAnalyticsIngestService(
        AnalyticsTrackingApi analyticsTrackingApi,
        AnalyticsEventRepository analyticsEventRepository,
        AnalyticsStageRepository analyticsStageRepository,
        AnalyticsStrictWarningEventService strictWarningEventService
    ) {
        this.analyticsTrackingApi = analyticsTrackingApi;
        this.analyticsEventRepository = analyticsEventRepository;
        this.analyticsStageRepository = analyticsStageRepository;
        this.strictWarningEventService = strictWarningEventService;
    }

    public void ingest(FrontendAnalyticsIngestRequest request, HttpServletRequest httpRequest) {
        if (request == null || request.events() == null || request.events().isEmpty()) {
            return;
        }
        for (FrontendAnalyticsIngestRequest.FrontendEventPayload payload : request.events()) {
            ingestOne(payload, httpRequest);
        }
    }

    private void ingestOne(
        FrontendAnalyticsIngestRequest.FrontendEventPayload payload,
        HttpServletRequest httpRequest
    ) {
        if (payload == null) {
            return;
        }
        String code = normalizeCode(payload.code());
        if (code == null) {
            return;
        }
        String requestPath = trimToLen(
            firstNonBlank(payload.requestPath(), payload.pagePath(), httpRequest != null ? httpRequest.getRequestURI() : null),
            512
        );
        if (isAnalyticsUiPath(requestPath)) {
            return;
        }
        String traceId = trimToLen(firstNonBlank(payload.traceId(), resolveTraceId(httpRequest)), 128);
        String moduleCode = normalizeModule(payload.moduleCode(), payload.pagePath(), requestPath);
        Integer statusCode = normalizeStatus(payload.statusCode(), payload.error());
        String errorMessage = trimToLen(payload.errorMessage(), MAX_TEXT_LENGTH);
        boolean isError = Boolean.TRUE.equals(payload.error()) || statusCode >= 400;
        if (shouldSkipFrontendPayload(payload, code)) {
            return;
        }
        if (shouldSuppressFrontendEvent(code, traceId, requestPath)) {
            return;
        }
        UUID parentEventUid = resolveParentEventUidWithRetry(payload, code, traceId, requestPath, moduleCode, isError);
        Long parentEventId = findEventIdByUid(parentEventUid);
        if (parentEventUid != null && parentEventId != null) {
            ingestAsLinkedFrontendStage(parentEventUid, parentEventId, payload, isError, errorMessage, requestPath, traceId);
        } else {
            // Frontend technical payloads are stored only as stages/metrics of business events.
            // Do not create standalone FRONTEND_* events.
            return;
        }
    }

    private void ingestAsLinkedFrontendStage(
        UUID parentEventUid,
        Long parentEventId,
        FrontendAnalyticsIngestRequest.FrontendEventPayload payload,
        boolean isError,
        String errorMessage,
        String requestPath,
        String traceId
    ) {
        Long stageId = null;
        Instant logAt = Instant.now();
        try {
            stageId = startFrontendStage(parentEventUid, parentEventId, requestPath, traceId);
            if (stageId == null) {
                return;
            }
            recordMetricsNum(stageId, payload.metricsNum());
            recordMetricsText(stageId, payload.metricsText());
            if (isError) {
                analyticsTrackingApi.finishStageError(stageId, firstNonBlank(errorMessage, "Frontend error"));
            } else {
                analyticsTrackingApi.finishStageSuccess(stageId);
            }
            Instant finishedAt = Instant.now();
            analyticsTrackingApi.markStageLogWindow(stageId, logAt, finishedAt);
            extendParentEventDurationFromFrontendTiming(parentEventUid, parentEventId, payload);
        } catch (RuntimeException ignored) {
            if (stageId != null) {
                try {
                    analyticsTrackingApi.markStageLogWindow(stageId, logAt, Instant.now());
                } catch (RuntimeException ignored2) {
                    // Analytics must never break page flow.
                }
            }
        }
    }

    private void extendParentEventDurationFromFrontendTiming(
        UUID parentEventUid,
        Long parentEventId,
        FrontendAnalyticsIngestRequest.FrontendEventPayload payload
    ) {
        if (parentEventUid == null || parentEventId == null || payload == null) {
            return;
        }
        String code = normalizeCode(payload.code());
        if (!FRONTEND_PAGE_LOAD_CODE.equals(code) && !FRONTEND_WEB_VITALS_CODE.equals(code)) {
            return;
        }
        Optional<AnalyticsEvent> eventOpt = analyticsEventRepository.findById(parentEventId);
        if (eventOpt.isEmpty() || eventOpt.get().getStartedAt() == null) {
            return;
        }

        BigDecimal elapsedMs = resolveFrontendElapsedMs(payload);
        Long elapsedRounded = sanitizeFrontendElapsedMs(elapsedMs);
        if (elapsedRounded == null) {
            return;
        }

        Instant candidateEndedAt = eventOpt.get().getStartedAt().plusMillis(elapsedRounded);
        analyticsTrackingApi.extendEventDurationIfLater(parentEventUid, candidateEndedAt);
    }

    private BigDecimal resolveFrontendElapsedMs(FrontendAnalyticsIngestRequest.FrontendEventPayload payload) {
        if (payload == null) {
            return null;
        }
        BigDecimal domContentLoadedMs = metricNum(payload.metricsNum(), "FRONTEND_DOM_CONTENT_LOADED_MS");
        BigDecimal loadEventMs = metricNum(payload.metricsNum(), "FRONTEND_LOAD_EVENT_MS");
        BigDecimal apiDurationMs = metricNum(payload.metricsNum(), "FRONTEND_API_DURATION_MS");
        BigDecimal ttfbMs = metricNum(payload.metricsNum(), "FRONTEND_TTFB_MS");
        if (domContentLoadedMs != null && domContentLoadedMs.signum() >= 0) {
            return domContentLoadedMs;
        }
        if (loadEventMs != null && loadEventMs.signum() >= 0) {
            return loadEventMs;
        }
        if (apiDurationMs != null && apiDurationMs.signum() >= 0) {
            return apiDurationMs;
        }
        if (ttfbMs != null && ttfbMs.signum() >= 0) {
            return ttfbMs;
        }
        return null;
    }

    private Long sanitizeFrontendElapsedMs(BigDecimal elapsedMs) {
        if (elapsedMs == null || elapsedMs.signum() < 0) {
            return null;
        }
        long rounded = Math.round(elapsedMs.doubleValue());
        if (rounded < FRONTEND_ELAPSED_MIN_MS) {
            return FRONTEND_ELAPSED_MIN_MS;
        }
        if (rounded > FRONTEND_ELAPSED_MAX_MS) {
            return FRONTEND_ELAPSED_MAX_MS;
        }
        return rounded;
    }

    private BigDecimal metricNum(Map<String, BigDecimal> metrics, String key) {
        if (metrics == null || metrics.isEmpty() || key == null || key.isBlank()) {
            return null;
        }
        BigDecimal direct = metrics.get(key);
        if (direct != null) {
            return direct;
        }
        String normalizedKey = normalizeCode(key);
        for (Map.Entry<String, BigDecimal> entry : metrics.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            if (normalizedKey.equals(normalizeCode(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Long findEventIdByUid(UUID eventUid) {
        if (eventUid == null) {
            return null;
        }
        return analyticsEventRepository.findByEventUid(eventUid)
            .map(event -> event.getId())
            .orElse(null);
    }

    private Long startFrontendStage(UUID eventUid, Long eventId, String requestPath, String traceId) {
        if (eventUid == null || eventId == null) {
            return null;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            int nextOrder = analyticsStageRepository.findTopByEventIdOrderByStageOrderDesc(eventId)
                .map(stage -> (stage.getStageOrder() == null ? 0 : stage.getStageOrder()) + 1)
                .orElse(1);
            try {
                return analyticsTrackingApi.startStage(eventUid, FRONTEND_STAGE_CODE, nextOrder);
            } catch (IllegalArgumentException ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("Unknown stage type")) {
                    strictWarningEventService.record(
                        "stage",
                        FRONTEND_STAGE_CODE,
                        ex.getMessage(),
                        FrontendAnalyticsIngestService.class.getSimpleName(),
                        "startFrontendStage",
                        requestPath,
                        traceId,
                        String.valueOf(eventUid),
                        null
                    );
                    return null;
                }
                throw ex;
            } catch (DataIntegrityViolationException ex) {
                // Concurrent stage insert, retry with recalculated order.
            }
        }
        return null;
    }

    private void recordMetricsNum(Long stageId, Map<String, BigDecimal> values) {
        if (stageId == null || values == null || values.isEmpty()) {
            return;
        }
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            String code = normalizeCode(entry.getKey());
            BigDecimal value = entry.getValue();
            if (code == null || value == null) {
                continue;
            }
            try {
                analyticsTrackingApi.recordMetricNum(stageId, code, value, null);
            } catch (RuntimeException ignored) {
                // Unknown metric type or invalid value must not break ingestion.
                strictWarningEventService.record(
                    "metric",
                    code,
                    "Unknown metric type or invalid value",
                    FrontendAnalyticsIngestService.class.getSimpleName(),
                    "recordMetricsNum",
                    null,
                    null,
                    String.valueOf(findEventUidByStageId(stageId)),
                    stageId
                );
            }
        }
    }

    private void recordMetricsText(Long stageId, Map<String, String> values) {
        if (stageId == null || values == null || values.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String code = normalizeCode(entry.getKey());
            String value = trimToLen(entry.getValue(), MAX_TEXT_LENGTH);
            if (code == null || value == null || value.isBlank()) {
                continue;
            }
            try {
                analyticsTrackingApi.recordMetricText(stageId, code, value, null);
            } catch (RuntimeException ignored) {
                // Unknown metric type or invalid value must not break ingestion.
                strictWarningEventService.record(
                    "metric",
                    code,
                    "Unknown metric type or invalid value",
                    FrontendAnalyticsIngestService.class.getSimpleName(),
                    "recordMetricsText",
                    null,
                    null,
                    String.valueOf(findEventUidByStageId(stageId)),
                    stageId
                );
            }
        }
    }

    private UUID findEventUidByStageId(Long stageId) {
        if (stageId == null) {
            return null;
        }
        return analyticsStageRepository.findById(stageId)
            .map(stage -> analyticsEventRepository.findById(stage.getEventId()))
            .flatMap(java.util.function.Function.identity())
            .map(AnalyticsEvent::getEventUid)
            .orElse(null);
    }

    private String normalizeCode(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private UUID resolveParentEventUid(
        FrontendAnalyticsIngestRequest.FrontendEventPayload payload,
        String traceId,
        String requestPath,
        String moduleCode,
        boolean isFrontendError
    ) {
        UUID explicit = parseUuid(payload.parentEventUid());
        if (explicit != null) {
            return explicit;
        }
        if (traceId == null || traceId.isBlank() || requestPath == null || requestPath.isBlank()) {
            return resolveByPathOnly(requestPath, moduleCode);
        }
        Instant from = Instant.now().minusSeconds(TRACE_SEARCH_WINDOW_SECONDS);
        List<UUID> candidates = analyticsEventRepository.findRecentNonFrontendByTraceAndPath(
                traceId,
                requestPath,
                from,
                PageRequest.of(0, 1)
            )
            .stream()
            .map(event -> event.getEventUid())
            .toList();
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }

        UUID byTraceScore = resolveByTraceScore(traceId, requestPath, moduleCode, isFrontendError);
        if (byTraceScore != null) {
            return byTraceScore;
        }

        return resolveByPathOnly(requestPath, moduleCode);
    }

    private UUID resolveParentEventUidWithRetry(
        FrontendAnalyticsIngestRequest.FrontendEventPayload payload,
        String code,
        String traceId,
        String requestPath,
        String moduleCode,
        boolean isFrontendError
    ) {
        UUID resolved = resolveParentEventUid(payload, traceId, requestPath, moduleCode, isFrontendError);
        if (resolved != null) {
            return resolved;
        }
        int attempts = isPageLifecycleCode(code)
            ? FRONTEND_PARENT_RETRY_PAGE_ATTEMPTS
            : FRONTEND_PARENT_RETRY_FAST_ATTEMPTS;
        long delayMs = isPageLifecycleCode(code)
            ? FRONTEND_PARENT_RETRY_PAGE_DELAY_MS
            : FRONTEND_PARENT_RETRY_FAST_DELAY_MS;
        for (int i = 0; i < attempts; i++) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return null;
            }
            resolved = resolveParentEventUid(payload, traceId, requestPath, moduleCode, isFrontendError);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private boolean isPageLifecycleCode(String code) {
        return "FRONTEND_PAGE_LOAD".equals(code) || "FRONTEND_WEB_VITALS".equals(code);
    }

    private boolean shouldSkipFrontendPayload(
        FrontendAnalyticsIngestRequest.FrontendEventPayload payload,
        String code
    ) {
        if (!"FRONTEND_API_CALL".equals(code)) {
            return false;
        }
        String apiUrl = payload != null && payload.metricsText() != null
            ? payload.metricsText().get("FRONTEND_API_URL")
            : null;
        String normalizedPath = normalizeApiPath(apiUrl);
        return "/api/cart/count".equals(normalizedPath)
            || "/api/wishlist/count".equals(normalizedPath)
            || "/api/analytics/frontend/ingest".equals(normalizedPath);
    }

    private String normalizeApiPath(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        String text = rawUrl.trim();
        try {
            URI uri = URI.create(text);
            String path = uri.getPath();
            if (path != null && !path.isBlank()) {
                return normalizePath(path);
            }
        } catch (IllegalArgumentException ignored) {
            // Fallback to plain string parsing.
        }
        int queryIndex = text.indexOf('?');
        if (queryIndex >= 0) {
            text = text.substring(0, queryIndex);
        }
        return normalizePath(text);
    }

    private UUID resolveByPathOnly(String requestPath, String moduleCode) {
        if (requestPath == null || requestPath.isBlank()) {
            return null;
        }
        Instant from = Instant.now().minusSeconds(PATH_SEARCH_WINDOW_SECONDS);
        List<AnalyticsEvent> candidates = analyticsEventRepository.findRecentNonFrontendByPath(
                requestPath,
                from,
                PageRequest.of(0, PATH_CANDIDATES_LIMIT)
            );
        for (AnalyticsEvent candidate : candidates) {
            if (candidate == null || candidate.getEventUid() == null) {
                continue;
            }
            if (!sameModule(candidate.getModuleCode(), moduleCode)) {
                continue;
            }
            return candidate.getEventUid();
        }
        if (!candidates.isEmpty() && candidates.get(0).getEventUid() != null) {
            return candidates.get(0).getEventUid();
        }
        return null;
    }

    private UUID resolveByTraceScore(
        String traceId,
        String requestPath,
        String moduleCode,
        boolean isFrontendError
    ) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        Instant from = Instant.now().minusSeconds(TRACE_SEARCH_WINDOW_SECONDS);
        List<AnalyticsEvent> candidates = analyticsEventRepository.findRecentNonFrontendByTrace(
                traceId,
                from,
                PageRequest.of(0, TRACE_CANDIDATES_LIMIT)
            );

        AnalyticsEvent best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AnalyticsEvent candidate : candidates) {
            if (candidate == null || candidate.getEventUid() == null) {
                continue;
            }
            int score = scoreCandidate(candidate, requestPath, moduleCode, isFrontendError);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null && bestScore >= TRACE_LINK_MIN_SCORE) {
            return best.getEventUid();
        }
        return null;
    }

    private int scoreCandidate(
        AnalyticsEvent candidate,
        String requestPath,
        String moduleCode,
        boolean isFrontendError
    ) {
        int score = 0;

        if (sameModule(candidate.getModuleCode(), moduleCode)) {
            score += 25;
        } else {
            score -= 10;
        }

        String candidatePath = normalizePath(candidate.getRequestPath());
        String targetPath = normalizePath(requestPath);
        if (!candidatePath.isBlank() && candidatePath.equals(targetPath)) {
            score += 70;
        } else if (isSameResourceFamily(candidatePath, targetPath)) {
            score += 40;
        } else if (isSameRootSegment(candidatePath, targetPath)) {
            score += 15;
        }

        if (Boolean.TRUE.equals(candidate.getIsError())) {
            score += isFrontendError ? 45 : 15;
        } else if (isFrontendError) {
            score -= 10;
        }

        if (candidate.getStatusCode() != null && candidate.getStatusCode() >= 400) {
            score += isFrontendError ? 20 : 5;
        }

        Instant startedAt = candidate.getStartedAt();
        if (startedAt != null) {
            long ageSec = Math.max(0L, Duration.between(startedAt, Instant.now()).getSeconds());
            if (ageSec <= 10) {
                score += 25;
            } else if (ageSec <= 30) {
                score += 16;
            } else if (ageSec <= 60) {
                score += 8;
            } else if (ageSec > TRACE_SEARCH_WINDOW_SECONDS) {
                score -= 20;
            }
        }

        return score;
    }

    private boolean sameModule(String candidateModule, String expectedModule) {
        if (candidateModule == null || expectedModule == null) {
            return false;
        }
        return candidateModule.equalsIgnoreCase(expectedModule);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String trimmed = path.trim().toLowerCase(Locale.ROOT);
        int queryIdx = trimmed.indexOf('?');
        if (queryIdx >= 0) {
            trimmed = trimmed.substring(0, queryIdx);
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private boolean isSameResourceFamily(String leftPath, String rightPath) {
        if (leftPath.isBlank() || rightPath.isBlank()) {
            return false;
        }
        String leftBase = resourceBase(leftPath);
        String rightBase = resourceBase(rightPath);
        if (leftBase.isBlank() || rightBase.isBlank()) {
            return false;
        }
        return leftBase.equals(rightBase);
    }

    private boolean isSameRootSegment(String leftPath, String rightPath) {
        if (leftPath.isBlank() || rightPath.isBlank()) {
            return false;
        }
        String leftRoot = rootSegment(leftPath);
        String rightRoot = rootSegment(rightPath);
        return !leftRoot.isBlank() && leftRoot.equals(rightRoot);
    }

    private String resourceBase(String path) {
        String[] parts = path.split("/");
        if (parts.length <= 2) {
            return path;
        }
        int take = Math.min(parts.length, 3);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < take; i++) {
            sb.append('/').append(parts[i]);
        }
        return sb.toString();
    }

    private String rootSegment(String path) {
        String[] parts = path.split("/");
        if (parts.length <= 1) {
            return "";
        }
        return parts[1];
    }

    private Integer normalizeStatus(Integer statusCode, Boolean error) {
        if (statusCode != null && statusCode >= 100 && statusCode <= 599) {
            return statusCode;
        }
        return Boolean.TRUE.equals(error) ? 500 : 200;
    }

    private String normalizeModule(String moduleCode, String pagePath, String requestPath) {
        String normalized = normalizeCode(moduleCode);
        if (normalized != null) {
            return normalized;
        }
        String path = firstNonBlank(pagePath, requestPath, "");
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/admin")) {
            return "ADMIN";
        }
        return "SHOP";
    }

    private boolean shouldSuppressFrontendEvent(String code, String traceId, String requestPath) {
        if (!"FRONTEND_WEB_VITALS".equals(code) && !"FRONTEND_PAGE_LOAD".equals(code)) {
            return false;
        }
        Instant from = Instant.now().minusSeconds(FRONTEND_SUPPRESS_ON_SERVER_ERROR_WINDOW_SECONDS);
        String normalizedPath = normalizePath(requestPath);

        if (!normalizedPath.isBlank()) {
            List<AnalyticsEvent> recentByPath = analyticsEventRepository.findRecentNonFrontendByPath(
                normalizedPath,
                from,
                PageRequest.of(0, 20)
            );
            for (AnalyticsEvent event : recentByPath) {
                if (event == null) {
                    continue;
                }
                Integer sc = event.getStatusCode();
                if (Boolean.TRUE.equals(event.getIsError()) && sc != null && sc >= 500) {
                    return true;
                }
            }
        }

        if (traceId == null || traceId.isBlank()) {
            return false;
        }
        List<AnalyticsEvent> recentBackendErrors = analyticsEventRepository.findRecentErrorNonFrontendByTrace(
            traceId,
            from,
            PageRequest.of(0, 20)
        );
        for (AnalyticsEvent event : recentBackendErrors) {
            if (event == null) {
                continue;
            }
            Integer sc = event.getStatusCode();
            if (sc == null || sc < 500) {
                continue;
            }
            if (normalizedPath.isBlank()) {
                return true;
            }
            if (normalizePath(event.getRequestPath()).equals(normalizedPath)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnalyticsUiPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.startsWith("/analytics") || lower.startsWith("/analytics-admin");
    }

    private String resolveTraceId(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        Object traceIdAttr = request.getAttribute("traceId");
        if (traceIdAttr instanceof String text && !text.isBlank()) {
            return text;
        }
        String header = request.getHeader("X-Trace-Id");
        return header == null ? "" : header;
    }

    private String trimToLen(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

}

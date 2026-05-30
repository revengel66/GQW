package com.example.gqw.config;

import com.example.gqw.analytics.service.AnalyticsHttpErrorTrackingService;
import com.example.gqw.analytics.aop.AnalyticsEventAspect;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_REQUEST_ATTRIBUTE = "traceId";
    public static final String REQUEST_STARTED_AT_ATTRIBUTE = "requestStartedAt";
    public static final String REQUEST_DURATION_MS_ATTRIBUTE = "requestDurationMs";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    private static final int TRACE_ID_MIN_LENGTH = 8;
    private static final int TRACE_ID_MAX_LENGTH = 64;
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1500L;
    private final AnalyticsHttpErrorTrackingService analyticsHttpErrorTrackingService;

    public TraceIdFilter(AnalyticsHttpErrorTrackingService analyticsHttpErrorTrackingService) {
        this.analyticsHttpErrorTrackingService = analyticsHttpErrorTrackingService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveOrCreateTraceId(request);
        if (request.getAttribute(REQUEST_STARTED_AT_ATTRIBUTE) == null) {
            request.setAttribute(REQUEST_STARTED_AT_ATTRIBUTE, Instant.now());
        }
        request.setAttribute(TRACE_ID_REQUEST_ATTRIBUTE, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        long startedAtNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
            Object analyticsEventUid = request.getAttribute(AnalyticsEventAspect.ANALYTICS_EVENT_UID_REQUEST_ATTRIBUTE);
            if (analyticsEventUid instanceof String eventUid && !eventUid.isBlank()) {
                response.setHeader(AnalyticsEventAspect.ANALYTICS_EVENT_UID_RESPONSE_HEADER, eventUid);
            }
            int status = response.getStatus();
            String method = request.getMethod();
            String path = resolveLoggedPath(request);
            request.setAttribute(REQUEST_DURATION_MS_ATTRIBUTE, durationMs);
            if (status >= 500) {
                Instant logStartedAt = Instant.now();
                request.setAttribute(AnalyticsHttpErrorTrackingService.ERROR_LOG_WINDOW_STARTED_AT_REQUEST_ATTRIBUTE, logStartedAt);
                Object throwable = request.getAttribute(AnalyticsHttpErrorTrackingService.ERROR_THROWABLE_REQUEST_ATTRIBUTE);
                Throwable error = throwable instanceof Throwable t ? t : null;
                log.error(
                    "HTTP {} {} -> {} ({} ms) | handler='{}' | error='{}' | cause='{}' | query='{}' | userAgent='{}'",
                    method,
                    path,
                    status,
                    durationMs,
                    resolveHandlerSignature(request),
                    summarizeThrowable(error),
                    summarizeRootCause(error),
                    safeValue(request.getQueryString()),
                    safeValue(request.getHeader("User-Agent"))
                );
                analyticsHttpErrorTrackingService.trackIfMissing(
                    request,
                    status,
                    error
                );
                request.setAttribute(
                    AnalyticsHttpErrorTrackingService.ERROR_LOG_WINDOW_ENDED_AT_REQUEST_ATTRIBUTE,
                    Instant.now()
                );
            } else if (status >= 400) {
                Instant logStartedAt = Instant.now();
                request.setAttribute(AnalyticsHttpErrorTrackingService.ERROR_LOG_WINDOW_STARTED_AT_REQUEST_ATTRIBUTE, logStartedAt);
                Object throwable = request.getAttribute(AnalyticsHttpErrorTrackingService.ERROR_THROWABLE_REQUEST_ATTRIBUTE);
                Throwable error = throwable instanceof Throwable t ? t : null;
                log.warn(
                    "HTTP {} {} -> {} ({} ms) | handler='{}' | error='{}' | cause='{}' | query='{}' | userAgent='{}'",
                    method,
                    path,
                    status,
                    durationMs,
                    resolveHandlerSignature(request),
                    summarizeThrowable(error),
                    summarizeRootCause(error),
                    safeValue(request.getQueryString()),
                    safeValue(request.getHeader("User-Agent"))
                );
                analyticsHttpErrorTrackingService.trackIfMissing(
                    request,
                    status,
                    error
                );
                request.setAttribute(
                    AnalyticsHttpErrorTrackingService.ERROR_LOG_WINDOW_ENDED_AT_REQUEST_ATTRIBUTE,
                    Instant.now()
                );
            } else if (durationMs >= SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("SLOW HTTP {} {} -> {} ({} ms)", method, path, status, durationMs);
            }
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private String normalizeTraceId(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() < TRACE_ID_MIN_LENGTH || normalized.length() > TRACE_ID_MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            boolean allowed = Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.';
            if (!allowed) {
                return null;
            }
        }
        return normalized;
    }

    private String resolveOrCreateTraceId(HttpServletRequest request) {
        Object existing = request.getAttribute(TRACE_ID_REQUEST_ATTRIBUTE);
        if (existing instanceof String existingTrace && !existingTrace.isBlank()) {
            String normalizedExisting = normalizeTraceId(existingTrace);
            if (normalizedExisting != null) {
                return normalizedExisting;
            }
        }
        String fromHeader = normalizeTraceId(request.getHeader(TRACE_ID_HEADER));
        if (fromHeader != null) {
            return fromHeader;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveLoggedPath(HttpServletRequest request) {
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (originalPath instanceof String text && !text.isBlank()) {
            return text;
        }
        return request.getRequestURI();
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

    private String summarizeThrowable(Throwable throwable) {
        if (throwable == null) {
            return "-";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + oneLine(message, 400);
    }

    private String summarizeRootCause(Throwable throwable) {
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
        return root.getClass().getSimpleName() + ": " + oneLine(message, 400);
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
}

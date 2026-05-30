package com.example.gqw.analytics.aop;

import com.example.gqw.analytics.service.AnalyticsTrackingApi;
import com.example.gqw.analytics.service.AnalyticsHttpErrorTrackingService;
import com.example.gqw.analytics.service.ErrorClassClassifier;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.config.TraceIdFilter;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AnalyticsEventAspect {

    public static final String ANALYTICS_EVENT_UID_MDC_KEY = "analyticsEventUid";
    public static final String ANALYTICS_EVENT_UID_REQUEST_ATTRIBUTE = "analyticsEventUid";
    public static final String ANALYTICS_EVENT_UID_RESPONSE_HEADER = "X-Analytics-Event-Uid";
    public static final String ANALYTICS_MODULE_MDC_KEY = "analyticsModule";
    public static final String APP_MODULE_MDC_KEY = "appModule";

    private final AnalyticsTrackingApi analyticsTrackingApi;
    private final CurrentUserService currentUserService;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AnalyticsEventAspect(
        AnalyticsTrackingApi analyticsTrackingApi,
        CurrentUserService currentUserService
    ) {
        this.analyticsTrackingApi = analyticsTrackingApi;
        this.currentUserService = currentUserService;
    }

    @Around("@annotation(TrackAnalyticsEvent)")
    public Object aroundTrackedMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        TrackAnalyticsEvent trackAnalyticsEvent = AnnotationUtils.findAnnotation(method, TrackAnalyticsEvent.class);
        if (trackAnalyticsEvent == null) {
            return joinPoint.proceed();
        }
        if (AnalyticsEventContextHolder.get() != null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = resolveRequest(joinPoint.getArgs());
        if (request == null) {
            return joinPoint.proceed();
        }

        Long userId = currentUserService.findCurrentUser().map(ShopUser::getId).orElse(null);
        String sessionId = request.getSession(false) != null ? request.getSession(false).getId() : null;
        UUID eventUid;
        AnalyticsEventContext context;
        Long controllerStageId;
        String previousAppModule = MDC.get(APP_MODULE_MDC_KEY);
        String previousAnalyticsModule = MDC.get(ANALYTICS_MODULE_MDC_KEY);
        String eventModuleCode = EventType.DEFAULT_MODULE_CODE;
        String eventCode = null;
        try {
            eventCode = resolveEventCode(trackAnalyticsEvent, method, joinPoint.getArgs(), request);
            eventUid = analyticsTrackingApi.startEvent(
                eventCode,
                userId,
                sessionId,
                request.getRequestURI(),
                request.getMethod(),
                resolveTraceId(request)
            );
            String resolvedEventModule = analyticsTrackingApi.resolveEventModuleCode(eventUid);
            if (resolvedEventModule != null && !resolvedEventModule.isBlank()) {
                eventModuleCode = resolvedEventModule;
            }
            context = new AnalyticsEventContext(eventUid);
            AnalyticsEventContextHolder.set(context);
            MDC.put(ANALYTICS_EVENT_UID_MDC_KEY, eventUid.toString());
            request.setAttribute(ANALYTICS_EVENT_UID_REQUEST_ATTRIBUTE, eventUid.toString());
            MDC.put(ANALYTICS_MODULE_MDC_KEY, eventModuleCode);
            MDC.put(APP_MODULE_MDC_KEY, eventModuleCode);
            controllerStageId = analyticsTrackingApi.startStage(eventUid, "CONTROLLER", context.nextStageOrder());
            context.pushStageId(controllerStageId);
        } catch (RuntimeException ignored) {
            AnalyticsEventContextHolder.clear();
            MDC.remove(ANALYTICS_EVENT_UID_MDC_KEY);
            restoreMdc(APP_MODULE_MDC_KEY, previousAppModule);
            restoreMdc(ANALYTICS_MODULE_MDC_KEY, previousAnalyticsModule);
            return joinPoint.proceed();
        }
        Set<String> resolvedAttributes = new HashSet<>();

        try {
            recordSystemAttributesOnStart(request, userId, sessionId, resolvedAttributes);
            recordAttributes(trackAnalyticsEvent, method, joinPoint.getArgs(), request, null, null, resolvedAttributes);
            if (trackAnalyticsEvent.trackPayloadSize()) {
                BigDecimal payloadSize = estimatePayloadFromArgs(joinPoint.getArgs());
                if (payloadSize.signum() > 0) {
                    safeRecordMetricNum(controllerStageId, "PAYLOAD_SIZE_BYTES", payloadSize, "bytes");
                }
            }

            Object result = joinPoint.proceed();

            recordAttributes(trackAnalyticsEvent, method, joinPoint.getArgs(), request, result, null, resolvedAttributes);
            BigDecimal responseItems = AnalyticsValueEstimator.estimateItemCount(result);
            if (responseItems.signum() > 0) {
                safeRecordMetricNum(controllerStageId, "ITEM_COUNT", responseItems, "count");
            }
            int responseStatus = resolveSuccessStatus(result);
            if (responseStatus >= 400) {
                String responseError = "HTTP " + responseStatus;
                String errorClass = ErrorClassClassifier.classify(responseStatus, responseError, null);
                recordSystemAttributesOnFinish(eventCode, request, responseStatus, responseError, errorClass, resolvedAttributes);
                safeRecordMetricText(
                    controllerStageId,
                    "ERROR_CODE",
                    eventCode + "_HTTP_" + responseStatus,
                    null
                );
                safeRecordMetricText(
                    controllerStageId,
                    "ERROR_CLASS",
                    errorClass,
                    null
                );
                safeFinishStageError(controllerStageId, responseError);
                safeFinishEventError(eventUid, responseStatus, responseError);
            } else {
                recordSystemAttributesOnFinish(eventCode, request, responseStatus, null, null, resolvedAttributes);
                safeFinishStageSuccess(controllerStageId);
                safeFinishEventSuccess(eventUid, responseStatus);
            }
            return result;
        } catch (Throwable throwable) {
            int errorStatus = resolveErrorStatus(throwable);
            String errorMessage = safeMessage(throwable);
            String errorClass = ErrorClassClassifier.classify(errorStatus, errorMessage, throwable);
            if (request != null) {
                request.setAttribute(AnalyticsHttpErrorTrackingService.ERROR_THROWABLE_REQUEST_ATTRIBUTE, throwable);
            }
            recordSystemAttributesOnFinish(eventCode, request, errorStatus, errorMessage, errorClass, resolvedAttributes);
            recordAttributes(trackAnalyticsEvent, method, joinPoint.getArgs(), request, null, throwable, resolvedAttributes);
            safeRecordMetricText(
                controllerStageId,
                "ERROR_CODE",
                eventCode + "_FAIL",
                null
            );
            safeRecordMetricText(
                controllerStageId,
                "ERROR_CLASS",
                errorClass,
                null
            );
            safeFinishStageError(controllerStageId, errorMessage);
            safeFinishEventError(eventUid, errorStatus, errorMessage);
            throw throwable;
        } finally {
            context.popStageId(controllerStageId);
            AnalyticsEventContextHolder.clear();
            MDC.remove(ANALYTICS_EVENT_UID_MDC_KEY);
            restoreMdc(APP_MODULE_MDC_KEY, previousAppModule);
            restoreMdc(ANALYTICS_MODULE_MDC_KEY, previousAnalyticsModule);
        }
    }

    private void recordAttributes(
        TrackAnalyticsEvent trackAnalyticsEvent,
        Method method,
        Object[] args,
        HttpServletRequest request,
        Object result,
        Throwable throwable,
        Set<String> resolvedAttributes
    ) {
        recordDeclaredEntityAttributes(trackAnalyticsEvent, method, args, request, result, throwable, resolvedAttributes);
        if (trackAnalyticsEvent.attributes().length == 0) {
            return;
        }
        for (TrackAnalyticsAttribute attribute : trackAnalyticsEvent.attributes()) {
            if (attribute.code() == null || attribute.code().isBlank()) {
                continue;
            }
            if (resolvedAttributes.contains(attribute.code())) {
                continue;
            }
            String value = evaluateAttribute(method, args, request, result, throwable, attribute.value());
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                analyticsTrackingApi.addAttribute(AnalyticsEventContextHolder.get().eventUid(), attribute.code(), value);
                resolvedAttributes.add(attribute.code());
            } catch (RuntimeException ignored) {
                // Analytics must never break business flow.
            }
        }
    }

    private void recordDeclaredEntityAttributes(
        TrackAnalyticsEvent trackAnalyticsEvent,
        Method method,
        Object[] args,
        HttpServletRequest request,
        Object result,
        Throwable throwable,
        Set<String> resolvedAttributes
    ) {
        if (trackAnalyticsEvent.entityType() != null && !trackAnalyticsEvent.entityType().isBlank()) {
            String value = evaluateAttribute(method, args, request, result, throwable, trackAnalyticsEvent.entityType());
            addAttributeSafe("ENTITY_TYPE", value, resolvedAttributes);
        }
        if (trackAnalyticsEvent.entityId() != null && !trackAnalyticsEvent.entityId().isBlank()) {
            String value = evaluateAttribute(method, args, request, result, throwable, trackAnalyticsEvent.entityId());
            addAttributeSafe("ENTITY_ID", value, resolvedAttributes);
        }
    }

    private void recordSystemAttributesOnStart(
        HttpServletRequest request,
        Long userId,
        String sessionId,
        Set<String> resolvedAttributes
    ) {
        addAttributeSafe("HTTP_METHOD", request.getMethod(), resolvedAttributes);
        addAttributeSafe("HTTP_PATH", request.getRequestURI(), resolvedAttributes);
        addAttributeSafe("CLIENT_TYPE", resolveClientType(request), resolvedAttributes);
        addAttributeSafe("USER_AGENT", request.getHeader("User-Agent"), resolvedAttributes);
        addAttributeSafe("REFERRER", request.getHeader("Referer"), resolvedAttributes);
        addAttributeSafe("REQUEST_ID", resolveRequestId(request), resolvedAttributes);
        addAttributeSafe("SESSION_ID_HASH", sha256Hex(sessionId), resolvedAttributes);
        addAttributeSafe("USER_ID_HASH", userId == null ? null : sha256Hex(String.valueOf(userId)), resolvedAttributes);
    }

    private void recordSystemAttributesOnFinish(
        String eventCode,
        HttpServletRequest request,
        int statusCode,
        String errorMessage,
        String errorClass,
        Set<String> resolvedAttributes
    ) {
        addAttributeSafe("HTTP_STATUS", String.valueOf(statusCode), resolvedAttributes);
        if (statusCode >= 400) {
            addAttributeSafe("ERROR_CLASS", errorClass, resolvedAttributes);
            addAttributeSafe("ERROR_CODE", eventCode + "_HTTP_" + statusCode, resolvedAttributes);
        }
    }

    private void addAttributeSafe(String code, String value, Set<String> resolvedAttributes) {
        if (code == null || code.isBlank() || value == null || value.isBlank()) {
            return;
        }
        if (resolvedAttributes.contains(code)) {
            return;
        }
        try {
            analyticsTrackingApi.addAttribute(AnalyticsEventContextHolder.get().eventUid(), code, value);
            resolvedAttributes.add(code);
        } catch (RuntimeException ignored) {
            // Analytics must never break business flow.
        }
    }

    private String resolveClientType(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return "API";
        }
        return "WEB";
    }

    private String resolveRequestId(HttpServletRequest request) {
        String[] headers = {"X-Request-Id", "X-Correlation-Id", "X-Correlation-ID", "X-Trace-Id"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        String traceId = resolveTraceId(request);
        return traceId == null || traceId.isBlank() ? null : traceId;
    }

    private String sha256Hex(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private void safeRecordMetricNum(Long stageId, String metricTypeCode, BigDecimal value, String unit) {
        try {
            analyticsTrackingApi.recordMetricNum(stageId, metricTypeCode, value, unit);
        } catch (RuntimeException ignored) {
            // Analytics must never break business flow.
        }
    }

    private void safeRecordMetricText(Long stageId, String metricTypeCode, String value, String unit) {
        try {
            analyticsTrackingApi.recordMetricText(stageId, metricTypeCode, value, unit);
        } catch (RuntimeException ignored) {
            // Analytics must never break business flow.
        }
    }

    private void safeFinishStageSuccess(Long stageId) {
        try {
            analyticsTrackingApi.finishStageSuccess(stageId);
        } catch (RuntimeException ignored) {
            // Analytics must never break business flow.
        }
    }

    private void safeFinishStageError(Long stageId, String errorMessage) {
        try {
            analyticsTrackingApi.finishStageError(stageId, errorMessage);
        } catch (RuntimeException ignored) {
            // Analytics must never break business flow.
        }
    }

    private void safeFinishEventSuccess(UUID eventUid, Integer statusCode) {
        try {
            analyticsTrackingApi.finishEventSuccess(eventUid, statusCode);
        } catch (RuntimeException ignored) {
            // Analytics must never break business flow.
        }
    }

    private void safeFinishEventError(UUID eventUid, Integer statusCode, String errorMessage) {
        try {
            analyticsTrackingApi.finishEventError(eventUid, statusCode, errorMessage);
        } catch (RuntimeException ignored) {
            // Analytics must never break business flow.
        }
    }

    private String evaluateAttribute(
        Method method,
        Object[] args,
        HttpServletRequest request,
        Object result,
        Throwable throwable,
        String expression
    ) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("request", request);
            context.setVariable("result", result);
            context.setVariable("exception", throwable);
            context.setVariable("args", args);

            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
                context.setVariable("a" + i, args[i]);
                context.setVariable("arg" + i, args[i]);
            }
            String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
            if (parameterNames != null) {
                for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                    if (parameterNames[i] != null && !parameterNames[i].isBlank()) {
                        context.setVariable(parameterNames[i], args[i]);
                    }
                }
            }
            Object value = expressionParser.parseExpression(expression).getValue(context);
            if (value == null) {
                return null;
            }
            return String.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private HttpServletRequest resolveRequest(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest request) {
                return request;
            }
        }
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String resolveEventCode(
        TrackAnalyticsEvent trackAnalyticsEvent,
        Method method,
        Object[] args,
        HttpServletRequest request
    ) {
        if (trackAnalyticsEvent.codeExpression() == null || trackAnalyticsEvent.codeExpression().isBlank()) {
            return trackAnalyticsEvent.code();
        }
        String resolved = evaluateAttribute(method, args, request, null, null, trackAnalyticsEvent.codeExpression());
        if (resolved == null || resolved.isBlank()) {
            return trackAnalyticsEvent.code();
        }
        return resolved;
    }

    private Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (joinPoint.getTarget() == null) {
            return method;
        }
        return AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
    }

    private static String resolveTraceId(HttpServletRequest request) {
        Object traceAttr = request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE);
        if (traceAttr instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String header = request.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        if (header != null && !header.trim().isBlank()) {
            return header.trim();
        }
        return "";
    }

    private static int resolveSuccessStatus(Object result) {
        if (result instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getStatusCode().value();
        }
        return 200;
    }

    private static int resolveErrorStatus(Throwable throwable) {
        if (throwable instanceof IllegalArgumentException || throwable instanceof IllegalStateException) {
            return 400;
        }
        return 500;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unexpected error";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + trimToMax(message, 900);
    }

    private static String trimToMax(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private static BigDecimal estimatePayloadFromArgs(Object[] args) {
        long total = 0L;
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof HttpServletRequest) {
                continue;
            }
            BigDecimal estimated = AnalyticsValueEstimator.estimatePayloadBytes(arg);
            total += estimated.longValue();
        }
        return BigDecimal.valueOf(Math.max(total, 0L));
    }

    private static void restoreMdc(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }

}

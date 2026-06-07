package com.example.gqw.analytics.logging;

import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.analytics.aop.AnalyticsEventAspect;
import com.example.gqw.analytics.aop.AnalyticsEventContextHolder;
import com.example.gqw.analytics.service.AnalyticsTrackingApi;
import com.example.gqw.config.TraceIdFilter;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.aop.support.AopUtils;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@ConditionalOnProperty(value = "app.analytics.method-logging.enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsMethodLoggingAspect {

    private static final String ANALYTICS_PACKAGE_PREFIX =
        AnalyticsMethodLoggingAspect.class.getPackageName().replace(".logging", "") + ".";
    private static final int MAX_FIELD_LENGTH = 240;
    private static final long CONTROLLER_WARN_MS = 800L;
    private static final long SERVICE_WARN_MS = 500L;
    private static final long REPOSITORY_WARN_MS = 250L;

    private final AnalyticsTrackingApi analyticsTrackingApi;
    private final List<AnalyticsOperationDescriptionResolver> operationDescriptionResolvers;
    private final String basePackage;
    private final boolean controllerEnabled;
    private final boolean serviceEnabled;
    private final boolean repositoryEnabled;

    public AnalyticsMethodLoggingAspect(
        AnalyticsTrackingApi analyticsTrackingApi,
        List<AnalyticsOperationDescriptionResolver> operationDescriptionResolvers,
        @Value("${app.analytics.method-logging.base-package:}") String basePackage,
        @Value("${app.analytics.method-logging.controller-enabled:true}") boolean controllerEnabled,
        @Value("${app.analytics.method-logging.service-enabled:true}") boolean serviceEnabled,
        @Value("${app.analytics.method-logging.repository-enabled:false}") boolean repositoryEnabled
    ) {
        this.analyticsTrackingApi = analyticsTrackingApi;
        this.operationDescriptionResolvers = operationDescriptionResolvers;
        this.basePackage = basePackage == null ? "" : basePackage.trim();
        this.controllerEnabled = controllerEnabled;
        this.serviceEnabled = serviceEnabled;
        this.repositoryEnabled = repositoryEnabled;
    }

    @Around(
        "execution(public * *(..))"
            + " && (within(@org.springframework.stereotype.Controller *)"
            + " || within(@org.springframework.web.bind.annotation.RestController *)"
            + " || within(@org.springframework.stereotype.Service *)"
            + " || within(@org.springframework.stereotype.Repository *))"
    )
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!isWithinBasePackage(joinPoint, basePackage)) {
            return joinPoint.proceed();
        }
        if (isAnalyticsInfrastructure(joinPoint)) {
            return joinPoint.proceed();
        }

        Logger log = resolveLogger(joinPoint);
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String layer = resolveLayer(signature.getDeclaringTypeName());
        if (!isLayerEnabled(layer)) {
            return joinPoint.proceed();
        }
        String operation = resolveOperationDescription(joinPoint, signature, className, methodName, layer);
        String traceId = safeMdc(TraceIdFilter.TRACE_ID_MDC_KEY);
        String analyticsEventUid = safeMdc(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY);
        String argsSummary = summarizeArgs(joinPoint.getArgs());
        Long currentStageId = AnalyticsEventContextHolder.currentStageId();

        Instant startedAt = Instant.now();
        Instant logWindowStartedAt = Instant.now();
        if (log.isTraceEnabled()) {
            log.trace(
                "Method call details {}.{}: layer={}, traceId='{}', eventUid='{}', args={}",
                className,
                methodName,
                layer,
                traceId,
                analyticsEventUid,
                argsSummary
            );
        }
        if (log.isDebugEnabled()) {
            log.debug(
                "Method started {}.{} (operation='{}', layer={}, traceId='{}', eventUid='{}').",
                className,
                methodName,
                operation,
                layer,
                traceId,
                analyticsEventUid
            );
        }
        log.info(
            "Method started {}.{} (operation='{}', layer={}, traceId='{}', eventUid='{}').",
            className,
            methodName,
            operation,
            layer,
            traceId,
            analyticsEventUid
        );

        try {
            Object result = joinPoint.proceed();
            Instant endedAt = Instant.now();
            long durationMs = Duration.between(startedAt, endedAt).toMillis();
            String resultSummary = summarizeValue(result);
            int responseStatus = resolveHttpStatus(result);
            boolean isHttpError = responseStatus >= 400;

            if (isHttpError) {
                log.warn(
                    "HTTP error in {}.{}: operation='{}', layer={}, status={}, durationMs={}, traceId='{}', eventUid='{}', response='{}'.",
                    className,
                    methodName,
                    operation,
                    layer,
                    responseStatus,
                    durationMs,
                    traceId,
                    analyticsEventUid,
                    resultSummary
                );
            } else {
                log.info(
                    "Method finished successfully {}.{}: operation='{}', layer={}, durationMs={}, traceId='{}', eventUid='{}'.",
                    className,
                    methodName,
                    operation,
                    layer,
                    durationMs,
                    traceId,
                    analyticsEventUid
                );
            }
            if (log.isDebugEnabled()) {
                log.debug(
                    "Method result {}.{}: {}",
                    className,
                    methodName,
                    resultSummary
                );
            }
            if (durationMs >= warnThresholdForLayer(layer)) {
                log.warn(
                    "Slow execution {}.{}: operation='{}', layer={}, durationMs={}, traceId='{}', eventUid='{}'.",
                    className,
                    methodName,
                    operation,
                    layer,
                    durationMs,
                    traceId,
                    analyticsEventUid
                );
            }
            Instant logWindowEndedAt = Instant.now();
            markStageLogWindow(currentStageId, logWindowStartedAt, logWindowEndedAt);
            return result;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            Instant endedAt = Instant.now();
            long durationMs = Duration.between(startedAt, endedAt).toMillis();
            log.warn(
                "Business error in {}.{}: operation='{}', layer={}, durationMs={}, traceId='{}', eventUid='{}', error='{}', cause='{}', args={}",
                className,
                methodName,
                operation,
                layer,
                durationMs,
                traceId,
                analyticsEventUid,
                summarizeThrowable(ex),
                summarizeRootCause(ex),
                argsSummary
            );
            Instant logWindowEndedAt = Instant.now();
            markStageLogWindow(currentStageId, logWindowStartedAt, logWindowEndedAt);
            throw ex;
        } catch (Throwable ex) {
            Instant endedAt = Instant.now();
            long durationMs = Duration.between(startedAt, endedAt).toMillis();
            log.error(
                "Technical error in {}.{}: operation='{}', layer={}, durationMs={}, traceId='{}', eventUid='{}', error='{}', cause='{}', args={}",
                className,
                methodName,
                operation,
                layer,
                durationMs,
                traceId,
                analyticsEventUid,
                summarizeThrowable(ex),
                summarizeRootCause(ex),
                argsSummary,
                ex
            );
            Instant logWindowEndedAt = Instant.now();
            markStageLogWindow(currentStageId, logWindowStartedAt, logWindowEndedAt);
            throw ex;
        }
    }

    private void markStageLogWindow(Long stageId, Instant startedAt, Instant endedAt) {
        if (stageId == null || startedAt == null || endedAt == null) {
            return;
        }
        try {
            analyticsTrackingApi.markStageLogWindow(stageId, startedAt, endedAt);
        } catch (RuntimeException ignored) {
            // Detailed logging must never break business flow.
        }
    }

    private String resolveOperationDescription(
        ProceedingJoinPoint joinPoint,
        MethodSignature signature,
        String className,
        String methodName,
        String layer
    ) {
        String explicitFromAnnotation = resolveOperationDescriptionFromAnnotation(joinPoint, signature);
        if (explicitFromAnnotation != null && !explicitFromAnnotation.isBlank()) {
            return explicitFromAnnotation;
        }
        for (AnalyticsOperationDescriptionResolver resolver : operationDescriptionResolvers) {
            if (resolver == null) {
                continue;
            }
            String resolved = resolver.resolve(className, methodName, layer);
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }
        return className + "." + methodName;
    }

    private String resolveOperationDescriptionFromAnnotation(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null
            ? joinPoint.getTarget().getClass()
            : signature.getDeclaringType();
        Method mostSpecificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

        TrackAnalyticsEvent annotation = AnnotationUtils.findAnnotation(mostSpecificMethod, TrackAnalyticsEvent.class);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(method, TrackAnalyticsEvent.class);
        }
        if (annotation == null || annotation.operationDescription() == null) {
            return null;
        }
        String value = annotation.operationDescription().trim();
        return value.isBlank() ? null : value;
    }

    private boolean isLayerEnabled(String layer) {
        return switch (layer) {
            case "CONTROLLER" -> controllerEnabled;
            case "SERVICE" -> serviceEnabled;
            case "REPOSITORY" -> repositoryEnabled;
            default -> false;
        };
    }

    private static Logger resolveLogger(ProceedingJoinPoint joinPoint) {
        String declaringTypeName = joinPoint.getSignature().getDeclaringTypeName();
        if (declaringTypeName != null && declaringTypeName.contains(".repository.")) {
            return LoggerFactory.getLogger(joinPoint.getSignature().getDeclaringType());
        }
        Object target = joinPoint.getTarget();
        if (target != null) {
            return LoggerFactory.getLogger(target.getClass());
        }
        return LoggerFactory.getLogger(joinPoint.getSignature().getDeclaringType());
    }

    private static String resolveLayer(String declaringTypeName) {
        if (declaringTypeName.contains(".controller.")) {
            return "CONTROLLER";
        }
        if (declaringTypeName.contains(".service.")) {
            return "SERVICE";
        }
        if (declaringTypeName.contains(".repository.")) {
            return "REPOSITORY";
        }
        if (declaringTypeName.contains("Repository")) {
            return "REPOSITORY";
        }
        return "UNKNOWN";
    }

    private static long warnThresholdForLayer(String layer) {
        return switch (layer) {
            case "CONTROLLER" -> CONTROLLER_WARN_MS;
            case "SERVICE" -> SERVICE_WARN_MS;
            case "REPOSITORY" -> REPOSITORY_WARN_MS;
            default -> SERVICE_WARN_MS;
        };
    }

    private static int resolveHttpStatus(Object result) {
        if (result instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getStatusCode().value();
        }
        return 200;
    }

    private static String safeMdc(String key) {
        String value = MDC.get(key);
        return value == null ? "" : value;
    }

    private static String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (Object arg : args) {
            joiner.add(summarizeValue(arg));
        }
        return trim(joiner.toString());
    }

    private static String summarizeValue(Object value) {
        if (value == null) {
            return "null";
        }
        String type = value.getClass().getSimpleName();
        if (value instanceof Collection<?> collection) {
            return type + "(size=" + collection.size() + ")";
        }
        if (value instanceof Map<?, ?> map) {
            return type + "(size=" + map.size() + ")";
        }
        if (value.getClass().isArray()) {
            return type + "(length=" + Array.getLength(value) + ")";
        }
        if (value instanceof ResponseEntity<?> responseEntity) {
            Object body = responseEntity.getBody();
            return type + "(status=" + responseEntity.getStatusCode().value()
                + ", body=" + (body == null ? "null" : summarizeValue(body)) + ")";
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return type;
        }
        return type + "(" + trim(text) + ")";
    }

    private static String summarizeThrowable(Throwable throwable) {
        if (throwable == null) {
            return "-";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + trim(message);
    }

    private static String summarizeRootCause(Throwable throwable) {
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
        return root.getClass().getSimpleName() + ": " + trim(message);
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= MAX_FIELD_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_FIELD_LENGTH) + "...";
    }

    private static boolean isAnalyticsInfrastructure(ProceedingJoinPoint joinPoint) {
        if (isAnalyticsType(joinPoint.getSignature().getDeclaringTypeName())) {
            return true;
        }
        Object target = joinPoint.getTarget();
        if (target != null) {
            if (isAnalyticsType(target.getClass().getName())) {
                return true;
            }
            for (Class<?> iface : target.getClass().getInterfaces()) {
                if (isAnalyticsType(iface.getName())) {
                    return true;
                }
            }
        }
        Object proxy = joinPoint.getThis();
        if (proxy != null) {
            if (isAnalyticsType(proxy.getClass().getName())) {
                return true;
            }
            for (Class<?> iface : proxy.getClass().getInterfaces()) {
                if (isAnalyticsType(iface.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAnalyticsType(String typeName) {
        return typeName != null && typeName.startsWith(ANALYTICS_PACKAGE_PREFIX);
    }

    private static boolean isWithinBasePackage(ProceedingJoinPoint joinPoint, String basePackage) {
        if (basePackage == null || basePackage.isBlank()) {
            return true;
        }
        if (isInBasePackage(joinPoint.getSignature().getDeclaringTypeName(), basePackage)) {
            return true;
        }
        Object target = joinPoint.getTarget();
        if (target != null && isInBasePackage(target.getClass().getName(), basePackage)) {
            return true;
        }
        Object proxy = joinPoint.getThis();
        return proxy != null && isInBasePackage(proxy.getClass().getName(), basePackage);
    }

    private static boolean isInBasePackage(String className, String basePackage) {
        return className != null && className.startsWith(basePackage + ".");
    }
}

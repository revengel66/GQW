package com.example.gqw.analytics.aop;

import com.example.gqw.analytics.service.AnalyticsTrackingApi;
import com.example.gqw.analytics.service.ErrorClassClassifier;
import com.example.gqw.analytics.service.AnalyticsInstrumentationPolicy;
import com.example.gqw.analytics.service.AnalyticsLoggingPolicy;
import com.example.gqw.analytics.service.AnalyticsStrictWarningEventService;
import com.example.gqw.analytics.support.AnalyticsTraceContext;
import java.math.BigDecimal;
import java.time.Instant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AnalyticsServiceStageAspect {

    private static final String ANALYTICS_PACKAGE_PREFIX =
        AnalyticsServiceStageAspect.class.getPackageName().replace(".aop", "") + ".";
    private static final Logger log = LoggerFactory.getLogger(AnalyticsServiceStageAspect.class);

    private final AnalyticsTrackingApi analyticsTrackingApi;
    private final AnalyticsInstrumentationPolicy instrumentationPolicy;
    private final AnalyticsLoggingPolicy loggingPolicy;
    private final AnalyticsStrictWarningEventService strictWarningEventService;

    public AnalyticsServiceStageAspect(
        AnalyticsTrackingApi analyticsTrackingApi,
        AnalyticsInstrumentationPolicy instrumentationPolicy,
        AnalyticsLoggingPolicy loggingPolicy,
        AnalyticsStrictWarningEventService strictWarningEventService
    ) {
        this.analyticsTrackingApi = analyticsTrackingApi;
        this.instrumentationPolicy = instrumentationPolicy;
        this.loggingPolicy = loggingPolicy;
        this.strictWarningEventService = strictWarningEventService;
    }

    @Around(
        "within(@org.springframework.stereotype.Service *) "
            + "&& execution(public * *(..))"
    )
    public Object aroundServiceStage(ProceedingJoinPoint joinPoint) throws Throwable {
        AnalyticsEventContext context = AnalyticsEventContextHolder.get();
        if (context == null) {
            return joinPoint.proceed();
        }
        if (isAnalyticsInfrastructure(joinPoint)) {
            return joinPoint.proceed();
        }
        if (!instrumentationPolicy.isEnabled()) {
            return joinPoint.proceed();
        }
        if (hasCustomAnalyticsLayer(joinPoint)) {
            return joinPoint.proceed();
        }

        Long stageId;
        try {
            stageId = analyticsTrackingApi.startStage(context.eventUid(), "SERVICE", context.nextStageOrder());
        } catch (RuntimeException exception) {
            if (loggingPolicy.isStrictWarningsEnabled()) {
                log.warn(
                    "Analytics stage skipped: stageType=SERVICE class={} method={} traceId={} eventUid={} reason={}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    safeMdc(AnalyticsTraceContext.TRACE_ID_MDC_KEY),
                    context.eventUid(),
                    exception.getMessage(),
                    exception
                );
            }
            strictWarningEventService.record(
                "stage",
                "SERVICE",
                exception.getMessage(),
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                null,
                safeMdc(AnalyticsTraceContext.TRACE_ID_MDC_KEY),
                String.valueOf(context.eventUid()),
                null
            );
            return joinPoint.proceed();
        }
        context.pushStageId(stageId);
        Instant stageLogStartedAt = Instant.now();
        try {
            Object result = joinPoint.proceed();
            BigDecimal itemCount = AnalyticsValueEstimator.estimateItemCount(result);
            if (itemCount.signum() > 0) {
                try {
                    analyticsTrackingApi.recordMetricNum(stageId, "ITEM_COUNT", itemCount, "count");
                } catch (RuntimeException ignored) {
                    // Analytics must never break business flow.
                }
            }
            try {
                analyticsTrackingApi.finishStageSuccess(stageId);
            } catch (RuntimeException ignored) {
                // Analytics must never break business flow.
            }
            return result;
        } catch (Throwable throwable) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String metricErrorCode = signature.getDeclaringType().getSimpleName() + "." + signature.getName() + "_FAIL";
            String errorClass = ErrorClassClassifier.classify(null, safeMessage(throwable), throwable);
            try {
                analyticsTrackingApi.recordMetricText(stageId, "ERROR_CODE", metricErrorCode, null);
            } catch (RuntimeException ignored) {
                // Analytics must never break business flow.
            }
            try {
                analyticsTrackingApi.recordMetricText(stageId, "ERROR_CLASS", errorClass, null);
            } catch (RuntimeException ignored) {
                // Optional metric for backward compatibility.
            }
            try {
                analyticsTrackingApi.finishStageError(stageId, safeMessage(throwable));
            } catch (RuntimeException ignored) {
                // Analytics must never break business flow.
            }
            throw throwable;
        } finally {
            Instant stageLogEndedAt = Instant.now();
            try {
                analyticsTrackingApi.markStageLogWindow(stageId, stageLogStartedAt, stageLogEndedAt);
            } catch (RuntimeException ignored) {
                // Analytics must never break business flow.
            }
            context.popStageId(stageId);
        }
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "Service execution failed";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() > 900) {
            normalized = normalized.substring(0, 897) + "...";
        }
        return throwable.getClass().getSimpleName() + ": " + normalized;
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

    private static boolean hasCustomAnalyticsLayer(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        java.lang.reflect.Method signatureMethod = signature.getMethod();
        Object target = joinPoint.getTarget();
        java.lang.reflect.Method method = target == null
            ? signatureMethod
            : AopUtils.getMostSpecificMethod(signatureMethod, target.getClass());
        if (AnnotationUtils.findAnnotation(method, TrackAnalyticsLayer.class) != null) {
            return true;
        }
        if (target != null && AnnotationUtils.findAnnotation(target.getClass(), TrackAnalyticsLayer.class) != null) {
            return true;
        }
        return AnnotationUtils.findAnnotation(signature.getDeclaringType(), TrackAnalyticsLayer.class) != null;
    }

    private static String safeMdc(String key) {
        String value = MDC.get(key);
        return value == null ? "" : value;
    }
}

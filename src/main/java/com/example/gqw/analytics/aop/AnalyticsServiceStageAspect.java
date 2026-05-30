package com.example.gqw.analytics.aop;

import com.example.gqw.analytics.service.AnalyticsTrackingApi;
import com.example.gqw.analytics.service.ErrorClassClassifier;
import java.math.BigDecimal;
import java.time.Instant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AnalyticsServiceStageAspect {

    private static final String ANALYTICS_PACKAGE_PREFIX =
        AnalyticsServiceStageAspect.class.getPackageName().replace(".aop", "") + ".";

    private final AnalyticsTrackingApi analyticsTrackingApi;

    public AnalyticsServiceStageAspect(AnalyticsTrackingApi analyticsTrackingApi) {
        this.analyticsTrackingApi = analyticsTrackingApi;
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

        Long stageId;
        try {
            stageId = analyticsTrackingApi.startStage(context.eventUid(), "SERVICE", context.nextStageOrder());
        } catch (RuntimeException ignored) {
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
}

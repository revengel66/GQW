package com.example.gqw.analytics.aop;

import com.example.gqw.analytics.service.AnalyticsTrackingApi;
import com.example.gqw.analytics.service.ErrorClassClassifier;
import com.example.gqw.analytics.service.AnalyticsInstrumentationPolicy;
import com.example.gqw.analytics.service.AnalyticsLoggingPolicy;
import com.example.gqw.config.TraceIdFilter;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class AnalyticsRepositoryStageAspect {

    private static final String ANALYTICS_PACKAGE_PREFIX =
        AnalyticsRepositoryStageAspect.class.getPackageName().replace(".aop", "") + ".";
    private static final Logger DB_STAGE_LOG = LoggerFactory.getLogger("analytics.db.stage");

    private final AnalyticsTrackingApi analyticsTrackingApi;
    private final AnalyticsInstrumentationPolicy instrumentationPolicy;
    private final AnalyticsLoggingPolicy loggingPolicy;

    public AnalyticsRepositoryStageAspect(
        AnalyticsTrackingApi analyticsTrackingApi,
        AnalyticsInstrumentationPolicy instrumentationPolicy,
        AnalyticsLoggingPolicy loggingPolicy
    ) {
        this.analyticsTrackingApi = analyticsTrackingApi;
        this.instrumentationPolicy = instrumentationPolicy;
        this.loggingPolicy = loggingPolicy;
    }

    @Around("execution(public * org.springframework.data.repository.CrudRepository+.*(..))")
    public Object aroundRepositoryStage(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!instrumentationPolicy.isEnabled()) {
            return joinPoint.proceed();
        }
        AnalyticsEventContext context = AnalyticsEventContextHolder.get();
        if (context == null) {
            return joinPoint.proceed();
        }
        if (isAnalyticsInfrastructure(joinPoint)) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String traceId = safeMdc(TraceIdFilter.TRACE_ID_MDC_KEY);
        String eventUid = safeMdc(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY);
        Logger log = resolveLogger(joinPoint);

        Long stageId;
        try {
            stageId = analyticsTrackingApi.startStage(context.eventUid(), "DATABASE", context.nextStageOrder());
            analyticsTrackingApi.recordMetricNum(stageId, "DB_QUERY_COUNT", BigDecimal.ONE, "count");
        } catch (RuntimeException exception) {
            if (loggingPolicy.isStrictWarningsEnabled()) {
                DB_STAGE_LOG.warn(
                    "Analytics stage skipped: stageType=DATABASE class={} method={} traceId={} eventUid={} reason={}",
                    className,
                    methodName,
                    traceId,
                    eventUid,
                    exception.getMessage(),
                    exception
                );
            }
            return joinPoint.proceed();
        }
        context.pushStageId(stageId);
        Instant stageLogStartedAt = Instant.now();
        if (loggingPolicy.isDatabaseEnabled() && loggingPolicy.isInfoEnabled()) {
            DB_STAGE_LOG.info(
                "DB_STAGE_START stageId={} method={}.{} traceId='{}' eventUid='{}'",
                stageId,
                className,
                methodName,
                traceId,
                eventUid
            );
        }
        if (loggingPolicy.isDatabaseEnabled() && loggingPolicy.isInfoEnabled() && log.isTraceEnabled()) {
            log.trace(
                "Database call started {}.{}: layer=DATABASE, stageId={}, traceId='{}', eventUid='{}'.",
                className,
                methodName,
                stageId,
                traceId,
                eventUid
            );
        }
        try {
            Object result = joinPoint.proceed();
            BigDecimal responseSize = AnalyticsValueEstimator.estimatePayloadBytes(result);
            if (responseSize.signum() > 0) {
                try {
                    analyticsTrackingApi.recordMetricNum(stageId, "RESPONSE_SIZE_BYTES", responseSize, "bytes");
                } catch (RuntimeException ignored) {
                    // Analytics must never break business flow.
                }
            }
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
            long durationMs = Duration.between(stageLogStartedAt, Instant.now()).toMillis();
            if (loggingPolicy.isDatabaseEnabled() && loggingPolicy.isInfoEnabled()) {
                log.info(
                    "Database call completed successfully {}.{}: layer=DATABASE, stageId={}, durationMs={}, traceId='{}', eventUid='{}'.",
                    className,
                    methodName,
                    stageId,
                    durationMs,
                    traceId,
                    eventUid
                );
                DB_STAGE_LOG.info(
                    "DB_STAGE_END stageId={} method={}.{} durationMs={} traceId='{}' eventUid='{}'",
                    stageId,
                    className,
                    methodName,
                    durationMs,
                    traceId,
                    eventUid
                );
            }
            return result;
        } catch (Throwable throwable) {
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
            long durationMs = Duration.between(stageLogStartedAt, Instant.now()).toMillis();
            if (loggingPolicy.isDatabaseEnabled() && loggingPolicy.isErrorEnabled()) {
                log.error(
                    "Database call failed {}.{}: layer=DATABASE, stageId={}, durationMs={}, traceId='{}', eventUid='{}'.",
                    className,
                    methodName,
                    stageId,
                    durationMs,
                    traceId,
                    eventUid,
                    throwable
                );
                DB_STAGE_LOG.error(
                    "DB_STAGE_ERROR stageId={} method={}.{} durationMs={} traceId='{}' eventUid='{}'",
                    stageId,
                    className,
                    methodName,
                    durationMs,
                    traceId,
                    eventUid,
                    throwable
                );
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

    private static String safeMdc(String key) {
        String value = MDC.get(key);
        return value == null ? "" : value;
    }

    private static Logger resolveLogger(ProceedingJoinPoint joinPoint) {
        Object target = joinPoint.getTarget();
        if (target != null) {
            return org.slf4j.LoggerFactory.getLogger(target.getClass());
        }
        return org.slf4j.LoggerFactory.getLogger(joinPoint.getSignature().getDeclaringType());
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "Repository execution failed";
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

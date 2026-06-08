package com.example.gqw.analytics.aop;

import com.example.gqw.analytics.entity.StageType;
import com.example.gqw.analytics.repository.StageTypeRepository;
import com.example.gqw.analytics.service.AnalyticsInstrumentationPolicy;
import com.example.gqw.analytics.service.AnalyticsLoggingPolicy;
import com.example.gqw.analytics.service.AnalyticsTrackingApi;
import com.example.gqw.config.TraceIdFilter;
import java.time.Instant;
import java.util.Locale;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 19)
public class AnalyticsLayerStageAspect {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsLayerStageAspect.class);
    private static final Logger LAYER_STAGE_LOG = LoggerFactory.getLogger("analytics.layer.stage");

    private final AnalyticsTrackingApi analyticsTrackingApi;
    private final StageTypeRepository stageTypeRepository;
    private final AnalyticsInstrumentationPolicy instrumentationPolicy;
    private final AnalyticsLoggingPolicy loggingPolicy;

    public AnalyticsLayerStageAspect(
        AnalyticsTrackingApi analyticsTrackingApi,
        StageTypeRepository stageTypeRepository,
        AnalyticsInstrumentationPolicy instrumentationPolicy,
        AnalyticsLoggingPolicy loggingPolicy
    ) {
        this.analyticsTrackingApi = analyticsTrackingApi;
        this.stageTypeRepository = stageTypeRepository;
        this.instrumentationPolicy = instrumentationPolicy;
        this.loggingPolicy = loggingPolicy;
    }

    @Around("@within(com.example.gqw.analytics.aop.TrackAnalyticsLayer) || @annotation(com.example.gqw.analytics.aop.TrackAnalyticsLayer)")
    public Object aroundAnalyticsLayer(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!instrumentationPolicy.isEnabled()) {
            return joinPoint.proceed();
        }
        TrackAnalyticsLayer annotation = resolveAnnotation(joinPoint);
        if (annotation == null || !annotation.enabled()) {
            return joinPoint.proceed();
        }

        AnalyticsEventContext context = AnalyticsEventContextHolder.get();
        if (context == null) {
            if (loggingPolicy.isCustomLayerEnabled() && loggingPolicy.isInfoEnabled()) {
                log.debug(
                    "Analytics layer skipped: code={}, class={}, method={}, reason=no active analytics event",
                    annotation.code(),
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName()
                );
            }
            return joinPoint.proceed();
        }

        String stageTypeCode = normalizeStageTypeCode(annotation.code());
        if (stageTypeCode == null) {
            if (loggingPolicy.isStrictWarningsEnabled()) {
                log.warn(
                    "Analytics stage skipped: stageType={}, class={}, method={}, eventUid={}, traceId={}, reason=Blank stage type code",
                    annotation.code(),
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    context.eventUid(),
                    safeMdc(TraceIdFilter.TRACE_ID_MDC_KEY)
                );
            }
            return joinPoint.proceed();
        }

        StageType stageType = stageTypeRepository.findById(stageTypeCode).orElse(null);
        if (stageType == null) {
            if (loggingPolicy.isStrictWarningsEnabled()) {
                log.warn(
                    "Analytics stage skipped: stageType={}, class={}, method={}, eventUid={}, traceId={}, reason=Unknown stage type",
                    stageTypeCode,
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    context.eventUid(),
                    safeMdc(TraceIdFilter.TRACE_ID_MDC_KEY)
                );
            }
            return joinPoint.proceed();
        }
        if (!Boolean.TRUE.equals(stageType.getIsActive())) {
            if (loggingPolicy.isStrictWarningsEnabled()) {
                log.warn(
                    "Analytics stage skipped: stageType={}, class={}, method={}, eventUid={}, traceId={}, reason=Inactive stage type",
                    stageTypeCode,
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    context.eventUid(),
                    safeMdc(TraceIdFilter.TRACE_ID_MDC_KEY)
                );
            }
            return joinPoint.proceed();
        }

        Long stageId;
        try {
            stageId = analyticsTrackingApi.startStage(context.eventUid(), stageTypeCode, context.nextStageOrder());
        } catch (RuntimeException exception) {
            if (loggingPolicy.isStrictWarningsEnabled()) {
                log.warn(
                    "Analytics stage skipped: stageType={}, class={}, method={}, eventUid={}, traceId={}, reason={}",
                    stageTypeCode,
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    context.eventUid(),
                    safeMdc(TraceIdFilter.TRACE_ID_MDC_KEY),
                    exception.getMessage(),
                    exception
                );
            }
            return joinPoint.proceed();
        }

        context.pushStageId(stageId);
        Instant stageLogStartedAt = Instant.now();
        String operation = resolveOperation(annotation, joinPoint);
        if (loggingPolicy.isCustomLayerEnabled() && loggingPolicy.isInfoEnabled()) {
            LAYER_STAGE_LOG.info(
                "LAYER_STAGE_START stageId={} layer={} operation='{}' traceId='{}' eventUid='{}'",
                stageId,
                stageTypeCode,
                operation,
                safeMdc(TraceIdFilter.TRACE_ID_MDC_KEY),
                context.eventUid()
            );
        }
        try {
            Object result = joinPoint.proceed();
            try {
                analyticsTrackingApi.finishStageSuccess(stageId);
            } catch (RuntimeException ignored) {
                // Analytics must never break business flow.
            }
            if (loggingPolicy.isCustomLayerEnabled() && loggingPolicy.isInfoEnabled()) {
                LAYER_STAGE_LOG.info(
                    "LAYER_STAGE_END stageId={} layer={} operation='{}' traceId='{}' eventUid='{}'",
                    stageId,
                    stageTypeCode,
                    operation,
                    safeMdc(TraceIdFilter.TRACE_ID_MDC_KEY),
                    context.eventUid()
                );
            }
            return result;
        } catch (Throwable throwable) {
            try {
                analyticsTrackingApi.finishStageError(stageId, safeMessage(throwable));
            } catch (RuntimeException ignored) {
                // Analytics must never break business flow.
            }
            if (loggingPolicy.isCustomLayerEnabled() && loggingPolicy.isErrorEnabled()) {
                LAYER_STAGE_LOG.error(
                    "LAYER_STAGE_ERROR stageId={} layer={} operation='{}' traceId='{}' eventUid='{}'",
                    stageId,
                    stageTypeCode,
                    operation,
                    safeMdc(TraceIdFilter.TRACE_ID_MDC_KEY),
                    context.eventUid(),
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

    private TrackAnalyticsLayer resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        java.lang.reflect.Method signatureMethod = signature.getMethod();
        Object target = joinPoint.getTarget();
        java.lang.reflect.Method method = target == null
            ? signatureMethod
            : AopUtils.getMostSpecificMethod(signatureMethod, target.getClass());

        TrackAnalyticsLayer methodAnnotation = AnnotationUtils.findAnnotation(method, TrackAnalyticsLayer.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        if (target != null) {
            TrackAnalyticsLayer targetAnnotation = AnnotationUtils.findAnnotation(target.getClass(), TrackAnalyticsLayer.class);
            if (targetAnnotation != null) {
                return targetAnnotation;
            }
        }
        return AnnotationUtils.findAnnotation(signature.getDeclaringType(), TrackAnalyticsLayer.class);
    }

    private String resolveOperation(TrackAnalyticsLayer annotation, ProceedingJoinPoint joinPoint) {
        if (annotation.operation() != null && !annotation.operation().isBlank()) {
            return annotation.operation().trim();
        }
        return joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + joinPoint.getSignature().getName();
    }

    private static String normalizeStageTypeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String safeMdc(String key) {
        String value = MDC.get(key);
        return value == null ? "" : value;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "Layer execution failed";
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
}

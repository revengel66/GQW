package com.example.gqw.analytics.aop;

import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.entity.StageMetricType;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import com.example.gqw.analytics.service.AnalyticsInstrumentationPolicy;
import com.example.gqw.analytics.service.AnalyticsLoggingPolicy;
import com.example.gqw.analytics.service.AnalyticsTrackingApi;
import com.example.gqw.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 35)
public class AnalyticsStageMetricAspect {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsStageMetricAspect.class);

    private final AnalyticsTrackingApi analyticsTrackingApi;
    private final StageMetricTypeRepository stageMetricTypeRepository;
    private final AnalyticsInstrumentationPolicy instrumentationPolicy;
    private final AnalyticsLoggingPolicy loggingPolicy;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AnalyticsStageMetricAspect(
        AnalyticsTrackingApi analyticsTrackingApi,
        StageMetricTypeRepository stageMetricTypeRepository,
        AnalyticsInstrumentationPolicy instrumentationPolicy,
        AnalyticsLoggingPolicy loggingPolicy
    ) {
        this.analyticsTrackingApi = analyticsTrackingApi;
        this.stageMetricTypeRepository = stageMetricTypeRepository;
        this.instrumentationPolicy = instrumentationPolicy;
        this.loggingPolicy = loggingPolicy;
    }

    @Around("@annotation(com.example.gqw.analytics.aop.TrackAnalyticsStageMetric)")
    public Object aroundStageMetric(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!instrumentationPolicy.isEnabled()) {
            return joinPoint.proceed();
        }
        Method method = resolveAnnotatedMethod(joinPoint);
        TrackAnalyticsStageMetric annotation = AnnotationUtils.findAnnotation(method, TrackAnalyticsStageMetric.class);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        try {
            Object result = joinPoint.proceed();
            recordStageMetrics(annotation, method, joinPoint.getArgs(), resolveRequest(joinPoint.getArgs()), result, null);
            return result;
        } catch (Throwable throwable) {
            recordStageMetrics(annotation, method, joinPoint.getArgs(), resolveRequest(joinPoint.getArgs()), null, throwable);
            throw throwable;
        }
    }

    private void recordStageMetrics(
        TrackAnalyticsStageMetric annotation,
        Method method,
        Object[] args,
        HttpServletRequest request,
        Object result,
        Throwable throwable
    ) {
        for (MetricSpec metric : collectMetricSpecs(annotation)) {
            recordStageMetric(metric, method, args, request, result, throwable);
        }
    }

    private void recordStageMetric(
        MetricSpec metric,
        Method method,
        Object[] args,
        HttpServletRequest request,
        Object result,
        Throwable throwable
    ) {
        Long stageId = AnalyticsEventContextHolder.currentStageId();
        if (stageId == null) {
            logStageMetricWarning(metric, method, request, null, "No active analytics stage is available", null);
            return;
        }

        String code = normalizeMetricCode(metric.code());
        if (code == null) {
            logStageMetricWarning(metric, method, request, stageId, "Metric code is blank", null);
            return;
        }
        StageMetricType type = stageMetricTypeRepository.findById(code).orElse(null);
        if (type == null) {
            logStageMetricWarning(metric, method, request, stageId, "Unknown metric type: " + code, null);
            return;
        }
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            logStageMetricWarning(metric, method, request, stageId, "Inactive metric type: " + code, null);
            return;
        }

        MetricExpressionResult expressionResult = evaluateMetricExpression(method, args, request, result, throwable, metric.value());
        if (expressionResult.error() != null) {
            logStageMetricWarning(
                metric,
                method,
                request,
                stageId,
                "Metric expression failed: " + metric.value(),
                expressionResult.error()
            );
            return;
        }
        Object value = expressionResult.value();
        if (value == null || (value instanceof CharSequence text && text.toString().isBlank())) {
            logStageMetricWarning(metric, method, request, stageId, "Metric expression returned null/blank value", null);
            return;
        }

        try {
            if (type.getValueKind() == MetricValueKind.NUMERIC) {
                BigDecimal number = toBigDecimal(value);
                if (number == null) {
                    logStageMetricWarning(
                        metric,
                        method,
                        request,
                        stageId,
                        "Metric type is NUMERIC but expression returned non-numeric value: " + value,
                        null
                    );
                    return;
                }
                analyticsTrackingApi.recordMetricNum(stageId, code, number, normalizeMetricUnit(metric.unit()));
                return;
            }
            analyticsTrackingApi.recordMetricText(stageId, code, String.valueOf(value), normalizeMetricUnit(metric.unit()));
        } catch (RuntimeException exception) {
            logStageMetricWarning(metric, method, request, stageId, exception.getMessage(), exception);
        }
    }

    private MetricSpec[] collectMetricSpecs(TrackAnalyticsStageMetric annotation) {
        TrackAnalyticsMetric[] nestedMetrics = annotation.metrics();
        if (nestedMetrics != null && nestedMetrics.length > 0) {
            MetricSpec[] specs = new MetricSpec[nestedMetrics.length];
            for (int i = 0; i < nestedMetrics.length; i++) {
                TrackAnalyticsMetric metric = nestedMetrics[i];
                specs[i] = new MetricSpec(metric.code(), metric.value(), metric.unit(), metric.required());
            }
            return specs;
        }
        return new MetricSpec[] {
            new MetricSpec(annotation.code(), annotation.value(), annotation.unit(), annotation.required())
        };
    }

    private MetricExpressionResult evaluateMetricExpression(
        Method method,
        Object[] args,
        HttpServletRequest request,
        Object result,
        Throwable throwable,
        String expression
    ) {
        if (expression == null || expression.isBlank()) {
            return new MetricExpressionResult(null, null);
        }
        try {
            StandardEvaluationContext context = buildEvaluationContext(method, args, request, result, throwable);
            return new MetricExpressionResult(expressionParser.parseExpression(expression).getValue(context), null);
        } catch (RuntimeException exception) {
            return new MetricExpressionResult(null, exception);
        }
    }

    private StandardEvaluationContext buildEvaluationContext(
        Method method,
        Object[] args,
        HttpServletRequest request,
        Object result,
        Throwable throwable
    ) {
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
        return context;
    }

    private Method resolveAnnotatedMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method signatureMethod = signature.getMethod();
        if (AnnotationUtils.findAnnotation(signatureMethod, TrackAnalyticsStageMetric.class) != null) {
            return signatureMethod;
        }
        Object target = joinPoint.getTarget();
        if (target == null) {
            return signatureMethod;
        }
        Method targetMethod = AopUtils.getMostSpecificMethod(signatureMethod, target.getClass());
        return targetMethod == null ? signatureMethod : targetMethod;
    }

    private HttpServletRequest resolveRequest(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest request) {
                return request;
            }
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof CharSequence text) {
            try {
                return new BigDecimal(text.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void logStageMetricWarning(
        MetricSpec metric,
        Method method,
        HttpServletRequest request,
        Long stageId,
        String reason,
        RuntimeException exception
    ) {
        if (!loggingPolicy.isStrictWarningsEnabled()) {
            return;
        }
        AnalyticsEventContext context = AnalyticsEventContextHolder.get();
        String eventUid = context == null ? "" : String.valueOf(context.eventUid());
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        String path = request == null ? "" : request.getRequestURI();
        if (exception == null) {
            log.warn(
                "Analytics metric skipped: code={}, expression={}, class={}, method={}, path={}, traceId={}, eventUid={}, stageId={}, required={}, reason={}",
                metric.code(),
                metric.value(),
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                path,
                traceId == null ? "" : traceId,
                eventUid,
                stageId,
                metric.required(),
                reason
            );
            return;
        }
        log.warn(
            "Analytics metric skipped: code={}, expression={}, class={}, method={}, path={}, traceId={}, eventUid={}, stageId={}, required={}, reason={}",
            metric.code(),
            metric.value(),
            method.getDeclaringClass().getSimpleName(),
            method.getName(),
            path,
            traceId == null ? "" : traceId,
            eventUid,
            stageId,
            metric.required(),
            reason,
            exception
        );
    }

    private String normalizeMetricCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    private String normalizeMetricUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }
        return unit.trim();
    }

    private record MetricExpressionResult(Object value, RuntimeException error) {
    }

    private record MetricSpec(String code, String value, String unit, boolean required) {
    }
}

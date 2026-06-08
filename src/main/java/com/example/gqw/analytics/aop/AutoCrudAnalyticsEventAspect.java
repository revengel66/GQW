package com.example.gqw.analytics.aop;

import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.service.AnalyticsHttpErrorTrackingService;
import com.example.gqw.analytics.service.AnalyticsInstrumentationPolicy;
import com.example.gqw.analytics.service.AnalyticsTrackingApi;
import com.example.gqw.analytics.service.ErrorClassClassifier;
import com.example.gqw.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@ConditionalOnProperty(value = "app.analytics.auto-crud.enabled", havingValue = "true", matchIfMissing = false)
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class AutoCrudAnalyticsEventAspect {

    private static final Logger log = LoggerFactory.getLogger(AutoCrudAnalyticsEventAspect.class);
    private final AnalyticsTrackingApi analyticsTrackingApi;
    private final AnalyticsInstrumentationPolicy instrumentationPolicy;

    public AutoCrudAnalyticsEventAspect(
        AnalyticsTrackingApi analyticsTrackingApi,
        AnalyticsInstrumentationPolicy instrumentationPolicy
    ) {
        this.analyticsTrackingApi = analyticsTrackingApi;
        this.instrumentationPolicy = instrumentationPolicy;
    }

    @Around("execution(public * com.example.gqw.admin.controller..*(..)) || execution(public * com.example.gqw.shop.controller..*(..))")
    public Object aroundControllerCrud(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!instrumentationPolicy.isEnabled()) {
            return joinPoint.proceed();
        }
        Method method = resolveMethod(joinPoint);
        if (!isRequestMapped(method)) {
            return joinPoint.proceed();
        }
        if (AnnotationUtils.findAnnotation(method, TrackAnalyticsEvent.class) != null) {
            return joinPoint.proceed();
        }
        if (AnalyticsEventContextHolder.get() != null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = resolveRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        String httpMethod = resolveHttpMethod(method, request);
        String path = resolvePath(method, request);
        if (httpMethod == null || path == null || path.isBlank()) {
            return joinPoint.proceed();
        }
        if (shouldSkipAutoCrudEvent(httpMethod, path, request)) {
            return joinPoint.proceed();
        }

        String eventCode = buildEventCode(method, httpMethod, path);
        if (eventCode == null) {
            return joinPoint.proceed();
        }

        UUID eventUid;
        Long controllerStageId;
        String previousAppModule = MDC.get(AnalyticsEventAspect.APP_MODULE_MDC_KEY);
        String previousAnalyticsModule = MDC.get(AnalyticsEventAspect.ANALYTICS_MODULE_MDC_KEY);
        String eventModuleCode = EventType.DEFAULT_MODULE_CODE;
        try {
            eventUid = analyticsTrackingApi.startEvent(
                eventCode,
                null,
                request.getSession(false) != null ? request.getSession(false).getId() : null,
                request.getRequestURI(),
                request.getMethod(),
                resolveTraceId(request)
            );
            String resolvedEventModule = analyticsTrackingApi.resolveEventModuleCode(eventUid);
            if (resolvedEventModule != null && !resolvedEventModule.isBlank()) {
                eventModuleCode = resolvedEventModule;
            }
            AnalyticsEventContext context = new AnalyticsEventContext(eventUid);
            AnalyticsEventContextHolder.set(context);
            MDC.put(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY, eventUid.toString());
            MDC.put(AnalyticsEventAspect.ANALYTICS_MODULE_MDC_KEY, eventModuleCode);
            MDC.put(AnalyticsEventAspect.APP_MODULE_MDC_KEY, eventModuleCode);
            controllerStageId = analyticsTrackingApi.startStage(eventUid, "CONTROLLER", context.nextStageOrder());
            context.pushStageId(controllerStageId);
        } catch (RuntimeException ignored) {
            log.warn("AUTO CRUD analytics skipped for {} {}: {}", request.getMethod(), request.getRequestURI(), ignored.getMessage());
            AnalyticsEventContextHolder.clear();
            MDC.remove(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY);
            restoreMdc(AnalyticsEventAspect.APP_MODULE_MDC_KEY, previousAppModule);
            restoreMdc(AnalyticsEventAspect.ANALYTICS_MODULE_MDC_KEY, previousAnalyticsModule);
            return joinPoint.proceed();
        }

        try {
            Object result = joinPoint.proceed();
            int responseStatus = resolveSuccessStatus(result);
            if (responseStatus >= 400) {
                String errorMessage = "HTTP " + responseStatus;
                String errorClass = ErrorClassClassifier.classify(responseStatus, errorMessage, null);
                safeRecordMetricText(controllerStageId, "ERROR_CODE", eventCode + "_HTTP_" + responseStatus, null);
                safeRecordMetricText(controllerStageId, "ERROR_CLASS", errorClass, null);
                safeFinishStageError(controllerStageId, errorMessage);
                safeFinishEventError(eventUid, responseStatus, errorMessage);
            } else {
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
            safeRecordMetricText(controllerStageId, "ERROR_CODE", eventCode + "_FAIL", null);
            safeRecordMetricText(controllerStageId, "ERROR_CLASS", errorClass, null);
            safeFinishStageError(controllerStageId, errorMessage);
            safeFinishEventError(eventUid, errorStatus, errorMessage);
            throw throwable;
        } finally {
            AnalyticsEventContext context = AnalyticsEventContextHolder.get();
            if (context != null) {
                context.popStageId(controllerStageId);
            }
            AnalyticsEventContextHolder.clear();
            MDC.remove(AnalyticsEventAspect.ANALYTICS_EVENT_UID_MDC_KEY);
            restoreMdc(AnalyticsEventAspect.APP_MODULE_MDC_KEY, previousAppModule);
            restoreMdc(AnalyticsEventAspect.ANALYTICS_MODULE_MDC_KEY, previousAnalyticsModule);
        }
    }

    private Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null
            ? AopUtils.getTargetClass(joinPoint.getTarget())
            : method.getDeclaringClass();
        return AopUtils.getMostSpecificMethod(method, targetClass);
    }

    private HttpServletRequest resolveRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String resolveHttpMethod(Method method, HttpServletRequest request) {
        if (AnnotationUtils.findAnnotation(method, GetMapping.class) != null) {
            return "GET";
        }
        if (AnnotationUtils.findAnnotation(method, PostMapping.class) != null) {
            return "POST";
        }
        if (AnnotationUtils.findAnnotation(method, PutMapping.class) != null) {
            return "PUT";
        }
        if (AnnotationUtils.findAnnotation(method, PatchMapping.class) != null) {
            return "PATCH";
        }
        if (AnnotationUtils.findAnnotation(method, DeleteMapping.class) != null) {
            return "DELETE";
        }
        RequestMapping mapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
        if (mapping != null && mapping.method().length > 0) {
            return mapping.method()[0].name();
        }
        return request != null ? request.getMethod() : null;
    }

    private String resolvePath(Method method, HttpServletRequest request) {
        String[] paths = extractPaths(method);
        if (paths.length > 0 && paths[0] != null && !paths[0].isBlank()) {
            return paths[0];
        }
        return request != null ? request.getRequestURI() : "";
    }

    private String[] extractPaths(Method method) {
        GetMapping get = AnnotationUtils.findAnnotation(method, GetMapping.class);
        if (get != null && get.value().length > 0) {
            return get.value();
        }
        PostMapping post = AnnotationUtils.findAnnotation(method, PostMapping.class);
        if (post != null && post.value().length > 0) {
            return post.value();
        }
        PutMapping put = AnnotationUtils.findAnnotation(method, PutMapping.class);
        if (put != null && put.value().length > 0) {
            return put.value();
        }
        PatchMapping patch = AnnotationUtils.findAnnotation(method, PatchMapping.class);
        if (patch != null && patch.value().length > 0) {
            return patch.value();
        }
        DeleteMapping delete = AnnotationUtils.findAnnotation(method, DeleteMapping.class);
        if (delete != null && delete.value().length > 0) {
            return delete.value();
        }
        RequestMapping mapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
        if (mapping != null && mapping.value().length > 0) {
            return mapping.value();
        }
        return new String[0];
    }

    private String buildEventCode(Method method, String httpMethod, String path) {
        String specialEventCode = resolveSpecialEventCode(path, httpMethod, method.getName());
        if (specialEventCode != null && !specialEventCode.isBlank()) {
            return specialEventCode;
        }
        String entityCode = resolveEntity(path, method);
        String actionCode = resolveAction(httpMethod, path, method.getName());
        return (entityCode + "_" + actionCode).toUpperCase(Locale.ROOT);
    }

    private String resolveSpecialEventCode(String path, String httpMethod, String methodName) {
        if (path == null || httpMethod == null) {
            return null;
        }
        String lowerPath = path.trim().toLowerCase(Locale.ROOT);
        String lowerMethod = methodName == null ? "" : methodName.toLowerCase(Locale.ROOT);

        if ("/account".equals(lowerPath) && "GET".equalsIgnoreCase(httpMethod)) {
            return "ACCOUNT_VIEW";
        }
        if (("/account/profile".equals(lowerPath) || lowerMethod.contains("profile"))
            && "POST".equalsIgnoreCase(httpMethod)) {
            return "ACCOUNT_PROFILE_UPDATE";
        }
        if (("/account/address".equals(lowerPath) || lowerMethod.contains("address"))
            && "POST".equalsIgnoreCase(httpMethod)) {
            return "ACCOUNT_ADDRESS_UPDATE";
        }
        if (("/account/delete".equals(lowerPath) || lowerMethod.contains("deleteaccount"))
            && "POST".equalsIgnoreCase(httpMethod)) {
            return "ACCOUNT_DELETE";
        }
        if ((lowerPath.contains("/account/orders/") && lowerPath.endsWith("/cancel"))
            || lowerMethod.contains("cancelorder")) {
            return "ACCOUNT_ORDER_CANCEL";
        }
        if ((lowerPath.contains("/account/orders/") && lowerPath.endsWith("/update"))
            || lowerMethod.contains("updateorder")) {
            return "ACCOUNT_ORDER_UPDATE";
        }
        if (lowerPath.startsWith("/account/support/") || lowerMethod.contains("supportticket")) {
            return "ACCOUNT_SUPPORT_CREATE";
        }
        return null;
    }

    private String resolveEntity(String path, Method method) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.isEmpty()) {
            return method.getDeclaringClass().getSimpleName().replace("Controller", "").toUpperCase(Locale.ROOT);
        }
        String[] parts = normalized.replaceAll("^/+", "").split("/");
        for (String part : parts) {
            String lower = part.toLowerCase(Locale.ROOT);
            if (lower.isBlank() || lower.equals("api") || lower.equals("admin")) {
                continue;
            }
            if (lower.startsWith("{") && lower.endsWith("}")) {
                continue;
            }
            return normalizeToken(lower);
        }
        return method.getDeclaringClass().getSimpleName().replace("Controller", "").toUpperCase(Locale.ROOT);
    }

    private String resolveAction(String httpMethod, String path, String methodName) {
        String lowerPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        String lowerMethod = methodName == null ? "" : methodName.toLowerCase(Locale.ROOT);

        if ("GET".equals(httpMethod)) {
            return "VIEW";
        }
        if ("DELETE".equals(httpMethod)) {
            return "DELETE";
        }
        if ("PUT".equals(httpMethod) || "PATCH".equals(httpMethod)) {
            return "UPDATE";
        }
        if (lowerPath.contains("/duplicate") || lowerMethod.contains("duplicate")) {
            return "DUPLICATE";
        }
        if (lowerPath.contains("/delete") || lowerMethod.contains("delete") || lowerMethod.contains("remove")) {
            return "DELETE";
        }
        if (lowerPath.contains("/update") || lowerMethod.contains("update")
            || lowerPath.contains("/status") || lowerMethod.contains("status")
            || lowerPath.contains("/moderate") || lowerMethod.contains("moderate")
            || lowerPath.contains("/reply") || lowerMethod.contains("reply")
            || lowerPath.contains("/toggle") || lowerMethod.contains("toggle")
            || lowerPath.contains("/increment") || lowerPath.contains("/decrement")) {
            return "UPDATE";
        }
        if (lowerPath.contains("/create") || lowerPath.contains("/add")
            || lowerMethod.contains("create") || lowerMethod.contains("add")
            || lowerMethod.contains("save") || lowerPath.endsWith("/save")) {
            return "CREATE";
        }
        return "ACTION";
    }

    private boolean isRequestMapped(Method method) {
        return AnnotationUtils.findAnnotation(method, GetMapping.class) != null
            || AnnotationUtils.findAnnotation(method, PostMapping.class) != null
            || AnnotationUtils.findAnnotation(method, PutMapping.class) != null
            || AnnotationUtils.findAnnotation(method, PatchMapping.class) != null
            || AnnotationUtils.findAnnotation(method, DeleteMapping.class) != null
            || AnnotationUtils.findAnnotation(method, RequestMapping.class) != null;
    }

    private String normalizeToken(String token) {
        String normalized = token.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            return "ENTITY";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private boolean shouldSkipAutoCrudEvent(String httpMethod, String path, HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(httpMethod)) {
            return false;
        }
        String resolvedPath = path == null ? "" : path.trim().toLowerCase(Locale.ROOT);
        String requestPath = request != null && request.getRequestURI() != null
            ? request.getRequestURI().trim().toLowerCase(Locale.ROOT)
            : "";
        return "/api/cart/count".equals(resolvedPath) || "/api/cart/count".equals(requestPath);
    }

    private int resolveSuccessStatus(Object result) {
        if (result instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getStatusCode().value();
        }
        return 200;
    }

    private int resolveErrorStatus(Throwable throwable) {
        if (throwable instanceof org.springframework.web.server.ResponseStatusException ex) {
            return ex.getStatusCode().value();
        }
        return 500;
    }

    private String resolveTraceId(HttpServletRequest request) {
        if (request != null) {
            Object attribute = request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE);
            if (attribute != null) {
                return String.valueOf(attribute);
            }
        }
        return null;
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

    private void safeRecordMetricText(Long stageId, String metricTypeCode, String value, String unit) {
        try {
            analyticsTrackingApi.recordMetricText(stageId, metricTypeCode, value, unit);
        } catch (RuntimeException ignored) {
            // Analytics must never break business flow.
        }
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unexpected error";
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

    private void restoreMdc(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }
}

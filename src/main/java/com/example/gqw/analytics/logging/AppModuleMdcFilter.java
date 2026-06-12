package com.example.gqw.analytics.logging;

import com.example.gqw.analytics.aop.AnalyticsEventAspect;
import com.example.gqw.analytics.support.AnalyticsTraceContext;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class AppModuleMdcFilter extends OncePerRequestFilter {

    @Value("${app.analytics.logging.default-module-code:DEFAULT}")
    private String defaultModuleCode;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String previousModule = MDC.get(AnalyticsEventAspect.APP_MODULE_MDC_KEY);
        String previousTraceId = MDC.get(AnalyticsTraceContext.TRACE_ID_MDC_KEY);
        String resolvedModule = resolveModuleCode(request);
        String traceId = resolveTraceId(request);
        request.setAttribute(AnalyticsTraceContext.TRACE_ID_REQUEST_ATTRIBUTE, traceId);
        response.setHeader(AnalyticsTraceContext.TRACE_ID_HEADER, traceId);
        MDC.put(AnalyticsEventAspect.APP_MODULE_MDC_KEY, resolvedModule);
        MDC.put(AnalyticsTraceContext.TRACE_ID_MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            restoreMdc(AnalyticsEventAspect.APP_MODULE_MDC_KEY, previousModule);
            restoreMdc(AnalyticsTraceContext.TRACE_ID_MDC_KEY, previousTraceId);
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private String resolveModuleCode(HttpServletRequest request) {
        String fromRequestAttribute = resolveModuleFromRequestAttribute(request);
        if (fromRequestAttribute != null) {
            return fromRequestAttribute;
        }
        if (request == null) {
            return normalizeModuleCode(defaultModuleCode);
        }
        String path = resolveOriginalPath(request);
        if (path == null || path.isBlank()) {
            return normalizeModuleCode(defaultModuleCode);
        }
        String fromPath = resolveModuleFromPath(path);
        if (fromPath != null) {
            return normalizeModuleCode(fromPath);
        }
        return normalizeModuleCode(defaultModuleCode);
    }

    private String resolveOriginalPath(HttpServletRequest request) {
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (originalPath instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getRequestURI();
    }

    private String resolveModuleFromRequestAttribute(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object module = request.getAttribute(AnalyticsEventAspect.ANALYTICS_MODULE_MDC_KEY);
        if (module instanceof String text && !text.isBlank()) {
            return normalizeModuleCode(text);
        }
        Object appModule = request.getAttribute(AnalyticsEventAspect.APP_MODULE_MDC_KEY);
        if (appModule instanceof String text && !text.isBlank()) {
            return normalizeModuleCode(text);
        }
        return null;
    }

    private String normalizeModuleCode(String value) {
        if (value == null || value.isBlank()) {
            return "DEFAULT";
        }
        return value.trim().toUpperCase();
    }

    static String resolveModuleFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.startsWith("/admin")
            || path.startsWith("/analytics")
            || path.startsWith("/analytics-admin")) {
            return "ADMIN";
        }
        return null;
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object existing = request.getAttribute(AnalyticsTraceContext.TRACE_ID_REQUEST_ATTRIBUTE);
        if (existing instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        String header = request.getHeader(AnalyticsTraceContext.TRACE_ID_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return UUID.randomUUID().toString();
    }

    private static void restoreMdc(String key, String previousValue) {
        if (previousValue == null || previousValue.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }
}

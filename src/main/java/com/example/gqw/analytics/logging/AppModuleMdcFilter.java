package com.example.gqw.analytics.logging;

import com.example.gqw.analytics.aop.AnalyticsEventAspect;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
        String resolvedModule = resolveModuleCode(request);
        MDC.put(AnalyticsEventAspect.APP_MODULE_MDC_KEY, resolvedModule);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousModule == null || previousModule.isBlank()) {
                MDC.remove(AnalyticsEventAspect.APP_MODULE_MDC_KEY);
            } else {
                MDC.put(AnalyticsEventAspect.APP_MODULE_MDC_KEY, previousModule);
            }
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

    private String resolveModuleFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.startsWith("/admin")
            || path.startsWith("/analytics")
            || path.startsWith("/analytics-admin")) {
            return "ADMIN";
        }
        if (path.startsWith("/shop")
            || path.startsWith("/category")
            || path.startsWith("/product")
            || path.startsWith("/cart")
            || path.startsWith("/wishlist")
            || path.startsWith("/checkout")
            || path.startsWith("/support")
            || path.startsWith("/reviews")
            || path.startsWith("/login")
            || path.startsWith("/register")
            || path.startsWith("/account")) {
            return "SHOP";
        }
        return null;
    }
}

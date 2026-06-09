package com.example.gqw.analytics.config;

import com.example.gqw.analytics.service.AnalyticsAdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AnalyticsAdminAuthInterceptor implements HandlerInterceptor {

    private final AnalyticsAdminAuthService authService;

    public AnalyticsAdminAuthInterceptor(AnalyticsAdminAuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        if (path.startsWith("/analytics-admin/login")
            || path.startsWith("/analytics-admin/setup")
            || path.startsWith("/analytics-admin/logout")) {
            return true;
        }
        if (!authService.isSetupComplete()) {
            response.sendRedirect("/analytics-admin/setup");
            return false;
        }
        Object authFlag = request.getSession(true).getAttribute(AnalyticsAdminAuthService.SESSION_KEY_AUTH);
        if (!(authFlag instanceof Boolean authenticated) || !authenticated) {
            response.sendRedirect("/analytics-admin/login");
            return false;
        }
        return true;
    }
}


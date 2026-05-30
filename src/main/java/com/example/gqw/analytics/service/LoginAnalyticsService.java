package com.example.gqw.analytics.service;

import com.example.gqw.analytics.aop.TrackAnalyticsAttribute;
import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class LoginAnalyticsService {

    @TrackAnalyticsEvent(
        code = "LOGIN",
        attributes = {
            @TrackAnalyticsAttribute(code = "AUTH_RESULT", value = "'SUCCESS'")
        }
    )
    public void trackSuccess(HttpServletRequest request) {
        // The event is captured by AOP; explicit logic is intentionally empty.
    }

    @TrackAnalyticsEvent(
        code = "LOGIN",
        attributes = {
            @TrackAnalyticsAttribute(code = "AUTH_RESULT", value = "'FAIL'"),
            @TrackAnalyticsAttribute(code = "FAILURE_REASON", value = "#exception != null ? #exception.message : 'Authentication failed'")
        }
    )
    public void trackFailure(HttpServletRequest request, AuthenticationException exception) {
        // The event is captured by AOP; explicit logic is intentionally empty.
    }
}

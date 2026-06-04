package com.example.gqw.analytics.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnalyticsSystemEventClassifierTest {

    private final AnalyticsSystemEventClassifier classifier = new AnalyticsSystemEventClassifier();

    @Test
    void classifiesKnownTechnicalPathsAsSystemEvents() {
        assertTrue(classifier.isSystemEvent("ANY_EVENT", "Any event", "/favicon.ico", 200));
        assertTrue(classifier.isSystemEvent("ANY_EVENT", "Any event", "/static/app.css", 200));
        assertTrue(classifier.isSystemEvent("ANY_EVENT", "Any event", "/actuator/health", 200));
        assertTrue(classifier.isSystemEvent("ANY_EVENT", "Any event", "/error", 500));
    }

    @Test
    void classifiesTechnicalHttpRequestErrorsAsSystemEvents() {
        assertTrue(classifier.isSystemEvent("FRONTEND_JS_ERROR", "Frontend JS Error", "/product/laptop", 500));
        assertTrue(classifier.isSystemEvent("HTTP_REQUEST_ERROR", "Http Request Error", null, null));
        assertTrue(classifier.isSystemEvent("HTTP_REQUEST_ERROR", "Http Request Error", "/missing/app.js", 404));
        assertTrue(classifier.isSystemEvent("HTTP_REQUEST_ERROR", "Http Request Error", "/css/missing.css", 404));
    }

    @Test
    void keepsBusinessHttpErrorsOutOfSystemScope() {
        assertFalse(classifier.isSystemEvent("ORDER_CREATE_FAILED", "Order create failed", "/orders", 404));
        assertFalse(classifier.isSystemEvent("HTTP_REQUEST_ERROR", "Http Request Error", "/orders", 500));
    }
}

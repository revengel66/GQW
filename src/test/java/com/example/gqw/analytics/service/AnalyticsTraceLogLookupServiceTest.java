package com.example.gqw.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class AnalyticsTraceLogLookupServiceTest {

    @Test
    void returnsFallbackResultWhenTraceLogLookupFails() {
        AnalyticsLogViewService logViewService = mock(AnalyticsLogViewService.class);
        when(logViewService.loadTraceLogs(anyString(), anyString(), anyString(), any(), any()))
            .thenThrow(new DataAccessResourceFailureException("log index is unavailable"));
        AnalyticsTraceLogLookupService service = new AnalyticsTraceLogLookupService(logViewService);
        AnalyticsEvent event = event("HTTP_REQUEST_ERROR", "DEFAULT", "/missing", "trace-http-error");

        AnalyticsLogViewService.TraceLogLookupResult result = service.loadTraceLogsSafely(event);

        assertEquals("NOT_FOUND", result.status().status());
        assertTrue(result.rows().isEmpty());
        assertEquals(
            "Trace logs are temporarily unavailable, event details are shown without log rows.",
            result.status().message()
        );
    }

    @Test
    void passesEventContextToTraceLogLookup() {
        AnalyticsLogViewService logViewService = mock(AnalyticsLogViewService.class);
        when(logViewService.loadTraceLogs(anyString(), isNull(), anyString(), any(), any()))
            .thenReturn(AnalyticsLogViewService.TraceLogLookupResult.notFound("not found"));
        AnalyticsTraceLogLookupService service = new AnalyticsTraceLogLookupService(logViewService);
        AnalyticsEvent event = event("PRODUCT_VIEW", "SHOP", "/product/tv-7", "trace-product");
        event.setEventUid(null);

        service.loadTraceLogsSafely(event);

        verify(logViewService).loadTraceLogs(
            "trace-product",
            null,
            "SHOP",
            event.getStartedAt(),
            event.getEndedAt()
        );
    }

    private static AnalyticsEvent event(String eventTypeCode, String moduleCode, String requestPath, String traceId) {
        AnalyticsEvent event = new AnalyticsEvent();
        event.setEventUid(UUID.randomUUID());
        event.setEventTypeCode(eventTypeCode);
        event.setModuleCode(moduleCode);
        event.setRequestPath(requestPath);
        event.setTraceId(traceId);
        event.setStartedAt(Instant.parse("2026-06-09T00:00:00Z"));
        event.setEndedAt(Instant.parse("2026-06-09T00:00:01Z"));
        return event;
    }
}

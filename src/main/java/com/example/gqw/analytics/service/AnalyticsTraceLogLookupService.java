package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsTraceLogLookupService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsTraceLogLookupService.class);

    private final AnalyticsLogViewService analyticsLogViewService;

    public AnalyticsTraceLogLookupService(AnalyticsLogViewService analyticsLogViewService) {
        this.analyticsLogViewService = analyticsLogViewService;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public AnalyticsLogViewService.TraceLogLookupResult loadTraceLogsSafely(AnalyticsEvent event) {
        try {
            return analyticsLogViewService.loadTraceLogs(
                event.getTraceId(),
                event.getEventUid() == null ? null : event.getEventUid().toString(),
                event.getModuleCode(),
                event.getStartedAt(),
                event.getEndedAt()
            );
        } catch (RuntimeException ex) {
            log.warn(
                "Trace log lookup failed for eventUid={}, traceId={}, eventType={}, module={}, path={}: {}",
                event.getEventUid(),
                event.getTraceId(),
                event.getEventTypeCode(),
                event.getModuleCode(),
                event.getRequestPath(),
                ex.getMessage(),
                ex
            );
            return AnalyticsLogViewService.TraceLogLookupResult.notFound(
                "Trace logs are temporarily unavailable, event details are shown without log rows."
            );
        }
    }
}

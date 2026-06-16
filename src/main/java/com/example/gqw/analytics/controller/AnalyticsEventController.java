package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventDetailsResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventListResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/analytics/api/events", "/analytics-admin/api/events"})
public class AnalyticsEventController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventController.class);

    private final AnalyticsInsightsService analyticsInsightsService;

    public AnalyticsEventController(AnalyticsInsightsService analyticsInsightsService) {
        this.analyticsInsightsService = analyticsInsightsService;
    }

    @GetMapping
    public EventListResponse events(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) String stageTypeCode,
        @RequestParam(required = false) Boolean isError,
        @RequestParam(required = false) String errorKey,
        @RequestParam(required = false) String errorClass,
        @RequestParam(required = false) Integer minDurationMs,
        @RequestParam(required = false) String requestPath,
        @RequestParam(required = false) String attributeCode,
        @RequestParam(required = false) String attributeValue,
        @RequestParam(required = false) String metricTypeCode,
        @RequestParam(required = false) BigDecimal metricMinValue,
        @RequestParam(required = false) BigDecimal metricMaxValue,
        @RequestParam(required = false) String sortBy,
        @RequestParam(required = false) String sortDir,
        @RequestParam(required = false, defaultValue = "false") Boolean systemEventsOnly,
        @RequestParam(required = false, defaultValue = "0") Integer page,
        @RequestParam(required = false, defaultValue = "50") Integer size
    ) {
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        EventListResponse response = analyticsInsightsService.events(
            range.from(),
            range.to(),
            moduleCode,
            eventTypeCode,
            stageTypeCode,
            isError,
            errorKey,
            errorClass,
            minDurationMs,
            requestPath,
            attributeCode,
            attributeValue,
            metricTypeCode,
            metricMinValue,
            metricMaxValue,
            sortBy,
            sortDir,
            Boolean.TRUE.equals(systemEventsOnly),
            page,
            size
        );
        long totalMs = elapsedMs(started);
        if (totalMs >= 500L) {
            String message =
                "Analytics events controller slow endpoint=/api/events totalMs={} from={} to={} module={} eventTypes={} stage={} systemOnly={} page={} size={} returned={} hasMore={} requestPath={} attr={} metric={}";
            Object[] args = {
                totalMs,
                range.from(),
                range.to(),
                moduleCode,
                eventTypeCode == null ? 0 : eventTypeCode.size(),
                stageTypeCode,
                Boolean.TRUE.equals(systemEventsOnly),
                page,
                size,
                response.items() == null ? 0 : response.items().size(),
                response.hasMore(),
                requestPath,
                attributeCode,
                metricTypeCode
            };
            if (totalMs >= 3000L) {
                log.warn(message, args);
            } else {
                log.info(message, args);
            }
        }
        return response;
    }

    @GetMapping("/{eventUid}")
    public EventDetailsResponse eventDetails(@PathVariable UUID eventUid) {
        try {
            return analyticsInsightsService.eventDetails(eventUid);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/by-id/{eventId}")
    public EventDetailsResponse eventDetailsById(@PathVariable Long eventId) {
        try {
            return analyticsInsightsService.eventDetailsById(eventId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }
}

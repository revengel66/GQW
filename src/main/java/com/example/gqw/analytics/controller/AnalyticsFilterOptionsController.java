package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.FilterOptionsResponse;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/analytics/api/filter-options", "/analytics-admin/api/filter-options"})
public class AnalyticsFilterOptionsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsFilterOptionsController.class);

    private final AnalyticsInsightsService analyticsInsightsService;

    public AnalyticsFilterOptionsController(AnalyticsInsightsService analyticsInsightsService) {
        this.analyticsInsightsService = analyticsInsightsService;
    }

    @GetMapping
    public FilterOptionsResponse filterOptions(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) String eventTypeCode,
        @RequestParam(required = false) String requestPath,
        @RequestParam(required = false) String attributeCode,
        @RequestParam(required = false, defaultValue = "false") Boolean systemEventsOnly
    ) {
        AnalyticsTimeRangeResolver.TimeRange range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        long started = System.nanoTime();
        FilterOptionsResponse response = analyticsInsightsService.filterOptions(
            range.from(),
            range.to(),
            moduleCode,
            eventTypeCode,
            requestPath,
            attributeCode,
            Boolean.TRUE.equals(systemEventsOnly)
        );
        log.debug(
            "[FILTER_OPTIONS_PERF] controller endpoint=/api/filter-options totalMs={} from={} to={} module={} eventType={} requestPath={} attribute={} systemEventsOnly={} modules={} eventTypes={} attributeTypes={} attributeValues={}",
            elapsedMs(started),
            range.from(),
            range.to(),
            moduleCode,
            eventTypeCode,
            requestPath,
            attributeCode,
            Boolean.TRUE.equals(systemEventsOnly),
            response.modules() == null ? 0 : response.modules().size(),
            response.eventTypes() == null ? 0 : response.eventTypes().size(),
            response.attributeTypes() == null ? 0 : response.attributeTypes().size(),
            response.attributeValues() == null ? 0 : response.attributeValues().size()
        );
        return response;
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }
}


package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.CompareResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/analytics/api/compare", "/analytics-admin/api/compare"})
public class AnalyticsCompareController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsCompareController.class);

    private final AnalyticsInsightsService analyticsInsightsService;

    public AnalyticsCompareController(AnalyticsInsightsService analyticsInsightsService) {
        this.analyticsInsightsService = analyticsInsightsService;
    }

    @GetMapping
    public CompareResponse compare(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant baselineFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant baselineTo,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant targetFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant targetTo,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) String requestPath
    ) {
        AnalyticsTimeRangeResolver.TimeRange targetRange = AnalyticsTimeRangeResolver.resolveRange(
            targetFrom,
            targetTo,
            Duration.ofHours(24)
        );
        Duration targetDuration = Duration.between(targetRange.from(), targetRange.to());
        if (targetDuration.isNegative() || targetDuration.isZero()) {
            targetDuration = Duration.ofHours(24);
        }

        Instant resolvedBaselineTo = baselineTo;
        Instant resolvedBaselineFrom = baselineFrom;
        if (resolvedBaselineTo == null || resolvedBaselineFrom == null) {
            resolvedBaselineTo = targetRange.from();
            resolvedBaselineFrom = targetRange.from().minus(targetDuration);
        }
        AnalyticsTimeRangeResolver.TimeRange baselineRange = AnalyticsTimeRangeResolver.resolveRange(
            resolvedBaselineFrom,
            resolvedBaselineTo,
            targetDuration
        );

        long started = System.nanoTime();
        CompareResponse response = analyticsInsightsService.compare(
            baselineRange.from(),
            baselineRange.to(),
            targetRange.from(),
            targetRange.to(),
            moduleCode,
            eventTypeCode,
            requestPath
        );
        long totalMs = elapsedMs(started);
        if (totalMs >= 500L) {
            String message = "[COMPARE_PERF] controller endpoint=/api/compare totalMs={} baselineFrom={} baselineTo={} targetFrom={} targetTo={} module={} eventType={} requestPath={} rows={}";
            Object[] args = {
                totalMs,
                baselineRange.from(),
                baselineRange.to(),
                targetRange.from(),
                targetRange.to(),
                moduleCode,
                eventTypeCode,
                requestPath,
                response.events() == null ? 0 : response.events().size()
            };
            if (totalMs >= 3000L) {
                log.warn(message, args);
            } else {
                log.info(message, args);
            }
        }
        return response;
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }
}

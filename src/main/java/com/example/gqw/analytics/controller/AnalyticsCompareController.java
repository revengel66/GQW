package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.CompareResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/analytics/api/compare", "/analytics-admin/api/compare"})
public class AnalyticsCompareController {

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
        @RequestParam(required = false) String eventTypeCode,
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

        return analyticsInsightsService.compare(
            baselineRange.from(),
            baselineRange.to(),
            targetRange.from(),
            targetRange.to(),
            moduleCode,
            eventTypeCode,
            requestPath
        );
    }
}

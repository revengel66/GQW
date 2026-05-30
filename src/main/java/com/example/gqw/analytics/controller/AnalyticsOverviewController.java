package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.OverviewResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/analytics/api/overview", "/analytics-admin/api/overview"})
public class AnalyticsOverviewController {

    private final AnalyticsInsightsService analyticsInsightsService;

    public AnalyticsOverviewController(AnalyticsInsightsService analyticsInsightsService) {
        this.analyticsInsightsService = analyticsInsightsService;
    }

    @GetMapping
    public OverviewResponse overview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) String eventTypeCode,
        @RequestParam(required = false) String requestPath,
        @RequestParam(required = false) String filterMetricTypeCode,
        @RequestParam(required = false) String filterMetricValue,
        @RequestParam(required = false) BigDecimal filterMetricMinValue,
        @RequestParam(required = false) BigDecimal filterMetricMaxValue,
        @RequestParam(required = false) String filterAttributeCode,
        @RequestParam(required = false) String filterAttributeValue,
        @RequestParam(required = false) BigDecimal filterAttributeMinValue,
        @RequestParam(required = false) BigDecimal filterAttributeMaxValue,
        @RequestParam(required = false) Integer bucketMinutes
    ) {
        AnalyticsTimeRangeResolver.TimeRange range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        return analyticsInsightsService.overview(
            range.from(),
            range.to(),
            moduleCode,
            eventTypeCode,
            requestPath,
            filterMetricTypeCode,
            filterMetricValue,
            filterMetricMinValue,
            filterMetricMaxValue,
            filterAttributeCode,
            filterAttributeValue,
            filterAttributeMinValue,
            filterAttributeMaxValue,
            bucketMinutes
        );
    }
}

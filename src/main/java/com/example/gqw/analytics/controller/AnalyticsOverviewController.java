package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.OverviewCompareResponse;
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

    @GetMapping("/compare")
    public OverviewCompareResponse overviewCompare(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant beforeFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant beforeTo,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant afterFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant afterTo,
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
        AnalyticsTimeRangeResolver.TimeRange afterRange = AnalyticsTimeRangeResolver.resolveRange(afterFrom, afterTo, Duration.ofHours(24));
        AnalyticsTimeRangeResolver.TimeRange beforeRange = resolveBeforeRange(beforeFrom, beforeTo, afterRange);
        OverviewResponse before = analyticsInsightsService.overview(
            beforeRange.from(),
            beforeRange.to(),
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
        OverviewResponse after = analyticsInsightsService.overview(
            afterRange.from(),
            afterRange.to(),
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
        return new OverviewCompareResponse(before, after);
    }

    private static AnalyticsTimeRangeResolver.TimeRange resolveBeforeRange(
        Instant beforeFrom,
        Instant beforeTo,
        AnalyticsTimeRangeResolver.TimeRange afterRange
    ) {
        if (beforeFrom != null && beforeTo != null && beforeFrom.isBefore(beforeTo)) {
            return new AnalyticsTimeRangeResolver.TimeRange(beforeFrom, beforeTo);
        }
        Instant safeAfterFrom = afterRange.from();
        Instant safeAfterTo = afterRange.to();
        Duration duration = Duration.between(safeAfterFrom, safeAfterTo);
        if (duration.isZero() || duration.isNegative()) {
            duration = Duration.ofHours(24);
        }
        Instant resolvedBeforeTo = safeAfterFrom;
        Instant resolvedBeforeFrom = resolvedBeforeTo.minus(duration);
        return new AnalyticsTimeRangeResolver.TimeRange(resolvedBeforeFrom, resolvedBeforeTo);
    }
}

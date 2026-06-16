package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.OverviewCompareResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.OverviewResponse;
import java.math.BigDecimal;
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
@RequestMapping({"/analytics/api/overview", "/analytics-admin/api/overview"})
public class AnalyticsOverviewController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsOverviewController.class);

    private final AnalyticsInsightsService analyticsInsightsService;

    public AnalyticsOverviewController(AnalyticsInsightsService analyticsInsightsService) {
        this.analyticsInsightsService = analyticsInsightsService;
    }

    @GetMapping
    public OverviewResponse overview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) List<String> eventTypeCode,
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
        long started = System.nanoTime();
        OverviewResponse response = analyticsInsightsService.overview(
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
        long totalMs = elapsedMs(started);
        if (totalMs >= 500L) {
            String message =
                "Analytics overview slow endpoint=/api/overview totalMs={} from={} to={} module={} eventTypes={} requestPath={} bucket={} points={} eventRows={} partial={}";
            Object[] args = {
                totalMs,
                range.from(),
                range.to(),
                moduleCode,
                eventTypeCode,
                requestPath,
                bucketMinutes,
                response.series() == null ? 0 : response.series().size(),
                response.eventBreakdown() == null ? 0 : response.eventBreakdown().size(),
                response.partial()
            };
            if (totalMs >= 3000L) {
                log.warn(message, args);
            } else {
                log.info(message, args);
            }
        }
        return response;
    }

    @GetMapping("/compare")
    public OverviewCompareResponse overviewCompare(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant beforeFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant beforeTo,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant afterFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant afterTo,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) List<String> eventTypeCode,
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
        long started = System.nanoTime();
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
        OverviewCompareResponse response = new OverviewCompareResponse(before, after);
        long totalMs = elapsedMs(started);
        if (totalMs >= 500L) {
            String message =
                "Analytics overview slow endpoint=/api/overview/compare totalMs={} beforeFrom={} beforeTo={} afterFrom={} afterTo={} module={} eventTypes={} requestPath={} bucket={} beforePoints={} afterPoints={}";
            Object[] args = {
                totalMs,
                beforeRange.from(),
                beforeRange.to(),
                afterRange.from(),
                afterRange.to(),
                moduleCode,
                eventTypeCode,
                requestPath,
                bucketMinutes,
                before.series() == null ? 0 : before.series().size(),
                after.series() == null ? 0 : after.series().size()
            };
            if (totalMs >= 3000L) {
                log.warn(message, args);
            } else {
                log.info(message, args);
            }
        }
        return response;
    }

    private static long elapsedMs(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
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

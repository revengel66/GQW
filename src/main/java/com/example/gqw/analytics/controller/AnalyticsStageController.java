package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.StageBreakdownCompareResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.StageBreakdownResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.StageMetricCompareResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.StageMetricResponse;
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
@RequestMapping({"/analytics/api", "/analytics-admin/api"})
public class AnalyticsStageController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsStageController.class);

    private final AnalyticsInsightsService analyticsInsightsService;

    public AnalyticsStageController(AnalyticsInsightsService analyticsInsightsService) {
        this.analyticsInsightsService = analyticsInsightsService;
    }

    @GetMapping("/stages")
    public StageBreakdownResponse stages(
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
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        StageBreakdownResponse response = analyticsInsightsService.stageBreakdown(
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
        logSlow(
            "[STAGE_BREAKDOWN_PERF] controller endpoint=/api/stages totalMs={} from={} to={} module={} eventType={} requestPath={} bucket={} stages={} series={} partial={}",
            started,
            range.from(),
            range.to(),
            moduleCode,
            eventTypeCode,
            requestPath,
            bucketMinutes,
            response.stages() == null ? 0 : response.stages().size(),
            response.series() == null ? 0 : response.series().size(),
            response.partial()
        );
        return response;
    }

    @GetMapping("/stages/compare")
    public StageBreakdownCompareResponse stagesCompare(
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
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange afterRange = AnalyticsTimeRangeResolver.resolveRange(afterFrom, afterTo, Duration.ofHours(24));
        AnalyticsTimeRangeResolver.TimeRange beforeRange = resolveBeforeRange(beforeFrom, beforeTo, afterRange);

        StageBreakdownResponse before = analyticsInsightsService.stageBreakdown(
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
        StageBreakdownResponse after = analyticsInsightsService.stageBreakdown(
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
        StageBreakdownCompareResponse response = new StageBreakdownCompareResponse(before, after);
        logSlow(
            "[STAGE_BREAKDOWN_PERF] controller endpoint=/api/stages/compare totalMs={} beforeFrom={} beforeTo={} afterFrom={} afterTo={} module={} eventType={} requestPath={} bucket={} beforeStages={} afterStages={}",
            started,
            beforeRange.from(),
            beforeRange.to(),
            afterRange.from(),
            afterRange.to(),
            moduleCode,
            eventTypeCode,
            requestPath,
            bucketMinutes,
            before.stages() == null ? 0 : before.stages().size(),
            after.stages() == null ? 0 : after.stages().size()
        );
        return response;
    }

    @GetMapping("/stage-metrics")
    public StageMetricResponse stageMetrics(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) String requestPath,
        @RequestParam(required = false) String stageTypeCode,
        @RequestParam(required = false) String metricTypeCode,
        @RequestParam(required = false) String filterMetricTypeCode,
        @RequestParam(required = false) String filterMetricValue,
        @RequestParam(required = false) BigDecimal filterMetricMinValue,
        @RequestParam(required = false) BigDecimal filterMetricMaxValue,
        @RequestParam(required = false) String filterAttributeCode,
        @RequestParam(required = false) String filterAttributeValue,
        @RequestParam(required = false) BigDecimal filterAttributeMinValue,
        @RequestParam(required = false) BigDecimal filterAttributeMaxValue,
        @RequestParam(required = false) Integer bucketMinutes,
        @RequestParam(defaultValue = "true") boolean includeSummaries,
        @RequestParam(defaultValue = "true") boolean includeTopValues,
        @RequestParam(defaultValue = "true") boolean includeSeries
    ) {
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        StageMetricResponse response = analyticsInsightsService.stageMetrics(
            range.from(),
            range.to(),
            moduleCode,
            eventTypeCode,
            requestPath,
            stageTypeCode,
            metricTypeCode,
            filterMetricTypeCode,
            filterMetricValue,
            filterMetricMinValue,
            filterMetricMaxValue,
            filterAttributeCode,
            filterAttributeValue,
            filterAttributeMinValue,
            filterAttributeMaxValue,
            bucketMinutes,
            includeSummaries,
            includeTopValues,
            includeSeries
        );
        logSlow(
            "[STAGE_METRICS_PERF] controller endpoint=/api/stage-metrics totalMs={} from={} to={} module={} eventType={} stage={} metric={} bucket={} includeSummaries={} includeTopValues={} counts summaries={} series={} topValues={}",
            started,
            range.from(),
            range.to(),
            moduleCode,
            eventTypeCode,
            stageTypeCode,
            metricTypeCode,
            bucketMinutes,
            includeSummaries,
            includeTopValues,
            response.summaries() == null ? 0 : response.summaries().size(),
            response.numericSeries() == null ? 0 : response.numericSeries().size(),
            response.selectedTopValues() == null ? 0 : response.selectedTopValues().size()
        );
        return response;
    }

    @GetMapping("/stage-metrics/compare")
    public StageMetricCompareResponse stageMetricsCompare(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant beforeFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant beforeTo,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant afterFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant afterTo,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) String requestPath,
        @RequestParam(required = false) String stageTypeCode,
        @RequestParam(required = false) String metricTypeCode,
        @RequestParam(required = false) String filterMetricTypeCode,
        @RequestParam(required = false) String filterMetricValue,
        @RequestParam(required = false) BigDecimal filterMetricMinValue,
        @RequestParam(required = false) BigDecimal filterMetricMaxValue,
        @RequestParam(required = false) String filterAttributeCode,
        @RequestParam(required = false) String filterAttributeValue,
        @RequestParam(required = false) BigDecimal filterAttributeMinValue,
        @RequestParam(required = false) BigDecimal filterAttributeMaxValue,
        @RequestParam(required = false) Integer bucketMinutes,
        @RequestParam(defaultValue = "false") boolean includeSummaries,
        @RequestParam(defaultValue = "false") boolean includeTopValues,
        @RequestParam(defaultValue = "true") boolean includeSeries
    ) {
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange afterRange = AnalyticsTimeRangeResolver.resolveRange(afterFrom, afterTo, Duration.ofHours(24));
        AnalyticsTimeRangeResolver.TimeRange beforeRange = resolveBeforeRange(beforeFrom, beforeTo, afterRange);

        StageMetricResponse before = analyticsInsightsService.stageMetrics(
            beforeRange.from(),
            beforeRange.to(),
            moduleCode,
            eventTypeCode,
            requestPath,
            stageTypeCode,
            metricTypeCode,
            filterMetricTypeCode,
            filterMetricValue,
            filterMetricMinValue,
            filterMetricMaxValue,
            filterAttributeCode,
            filterAttributeValue,
            filterAttributeMinValue,
            filterAttributeMaxValue,
            bucketMinutes,
            includeSummaries,
            includeTopValues,
            includeSeries
        );
        StageMetricResponse after = analyticsInsightsService.stageMetrics(
            afterRange.from(),
            afterRange.to(),
            moduleCode,
            eventTypeCode,
            requestPath,
            stageTypeCode,
            metricTypeCode,
            filterMetricTypeCode,
            filterMetricValue,
            filterMetricMinValue,
            filterMetricMaxValue,
            filterAttributeCode,
            filterAttributeValue,
            filterAttributeMinValue,
            filterAttributeMaxValue,
            bucketMinutes,
            includeSummaries,
            includeTopValues,
            includeSeries
        );
        StageMetricCompareResponse response = new StageMetricCompareResponse(before, after);
        logSlow(
            "[STAGE_METRICS_PERF] controller endpoint=/api/stage-metrics/compare totalMs={} beforeFrom={} beforeTo={} afterFrom={} afterTo={} module={} eventType={} stage={} metric={} bucket={} includeSummaries={} includeTopValues={}",
            started,
            beforeRange.from(),
            beforeRange.to(),
            afterRange.from(),
            afterRange.to(),
            moduleCode,
            eventTypeCode,
            stageTypeCode,
            metricTypeCode,
            bucketMinutes,
            includeSummaries,
            includeTopValues
        );
        return response;
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static void logSlow(String message, long started, Object... args) {
        long totalMs = elapsedMs(started);
        if (totalMs < 500L) {
            return;
        }
        Object[] payload = new Object[args.length + 1];
        payload[0] = totalMs;
        System.arraycopy(args, 0, payload, 1, args.length);
        if (totalMs >= 3000L) {
            log.warn(message, payload);
        } else {
            log.info(message, payload);
        }
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

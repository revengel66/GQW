package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalCompareResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalResponse;
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
@RequestMapping({"/analytics/api/universal", "/analytics-admin/api/universal"})
public class AnalyticsUniversalController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsUniversalController.class);

    private final AnalyticsInsightsService analyticsInsightsService;

    public AnalyticsUniversalController(AnalyticsInsightsService analyticsInsightsService) {
        this.analyticsInsightsService = analyticsInsightsService;
    }

    @GetMapping
    public UniversalResponse universal(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) Boolean allTime,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) String requestPath,
        @RequestParam(required = false) String attributeCode,
        @RequestParam(required = false) String attributeValue,
        @RequestParam(required = false) String filterMetricTypeCode,
        @RequestParam(required = false) String filterMetricValue,
        @RequestParam(required = false) BigDecimal filterMetricMinValue,
        @RequestParam(required = false) BigDecimal filterMetricMaxValue,
        @RequestParam(required = false) String filterAttributeCode,
        @RequestParam(required = false) String filterAttributeValue,
        @RequestParam(required = false) BigDecimal filterAttributeMinValue,
        @RequestParam(required = false) BigDecimal filterAttributeMaxValue,
        @RequestParam(required = false) String stageTypeCode,
        @RequestParam(required = false) Integer bucketMinutes,
        @RequestParam(required = false, defaultValue = "true") Boolean includeEventStageBreakdown
    ) {
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange range;
        if (Boolean.TRUE.equals(allTime)) {
            range = new AnalyticsTimeRangeResolver.TimeRange(Instant.EPOCH, Instant.now());
        } else {
            range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        }
        UniversalResponse response = analyticsInsightsService.universal(
            range.from(),
            range.to(),
            moduleCode,
            eventTypeCode,
            requestPath,
            attributeCode,
            attributeValue,
            filterMetricTypeCode,
            filterMetricValue,
            filterMetricMinValue,
            filterMetricMaxValue,
            filterAttributeCode,
            filterAttributeValue,
            filterAttributeMinValue,
            filterAttributeMaxValue,
            stageTypeCode,
            bucketMinutes,
            !Boolean.FALSE.equals(includeEventStageBreakdown)
        );
        log.info(
            "[UNIVERSAL_PERF] controller endpoint=/api/universal totalMs={} from={} to={} module={} eventTypes={} stage={} attr={} includeEventStageBreakdown={} counts series={} stages={} events={} eventSeries={} eventStageBreakdown={}",
            elapsedMs(started),
            range.from(),
            range.to(),
            moduleCode,
            eventTypeCode == null ? 0 : eventTypeCode.size(),
            stageTypeCode,
            attributeCode,
            !Boolean.FALSE.equals(includeEventStageBreakdown),
            size(response.series()),
            size(response.stages()),
            size(response.eventBreakdown()),
            size(response.eventSeries()),
            size(response.eventStageBreakdown())
        );
        return response;
    }

    @GetMapping("/compare")
    public UniversalCompareResponse universalCompare(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant beforeFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant beforeTo,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant afterFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant afterTo,
        @RequestParam(required = false) Boolean afterAllTime,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) String requestPath,
        @RequestParam(required = false) String attributeCode,
        @RequestParam(required = false) String attributeValue,
        @RequestParam(required = false) String filterMetricTypeCode,
        @RequestParam(required = false) String filterMetricValue,
        @RequestParam(required = false) BigDecimal filterMetricMinValue,
        @RequestParam(required = false) BigDecimal filterMetricMaxValue,
        @RequestParam(required = false) String filterAttributeCode,
        @RequestParam(required = false) String filterAttributeValue,
        @RequestParam(required = false) BigDecimal filterAttributeMinValue,
        @RequestParam(required = false) BigDecimal filterAttributeMaxValue,
        @RequestParam(required = false) String stageTypeCode,
        @RequestParam(required = false) Integer bucketMinutes,
        @RequestParam(required = false, defaultValue = "true") Boolean includeEventStageBreakdown
    ) {
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange afterRange;
        if (Boolean.TRUE.equals(afterAllTime)) {
            afterRange = new AnalyticsTimeRangeResolver.TimeRange(Instant.EPOCH, Instant.now());
        } else {
            afterRange = AnalyticsTimeRangeResolver.resolveRange(afterFrom, afterTo, Duration.ofHours(24));
        }
        AnalyticsTimeRangeResolver.TimeRange beforeRange = resolveBeforeRange(beforeFrom, beforeTo, afterRange);

        UniversalResponse before = analyticsInsightsService.universal(
            beforeRange.from(),
            beforeRange.to(),
            moduleCode,
            eventTypeCode,
            requestPath,
            attributeCode,
            attributeValue,
            filterMetricTypeCode,
            filterMetricValue,
            filterMetricMinValue,
            filterMetricMaxValue,
            filterAttributeCode,
            filterAttributeValue,
            filterAttributeMinValue,
            filterAttributeMaxValue,
            stageTypeCode,
            bucketMinutes,
            !Boolean.FALSE.equals(includeEventStageBreakdown)
        );
        UniversalResponse after = analyticsInsightsService.universal(
            afterRange.from(),
            afterRange.to(),
            moduleCode,
            eventTypeCode,
            requestPath,
            attributeCode,
            attributeValue,
            filterMetricTypeCode,
            filterMetricValue,
            filterMetricMinValue,
            filterMetricMaxValue,
            filterAttributeCode,
            filterAttributeValue,
            filterAttributeMinValue,
            filterAttributeMaxValue,
            stageTypeCode,
            bucketMinutes,
            !Boolean.FALSE.equals(includeEventStageBreakdown)
        );
        log.info(
            "[UNIVERSAL_PERF] controller endpoint=/api/universal/compare totalMs={} beforeFrom={} beforeTo={} afterFrom={} afterTo={} module={} eventTypes={} includeEventStageBreakdown={}",
            elapsedMs(started),
            beforeRange.from(),
            beforeRange.to(),
            afterRange.from(),
            afterRange.to(),
            moduleCode,
            eventTypeCode == null ? 0 : eventTypeCode.size(),
            !Boolean.FALSE.equals(includeEventStageBreakdown)
        );
        return new UniversalCompareResponse(before, after);
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

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}

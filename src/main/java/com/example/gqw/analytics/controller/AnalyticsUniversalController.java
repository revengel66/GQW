package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.service.AnalyticsUniversalAttributeBreakdownService;
import com.example.gqw.analytics.service.AnalyticsUniversalErrorBreakdownService;
import com.example.gqw.analytics.service.AnalyticsUniversalModuleBreakdownService;
import com.example.gqw.analytics.service.AnalyticsUniversalRootCauseService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalAttributeBreakdownResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalCompareResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalErrorBreakdownResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalModuleBreakdownResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalRootCauseResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/analytics/api/universal", "/analytics-admin/api/universal", "/analytics/admin/api/universal"})
public class AnalyticsUniversalController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsUniversalController.class);

    private final AnalyticsInsightsService analyticsInsightsService;
    private final AnalyticsUniversalAttributeBreakdownService attributeBreakdownService;
    private final AnalyticsUniversalErrorBreakdownService errorBreakdownService;
    private final AnalyticsUniversalModuleBreakdownService moduleBreakdownService;
    private final AnalyticsUniversalRootCauseService rootCauseService;

    public AnalyticsUniversalController(
        AnalyticsInsightsService analyticsInsightsService,
        AnalyticsUniversalAttributeBreakdownService attributeBreakdownService,
        AnalyticsUniversalErrorBreakdownService errorBreakdownService,
        AnalyticsUniversalModuleBreakdownService moduleBreakdownService,
        AnalyticsUniversalRootCauseService rootCauseService
    ) {
        this.analyticsInsightsService = analyticsInsightsService;
        this.attributeBreakdownService = attributeBreakdownService;
        this.errorBreakdownService = errorBreakdownService;
        this.moduleBreakdownService = moduleBreakdownService;
        this.rootCauseService = rootCauseService;
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
        @RequestParam(required = false) List<String> stageTypeCodes,
        @RequestParam(required = false) List<String> stageTypeCode,
        @RequestParam(required = false) Integer bucketMinutes,
        @RequestParam(required = false, defaultValue = "true") Boolean includeEventStageBreakdown,
        @RequestParam(required = false, defaultValue = "false") Boolean systemEventsOnly,
        @RequestParam(required = false) Boolean isError
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
            merge(stageTypeCodes, stageTypeCode),
            bucketMinutes,
            !Boolean.FALSE.equals(includeEventStageBreakdown),
            Boolean.TRUE.equals(systemEventsOnly),
            isError
        );
        log.info(
            "[UNIVERSAL_PERF] controller endpoint=/api/universal totalMs={} from={} to={} module={} eventTypes={} stage={} attr={} includeEventStageBreakdown={} counts series={} stages={} events={} eventSeries={} eventStageBreakdown={}",
            elapsedMs(started),
            range.from(),
            range.to(),
            moduleCode,
            eventTypeCode == null ? 0 : eventTypeCode.size(),
            merge(stageTypeCodes, stageTypeCode).size(),
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
        @RequestParam(required = false) List<String> stageTypeCodes,
        @RequestParam(required = false) List<String> stageTypeCode,
        @RequestParam(required = false) Integer bucketMinutes,
        @RequestParam(required = false, defaultValue = "true") Boolean includeEventStageBreakdown,
        @RequestParam(required = false, defaultValue = "false") Boolean systemEventsOnly,
        @RequestParam(required = false) Boolean isError
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
            merge(stageTypeCodes, stageTypeCode),
            bucketMinutes,
            !Boolean.FALSE.equals(includeEventStageBreakdown),
            Boolean.TRUE.equals(systemEventsOnly),
            isError
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
            merge(stageTypeCodes, stageTypeCode),
            bucketMinutes,
            !Boolean.FALSE.equals(includeEventStageBreakdown),
            Boolean.TRUE.equals(systemEventsOnly),
            isError
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

    @GetMapping("/attribute-breakdown")
    public UniversalAttributeBreakdownResponse attributeBreakdown(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) Boolean allTime,
        @RequestParam(required = false) List<String> eventCodes,
        @RequestParam(required = false) List<String> eventCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) List<String> stageTypeCodes,
        @RequestParam(required = false) List<String> stageTypeCode,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) String attributeCode,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset,
        @RequestParam(required = false) String sortBy,
        @RequestParam(required = false) String sortDir
    ) {
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange range;
        if (Boolean.TRUE.equals(allTime)) {
            range = new AnalyticsTimeRangeResolver.TimeRange(Instant.EPOCH, Instant.now());
        } else {
            range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        }
        List<String> resolvedEventCodes = merge(eventCodes, eventCode, eventTypeCode);
        List<String> resolvedStageCodes = merge(stageTypeCodes, stageTypeCode);
        UniversalAttributeBreakdownResponse response = attributeBreakdownService.breakdown(
            range.from(),
            range.to(),
            resolvedEventCodes,
            resolvedStageCodes,
            moduleCode,
            attributeCode,
            limit,
            offset,
            sortBy,
            sortDir
        );
        log.info(
            "[UNIVERSAL_PERF] controller endpoint=/api/universal/attribute-breakdown totalMs={} from={} to={} eventCodes={} stageCodes={} attr={} limit={} offset={} sortBy={} sortDir={} rows={} total={}",
            elapsedMs(started),
            range.from(),
            range.to(),
            resolvedEventCodes.size(),
            resolvedStageCodes.size(),
            attributeCode,
            limit,
            offset,
            sortBy,
            sortDir,
            size(response.rows()),
            response.total()
        );
        return response;
    }

    @GetMapping("/root-cause")
    public UniversalRootCauseResponse rootCause(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) Boolean allTime,
        @RequestParam(required = false) List<String> eventCodes,
        @RequestParam(required = false) List<String> eventCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) List<String> stageTypeCodes,
        @RequestParam(required = false) List<String> stageTypeCode,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) String attributeCode,
        @RequestParam(required = false) String attributeValue,
        @RequestParam(required = false) Integer limit
    ) {
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange range;
        if (Boolean.TRUE.equals(allTime)) {
            range = new AnalyticsTimeRangeResolver.TimeRange(Instant.EPOCH, Instant.now());
        } else {
            range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        }
        List<String> resolvedEventCodes = merge(eventCodes, eventCode, eventTypeCode);
        List<String> resolvedStageCodes = merge(stageTypeCodes, stageTypeCode);
        UniversalRootCauseResponse response = rootCauseService.rootCause(
            range.from(),
            range.to(),
            resolvedEventCodes,
            resolvedStageCodes,
            moduleCode,
            attributeCode,
            attributeValue,
            limit
        );
        log.info(
            "[UNIVERSAL_PERF] controller endpoint=/api/universal/root-cause totalMs={} from={} to={} eventCodes={} stageCodes={} attr={} value={} factors={} problemEvents={}",
            elapsedMs(started),
            range.from(),
            range.to(),
            resolvedEventCodes.size(),
            resolvedStageCodes.size(),
            attributeCode,
            attributeValue == null ? "" : "[set]",
            size(response.factors()),
            response.problemEventCount()
        );
        return response;
    }

    @GetMapping("/event-root-cause")
    public UniversalRootCauseResponse eventRootCause(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) Boolean allTime,
        @RequestParam(required = false) List<String> eventCodes,
        @RequestParam(required = false) String eventCode,
        @RequestParam(required = false) String eventTypeCode,
        @RequestParam(required = false) List<String> stageTypeCodes,
        @RequestParam(required = false) List<String> stageTypeCode,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) Integer limit
    ) {
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange range;
        if (Boolean.TRUE.equals(allTime)) {
            range = new AnalyticsTimeRangeResolver.TimeRange(Instant.EPOCH, Instant.now());
        } else {
            range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        }
        List<String> resolvedEventCodes = merge(eventCodes, singleOrEmpty(eventCode), singleOrEmpty(eventTypeCode));
        List<String> resolvedStageCodes = merge(stageTypeCodes, stageTypeCode);
        UniversalRootCauseResponse response = rootCauseService.eventRootCause(
            range.from(),
            range.to(),
            resolvedEventCodes,
            resolvedStageCodes,
            moduleCode,
            limit
        );
        log.info(
            "[UNIVERSAL_PERF] controller endpoint=/api/universal/event-root-cause totalMs={} from={} to={} eventCodes={} stageCodes={} factors={} problemEvents={}",
            elapsedMs(started),
            range.from(),
            range.to(),
            resolvedEventCodes.size(),
            resolvedStageCodes.size(),
            size(response.factors()),
            response.problemEventCount()
        );
        return response;
    }

    @GetMapping("/error-breakdown")
    public UniversalErrorBreakdownResponse errorBreakdown(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) Boolean allTime,
        @RequestParam(required = false) List<String> eventCodes,
        @RequestParam(required = false) List<String> eventCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) List<String> stageTypeCodes,
        @RequestParam(required = false) List<String> stageTypeCode,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset,
        @RequestParam(required = false) String sortBy,
        @RequestParam(required = false) String sortDir
    ) {
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange range;
        if (Boolean.TRUE.equals(allTime)) {
            range = new AnalyticsTimeRangeResolver.TimeRange(Instant.EPOCH, Instant.now());
        } else {
            range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        }
        List<String> resolvedEventCodes = merge(eventCodes, eventCode, eventTypeCode);
        List<String> resolvedStageCodes = merge(stageTypeCodes, stageTypeCode);
        UniversalErrorBreakdownResponse response = errorBreakdownService.breakdown(
            range.from(),
            range.to(),
            resolvedEventCodes,
            resolvedStageCodes,
            moduleCode,
            limit,
            offset,
            sortBy,
            sortDir
        );
        log.info(
            "[UNIVERSAL_PERF] controller endpoint=/api/universal/error-breakdown totalMs={} from={} to={} eventCodes={} stageCodes={} limit={} offset={} sortBy={} sortDir={} rows={} total={}",
            elapsedMs(started),
            range.from(),
            range.to(),
            resolvedEventCodes.size(),
            resolvedStageCodes.size(),
            limit,
            offset,
            sortBy,
            sortDir,
            size(response.rows()),
            response.total()
        );
        return response;
    }

    @GetMapping("/error-root-cause")
    public UniversalRootCauseResponse errorRootCause(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) Boolean allTime,
        @RequestParam(required = false) List<String> eventCodes,
        @RequestParam(required = false) List<String> eventCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) List<String> stageTypeCodes,
        @RequestParam(required = false) List<String> stageTypeCode,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) String errorKey,
        @RequestParam(required = false) Boolean systemEventsOnly,
        @RequestParam(required = false) Integer limit
    ) {
        long started = System.nanoTime();
        AnalyticsTimeRangeResolver.TimeRange range;
        if (Boolean.TRUE.equals(allTime)) {
            range = new AnalyticsTimeRangeResolver.TimeRange(Instant.EPOCH, Instant.now());
        } else {
            range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        }
        List<String> resolvedEventCodes = merge(eventCodes, eventCode, eventTypeCode);
        List<String> resolvedStageCodes = merge(stageTypeCodes, stageTypeCode);
        UniversalRootCauseResponse response = errorBreakdownService.rootCause(
            range.from(),
            range.to(),
            resolvedEventCodes,
            resolvedStageCodes,
            moduleCode,
            errorKey,
            systemEventsOnly,
            limit
        );
        log.info(
            "[UNIVERSAL_PERF] controller endpoint=/api/universal/error-root-cause totalMs={} from={} to={} eventCodes={} stageCodes={} errorKey={} factors={} problemEvents={}",
            elapsedMs(started),
            range.from(),
            range.to(),
            resolvedEventCodes.size(),
            resolvedStageCodes.size(),
            errorKey == null ? "" : "[set]",
            size(response.factors()),
            response.problemEventCount()
        );
        return response;
    }

    @GetMapping("/module-breakdown")
    public UniversalModuleBreakdownResponse moduleBreakdown(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) Boolean allTime,
        @RequestParam(required = false) List<String> eventCodes,
        @RequestParam(required = false) List<String> eventCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) List<String> stageTypeCodes,
        @RequestParam(required = false) List<String> stageTypeCode,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset,
        @RequestParam(required = false) String sortBy,
        @RequestParam(required = false) String sortDir
    ) {
        AnalyticsTimeRangeResolver.TimeRange range = Boolean.TRUE.equals(allTime)
            ? new AnalyticsTimeRangeResolver.TimeRange(Instant.EPOCH, Instant.now())
            : AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        return moduleBreakdownService.breakdown(
            range.from(),
            range.to(),
            merge(eventCodes, eventCode, eventTypeCode),
            merge(stageTypeCodes, stageTypeCode),
            moduleCode,
            limit,
            offset,
            sortBy,
            sortDir
        );
    }

    @GetMapping("/module-root-cause")
    public UniversalRootCauseResponse moduleRootCause(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) Boolean allTime,
        @RequestParam(required = false) List<String> eventCodes,
        @RequestParam(required = false) List<String> eventCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) List<String> stageTypeCodes,
        @RequestParam(required = false) List<String> stageTypeCode,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) String selectedStageTypeCode,
        @RequestParam(required = false) Boolean systemEventsOnly,
        @RequestParam(required = false) Integer limit
    ) {
        AnalyticsTimeRangeResolver.TimeRange range = Boolean.TRUE.equals(allTime)
            ? new AnalyticsTimeRangeResolver.TimeRange(Instant.EPOCH, Instant.now())
            : AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        return moduleBreakdownService.rootCause(
            range.from(),
            range.to(),
            merge(eventCodes, eventCode, eventTypeCode),
            merge(stageTypeCodes, stageTypeCode),
            moduleCode,
            selectedStageTypeCode,
            systemEventsOnly,
            limit
        );
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

    private static List<String> singleOrEmpty(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.trim());
    }

    @SafeVarargs
    private static List<String> merge(List<String>... sources) {
        List<String> result = new ArrayList<>();
        if (sources == null) {
            return result;
        }
        for (List<String> source : sources) {
            if (source == null) {
                continue;
            }
            source.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .filter(value -> !result.contains(value))
                .forEach(result::add);
        }
        return result;
    }
}

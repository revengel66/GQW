package com.example.gqw.analytics.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalyticsApiDto {

    private AnalyticsApiDto() {
    }

    public record OptionDto(
        String code,
        String name
    ) {
    }

    public record DictionariesResponse(
        List<OptionDto> modules,
        List<OptionDto> eventTypes,
        List<OptionDto> stageTypes,
        List<OptionDto> stageMetricTypes,
        List<OptionDto> eventAttributeTypes
    ) {
    }

    public record RangeStartResponse(
        Instant from
    ) {
    }

    public record KpiSnapshot(
        long count,
        long errorCount,
        long successCount,
        BigDecimal errorRate,
        BigDecimal avgMs,
        BigDecimal p95Ms,
        BigDecimal p99Ms,
        BigDecimal maxMs
    ) {
    }

    public record TimeSeriesPointDto(
        Instant time,
        long count,
        BigDecimal avgMs,
        BigDecimal p95Ms,
        BigDecimal p99Ms,
        BigDecimal errorRate
    ) {
    }

    public record EventKpiDto(
        String eventTypeCode,
        String eventTypeName,
        long count,
        BigDecimal errorRate,
        BigDecimal avgMs,
        BigDecimal p95Ms,
        BigDecimal p99Ms,
        BigDecimal maxMs
    ) {
    }

    public record OverviewResponse(
        Instant from,
        Instant to,
        int bucketMinutes,
        KpiSnapshot totals,
        List<EventKpiDto> eventBreakdown,
        List<TimeSeriesPointDto> series,
        boolean partial,
        String warning
    ) {
        public OverviewResponse(
            Instant from,
            Instant to,
            int bucketMinutes,
            KpiSnapshot totals,
            List<EventKpiDto> eventBreakdown,
            List<TimeSeriesPointDto> series
        ) {
            this(from, to, bucketMinutes, totals, eventBreakdown, series, false, null);
        }
    }

    public record OverviewCompareResponse(
        OverviewResponse before,
        OverviewResponse after
    ) {
    }

    public record FilterOptionsResponse(
        List<OptionDto> modules,
        List<OptionDto> eventTypes,
        List<OptionDto> attributeTypes,
        List<OptionDto> attributeValues,
        boolean partial,
        String warning
    ) {
        public FilterOptionsResponse(
            List<OptionDto> modules,
            List<OptionDto> eventTypes,
            List<OptionDto> attributeTypes,
            List<OptionDto> attributeValues
        ) {
            this(modules, eventTypes, attributeTypes, attributeValues, false, null);
        }
    }

    public record StageKpiDto(
        String stageTypeCode,
        String stageTypeName,
        long count,
        BigDecimal errorRate,
        BigDecimal avgMs,
        BigDecimal p95Ms,
        BigDecimal p99Ms,
        BigDecimal maxMs
    ) {
    }

    public record StageSeriesDto(
        String stageTypeCode,
        String stageTypeName,
        List<TimeSeriesPointDto> p95Series
    ) {
    }

    public record StageBreakdownResponse(
        Instant from,
        Instant to,
        int bucketMinutes,
        List<StageKpiDto> stages,
        List<StageSeriesDto> series,
        boolean partial,
        String warning
    ) {
        public StageBreakdownResponse(
            Instant from,
            Instant to,
            int bucketMinutes,
            List<StageKpiDto> stages,
            List<StageSeriesDto> series
        ) {
            this(from, to, bucketMinutes, stages, series, false, null);
        }
    }

    public record StageBreakdownCompareResponse(
        StageBreakdownResponse before,
        StageBreakdownResponse after
    ) {
    }

    public record TopValueDto(
        String value,
        long count
    ) {
    }

    public record StageMetricSummaryDto(
        String metricTypeCode,
        String metricTypeName,
        String metricTypeDescription,
        String metricTypeReadingGuide,
        String unit,
        boolean numeric,
        long sampleCount,
        BigDecimal avgValue,
        BigDecimal p95Value,
        BigDecimal minValue,
        BigDecimal maxValue,
        List<TopValueDto> topValues
    ) {
    }

    public record StageMetricResponse(
        Instant from,
        Instant to,
        int bucketMinutes,
        String selectedMetricTypeCode,
        String selectedMetricTypeName,
        String selectedUnit,
        boolean selectedNumeric,
        List<StageMetricSummaryDto> summaries,
        List<TimeSeriesPointDto> numericSeries,
        List<TopValueDto> selectedTopValues,
        boolean partial,
        String warning
    ) {
        public StageMetricResponse(
            Instant from,
            Instant to,
            int bucketMinutes,
            String selectedMetricTypeCode,
            String selectedMetricTypeName,
            String selectedUnit,
            boolean selectedNumeric,
            List<StageMetricSummaryDto> summaries,
            List<TimeSeriesPointDto> numericSeries,
            List<TopValueDto> selectedTopValues
        ) {
            this(
                from,
                to,
                bucketMinutes,
                selectedMetricTypeCode,
                selectedMetricTypeName,
                selectedUnit,
                selectedNumeric,
                summaries,
                numericSeries,
                selectedTopValues,
                false,
                null
            );
        }
    }

    public record StageMetricCompareResponse(
        StageMetricResponse before,
        StageMetricResponse after
    ) {
    }

    public record EventStageBriefDto(
        String stageTypeCode,
        String stageTypeName,
        Integer durationMs,
        Boolean isError
    ) {
    }

    public record EventListItemDto(
        Long eventId,
        UUID eventUid,
        Instant startedAt,
        String moduleCode,
        String moduleName,
        String eventTypeCode,
        String eventTypeName,
        Integer durationMs,
        Integer statusCode,
        Boolean isError,
        String errorClass,
        String requestPath,
        String traceId,
        String errorMessage,
        Map<String, String> attributes,
        List<EventStageBriefDto> stages
    ) {
    }

    public record EventListResponse(
        long total,
        int page,
        int size,
        boolean hasMore,
        List<EventListItemDto> items,
        boolean partial,
        String warning
    ) {
        public EventListResponse(
            long total,
            int page,
            int size,
            boolean hasMore,
            List<EventListItemDto> items
        ) {
            this(total, page, size, hasMore, items, false, null);
        }
    }

    public record EventStageMetricDto(
        String metricTypeCode,
        String metricTypeName,
        BigDecimal metricValueNum,
        String metricValueText,
        String unit
    ) {
    }

    public record EventLogEntryDto(
        Instant timestamp,
        String level,
        String status,
        String layer,
        String source,
        String operation,
        Integer durationMs,
        String message,
        String rawMessage,
        String logger,
        String traceId,
        String eventUid,
        String moduleCode
    ) {
    }

    public record EventLogExcerptDto(
        Instant timestamp,
        String level,
        String source,
        String messageShort,
        String excerpt,
        Long lineNumber
    ) {
    }

    public record EventTraceLogStatusDto(
        String status,
        String message,
        String moduleCode,
        String fileName,
        String filePath,
        Instant fromTs,
        Instant toTs,
        Long lineCount,
        Long errorCount,
        Long warnCount,
        boolean archiveReadable,
        String summary,
        List<EventLogExcerptDto> excerpts
    ) {
    }

    public record EventStageDetailsDto(
        String stageTypeCode,
        String stageTypeName,
        Integer stageOrder,
        Instant startedAt,
        Instant endedAt,
        Instant logStartedAt,
        Instant logEndedAt,
        Integer durationMs,
        Boolean isError,
        String errorMessage,
        List<EventStageMetricDto> metrics,
        List<EventLogEntryDto> logs
    ) {
    }

    public record EventAttributeDto(
        String attributeTypeCode,
        String attributeTypeName,
        String value,
        String valueJson
    ) {
    }

    public record EventDetailsResponse(
        UUID eventUid,
        Instant startedAt,
        Instant endedAt,
        Integer durationMs,
        String moduleCode,
        String moduleName,
        String eventTypeCode,
        String eventTypeName,
        Integer statusCode,
        Boolean isError,
        String errorClass,
        String requestPath,
        String httpMethod,
        String traceId,
        String errorMessage,
        List<EventAttributeDto> attributes,
        List<EventStageDetailsDto> stages,
        EventTraceLogStatusDto traceLogStatus,
        List<EventLogEntryDto> traceLogs
    ) {
    }

    public record KpiDelta(
        BigDecimal countPct,
        BigDecimal avgMsPct,
        BigDecimal p95MsPct,
        BigDecimal errorRatePct
    ) {
    }

    public record CompareEventRow(
        String eventTypeCode,
        String eventTypeName,
        KpiSnapshot baseline,
        KpiSnapshot target,
        KpiDelta delta,
        long countDelta,
        long errorCountDelta
    ) {
    }

    public record CompareResponse(
        Instant baselineFrom,
        Instant baselineTo,
        KpiSnapshot baseline,
        Instant targetFrom,
        Instant targetTo,
        KpiSnapshot target,
        KpiDelta delta,
        List<CompareEventRow> events
    ) {
    }

    public record UniversalResponse(
        Instant from,
        Instant to,
        int bucketMinutes,
        KpiSnapshot totals,
        List<TimeSeriesPointDto> series,
        List<StageKpiDto> stages,
        List<EventKpiDto> eventBreakdown,
        List<UniversalEventSeriesDto> eventSeries,
        List<UniversalEventStageBreakdownDto> eventStageBreakdown,
        List<OptionDto> availableAttributeTypes
    ) {
    }

    public record UniversalCompareResponse(
        UniversalResponse before,
        UniversalResponse after
    ) {
    }

    public record UniversalAttributeBreakdownResponse(
        String attributeCode,
        long total,
        long criticalTotal,
        long warningTotal,
        long normalTotal,
        long problemEventCount,
        List<UniversalAttributeBreakdownRowDto> rows
    ) {
    }

    public record UniversalAttributeBreakdownRowDto(
        String value,
        long count,
        BigDecimal share,
        BigDecimal avgMs,
        BigDecimal p95Ms,
        BigDecimal errorRate,
        String severityLevel
    ) {
    }

    public record UniversalRootCauseResponse(
        String attributeCode,
        String attributeValue,
        long problemEventCount,
        long criticalValueCount,
        long warningValueCount,
        List<UniversalRootCauseFactorDto> factors
    ) {
    }

    public record UniversalRootCauseFactorDto(
        String attributeCode,
        String value,
        long count,
        BigDecimal share,
        BigDecimal avgMs,
        BigDecimal p95Ms,
        BigDecimal errorRate
    ) {
    }

    public record UniversalErrorBreakdownResponse(
        long total,
        long criticalTotal,
        long warningTotal,
        long normalTotal,
        long problemEventCount,
        List<UniversalErrorBreakdownRowDto> rows
    ) {
    }

    public record UniversalErrorBreakdownRowDto(
        String errorKey,
        String errorMessage,
        boolean systemEvent,
        String eventTypeCode,
        long count,
        BigDecimal share,
        long eventCount,
        BigDecimal avgMs,
        BigDecimal p95Ms,
        Instant lastSeen,
        String severityLevel
    ) {
    }

    public record UniversalModuleBreakdownResponse(
        long total,
        long criticalTotal,
        long warningTotal,
        long normalTotal,
        long problemEventCount,
        List<UniversalModuleBreakdownRowDto> rows
    ) {
    }

    public record UniversalModuleBreakdownRowDto(
        String moduleKey,
        String moduleCode,
        String moduleName,
        String stageTypeCode,
        String stageTypeName,
        long count,
        BigDecimal share,
        long errorCount,
        BigDecimal errorRate,
        BigDecimal avgMs,
        BigDecimal p95Ms,
        long eventCount,
        boolean systemEvent,
        String severityLevel
    ) {
    }

    public record UniversalEventSeriesDto(
        String eventTypeCode,
        String eventTypeName,
        List<TimeSeriesPointDto> series
    ) {
    }

    public record UniversalEventStageBreakdownDto(
        String eventTypeCode,
        String eventTypeName,
        List<StageKpiDto> stages
    ) {
    }
}

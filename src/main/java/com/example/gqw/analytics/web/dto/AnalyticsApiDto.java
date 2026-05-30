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
        List<TimeSeriesPointDto> series
    ) {
    }

    public record FilterOptionsResponse(
        List<OptionDto> eventTypes,
        List<OptionDto> attributeTypes,
        List<OptionDto> attributeValues
    ) {
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
        List<StageSeriesDto> series
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
        List<TopValueDto> selectedTopValues
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
        List<EventListItemDto> items
    ) {
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

    public record CompareResponse(
        Instant baselineFrom,
        Instant baselineTo,
        KpiSnapshot baseline,
        Instant targetFrom,
        Instant targetTo,
        KpiSnapshot target,
        KpiDelta delta
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

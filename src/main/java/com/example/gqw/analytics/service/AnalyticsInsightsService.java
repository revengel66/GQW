package com.example.gqw.analytics.service;
 
import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import com.example.gqw.analytics.entity.AnalyticsStage;
import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.repository.AnalyticsEventAttributeRepository;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.analytics.repository.AnalyticsStageMetricRepository;
import com.example.gqw.analytics.repository.AnalyticsStageRepository;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import com.example.gqw.analytics.repository.EventTypeRepository;
import com.example.gqw.analytics.repository.ModuleTypeRepository;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import com.example.gqw.analytics.repository.StageTypeRepository;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.CompareResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.DictionariesResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventAttributeDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventDetailsResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventLogEntryDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventKpiDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventListItemDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventListResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventStageBriefDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventStageDetailsDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventStageMetricDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.FilterOptionsResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.KpiDelta;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.KpiSnapshot;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.OptionDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.OverviewResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.StageBreakdownResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.StageKpiDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.StageMetricResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.StageMetricSummaryDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.StageSeriesDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.TimeSeriesPointDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.TopValueDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalEventSeriesDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalEventStageBreakdownDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsInsightsService {
    private static final int DEFAULT_EVENT_PAGE_SIZE = 20;
    private static final int MAX_EVENT_PAGE_SIZE = 100;
    private static final int IN_CLAUSE_BATCH_SIZE = 2000;

    private final AnalyticsEventRepository eventRepository;
    private final AnalyticsStageRepository stageRepository;
    private final AnalyticsStageMetricRepository stageMetricRepository;
    private final AnalyticsEventAttributeRepository eventAttributeRepository;
    private final EventTypeRepository eventTypeRepository;
    private final ModuleTypeRepository moduleTypeRepository;
    private final StageTypeRepository stageTypeRepository;
    private final StageMetricTypeRepository stageMetricTypeRepository;
    private final EventAttributeTypeRepository eventAttributeTypeRepository;
    private final AnalyticsLogViewService analyticsLogViewService;
    private final AnalyticsFilterRollupService filterRollupService;

    public AnalyticsInsightsService(
        AnalyticsEventRepository eventRepository,
        AnalyticsStageRepository stageRepository,
        AnalyticsStageMetricRepository stageMetricRepository,
        AnalyticsEventAttributeRepository eventAttributeRepository,
        EventTypeRepository eventTypeRepository,
        ModuleTypeRepository moduleTypeRepository,
        StageTypeRepository stageTypeRepository,
        StageMetricTypeRepository stageMetricTypeRepository,
        EventAttributeTypeRepository eventAttributeTypeRepository,
        AnalyticsLogViewService analyticsLogViewService,
        AnalyticsFilterRollupService filterRollupService
    ) {
        this.eventRepository = eventRepository;
        this.stageRepository = stageRepository;
        this.stageMetricRepository = stageMetricRepository;
        this.eventAttributeRepository = eventAttributeRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.moduleTypeRepository = moduleTypeRepository;
        this.stageTypeRepository = stageTypeRepository;
        this.stageMetricTypeRepository = stageMetricTypeRepository;
        this.eventAttributeTypeRepository = eventAttributeTypeRepository;
        this.analyticsLogViewService = analyticsLogViewService;
        this.filterRollupService = filterRollupService;
    }

    public DictionariesResponse dictionaries(String moduleCode) {
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        List<OptionDto> modules = moduleTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .map(type -> new OptionDto(type.getCode(), type.getName()))
            .toList();
        List<OptionDto> eventTypes = (normalizedModuleCode == null
            ? eventTypeRepository.findByIsActiveTrueAndIsSystemFalseOrderByNameAsc()
            : eventTypeRepository.findByIsActiveTrueAndModuleCodeAndIsSystemFalseOrderByNameAsc(normalizedModuleCode)).stream()
            .map(type -> new OptionDto(type.getCode(), type.getName()))
            .toList();
        List<OptionDto> stageTypes = stageTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .map(type -> new OptionDto(type.getCode(), type.getName()))
            .toList();
        List<OptionDto> metricTypes = stageMetricTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .map(type -> new OptionDto(type.getCode(), localizeStageMetricTypeName(type.getCode(), type.getName())))
            .toList();
        List<OptionDto> attributeTypes = eventAttributeTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .map(type -> new OptionDto(type.getCode(), type.getName()))
            .toList();
        return new DictionariesResponse(modules, eventTypes, stageTypes, metricTypes, attributeTypes);
    }

    public Instant firstEventStartedAt() {
        return eventRepository.findMinStartedAt();
    }

    public FilterOptionsResponse filterOptions(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String requestPath,
        String attributeCode
    ) {
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        String normalizedEventType = normalizeCode(eventTypeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        String normalizedAttributeCode = normalizeCode(attributeCode);
        Set<String> hiddenSystemEventCodes = hiddenSystemEventTypeCodes();
        if (normalizedEventType != null && hiddenSystemEventCodes.contains(normalizedEventType)) {
            normalizedEventType = null;
        }

        Map<String, String> eventTypeNames = eventTypeNameMap();
        Map<String, String> attributeTypeNames = eventAttributeTypeNameMap();

        boolean useRollup = filterRollupService.shouldUseRollup(from, to, normalizedRequestPath);

        List<String> eventTypeCodes = useRollup
            ? filterRollupService.findEventTypeCodes(from, to, normalizedModuleCode)
            : (normalizedRequestPath == null
                ? eventRepository.findDistinctEventTypeCodesByScopeNoPath(from, to, normalizedModuleCode)
                : eventRepository.findDistinctEventTypeCodesByScope(from, to, normalizedModuleCode, normalizedRequestPath));
        List<OptionDto> eventTypes = eventTypeCodes
            .stream()
            .filter(code -> !hiddenSystemEventCodes.contains(normalizeCode(code)))
            .map(code -> new OptionDto(code, eventTypeNames.getOrDefault(code, code)))
            .toList();

        List<String> attributeTypeCodes = useRollup
            ? filterRollupService.findAttributeTypeCodes(from, to, normalizedModuleCode, normalizedEventType)
            : (normalizedRequestPath == null
                ? eventAttributeRepository.findDistinctAttributeTypeCodesByScopeNoPath(
                    from,
                    to,
                    normalizedModuleCode,
                    normalizedEventType
                )
                : eventAttributeRepository.findDistinctAttributeTypeCodesByScope(
                    from,
                    to,
                    normalizedModuleCode,
                    normalizedEventType,
                    normalizedRequestPath
                ));
        List<OptionDto> attributeTypes = attributeTypeCodes
            .stream()
            .map(code -> new OptionDto(code, attributeTypeNames.getOrDefault(code, code)))
            .toList();

        List<OptionDto> attributeValues = normalizedAttributeCode == null
            ? List.of()
            : (useRollup
                ? filterRollupService.findAttributeValues(
                    from,
                    to,
                    normalizedModuleCode,
                    normalizedEventType,
                    normalizedAttributeCode
                )
                : (normalizedRequestPath == null
                    ? eventAttributeRepository.findDistinctAttributeValuesByScopeNoPath(
                        from,
                        to,
                        normalizedModuleCode,
                        normalizedEventType,
                        normalizedAttributeCode
                    )
                    : eventAttributeRepository.findDistinctAttributeValuesByScope(
                        from,
                        to,
                        normalizedModuleCode,
                        normalizedEventType,
                        normalizedRequestPath,
                        normalizedAttributeCode
                    )))
                .stream()
                .map(value -> new OptionDto(value, value))
                .toList();

        return new FilterOptionsResponse(eventTypes, attributeTypes, attributeValues);
    }

    public OverviewResponse overview(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String requestPath,
        String filterMetricTypeCode,
        String filterMetricValue,
        BigDecimal filterMetricMinValue,
        BigDecimal filterMetricMaxValue,
        String filterAttributeCode,
        String filterAttributeValue,
        BigDecimal filterAttributeMinValue,
        BigDecimal filterAttributeMaxValue,
        Integer bucketMinutes
    ) {
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        String normalizedEventType = normalizeCode(eventTypeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        List<AnalyticsEvent> events = filterEventsByRequestPath(
            eventRepository.findAllByRangeOrdered(from, to, normalizedEventType, normalizedModuleCode),
            normalizedRequestPath
        );
        events = filterEventsByMetric(events, filterMetricTypeCode, filterMetricValue, filterMetricMinValue, filterMetricMaxValue);
        events = filterEventsByAttribute(events, filterAttributeCode, filterAttributeValue, filterAttributeMinValue, filterAttributeMaxValue);

        int resolvedBucket = resolveBucketMinutes(from, to, bucketMinutes);
        KpiSnapshot totals = snapshotFromEvents(events);

        Map<String, String> eventTypeNames = eventTypeNameMap();
        List<EventKpiDto> breakdown = events.stream()
            .collect(Collectors.groupingBy(AnalyticsEvent::getEventTypeCode))
            .entrySet()
            .stream()
            .map(entry -> {
                String code = entry.getKey();
                KpiSnapshot stat = snapshotFromEvents(entry.getValue());
                return new EventKpiDto(
                    code,
                    eventTypeNames.getOrDefault(code, code),
                    stat.count(),
                    stat.errorRate(),
                    stat.avgMs(),
                    stat.p95Ms(),
                    stat.p99Ms(),
                    stat.maxMs()
                );
            })
            .sorted(Comparator.comparing(EventKpiDto::count).reversed().thenComparing(EventKpiDto::eventTypeCode))
            .toList();

        List<TimeSeriesPointDto> series = buildSeries(
            from,
            to,
            resolvedBucket,
            events,
            AnalyticsEvent::getStartedAt,
            AnalyticsEvent::getDurationMs,
            event -> Boolean.TRUE.equals(event.getIsError())
        );
        return new OverviewResponse(from, to, resolvedBucket, totals, breakdown, series);
    }

    public StageBreakdownResponse stageBreakdown(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String requestPath,
        String filterMetricTypeCode,
        String filterMetricValue,
        BigDecimal filterMetricMinValue,
        BigDecimal filterMetricMaxValue,
        String filterAttributeCode,
        String filterAttributeValue,
        BigDecimal filterAttributeMinValue,
        BigDecimal filterAttributeMaxValue,
        Integer bucketMinutes
    ) {
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        String normalizedEventType = normalizeCode(eventTypeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        List<AnalyticsEvent> events = filterEventsByRequestPath(
            eventRepository.findAllByRangeOrdered(from, to, normalizedEventType, normalizedModuleCode),
            normalizedRequestPath
        );
        events = filterEventsByMetric(events, filterMetricTypeCode, filterMetricValue, filterMetricMinValue, filterMetricMaxValue);
        events = filterEventsByAttribute(events, filterAttributeCode, filterAttributeValue, filterAttributeMinValue, filterAttributeMaxValue);
        if (events.isEmpty()) {
            return new StageBreakdownResponse(from, to, resolveBucketMinutes(from, to, bucketMinutes), List.of(), List.of());
        }

        Map<String, String> stageTypeNames = stageTypeNameMap();
        List<AnalyticsStage> stages = findStagesByEventIds(ids(events));
        int resolvedBucket = resolveBucketMinutes(from, to, bucketMinutes);

        List<StageKpiDto> kpi = stages.stream()
            .collect(Collectors.groupingBy(AnalyticsStage::getStageTypeCode))
            .entrySet()
            .stream()
            .map(entry -> {
                KpiSnapshot stageStat = snapshotFromStages(entry.getValue());
                String code = entry.getKey();
                return new StageKpiDto(
                    code,
                    stageTypeNames.getOrDefault(code, code),
                    stageStat.count(),
                    stageStat.errorRate(),
                    stageStat.avgMs(),
                    stageStat.p95Ms(),
                    stageStat.p99Ms(),
                    stageStat.maxMs()
                );
            })
            .sorted(Comparator.comparing(StageKpiDto::count).reversed().thenComparing(StageKpiDto::stageTypeCode))
            .toList();

        Set<String> topStageCodes = kpi.stream()
            .limit(6)
            .map(StageKpiDto::stageTypeCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, List<AnalyticsStage>> byStageType = stages.stream()
            .filter(stage -> topStageCodes.contains(stage.getStageTypeCode()))
            .collect(Collectors.groupingBy(AnalyticsStage::getStageTypeCode));

        List<StageSeriesDto> stageSeries = byStageType.entrySet().stream()
            .map(entry -> new StageSeriesDto(
                entry.getKey(),
                stageTypeNames.getOrDefault(entry.getKey(), entry.getKey()),
                buildSeries(
                    from,
                    to,
                    resolvedBucket,
                    entry.getValue(),
                    AnalyticsStage::getStartedAt,
                    AnalyticsStage::getDurationMs,
                    stage -> Boolean.TRUE.equals(stage.getIsError())
                )
            ))
            .sorted(Comparator.comparing(StageSeriesDto::stageTypeCode))
            .toList();

        return new StageBreakdownResponse(from, to, resolvedBucket, kpi, stageSeries);
    }

    public StageMetricResponse stageMetrics(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String requestPath,
        String stageTypeCode,
        String metricTypeCode,
        String filterMetricTypeCode,
        String filterMetricValue,
        BigDecimal filterMetricMinValue,
        BigDecimal filterMetricMaxValue,
        String filterAttributeCode,
        String filterAttributeValue,
        BigDecimal filterAttributeMinValue,
        BigDecimal filterAttributeMaxValue,
        Integer bucketMinutes,
        boolean includeSummaries
    ) {
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        String normalizedEventType = normalizeCode(eventTypeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        String normalizedStageType = normalizeCode(stageTypeCode);
        String normalizedMetricType = normalizeCode(metricTypeCode);
        int resolvedBucket = resolveBucketMinutes(from, to, bucketMinutes);

        boolean hasAdditionalEventFilters = normalizeCode(filterMetricTypeCode) != null
            || normalizeText(filterMetricValue) != null
            || filterMetricMinValue != null
            || filterMetricMaxValue != null
            || normalizeCode(filterAttributeCode) != null
            || normalizeText(filterAttributeValue) != null
            || filterAttributeMinValue != null
            || filterAttributeMaxValue != null;

        // Fast path for large periods: direct DB join for a concrete metric type, without loading all events/stages into memory.
        if (!includeSummaries
            && normalizedMetricType != null
            && normalizedRequestPath == null
            && !hasAdditionalEventFilters) {
            List<AnalyticsStageMetric> scopedMetrics = stageMetricRepository.findByScope(
                from,
                to,
                normalizedModuleCode,
                normalizedEventType,
                normalizedStageType,
                normalizedMetricType
            );
            if (scopedMetrics.isEmpty()) {
                return new StageMetricResponse(from, to, resolvedBucket, null, null, null, false, List.of(), List.of(), List.of());
            }

            Map<String, String> metricTypeNames = stageMetricTypeNameMap();
            List<BigDecimal> numericValues = scopedMetrics.stream()
                .map(AnalyticsStageMetric::getMetricValueNum)
                .filter(Objects::nonNull)
                .toList();
            boolean numeric = !numericValues.isEmpty();
            String unit = scopedMetrics.stream()
                .map(AnalyticsStageMetric::getUnit)
                .filter(unitValue -> unitValue != null && !unitValue.isBlank())
                .findFirst()
                .orElse(null);
            List<TimeSeriesPointDto> numericSeries = numeric
                ? buildSeries(
                    from,
                    to,
                    resolvedBucket,
                    scopedMetrics.stream()
                        .filter(metric -> metric.getMetricValueNum() != null)
                        .map(metric -> new NumericMetricPoint(metric.getRecordedAt(), metric.getMetricValueNum()))
                        .toList(),
                    NumericMetricPoint::time,
                    point -> point.value().setScale(0, RoundingMode.HALF_UP).intValue(),
                    point -> false
                )
                : List.of();
            List<TopValueDto> selectedTopValues = topValuesForMetrics(scopedMetrics);

            return new StageMetricResponse(
                from,
                to,
                resolvedBucket,
                normalizedMetricType,
                metricTypeNames.getOrDefault(normalizedMetricType, normalizedMetricType),
                unit,
                numeric,
                List.of(),
                numericSeries,
                selectedTopValues
            );
        }

        List<AnalyticsEvent> events = filterEventsByRequestPath(
            eventRepository.findAllByRangeOrdered(from, to, normalizedEventType, normalizedModuleCode),
            normalizedRequestPath
        );
        events = filterEventsByMetric(events, filterMetricTypeCode, filterMetricValue, filterMetricMinValue, filterMetricMaxValue);
        events = filterEventsByAttribute(events, filterAttributeCode, filterAttributeValue, filterAttributeMinValue, filterAttributeMaxValue);
        if (events.isEmpty()) {
            return new StageMetricResponse(from, to, resolvedBucket, null, null, null, false, List.of(), List.of(), List.of());
        }

        List<Long> eventIds = ids(events);
        List<AnalyticsStage> stages = findStagesByEventIds(eventIds, normalizedStageType);
        if (stages.isEmpty()) {
            return new StageMetricResponse(from, to, resolvedBucket, null, null, null, false, List.of(), List.of(), List.of());
        }

        List<AnalyticsStageMetric> metrics = findStageMetricsByStageIds(
            ids(stages),
            includeSummaries ? null : normalizedMetricType
        );
        if (metrics.isEmpty()) {
            return new StageMetricResponse(from, to, resolvedBucket, null, null, null, false, List.of(), List.of(), List.of());
        }

        Map<String, String> metricTypeNames = stageMetricTypeNameMap();
        Map<String, String> metricTypeDescriptions = stageMetricTypeDescriptionMap();
        Map<String, String> metricTypeReadingGuides = stageMetricTypeReadingGuideMap();

        if (!includeSummaries) {
            String selectedCode = normalizedMetricType != null
                ? normalizedMetricType
                : metrics.stream()
                    .map(AnalyticsStageMetric::getMetricTypeCode)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (selectedCode == null) {
                return new StageMetricResponse(from, to, resolvedBucket, null, null, null, false, List.of(), List.of(), List.of());
            }

            List<AnalyticsStageMetric> selectedMetrics = metrics.stream()
                .filter(metric -> Objects.equals(metric.getMetricTypeCode(), selectedCode))
                .toList();
            if (selectedMetrics.isEmpty()) {
                return new StageMetricResponse(from, to, resolvedBucket, null, null, null, false, List.of(), List.of(), List.of());
            }

            List<BigDecimal> numericValues = selectedMetrics.stream()
                .map(AnalyticsStageMetric::getMetricValueNum)
                .filter(Objects::nonNull)
                .toList();
            boolean numeric = !numericValues.isEmpty();
            String unit = selectedMetrics.stream()
                .map(AnalyticsStageMetric::getUnit)
                .filter(unitValue -> unitValue != null && !unitValue.isBlank())
                .findFirst()
                .orElse(null);
            List<TimeSeriesPointDto> numericSeries = numeric
                ? buildSeries(
                    from,
                    to,
                    resolvedBucket,
                    selectedMetrics.stream()
                        .filter(metric -> metric.getMetricValueNum() != null)
                        .map(metric -> new NumericMetricPoint(metric.getRecordedAt(), metric.getMetricValueNum()))
                        .toList(),
                    NumericMetricPoint::time,
                    point -> point.value().setScale(0, RoundingMode.HALF_UP).intValue(),
                    point -> false
                )
                : List.of();

            List<TopValueDto> selectedTopValues = topValuesForMetrics(selectedMetrics);
            return new StageMetricResponse(
                from,
                to,
                resolvedBucket,
                selectedCode,
                metricTypeNames.getOrDefault(selectedCode, selectedCode),
                unit,
                numeric,
                List.of(),
                numericSeries,
                selectedTopValues
            );
        }

        Map<String, List<AnalyticsStageMetric>> byMetricType = metrics.stream()
            .collect(Collectors.groupingBy(AnalyticsStageMetric::getMetricTypeCode));

        List<StageMetricSummaryDto> summaries = byMetricType.entrySet().stream()
            .map(entry -> {
                String code = entry.getKey();
                List<AnalyticsStageMetric> values = entry.getValue();
                List<BigDecimal> numericValues = values.stream()
                    .map(AnalyticsStageMetric::getMetricValueNum)
                    .filter(Objects::nonNull)
                    .toList();
                boolean numeric = !numericValues.isEmpty();
                BigDecimal avg = numeric ? avgDecimal(numericValues) : BigDecimal.ZERO;
                BigDecimal p95 = numeric ? percentileBigDecimal(numericValues, 0.95) : BigDecimal.ZERO;
                BigDecimal min = numeric ? numericValues.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO) : BigDecimal.ZERO;
                BigDecimal max = numeric ? numericValues.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO) : BigDecimal.ZERO;
                List<TopValueDto> topValues = topValuesForMetrics(values);
                String unit = values.stream()
                    .map(AnalyticsStageMetric::getUnit)
                    .filter(unitValue -> unitValue != null && !unitValue.isBlank())
                    .findFirst()
                    .orElse(null);
                return new StageMetricSummaryDto(
                    code,
                    metricTypeNames.getOrDefault(code, code),
                    metricTypeDescriptions.getOrDefault(code, null),
                    metricTypeReadingGuides.getOrDefault(code, null),
                    unit,
                    numeric,
                    values.size(),
                    avg,
                    p95,
                    min,
                    max,
                    topValues
                );
            })
            .sorted(Comparator.comparing(StageMetricSummaryDto::sampleCount).reversed().thenComparing(StageMetricSummaryDto::metricTypeCode))
            .toList();

        if (summaries.isEmpty()) {
            return new StageMetricResponse(from, to, resolvedBucket, null, null, null, false, List.of(), List.of(), List.of());
        }

        StageMetricSummaryDto selected = summaries.stream()
            .filter(summary -> Objects.equals(summary.metricTypeCode(), normalizedMetricType))
            .findFirst()
            .orElseGet(() -> summaries.stream()
                .filter(StageMetricSummaryDto::numeric)
                .findFirst()
                .orElse(summaries.getFirst()));

        List<AnalyticsStageMetric> selectedMetrics = byMetricType.getOrDefault(selected.metricTypeCode(), List.of());
        List<TimeSeriesPointDto> numericSeries = selected.numeric()
            ? buildSeries(
                from,
                to,
                resolvedBucket,
                selectedMetrics.stream()
                    .filter(metric -> metric.getMetricValueNum() != null)
                    .map(metric -> new NumericMetricPoint(metric.getRecordedAt(), metric.getMetricValueNum()))
                    .toList(),
                NumericMetricPoint::time,
                point -> point.value().setScale(0, RoundingMode.HALF_UP).intValue(),
                point -> false
            )
            : List.of();

        List<TopValueDto> selectedTopValues = selected.topValues();

        return new StageMetricResponse(
            from,
            to,
            resolvedBucket,
            selected.metricTypeCode(),
            selected.metricTypeName(),
            selected.unit(),
            selected.numeric(),
            summaries,
            numericSeries,
            selectedTopValues
        );
    }

    public UniversalResponse universal(
        Instant from,
        Instant to,
        String moduleCode,
        List<String> eventTypeCode,
        String requestPath,
        String attributeCode,
        String attributeValue,
        String filterMetricTypeCode,
        String filterMetricValue,
        BigDecimal filterMetricMinValue,
        BigDecimal filterMetricMaxValue,
        String filterAttributeCode,
        String filterAttributeValue,
        BigDecimal filterAttributeMinValue,
        BigDecimal filterAttributeMaxValue,
        String stageTypeCode,
        Integer bucketMinutes
    ) {
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        Set<String> normalizedEventTypes = normalizeCodes(eventTypeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        String normalizedAttributeCode = normalizeCode(attributeCode);
        String normalizedAttributeValue = normalizeText(attributeValue);
        String normalizedStageType = normalizeCode(stageTypeCode);
        int resolvedBucket = resolveBucketMinutes(from, to, bucketMinutes);

        List<AnalyticsEvent> events = normalizedAttributeCode == null
            ? eventRepository.findAllByRangeOrdered(from, to, null, normalizedModuleCode)
            : eventRepository.findAllByRangeOrderedWithAttribute(
                from,
                to,
                null,
                normalizedModuleCode,
                normalizedAttributeCode,
                normalizedAttributeValue
            );
        if (!normalizedEventTypes.isEmpty()) {
            events = events.stream()
                .filter(event -> normalizedEventTypes.contains(normalizeCode(event.getEventTypeCode())))
                .toList();
        }
        events = filterEventsByRequestPath(events, normalizedRequestPath);
        events = filterEventsByMetric(events, filterMetricTypeCode, filterMetricValue, filterMetricMinValue, filterMetricMaxValue);
        events = filterEventsByAttribute(events, filterAttributeCode, filterAttributeValue, filterAttributeMinValue, filterAttributeMaxValue);
        if (events.isEmpty()) {
            return new UniversalResponse(from, to, resolvedBucket, snapshotFromEvents(List.of()), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        List<AnalyticsStage> stages = findStagesByEventIds(ids(events));
        if (normalizedStageType != null) {
            Set<Long> matchedEventIds = stages.stream()
                .filter(stage -> normalizedStageType.equals(stage.getStageTypeCode()))
                .map(AnalyticsStage::getEventId)
                .collect(Collectors.toSet());
            events = events.stream()
                .filter(event -> matchedEventIds.contains(event.getId()))
                .toList();
            stages = stages.stream()
                .filter(stage -> normalizedStageType.equals(stage.getStageTypeCode()))
                .toList();
        }

        if (events.isEmpty()) {
            return new UniversalResponse(from, to, resolvedBucket, snapshotFromEvents(List.of()), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        Map<String, String> stageTypeNames = stageTypeNameMap();
        List<StageKpiDto> stageRows = stages.stream()
            .collect(Collectors.groupingBy(AnalyticsStage::getStageTypeCode))
            .entrySet()
            .stream()
            .map(entry -> {
                KpiSnapshot stageStat = snapshotFromStages(entry.getValue());
                String code = entry.getKey();
                return new StageKpiDto(
                    code,
                    stageTypeNames.getOrDefault(code, code),
                    stageStat.count(),
                    stageStat.errorRate(),
                    stageStat.avgMs(),
                    stageStat.p95Ms(),
                    stageStat.p99Ms(),
                    stageStat.maxMs()
                );
            })
            .sorted(Comparator.comparing(StageKpiDto::count).reversed().thenComparing(StageKpiDto::stageTypeCode))
            .toList();

        KpiSnapshot totals = snapshotFromEvents(events);
        Map<String, String> eventTypeNames = eventTypeNameMap();
        List<EventKpiDto> eventBreakdown = events.stream()
            .collect(Collectors.groupingBy(AnalyticsEvent::getEventTypeCode))
            .entrySet()
            .stream()
            .map(entry -> {
                String code = entry.getKey();
                KpiSnapshot stat = snapshotFromEvents(entry.getValue());
                return new EventKpiDto(
                    code,
                    eventTypeNames.getOrDefault(code, code),
                    stat.count(),
                    stat.errorRate(),
                    stat.avgMs(),
                    stat.p95Ms(),
                    stat.p99Ms(),
                    stat.maxMs()
                );
            })
            .sorted(Comparator.comparing(EventKpiDto::count).reversed().thenComparing(EventKpiDto::eventTypeCode))
            .toList();
        List<TimeSeriesPointDto> series = buildSeries(
            from,
            to,
            resolvedBucket,
            events,
            AnalyticsEvent::getStartedAt,
            AnalyticsEvent::getDurationMs,
            event -> Boolean.TRUE.equals(event.getIsError())
        );
        List<UniversalEventSeriesDto> eventSeries = events.stream()
            .collect(Collectors.groupingBy(AnalyticsEvent::getEventTypeCode))
            .entrySet()
            .stream()
            .map(entry -> new UniversalEventSeriesDto(
                entry.getKey(),
                eventTypeNames.getOrDefault(entry.getKey(), entry.getKey()),
                buildSeries(
                    from,
                    to,
                    resolvedBucket,
                    entry.getValue(),
                    AnalyticsEvent::getStartedAt,
                    AnalyticsEvent::getDurationMs,
                    event -> Boolean.TRUE.equals(event.getIsError())
                )
            ))
            .sorted(Comparator.comparing(UniversalEventSeriesDto::eventTypeCode))
            .toList();

        Map<Long, String> eventTypeByEventId = events.stream()
            .collect(Collectors.toMap(AnalyticsEvent::getId, AnalyticsEvent::getEventTypeCode, (first, second) -> first));
        Map<String, List<AnalyticsStage>> stagesByEventType = stages.stream()
            .filter(stage -> stage.getEventId() != null && eventTypeByEventId.containsKey(stage.getEventId()))
            .collect(Collectors.groupingBy(stage -> eventTypeByEventId.get(stage.getEventId())));
        List<UniversalEventStageBreakdownDto> eventStageBreakdown = stagesByEventType.entrySet().stream()
            .map(entry -> {
                String eventCode = entry.getKey();
                List<StageKpiDto> stageKpis = entry.getValue().stream()
                    .collect(Collectors.groupingBy(AnalyticsStage::getStageTypeCode))
                    .entrySet()
                    .stream()
                    .map(stageEntry -> {
                        KpiSnapshot stat = snapshotFromStages(stageEntry.getValue());
                        String stageCode = stageEntry.getKey();
                        return new StageKpiDto(
                            stageCode,
                            stageTypeNames.getOrDefault(stageCode, stageCode),
                            stat.count(),
                            stat.errorRate(),
                            stat.avgMs(),
                            stat.p95Ms(),
                            stat.p99Ms(),
                            stat.maxMs()
                        );
                    })
                    .sorted(Comparator.comparing(StageKpiDto::count).reversed().thenComparing(StageKpiDto::stageTypeCode))
                    .toList();
                return new UniversalEventStageBreakdownDto(
                    eventCode,
                    eventTypeNames.getOrDefault(eventCode, eventCode),
                    stageKpis
                );
            })
            .sorted(Comparator.comparing(UniversalEventStageBreakdownDto::eventTypeCode))
            .toList();

        Map<String, String> attributeTypeNames = eventAttributeTypeNameMap();
        List<OptionDto> availableAttributeTypes = findAttributesByEventIds(ids(events)).stream()
            .map(AnalyticsEventAttribute::getAttributeTypeCode)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(code -> !code.isBlank())
            .distinct()
            .map(code -> new OptionDto(code, attributeTypeNames.getOrDefault(code, code)))
            .sorted(Comparator.comparing(OptionDto::name))
            .toList();

        return new UniversalResponse(
            from,
            to,
            resolvedBucket,
            totals,
            series,
            stageRows,
            eventBreakdown,
            eventSeries,
            eventStageBreakdown,
            availableAttributeTypes
        );
    }

    public EventListResponse events(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        Boolean isError,
        String errorClass,
        Integer minDurationMs,
        String requestPath,
        String attributeCode,
        String attributeValue,
        String metricTypeCode,
        BigDecimal metricMinValue,
        BigDecimal metricMaxValue,
        String sortBy,
        String sortDir,
        Integer page,
        Integer size
    ) {
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        String normalizedEventType = normalizeCode(eventTypeCode);
        String normalizedErrorClass = ErrorClassClassifier.normalizeFilterValue(errorClass);
        String normalizedAttributeCode = normalizeCode(attributeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        String normalizedAttributeValue = normalizeText(attributeValue);
        String normalizedMetricType = normalizeCode(metricTypeCode);
        String normalizedSortBy = normalizeText(sortBy);
        String normalizedSortDir = normalizeText(sortDir);

        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? DEFAULT_EVENT_PAGE_SIZE : Math.min(size, MAX_EVENT_PAGE_SIZE);

        Integer safeMinDuration = minDurationMs != null && minDurationMs > 0 ? minDurationMs : null;
        BigDecimal safeMetricMin = metricMinValue;
        BigDecimal safeMetricMax = metricMaxValue;
        List<AnalyticsEvent> candidates = eventRepository.findAllByRangeOrdered(from, to, normalizedEventType, normalizedModuleCode).stream()
            .filter(event -> isError == null || Objects.equals(Boolean.TRUE.equals(event.getIsError()), isError))
            .filter(event -> normalizedErrorClass == null || normalizedErrorClass.equals(ErrorClassClassifier.classifyFromEvent(event)))
            .filter(event -> safeMinDuration == null || (event.getDurationMs() != null && event.getDurationMs() >= safeMinDuration))
            .filter(event -> normalizedRequestPath == null || containsIgnoreCase(event.getRequestPath(), normalizedRequestPath))
            .toList();

        if (normalizedAttributeCode != null) {
            List<Long> candidateIds = ids(candidates);
            Map<Long, List<AnalyticsEventAttribute>> attributesByEventId = candidateIds.isEmpty()
                ? Map.of()
                : findAttributesByEventIds(candidateIds).stream()
                    .collect(Collectors.groupingBy(AnalyticsEventAttribute::getEventId));

            candidates = candidates.stream()
                .filter(event -> attributesByEventId.getOrDefault(event.getId(), List.of()).stream().anyMatch(attribute ->
                    normalizedAttributeCode.equals(attribute.getAttributeTypeCode())
                        && (normalizedAttributeValue == null
                        || containsIgnoreCase(eventAttributeValue(attribute), normalizedAttributeValue))
                ))
                .toList();
        }

        Map<Long, BigDecimal> metricSortValuesByEventId = Map.of();
        if (hasMetricFilter(normalizedMetricType, safeMetricMin, safeMetricMax)) {
            List<Long> candidateIds = ids(candidates);
            List<AnalyticsStage> candidateStages = findStagesByEventIds(candidateIds);
            Map<Long, Long> stageToEventId = candidateStages.stream()
                .collect(Collectors.toMap(
                    AnalyticsStage::getId,
                    AnalyticsStage::getEventId,
                    (first, second) -> first,
                    LinkedHashMap::new
                ));
            List<Long> stageIds = ids(candidateStages);
            List<AnalyticsStageMetric> candidateMetrics = findStageMetricsByStageIds(stageIds, normalizedMetricType);

            metricSortValuesByEventId = resolveMetricValuesByEventId(
                candidateMetrics,
                stageToEventId,
                safeMetricMin,
                safeMetricMax
            );
            Map<Long, BigDecimal> metricFilterMap = metricSortValuesByEventId;
            candidates = candidates.stream()
                .filter(event -> metricFilterMap.containsKey(event.getId()))
                .toList();
        }

        Comparator<AnalyticsEvent> comparator = eventComparator(normalizedSortBy, normalizedSortDir, metricSortValuesByEventId);
        candidates = candidates.stream()
            .sorted(comparator)
            .toList();

        long totalElements = candidates.size();
        int fromIndex = Math.max(0, safePage * safeSize);
        int toIndex = Math.min(candidates.size(), fromIndex + safeSize);
        List<AnalyticsEvent> events = fromIndex >= toIndex ? List.of() : candidates.subList(fromIndex, toIndex);
        List<Long> eventIds = ids(events);
        List<AnalyticsStage> stages = findStagesByEventIds(eventIds);
        List<AnalyticsEventAttribute> attributes = findAttributesByEventIds(eventIds);

        Map<String, String> eventTypeNames = eventTypeNameMap();
        Map<String, String> moduleNames = moduleTypeNameMap();
        Map<String, String> stageTypeNames = stageTypeNameMap();
        Map<Long, List<EventStageBriefDto>> stageMap = stages.stream()
            .collect(Collectors.groupingBy(
                AnalyticsStage::getEventId,
                Collectors.mapping(stage -> new EventStageBriefDto(
                    stage.getStageTypeCode(),
                    stageTypeNames.getOrDefault(stage.getStageTypeCode(), stage.getStageTypeCode()),
                    stage.getDurationMs(),
                    stage.getIsError()
                ), Collectors.toList())
            ));
        Map<Long, Map<String, String>> attributeMap = attributes.stream()
            .collect(Collectors.groupingBy(
                AnalyticsEventAttribute::getEventId,
                Collectors.toMap(
                    AnalyticsEventAttribute::getAttributeTypeCode,
                    this::eventAttributeValue,
                    (first, second) -> first,
                    LinkedHashMap::new
                )
            ));

        List<EventListItemDto> items = events.stream()
            .map(event -> new EventListItemDto(
                event.getId(),
                event.getEventUid(),
                event.getStartedAt(),
                event.getModuleCode(),
                moduleNames.getOrDefault(event.getModuleCode(), event.getModuleCode()),
                event.getEventTypeCode(),
                eventTypeNames.getOrDefault(event.getEventTypeCode(), event.getEventTypeCode()),
                event.getDurationMs(),
                event.getStatusCode(),
                event.getIsError(),
                ErrorClassClassifier.classifyFromEvent(event),
                event.getRequestPath(),
                event.getTraceId(),
                event.getErrorMessage(),
                attributeMap.getOrDefault(event.getId(), Map.of()),
                stageMap.getOrDefault(event.getId(), List.of())
            ))
            .toList();

        boolean hasMore = (long) (safePage + 1) * safeSize < totalElements;
        return new EventListResponse(totalElements, safePage, safeSize, hasMore, items);
    }

    private boolean hasMetricFilter(String metricTypeCode, BigDecimal metricMinValue, BigDecimal metricMaxValue) {
        return metricTypeCode != null || metricMinValue != null || metricMaxValue != null;
    }

    private List<AnalyticsEvent> filterEventsByMetric(
        List<AnalyticsEvent> events,
        String metricTypeCode,
        String metricValue,
        BigDecimal metricMinValue,
        BigDecimal metricMaxValue
    ) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        String normalizedMetricType = normalizeCode(metricTypeCode);
        String normalizedMetricValue = normalizeText(metricValue);
        if (!hasMetricFilter(normalizedMetricType, metricMinValue, metricMaxValue) && normalizedMetricValue == null) {
            return events;
        }

        List<Long> eventIds = ids(events);
        if (eventIds.isEmpty()) {
            return events;
        }
        List<AnalyticsStage> stages = findStagesByEventIds(eventIds);
        if (stages.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> stageToEventId = stages.stream()
            .filter(stage -> stage.getId() != null && stage.getEventId() != null)
            .collect(Collectors.toMap(AnalyticsStage::getId, AnalyticsStage::getEventId, (first, second) -> first, LinkedHashMap::new));
        if (stageToEventId.isEmpty()) {
            return List.of();
        }

        List<AnalyticsStageMetric> metrics = findStageMetricsByStageIds(new ArrayList<>(stageToEventId.keySet()), normalizedMetricType);
        if (metrics.isEmpty()) {
            return List.of();
        }

        Set<Long> matchedEventIds = metrics.stream()
            .filter(metric -> metricMatchesFilter(metric, normalizedMetricValue, metricMinValue, metricMaxValue))
            .map(metric -> stageToEventId.get(metric.getStageId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (matchedEventIds.isEmpty()) {
            return List.of();
        }
        return events.stream()
            .filter(event -> matchedEventIds.contains(event.getId()))
            .toList();
    }

    private boolean metricMatchesFilter(
        AnalyticsStageMetric metric,
        String normalizedMetricValue,
        BigDecimal metricMinValue,
        BigDecimal metricMaxValue
    ) {
        if (metric == null) {
            return false;
        }
        BigDecimal numericValue = metric.getMetricValueNum();
        if (metricMinValue != null) {
            if (numericValue == null || numericValue.compareTo(metricMinValue) < 0) {
                return false;
            }
        }
        if (metricMaxValue != null) {
            if (numericValue == null || numericValue.compareTo(metricMaxValue) > 0) {
                return false;
            }
        }
        if (normalizedMetricValue == null) {
            return true;
        }
        String textValue = normalizeText(metric.getMetricValueText());
        if (textValue != null && textValue.contains(normalizedMetricValue)) {
            return true;
        }
        if (numericValue != null) {
            String numericText = normalizeText(numericValue.stripTrailingZeros().toPlainString());
            return numericText != null && numericText.contains(normalizedMetricValue);
        }
        return false;
    }

    private List<AnalyticsEvent> filterEventsByAttribute(
        List<AnalyticsEvent> events,
        String attributeCode,
        String attributeValue,
        BigDecimal attributeMinValue,
        BigDecimal attributeMaxValue
    ) {
        String normalizedCode = normalizeCode(attributeCode);
        String normalizedValue = normalizeText(attributeValue);
        if (normalizedCode == null && normalizedValue == null && attributeMinValue == null && attributeMaxValue == null) {
            return events;
        }
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<Long> eventIds = ids(events);
        if (eventIds.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AnalyticsEventAttribute>> attributesByEventId = findAttributesByEventIds(eventIds).stream()
            .collect(Collectors.groupingBy(AnalyticsEventAttribute::getEventId));

        return events.stream()
            .filter(event -> {
                List<AnalyticsEventAttribute> attrs = attributesByEventId.getOrDefault(event.getId(), List.of());
                return attrs.stream().anyMatch(attr -> attributeMatchesFilter(attr, normalizedCode, normalizedValue, attributeMinValue, attributeMaxValue));
            })
            .toList();
    }

    private boolean attributeMatchesFilter(
        AnalyticsEventAttribute attribute,
        String normalizedCode,
        String normalizedValue,
        BigDecimal attributeMinValue,
        BigDecimal attributeMaxValue
    ) {
        if (attribute == null) {
            return false;
        }
        if (normalizedCode != null && !normalizedCode.equals(normalizeCode(attribute.getAttributeTypeCode()))) {
            return false;
        }
        String valueText = normalizeText(eventAttributeValue(attribute));
        if (normalizedValue != null && (valueText == null || !valueText.contains(normalizedValue))) {
            return false;
        }
        if (attributeMinValue == null && attributeMaxValue == null) {
            return true;
        }
        if (valueText == null) {
            return false;
        }
        BigDecimal numeric;
        try {
            numeric = new BigDecimal(valueText.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return false;
        }
        if (attributeMinValue != null && numeric.compareTo(attributeMinValue) < 0) {
            return false;
        }
        if (attributeMaxValue != null && numeric.compareTo(attributeMaxValue) > 0) {
            return false;
        }
        return true;
    }

    private Map<Long, BigDecimal> resolveMetricValuesByEventId(
        List<AnalyticsStageMetric> metrics,
        Map<Long, Long> stageToEventId,
        BigDecimal metricMinValue,
        BigDecimal metricMaxValue
    ) {
        if (metrics == null || metrics.isEmpty() || stageToEventId == null || stageToEventId.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> valuesByEventId = new LinkedHashMap<>();
        for (AnalyticsStageMetric metric : metrics) {
            BigDecimal value = metric.getMetricValueNum();
            if (value == null) {
                continue;
            }
            if (metricMinValue != null && value.compareTo(metricMinValue) < 0) {
                continue;
            }
            if (metricMaxValue != null && value.compareTo(metricMaxValue) > 0) {
                continue;
            }
            Long eventId = stageToEventId.get(metric.getStageId());
            if (eventId == null) {
                continue;
            }
            valuesByEventId.merge(eventId, value, (first, second) -> second.compareTo(first) > 0 ? second : first);
        }
        return valuesByEventId;
    }

    private Comparator<AnalyticsEvent> eventComparator(
        String sortBy,
        String sortDir,
        Map<Long, BigDecimal> metricSortValuesByEventId
    ) {
        String resolvedSortBy = sortBy == null ? "startedAt" : sortBy.trim().toLowerCase(Locale.ROOT);
        boolean ascending = "asc".equalsIgnoreCase(sortDir);

        Comparator<AnalyticsEvent> comparator = switch (resolvedSortBy) {
            case "duration", "durationms" -> Comparator.comparing(
                AnalyticsEvent::getDurationMs,
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "status", "statuscode" -> Comparator.comparing(
                AnalyticsEvent::getStatusCode,
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "event", "eventtype", "eventtypecode" -> Comparator.comparing(
                event -> normalizeSortValue(event.getEventTypeCode()),
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "trace", "traceid" -> Comparator.comparing(
                event -> normalizeSortValue(event.getTraceId()),
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "path", "requestpath" -> Comparator.comparing(
                event -> normalizeSortValue(event.getRequestPath()),
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "error", "iserror" -> Comparator.comparing(
                event -> Boolean.TRUE.equals(event.getIsError())
            );
            case "metric", "metricvalue" -> Comparator.comparing(
                event -> metricSortValuesByEventId.get(event.getId()),
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            default -> Comparator.comparing(
                AnalyticsEvent::getStartedAt,
                Comparator.nullsLast(Comparator.naturalOrder())
            );
        };

        if (!ascending) {
            comparator = comparator.reversed();
        }

        Comparator<AnalyticsEvent> tieBreaker = Comparator
            .comparing(AnalyticsEvent::getStartedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(AnalyticsEvent::getId, Comparator.nullsLast(Comparator.reverseOrder()));

        return comparator.thenComparing(tieBreaker);
    }

    private String normalizeSortValue(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    public EventDetailsResponse eventDetails(UUID eventUid) {
        AnalyticsEvent event = eventRepository.findByEventUid(eventUid)
            .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventUid));
        return eventDetails(event);
    }

    public EventDetailsResponse eventDetailsById(Long eventId) {
        AnalyticsEvent event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));
        return eventDetails(event);
    }

    private EventDetailsResponse eventDetails(AnalyticsEvent event) {
        List<AnalyticsStage> stages = stageRepository.findByEventIdOrderByStageOrder(event.getId());
        List<AnalyticsStageMetric> metrics = stages.isEmpty()
            ? List.of()
            : findStageMetricsByStageIds(ids(stages), null);
        List<AnalyticsEventAttribute> attributes = eventAttributeRepository.findByEventId(event.getId());

        Map<String, String> eventTypeNames = eventTypeNameMap();
        Map<String, String> moduleNames = moduleTypeNameMap();
        Map<String, String> stageTypeNames = stageTypeNameMap();
        Map<String, String> metricTypeNames = stageMetricTypeNameMap();
        Map<String, String> attributeTypeNames = eventAttributeTypeNameMap();
        List<EventLogEntryDto> traceLogs = analyticsLogViewService.loadTraceLogs(
            event.getTraceId(),
            event.getEventUid() == null ? null : event.getEventUid().toString(),
            event.getModuleCode(),
            event.getStartedAt(),
            event.getEndedAt()
        );

        Map<Long, List<EventStageMetricDto>> metricsByStage = metrics.stream()
            .collect(Collectors.groupingBy(
                AnalyticsStageMetric::getStageId,
                Collectors.mapping(metric -> new EventStageMetricDto(
                    metric.getMetricTypeCode(),
                    metricTypeNames.getOrDefault(metric.getMetricTypeCode(), metric.getMetricTypeCode()),
                    metric.getMetricValueNum(),
                    metric.getMetricValueText(),
                    metric.getUnit()
                ), Collectors.toList())
            ));

        List<EventStageDetailsDto> stageDtos = stages.stream()
            .map(stage -> new EventStageDetailsDto(
                stage.getStageTypeCode(),
                stageTypeNames.getOrDefault(stage.getStageTypeCode(), stage.getStageTypeCode()),
                stage.getStageOrder(),
                stage.getStartedAt(),
                stage.getEndedAt(),
                stage.getLogStartedAt(),
                stage.getLogEndedAt(),
                stage.getDurationMs(),
                stage.getIsError(),
                stage.getErrorMessage(),
                metricsByStage.getOrDefault(stage.getId(), List.of()),
                filterLogsForStage(
                    traceLogs,
                    stage.getStageTypeCode(),
                    stage.getLogStartedAt() != null ? stage.getLogStartedAt() : stage.getStartedAt(),
                    stage.getLogEndedAt() != null ? stage.getLogEndedAt() : stage.getEndedAt()
                )
            ))
            .toList();

        List<EventAttributeDto> attributeDtos = attributes.stream()
            .map(attribute -> new EventAttributeDto(
                attribute.getAttributeTypeCode(),
                attributeTypeNames.getOrDefault(attribute.getAttributeTypeCode(), attribute.getAttributeTypeCode()),
                attribute.getAttrValue(),
                attribute.getAttrValueJson()
            ))
            .toList();

        return new EventDetailsResponse(
            event.getEventUid(),
            event.getStartedAt(),
            event.getEndedAt(),
            event.getDurationMs(),
            event.getModuleCode(),
            moduleNames.getOrDefault(event.getModuleCode(), event.getModuleCode()),
            event.getEventTypeCode(),
            eventTypeNames.getOrDefault(event.getEventTypeCode(), event.getEventTypeCode()),
            event.getStatusCode(),
            event.getIsError(),
            ErrorClassClassifier.classifyFromEvent(event),
            event.getRequestPath(),
            event.getHttpMethod(),
            event.getTraceId(),
            event.getErrorMessage(),
            attributeDtos,
            stageDtos,
            traceLogs
        );
    }

    private List<EventLogEntryDto> filterLogsForStage(
        List<EventLogEntryDto> logs,
        String stageTypeCode,
        Instant from,
        Instant to
    ) {
        if (logs == null || logs.isEmpty() || from == null || to == null) {
            return List.of();
        }
        // Stage boundaries are stored with sub-millisecond precision,
        // while parsed log timestamps are millisecond precision.
        // Expand the window slightly to avoid dropping edge records
        // that fall into the same millisecond bucket.
        Instant fromInclusive = from.minusMillis(1);
        Instant toInclusive = to.plusMillis(1);
        String normalizedStageType = normalizeCode(stageTypeCode);
        return logs.stream()
            .filter(log -> log.timestamp() != null)
            .filter(log -> !log.timestamp().isBefore(fromInclusive) && !log.timestamp().isAfter(toInclusive))
            .filter(log -> logMatchesStage(log, normalizedStageType))
            .toList();
    }

    private boolean logMatchesStage(EventLogEntryDto log, String normalizedStageType) {
        if (normalizedStageType == null || normalizedStageType.isBlank()) {
            return true;
        }
        String logLayer = normalizeCode(log.layer());
        if (Objects.equals(logLayer, normalizedStageType)) {
            return true;
        }
        if ("CONTROLLER".equals(normalizedStageType) && "HTTP".equals(logLayer)) {
            return true;
        }
        if (logLayer != null && !"UNKNOWN".equals(logLayer)) {
            return false;
        }
        String source = log.source() == null ? "" : log.source().toLowerCase(Locale.ROOT);
        return switch (normalizedStageType) {
            case "CONTROLLER" -> source.contains("controller");
            case "SERVICE" -> source.contains("service");
            case "DATABASE" -> source.contains("repository")
                || source.contains("jpaspecificationexecutor")
                || source.contains("listcrudrepository");
            case "FRONTEND" -> source.contains("frontend");
            default -> true;
        };
    }

    public CompareResponse compare(
        Instant baselineFrom,
        Instant baselineTo,
        Instant targetFrom,
        Instant targetTo,
        String moduleCode,
        String eventTypeCode,
        String requestPath
    ) {
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        String normalizedEventType = normalizeCode(eventTypeCode);
        String normalizedRequestPath = normalizeText(requestPath);

        List<AnalyticsEvent> baselineEvents = filterEventsByRequestPath(
            eventRepository.findAllByRangeOrdered(
                baselineFrom,
                baselineTo,
                normalizedEventType,
                normalizedModuleCode
            ),
            normalizedRequestPath
        );
        List<AnalyticsEvent> targetEvents = filterEventsByRequestPath(
            eventRepository.findAllByRangeOrdered(
                targetFrom,
                targetTo,
                normalizedEventType,
                normalizedModuleCode
            ),
            normalizedRequestPath
        );

        KpiSnapshot baseline = snapshotFromEvents(baselineEvents);
        KpiSnapshot target = snapshotFromEvents(targetEvents);
        KpiDelta delta = new KpiDelta(
            percentChange(baseline.count(), target.count()),
            percentChange(baseline.avgMs(), target.avgMs()),
            percentChange(baseline.p95Ms(), target.p95Ms()),
            percentChange(baseline.errorRate(), target.errorRate())
        );

        return new CompareResponse(baselineFrom, baselineTo, baseline, targetFrom, targetTo, target, delta);
    }

    private KpiSnapshot snapshotFromEvents(List<AnalyticsEvent> events) {
        long total = events.size();
        long errors = events.stream().filter(event -> Boolean.TRUE.equals(event.getIsError())).count();
        List<Integer> durations = events.stream()
            .map(AnalyticsEvent::getDurationMs)
            .filter(duration -> duration != null && duration >= 0)
            .sorted()
            .toList();
        return snapshot(total, errors, durations);
    }

    private KpiSnapshot snapshotFromStages(List<AnalyticsStage> stages) {
        long total = stages.size();
        long errors = stages.stream().filter(stage -> Boolean.TRUE.equals(stage.getIsError())).count();
        List<Integer> durations = stages.stream()
            .map(AnalyticsStage::getDurationMs)
            .filter(duration -> duration != null && duration >= 0)
            .sorted()
            .toList();
        return snapshot(total, errors, durations);
    }

    private KpiSnapshot snapshot(long total, long errors, List<Integer> sortedDurations) {
        BigDecimal avg = sortedDurations.isEmpty()
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(sortedDurations.stream().mapToInt(Integer::intValue).average().orElse(0.0)).setScale(3, RoundingMode.HALF_UP);
        BigDecimal p95 = percentileInt(sortedDurations, 0.95);
        BigDecimal p99 = percentileInt(sortedDurations, 0.99);
        BigDecimal max = sortedDurations.isEmpty()
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(sortedDurations.get(sortedDurations.size() - 1)).setScale(3, RoundingMode.HALF_UP);
        BigDecimal errorRate = ratio(errors, total);
        return new KpiSnapshot(total, errors, Math.max(0, total - errors), errorRate, avg, p95, p99, max);
    }

    private <T> List<TimeSeriesPointDto> buildSeries(
        Instant from,
        Instant to,
        int bucketMinutes,
        List<T> values,
        Function<T, Instant> timeExtractor,
        Function<T, Integer> durationExtractor,
        Function<T, Boolean> errorExtractor
    ) {
        long stepSeconds = bucketMinutes * 60L;
        Instant start = floorToBucket(from, stepSeconds);
        Instant end = floorToBucket(to.minusSeconds(1), stepSeconds).plusSeconds(stepSeconds);

        Map<Instant, List<T>> grouped = values.stream()
            .filter(value -> timeExtractor.apply(value) != null)
            .collect(Collectors.groupingBy(value -> floorToBucket(timeExtractor.apply(value), stepSeconds)));

        List<TimeSeriesPointDto> result = new ArrayList<>();
        for (Instant bucket = start; bucket.isBefore(end); bucket = bucket.plusSeconds(stepSeconds)) {
            List<T> bucketValues = grouped.getOrDefault(bucket, List.of());
            long count = bucketValues.size();
            long errors = bucketValues.stream().filter(value -> Boolean.TRUE.equals(errorExtractor.apply(value))).count();
            List<Integer> sortedDurations = bucketValues.stream()
                .map(durationExtractor)
                .filter(duration -> duration != null && duration >= 0)
                .sorted()
                .toList();

            BigDecimal avg = sortedDurations.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(sortedDurations.stream().mapToInt(Integer::intValue).average().orElse(0.0)).setScale(3, RoundingMode.HALF_UP);
            BigDecimal p95 = percentileInt(sortedDurations, 0.95);
            BigDecimal p99 = percentileInt(sortedDurations, 0.99);
            BigDecimal errorRate = ratio(errors, count);
            result.add(new TimeSeriesPointDto(bucket, count, avg, p95, p99, errorRate));
        }
        return result;
    }

    private BigDecimal percentileInt(List<Integer> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return BigDecimal.valueOf(sortedValues.get(index)).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal percentileBigDecimal(List<BigDecimal> values, double percentile) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal avgDecimal(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 3, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal percentChange(long baseline, long target) {
        if (baseline <= 0L) {
            return target <= 0L ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return BigDecimal.valueOf(target - baseline)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(baseline), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentChange(BigDecimal baseline, BigDecimal target) {
        BigDecimal safeBaseline = baseline == null ? BigDecimal.ZERO : baseline;
        BigDecimal safeTarget = target == null ? BigDecimal.ZERO : target;
        if (safeBaseline.compareTo(BigDecimal.ZERO) == 0) {
            return safeTarget.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return safeTarget.subtract(safeBaseline)
            .multiply(BigDecimal.valueOf(100))
            .divide(safeBaseline, 2, RoundingMode.HALF_UP);
    }

    private Instant floorToBucket(Instant value, long stepSeconds) {
        long epochSecond = value.getEpochSecond();
        long normalized = Math.floorDiv(epochSecond, stepSeconds) * stepSeconds;
        return Instant.ofEpochSecond(normalized);
    }

    private int resolveBucketMinutes(Instant from, Instant to, Integer requested) {
        if (requested != null && requested > 0) {
            return requested;
        }
        long diffMinutes = Math.max(1L, (to.getEpochSecond() - from.getEpochSecond()) / 60L);
        if (diffMinutes <= 360) {
            return 5;
        }
        if (diffMinutes <= 1_440) {
            return 15;
        }
        if (diffMinutes <= 4_320) {
            return 60;
        }
        if (diffMinutes <= 10_080) {
            return 180;
        }
        if (diffMinutes <= 44_640) {
            return 360;
        }
        return 1_440;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String value = code.trim();
        return value.isBlank() ? null : value.toUpperCase(Locale.ROOT);
    }

    private Set<String> normalizeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        return codes.stream()
            .map(this::normalizeCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private String normalizeModuleFilterCode(String moduleCode) {
        String normalized = normalizeCode(moduleCode);
        if (normalized == null) {
            return null;
        }
        return EventType.DEFAULT_MODULE_CODE.equals(normalized) ? null : normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean containsIgnoreCase(String source, String target) {
        if (target == null) {
            return true;
        }
        if (source == null || source.isBlank()) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
    }

    private List<AnalyticsEvent> filterEventsByRequestPath(List<AnalyticsEvent> events, String normalizedRequestPath) {
        if (normalizedRequestPath == null) {
            return events;
        }
        return events.stream()
            .filter(event -> containsIgnoreCase(event.getRequestPath(), normalizedRequestPath))
            .toList();
    }

    private List<Long> ids(Collection<? extends AnalyticsEvent> events) {
        return events.stream()
            .map(AnalyticsEvent::getId)
            .filter(Objects::nonNull)
            .toList();
    }

    private List<Long> ids(List<AnalyticsStage> stages) {
        return stages.stream()
            .map(AnalyticsStage::getId)
            .filter(Objects::nonNull)
            .toList();
    }

    private String eventAttributeValue(AnalyticsEventAttribute attribute) {
        if (attribute.getAttrValue() != null && !attribute.getAttrValue().isBlank()) {
            return attribute.getAttrValue();
        }
        if (attribute.getAttrValueJson() != null && !attribute.getAttrValueJson().isBlank()) {
            return attribute.getAttrValueJson();
        }
        return "";
    }

    private Map<String, String> eventTypeNameMap() {
        return eventTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .collect(Collectors.toMap(type -> type.getCode(), type -> type.getName(), (a, b) -> a, LinkedHashMap::new));
    }

    private Set<String> hiddenSystemEventTypeCodes() {
        return eventTypeRepository.findByIsSystemTrueOrderByCodeAsc().stream()
            .map(EventType::getCode)
            .map(this::normalizeCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private Map<String, String> moduleTypeNameMap() {
        return moduleTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .collect(Collectors.toMap(type -> type.getCode(), type -> type.getName(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, String> stageTypeNameMap() {
        return stageTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .collect(Collectors.toMap(type -> type.getCode(), type -> type.getName(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, String> stageMetricTypeNameMap() {
        return stageMetricTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .collect(Collectors.toMap(
                type -> type.getCode(),
                type -> localizeStageMetricTypeName(type.getCode(), type.getName()),
                (a, b) -> a,
                LinkedHashMap::new
            ));
    }

    private Map<String, String> stageMetricTypeDescriptionMap() {
        return stageMetricTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .collect(Collectors.toMap(
                type -> type.getCode(),
                type -> type.getDescription(),
                (a, b) -> a,
                LinkedHashMap::new
            ));
    }

    private Map<String, String> stageMetricTypeReadingGuideMap() {
        return stageMetricTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .collect(Collectors.toMap(
                type -> type.getCode(),
                type -> type.getReadingGuide(),
                (a, b) -> a,
                LinkedHashMap::new
            ));
    }

    private Map<String, String> eventAttributeTypeNameMap() {
        return eventAttributeTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .collect(Collectors.toMap(type -> type.getCode(), type -> type.getName(), (a, b) -> a, LinkedHashMap::new));
    }

    private List<TopValueDto> topValuesForMetrics(List<AnalyticsStageMetric> values) {
        List<TopValueDto> topValuesText = values.stream()
            .map(AnalyticsStageMetric::getMetricValueText)
            .filter(text -> text != null && !text.isBlank())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed().thenComparing(Map.Entry::getKey))
            .limit(8)
            .map(entryItem -> new TopValueDto(entryItem.getKey(), entryItem.getValue()))
            .toList();
        if (!topValuesText.isEmpty()) {
            return topValuesText;
        }
        return values.stream()
            .map(AnalyticsStageMetric::getMetricValueNum)
            .filter(Objects::nonNull)
            .map(num -> num.stripTrailingZeros().toPlainString())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed().thenComparing(Map.Entry::getKey))
            .limit(8)
            .map(entryItem -> new TopValueDto(entryItem.getKey(), entryItem.getValue()))
            .toList();
    }

    private record NumericMetricPoint(Instant time, BigDecimal value) {
    }

    private String localizeStageMetricTypeName(String code, String fallbackName) {
        if (code == null || code.isBlank()) {
            return fallbackName;
        }
        return switch (code) {
            case "DB_QUERY_COUNT" -> "SQL-запросы";
            case "RESPONSE_SIZE_BYTES" -> "Полный размер ответа (байт)";
            case "RETRY_COUNT" -> "Повторные попытки";
            case "ERROR_CODE" -> "Код ошибки";
            case "ERROR_CLASS" -> "Класс ошибки";
            case "ITEM_COUNT" -> "Количество элементов";
            case "PAYLOAD_SIZE_BYTES" -> "Размер полезных данных ответа (байт)";
            case "VALIDATION_ERROR_COUNT" -> "Ошибки валидации";
            default -> fallbackName;
        };
    }
    private List<AnalyticsStage> findStagesByEventIds(List<Long> eventIds) {
        return findStagesByEventIds(eventIds, null);
    }

    private List<AnalyticsStage> findStagesByEventIds(List<Long> eventIds, String stageTypeCode) {
        List<AnalyticsStage> stages = loadInBatches(eventIds, batchIds -> {
            if (stageTypeCode == null || stageTypeCode.isBlank()) {
                return stageRepository.findByEventIdInOrderByEventIdAscStageOrderAsc(batchIds);
            }
            return stageRepository.findByEventIdInAndStageTypeCodeOrderByEventIdAscStageOrderAsc(batchIds, stageTypeCode);
        });

        if (stages == null || stages.size() < 2) {
            return stages == null ? List.of() : stages;
        }

        List<AnalyticsStage> sorted = new ArrayList<>(stages);
        sorted.sort(
            Comparator
                .comparing(AnalyticsStage::getEventId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AnalyticsStage::getStageOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AnalyticsStage::getId, Comparator.nullsLast(Comparator.naturalOrder()))
        );
        return sorted;
    }

    private List<AnalyticsStageMetric> findStageMetricsByStageIds(List<Long> stageIds, String metricTypeCode) {
        return loadInBatches(stageIds, batchIds -> {
            if (metricTypeCode == null || metricTypeCode.isBlank()) {
                return stageMetricRepository.findByStageIdIn(batchIds);
            }
            return stageMetricRepository.findByStageIdInAndMetricTypeCode(batchIds, metricTypeCode);
        });
    }

    private List<AnalyticsEventAttribute> findAttributesByEventIds(List<Long> eventIds) {
        return loadInBatches(eventIds, eventAttributeRepository::findByEventIdIn);
    }

    private <ID, ENTITY> List<ENTITY> loadInBatches(
            List<ID> ids,
            java.util.function.Function<List<ID>, List<ENTITY>> loader
    ) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<ENTITY> result = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += IN_CLAUSE_BATCH_SIZE) {
            int to = Math.min(from + IN_CLAUSE_BATCH_SIZE, ids.size());
            List<ID> batchIds = ids.subList(from, to);
            List<ENTITY> batch = loader.apply(batchIds);
            if (batch != null && !batch.isEmpty()) {
                result.addAll(batch);
            }
        }
        return result;
    }
}

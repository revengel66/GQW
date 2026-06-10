package com.example.gqw.analytics.service;
 
import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import com.example.gqw.analytics.entity.AnalyticsStage;
import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.entity.MetricValueKind;
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
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.CompareEventRow;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventDurationBreakdownDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventDetailsResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventLogEntryDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventStageIntervalDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventKpiDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventListItemDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventListResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventStageBriefDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventStageDetailsDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventStageMetricDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventUnaccountedIntervalDto;
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
import java.time.Duration;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
public class AnalyticsInsightsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsInsightsService.class);
    private static final int DEFAULT_EVENT_PAGE_SIZE = 50;
    private static final int MAX_EVENT_PAGE_SIZE = 100;
    private static final int IN_CLAUSE_BATCH_SIZE = 2000;
    private static final int FILTER_OPTION_LIMIT = 200;
    private static final Duration MAX_RAW_READ_RANGE = Duration.ofHours(24);
    private static final String RAW_READ_WARNING =
        "Отображается ограниченная выборка: агрегаты для выбранного среза недоступны, raw-данные ограничены последними 24 часами выбранного периода.";

    private final AnalyticsEventRepository eventRepository;
    private final AnalyticsStageRepository stageRepository;
    private final AnalyticsStageMetricRepository stageMetricRepository;
    private final AnalyticsEventAttributeRepository eventAttributeRepository;
    private final EventTypeRepository eventTypeRepository;
    private final ModuleTypeRepository moduleTypeRepository;
    private final StageTypeRepository stageTypeRepository;
    private final StageMetricTypeRepository stageMetricTypeRepository;
    private final EventAttributeTypeRepository eventAttributeTypeRepository;
    private final AnalyticsTraceLogLookupService traceLogLookupService;
    private final AnalyticsFilterRollupService filterRollupService;
    private final AnalyticsTimeRollupService timeRollupService;
    private final AnalyticsStageMetricRollupService stageMetricRollupService;

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
        AnalyticsTraceLogLookupService traceLogLookupService,
        AnalyticsFilterRollupService filterRollupService,
        AnalyticsTimeRollupService timeRollupService,
        AnalyticsStageMetricRollupService stageMetricRollupService
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
        this.traceLogLookupService = traceLogLookupService;
        this.filterRollupService = filterRollupService;
        this.timeRollupService = timeRollupService;
        this.stageMetricRollupService = stageMetricRollupService;
    }

    public DictionariesResponse dictionaries(String moduleCode) {
        return dictionaries(moduleCode, false);
    }

    public DictionariesResponse dictionaries(String moduleCode, boolean systemEventsOnly) {
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        List<OptionDto> modules = moduleTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .map(type -> new OptionDto(type.getCode(), type.getName()))
            .toList();
        List<EventType> eventTypeSource = systemEventsOnly
            ? eventTypeRepository.findByIsSystemTrueOrderByCodeAsc()
            : (normalizedModuleCode == null
                ? eventTypeRepository.findByIsActiveTrueAndIsSystemFalseOrderByNameAsc()
                : eventTypeRepository.findByIsActiveTrueAndModuleCodeAndIsSystemFalseOrderByNameAsc(normalizedModuleCode));
        List<OptionDto> eventTypes = eventTypeSource.stream()
            .filter(type -> normalizedModuleCode == null || normalizedModuleCode.equalsIgnoreCase(type.getModuleCode()))
            .filter(type -> systemEventsOnly || Boolean.TRUE.equals(type.getIsActive()))
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

    private ReadWindow boundedRawReadWindow(Instant from, Instant to, String endpoint) {
        if (from == null || to == null || !from.isBefore(to)) {
            return new ReadWindow(from, to, false, null);
        }
        Duration requested = Duration.between(from, to);
        if (requested.compareTo(MAX_RAW_READ_RANGE) <= 0) {
            return new ReadWindow(from, to, false, null);
        }
        Instant effectiveFrom = to.minus(MAX_RAW_READ_RANGE);
        log.warn(
            "Analytics Admin raw read bounded endpoint={} requestedFrom={} requestedTo={} effectiveFrom={} effectiveTo={} maxRangeHours={}",
            endpoint,
            from,
            to,
            effectiveFrom,
            to,
            MAX_RAW_READ_RANGE.toHours()
        );
        return new ReadWindow(effectiveFrom, to, true, RAW_READ_WARNING);
    }

    public FilterOptionsResponse filterOptions(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String requestPath,
        String attributeCode
    ) {
        return filterOptions(from, to, moduleCode, eventTypeCode, requestPath, attributeCode, false);
    }

    public FilterOptionsResponse filterOptions(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String requestPath,
        String attributeCode,
        boolean systemEventsOnly
    ) {
        long serviceStarted = System.nanoTime();
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        String normalizedEventType = normalizeCode(eventTypeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        String normalizedAttributeCode = normalizeCode(attributeCode);
        Set<String> scopedEventCodes = scopedEventTypeCodes(systemEventsOnly);
        if (normalizedEventType != null && !scopedEventCodes.contains(normalizedEventType)) {
            normalizedEventType = null;
        }

        Map<String, String> eventTypeNames = eventTypeNameMap();
        Map<String, String> moduleTypeNames = moduleTypeNameMap();
        Map<String, String> attributeTypeNames = eventAttributeTypeNameMap();

        boolean useRollup = filterRollupService.shouldUseRollup(from, to, normalizedRequestPath);
        ReadWindow rawWindow = useRollup ? new ReadWindow(from, to, false, null) : boundedRawReadWindow(from, to, "/api/filter-options");
        Instant effectiveFrom = rawWindow.from();
        Instant effectiveTo = rawWindow.to();

        List<String> moduleCodes = useRollup
            ? filterRollupService.findModuleCodes(effectiveFrom, effectiveTo, scopedEventCodes)
            : (normalizedRequestPath == null
                ? eventRepository.findDistinctModuleCodesByScopeNoPath(effectiveFrom, effectiveTo, scopedEventCodes)
                : eventRepository.findDistinctModuleCodesByScope(effectiveFrom, effectiveTo, scopedEventCodes, normalizedRequestPath));
        List<OptionDto> modules = moduleCodes.stream()
            .limit(FILTER_OPTION_LIMIT)
            .map(code -> new OptionDto(code, moduleTypeNames.getOrDefault(code, code)))
            .toList();

        List<String> eventTypeCodes = normalizedRequestPath == null
            ? eventRepository.findDistinctEventTypeCodesByScopeNoPath(from, to, normalizedModuleCode)
            : eventRepository.findDistinctEventTypeCodesByScope(from, to, normalizedModuleCode, normalizedRequestPath);
        List<OptionDto> eventTypes = eventTypeCodes
            .stream()
            .limit(FILTER_OPTION_LIMIT)
            .filter(code -> scopedEventCodes.contains(normalizeCode(code)))
            .map(code -> new OptionDto(code, eventTypeNames.getOrDefault(code, code)))
            .toList();

        List<String> attributeTypeCodes = useRollup
            ? filterRollupService.findAttributeTypeCodes(effectiveFrom, effectiveTo, normalizedModuleCode, normalizedEventType)
            : (normalizedRequestPath == null
                ? eventAttributeRepository.findDistinctAttributeTypeCodesByScopeNoPath(
                    effectiveFrom,
                    effectiveTo,
                    normalizedModuleCode,
                    normalizedEventType
                )
                : eventAttributeRepository.findDistinctAttributeTypeCodesByScope(
                    effectiveFrom,
                    effectiveTo,
                    normalizedModuleCode,
                    normalizedEventType,
                    normalizedRequestPath
                ));
        List<OptionDto> attributeTypes = attributeTypeCodes
            .stream()
            .limit(FILTER_OPTION_LIMIT)
            .map(code -> new OptionDto(code, attributeTypeNames.getOrDefault(code, code)))
            .toList();

        List<OptionDto> attributeValues = normalizedAttributeCode == null
            ? List.of()
            : (useRollup
                ? filterRollupService.findAttributeValues(
                    effectiveFrom,
                    effectiveTo,
                    normalizedModuleCode,
                    normalizedEventType,
                    normalizedAttributeCode
                )
                : (normalizedRequestPath == null
                    ? eventAttributeRepository.findDistinctAttributeValuesByScopeNoPath(
                        effectiveFrom,
                        effectiveTo,
                        normalizedModuleCode,
                        normalizedEventType,
                        normalizedAttributeCode
                    )
                    : eventAttributeRepository.findDistinctAttributeValuesByScope(
                        effectiveFrom,
                        effectiveTo,
                        normalizedModuleCode,
                        normalizedEventType,
                        normalizedRequestPath,
                        normalizedAttributeCode
                    )))
                .stream()
                .limit(FILTER_OPTION_LIMIT)
                .map(value -> new OptionDto(value, value))
                .toList();

        FilterOptionsResponse response = new FilterOptionsResponse(modules, eventTypes, attributeTypes, attributeValues, rawWindow.partial(), rawWindow.warning());
        log.debug(
            "[FILTER_OPTIONS_PERF] service path={} totalMs={} from={} to={} module={} eventType={} requestPath={} attribute={} modules={} eventTypes={} attributeTypes={} attributeValues={} partial={}",
            useRollup ? "rollup" : "raw",
            elapsedMs(serviceStarted),
            effectiveFrom,
            effectiveTo,
            normalizedModuleCode,
            normalizedEventType,
            normalizedRequestPath,
            normalizedAttributeCode,
            modules.size(),
            eventTypes.size(),
            attributeTypes.size(),
            attributeValues.size(),
            rawWindow.partial()
        );
        return response;
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
        Set<String> visibleEventCodes = scopedEventTypeCodes(false);
        if (normalizedEventType != null && !visibleEventCodes.contains(normalizedEventType)) {
            normalizedEventType = "__NO_MATCH__";
        }
        int resolvedBucket = resolveBucketMinutes(from, to, bucketMinutes);

        if (canUseTimeRollup(
            normalizedRequestPath,
            filterMetricTypeCode,
            filterMetricValue,
            filterMetricMinValue,
            filterMetricMaxValue,
            filterAttributeCode,
            filterAttributeValue,
            filterAttributeMinValue,
            filterAttributeMaxValue
        )) {
            Set<String> eventTypeFilter = normalizedEventType == null ? nonEmptyEventTypeFilter(visibleEventCodes) : Set.of(normalizedEventType);
            List<AnalyticsTimeRollupService.AggregatePoint> points = timeRollupService.loadEventAggregatePoints(
                from,
                to,
                normalizedModuleCode,
                eventTypeFilter,
                resolvedBucket
            );
            AnalyticsTimeRollupService.AnalyticsAccumulator totalsAcc = timeRollupService.accumulateAll(points);
            Map<String, AnalyticsTimeRollupService.AnalyticsAccumulator> byType = timeRollupService.accumulateByCode(points);
            Map<String, String> eventTypeNames = eventTypeNameMap();

            List<EventKpiDto> breakdown = byType.entrySet().stream()
                .map(entry -> {
                    String code = entry.getKey();
                    AnalyticsTimeRollupService.AnalyticsAccumulator stat = entry.getValue();
                    return new EventKpiDto(
                        code,
                        eventTypeNames.getOrDefault(code, code),
                        stat.sampleCount(),
                        stat.errorRate(),
                        stat.avgMs(),
                        stat.p95Ms(),
                        stat.p99Ms(),
                        stat.maxMs()
                    );
                })
                .sorted(Comparator.comparing(EventKpiDto::count).reversed().thenComparing(EventKpiDto::eventTypeCode))
                .toList();

            List<TimeSeriesPointDto> series = timeRollupService.seriesFromPoints(from, to, resolvedBucket, points);
            KpiSnapshot totals = snapshotFromAccumulator(totalsAcc);
            return new OverviewResponse(from, to, resolvedBucket, totals, breakdown, series);
        }

        ReadWindow rawWindow = boundedRawReadWindow(from, to, "/api/overview");
        Instant effectiveFrom = rawWindow.from();
        Instant effectiveTo = rawWindow.to();
        List<AnalyticsEvent> events = filterEventsByRequestPath(
            eventRepository.findAllByRangeOrdered(effectiveFrom, effectiveTo, normalizedEventType, normalizedModuleCode),
            normalizedRequestPath
        );
        events = filterEventsByScope(events, false);
        events = filterEventsByMetric(events, filterMetricTypeCode, filterMetricValue, filterMetricMinValue, filterMetricMaxValue);
        events = filterEventsByAttribute(events, filterAttributeCode, filterAttributeValue, filterAttributeMinValue, filterAttributeMaxValue);
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
            effectiveFrom,
            effectiveTo,
            resolvedBucket,
            events,
            AnalyticsEvent::getStartedAt,
            AnalyticsEvent::getDurationMs,
            event -> Boolean.TRUE.equals(event.getIsError())
        );
        return new OverviewResponse(effectiveFrom, effectiveTo, resolvedBucket, totals, breakdown, series, rawWindow.partial(), rawWindow.warning());
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
        long serviceStarted = System.nanoTime();
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        String normalizedEventType = normalizeCode(eventTypeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        Set<String> visibleEventCodes = scopedEventTypeCodes(false);
        if (normalizedEventType != null && !visibleEventCodes.contains(normalizedEventType)) {
            normalizedEventType = "__NO_MATCH__";
        }
        int resolvedBucket = resolveBucketMinutes(from, to, bucketMinutes);

        if (canUseTimeRollup(
            normalizedRequestPath,
            filterMetricTypeCode,
            filterMetricValue,
            filterMetricMinValue,
            filterMetricMaxValue,
            filterAttributeCode,
            filterAttributeValue,
            filterAttributeMinValue,
            filterAttributeMaxValue
        )) {
            Set<String> eventTypeFilter = normalizedEventType == null ? nonEmptyEventTypeFilter(visibleEventCodes) : Set.of(normalizedEventType);
            long rollupQueryStarted = System.nanoTime();
            List<AnalyticsTimeRollupService.AggregatePoint> points = timeRollupService.loadStageAggregatePoints(
                from,
                to,
                normalizedModuleCode,
                eventTypeFilter,
                Set.of(),
                resolvedBucket
            );
            long rollupQueryMs = elapsedMs(rollupQueryStarted);
            if (points.isEmpty()) {
                StageBreakdownResponse response = new StageBreakdownResponse(from, to, resolvedBucket, List.of(), List.of());
                log.debug(
            "[STAGE_BREAKDOWN_PERF] service path=rollup totalMs={} rollupQueryMs={} buildMs=0 from={} to={} module={} eventType={} requestPath={} bucket={} stages={} series={}",
                    elapsedMs(serviceStarted),
                    rollupQueryMs,
                    from,
                    to,
                    normalizedModuleCode,
                    normalizedEventType,
                    normalizedRequestPath,
                    resolvedBucket,
                    0,
                    0
                );
                return response;
            }

            long buildStarted = System.nanoTime();
            Map<String, String> stageTypeNames = stageTypeNameMap();
            Map<String, AnalyticsTimeRollupService.AnalyticsAccumulator> byStage = timeRollupService.accumulateByCode(points);
            List<StageKpiDto> kpi = byStage.entrySet().stream()
                .map(entry -> {
                    String code = entry.getKey();
                    AnalyticsTimeRollupService.AnalyticsAccumulator stat = entry.getValue();
                    return new StageKpiDto(
                        code,
                        stageTypeNames.getOrDefault(code, code),
                        stat.sampleCount(),
                        stat.errorRate(),
                        stat.avgMs(),
                        stat.p95Ms(),
                        stat.p99Ms(),
                        stat.maxMs()
                    );
                })
                .sorted(Comparator.comparing(StageKpiDto::count).reversed().thenComparing(StageKpiDto::stageTypeCode))
                .toList();

            Set<String> topStageCodes = kpi.stream()
                .limit(6)
                .map(StageKpiDto::stageTypeCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

            List<StageSeriesDto> stageSeries = topStageCodes.stream()
                .map(code -> new StageSeriesDto(
                    code,
                    stageTypeNames.getOrDefault(code, code),
                    timeRollupService.seriesFromPoints(
                        from,
                        to,
                        resolvedBucket,
                        timeRollupService.pointsForCode(points, code)
                    )
                ))
                .sorted(Comparator.comparing(StageSeriesDto::stageTypeCode))
                .toList();

            StageBreakdownResponse response = new StageBreakdownResponse(from, to, resolvedBucket, kpi, stageSeries);
            log.debug(
            "[STAGE_BREAKDOWN_PERF] service path=rollup totalMs={} rollupQueryMs={} buildMs={} from={} to={} module={} eventType={} requestPath={} bucket={} points={} stages={} series={}",
                elapsedMs(serviceStarted),
                rollupQueryMs,
                elapsedMs(buildStarted),
                from,
                to,
                normalizedModuleCode,
                normalizedEventType,
                normalizedRequestPath,
                resolvedBucket,
                points.size(),
                kpi.size(),
                stageSeries.size()
            );
            return response;
        }

        long rawQueryStarted = System.nanoTime();
        ReadWindow rawWindow = boundedRawReadWindow(from, to, "/api/stages");
        Instant effectiveFrom = rawWindow.from();
        Instant effectiveTo = rawWindow.to();
        List<AnalyticsEvent> events = filterEventsByRequestPath(
            eventRepository.findAllByRangeOrdered(effectiveFrom, effectiveTo, normalizedEventType, normalizedModuleCode),
            normalizedRequestPath
        );
        long rawQueryMs = elapsedMs(rawQueryStarted);
        events = filterEventsByScope(events, false);
        events = filterEventsByMetric(events, filterMetricTypeCode, filterMetricValue, filterMetricMinValue, filterMetricMaxValue);
        events = filterEventsByAttribute(events, filterAttributeCode, filterAttributeValue, filterAttributeMinValue, filterAttributeMaxValue);
        if (events.isEmpty()) {
            StageBreakdownResponse response = new StageBreakdownResponse(effectiveFrom, effectiveTo, resolvedBucket, List.of(), List.of(), rawWindow.partial(), rawWindow.warning());
            log.debug(
            "[STAGE_BREAKDOWN_PERF] service path=raw totalMs={} rawQueryMs={} stageQueryMs=0 buildMs=0 from={} to={} module={} eventType={} requestPath={} bucket={} events={} stages={} series={}",
                elapsedMs(serviceStarted),
                rawQueryMs,
                effectiveFrom,
                effectiveTo,
                normalizedModuleCode,
                normalizedEventType,
                normalizedRequestPath,
                resolvedBucket,
                0,
                0,
                0
            );
            return response;
        }

        Map<String, String> stageTypeNames = stageTypeNameMap();
        long stageLoadStarted = System.nanoTime();
        List<AnalyticsStage> stages = findStagesByEventIds(ids(events));
        long stageLoadMs = elapsedMs(stageLoadStarted);

        long buildStarted = System.nanoTime();
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
                    effectiveFrom,
                    effectiveTo,
                    resolvedBucket,
                    entry.getValue(),
                    AnalyticsStage::getStartedAt,
                    AnalyticsStage::getDurationMs,
                    stage -> Boolean.TRUE.equals(stage.getIsError())
                )
            ))
            .sorted(Comparator.comparing(StageSeriesDto::stageTypeCode))
            .toList();

        StageBreakdownResponse response = new StageBreakdownResponse(effectiveFrom, effectiveTo, resolvedBucket, kpi, stageSeries, rawWindow.partial(), rawWindow.warning());
        log.debug(
            "[STAGE_BREAKDOWN_PERF] service path=raw totalMs={} rawQueryMs={} stageQueryMs={} buildMs={} from={} to={} module={} eventType={} requestPath={} bucket={} events={} stages={} series={}",
            elapsedMs(serviceStarted),
            rawQueryMs,
            stageLoadMs,
            elapsedMs(buildStarted),
            effectiveFrom,
            effectiveTo,
            normalizedModuleCode,
            normalizedEventType,
            normalizedRequestPath,
            resolvedBucket,
            events.size(),
            stages.size(),
            stageSeries.size()
        );
        return response;
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
        boolean includeSummaries,
        boolean includeTopValues,
        boolean includeSeries
    ) {
        long serviceStarted = System.nanoTime();
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        String normalizedEventType = normalizeCode(eventTypeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        String normalizedStageType = normalizeCode(stageTypeCode);
        String normalizedMetricType = normalizeCode(metricTypeCode);
        Set<String> visibleEventCodes = scopedEventTypeCodes(false);
        Set<String> effectiveEventTypes;
        if (normalizedEventType != null && !visibleEventCodes.contains(normalizedEventType)) {
            normalizedEventType = "__NO_MATCH__";
            effectiveEventTypes = Set.of(normalizedEventType);
        } else if (normalizedEventType != null) {
            effectiveEventTypes = Set.of(normalizedEventType);
        } else {
            effectiveEventTypes = nonEmptyEventTypeFilter(visibleEventCodes);
        }
        if (effectiveEventTypes.isEmpty()) {
            return new StageMetricResponse(from, to, resolveBucketMinutes(from, to, bucketMinutes), null, null, null, false, List.of(), List.of(), List.of());
        }
        int resolvedBucket = resolveBucketMinutes(from, to, bucketMinutes);

        boolean hasAdditionalEventFilters = normalizeCode(filterMetricTypeCode) != null
            || normalizeText(filterMetricValue) != null
            || filterMetricMinValue != null
            || filterMetricMaxValue != null
            || normalizeCode(filterAttributeCode) != null
            || normalizeText(filterAttributeValue) != null
            || filterAttributeMinValue != null
            || filterAttributeMaxValue != null;

        boolean canUseStageMetricRollup = stageMetricRollupService.isEnabled()
            && normalizedRequestPath == null
            && !hasAdditionalEventFilters
            && !effectiveEventTypes.isEmpty();
        if (canUseStageMetricRollup) {
            if (!includeSummaries && normalizedMetricType != null) {
                long buildStarted = System.nanoTime();
                Map<String, String> metricTypeNames = stageMetricTypeNameMap();
                MetricValueKind valueKind = stageMetricTypeValueKindMap()
                    .getOrDefault(normalizedMetricType, MetricValueKind.NUMERIC);
                boolean numeric = valueKind == MetricValueKind.NUMERIC && !shouldTreatMetricAsText(normalizedMetricType);
                long buildMs = elapsedMs(buildStarted);

                long seriesStarted = System.nanoTime();
                List<TimeSeriesPointDto> numericSeries = numeric && includeSeries
                    ? stageMetricRollupService.loadNumericSeries(
                        from,
                        to,
                        normalizedModuleCode,
                        effectiveEventTypes,
                        normalizedStageType,
                        normalizedMetricType,
                        resolvedBucket
                    )
                    : List.of();
                long seriesMs = elapsedMs(seriesStarted);

                long topValuesStarted = System.nanoTime();
                List<TopValueDto> selectedTopValues = includeTopValues
                    ? stageMetricRollupService.loadTopValues(
                        from,
                        to,
                        normalizedModuleCode,
                        effectiveEventTypes,
                        normalizedStageType,
                        normalizedMetricType,
                        8
                    )
                    : List.of();
                long topValuesMs = elapsedMs(topValuesStarted);

                StageMetricResponse response = new StageMetricResponse(
                    from,
                    to,
                    resolvedBucket,
                    normalizedMetricType,
                    metricTypeNames.getOrDefault(normalizedMetricType, normalizedMetricType),
                    null,
                    numeric,
                    List.of(),
                    numericSeries,
                    selectedTopValues
                );
                logStageMetricsServicePerf(
                    serviceStarted,
                    "rollup",
                    from,
                    to,
                    normalizedModuleCode,
                    normalizedEventType,
                    normalizedStageType,
                    normalizedMetricType,
                    resolvedBucket,
                    includeSummaries,
                    includeTopValues,
                    0,
                    0,
                    0,
                    buildMs,
                    seriesMs,
                    topValuesMs,
                    response.summaries().size(),
                    response.numericSeries().size(),
                    response.selectedTopValues().size()
                );
                return response;
            }

            long rollupStarted = System.nanoTime();
            List<AnalyticsStageMetricRollupService.MetricSummaryPoint> summaryPoints =
                stageMetricRollupService.loadMetricSummaries(
                    from,
                    to,
                    normalizedModuleCode,
                    effectiveEventTypes,
                    normalizedStageType
                );
            long rollupMs = elapsedMs(rollupStarted);
            if (summaryPoints.isEmpty()) {
                logStageMetricsServicePerf(
                    serviceStarted,
                    "rollup",
                    from,
                    to,
                    normalizedModuleCode,
                    normalizedEventType,
                    normalizedStageType,
                    normalizedMetricType,
                    resolvedBucket,
                    includeSummaries,
                    includeTopValues,
                    rollupMs,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
                );
                return new StageMetricResponse(from, to, resolvedBucket, null, null, null, false, List.of(), List.of(), List.of());
            }

            long buildStarted = System.nanoTime();
            Map<String, String> metricTypeNames = stageMetricTypeNameMap();
            Map<String, String> metricTypeDescriptions = stageMetricTypeDescriptionMap();
            Map<String, String> metricTypeReadingGuides = stageMetricTypeReadingGuideMap();

            List<StageMetricSummaryDto> summaries = summaryPoints.stream()
                .map(point -> {
                    boolean numeric = point.numeric() && !shouldTreatMetricAsText(point.metricTypeCode());
                    return new StageMetricSummaryDto(
                        point.metricTypeCode(),
                        metricTypeNames.getOrDefault(point.metricTypeCode(), point.metricTypeCode()),
                        metricTypeDescriptions.getOrDefault(point.metricTypeCode(), null),
                        metricTypeReadingGuides.getOrDefault(point.metricTypeCode(), null),
                        point.unit(),
                        numeric,
                        point.sampleCount(),
                        numeric ? point.avgValue() : BigDecimal.ZERO,
                        numeric ? point.p95Value() : BigDecimal.ZERO,
                        numeric ? Objects.requireNonNullElse(point.minValue(), BigDecimal.ZERO) : BigDecimal.ZERO,
                        numeric ? Objects.requireNonNullElse(point.maxValue(), BigDecimal.ZERO) : BigDecimal.ZERO,
                        List.of()
                    );
                })
                .sorted(Comparator.comparing(StageMetricSummaryDto::sampleCount).reversed().thenComparing(StageMetricSummaryDto::metricTypeCode))
                .toList();
            long buildMs = elapsedMs(buildStarted);
            if (summaries.isEmpty()) {
                return new StageMetricResponse(from, to, resolvedBucket, null, null, null, false, List.of(), List.of(), List.of());
            }

            if (!includeSummaries) {
                StageMetricSummaryDto selected = null;
                if (normalizedMetricType != null) {
                    selected = summaries.stream()
                        .filter(summary -> Objects.equals(summary.metricTypeCode(), normalizedMetricType))
                        .findFirst()
                        .orElse(null);
                    if (selected == null) {
                        return new StageMetricResponse(from, to, resolvedBucket, null, null, null, false, List.of(), List.of(), List.of());
                    }
                } else {
                    selected = summaries.stream()
                        .filter(StageMetricSummaryDto::numeric)
                        .findFirst()
                        .orElse(summaries.getFirst());
                }

                long seriesStarted = System.nanoTime();
                List<TimeSeriesPointDto> numericSeries = selected.numeric() && includeSeries
                    ? stageMetricRollupService.loadNumericSeries(
                        from,
                        to,
                        normalizedModuleCode,
                        effectiveEventTypes,
                        normalizedStageType,
                        selected.metricTypeCode(),
                        resolvedBucket
                    )
                    : List.of();
                long seriesMs = elapsedMs(seriesStarted);
                long topValuesStarted = System.nanoTime();
                List<TopValueDto> selectedTopValues = includeTopValues
                    ? stageMetricRollupService.loadTopValues(
                        from,
                        to,
                        normalizedModuleCode,
                        effectiveEventTypes,
                        normalizedStageType,
                        selected.metricTypeCode(),
                        8
                    )
                    : List.of();
                long topValuesMs = elapsedMs(topValuesStarted);

                StageMetricResponse response = new StageMetricResponse(
                    from,
                    to,
                    resolvedBucket,
                    selected.metricTypeCode(),
                    selected.metricTypeName(),
                    selected.unit(),
                    selected.numeric(),
                    List.of(),
                    numericSeries,
                    selectedTopValues
                );
                logStageMetricsServicePerf(
                    serviceStarted,
                    "rollup",
                    from,
                    to,
                    normalizedModuleCode,
                    normalizedEventType,
                    normalizedStageType,
                    selected.metricTypeCode(),
                    resolvedBucket,
                    includeSummaries,
                    includeTopValues,
                    rollupMs,
                    0,
                    0,
                    buildMs,
                    seriesMs,
                    topValuesMs,
                    response.summaries().size(),
                    response.numericSeries().size(),
                    response.selectedTopValues().size()
                );
                return response;
            }

            StageMetricSummaryDto selected = summaries.stream()
                .filter(summary -> Objects.equals(summary.metricTypeCode(), normalizedMetricType))
                .findFirst()
                .orElseGet(() -> summaries.stream()
                .filter(StageMetricSummaryDto::numeric)
                .findFirst()
                .orElse(summaries.getFirst()));

            long seriesStarted = System.nanoTime();
            List<TimeSeriesPointDto> numericSeries = selected.numeric() && includeSeries
                ? stageMetricRollupService.loadNumericSeries(
                    from,
                    to,
                    normalizedModuleCode,
                    effectiveEventTypes,
                    normalizedStageType,
                    selected.metricTypeCode(),
                    resolvedBucket
                )
                : List.of();
            long seriesMs = elapsedMs(seriesStarted);
            long topValuesStarted = System.nanoTime();
            List<TopValueDto> selectedTopValues = includeTopValues
                ? stageMetricRollupService.loadTopValues(
                    from,
                    to,
                    normalizedModuleCode,
                    effectiveEventTypes,
                    normalizedStageType,
                    selected.metricTypeCode(),
                    8
                )
                : List.of();
            long topValuesMs = elapsedMs(topValuesStarted);

            StageMetricResponse response = new StageMetricResponse(
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
            logStageMetricsServicePerf(
                serviceStarted,
                "rollup",
                from,
                to,
                normalizedModuleCode,
                normalizedEventType,
                normalizedStageType,
                selected.metricTypeCode(),
                resolvedBucket,
                includeSummaries,
                includeTopValues,
                rollupMs,
                0,
                0,
                buildMs,
                seriesMs,
                topValuesMs,
                response.summaries().size(),
                response.numericSeries().size(),
                response.selectedTopValues().size()
            );
            return response;
        }

        ReadWindow rawWindow = boundedRawReadWindow(from, to, "/api/stage-metrics");
        Instant effectiveFrom = rawWindow.from();
        Instant effectiveTo = rawWindow.to();

        // Fast path for large periods: direct DB join for a concrete metric type, without loading all events/stages into memory.
        if (!includeSummaries
            && normalizedMetricType != null
            && normalizedRequestPath == null
            && !hasAdditionalEventFilters) {
            long directQueryStarted = System.nanoTime();
            List<AnalyticsStageMetric> scopedMetrics = stageMetricRepository.findByScope(
                effectiveFrom,
                effectiveTo,
                normalizedModuleCode,
                normalizedEventType,
                normalizedStageType,
                normalizedMetricType
            );
            long directQueryMs = elapsedMs(directQueryStarted);
            if (scopedMetrics.isEmpty()) {
                logStageMetricsServicePerf(
                    serviceStarted,
                    "direct_metric_scope",
                    from,
                    to,
                    normalizedModuleCode,
                    normalizedEventType,
                    normalizedStageType,
                    normalizedMetricType,
                    resolvedBucket,
                    includeSummaries,
                    includeTopValues,
                    0,
                    directQueryMs,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
                );
                return new StageMetricResponse(
                    effectiveFrom,
                    effectiveTo,
                    resolvedBucket,
                    null,
                    null,
                    null,
                    false,
                    List.of(),
                    List.of(),
                    List.of(),
                    rawWindow.partial(),
                    rawWindow.warning()
                );
            }

            long buildStarted = System.nanoTime();
            Map<String, String> metricTypeNames = stageMetricTypeNameMap();
            List<BigDecimal> numericValues = scopedMetrics.stream()
                .map(AnalyticsStageMetric::getMetricValueNum)
                .filter(Objects::nonNull)
                .toList();
            boolean numeric = !shouldTreatMetricAsText(normalizedMetricType) && !numericValues.isEmpty();
            String unit = scopedMetrics.stream()
                .map(AnalyticsStageMetric::getUnit)
                .filter(unitValue -> unitValue != null && !unitValue.isBlank())
                .findFirst()
                .orElse(null);
            List<TimeSeriesPointDto> numericSeries = numeric && includeSeries
                    ? buildSeries(
                    effectiveFrom,
                    effectiveTo,
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
            List<TopValueDto> selectedTopValues = includeTopValues ? topValuesForMetrics(scopedMetrics) : List.of();
            long buildMs = elapsedMs(buildStarted);

            StageMetricResponse response = new StageMetricResponse(
                effectiveFrom,
                effectiveTo,
                resolvedBucket,
                normalizedMetricType,
                metricTypeNames.getOrDefault(normalizedMetricType, normalizedMetricType),
                unit,
                numeric,
                List.of(),
                numericSeries,
                selectedTopValues,
                rawWindow.partial(),
                rawWindow.warning()
            );
            logStageMetricsServicePerf(
                serviceStarted,
                "direct_metric_scope",
                from,
                to,
                normalizedModuleCode,
                normalizedEventType,
                normalizedStageType,
                normalizedMetricType,
                resolvedBucket,
                includeSummaries,
                includeTopValues,
                0,
                directQueryMs,
                0,
                buildMs,
                0,
                0,
                response.summaries().size(),
                response.numericSeries().size(),
                response.selectedTopValues().size()
            );
            return response;
        }

        long rawQueryStarted = System.nanoTime();
        List<AnalyticsEvent> events = filterEventsByRequestPath(
            eventRepository.findAllByRangeOrdered(effectiveFrom, effectiveTo, normalizedEventType, normalizedModuleCode),
            normalizedRequestPath
        );
        events = filterEventsByScope(events, false);
        events = filterEventsByMetric(events, filterMetricTypeCode, filterMetricValue, filterMetricMinValue, filterMetricMaxValue);
        events = filterEventsByAttribute(events, filterAttributeCode, filterAttributeValue, filterAttributeMinValue, filterAttributeMaxValue);
        long rawQueryMs = elapsedMs(rawQueryStarted);
        if (events.isEmpty()) {
            logStageMetricsServicePerf(
                serviceStarted,
                "raw",
                from,
                to,
                normalizedModuleCode,
                normalizedEventType,
                normalizedStageType,
                normalizedMetricType,
                resolvedBucket,
                includeSummaries,
                includeTopValues,
                0,
                rawQueryMs,
                0,
                0,
                0,
                0,
                0,
                0,
                0
            );
            return new StageMetricResponse(
                effectiveFrom,
                effectiveTo,
                resolvedBucket,
                null,
                null,
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                rawWindow.partial(),
                rawWindow.warning()
            );
        }

        long groupingStarted = System.nanoTime();
        List<Long> eventIds = ids(events);
        List<AnalyticsStage> stages = findStagesByEventIds(eventIds, normalizedStageType);
        if (stages.isEmpty()) {
            logStageMetricsServicePerf(
                serviceStarted,
                "raw",
                from,
                to,
                normalizedModuleCode,
                normalizedEventType,
                normalizedStageType,
                normalizedMetricType,
                resolvedBucket,
                includeSummaries,
                includeTopValues,
                0,
                rawQueryMs,
                elapsedMs(groupingStarted),
                0,
                0,
                0,
                0,
                0,
                0
            );
            return new StageMetricResponse(
                effectiveFrom,
                effectiveTo,
                resolvedBucket,
                null,
                null,
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                rawWindow.partial(),
                rawWindow.warning()
            );
        }

        List<AnalyticsStageMetric> metrics = findStageMetricsByStageIds(
            ids(stages),
            includeSummaries ? null : normalizedMetricType
        );
        if (metrics.isEmpty()) {
            logStageMetricsServicePerf(
                serviceStarted,
                "raw",
                from,
                to,
                normalizedModuleCode,
                normalizedEventType,
                normalizedStageType,
                normalizedMetricType,
                resolvedBucket,
                includeSummaries,
                includeTopValues,
                0,
                rawQueryMs,
                elapsedMs(groupingStarted),
                0,
                0,
                0,
                0,
                0,
                0
            );
            return new StageMetricResponse(
                effectiveFrom,
                effectiveTo,
                resolvedBucket,
                null,
                null,
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                rawWindow.partial(),
                rawWindow.warning()
            );
        }
        long groupingMs = elapsedMs(groupingStarted);

        long buildStarted = System.nanoTime();
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
                return new StageMetricResponse(
                    effectiveFrom,
                    effectiveTo,
                    resolvedBucket,
                    null,
                    null,
                    null,
                    false,
                    List.of(),
                    List.of(),
                    List.of(),
                    rawWindow.partial(),
                    rawWindow.warning()
                );
            }

            List<AnalyticsStageMetric> selectedMetrics = metrics.stream()
                .filter(metric -> Objects.equals(metric.getMetricTypeCode(), selectedCode))
                .toList();
            if (selectedMetrics.isEmpty()) {
                return new StageMetricResponse(
                    effectiveFrom,
                    effectiveTo,
                    resolvedBucket,
                    null,
                    null,
                    null,
                    false,
                    List.of(),
                    List.of(),
                    List.of(),
                    rawWindow.partial(),
                    rawWindow.warning()
                );
            }

            List<BigDecimal> numericValues = selectedMetrics.stream()
                .map(AnalyticsStageMetric::getMetricValueNum)
                .filter(Objects::nonNull)
                .toList();
            boolean numeric = !shouldTreatMetricAsText(selectedCode) && !numericValues.isEmpty();
            String unit = selectedMetrics.stream()
                .map(AnalyticsStageMetric::getUnit)
                .filter(unitValue -> unitValue != null && !unitValue.isBlank())
                .findFirst()
                .orElse(null);
            List<TimeSeriesPointDto> numericSeries = numeric && includeSeries
                ? buildSeries(
                    effectiveFrom,
                    effectiveTo,
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

            List<TopValueDto> selectedTopValues = includeTopValues ? topValuesForMetrics(selectedMetrics) : List.of();
            StageMetricResponse response = new StageMetricResponse(
                effectiveFrom,
                effectiveTo,
                resolvedBucket,
                selectedCode,
                metricTypeNames.getOrDefault(selectedCode, selectedCode),
                unit,
                numeric,
                List.of(),
                numericSeries,
                selectedTopValues,
                rawWindow.partial(),
                rawWindow.warning()
            );
            logStageMetricsServicePerf(
                serviceStarted,
                "raw",
                from,
                to,
                normalizedModuleCode,
                normalizedEventType,
                normalizedStageType,
                selectedCode,
                resolvedBucket,
                includeSummaries,
                includeTopValues,
                0,
                rawQueryMs,
                groupingMs,
                elapsedMs(buildStarted),
                0,
                0,
                response.summaries().size(),
                response.numericSeries().size(),
                response.selectedTopValues().size()
            );
            return response;
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
                boolean numeric = !shouldTreatMetricAsText(code) && !numericValues.isEmpty();
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
            return new StageMetricResponse(
                effectiveFrom,
                effectiveTo,
                resolvedBucket,
                null,
                null,
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                rawWindow.partial(),
                rawWindow.warning()
            );
        }

        StageMetricSummaryDto selected = summaries.stream()
            .filter(summary -> Objects.equals(summary.metricTypeCode(), normalizedMetricType))
            .findFirst()
            .orElseGet(() -> summaries.stream()
                .filter(StageMetricSummaryDto::numeric)
                .findFirst()
                .orElse(summaries.getFirst()));

        List<AnalyticsStageMetric> selectedMetrics = byMetricType.getOrDefault(selected.metricTypeCode(), List.of());
        List<TimeSeriesPointDto> numericSeries = selected.numeric() && includeSeries
            ? buildSeries(
                effectiveFrom,
                effectiveTo,
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

        List<TopValueDto> selectedTopValues = includeTopValues ? selected.topValues() : List.of();

        StageMetricResponse response = new StageMetricResponse(
            effectiveFrom,
            effectiveTo,
            resolvedBucket,
            selected.metricTypeCode(),
            selected.metricTypeName(),
            selected.unit(),
            selected.numeric(),
            summaries,
            numericSeries,
            selectedTopValues,
            rawWindow.partial(),
            rawWindow.warning()
        );
        logStageMetricsServicePerf(
            serviceStarted,
            "raw",
            from,
            to,
            normalizedModuleCode,
            normalizedEventType,
            normalizedStageType,
            selected.metricTypeCode(),
            resolvedBucket,
            includeSummaries,
            includeTopValues,
            0,
            rawQueryMs,
            groupingMs,
            elapsedMs(buildStarted),
            0,
            0,
            response.summaries().size(),
            response.numericSeries().size(),
            response.selectedTopValues().size()
        );
        return response;
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
        Integer bucketMinutes,
        boolean includeEventStageBreakdown
    ) {
        return universal(
            from,
            to,
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
            includeEventStageBreakdown,
            false,
            null
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
        Integer bucketMinutes,
        boolean includeEventStageBreakdown,
        boolean systemEventsOnly
    ) {
        return universal(
            from,
            to,
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
            includeEventStageBreakdown,
            systemEventsOnly,
            null
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
        Integer bucketMinutes,
        boolean includeEventStageBreakdown,
        boolean systemEventsOnly,
        Boolean isError
    ) {
        return universal(
            from,
            to,
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
            stageTypeCode == null ? List.of() : List.of(stageTypeCode),
            bucketMinutes,
            includeEventStageBreakdown,
            systemEventsOnly,
            isError
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
        List<String> stageTypeCodes,
        Integer bucketMinutes,
        boolean includeEventStageBreakdown,
        boolean systemEventsOnly,
        Boolean isError
    ) {
        long serviceStarted = System.nanoTime();
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        Set<String> normalizedEventTypes = normalizeCodes(eventTypeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        String normalizedAttributeCode = normalizeCode(attributeCode);
        String normalizedAttributeValue = normalizeText(attributeValue);
        Set<String> normalizedStageTypes = normalizeCodes(stageTypeCodes);
        Set<String> visibleEventCodes = scopedEventTypeCodes(systemEventsOnly);
        normalizedEventTypes = normalizedEventTypes.isEmpty()
            ? nonEmptyEventTypeFilter(visibleEventCodes)
            : normalizedEventTypes.stream()
                .filter(visibleEventCodes::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedEventTypes.isEmpty()) {
            normalizedEventTypes = Set.of("__NO_MATCH__");
        }
        int resolvedBucket = resolveBucketMinutes(from, to, bucketMinutes);
        boolean canUseRollup = canUseTimeRollup(
            normalizedRequestPath,
            filterMetricTypeCode,
            filterMetricValue,
            filterMetricMinValue,
            filterMetricMaxValue,
            filterAttributeCode,
            filterAttributeValue,
            filterAttributeMinValue,
            filterAttributeMaxValue
        ) && normalizedAttributeCode == null && normalizedAttributeValue == null;

        if (canUseRollup) {
            Set<String> stageTypeFilter = normalizedStageTypes;

            long eventRollupStarted = System.nanoTime();
            List<AnalyticsTimeRollupService.AggregatePoint> eventPoints = normalizedStageTypes.isEmpty()
                ? timeRollupService.loadEventAggregatePoints(
                    from,
                    to,
                    normalizedModuleCode,
                    normalizedEventTypes,
                    resolvedBucket,
                    isError
                )
                : timeRollupService.loadStageAggregatePointsByEvent(
                    from,
                    to,
                    normalizedModuleCode,
                    normalizedEventTypes,
                    stageTypeFilter,
                    resolvedBucket
                );
            long eventRollupMs = elapsedMs(eventRollupStarted);
            long stageRollupStarted = System.nanoTime();
            List<AnalyticsTimeRollupService.AggregatePoint> stagePoints = isError == null
                ? timeRollupService.loadStageAggregatePoints(
                    from,
                    to,
                    normalizedModuleCode,
                    normalizedEventTypes,
                    stageTypeFilter,
                    resolvedBucket
                )
                : List.of();
            long stageRollupMs = elapsedMs(stageRollupStarted);

            long responseBuildStarted = System.nanoTime();
            Map<String, String> eventTypeNames = eventTypeNameMap();
            Map<String, String> stageTypeNames = stageTypeNameMap();
            AnalyticsTimeRollupService.AnalyticsAccumulator totalsAcc = timeRollupService.accumulateAll(eventPoints);
            KpiSnapshot totals = snapshotFromAccumulator(totalsAcc);

            Map<String, AnalyticsTimeRollupService.AnalyticsAccumulator> eventByCode = timeRollupService.accumulateByCode(eventPoints);
            List<EventKpiDto> eventBreakdown = eventByCode.entrySet().stream()
                .map(entry -> {
                    String code = entry.getKey();
                    AnalyticsTimeRollupService.AnalyticsAccumulator stat = entry.getValue();
                    return new EventKpiDto(
                        code,
                        eventTypeNames.getOrDefault(code, code),
                        stat.sampleCount(),
                        stat.errorRate(),
                        stat.avgMs(),
                        stat.p95Ms(),
                        stat.p99Ms(),
                        stat.maxMs()
                    );
                })
                .sorted(Comparator.comparing(EventKpiDto::count).reversed().thenComparing(EventKpiDto::eventTypeCode))
                .toList();

            Map<String, AnalyticsTimeRollupService.AnalyticsAccumulator> stageByCode = timeRollupService.accumulateByCode(stagePoints);
            List<StageKpiDto> stageRows = stageByCode.entrySet().stream()
                .map(entry -> {
                    String code = entry.getKey();
                    AnalyticsTimeRollupService.AnalyticsAccumulator stat = entry.getValue();
                    return new StageKpiDto(
                        code,
                        stageTypeNames.getOrDefault(code, code),
                        stat.sampleCount(),
                        stat.errorRate(),
                        stat.avgMs(),
                        stat.p95Ms(),
                        stat.p99Ms(),
                        stat.maxMs()
                    );
                })
                .sorted(Comparator.comparing(StageKpiDto::count).reversed().thenComparing(StageKpiDto::stageTypeCode))
                .toList();

            List<TimeSeriesPointDto> series = timeRollupService.seriesFromPoints(from, to, resolvedBucket, eventPoints);
            List<UniversalEventSeriesDto> eventSeries = eventBreakdown.stream()
                .map(row -> new UniversalEventSeriesDto(
                    row.eventTypeCode(),
                    row.eventTypeName(),
                    timeRollupService.seriesFromPoints(
                        from,
                        to,
                        resolvedBucket,
                        timeRollupService.pointsForCode(eventPoints, row.eventTypeCode())
                    )
                ))
                .toList();

            long eventStageBreakdownStarted = System.nanoTime();
            List<UniversalEventStageBreakdownDto> eventStageBreakdown = includeEventStageBreakdown
                ? eventBreakdown.stream()
                    .limit(10)
                    .map(row -> {
                        List<AnalyticsTimeRollupService.AggregatePoint> scopedStagePoints = timeRollupService.loadStageAggregatePoints(
                            from,
                            to,
                            normalizedModuleCode,
                            Set.of(row.eventTypeCode()),
                            stageTypeFilter,
                            resolvedBucket
                        );
                        Map<String, AnalyticsTimeRollupService.AnalyticsAccumulator> scopedStages = timeRollupService.accumulateByCode(scopedStagePoints);
                        List<StageKpiDto> stageKpis = scopedStages.entrySet().stream()
                            .map(entry -> {
                                String stageCode = entry.getKey();
                                AnalyticsTimeRollupService.AnalyticsAccumulator stat = entry.getValue();
                                return new StageKpiDto(
                                    stageCode,
                                    stageTypeNames.getOrDefault(stageCode, stageCode),
                                    stat.sampleCount(),
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
                            row.eventTypeCode(),
                            row.eventTypeName(),
                            stageKpis
                        );
                    })
                    .toList()
                : List.of();
            long eventStageBreakdownMs = includeEventStageBreakdown ? elapsedMs(eventStageBreakdownStarted) : 0L;

            Map<String, String> attributeTypeNames = eventAttributeTypeNameMap();
            long attributesStarted = System.nanoTime();
            List<OptionDto> availableAttributeTypes = availableAttributeTypesForRollup(
                from,
                to,
                normalizedModuleCode,
                normalizedEventTypes,
                attributeTypeNames
            );
            long attributesMs = elapsedMs(attributesStarted);
            long responseBuildMs = elapsedMs(responseBuildStarted);

            UniversalResponse response = new UniversalResponse(
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
            log.debug(
            "[UNIVERSAL_PERF] service path=aggregate totalMs={} eventRollupMs={} stageRollupMs={} eventStageBreakdownMs={} attributesMs={} responseBuildMs={} includeEventStageBreakdown={} counts eventPoints={} stagePoints={} series={} stages={} events={} eventSeries={} eventStageBreakdown={} attrs={}",
                elapsedMs(serviceStarted),
                eventRollupMs,
                stageRollupMs,
                eventStageBreakdownMs,
                attributesMs,
                responseBuildMs,
                includeEventStageBreakdown,
                eventPoints.size(),
                stagePoints.size(),
                series.size(),
                stageRows.size(),
                eventBreakdown.size(),
                eventSeries.size(),
                eventStageBreakdown.size(),
                availableAttributeTypes.size()
            );
            return response;
        }

        long rawQueryStarted = System.nanoTime();
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
        long rawQueryMs = elapsedMs(rawQueryStarted);
        long javaFilterStarted = System.nanoTime();
        Set<String> effectiveEventTypes = normalizedEventTypes;
        events = events.stream()
            .filter(event -> effectiveEventTypes.contains(normalizeCode(event.getEventTypeCode())))
            .filter(event -> isError == null || isError.equals(event.getIsError()))
            .toList();
        events = filterEventsByRequestPath(events, normalizedRequestPath);
        events = filterEventsByMetric(events, filterMetricTypeCode, filterMetricValue, filterMetricMinValue, filterMetricMaxValue);
        events = filterEventsByAttribute(events, filterAttributeCode, filterAttributeValue, filterAttributeMinValue, filterAttributeMaxValue);
        long javaFilterMs = elapsedMs(javaFilterStarted);
        if (events.isEmpty()) {
            UniversalResponse empty = new UniversalResponse(from, to, resolvedBucket, snapshotFromEvents(List.of()), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            log.debug(
            "[UNIVERSAL_PERF] service path=raw totalMs={} rawQueryMs={} javaFilterMs={} stageBatchLoadMs=0 javaGroupingMs=0 attributesMs=0 responseBuildMs=0 includeEventStageBreakdown={} counts events=0 stages=0 eventStageBreakdown=0 attrs=0",
                elapsedMs(serviceStarted),
                rawQueryMs,
                javaFilterMs,
                includeEventStageBreakdown
            );
            return empty;
        }

        long stageBatchStarted = System.nanoTime();
        List<AnalyticsStage> stages = findStagesByEventIds(ids(events));
        long stageBatchLoadMs = elapsedMs(stageBatchStarted);
        if (!normalizedStageTypes.isEmpty()) {
            Set<Long> matchedEventIds = stages.stream()
                .filter(stage -> normalizedStageTypes.contains(normalizeCode(stage.getStageTypeCode())))
                .map(AnalyticsStage::getEventId)
                .collect(Collectors.toSet());
            events = events.stream()
                .filter(event -> matchedEventIds.contains(event.getId()))
                .toList();
            stages = stages.stream()
                .filter(stage -> normalizedStageTypes.contains(normalizeCode(stage.getStageTypeCode())))
                .toList();
        }

        if (events.isEmpty()) {
            UniversalResponse empty = new UniversalResponse(from, to, resolvedBucket, snapshotFromEvents(List.of()), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            log.debug(
            "[UNIVERSAL_PERF] service path=raw totalMs={} rawQueryMs={} javaFilterMs={} stageBatchLoadMs={} javaGroupingMs=0 attributesMs=0 responseBuildMs=0 includeEventStageBreakdown={} counts events=0 stages={} eventStageBreakdown=0 attrs=0",
                elapsedMs(serviceStarted),
                rawQueryMs,
                javaFilterMs,
                stageBatchLoadMs,
                includeEventStageBreakdown,
                stages.size()
            );
            return empty;
        }

        long javaGroupingStarted = System.nanoTime();
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

        Map<String, String> eventTypeNames = eventTypeNameMap();
        Map<Long, String> eventTypeByEventId = events.stream()
            .collect(Collectors.toMap(AnalyticsEvent::getId, AnalyticsEvent::getEventTypeCode, (first, second) -> first));
        KpiSnapshot totals;
        List<EventKpiDto> eventBreakdown;
        List<TimeSeriesPointDto> series;
        List<UniversalEventSeriesDto> eventSeries;
        if (!normalizedStageTypes.isEmpty()) {
            List<AnalyticsStage> stagesWithEventType = stages.stream()
                .filter(stage -> stage.getEventId() != null && eventTypeByEventId.containsKey(stage.getEventId()))
                .toList();
            totals = snapshotFromStages(stagesWithEventType);
            eventBreakdown = stagesWithEventType.stream()
                .collect(Collectors.groupingBy(stage -> eventTypeByEventId.get(stage.getEventId())))
                .entrySet()
                .stream()
                .map(entry -> {
                    String code = entry.getKey();
                    KpiSnapshot stat = snapshotFromStages(entry.getValue());
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
            series = buildSeries(
                from,
                to,
                resolvedBucket,
                stagesWithEventType,
                AnalyticsStage::getStartedAt,
                AnalyticsStage::getDurationMs,
                stage -> Boolean.TRUE.equals(stage.getIsError())
            );
            eventSeries = stagesWithEventType.stream()
                .collect(Collectors.groupingBy(stage -> eventTypeByEventId.get(stage.getEventId())))
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
                        AnalyticsStage::getStartedAt,
                        AnalyticsStage::getDurationMs,
                        stage -> Boolean.TRUE.equals(stage.getIsError())
                    )
                ))
                .sorted(Comparator.comparing(UniversalEventSeriesDto::eventTypeCode))
                .toList();
        } else {
            totals = snapshotFromEvents(events);
            eventBreakdown = events.stream()
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
            series = buildSeries(
                from,
                to,
                resolvedBucket,
                events,
                AnalyticsEvent::getStartedAt,
                AnalyticsEvent::getDurationMs,
                event -> Boolean.TRUE.equals(event.getIsError())
            );
            eventSeries = events.stream()
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
        }
        Map<String, List<AnalyticsStage>> stagesByEventType = stages.stream()
            .filter(stage -> stage.getEventId() != null && eventTypeByEventId.containsKey(stage.getEventId()))
            .collect(Collectors.groupingBy(stage -> eventTypeByEventId.get(stage.getEventId())));
        List<UniversalEventStageBreakdownDto> eventStageBreakdown = includeEventStageBreakdown
            ? stagesByEventType.entrySet().stream()
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
                .toList()
            : List.of();
        long javaGroupingMs = elapsedMs(javaGroupingStarted);

        Map<String, String> attributeTypeNames = eventAttributeTypeNameMap();
        long attributesStarted = System.nanoTime();
        List<OptionDto> availableAttributeTypes = findAttributesByEventIds(ids(events)).stream()
            .map(AnalyticsEventAttribute::getAttributeTypeCode)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(code -> !code.isBlank())
            .distinct()
            .map(code -> new OptionDto(code, attributeTypeNames.getOrDefault(code, code)))
            .sorted(Comparator.comparing(OptionDto::name))
            .toList();
        long attributesMs = elapsedMs(attributesStarted);

        long responseBuildStarted = System.nanoTime();
        UniversalResponse response = new UniversalResponse(
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
        long responseBuildMs = elapsedMs(responseBuildStarted);
        log.debug(
            "[UNIVERSAL_PERF] service path=raw totalMs={} rawQueryMs={} javaFilterMs={} stageBatchLoadMs={} javaGroupingMs={} attributesMs={} responseBuildMs={} includeEventStageBreakdown={} counts events={} stages={} series={} stageRows={} eventBreakdown={} eventSeries={} eventStageBreakdown={} attrs={}",
            elapsedMs(serviceStarted),
            rawQueryMs,
            javaFilterMs,
            stageBatchLoadMs,
            javaGroupingMs,
            attributesMs,
            responseBuildMs,
            includeEventStageBreakdown,
            events.size(),
            stages.size(),
            series.size(),
            stageRows.size(),
            eventBreakdown.size(),
            eventSeries.size(),
            eventStageBreakdown.size(),
            availableAttributeTypes.size()
        );
        return response;
    }

    public EventListResponse events(
        Instant from,
        Instant to,
        String moduleCode,
        List<String> eventTypeCode,
        String stageTypeCode,
        Boolean isError,
        String errorKey,
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
        boolean systemEventsOnly,
        Integer page,
        Integer size
    ) {
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        Set<String> normalizedEventTypes = normalizeCodes(eventTypeCode);
        String normalizedStageType = normalizeCode(stageTypeCode);
        String normalizedErrorKey = normalizeText(errorKey);
        String normalizedErrorClass = ErrorClassClassifier.normalizeFilterValue(errorClass);
        String normalizedAttributeCode = normalizeCode(attributeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        String normalizedAttributeValue = normalizeText(attributeValue);
        String normalizedMetricType = normalizeCode(metricTypeCode);
        String normalizedSortBy = normalizeEventSortBy(sortBy);
        boolean sortAscending = "asc".equalsIgnoreCase(normalizeText(sortDir));

        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? DEFAULT_EVENT_PAGE_SIZE : Math.min(size, MAX_EVENT_PAGE_SIZE);

        Integer safeMinDuration = minDurationMs != null && minDurationMs > 0 ? minDurationMs : null;
        BigDecimal safeMetricMin = metricMinValue;
        BigDecimal safeMetricMax = metricMaxValue;
        normalizedEventTypes = normalizedEventTypes.stream()
            .filter(code -> eventTypeMatchesSystemScope(code, systemEventsOnly))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (eventTypeCode != null && !eventTypeCode.isEmpty() && normalizedEventTypes.isEmpty()) {
            return new EventListResponse(0, safePage, safeSize, false, List.of());
        }
        boolean eventTypeFilterEnabled = !normalizedEventTypes.isEmpty();
        Set<String> eventTypeCodesForQuery = eventTypeFilterEnabled ? normalizedEventTypes : Set.of("__ALL__");

        List<AnalyticsEvent> eventRows = eventRepository.searchEventsScoped(
            from,
            to,
            eventTypeFilterEnabled,
            eventTypeCodesForQuery,
            normalizedModuleCode,
            normalizedStageType,
            isError,
            normalizedErrorKey,
            normalizedErrorClass,
            safeMinDuration,
            normalizedRequestPath,
            normalizedAttributeCode,
            normalizedAttributeValue,
            hasMetricFilter(normalizedMetricType, safeMetricMin, safeMetricMax),
            normalizedMetricType,
            safeMetricMin,
            safeMetricMax,
            systemEventsOnly,
            normalizedSortBy,
            sortAscending,
            PageRequest.of(safePage, safeSize + 1)
        );
        boolean hasMore = eventRows.size() > safeSize;
        List<AnalyticsEvent> events = hasMore ? eventRows.subList(0, safeSize) : eventRows;
        long totalElements = (long) safePage * safeSize + events.size() + (hasMore ? 1 : 0);
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

        String warning = hasMore
            ? "Список событий загружается постранично. Для больших периодов отображается текущая страница без полного пересчёта общего количества."
            : null;
        return new EventListResponse(totalElements, safePage, safeSize, hasMore, items, hasMore, warning);
    }

    private boolean eventTypeMatchesSystemScope(String eventTypeCode, boolean systemEventsOnly) {
        String normalized = normalizeCode(eventTypeCode);
        if (normalized == null) {
            return true;
        }
        return eventTypeRepository.findById(normalized)
            .map(type -> Boolean.TRUE.equals(type.getIsSystem()) == systemEventsOnly)
            .orElse(false);
    }

    private String normalizeEventSortBy(String sortBy) {
        String normalized = normalizeText(sortBy);
        if (normalized == null) {
            return "startedAt";
        }
        return switch (normalized) {
            case "startedAt", "durationMs", "statusCode", "eventTypeCode", "traceId", "requestPath", "isError", "metricValue" -> normalized;
            default -> "startedAt";
        };
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
        AnalyticsLogViewService.TraceLogLookupResult traceLogLookup = traceLogLookupService.loadTraceLogsSafely(event);
        List<EventLogEntryDto> traceLogs = traceLogLookup.rows();
        EventDurationBreakdownDto durationBreakdown = buildEventDurationBreakdown(event, stages, stageTypeNames);

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
            durationBreakdown,
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
            traceLogLookup.status(),
            traceLogs
        );
    }

    private EventDurationBreakdownDto buildEventDurationBreakdown(
        AnalyticsEvent event,
        List<AnalyticsStage> stages,
        Map<String, String> stageTypeNames
    ) {
        long eventDurationMs = resolveDurationMs(event.getStartedAt(), event.getEndedAt(), event.getDurationMs());
        List<StageInterval> stageIntervals = new ArrayList<>();
        long sumStageDurationMs = 0L;

        for (AnalyticsStage stage : stages) {
            TimeInterval interval = toStageInterval(stage);
            if (interval == null) {
                continue;
            }
            stageIntervals.add(new StageInterval(stage, interval.startedAt(), interval.endedAt()));
            sumStageDurationMs += interval.durationMs();
        }

        List<TimeInterval> coverageIntervals = stageIntervals.stream()
            .map(interval -> clipToEventWindow(interval.asTimeInterval(), event.getStartedAt(), event.getEndedAt()))
            .filter(Objects::nonNull)
            .toList();
        List<TimeInterval> mergedIntervals = mergeIntervalList(coverageIntervals);
        long coveredStageDurationMs = mergedIntervals.stream().mapToLong(TimeInterval::durationMs).sum();
        long timestampWindowDurationMs = positiveDuration(event.getStartedAt(), event.getEndedAt());
        long durationOutsideTimestampWindowMs = Math.max(0L, eventDurationMs - timestampWindowDurationMs);
        List<EventUnaccountedIntervalDto> unaccountedIntervals = buildUnaccountedIntervals(
            event.getStartedAt(),
            event.getEndedAt(),
            mergedIntervals
        );
        long firstStageOffsetMs = sumUnaccounted(unaccountedIntervals, "BEFORE_FIRST_STAGE");
        long betweenStagesMs = sumUnaccounted(unaccountedIntervals, "BETWEEN_STAGE_INTERVALS");
        long tailAfterLastStageMs = sumUnaccounted(unaccountedIntervals, "AFTER_LAST_STAGE");
        if (durationOutsideTimestampWindowMs > 0L) {
            unaccountedIntervals.add(new EventUnaccountedIntervalDto(
                "OUTSIDE_TIMESTAMP_WINDOW",
                "Разница между сохранённой duration и границами startedAt/finishedAt",
                null,
                null,
                durationOutsideTimestampWindowMs,
                timestampWindowDurationMs
            ));
        }
        long unaccountedDurationMs = Math.max(0L, eventDurationMs - coveredStageDurationMs);
        List<EventStageIntervalDto> stageIntervalDtos = stageIntervals.stream()
            .map(interval -> toStageIntervalDto(interval, stageIntervals, event.getStartedAt(), stageTypeNames))
            .toList();
        return new EventDurationBreakdownDto(
            eventDurationMs,
            sumStageDurationMs,
            coveredStageDurationMs,
            unaccountedDurationMs,
            firstStageOffsetMs,
            betweenStagesMs,
            tailAfterLastStageMs,
            timestampWindowDurationMs,
            durationOutsideTimestampWindowMs,
            stageIntervalDtos,
            unaccountedIntervals
        );
    }

    private EventStageIntervalDto toStageIntervalDto(
        StageInterval interval,
        List<StageInterval> intervals,
        Instant eventStartedAt,
        Map<String, String> stageTypeNames
    ) {
        StageInterval parent = intervals.stream()
            .filter(candidate -> candidate != interval)
            .filter(candidate -> candidate.contains(interval))
            .filter(candidate -> candidate.durationMs() > interval.durationMs())
            .min(Comparator.comparingLong(StageInterval::durationMs))
            .orElse(null);
        AnalyticsStage stage = interval.stage();
        return new EventStageIntervalDto(
            stage.getStageTypeCode(),
            stageTypeNames.getOrDefault(stage.getStageTypeCode(), stage.getStageTypeCode()),
            stage.getStageOrder(),
            interval.startedAt(),
            interval.endedAt(),
            interval.durationMs(),
            positiveDuration(eventStartedAt, interval.startedAt()),
            parent != null,
            parent == null ? null : parent.stage().getStageOrder()
        );
    }

    private List<EventUnaccountedIntervalDto> buildUnaccountedIntervals(
        Instant eventStartedAt,
        Instant eventEndedAt,
        List<TimeInterval> mergedIntervals
    ) {
        List<EventUnaccountedIntervalDto> result = new ArrayList<>();
        if (eventStartedAt == null || eventEndedAt == null || eventEndedAt.isBefore(eventStartedAt)) {
            return result;
        }
        if (mergedIntervals.isEmpty()) {
            long durationMs = positiveDuration(eventStartedAt, eventEndedAt);
            if (durationMs > 0L) {
                result.add(unaccountedInterval(
                    "OUTSIDE_INSTRUMENTED_STAGES",
                    "Время вне инструментированных этапов",
                    eventStartedAt,
                    eventEndedAt,
                    eventStartedAt
                ));
            }
            return result;
        }
        Instant cursor = eventStartedAt;
        boolean first = true;
        for (TimeInterval interval : mergedIntervals) {
            if (interval.startedAt().isAfter(cursor)) {
                result.add(unaccountedInterval(
                    first ? "BEFORE_FIRST_STAGE" : "BETWEEN_STAGE_INTERVALS",
                    first ? "До первого stage" : "Между stage intervals",
                    cursor,
                    interval.startedAt(),
                    eventStartedAt
                ));
            }
            if (interval.endedAt().isAfter(cursor)) {
                cursor = interval.endedAt();
            }
            first = false;
        }
        if (eventEndedAt.isAfter(cursor)) {
            result.add(unaccountedInterval(
                "AFTER_LAST_STAGE",
                "После последнего stage",
                cursor,
                eventEndedAt,
                eventStartedAt
            ));
        }
        return result;
    }

    private EventUnaccountedIntervalDto unaccountedInterval(
        String type,
        String label,
        Instant startedAt,
        Instant endedAt,
        Instant eventStartedAt
    ) {
        return new EventUnaccountedIntervalDto(
            type,
            label,
            startedAt,
            endedAt,
            positiveDuration(startedAt, endedAt),
            positiveDuration(eventStartedAt, startedAt)
        );
    }

    private long sumUnaccounted(List<EventUnaccountedIntervalDto> intervals, String type) {
        return intervals.stream()
            .filter(interval -> Objects.equals(type, interval.type()))
            .map(EventUnaccountedIntervalDto::durationMs)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .sum();
    }

    private TimeInterval toStageInterval(AnalyticsStage stage) {
        if (stage == null) {
            return null;
        }
        Instant startedAt = stage.getStartedAt();
        Instant endedAt = stage.getEndedAt();
        if (startedAt == null && endedAt == null) {
            return null;
        }
        if (startedAt == null) {
            startedAt = endedAt;
        }
        if (endedAt == null) {
            Integer durationMs = stage.getDurationMs();
            if (durationMs != null && durationMs >= 0) {
                endedAt = startedAt.plusMillis(durationMs.longValue());
            } else {
                endedAt = startedAt;
            }
        }
        if (endedAt.isBefore(startedAt)) {
            endedAt = startedAt;
        }
        return new TimeInterval(startedAt, endedAt);
    }

    private TimeInterval clipToEventWindow(TimeInterval interval, Instant eventStartedAt, Instant eventEndedAt) {
        if (interval == null) {
            return null;
        }
        if (eventStartedAt == null || eventEndedAt == null || eventEndedAt.isBefore(eventStartedAt)) {
            return interval;
        }
        Instant startedAt = interval.startedAt().isBefore(eventStartedAt) ? eventStartedAt : interval.startedAt();
        Instant endedAt = interval.endedAt().isAfter(eventEndedAt) ? eventEndedAt : interval.endedAt();
        if (endedAt.isBefore(startedAt)) {
            return null;
        }
        return new TimeInterval(startedAt, endedAt);
    }

    private List<TimeInterval> mergeIntervalList(List<TimeInterval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }
        List<TimeInterval> sorted = intervals.stream()
            .sorted(Comparator.comparing(TimeInterval::startedAt).thenComparing(TimeInterval::endedAt))
            .toList();
        List<TimeInterval> merged = new ArrayList<>();
        Instant currentStart = null;
        Instant currentEnd = null;
        for (TimeInterval interval : sorted) {
            if (interval == null) {
                continue;
            }
            if (currentStart == null) {
                currentStart = interval.startedAt();
                currentEnd = interval.endedAt();
                continue;
            }
            if (!interval.startedAt().isAfter(currentEnd)) {
                if (interval.endedAt().isAfter(currentEnd)) {
                    currentEnd = interval.endedAt();
                }
                continue;
            }
            merged.add(new TimeInterval(currentStart, currentEnd));
            currentStart = interval.startedAt();
            currentEnd = interval.endedAt();
        }
        if (currentStart != null && currentEnd != null) {
            merged.add(new TimeInterval(currentStart, currentEnd));
        }
        return merged;
    }

    private long resolveDurationMs(Instant startedAt, Instant endedAt, Integer storedDurationMs) {
        if (storedDurationMs != null && storedDurationMs >= 0) {
            return storedDurationMs.longValue();
        }
        if (startedAt == null || endedAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(startedAt, endedAt).toMillis());
    }

    private long positiveDuration(Instant from, Instant to) {
        if (from == null || to == null || to.isBefore(from)) {
            return 0L;
        }
        return Duration.between(from, to).toMillis();
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

    private record TimeInterval(Instant startedAt, Instant endedAt) {
        long durationMs() {
            return Math.max(0L, Duration.between(startedAt, endedAt).toMillis());
        }
    }

    private record StageInterval(AnalyticsStage stage, Instant startedAt, Instant endedAt) {
        long durationMs() {
            return Math.max(0L, Duration.between(startedAt, endedAt).toMillis());
        }

        TimeInterval asTimeInterval() {
            return new TimeInterval(startedAt, endedAt);
        }

        boolean contains(StageInterval other) {
            return other != null
                && !other.startedAt().isBefore(startedAt)
                && !other.endedAt().isAfter(endedAt);
        }
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
        long serviceStarted = System.nanoTime();
        String normalizedModuleCode = normalizeModuleFilterCode(moduleCode);
        String normalizedEventType = normalizeCode(eventTypeCode);
        String normalizedRequestPath = normalizeText(requestPath);
        Set<String> visibleEventCodes = scopedEventTypeCodes(false);
        if (normalizedEventType != null && !visibleEventCodes.contains(normalizedEventType)) {
            normalizedEventType = "__NO_MATCH__";
        }

        if (normalizedRequestPath == null) {
            int compareBucketMinutes = resolveCompareBucketMinutes(baselineFrom, baselineTo, targetFrom, targetTo);
            Set<String> eventTypeFilter = normalizedEventType == null
                ? nonEmptyEventTypeFilter(visibleEventCodes)
                : Set.of(normalizedEventType);

            long baselineLoadStarted = System.nanoTime();
            List<AnalyticsTimeRollupService.AggregatePoint> baselinePoints = timeRollupService.loadEventAggregatePoints(
                baselineFrom,
                baselineTo,
                normalizedModuleCode,
                eventTypeFilter,
                compareBucketMinutes
            );
            long baselineLoadMs = elapsedMs(baselineLoadStarted);

            long targetLoadStarted = System.nanoTime();
            List<AnalyticsTimeRollupService.AggregatePoint> targetPoints = timeRollupService.loadEventAggregatePoints(
                targetFrom,
                targetTo,
                normalizedModuleCode,
                eventTypeFilter,
                compareBucketMinutes
            );
            long targetLoadMs = elapsedMs(targetLoadStarted);

            long buildStarted = System.nanoTime();
            KpiSnapshot baseline = snapshotFromAccumulator(timeRollupService.accumulateAll(baselinePoints));
            KpiSnapshot target = snapshotFromAccumulator(timeRollupService.accumulateAll(targetPoints));
            KpiDelta delta = new KpiDelta(
                percentChange(baseline.count(), target.count()),
                percentChange(baseline.avgMs(), target.avgMs()),
                percentChange(baseline.p95Ms(), target.p95Ms()),
                percentChange(baseline.errorRate(), target.errorRate())
            );
            List<CompareEventRow> events = compareEventsByTypeFromPoints(baselinePoints, targetPoints);
            CompareResponse response = new CompareResponse(baselineFrom, baselineTo, baseline, targetFrom, targetTo, target, delta, events);

            log.debug(
            "[COMPARE_PERF] service path=aggregate totalMs={} baselineLoadMs={} targetLoadMs={} buildMs={} compareBucket={} module={} eventType={} requestPath={} baselinePoints={} targetPoints={} baselineCount={} targetCount={} rows={}",
                elapsedMs(serviceStarted),
                baselineLoadMs,
                targetLoadMs,
                elapsedMs(buildStarted),
                compareBucketMinutes,
                normalizedModuleCode,
                normalizedEventType,
                normalizedRequestPath,
                baselinePoints.size(),
                targetPoints.size(),
                baseline.count(),
                target.count(),
                events.size()
            );
            return response;
        }

        long baselineQueryStarted = System.nanoTime();
        List<AnalyticsEvent> baselineEvents = filterEventsByRequestPath(
            eventRepository.findAllByRangeOrdered(
                baselineFrom,
                baselineTo,
                normalizedEventType,
                normalizedModuleCode
            ),
            normalizedRequestPath
        );
        baselineEvents = filterEventsByScope(baselineEvents, false);
        long baselineQueryMs = elapsedMs(baselineQueryStarted);

        long targetQueryStarted = System.nanoTime();
        List<AnalyticsEvent> targetEvents = filterEventsByRequestPath(
            eventRepository.findAllByRangeOrdered(
                targetFrom,
                targetTo,
                normalizedEventType,
                normalizedModuleCode
            ),
            normalizedRequestPath
        );
        targetEvents = filterEventsByScope(targetEvents, false);
        long targetQueryMs = elapsedMs(targetQueryStarted);

        long buildStarted = System.nanoTime();
        KpiSnapshot baseline = snapshotFromEvents(baselineEvents);
        KpiSnapshot target = snapshotFromEvents(targetEvents);
        KpiDelta delta = new KpiDelta(
            percentChange(baseline.count(), target.count()),
            percentChange(baseline.avgMs(), target.avgMs()),
            percentChange(baseline.p95Ms(), target.p95Ms()),
            percentChange(baseline.errorRate(), target.errorRate())
        );
        List<CompareEventRow> events = compareEventsByType(baselineEvents, targetEvents);
        CompareResponse response = new CompareResponse(baselineFrom, baselineTo, baseline, targetFrom, targetTo, target, delta, events);

        log.debug(
            "[COMPARE_PERF] service path=raw totalMs={} baselineQueryMs={} targetQueryMs={} buildMs={} module={} eventType={} requestPath={} baselineCount={} targetCount={} rows={}",
            elapsedMs(serviceStarted),
            baselineQueryMs,
            targetQueryMs,
            elapsedMs(buildStarted),
            normalizedModuleCode,
            normalizedEventType,
            normalizedRequestPath,
            baselineEvents.size(),
            targetEvents.size(),
            events.size()
        );
        return response;
    }

    private List<CompareEventRow> compareEventsByTypeFromPoints(
        List<AnalyticsTimeRollupService.AggregatePoint> baselinePoints,
        List<AnalyticsTimeRollupService.AggregatePoint> targetPoints
    ) {
        Map<String, String> eventTypeNames = eventTypeNameMap();
        Map<String, AnalyticsTimeRollupService.AnalyticsAccumulator> baselineByCode = timeRollupService.accumulateByCode(baselinePoints);
        Map<String, AnalyticsTimeRollupService.AnalyticsAccumulator> targetByCode = timeRollupService.accumulateByCode(targetPoints);
        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(baselineByCode.keySet());
        codes.addAll(targetByCode.keySet());
        return codes.stream()
            .map(code -> {
                KpiSnapshot baseline = snapshotFromAccumulator(baselineByCode.get(code));
                KpiSnapshot target = snapshotFromAccumulator(targetByCode.get(code));
                return new CompareEventRow(
                    code,
                    eventTypeNames.getOrDefault(code, code),
                    baseline,
                    target,
                    new KpiDelta(
                        percentChange(baseline.count(), target.count()),
                        percentChange(baseline.avgMs(), target.avgMs()),
                        percentChange(baseline.p95Ms(), target.p95Ms()),
                        percentChange(baseline.errorRate(), target.errorRate())
                    ),
                    target.count() - baseline.count(),
                    target.errorCount() - baseline.errorCount()
                );
            })
            .toList();
    }

    private List<CompareEventRow> compareEventsByType(List<AnalyticsEvent> baselineEvents, List<AnalyticsEvent> targetEvents) {
        Map<String, String> eventTypeNames = eventTypeNameMap();
        Map<String, List<AnalyticsEvent>> baselineByCode = groupEventsByType(baselineEvents);
        Map<String, List<AnalyticsEvent>> targetByCode = groupEventsByType(targetEvents);
        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(baselineByCode.keySet());
        codes.addAll(targetByCode.keySet());
        return codes.stream()
            .map(code -> {
                List<AnalyticsEvent> baselineGroup = baselineByCode.getOrDefault(code, List.of());
                List<AnalyticsEvent> targetGroup = targetByCode.getOrDefault(code, List.of());
                KpiSnapshot baseline = snapshotFromEvents(baselineGroup);
                KpiSnapshot target = snapshotFromEvents(targetGroup);
                return new CompareEventRow(
                    code,
                    eventTypeNames.getOrDefault(code, code),
                    baseline,
                    target,
                    new KpiDelta(
                        percentChange(baseline.count(), target.count()),
                        percentChange(baseline.avgMs(), target.avgMs()),
                        percentChange(baseline.p95Ms(), target.p95Ms()),
                        percentChange(baseline.errorRate(), target.errorRate())
                    ),
                    target.count() - baseline.count(),
                    target.errorCount() - baseline.errorCount()
                );
            })
            .toList();
    }

    private int resolveCompareBucketMinutes(
        Instant baselineFrom,
        Instant baselineTo,
        Instant targetFrom,
        Instant targetTo
    ) {
        Duration baselineDuration = Duration.between(baselineFrom, baselineTo);
        Duration targetDuration = Duration.between(targetFrom, targetTo);
        Duration effectiveDuration = baselineDuration.compareTo(targetDuration) >= 0 ? baselineDuration : targetDuration;
        if (effectiveDuration.isZero() || effectiveDuration.isNegative()) {
            effectiveDuration = Duration.ofHours(24);
        }
        Instant syntheticFrom = Instant.EPOCH;
        Instant syntheticTo = syntheticFrom.plus(effectiveDuration);
        return resolveBucketMinutes(syntheticFrom, syntheticTo, null);
    }

    private Map<String, List<AnalyticsEvent>> groupEventsByType(List<AnalyticsEvent> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }
        return events.stream()
            .filter(event -> event.getEventTypeCode() != null && !event.getEventTypeCode().isBlank())
            .collect(Collectors.groupingBy(
                event -> event.getEventTypeCode().trim(),
                LinkedHashMap::new,
                Collectors.toList()
            ));
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

    private KpiSnapshot snapshotFromAccumulator(AnalyticsTimeRollupService.AnalyticsAccumulator accumulator) {
        if (accumulator == null) {
            return new KpiSnapshot(
                0,
                0,
                0,
                BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
            );
        }
        long count = accumulator.sampleCount();
        long errors = Math.max(0L, accumulator.errorCount());
        return new KpiSnapshot(
            count,
            errors,
            Math.max(0L, count - errors),
            accumulator.errorRate(),
            accumulator.avgMs(),
            accumulator.p95Ms(),
            accumulator.p99Ms(),
            accumulator.maxMs()
        );
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
            result.add(new TimeSeriesPointDto(
                AnalyticsSeriesTime.displayTimeForBucket(bucket, to, stepSeconds),
                count,
                avg,
                p95,
                p99,
                errorRate
            ));
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

    private boolean canUseTimeRollup(
        String normalizedRequestPath,
        String filterMetricTypeCode,
        String filterMetricValue,
        BigDecimal filterMetricMinValue,
        BigDecimal filterMetricMaxValue,
        String filterAttributeCode,
        String filterAttributeValue,
        BigDecimal filterAttributeMinValue,
        BigDecimal filterAttributeMaxValue
    ) {
        if (!timeRollupService.isEnabled()) {
            return false;
        }
        if (normalizedRequestPath != null) {
            return false;
        }
        return normalizeCode(filterMetricTypeCode) == null
            && normalizeText(filterMetricValue) == null
            && filterMetricMinValue == null
            && filterMetricMaxValue == null
            && normalizeCode(filterAttributeCode) == null
            && normalizeText(filterAttributeValue) == null
            && filterAttributeMinValue == null
            && filterAttributeMaxValue == null;
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

    private boolean shouldTreatMetricAsText(String metricTypeCode) {
        String normalized = normalizeCode(metricTypeCode);
        if (normalized == null) {
            return false;
        }
        return normalized.contains("HTTP_STATUS");
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

    private List<OptionDto> availableAttributeTypesForRollup(
        Instant from,
        Instant to,
        String normalizedModuleCode,
        Set<String> normalizedEventTypes,
        Map<String, String> attributeTypeNames
    ) {
        Set<String> codes = new LinkedHashSet<>();
        if (normalizedEventTypes == null || normalizedEventTypes.isEmpty()) {
            codes.addAll(eventAttributeRepository.findDistinctAttributeTypeCodesByScopeNoPath(
                from,
                to,
                normalizedModuleCode,
                null
            ));
        } else if (normalizedEventTypes.size() == 1) {
            String eventType = normalizedEventTypes.iterator().next();
            codes.addAll(eventAttributeRepository.findDistinctAttributeTypeCodesByScopeNoPath(
                from,
                to,
                normalizedModuleCode,
                eventType
            ));
        } else {
            for (String eventType : normalizedEventTypes) {
                codes.addAll(eventAttributeRepository.findDistinctAttributeTypeCodesByScopeNoPath(
                    from,
                    to,
                    normalizedModuleCode,
                    eventType
                ));
            }
        }

        return codes.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(code -> !code.isBlank())
            .map(code -> new OptionDto(code, attributeTypeNames.getOrDefault(code, code)))
            .sorted(Comparator.comparing(OptionDto::name))
            .toList();
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

    private Set<String> userEventTypeCodes() {
        return eventTypeRepository.findByIsActiveTrueAndIsSystemFalseOrderByNameAsc().stream()
            .map(EventType::getCode)
            .map(this::normalizeCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> scopedEventTypeCodes(boolean systemEventsOnly) {
        if (systemEventsOnly) {
            return hiddenSystemEventTypeCodes();
        }
        return userEventTypeCodes();
    }

    private Set<String> nonEmptyEventTypeFilter(Set<String> eventTypeCodes) {
        if (eventTypeCodes == null || eventTypeCodes.isEmpty()) {
            return Set.of("__NO_MATCH__");
        }
        return eventTypeCodes;
    }

    private List<AnalyticsEvent> filterEventsByScope(List<AnalyticsEvent> events, boolean systemEventsOnly) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        Set<String> scopedEventCodes = scopedEventTypeCodes(systemEventsOnly);
        return events.stream()
            .filter(event -> scopedEventCodes.contains(normalizeCode(event.getEventTypeCode())))
            .toList();
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

    private Map<String, MetricValueKind> stageMetricTypeValueKindMap() {
        return stageMetricTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
            .collect(Collectors.toMap(
                type -> type.getCode(),
                type -> Objects.requireNonNullElse(type.getValueKind(), MetricValueKind.NUMERIC),
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

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private void logStageMetricsServicePerf(
        long serviceStarted,
        String path,
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String stageTypeCode,
        String metricTypeCode,
        int bucketMinutes,
        boolean includeSummaries,
        boolean includeTopValues,
        long rollupQueryMs,
        long rawQueryMs,
        long groupingMs,
        long buildMs,
        long seriesMs,
        long topValuesMs,
        int summariesCount,
        int numericSeriesCount,
        int topValuesCount
    ) {
        log.debug(
            "[STAGE_METRICS_PERF] service path={} totalMs={} from={} to={} module={} eventType={} stage={} metric={} bucket={} includeSummaries={} includeTopValues={} rollupQueryMs={} rawQueryMs={} groupingMs={} buildMs={} seriesMs={} topValuesMs={} counts summaries={} series={} topValues={}",
            path,
            elapsedMs(serviceStarted),
            from,
            to,
            moduleCode,
            eventTypeCode,
            stageTypeCode,
            metricTypeCode,
            bucketMinutes,
            includeSummaries,
            includeTopValues,
            rollupQueryMs,
            rawQueryMs,
            groupingMs,
            buildMs,
            seriesMs,
            topValuesMs,
            summariesCount,
            numericSeriesCount,
            topValuesCount
        );
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

    private record ReadWindow(
        Instant from,
        Instant to,
        boolean partial,
        String warning
    ) {
    }
}

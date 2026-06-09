package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import com.example.gqw.analytics.entity.AnalyticsStage;
import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import com.example.gqw.analytics.entity.EventAttributeType;
import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.entity.StageMetricType;
import com.example.gqw.analytics.entity.StageType;
import com.example.gqw.analytics.repository.AnalyticsEventAttributeRepository;
import com.example.gqw.analytics.repository.AnalyticsStageMetricRepository;
import com.example.gqw.analytics.repository.AnalyticsStageRepository;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import com.example.gqw.analytics.repository.StageTypeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsEventSnapshotPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventSnapshotPersistenceService.class);

    private final AnalyticsEventService eventService;
    private final AnalyticsStageRepository stageRepository;
    private final AnalyticsEventAttributeRepository attributeRepository;
    private final AnalyticsStageMetricRepository metricRepository;
    private final StageTypeRepository stageTypeRepository;
    private final EventAttributeTypeRepository attributeTypeRepository;
    private final StageMetricTypeRepository metricTypeRepository;
    private final AnalyticsCodeResolverService codeResolverService;
    private final AnalyticsLoggingPolicy loggingPolicy;
    private final AnalyticsStrictWarningEventService strictWarningEventService;

    public AnalyticsEventSnapshotPersistenceService(
        AnalyticsEventService eventService,
        AnalyticsStageRepository stageRepository,
        AnalyticsEventAttributeRepository attributeRepository,
        AnalyticsStageMetricRepository metricRepository,
        StageTypeRepository stageTypeRepository,
        EventAttributeTypeRepository attributeTypeRepository,
        StageMetricTypeRepository metricTypeRepository,
        AnalyticsCodeResolverService codeResolverService,
        AnalyticsLoggingPolicy loggingPolicy,
        AnalyticsStrictWarningEventService strictWarningEventService
    ) {
        this.eventService = eventService;
        this.stageRepository = stageRepository;
        this.attributeRepository = attributeRepository;
        this.metricRepository = metricRepository;
        this.stageTypeRepository = stageTypeRepository;
        this.attributeTypeRepository = attributeTypeRepository;
        this.metricTypeRepository = metricTypeRepository;
        this.codeResolverService = codeResolverService;
        this.loggingPolicy = loggingPolicy;
        this.strictWarningEventService = strictWarningEventService;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void persist(AnalyticsEventSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        var event = eventService.createEvent(
            snapshot.eventUid(),
            snapshot.eventTypeCode(),
            snapshot.userId(),
            snapshot.sessionId(),
            snapshot.requestPath(),
            snapshot.httpMethod(),
            snapshot.traceId(),
            snapshot.startedAt()
        );

        Map<Long, Long> stageIds = persistStages(event.getId(), snapshot.stages());
        persistAttributes(event.getId(), snapshot.attributes());
        persistMetrics(stageIds, snapshot.stages());

        Instant endedAt = snapshot.endedAt() == null ? Instant.now() : snapshot.endedAt();
        if (snapshot.error()) {
            eventService.finishEventErrorAt(snapshot.eventUid(), snapshot.statusCode(), snapshot.errorMessage(), endedAt);
        } else {
            eventService.finishEventSuccessAt(snapshot.eventUid(), snapshot.statusCode(), endedAt);
        }
    }

    private Map<Long, Long> persistStages(Long eventId, List<AnalyticsEventSnapshot.StageSnapshot> stages) {
        if (eventId == null || stages == null || stages.isEmpty()) {
            return Map.of();
        }
        Map<String, StageType> types = stageTypes(stages);
        List<AnalyticsStage> entities = new ArrayList<>();
        List<Long> localIds = new ArrayList<>();
        for (AnalyticsEventSnapshot.StageSnapshot stage : stages) {
            String code = normalizeStageCode(stage.stageTypeCode());
            StageType type = types.get(code);
            if (type == null) {
                logStageSkipped(code, "Unknown stage type");
                continue;
            }
            if (!Boolean.TRUE.equals(type.getIsActive())) {
                logStageSkipped(code, "Inactive stage type");
                continue;
            }
            AnalyticsStage entity = new AnalyticsStage();
            entity.setEventId(eventId);
            entity.setStageTypeCode(code);
            entity.setStageOrder(stage.stageOrder());
            entity.setStartedAt(stage.startedAt() == null ? Instant.now() : stage.startedAt());
            entity.setEndedAt(stage.endedAt());
            entity.setDurationMs(stage.durationMs());
            entity.setLogStartedAt(stage.logStartedAt());
            entity.setLogEndedAt(stage.logEndedAt());
            entity.setIsError(stage.error());
            entity.setErrorMessage(stage.errorMessage());
            entities.add(entity);
            localIds.add(stage.localStageId());
        }
        List<AnalyticsStage> saved = stageRepository.saveAll(entities);
        Map<Long, Long> result = new HashMap<>();
        for (int i = 0; i < saved.size(); i++) {
            result.put(localIds.get(i), saved.get(i).getId());
        }
        return result;
    }

    private void persistAttributes(
        Long eventId,
        List<AnalyticsEventSnapshot.EventAttributeSnapshot> attributes
    ) {
        if (eventId == null || attributes == null || attributes.isEmpty()) {
            return;
        }
        Map<String, EventAttributeType> types = attributeTypes(attributes);
        List<AnalyticsEventAttribute> entities = new ArrayList<>();
        for (AnalyticsEventSnapshot.EventAttributeSnapshot attribute : attributes) {
            String code = codeResolverService.resolveAttributeTypeCode(attribute.attributeTypeCode());
            EventAttributeType type = types.get(code);
            if (type == null) {
                logAttributeSkipped(code, "Unknown attribute type");
                continue;
            }
            if (!Boolean.TRUE.equals(type.getIsActive())) {
                logAttributeSkipped(code, "Inactive attribute type");
                continue;
            }
            if (!attribute.json() && type.getValueKind() == MetricValueKind.NUMERIC) {
                logAttributeSkipped(code, "Attribute type is not text");
                continue;
            }
            AnalyticsEventAttribute entity = new AnalyticsEventAttribute();
            entity.setEventId(eventId);
            entity.setAttributeTypeCode(code);
            if (attribute.json()) {
                entity.setAttrValueJson(attribute.value());
            } else {
                entity.setAttrValue(attribute.value());
            }
            entity.setCreatedAt(Instant.now());
            entities.add(entity);
        }
        attributeRepository.saveAll(entities);
    }

    private void persistMetrics(Map<Long, Long> stageIds, List<AnalyticsEventSnapshot.StageSnapshot> stages) {
        if (stageIds == null || stageIds.isEmpty() || stages == null || stages.isEmpty()) {
            return;
        }
        List<AnalyticsEventSnapshot.StageMetricSnapshot> metricSnapshots = stages.stream()
            .flatMap(stage -> stage.metrics().stream())
            .toList();
        if (metricSnapshots.isEmpty()) {
            return;
        }
        Map<String, StageMetricType> types = metricTypes(metricSnapshots);
        Map<String, AnalyticsStageMetric> deduped = new LinkedHashMap<>();
        for (AnalyticsEventSnapshot.StageMetricSnapshot metric : metricSnapshots) {
            Long realStageId = stageIds.get(metric.localStageId());
            if (realStageId == null) {
                continue;
            }
            String code = codeResolverService.resolveMetricTypeCode(metric.metricTypeCode());
            StageMetricType type = types.get(code);
            if (type == null) {
                logMetricSkipped(code, realStageId, "Unknown metric type");
                continue;
            }
            if (!Boolean.TRUE.equals(type.getIsActive())) {
                logMetricSkipped(code, realStageId, "Inactive metric type");
                continue;
            }
            if (metric.numeric() && type.getValueKind() != MetricValueKind.NUMERIC) {
                logMetricSkipped(code, realStageId, "Metric type is not numeric");
                continue;
            }
            if (!metric.numeric() && type.getValueKind() != MetricValueKind.TEXT) {
                logMetricSkipped(code, realStageId, "Metric type is not text");
                continue;
            }
            AnalyticsStageMetric entity = new AnalyticsStageMetric();
            entity.setStageId(realStageId);
            entity.setMetricTypeCode(code);
            entity.setMetricValueNum(metric.numeric() ? metric.numericValue() : null);
            entity.setMetricValueText(metric.numeric() ? null : metric.textValue());
            entity.setUnit(metric.unit() != null ? metric.unit() : type.getUnitDefault());
            entity.setRecordedAt(metric.recordedAt() == null ? Instant.now() : metric.recordedAt());
            deduped.put(realStageId + "|" + code, entity);
        }
        metricRepository.saveAll(deduped.values());
    }

    private Map<String, StageType> stageTypes(List<AnalyticsEventSnapshot.StageSnapshot> stages) {
        Set<String> codes = stages.stream()
            .map(AnalyticsEventSnapshot.StageSnapshot::stageTypeCode)
            .map(this::normalizeStageCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return stageTypeRepository.findAllById(codes).stream()
            .collect(Collectors.toMap(StageType::getCode, Function.identity()));
    }

    private Map<String, EventAttributeType> attributeTypes(
        List<AnalyticsEventSnapshot.EventAttributeSnapshot> attributes
    ) {
        Set<String> codes = attributes.stream()
            .map(AnalyticsEventSnapshot.EventAttributeSnapshot::attributeTypeCode)
            .map(codeResolverService::resolveAttributeTypeCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return attributeTypeRepository.findAllById(codes).stream()
            .collect(Collectors.toMap(EventAttributeType::getCode, Function.identity()));
    }

    private Map<String, StageMetricType> metricTypes(
        List<AnalyticsEventSnapshot.StageMetricSnapshot> metrics
    ) {
        Set<String> codes = metrics.stream()
            .map(AnalyticsEventSnapshot.StageMetricSnapshot::metricTypeCode)
            .map(codeResolverService::resolveMetricTypeCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return metricTypeRepository.findAllById(codes).stream()
            .collect(Collectors.toMap(StageMetricType::getCode, Function.identity()));
    }

    private String normalizeStageCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void logStageSkipped(String code, String reason) {
        if (loggingPolicy.isStrictWarningsEnabled()) {
            log.warn("Analytics stage skipped: stageType={} reason={}", code, reason);
        }
        strictWarningEventService.record(
            "stage",
            code,
            reason,
            AnalyticsEventSnapshotPersistenceService.class.getSimpleName(),
            "persistStages",
            null,
            null,
            null
        );
    }

    private void logAttributeSkipped(String code, String reason) {
        if (loggingPolicy.isStrictWarningsEnabled()) {
            log.warn("Analytics attribute skipped: code={} reason={}", code, reason);
        }
        strictWarningEventService.record(
            "attribute",
            code,
            reason,
            AnalyticsEventSnapshotPersistenceService.class.getSimpleName(),
            "persistAttributes",
            null,
            null,
            null
        );
    }

    private void logMetricSkipped(String code, Long stageId, String reason) {
        if (loggingPolicy.isStrictWarningsEnabled()) {
            log.warn("Analytics metric skipped: code={} stageId={} reason={}", code, stageId, reason);
        }
        strictWarningEventService.record(
            "metric",
            code,
            reason,
            AnalyticsEventSnapshotPersistenceService.class.getSimpleName(),
            "persistMetrics",
            null,
            null,
            stageId
        );
    }
}

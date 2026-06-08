package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.entity.StageMetricType;
import com.example.gqw.analytics.repository.AnalyticsStageMetricRepository;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsStageMetricService {

    private final AnalyticsStageMetricRepository stageMetricRepository;
    private final StageMetricTypeRepository stageMetricTypeRepository;
    private final AnalyticsCodeResolverService codeResolverService;

    public AnalyticsStageMetricService(
        AnalyticsStageMetricRepository stageMetricRepository,
        StageMetricTypeRepository stageMetricTypeRepository,
        AnalyticsCodeResolverService codeResolverService
    ) {
        this.stageMetricRepository = stageMetricRepository;
        this.stageMetricTypeRepository = stageMetricTypeRepository;
        this.codeResolverService = codeResolverService;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void recordMetricNum(Long stageId, String metricTypeCode, BigDecimal value, String unit) {
        String resolvedCode = codeResolverService.resolveMetricTypeCode(metricTypeCode);
        StageMetricType type = stageMetricTypeRepository.findById(resolvedCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown metric type: " + metricTypeCode));
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            throw new IllegalArgumentException("Inactive metric type: " + resolvedCode);
        }
        if (type.getValueKind() != MetricValueKind.NUMERIC) {
            throw new IllegalArgumentException("Metric type is not numeric: " + resolvedCode);
        }

        AnalyticsStageMetric metric = stageMetricRepository.findByStageIdAndMetricTypeCode(stageId, resolvedCode)
            .orElseGet(AnalyticsStageMetric::new);
        metric.setStageId(stageId);
        metric.setMetricTypeCode(resolvedCode);
        metric.setMetricValueNum(value);
        metric.setMetricValueText(null);
        metric.setUnit(unit != null ? unit : type.getUnitDefault());
        metric.setRecordedAt(Instant.now());
        stageMetricRepository.save(metric);
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void recordMetricText(Long stageId, String metricTypeCode, String value, String unit) {
        String resolvedCode = codeResolverService.resolveMetricTypeCode(metricTypeCode);
        StageMetricType type = stageMetricTypeRepository.findById(resolvedCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown metric type: " + metricTypeCode));
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            throw new IllegalArgumentException("Inactive metric type: " + resolvedCode);
        }
        if (type.getValueKind() != MetricValueKind.TEXT) {
            throw new IllegalArgumentException("Metric type is not text: " + resolvedCode);
        }

        AnalyticsStageMetric metric = stageMetricRepository.findByStageIdAndMetricTypeCode(stageId, resolvedCode)
            .orElseGet(AnalyticsStageMetric::new);
        metric.setStageId(stageId);
        metric.setMetricTypeCode(resolvedCode);
        metric.setMetricValueNum(null);
        metric.setMetricValueText(value);
        metric.setUnit(unit != null ? unit : type.getUnitDefault());
        metric.setRecordedAt(Instant.now());
        stageMetricRepository.save(metric);
    }
}


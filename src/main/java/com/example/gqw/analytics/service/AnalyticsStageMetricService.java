package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.entity.StageMetricType;
import com.example.gqw.analytics.repository.AnalyticsStageMetricRepository;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsStageMetricService {

    private final AnalyticsStageMetricRepository stageMetricRepository;
    private final StageMetricTypeRepository stageMetricTypeRepository;

    public AnalyticsStageMetricService(
        AnalyticsStageMetricRepository stageMetricRepository,
        StageMetricTypeRepository stageMetricTypeRepository
    ) {
        this.stageMetricRepository = stageMetricRepository;
        this.stageMetricTypeRepository = stageMetricTypeRepository;
    }

    @Transactional
    public void recordMetricNum(Long stageId, String metricTypeCode, BigDecimal value, String unit) {
        StageMetricType type = stageMetricTypeRepository.findById(metricTypeCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown metric type: " + metricTypeCode));
        if (type.getValueKind() != MetricValueKind.NUMERIC) {
            throw new IllegalArgumentException("Metric type is not numeric: " + metricTypeCode);
        }

        AnalyticsStageMetric metric = stageMetricRepository.findByStageIdAndMetricTypeCode(stageId, metricTypeCode)
            .orElseGet(AnalyticsStageMetric::new);
        metric.setStageId(stageId);
        metric.setMetricTypeCode(metricTypeCode);
        metric.setMetricValueNum(value);
        metric.setMetricValueText(null);
        metric.setUnit(unit != null ? unit : type.getUnitDefault());
        metric.setRecordedAt(Instant.now());
        stageMetricRepository.save(metric);
    }

    @Transactional
    public void recordMetricText(Long stageId, String metricTypeCode, String value, String unit) {
        StageMetricType type = stageMetricTypeRepository.findById(metricTypeCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown metric type: " + metricTypeCode));
        if (type.getValueKind() != MetricValueKind.TEXT) {
            throw new IllegalArgumentException("Metric type is not text: " + metricTypeCode);
        }

        AnalyticsStageMetric metric = stageMetricRepository.findByStageIdAndMetricTypeCode(stageId, metricTypeCode)
            .orElseGet(AnalyticsStageMetric::new);
        metric.setStageId(stageId);
        metric.setMetricTypeCode(metricTypeCode);
        metric.setMetricValueNum(null);
        metric.setMetricValueText(value);
        metric.setUnit(unit != null ? unit : type.getUnitDefault());
        metric.setRecordedAt(Instant.now());
        stageMetricRepository.save(metric);
    }
}


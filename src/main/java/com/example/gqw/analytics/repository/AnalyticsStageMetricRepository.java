package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsStageMetricRepository extends JpaRepository<AnalyticsStageMetric, Long> {

    List<AnalyticsStageMetric> findByStageId(Long stageId);

    Optional<AnalyticsStageMetric> findByStageIdAndMetricTypeCode(Long stageId, String metricTypeCode);
}


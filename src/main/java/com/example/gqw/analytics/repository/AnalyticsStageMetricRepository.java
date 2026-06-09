package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsStageMetricRepository extends JpaRepository<AnalyticsStageMetric, Long> {

    List<AnalyticsStageMetric> findByStageId(Long stageId);

    List<AnalyticsStageMetric> findByStageIdIn(Collection<Long> stageIds);

    List<AnalyticsStageMetric> findByStageIdInAndMetricTypeCode(Collection<Long> stageIds, String metricTypeCode);

    Optional<AnalyticsStageMetric> findByStageIdAndMetricTypeCode(Long stageId, String metricTypeCode);

    long countByMetricTypeCode(String metricTypeCode);

    @Query("""
        select sm from AnalyticsStageMetric sm
        join AnalyticsStage s on s.id = sm.stageId
        join AnalyticsEvent e on e.id = s.eventId
        where e.startedAt between :from and :to
          and (:moduleCode is null or e.moduleCode = :moduleCode)
          and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
          and (:stageTypeCode is null or s.stageTypeCode = :stageTypeCode)
          and (:metricTypeCode is null or sm.metricTypeCode = :metricTypeCode)
        """)
    List<AnalyticsStageMetric> findByScope(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("moduleCode") String moduleCode,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("stageTypeCode") String stageTypeCode,
        @Param("metricTypeCode") String metricTypeCode
    );
}


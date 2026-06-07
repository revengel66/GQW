package com.example.gqw;

import com.example.gqw.analytics.aop.TrackAnalyticsStageMetric;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnalyticsStageMetricProbeRepository extends JpaRepository<AnalyticsStageMetricProbe, Long> {

    @TrackAnalyticsStageMetric(code = "ANNOTATION_DB_METRIC", value = "#result.size()", unit = "count")
    List<AnalyticsStageMetricProbe> findTop10ByOrderByNameAsc();
}

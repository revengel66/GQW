package com.example.gqw;

import com.example.gqw.analytics.aop.TrackAnalyticsStageMetric;
import com.example.gqw.analytics.aop.TrackAnalyticsMetric;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class AnalyticsStageMetricTestService {

    private final AnalyticsStageMetricProbeRepository probeRepository;

    AnalyticsStageMetricTestService(AnalyticsStageMetricProbeRepository probeRepository) {
        this.probeRepository = probeRepository;
    }

    @TrackAnalyticsStageMetric(metrics = {
        @TrackAnalyticsMetric(code = "ANNOTATION_SERVICE_METRIC", value = "#result.size()", unit = "count"),
        @TrackAnalyticsMetric(code = "ANNOTATION_TEXT_METRIC", value = "'service-stage'")
    })
    public List<String> serviceMetric() {
        return List.of("one", "two", "three");
    }

    public List<AnalyticsStageMetricProbe> databaseMetric() {
        if (probeRepository.count() == 0) {
            probeRepository.saveAll(List.of(
                new AnalyticsStageMetricProbe("one"),
                new AnalyticsStageMetricProbe("two")
            ));
        }
        return probeRepository.findTop10ByOrderByNameAsc();
    }

    @TrackAnalyticsStageMetric(code = "UNKNOWN_STAGE_METRIC_FOR_TEST", value = "'7'")
    public String unknownMetric() {
        return "ok";
    }

    @TrackAnalyticsStageMetric(code = "ANNOTATION_SERVICE_METRIC", value = "#missing.value")
    public String spelErrorMetric() {
        return "ok";
    }
}

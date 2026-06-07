package com.example.gqw;

import com.example.gqw.analytics.aop.TrackAnalyticsLayer;
import com.example.gqw.analytics.aop.TrackAnalyticsMetric;
import com.example.gqw.analytics.aop.TrackAnalyticsStageMetric;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@TrackAnalyticsLayer(code = "FACADE")
class AnalyticsLayerTestFacade {

    private final AnalyticsStageMetricTestService stageMetricTestService;

    AnalyticsLayerTestFacade(AnalyticsStageMetricTestService stageMetricTestService) {
        this.stageMetricTestService = stageMetricTestService;
    }

    public List<AnalyticsStageMetricProbe> facadeWithDatabase() {
        return stageMetricTestService.databaseMetric();
    }

    @TrackAnalyticsStageMetric(metrics = {
        @TrackAnalyticsMetric(code = "ANNOTATION_SERVICE_METRIC", value = "#result.size()", unit = "count"),
        @TrackAnalyticsMetric(code = "ANNOTATION_TEXT_METRIC", value = "'facade-stage'")
    })
    public List<String> facadeMetric() {
        return List.of("one", "two", "three", "four");
    }

    @TrackAnalyticsLayer(code = "UNKNOWN_LAYER_FOR_TEST")
    public String unknownLayer() {
        return "ok";
    }

    @TrackAnalyticsLayer(code = "INACTIVE_LAYER_FOR_TEST")
    public String inactiveLayer() {
        return "ok";
    }
}

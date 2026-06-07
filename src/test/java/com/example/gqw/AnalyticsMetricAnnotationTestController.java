package com.example.gqw;

import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.analytics.aop.TrackAnalyticsMetric;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsMetricAnnotationTestController {

    private final AnalyticsStageMetricTestService stageMetricTestService;

    AnalyticsMetricAnnotationTestController(AnalyticsStageMetricTestService stageMetricTestService) {
        this.stageMetricTestService = stageMetricTestService;
    }

    @GetMapping("/test/analytics/metrics/success")
    @TrackAnalyticsEvent(
        code = "ANNOTATION_METRIC_EVENT",
        metrics = {
            @TrackAnalyticsMetric(code = "ANNOTATION_NUMERIC_METRIC", value = "'42.5'", unit = "ms"),
            @TrackAnalyticsMetric(code = "ANNOTATION_TEXT_METRIC", value = "'blue'")
        },
        trackPayloadSize = false
    )
    public Map<String, Object> success() {
        return Map.of("ok", true);
    }

    @GetMapping("/test/analytics/metrics/unknown")
    @TrackAnalyticsEvent(
        code = "ANNOTATION_METRIC_EVENT",
        metrics = @TrackAnalyticsMetric(code = "UNKNOWN_METRIC_FOR_TEST", value = "'7'"),
        trackPayloadSize = false
    )
    public Map<String, Object> unknown() {
        return Map.of("ok", true);
    }

    @GetMapping("/test/analytics/metrics/spel-error")
    @TrackAnalyticsEvent(
        code = "ANNOTATION_METRIC_EVENT",
        metrics = @TrackAnalyticsMetric(code = "ANNOTATION_NUMERIC_METRIC", value = "#missing.value"),
        trackPayloadSize = false
    )
    public Map<String, Object> spelError() {
        return Map.of("ok", true);
    }

    @GetMapping("/test/analytics/metrics/type-mismatch")
    @TrackAnalyticsEvent(
        code = "ANNOTATION_METRIC_EVENT",
        metrics = @TrackAnalyticsMetric(code = "ANNOTATION_NUMERIC_METRIC", value = "'not-a-number'"),
        trackPayloadSize = false
    )
    public Map<String, Object> typeMismatch() {
        return Map.of("ok", true);
    }

    @GetMapping("/test/analytics/stage-metrics/service")
    @TrackAnalyticsEvent(code = "ANNOTATION_METRIC_EVENT", trackPayloadSize = false)
    public Map<String, Object> serviceStageMetric() {
        return Map.of("size", stageMetricTestService.serviceMetric().size());
    }

    @GetMapping("/test/analytics/stage-metrics/database")
    @TrackAnalyticsEvent(code = "ANNOTATION_METRIC_EVENT", trackPayloadSize = false)
    public Map<String, Object> databaseStageMetric() {
        return Map.of("size", stageMetricTestService.databaseMetric().size());
    }

    @GetMapping("/test/analytics/stage-metrics/unknown")
    @TrackAnalyticsEvent(code = "ANNOTATION_METRIC_EVENT", trackPayloadSize = false)
    public Map<String, Object> unknownStageMetric() {
        return Map.of("value", stageMetricTestService.unknownMetric());
    }

    @GetMapping("/test/analytics/stage-metrics/spel-error")
    @TrackAnalyticsEvent(code = "ANNOTATION_METRIC_EVENT", trackPayloadSize = false)
    public Map<String, Object> stageMetricSpelError() {
        return Map.of("value", stageMetricTestService.spelErrorMetric());
    }
}

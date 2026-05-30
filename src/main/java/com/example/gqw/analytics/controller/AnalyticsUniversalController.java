package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/analytics/api/universal", "/analytics-admin/api/universal"})
public class AnalyticsUniversalController {

    private final AnalyticsInsightsService analyticsInsightsService;

    public AnalyticsUniversalController(AnalyticsInsightsService analyticsInsightsService) {
        this.analyticsInsightsService = analyticsInsightsService;
    }

    @GetMapping
    public UniversalResponse universal(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) Boolean allTime,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) List<String> eventTypeCode,
        @RequestParam(required = false) String requestPath,
        @RequestParam(required = false) String attributeCode,
        @RequestParam(required = false) String attributeValue,
        @RequestParam(required = false) String filterMetricTypeCode,
        @RequestParam(required = false) String filterMetricValue,
        @RequestParam(required = false) BigDecimal filterMetricMinValue,
        @RequestParam(required = false) BigDecimal filterMetricMaxValue,
        @RequestParam(required = false) String filterAttributeCode,
        @RequestParam(required = false) String filterAttributeValue,
        @RequestParam(required = false) BigDecimal filterAttributeMinValue,
        @RequestParam(required = false) BigDecimal filterAttributeMaxValue,
        @RequestParam(required = false) String stageTypeCode,
        @RequestParam(required = false) Integer bucketMinutes
    ) {
        AnalyticsTimeRangeResolver.TimeRange range;
        if (Boolean.TRUE.equals(allTime)) {
            range = new AnalyticsTimeRangeResolver.TimeRange(Instant.EPOCH, Instant.now());
        } else {
            range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(24));
        }
        return analyticsInsightsService.universal(
            range.from(),
            range.to(),
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
            bucketMinutes
        );
    }
}

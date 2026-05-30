package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.RangeStartResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/analytics/api/range-start", "/analytics-admin/api/range-start"})
public class AnalyticsRangeController {

    private final AnalyticsInsightsService analyticsInsightsService;

    public AnalyticsRangeController(AnalyticsInsightsService analyticsInsightsService) {
        this.analyticsInsightsService = analyticsInsightsService;
    }

    @GetMapping
    public RangeStartResponse rangeStart() {
        return new RangeStartResponse(analyticsInsightsService.firstEventStartedAt());
    }
}

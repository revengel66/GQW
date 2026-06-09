package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInsightsService;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.DictionariesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/analytics/api/dictionaries", "/analytics-admin/api/dictionaries"})
public class AnalyticsDictionaryController {

    private final AnalyticsInsightsService analyticsInsightsService;

    public AnalyticsDictionaryController(AnalyticsInsightsService analyticsInsightsService) {
        this.analyticsInsightsService = analyticsInsightsService;
    }

    @GetMapping
    public DictionariesResponse dictionaries(
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false, defaultValue = "false") Boolean systemEventsOnly
    ) {
        return analyticsInsightsService.dictionaries(moduleCode, Boolean.TRUE.equals(systemEventsOnly));
    }
}

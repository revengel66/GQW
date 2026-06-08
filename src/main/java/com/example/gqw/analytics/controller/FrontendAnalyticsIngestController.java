package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsInstrumentationPolicy;
import com.example.gqw.analytics.service.FrontendAnalyticsIngestService;
import com.example.gqw.analytics.web.dto.FrontendAnalyticsIngestRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics/frontend")
public class FrontendAnalyticsIngestController {

    private static final Logger log = LoggerFactory.getLogger(FrontendAnalyticsIngestController.class);

    private final AnalyticsInstrumentationPolicy instrumentationPolicy;
    private final FrontendAnalyticsIngestService frontendAnalyticsIngestService;

    public FrontendAnalyticsIngestController(
        AnalyticsInstrumentationPolicy instrumentationPolicy,
        FrontendAnalyticsIngestService frontendAnalyticsIngestService
    ) {
        this.instrumentationPolicy = instrumentationPolicy;
        this.frontendAnalyticsIngestService = frontendAnalyticsIngestService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(
        @RequestBody(required = false) FrontendAnalyticsIngestRequest request,
        HttpServletRequest httpRequest
    ) {
        if (!instrumentationPolicy.isEnabled()) {
            return ResponseEntity.noContent().build();
        }
        try {
            frontendAnalyticsIngestService.ingest(request, httpRequest);
        } catch (RuntimeException ex) {
            // Frontend analytics ingestion is best-effort and must never break client flow.
            log.warn("Frontend analytics ingest failed but request accepted", ex);
        }
        return ResponseEntity.accepted().build();
    }
}

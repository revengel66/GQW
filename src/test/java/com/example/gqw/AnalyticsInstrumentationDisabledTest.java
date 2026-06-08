package com.example.gqw;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.gqw.analytics.aop.AnalyticsEventAspect;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.analytics.repository.AnalyticsStageMetricRepository;
import com.example.gqw.analytics.repository.AnalyticsStageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.analytics.instrumentation.enabled=false")
class AnalyticsInstrumentationDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyticsEventRepository analyticsEventRepository;

    @Autowired
    private AnalyticsStageRepository analyticsStageRepository;

    @Autowired
    private AnalyticsStageMetricRepository analyticsStageMetricRepository;

    @Test
    void trackedControllerRequestExecutesBusinessMethodWithoutAnalyticsRecords() throws Exception {
        long eventsBefore = analyticsEventRepository.count();
        long stagesBefore = analyticsStageRepository.count();
        long metricsBefore = analyticsStageMetricRepository.count();

        mockMvc.perform(get("/test/analytics/layers/facade"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"size\":2")))
            .andExpect(header().doesNotExist(AnalyticsEventAspect.ANALYTICS_EVENT_UID_RESPONSE_HEADER));

        assertEquals(eventsBefore, analyticsEventRepository.count());
        assertEquals(stagesBefore, analyticsStageRepository.count());
        assertEquals(metricsBefore, analyticsStageMetricRepository.count());
    }

    @Test
    void frontendIngestReturnsNoContentWithoutAnalyticsRecords() throws Exception {
        long eventsBefore = analyticsEventRepository.count();
        long stagesBefore = analyticsStageRepository.count();
        long metricsBefore = analyticsStageMetricRepository.count();

        String payload = """
            {
              "events": [
                {
                  "code": "FRONTEND_JS_ERROR",
                  "pagePath": "/catalog",
                  "requestPath": "/catalog",
                  "httpMethod": "GET",
                  "traceId": "trace-disabled-1",
                  "statusCode": 500,
                  "error": true,
                  "errorMessage": "JS disabled test"
                }
              ]
            }
            """;

        mockMvc.perform(
                post("/api/analytics/frontend/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
            .andExpect(status().isNoContent());

        assertEquals(eventsBefore, analyticsEventRepository.count());
        assertEquals(stagesBefore, analyticsStageRepository.count());
        assertEquals(metricsBefore, analyticsStageMetricRepository.count());
    }
}

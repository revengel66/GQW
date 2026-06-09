package com.example.gqw.analytics.performance.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import com.example.gqw.analytics.entity.AnalyticsStage;
import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import com.example.gqw.analytics.entity.EventAttributeType;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.entity.ModuleType;
import com.example.gqw.analytics.entity.StageMetricType;
import com.example.gqw.analytics.entity.StageType;
import com.example.gqw.analytics.repository.AnalyticsEventAttributeRepository;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.analytics.repository.AnalyticsStageMetricRepository;
import com.example.gqw.analytics.repository.AnalyticsStageRepository;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import com.example.gqw.analytics.repository.EventTypeRepository;
import com.example.gqw.analytics.repository.ModuleTypeRepository;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import com.example.gqw.analytics.repository.StageTypeRepository;
import com.example.gqw.analytics.service.AnalyticsAdminAuthService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsAdminApiPerformanceSmokeTest {

    private static final long API_THRESHOLD_MS = 60_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyticsAdminAuthService authService;

    @Autowired
    private AnalyticsEventRepository eventRepository;

    @Autowired
    private AnalyticsStageRepository stageRepository;

    @Autowired
    private AnalyticsStageMetricRepository stageMetricRepository;

    @Autowired
    private AnalyticsEventAttributeRepository eventAttributeRepository;

    @Autowired
    private EventTypeRepository eventTypeRepository;

    @Autowired
    private ModuleTypeRepository moduleTypeRepository;

    @Autowired
    private StageTypeRepository stageTypeRepository;

    @Autowired
    private StageMetricTypeRepository stageMetricTypeRepository;

    @Autowired
    private EventAttributeTypeRepository eventAttributeTypeRepository;

    private AnalyticsEvent productViewEvent;
    private Instant from;
    private Instant to;

    @BeforeEach
    void setUp() {
        seedDictionaries();
        productViewEvent = seedProductViewEvent();
        from = productViewEvent.getStartedAt().minusSeconds(60);
        to = productViewEvent.getStartedAt().plusSeconds(60);
    }

    @Test
    void analyticsAdminApiEndpointsRespondWithinSmokeThreshold() throws Exception {
        MockHttpSession session = analyticsAdminSession();
        List<EndpointResult> results = new ArrayList<>();

        results.add(measure("overview", get("/analytics-admin/api/overview")
            .session(session)
            .param("from", from.toString())
            .param("to", to.toString())
            .param("bucketMinutes", "5")
            .accept(MediaType.APPLICATION_JSON)));
        results.add(measure("events", get("/analytics-admin/api/events")
            .session(session)
            .param("from", from.toString())
            .param("to", to.toString())
            .param("size", "10")
            .accept(MediaType.APPLICATION_JSON)));
        results.add(measure("filter-options", get("/analytics-admin/api/filter-options")
            .session(session)
            .param("from", from.toString())
            .param("to", to.toString())
            .accept(MediaType.APPLICATION_JSON)));
        results.add(measure("stages", get("/analytics-admin/api/stages")
            .session(session)
            .param("from", from.toString())
            .param("to", to.toString())
            .param("bucketMinutes", "5")
            .accept(MediaType.APPLICATION_JSON)));
        results.add(measure("compare", get("/analytics-admin/api/compare")
            .session(session)
            .param("baselineFrom", from.minusSeconds(120).toString())
            .param("baselineTo", from.minusSeconds(60).toString())
            .param("targetFrom", from.toString())
            .param("targetTo", to.toString())
            .accept(MediaType.APPLICATION_JSON)));
        results.add(measure("event-details", get("/analytics-admin/api/events/{eventUid}", productViewEvent.getEventUid())
            .session(session)
            .accept(MediaType.APPLICATION_JSON)));

        printSummary(results);
        results.forEach(result -> {
            assertEquals(200, result.status(), result.name() + " HTTP status");
            assertTrue(
                result.elapsedMs() < API_THRESHOLD_MS,
                result.name() + " took " + result.elapsedMs() + " ms, threshold is " + API_THRESHOLD_MS + " ms"
            );
        });
    }

    private EndpointResult measure(String name, RequestBuilder request) throws Exception {
        long started = System.nanoTime();
        MvcResult result = mockMvc.perform(request).andReturn();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        return new EndpointResult(name, result.getResponse().getStatus(), elapsedMs);
    }

    private MockHttpSession analyticsAdminSession() {
        if (!authService.isSetupComplete()) {
            authService.registerInitial("performance_smoke_admin", "secret123");
        }
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AnalyticsAdminAuthService.SESSION_KEY_AUTH, true);
        session.setAttribute(AnalyticsAdminAuthService.SESSION_KEY_USER_ID, 1L);
        session.setAttribute(AnalyticsAdminAuthService.SESSION_KEY_USERNAME, "performance_smoke_admin");
        return session;
    }

    private void seedDictionaries() {
        seedModule("SHOP", "Shop");
        seedModule(EventType.DEFAULT_MODULE_CODE, "Default");
        seedEventType("PRODUCT_VIEW", "Product view", false);
        seedStageType("CONTROLLER", "Controller");
        seedStageType("SERVICE", "Service");
        seedMetricType("DURATION_MS", "Duration", MetricValueKind.NUMERIC, "ms");
        seedAttributeType("HTTP_PATH", "HTTP path", MetricValueKind.TEXT);
    }

    private AnalyticsEvent seedProductViewEvent() {
        AnalyticsEvent event = new AnalyticsEvent();
        event.setEventUid(UUID.randomUUID());
        event.setEventTypeCode("PRODUCT_VIEW");
        event.setModuleCode("SHOP");
        event.setRequestPath("/product/tv-7");
        event.setHttpMethod("GET");
        event.setTraceId("performance-smoke-" + UUID.randomUUID());
        event.setStatusCode(200);
        event.setIsError(false);
        event.setStartedAt(Instant.now().minusSeconds(30));
        event.setEndedAt(event.getStartedAt().plusMillis(120));
        event.setDurationMs(120);
        event = eventRepository.saveAndFlush(event);

        AnalyticsStage stage = new AnalyticsStage();
        stage.setEventId(event.getId());
        stage.setStageTypeCode("CONTROLLER");
        stage.setStageOrder(1);
        stage.setStartedAt(event.getStartedAt());
        stage.setEndedAt(event.getEndedAt());
        stage.setDurationMs(120);
        stage.setIsError(false);
        stage = stageRepository.saveAndFlush(stage);

        AnalyticsStageMetric metric = new AnalyticsStageMetric();
        metric.setStageId(stage.getId());
        metric.setMetricTypeCode("DURATION_MS");
        metric.setMetricValueNum(BigDecimal.valueOf(120));
        metric.setUnit("ms");
        stageMetricRepository.save(metric);

        AnalyticsEventAttribute attribute = new AnalyticsEventAttribute();
        attribute.setEventId(event.getId());
        attribute.setAttributeTypeCode("HTTP_PATH");
        attribute.setAttrValue("/product/tv-7");
        eventAttributeRepository.save(attribute);

        return event;
    }

    private void seedModule(String code, String name) {
        if (moduleTypeRepository.existsById(code)) {
            return;
        }
        ModuleType moduleType = new ModuleType();
        moduleType.setCode(code);
        moduleType.setName(name);
        moduleType.setDescription(name);
        moduleType.setIsActive(true);
        moduleTypeRepository.save(moduleType);
    }

    private void seedEventType(String code, String name, boolean system) {
        if (eventTypeRepository.existsById(code)) {
            return;
        }
        EventType eventType = new EventType();
        eventType.setCode(code);
        eventType.setName(name);
        eventType.setDescription(name);
        eventType.setModuleCode("SHOP");
        eventType.setIsSystem(system);
        eventType.setIsActive(true);
        eventTypeRepository.save(eventType);
    }

    private void seedStageType(String code, String name) {
        if (stageTypeRepository.existsById(code)) {
            return;
        }
        StageType stageType = new StageType();
        stageType.setCode(code);
        stageType.setName(name);
        stageType.setDescription(name);
        stageType.setIsActive(true);
        stageTypeRepository.save(stageType);
    }

    private void seedMetricType(String code, String name, MetricValueKind valueKind, String unit) {
        if (stageMetricTypeRepository.existsById(code)) {
            return;
        }
        StageMetricType metricType = new StageMetricType();
        metricType.setCode(code);
        metricType.setName(name);
        metricType.setDescription(name);
        metricType.setValueKind(valueKind);
        metricType.setUnitDefault(unit);
        metricType.setIsActive(true);
        stageMetricTypeRepository.save(metricType);
    }

    private void seedAttributeType(String code, String name, MetricValueKind valueKind) {
        if (eventAttributeTypeRepository.existsById(code)) {
            return;
        }
        EventAttributeType attributeType = new EventAttributeType();
        attributeType.setCode(code);
        attributeType.setName(name);
        attributeType.setDescription(name);
        attributeType.setValueKind(valueKind);
        attributeType.setIsActive(true);
        eventAttributeTypeRepository.save(attributeType);
    }

    private static void printSummary(List<EndpointResult> results) {
        System.out.println("Analytics Admin API performance smoke:");
        results.forEach(result -> System.out.printf(
            "%-16s status=%d timeMs=%d thresholdMs=%d result=%s%n",
            result.name(),
            result.status(),
            result.elapsedMs(),
            API_THRESHOLD_MS,
            result.status() == 200 && result.elapsedMs() < API_THRESHOLD_MS ? "PASS" : "FAIL"
        ));
    }

    private record EndpointResult(String name, int status, long elapsedMs) {
    }
}

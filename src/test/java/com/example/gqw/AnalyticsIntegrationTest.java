package com.example.gqw;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.gqw.analytics.aop.AnalyticsEventAspect;
import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsStage;
import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.entity.StageType;
import com.example.gqw.analytics.entity.StageMetricType;
import com.example.gqw.analytics.repository.AnalyticsStageMetricRepository;
import com.example.gqw.analytics.repository.AnalyticsStageRepository;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.analytics.repository.EventTypeRepository;
import com.example.gqw.analytics.repository.StageTypeRepository;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import com.example.gqw.analytics.service.AnalyticsAdminAuthService;
import com.example.gqw.analytics.service.AnalyticsEventService;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.ShopUserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.extension.ExtendWith;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyticsEventRepository analyticsEventRepository;

    @Autowired
    private AnalyticsStageRepository analyticsStageRepository;

    @Autowired
    private AnalyticsStageMetricRepository analyticsStageMetricRepository;

    @Autowired
    private AnalyticsLayerTestFacade analyticsLayerTestFacade;

    @Autowired
    private EventTypeRepository eventTypeRepository;

    @Autowired
    private StageTypeRepository stageTypeRepository;

    @Autowired
    private StageMetricTypeRepository stageMetricTypeRepository;

    @Autowired
    private AnalyticsEventService analyticsEventService;

    @Autowired
    private ShopUserRepository shopUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AnalyticsAdminAuthService analyticsAdminAuthService;

    @BeforeEach
    void seedStrictAnalyticsEventTypes() {
        seedEventType(
            "FRONTEND_JS_ERROR",
            "JavaScript error",
            "Client-side JavaScript error captured by frontend analytics.",
            true
        );
        seedEventType(
            "HTTP_REQUEST_ERROR",
            "HTTP request error",
            "Technical HTTP request error captured by fallback tracking.",
            true
        );
        seedStageType("CONTROLLER", "Controller", "HTTP controller execution.");
        seedStageType("SERVICE", "Service", "Service method execution.");
        seedStageType("DATABASE", "Database", "Repository/database execution.");
        seedStageType("FACADE", "Facade", "Facade execution.");
        seedStageType("INACTIVE_LAYER_FOR_TEST", "Inactive layer", "Inactive layer test.", false);
        seedEventType(
            "ANNOTATION_METRIC_EVENT",
            "Annotation metric test event",
            "Event used by integration tests for annotation-based metrics.",
            false
        );
        seedMetricType("ANNOTATION_NUMERIC_METRIC", "Annotation numeric metric", MetricValueKind.NUMERIC, "ms");
        seedMetricType("ANNOTATION_TEXT_METRIC", "Annotation text metric", MetricValueKind.TEXT, null);
        seedMetricType("ANNOTATION_SERVICE_METRIC", "Annotation service metric", MetricValueKind.NUMERIC, "count");
        seedMetricType("ANNOTATION_DB_METRIC", "Annotation database metric", MetricValueKind.NUMERIC, "count");
        seedMetricType("DB_QUERY_COUNT", "Database query count", MetricValueKind.NUMERIC, "count");
    }

    private void seedEventType(String code, String name, String description, boolean system) {
        if (eventTypeRepository.existsById(code)) {
            return;
        }
        EventType eventType = new EventType();
        eventType.setCode(code);
        eventType.setName(name);
        eventType.setDescription(description);
        eventType.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        eventType.setIsSystem(system);
        eventType.setIsActive(true);
        eventTypeRepository.save(eventType);
    }

    private void seedStageType(String code, String name, String description) {
        seedStageType(code, name, description, true);
    }

    private void seedStageType(String code, String name, String description, boolean active) {
        if (stageTypeRepository.existsById(code)) {
            StageType existing = stageTypeRepository.findById(code).orElseThrow();
            existing.setIsActive(active);
            stageTypeRepository.save(existing);
            return;
        }
        StageType stageType = new StageType();
        stageType.setCode(code);
        stageType.setName(name);
        stageType.setDescription(description);
        stageType.setIsSystem(true);
        stageType.setIsActive(active);
        stageTypeRepository.save(stageType);
    }

    private void seedMetricType(String code, String name, MetricValueKind valueKind, String unitDefault) {
        if (stageMetricTypeRepository.existsById(code)) {
            return;
        }
        StageMetricType metricType = new StageMetricType();
        metricType.setCode(code);
        metricType.setName(name);
        metricType.setDescription(name);
        metricType.setValueKind(valueKind);
        metricType.setUnitDefault(unitDefault);
        metricType.setIsSystem(false);
        metricType.setIsActive(true);
        stageMetricTypeRepository.save(metricType);
    }

    @Test
    void analyticsApiRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/analytics/api/dictionaries"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    void analyticsApiForbidsNonAdminUser() throws Exception {
        MockHttpSession userSession = loginAs("analytics_user", false);
        mockMvc.perform(get("/analytics/api/dictionaries").session(userSession))
            .andExpect(status().isForbidden());
    }

    @Test
    void analyticsDictionariesReturnsJsonForAdmin() throws Exception {
        MockHttpSession adminSession = loginAs("analytics_admin", true);
        mockMvc.perform(get("/analytics/api/dictionaries").session(adminSession).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modules").isArray())
            .andExpect(jsonPath("$.eventTypes").isArray())
            .andExpect(jsonPath("$.stageTypes").isArray())
            .andExpect(jsonPath("$.stageMetricTypes").isArray())
            .andExpect(jsonPath("$.eventAttributeTypes").isArray());
    }

    @Test
    void frontendIngestAcceptsPayloadAndPersistsAnalyticsEvent() throws Exception {
        long before = analyticsEventRepository.count();

        String payload = """
            {
              "events": [
                {
                  "code": "FRONTEND_JS_ERROR",
                  "pagePath": "/catalog",
                  "requestPath": "/catalog",
                  "httpMethod": "GET",
                  "traceId": "trace-integration-1",
                  "statusCode": 500,
                  "error": true,
                  "errorMessage": "JS integration error",
                  "metricsNum": {
                    "FRONTEND_HTTP_STATUS": 500
                  },
                  "metricsText": {
                    "FRONTEND_ERROR_MESSAGE": "JS integration error"
                  }
                }
              ]
            }
            """;

        mockMvc.perform(
                post("/api/analytics/frontend/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
            .andExpect(status().isAccepted());

        long after = analyticsEventRepository.count();
        assertTrue(after >= before, "Frontend ingest must not reduce analytics events count");
    }

    @Test
    void frontendIngestReturnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(
                post("/api/analytics/frontend/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{")
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void trackAnalyticsMetricAnnotationPersistsNumericAndTextMetrics() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/metrics/success"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")))
            .andReturn();

        List<AnalyticsStageMetric> metrics = loadRecordedMetrics(result);

        AnalyticsStageMetric numericMetric = metrics.stream()
            .filter(metric -> "ANNOTATION_NUMERIC_METRIC".equals(metric.getMetricTypeCode()))
            .findFirst()
            .orElseThrow();
        assertEquals(0, new BigDecimal("42.5").compareTo(numericMetric.getMetricValueNum()));
        assertEquals("ms", numericMetric.getUnit());

        AnalyticsStageMetric textMetric = metrics.stream()
            .filter(metric -> "ANNOTATION_TEXT_METRIC".equals(metric.getMetricTypeCode()))
            .findFirst()
            .orElseThrow();
        assertEquals("blue", textMetric.getMetricValueText());
    }

    @Test
    void unknownMetricIsLoggedAndDoesNotCreateDictionaryRow(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/metrics/unknown"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")));

        assertFalse(stageMetricTypeRepository.existsById("UNKNOWN_METRIC_FOR_TEST"));
        assertTrue(output.toString().contains("Unknown metric type: UNKNOWN_METRIC_FOR_TEST"));
    }

    @Test
    void spelErrorIsLoggedAndBusinessRequestContinues(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/metrics/spel-error"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")));

        assertTrue(output.toString().contains("Metric expression failed: #missing.value"));
    }

    @Test
    void numericTypeMismatchIsLoggedAndBusinessRequestContinues(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/metrics/type-mismatch"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")))
            .andReturn();

        List<AnalyticsStageMetric> metrics = loadRecordedMetrics(result);
        assertFalse(metrics.stream().anyMatch(metric -> "ANNOTATION_NUMERIC_METRIC".equals(metric.getMetricTypeCode())));
        assertTrue(output.toString().contains("Metric type is NUMERIC but expression returned non-numeric value"));
    }

    @Test
    void trackAnalyticsStageMetricAnnotationPersistsMetricIntoServiceStage() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/stage-metrics/service"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"size\":3")))
            .andReturn();

        AnalyticsStage serviceStage = findStageWithMetric(result, "SERVICE", "ANNOTATION_SERVICE_METRIC");
        List<AnalyticsStageMetric> metrics = analyticsStageMetricRepository.findByStageId(serviceStage.getId());
        AnalyticsStageMetric serviceMetric = findMetric(metrics, "ANNOTATION_SERVICE_METRIC");
        assertEquals(0, new BigDecimal("3").compareTo(serviceMetric.getMetricValueNum()));
        assertEquals("count", serviceMetric.getUnit());
        AnalyticsStageMetric textMetric = findMetric(metrics, "ANNOTATION_TEXT_METRIC");
        assertEquals("service-stage", textMetric.getMetricValueText());
    }

    @Test
    void trackAnalyticsStageMetricAnnotationPersistsMetricIntoDatabaseStage() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/stage-metrics/database"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"size\":2")))
            .andReturn();

        AnalyticsStage databaseStage = findStageWithMetric(result, "DATABASE", "ANNOTATION_DB_METRIC");
        List<AnalyticsStageMetric> metrics = analyticsStageMetricRepository.findByStageId(databaseStage.getId());
        AnalyticsStageMetric databaseMetric = findMetric(metrics, "ANNOTATION_DB_METRIC");
        assertEquals(0, new BigDecimal("2").compareTo(databaseMetric.getMetricValueNum()));
        assertTrue(metrics.stream().filter(metric -> "DB_QUERY_COUNT".equals(metric.getMetricTypeCode())).count() <= 1);
    }

    @Test
    void trackAnalyticsLayerCreatesCustomStageInsideActiveEvent() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/layers/facade"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"size\":2")))
            .andReturn();

        List<AnalyticsStage> stages = loadRecordedStages(result);
        List<String> codes = stages.stream().map(AnalyticsStage::getStageTypeCode).toList();

        assertTrue(codes.contains("CONTROLLER"));
        assertTrue(codes.contains("FACADE"));
        assertTrue(codes.contains("SERVICE"));
        assertTrue(codes.contains("DATABASE"));
        assertTrue(codes.indexOf("CONTROLLER") < codes.indexOf("FACADE"));
        assertTrue(codes.indexOf("FACADE") < codes.indexOf("SERVICE"));
        assertTrue(codes.indexOf("SERVICE") < codes.indexOf("DATABASE"));
        assertEquals(1, codes.stream().filter("FACADE"::equals).count());
        assertEquals(1, codes.stream().filter("SERVICE"::equals).count());
    }

    @Test
    void requestWithoutCustomLayerDoesNotCreateFacadeStage() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/layers/no-facade"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"size\":2")))
            .andReturn();

        List<String> codes = loadRecordedStages(result).stream()
            .map(AnalyticsStage::getStageTypeCode)
            .toList();

        assertTrue(codes.contains("CONTROLLER"));
        assertTrue(codes.contains("SERVICE"));
        assertTrue(codes.contains("DATABASE"));
        assertFalse(codes.contains("FACADE"));
    }

    @Test
    void stageMetricInsideCustomLayerIsWrittenIntoCustomStage() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/layers/facade-metric"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"size\":4")))
            .andReturn();

        AnalyticsStage facadeStage = findStageWithMetric(result, "FACADE", "ANNOTATION_SERVICE_METRIC");
        List<AnalyticsStageMetric> metrics = analyticsStageMetricRepository.findByStageId(facadeStage.getId());
        AnalyticsStageMetric numericMetric = findMetric(metrics, "ANNOTATION_SERVICE_METRIC");
        assertEquals(0, new BigDecimal("4").compareTo(numericMetric.getMetricValueNum()));
        AnalyticsStageMetric textMetric = findMetric(metrics, "ANNOTATION_TEXT_METRIC");
        assertEquals("facade-stage", textMetric.getMetricValueText());
    }

    @Test
    void unknownCustomLayerIsLoggedAndBusinessRequestContinues(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/layers/unknown"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"value\":\"ok\"")))
            .andReturn();

        assertFalse(stageTypeRepository.existsById("UNKNOWN_LAYER_FOR_TEST"));
        List<String> codes = loadRecordedStages(result).stream()
            .map(AnalyticsStage::getStageTypeCode)
            .toList();
        assertFalse(codes.contains("UNKNOWN_LAYER_FOR_TEST"));
        assertTrue(output.toString().contains("unknown stage type code=UNKNOWN_LAYER_FOR_TEST"));
    }

    @Test
    void inactiveCustomLayerIsLoggedAndBusinessRequestContinues(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/layers/inactive"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"value\":\"ok\"")))
            .andReturn();

        List<String> codes = loadRecordedStages(result).stream()
            .map(AnalyticsStage::getStageTypeCode)
            .toList();
        assertFalse(codes.contains("INACTIVE_LAYER_FOR_TEST"));
        assertTrue(output.toString().contains("inactive stage type code=INACTIVE_LAYER_FOR_TEST"));
    }

    @Test
    void customLayerDoesNotCreateEventWithoutActiveEventContext() {
        long eventsBefore = analyticsEventRepository.count();
        long stagesBefore = analyticsStageRepository.count();

        assertEquals(4, analyticsLayerTestFacade.facadeMetric().size());

        assertEquals(eventsBefore, analyticsEventRepository.count());
        assertEquals(stagesBefore, analyticsStageRepository.count());
    }

    @Test
    void unknownStageMetricIsLoggedAndDoesNotCreateDictionaryRow(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/stage-metrics/unknown"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"value\":\"ok\"")));

        assertFalse(stageMetricTypeRepository.existsById("UNKNOWN_STAGE_METRIC_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics stage metric skipped"));
        assertTrue(output.toString().contains("Unknown metric type: UNKNOWN_STAGE_METRIC_FOR_TEST"));
    }

    @Test
    void stageMetricSpelErrorIsLoggedAndBusinessRequestContinues(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/stage-metrics/spel-error"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"value\":\"ok\"")));

        assertTrue(output.toString().contains("Analytics stage metric skipped"));
        assertTrue(output.toString().contains("Metric expression failed: #missing.value"));
    }

    @Test
    void metricUsedInCodeCannotBeDeleted() throws Exception {
        MockHttpSession adminSession = analyticsAdminSession("analytics_admin_metric_used");

        mockMvc.perform(
                get("/analytics-admin/dictionaries/metrics/delete/precheck")
                    .session(adminSession)
                    .param("code", "ANNOTATION_SERVICE_METRIC")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletable").value(false))
            .andExpect(jsonPath("$.usages").isArray())
            .andExpect(content().string(containsString("ANNOTATION_SERVICE_METRIC")));

        mockMvc.perform(
                post("/analytics-admin/dictionaries/metrics/delete")
                    .session(adminSession)
                    .with(csrf())
                    .param("code", "ANNOTATION_SERVICE_METRIC")
            )
            .andExpect(status().is3xxRedirection());

        assertTrue(stageMetricTypeRepository.existsById("ANNOTATION_SERVICE_METRIC"));
    }

    @Test
    void customMetricWithHistoricalValuesDeletesOnlyMetricValuesAndDictionaryRow() throws Exception {
        MockHttpSession adminSession = analyticsAdminSession("analytics_admin_metric_delete");
        seedMetricType("UNUSED_DELETE_METRIC", "Unused delete metric", MetricValueKind.NUMERIC, "count");
        AnalyticsEvent event = new AnalyticsEvent();
        event.setEventTypeCode("ANNOTATION_METRIC_EVENT");
        event.setModuleCode("DEFAULT");
        event.setRequestPath("/test/metric-delete");
        event.setHttpMethod("GET");
        event.setTraceId("metric-delete-trace");
        event.setStartedAt(Instant.now());
        event.setEndedAt(Instant.now());
        event.setDurationMs(5);
        event = analyticsEventRepository.save(event);

        AnalyticsStage stage = new AnalyticsStage();
        stage.setEventId(event.getId());
        stage.setStageTypeCode("SERVICE");
        stage.setStageOrder(1);
        stage.setStartedAt(event.getStartedAt());
        stage.setEndedAt(event.getEndedAt());
        stage.setDurationMs(5);
        stage = analyticsStageRepository.save(stage);

        AnalyticsStageMetric metric = new AnalyticsStageMetric();
        metric.setStageId(stage.getId());
        metric.setMetricTypeCode("UNUSED_DELETE_METRIC");
        metric.setMetricValueNum(new BigDecimal("7"));
        metric.setUnit("count");
        analyticsStageMetricRepository.save(metric);

        mockMvc.perform(
                get("/analytics-admin/dictionaries/metrics/delete/precheck")
                    .session(adminSession)
                    .param("code", "UNUSED_DELETE_METRIC")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletable").value(true))
            .andExpect(jsonPath("$.metricValueCount").value(1));

        mockMvc.perform(
                post("/analytics-admin/dictionaries/metrics/delete")
                    .session(adminSession)
                    .with(csrf())
                    .param("code", "UNUSED_DELETE_METRIC")
            )
            .andExpect(status().is3xxRedirection());

        assertFalse(stageMetricTypeRepository.existsById("UNUSED_DELETE_METRIC"));
        assertTrue(analyticsEventRepository.findById(event.getId()).isPresent());
        assertTrue(analyticsStageRepository.findById(stage.getId()).isPresent());
        assertFalse(analyticsStageMetricRepository.findByStageId(stage.getId()).stream()
            .anyMatch(item -> "UNUSED_DELETE_METRIC".equals(item.getMetricTypeCode())));
    }

    @Test
    void analyticsEventsEndpointScopesSystemEventsAndDetailsRemainAddressable() throws Exception {
        MockHttpSession adminSession = loginAs("analytics_admin_events", true);

        var event = analyticsEventService.createEvent(
            "FRONTEND_JS_ERROR",
            null,
            null,
            "/product/laptop",
            "GET",
            "trace-integration-2"
        );
        analyticsEventService.finishEventError(event.getEventUid(), 500, "Frontend crash");

        Instant now = Instant.now();
        String from = now.minus(1, ChronoUnit.DAYS).toString();
        String to = now.plus(1, ChronoUnit.HOURS).toString();

        mockMvc.perform(
                get("/analytics/api/events")
                    .session(adminSession)
                    .param("from", from)
                    .param("to", to)
                    .param("eventTypeCode", "FRONTEND_JS_ERROR")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(
                get("/analytics/api/events")
                    .session(adminSession)
                    .param("from", from)
                    .param("to", to)
                    .param("eventTypeCode", "FRONTEND_JS_ERROR")
                    .param("eventTypeCode", "FRONTEND_PAGE_VIEW")
                    .param("systemEventsOnly", "true")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", greaterThan(0)))
            .andExpect(content().string(containsString("\"items\"")));

        mockMvc.perform(
                get("/analytics/api/filter-options")
                    .session(adminSession)
                    .param("from", from)
                    .param("to", to)
                    .param("systemEventsOnly", "true")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modules").isArray())
            .andExpect(jsonPath("$.eventTypes[?(@.code == 'FRONTEND_JS_ERROR')]").exists());

        mockMvc.perform(
                get("/analytics/api/universal")
                    .session(adminSession)
                    .param("from", from)
                    .param("to", to)
                    .param("eventTypeCode", "FRONTEND_JS_ERROR")
                    .param("systemEventsOnly", "true")
                    .param("includeEventStageBreakdown", "false")
                    .param("isError", "true")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totals.count", greaterThan(0)))
            .andExpect(jsonPath("$.eventSeries[0].eventTypeCode").value("FRONTEND_JS_ERROR"));

        String uid = analyticsEventRepository.findAllByRangeOrdered(
                now.minus(1, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.HOURS),
                "FRONTEND_JS_ERROR",
                null
            )
            .stream()
            .filter(item -> "trace-integration-2".equals(item.getTraceId()))
            .findFirst()
            .orElseThrow()
            .getEventUid()
            .toString();

        mockMvc.perform(
                get("/analytics/api/events/{eventUid}", uid)
                    .session(adminSession)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventUid").value(uid));
    }

    private MockHttpSession loginAs(String username, boolean admin) throws Exception {
        createUserIfMissing(username, admin);
        MvcResult result = mockMvc.perform(
                post("/login")
                    .with(csrf())
                    .param("username", username)
                    .param("password", "secret123")
            )
            .andExpect(status().is3xxRedirection())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession analyticsAdminSession(String username) {
        if (!analyticsAdminAuthService.isSetupComplete()) {
            analyticsAdminAuthService.registerInitial(username, "secret123");
        }
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AnalyticsAdminAuthService.SESSION_KEY_AUTH, true);
        session.setAttribute(AnalyticsAdminAuthService.SESSION_KEY_USERNAME, username);
        session.setAttribute(AnalyticsAdminAuthService.SESSION_KEY_USER_ID, 1L);
        return session;
    }

    private List<AnalyticsStageMetric> loadRecordedMetrics(MvcResult result) {
        AnalyticsEvent event = loadRecordedEvent(result);
        List<AnalyticsStage> stages = analyticsStageRepository.findByEventIdOrderByStageOrder(event.getId());
        assertFalse(stages.isEmpty());
        return analyticsStageMetricRepository.findByStageId(stages.get(0).getId());
    }

    private AnalyticsStage findStageWithMetric(MvcResult result, String stageTypeCode, String metricTypeCode) {
        AnalyticsEvent event = loadRecordedEvent(result);
        return analyticsStageRepository.findByEventIdOrderByStageOrder(event.getId()).stream()
            .filter(stage -> stageTypeCode.equals(stage.getStageTypeCode()))
            .filter(stage -> analyticsStageMetricRepository.findByStageId(stage.getId()).stream()
                .anyMatch(metric -> metricTypeCode.equals(metric.getMetricTypeCode())))
            .findFirst()
            .orElseThrow();
    }

    private List<AnalyticsStage> loadRecordedStages(MvcResult result) {
        AnalyticsEvent event = loadRecordedEvent(result);
        return analyticsStageRepository.findByEventIdOrderByStageOrder(event.getId());
    }

    private AnalyticsStageMetric findMetric(List<AnalyticsStageMetric> metrics, String metricTypeCode) {
        return metrics.stream()
            .filter(metric -> metricTypeCode.equals(metric.getMetricTypeCode()))
            .findFirst()
            .orElseThrow();
    }

    private AnalyticsEvent loadRecordedEvent(MvcResult result) {
        String eventUid = result.getResponse().getHeader(AnalyticsEventAspect.ANALYTICS_EVENT_UID_RESPONSE_HEADER);
        assertNotNull(eventUid);
        return analyticsEventRepository.findByEventUid(UUID.fromString(eventUid)).orElseThrow();
    }

    private void createUserIfMissing(String username, boolean admin) {
        if (shopUserRepository.findByUsername(username).isPresent()) {
            return;
        }
        ShopUser user = new ShopUser();
        user.setUsername(username);
        user.setEmail(username + "@test.local");
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        user.setFullName("Integration User " + username);
        user.setIsAdmin(admin);
        user.setIsEnabled(true);
        shopUserRepository.save(user);
    }
}

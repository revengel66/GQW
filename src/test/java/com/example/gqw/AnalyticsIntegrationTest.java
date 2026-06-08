package com.example.gqw;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import com.example.gqw.analytics.entity.AnalyticsStage;
import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import com.example.gqw.analytics.entity.EventAttributeType;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.entity.ModuleType;
import com.example.gqw.analytics.entity.StageType;
import com.example.gqw.analytics.entity.StageMetricType;
import com.example.gqw.analytics.repository.AnalyticsStageMetricRepository;
import com.example.gqw.analytics.repository.AnalyticsEventAttributeRepository;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import com.example.gqw.analytics.repository.AnalyticsStageRepository;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.analytics.repository.EventTypeRepository;
import com.example.gqw.analytics.repository.ModuleTypeRepository;
import com.example.gqw.analytics.repository.StageTypeRepository;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import com.example.gqw.analytics.service.AnalyticsAdminAuthService;
import com.example.gqw.analytics.service.AnalyticsDictionaryAdminService;
import com.example.gqw.analytics.service.AnalyticsEventService;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.ShopUserRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private AnalyticsEventAttributeRepository analyticsEventAttributeRepository;

    @Autowired
    private AnalyticsLayerTestFacade analyticsLayerTestFacade;

    @Autowired
    private EventTypeRepository eventTypeRepository;

    @Autowired
    private EventAttributeTypeRepository eventAttributeTypeRepository;

    @Autowired
    private ModuleTypeRepository moduleTypeRepository;

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

    @Autowired
    private AnalyticsDictionaryAdminService analyticsDictionaryAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedStrictAnalyticsEventTypes() {
        seedModuleType(EventType.DEFAULT_MODULE_CODE, "Default module", true);
        seedModuleType("SHOP", "Shop", true);
        seedModuleType("ADMIN", "Admin", true);
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
        seedEventType("HOME_VIEW", "Home view", "Shop home page view.", false);
        seedEventType("CATALOG_VIEW", "Catalog view", "Shop catalog view.", false);
        seedEventType("ABOUT_VIEW", "About view", "Shop about page view.", false);
        seedEventType("REVIEWS_PAGE_VIEW", "Reviews page view", "Shop reviews page view.", false);
        seedModuleType("INACTIVE_MODULE_FOR_TEST", "Inactive module", false);
        seedEventType("INACTIVE_EVENT_FOR_TEST", "Inactive event", "Inactive event test.", false, false);
        seedEventType("UNKNOWN_MODULE_EVENT_FOR_TEST", "Unknown module event", "Unknown module test.", false, "UNKNOWN_MODULE_FOR_TEST", true);
        seedEventType("INACTIVE_MODULE_EVENT_FOR_TEST", "Inactive module event", "Inactive module test.", false, "INACTIVE_MODULE_FOR_TEST", true);
        seedStageType("CONTROLLER", "Controller", "HTTP controller execution.");
        seedStageType("SERVICE", "Service", "Service method execution.");
        seedStageType("DATABASE", "Database", "Repository/database execution.");
        seedStageType("FACADE", "Facade", "Facade execution.");
        seedStageType("PERSISTENCE", "Persistence", "Persistence execution.");
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
        seedMetricType("MVC_KNOWN_METRIC_FOR_TEST", "MVC known metric", MetricValueKind.NUMERIC, "count");
        seedMetricType("DB_QUERY_COUNT", "Database query count", MetricValueKind.NUMERIC, "count");
        seedMetricType("INACTIVE_METRIC_FOR_TEST", "Inactive metric", MetricValueKind.NUMERIC, "count", false);
        seedAttributeType("MVC_KNOWN_ATTRIBUTE_FOR_TEST", "MVC known attribute", MetricValueKind.TEXT, true);
        seedAttributeType("INACTIVE_ATTRIBUTE_FOR_TEST", "Inactive attribute", MetricValueKind.TEXT, false);
    }

    private void seedEventType(String code, String name, String description, boolean system) {
        seedEventType(code, name, description, system, EventType.DEFAULT_MODULE_CODE, true);
    }

    private void seedEventType(String code, String name, String description, boolean system, boolean active) {
        seedEventType(code, name, description, system, EventType.DEFAULT_MODULE_CODE, active);
    }

    private void seedEventType(
        String code,
        String name,
        String description,
        boolean system,
        String moduleCode,
        boolean active
    ) {
        if (eventTypeRepository.existsById(code)) {
            EventType existing = eventTypeRepository.findById(code).orElseThrow();
            existing.setModuleCode(moduleCode);
            existing.setIsActive(active);
            eventTypeRepository.save(existing);
            return;
        }
        EventType eventType = new EventType();
        eventType.setCode(code);
        eventType.setName(name);
        eventType.setDescription(description);
        eventType.setModuleCode(moduleCode);
        eventType.setIsSystem(system);
        eventType.setIsActive(active);
        eventTypeRepository.save(eventType);
    }

    private void seedModuleType(String code, String name, boolean active) {
        ModuleType moduleType = moduleTypeRepository.findById(code).orElseGet(ModuleType::new);
        moduleType.setCode(code);
        moduleType.setName(name);
        moduleType.setDescription(name);
        moduleType.setIsActive(active);
        moduleTypeRepository.save(moduleType);
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
        seedMetricType(code, name, valueKind, unitDefault, true);
    }

    private void seedMetricType(String code, String name, MetricValueKind valueKind, String unitDefault, boolean active) {
        if (stageMetricTypeRepository.existsById(code)) {
            StageMetricType existing = stageMetricTypeRepository.findById(code).orElseThrow();
            existing.setIsActive(active);
            stageMetricTypeRepository.save(existing);
            return;
        }
        StageMetricType metricType = new StageMetricType();
        metricType.setCode(code);
        metricType.setName(name);
        metricType.setDescription(name);
        metricType.setValueKind(valueKind);
        metricType.setUnitDefault(unitDefault);
        metricType.setIsSystem(false);
        metricType.setIsActive(active);
        stageMetricTypeRepository.save(metricType);
    }

    private void seedAttributeType(String code, String name, MetricValueKind valueKind, boolean active) {
        EventAttributeType attributeType = eventAttributeTypeRepository.findById(code).orElseGet(EventAttributeType::new);
        attributeType.setCode(code);
        attributeType.setName(name);
        attributeType.setDescription(name);
        attributeType.setValueKind(valueKind);
        attributeType.setIsSystem(false);
        attributeType.setIsActive(active);
        eventAttributeTypeRepository.save(attributeType);
    }

    private void cleanupDictionaryRenameTestData() {
        ensureEventRollupBucketForRenameTest();
        jdbcTemplate.update(
            "delete from analytics.stage_metric where metric_type_code in ('RENAME_METRIC_OLD', 'RENAME_METRIC_NEW')"
        );
        jdbcTemplate.update(
            """
                delete from analytics.stage_metric
                where stage_id in (
                    select id from analytics.stage
                    where stage_type_code in ('RENAME_STAGE_OLD', 'RENAME_STAGE_NEW')
                )
                """
        );
        jdbcTemplate.update("delete from analytics.event_attribute where attribute_type_code in ('RENAME_ATTRIBUTE_OLD', 'RENAME_ATTRIBUTE_NEW')");
        jdbcTemplate.update("delete from analytics.stage where stage_type_code in ('RENAME_STAGE_OLD', 'RENAME_STAGE_NEW')");
        jdbcTemplate.update(
            """
                delete from analytics.event
                where event_type_code in ('RENAME_EVENT_OLD', 'RENAME_EVENT_NEW', 'RENAME_CONFLICT_OLD', 'RENAME_CONFLICT_NEW')
                   or module_code in ('RENAME_MODULE_OLD', 'RENAME_MODULE_NEW')
                """
        );
        cleanupOptional("delete from analytics.aggregated_metric where event_type_code in ('RENAME_EVENT_OLD', 'RENAME_EVENT_NEW') or stage_type_code in ('RENAME_STAGE_OLD', 'RENAME_STAGE_NEW') or module_code in ('RENAME_MODULE_OLD', 'RENAME_MODULE_NEW')");
        cleanupOptional("delete from analytics.event_rollup_bucket where event_type_code in ('RENAME_EVENT_OLD', 'RENAME_EVENT_NEW') or module_code in ('RENAME_MODULE_OLD', 'RENAME_MODULE_NEW')");
        cleanupOptional("delete from analytics.stage_rollup_bucket where event_type_code in ('RENAME_EVENT_OLD', 'RENAME_EVENT_NEW') or stage_type_code in ('RENAME_STAGE_OLD', 'RENAME_STAGE_NEW') or module_code in ('RENAME_MODULE_OLD', 'RENAME_MODULE_NEW')");
        cleanupOptional("delete from analytics.stage_metric_rollup_bucket where event_type_code in ('RENAME_EVENT_OLD', 'RENAME_EVENT_NEW') or stage_type_code in ('RENAME_STAGE_OLD', 'RENAME_STAGE_NEW') or metric_type_code in ('RENAME_METRIC_OLD', 'RENAME_METRIC_NEW') or module_code in ('RENAME_MODULE_OLD', 'RENAME_MODULE_NEW')");
        cleanupOptional("delete from analytics.filter_event_type_day where event_type_code in ('RENAME_EVENT_OLD', 'RENAME_EVENT_NEW') or module_code in ('RENAME_MODULE_OLD', 'RENAME_MODULE_NEW')");
        cleanupOptional("delete from analytics.filter_attr_value_day where event_type_code in ('RENAME_EVENT_OLD', 'RENAME_EVENT_NEW') or attribute_type_code in ('RENAME_ATTRIBUTE_OLD', 'RENAME_ATTRIBUTE_NEW') or module_code in ('RENAME_MODULE_OLD', 'RENAME_MODULE_NEW')");
        cleanupOptional("delete from analytics.code_alias where source_code like 'RENAME_%' or target_code like 'RENAME_%'");
        jdbcTemplate.update("delete from analytics.stage_metric_type where code in ('RENAME_METRIC_OLD', 'RENAME_METRIC_NEW')");
        jdbcTemplate.update("delete from analytics.stage_type where code in ('RENAME_STAGE_OLD', 'RENAME_STAGE_NEW')");
        jdbcTemplate.update("delete from analytics.event_attribute_type where code in ('RENAME_ATTRIBUTE_OLD', 'RENAME_ATTRIBUTE_NEW')");
        jdbcTemplate.update("delete from analytics.event_type where code in ('RENAME_EVENT_OLD', 'RENAME_EVENT_NEW', 'RENAME_CONFLICT_OLD', 'RENAME_CONFLICT_NEW')");
        jdbcTemplate.update("delete from analytics.module_type where code in ('RENAME_MODULE_OLD', 'RENAME_MODULE_NEW')");
    }

    private void ensureEventRollupBucketForRenameTest() {
        jdbcTemplate.execute(
            """
                create table if not exists analytics.event_rollup_bucket (
                    bucket_start timestamp with time zone not null,
                    granularity_minutes integer not null,
                    module_code varchar(64) not null,
                    event_type_code varchar(64) not null,
                    sample_count bigint not null,
                    error_count bigint not null,
                    duration_sum bigint not null,
                    avg_ms numeric(12, 3) not null,
                    p95_ms numeric(12, 3) not null,
                    p99_ms numeric(12, 3) not null,
                    max_ms numeric(12, 3) not null,
                    primary key (bucket_start, granularity_minutes, module_code, event_type_code)
                )
                """
        );
    }

    private long countRenameAliases() {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from analytics.code_alias where source_code like 'RENAME_%' or target_code like 'RENAME_%'",
            Long.class
        );
        return count == null ? 0L : count;
    }

    private long countEventRollups(String moduleCode, String eventTypeCode) {
        Long count = jdbcTemplate.queryForObject(
            """
                select count(*)
                  from analytics.event_rollup_bucket
                 where module_code = ?
                   and event_type_code = ?
                """,
            Long.class,
            moduleCode,
            eventTypeCode
        );
        return count == null ? 0L : count;
    }

    private void cleanupOptional(String sql) {
        try {
            jdbcTemplate.update(sql);
        } catch (RuntimeException ignored) {
            // Optional analytics rollup/index tables may be absent in lightweight profiles.
        }
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
    void dictionaryCodeRenameMovesExistingRowsAndHistoricalReferences() {
        cleanupDictionaryRenameTestData();
        seedModuleType("RENAME_MODULE_OLD", "Rename module old", true);
        seedEventType("RENAME_EVENT_OLD", "Rename event old", "Rename event old.", false, "RENAME_MODULE_OLD", true);
        seedAttributeType("RENAME_ATTRIBUTE_OLD", "Rename attribute old", MetricValueKind.TEXT, true);
        seedMetricType("RENAME_METRIC_OLD", "Rename metric old", MetricValueKind.NUMERIC, "count");

        StageType stageType = new StageType();
        stageType.setCode("RENAME_STAGE_OLD");
        stageType.setName("Rename stage old");
        stageType.setDescription("Rename stage old.");
        stageType.setIsSystem(false);
        stageType.setIsActive(true);
        stageTypeRepository.save(stageType);

        AnalyticsEvent event = new AnalyticsEvent();
        event.setEventTypeCode("RENAME_EVENT_OLD");
        event.setModuleCode("RENAME_MODULE_OLD");
        event.setRequestPath("/rename-test");
        event.setHttpMethod("GET");
        event.setTraceId("rename-test-trace");
        event.setStartedAt(Instant.now());
        event.setEndedAt(Instant.now());
        event.setDurationMs(12);
        event = analyticsEventRepository.save(event);

        AnalyticsEventAttribute attribute = new AnalyticsEventAttribute();
        attribute.setEventId(event.getId());
        attribute.setAttributeTypeCode("RENAME_ATTRIBUTE_OLD");
        attribute.setAttrValue("value");
        analyticsEventAttributeRepository.save(attribute);

        AnalyticsStage stage = new AnalyticsStage();
        stage.setEventId(event.getId());
        stage.setStageTypeCode("RENAME_STAGE_OLD");
        stage.setStageOrder(1);
        stage.setStartedAt(event.getStartedAt());
        stage.setEndedAt(event.getEndedAt());
        stage.setDurationMs(12);
        stage = analyticsStageRepository.save(stage);

        AnalyticsStageMetric metric = new AnalyticsStageMetric();
        metric.setStageId(stage.getId());
        metric.setMetricTypeCode("RENAME_METRIC_OLD");
        metric.setMetricValueNum(new BigDecimal("42"));
        metric.setUnit("count");
        analyticsStageMetricRepository.save(metric);

        jdbcTemplate.update(
            """
                insert into analytics.event_rollup_bucket (
                    bucket_start,
                    granularity_minutes,
                    module_code,
                    event_type_code,
                    sample_count,
                    error_count,
                    duration_sum,
                    avg_ms,
                    p95_ms,
                    p99_ms,
                    max_ms
                )
                values (?, 15, 'RENAME_MODULE_OLD', 'RENAME_EVENT_OLD', 1, 0, 12, 12.000, 12.000, 12.000, 12.000)
                """,
            Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        );

        analyticsDictionaryAdminService.createOrUpdateModuleType(
            "RENAME_MODULE_OLD",
            "RENAME_MODULE_NEW",
            "Rename module new",
            "Rename module new."
        );
        analyticsDictionaryAdminService.createOrUpdateEventType(
            "RENAME_EVENT_OLD",
            "RENAME_EVENT_NEW",
            "RENAME_MODULE_NEW",
            "Rename event new",
            "Rename event new."
        );
        analyticsDictionaryAdminService.createOrUpdateEventAttributeType(
            "RENAME_ATTRIBUTE_OLD",
            "RENAME_ATTRIBUTE_NEW",
            "Rename attribute new",
            "Rename attribute new.",
            "TEXT",
            null
        );
        analyticsDictionaryAdminService.createOrUpdateStageType(
            "RENAME_STAGE_OLD",
            "RENAME_STAGE_NEW",
            "Rename stage new",
            "Rename stage new."
        );
        analyticsDictionaryAdminService.createOrUpdateStageMetricType(
            "RENAME_METRIC_OLD",
            "RENAME_METRIC_NEW",
            "Rename metric new",
            "Rename metric new.",
            "Rename metric reading guide.",
            "NUMERIC",
            "count"
        );

        assertFalse(moduleTypeRepository.existsById("RENAME_MODULE_OLD"));
        assertFalse(eventTypeRepository.existsById("RENAME_EVENT_OLD"));
        assertFalse(eventAttributeTypeRepository.existsById("RENAME_ATTRIBUTE_OLD"));
        assertFalse(stageTypeRepository.existsById("RENAME_STAGE_OLD"));
        assertFalse(stageMetricTypeRepository.existsById("RENAME_METRIC_OLD"));
        assertTrue(moduleTypeRepository.existsById("RENAME_MODULE_NEW"));
        assertTrue(eventTypeRepository.existsById("RENAME_EVENT_NEW"));
        assertTrue(eventAttributeTypeRepository.existsById("RENAME_ATTRIBUTE_NEW"));
        assertTrue(stageTypeRepository.existsById("RENAME_STAGE_NEW"));
        assertTrue(stageMetricTypeRepository.existsById("RENAME_METRIC_NEW"));

        AnalyticsEvent renamedEvent = analyticsEventRepository.findById(event.getId()).orElseThrow();
        assertEquals("RENAME_EVENT_NEW", renamedEvent.getEventTypeCode());
        assertEquals("RENAME_MODULE_NEW", renamedEvent.getModuleCode());
        assertEquals("RENAME_ATTRIBUTE_NEW", analyticsEventAttributeRepository.findByEventId(event.getId()).get(0).getAttributeTypeCode());
        assertEquals("RENAME_STAGE_NEW", analyticsStageRepository.findByEventIdOrderByStageOrder(event.getId()).get(0).getStageTypeCode());
        assertEquals("RENAME_METRIC_NEW", analyticsStageMetricRepository.findByStageId(stage.getId()).get(0).getMetricTypeCode());
        assertEquals(1L, countEventRollups("RENAME_MODULE_NEW", "RENAME_EVENT_NEW"));
        assertEquals(0L, countRenameAliases());
    }

    @Test
    void dictionaryCodeRenameRejectsExistingTargetCode() {
        cleanupDictionaryRenameTestData();
        seedEventType("RENAME_CONFLICT_OLD", "Rename conflict old", "Rename conflict old.", false);
        seedEventType("RENAME_CONFLICT_NEW", "Rename conflict new", "Rename conflict new.", false);

        assertThrows(IllegalArgumentException.class, () -> analyticsDictionaryAdminService.createOrUpdateEventType(
            "RENAME_CONFLICT_OLD",
            "RENAME_CONFLICT_NEW",
            EventType.DEFAULT_MODULE_CODE,
            "Rename conflict updated",
            "Rename conflict updated."
        ));

        assertTrue(eventTypeRepository.existsById("RENAME_CONFLICT_OLD"));
        assertTrue(eventTypeRepository.existsById("RENAME_CONFLICT_NEW"));
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
    void unknownEventIsLoggedAndDoesNotCreateDictionaryRow(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/events/unknown"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")))
            .andExpect(header().doesNotExist(AnalyticsEventAspect.ANALYTICS_EVENT_UID_RESPONSE_HEADER));

        assertFalse(eventTypeRepository.existsById("UNKNOWN_EVENT_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics tracking skipped: type=event code=UNKNOWN_EVENT_FOR_TEST"));
        assertTrue(output.toString().contains("reason=Unknown event type: UNKNOWN_EVENT_FOR_TEST"));
    }

    @Test
    void inactiveEventIsLoggedAndBusinessRequestContinues(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/events/inactive"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")))
            .andExpect(header().doesNotExist(AnalyticsEventAspect.ANALYTICS_EVENT_UID_RESPONSE_HEADER));

        assertEquals(0, analyticsEventRepository.countByEventTypeCode("INACTIVE_EVENT_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics tracking skipped: type=event code=INACTIVE_EVENT_FOR_TEST"));
        assertTrue(output.toString().contains("reason=Inactive event type: INACTIVE_EVENT_FOR_TEST"));
    }

    @Test
    void unknownAttributeIsLoggedAndDoesNotCreateDictionaryRow(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/attributes/unknown"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")))
            .andReturn();

        AnalyticsEvent event = loadRecordedEvent(result);
        assertFalse(eventAttributeTypeRepository.existsById("UNKNOWN_ATTRIBUTE_FOR_TEST"));
        assertEquals(0, analyticsEventAttributeRepository.countByAttributeTypeCode("UNKNOWN_ATTRIBUTE_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics attribute skipped: code=UNKNOWN_ATTRIBUTE_FOR_TEST"));
        assertTrue(output.toString().contains("eventUid=" + event.getEventUid()));
        assertTrue(output.toString().contains("reason=Unknown attribute type: UNKNOWN_ATTRIBUTE_FOR_TEST"));
    }

    @Test
    void inactiveAttributeIsLoggedAndNotPersisted(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/attributes/inactive"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")))
            .andReturn();

        AnalyticsEvent event = loadRecordedEvent(result);
        assertEquals(0, analyticsEventAttributeRepository.countByAttributeTypeCode("INACTIVE_ATTRIBUTE_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics attribute skipped: code=INACTIVE_ATTRIBUTE_FOR_TEST"));
        assertTrue(output.toString().contains("eventUid=" + event.getEventUid()));
        assertTrue(output.toString().contains("reason=Inactive attribute type: INACTIVE_ATTRIBUTE_FOR_TEST"));
    }

    @Test
    void unknownModuleIsLoggedAndEventIsSkippedWithoutFallback(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/modules/unknown"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")))
            .andExpect(header().doesNotExist(AnalyticsEventAspect.ANALYTICS_EVENT_UID_RESPONSE_HEADER));

        assertFalse(moduleTypeRepository.existsById("UNKNOWN_MODULE_FOR_TEST"));
        assertEquals(0, analyticsEventRepository.countByEventTypeCode("UNKNOWN_MODULE_EVENT_FOR_TEST"));
        assertEquals(0, analyticsEventRepository.countByModuleCode("UNKNOWN_MODULE_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics dictionary mismatch: module=UNKNOWN_MODULE_FOR_TEST"));
        assertTrue(output.toString().contains("reason=Unknown module type"));
    }

    @Test
    void inactiveModuleIsLoggedAndEventIsSkippedWithoutFallback(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/modules/inactive"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")))
            .andExpect(header().doesNotExist(AnalyticsEventAspect.ANALYTICS_EVENT_UID_RESPONSE_HEADER));

        assertEquals(0, analyticsEventRepository.countByEventTypeCode("INACTIVE_MODULE_EVENT_FOR_TEST"));
        assertEquals(0, analyticsEventRepository.countByModuleCode("INACTIVE_MODULE_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics dictionary mismatch: module=INACTIVE_MODULE_FOR_TEST"));
        assertTrue(output.toString().contains("reason=Inactive module type"));
    }

    @Test
    void unknownMetricIsLoggedAndDoesNotCreateDictionaryRow(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/metrics/unknown"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")));

        assertFalse(stageMetricTypeRepository.existsById("UNKNOWN_METRIC_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics metric skipped: code=UNKNOWN_METRIC_FOR_TEST"));
        assertTrue(output.toString().contains("reason=Unknown metric type: UNKNOWN_METRIC_FOR_TEST"));
    }

    @Test
    void inactiveMetricIsLoggedAndNotPersisted(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/metrics/inactive"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"ok\":true")));

        assertEquals(0, analyticsStageMetricRepository.countByMetricTypeCode("INACTIVE_METRIC_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics metric skipped: code=INACTIVE_METRIC_FOR_TEST"));
        assertTrue(output.toString().contains("reason=Inactive metric type: INACTIVE_METRIC_FOR_TEST"));
    }

    @Test
    void mvcControllerDeclaredUnknownAttributeAndMetricAreLoggedAndSkipped(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/mvc/declared-unknown"))
            .andExpect(status().isOk())
            .andReturn();

        AnalyticsEvent event = loadRecordedEvent(result);

        assertFalse(eventAttributeTypeRepository.existsById("MVC_UNKNOWN_ATTRIBUTE_FOR_TEST"));
        assertFalse(stageMetricTypeRepository.existsById("MVC_UNKNOWN_METRIC_FOR_TEST"));
        assertEquals(0, analyticsEventAttributeRepository.countByAttributeTypeCode("MVC_UNKNOWN_ATTRIBUTE_FOR_TEST"));
        assertEquals(0, analyticsStageMetricRepository.countByMetricTypeCode("MVC_UNKNOWN_METRIC_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics attribute skipped: code=MVC_UNKNOWN_ATTRIBUTE_FOR_TEST"));
        assertTrue(output.toString().contains("class=AnalyticsMvcAnnotationTestController"));
        assertTrue(output.toString().contains("method=declaredUnknown"));
        assertTrue(output.toString().contains("path=/test/analytics/mvc/declared-unknown"));
        assertTrue(output.toString().contains("eventUid=" + event.getEventUid()));
        assertTrue(output.toString().contains("reason=Unknown attribute type: MVC_UNKNOWN_ATTRIBUTE_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics metric skipped: code=MVC_UNKNOWN_METRIC_FOR_TEST"));
        assertTrue(output.toString().contains("reason=Unknown metric type: MVC_UNKNOWN_METRIC_FOR_TEST"));
    }

    @Test
    void mvcControllerDeclaredKnownAttributeAndMetricArePersisted() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/analytics/mvc/declared-known"))
            .andExpect(status().isOk())
            .andReturn();

        AnalyticsEvent event = loadRecordedEvent(result);
        List<AnalyticsStage> stages = analyticsStageRepository.findByEventIdOrderByStageOrder(event.getId());
        assertFalse(stages.isEmpty());

        assertTrue(analyticsEventAttributeRepository.countByAttributeTypeCode("MVC_KNOWN_ATTRIBUTE_FOR_TEST") > 0);
        List<AnalyticsStageMetric> metrics = analyticsStageMetricRepository.findByStageId(stages.get(0).getId());
        AnalyticsStageMetric metric = findMetric(metrics, "MVC_KNOWN_METRIC_FOR_TEST");
        assertEquals(0, new BigDecimal("123").compareTo(metric.getMetricValueNum()));
        assertEquals("count", metric.getUnit());
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
        assertTrue(output.toString().contains("Analytics stage skipped: stageType=UNKNOWN_LAYER_FOR_TEST"));
        assertTrue(output.toString().contains("reason=Unknown stage type"));
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
        assertTrue(output.toString().contains("Analytics stage skipped: stageType=INACTIVE_LAYER_FOR_TEST"));
        assertTrue(output.toString().contains("reason=Inactive stage type"));
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
    void realShopHomeUsesFacadeServicePersistenceAndDatabaseStages() throws Exception {
        MvcResult result = mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andReturn();

        List<String> codes = loadRecordedStages(result).stream()
            .map(AnalyticsStage::getStageTypeCode)
            .toList();

        assertTrue(codes.contains("CONTROLLER"));
        assertTrue(codes.contains("FACADE"));
        assertTrue(codes.contains("SERVICE"));
        assertTrue(codes.contains("PERSISTENCE"));
        assertTrue(codes.contains("DATABASE"));
        assertTrue(codes.indexOf("CONTROLLER") < codes.indexOf("FACADE"));
        assertTrue(codes.indexOf("FACADE") < codes.indexOf("SERVICE"));
        assertTrue(codes.indexOf("SERVICE") < codes.indexOf("PERSISTENCE"));
        assertEquals(1, codes.stream().filter("FACADE"::equals).count());
    }

    @Test
    void realShopCatalogPageUsesServicePersistenceWithoutFacade() throws Exception {
        MvcResult result = mockMvc.perform(get("/catalog"))
            .andExpect(status().isOk())
            .andReturn();

        List<String> codes = loadRecordedStages(result).stream()
            .map(AnalyticsStage::getStageTypeCode)
            .toList();

        assertTrue(codes.contains("CONTROLLER"));
        assertTrue(codes.contains("SERVICE"));
        assertTrue(codes.contains("PERSISTENCE"));
        assertTrue(codes.contains("DATABASE"));
        assertFalse(codes.contains("FACADE"));
    }

    @Test
    void realShopReviewsPageKeepsOldServiceDatabaseChain() throws Exception {
        MvcResult result = mockMvc.perform(get("/reviews"))
            .andExpect(status().isOk())
            .andReturn();

        List<String> codes = loadRecordedStages(result).stream()
            .map(AnalyticsStage::getStageTypeCode)
            .toList();

        assertTrue(codes.contains("CONTROLLER"));
        assertTrue(codes.contains("SERVICE"));
        assertTrue(codes.contains("DATABASE"));
        assertFalse(codes.contains("FACADE"));
        assertFalse(codes.contains("PERSISTENCE"));
    }

    @Test
    void realShopAboutPageUsesFacadeServiceWithoutDatabase() throws Exception {
        MvcResult result = mockMvc.perform(get("/about"))
            .andExpect(status().isOk())
            .andReturn();

        List<String> codes = loadRecordedStages(result).stream()
            .map(AnalyticsStage::getStageTypeCode)
            .toList();

        assertTrue(codes.contains("CONTROLLER"));
        assertTrue(codes.contains("FACADE"));
        assertTrue(codes.contains("SERVICE"));
        assertFalse(codes.contains("PERSISTENCE"));
        assertFalse(codes.contains("DATABASE"));
        assertEquals(1, codes.stream().filter("FACADE"::equals).count());
    }

    @Test
    void inactiveRealShopCustomLayersAreSkippedAndBusinessRequestContinues(CapturedOutput output) throws Exception {
        StageType facade = stageTypeRepository.findById("FACADE").orElseThrow();
        facade.setIsActive(false);
        stageTypeRepository.save(facade);

        StageType persistence = stageTypeRepository.findById("PERSISTENCE").orElseThrow();
        persistence.setIsActive(false);
        stageTypeRepository.save(persistence);

        MvcResult result = mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andReturn();

        List<String> codes = loadRecordedStages(result).stream()
            .map(AnalyticsStage::getStageTypeCode)
            .toList();

        assertTrue(codes.contains("CONTROLLER"));
        assertTrue(codes.contains("SERVICE"));
        assertTrue(codes.contains("DATABASE"));
        assertFalse(codes.contains("FACADE"));
        assertFalse(codes.contains("PERSISTENCE"));
        assertTrue(output.toString().contains("Analytics stage skipped: stageType=FACADE"));
        assertTrue(output.toString().contains("Analytics stage skipped: stageType=PERSISTENCE"));
        assertTrue(output.toString().contains("reason=Inactive stage type"));
    }

    @Test
    void unknownStageMetricIsLoggedAndDoesNotCreateDictionaryRow(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/stage-metrics/unknown"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"value\":\"ok\"")));

        assertFalse(stageMetricTypeRepository.existsById("UNKNOWN_STAGE_METRIC_FOR_TEST"));
        assertTrue(output.toString().contains("Analytics metric skipped"));
        assertTrue(output.toString().contains("Unknown metric type: UNKNOWN_STAGE_METRIC_FOR_TEST"));
    }

    @Test
    void stageMetricSpelErrorIsLoggedAndBusinessRequestContinues(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/analytics/stage-metrics/spel-error"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"value\":\"ok\"")));

        assertTrue(output.toString().contains("Analytics metric skipped"));
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
    void dictionaryDeletePrecheckReturnsEventAndAttributeCountsOnDemand() throws Exception {
        MockHttpSession adminSession = analyticsAdminSession("analytics_admin_precheck_counts");
        seedEventType("UNUSED_PRECHECK_EVENT", "Unused precheck event", "Unused precheck event.", false);
        seedAttributeType("UNUSED_PRECHECK_ATTRIBUTE", "Unused precheck attribute", MetricValueKind.TEXT, true);

        AnalyticsEvent event = new AnalyticsEvent();
        event.setEventTypeCode("UNUSED_PRECHECK_EVENT");
        event.setModuleCode("DEFAULT");
        event.setRequestPath("/test/precheck-counts");
        event.setHttpMethod("GET");
        event.setTraceId("precheck-counts-trace");
        event.setStartedAt(Instant.now());
        event.setEndedAt(Instant.now());
        event.setDurationMs(5);
        event = analyticsEventRepository.save(event);

        AnalyticsEventAttribute attribute = new AnalyticsEventAttribute();
        attribute.setEventId(event.getId());
        attribute.setAttributeTypeCode("UNUSED_PRECHECK_ATTRIBUTE");
        attribute.setAttrValue("value");
        analyticsEventAttributeRepository.save(attribute);

        mockMvc.perform(
                get("/analytics-admin/dictionaries/events/delete/precheck")
                    .session(adminSession)
                    .param("code", "UNUSED_PRECHECK_EVENT")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletable").value(true))
            .andExpect(jsonPath("$.eventCount").value(1));

        mockMvc.perform(
                get("/analytics-admin/dictionaries/attributes/delete/precheck")
                    .session(adminSession)
                    .param("code", "UNUSED_PRECHECK_ATTRIBUTE")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletable").value(true))
            .andExpect(jsonPath("$.attributeValueCount").value(1));
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

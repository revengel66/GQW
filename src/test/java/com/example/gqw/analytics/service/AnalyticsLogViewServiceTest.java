package com.example.gqw.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventLogEntryDto;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class AnalyticsLogViewServiceTest {

    @Test
    void customLayerStageRowsKeepLayerFromMessage() {
        EventLogEntryDto facadeStart = normalize(
            "LAYER_STAGE_START stageId=11 layer=FACADE operation='CatalogFacade.homePage' traceId='t1' eventUid='e1'"
        );
        EventLogEntryDto persistenceEnd = normalize(
            "LAYER_STAGE_END stageId=12 layer=PERSISTENCE operation='CatalogPersistence.latestProducts' traceId='t1' eventUid='e1'"
        );
        EventLogEntryDto facadeError = normalize(
            "LAYER_STAGE_ERROR stageId=13 layer=FACADE operation='CatalogFacade.homePage' traceId='t1' eventUid='e1'"
        );

        assertEquals("START", facadeStart.status());
        assertEquals("FACADE", facadeStart.layer());
        assertEquals("CatalogFacade.homePage", facadeStart.operation());
        assertEquals("OK", persistenceEnd.status());
        assertEquals("PERSISTENCE", persistenceEnd.layer());
        assertEquals("CatalogPersistence.latestProducts", persistenceEnd.operation());
        assertEquals("ERROR", facadeError.status());
        assertEquals("FACADE", facadeError.layer());
    }

    @Test
    void knownSystemLayersAndUnknownFallbackArePreserved() {
        EventLogEntryDto controller = normalize(
            "Method started CatalogController.home (operation='Home page', layer=CONTROLLER, traceId='t1', eventUid='e1')."
        );
        EventLogEntryDto service = normalize(
            "Method finished successfully CatalogService.latestProducts: operation='Latest products', layer=SERVICE, durationMs=5, traceId='t1', eventUid='e1'."
        );
        EventLogEntryDto database = normalize(
            "Database call completed successfully ProductRepository.findAll: layer=DATABASE, stageId=7, durationMs=3, traceId='t1', eventUid='e1'."
        );
        EventLogEntryDto unknown = normalize("Unstructured application line");

        assertEquals("CONTROLLER", controller.layer());
        assertEquals("SERVICE", service.layer());
        assertEquals("DATABASE", database.layer());
        assertEquals("UNKNOWN", unknown.layer());
    }

    @Test
    void userLogCaptureDisabledKeepsBuiltInAnalyticsRowsOnly() {
        AnalyticsLogViewService service = logViewServiceWithUserLogCapture(false);
        EventLogEntryDto builtIn = normalize(
            "DB_STAGE_START stageId=7 method=ProductRepository.findAll traceId='t1' eventUid='e1'"
        );
        EventLogEntryDto userLog = normalize(
            "Custom business log: orderId=42, status=OK"
        );

        assertTrue(include(service, builtIn));
        assertFalse(include(service, userLog));
    }

    @Test
    void userLogCaptureEnabledKeepsApplicationRows() {
        AnalyticsLogViewService service = logViewServiceWithUserLogCapture(true);
        EventLogEntryDto userLog = normalize(
            "Custom business log: orderId=42, status=OK"
        );

        assertTrue(include(service, userLog));
    }

    private static EventLogEntryDto normalize(String message) {
        return AnalyticsLogViewService.normalizedLogEntryDto(
            Instant.EPOCH,
            "INFO",
            "analytics.layer.stage",
            message,
            "trace",
            "event",
            "SHOP"
        );
    }

    private static boolean include(AnalyticsLogViewService service, EventLogEntryDto row) {
        Boolean result = ReflectionTestUtils.invokeMethod(service, "shouldIncludeLogRow", row);
        return Boolean.TRUE.equals(result);
    }

    private static AnalyticsLogViewService logViewServiceWithUserLogCapture(boolean enabled) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:log-view-policy-test-" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create schema if not exists analytics");
        jdbcTemplate.execute(
            """
                create table analytics.runtime_setting (
                    setting_key varchar(160) not null primary key,
                    setting_value varchar(2048),
                    updated_at timestamp with time zone not null default now(),
                    updated_by varchar(128)
                )
            """
        );
        AnalyticsRuntimeSettingsService settingsService = new AnalyticsRuntimeSettingsService(
            new NamedParameterJdbcTemplate(dataSource)
        );
        settingsService.update(Map.of(
            AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_USER_LOG_CAPTURE_ENABLED,
            Boolean.toString(enabled)
        ), "test");
        return new AnalyticsLogViewService(null, new AnalyticsLoggingPolicy(settingsService));
    }
}

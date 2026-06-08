package com.example.gqw.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AnalyticsRuntimeSettingsServiceTest {

    private AnalyticsRuntimeSettingsService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:runtime-settings-test-" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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
        service = new AnalyticsRuntimeSettingsService(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void savesAndReadsBooleanValue() {
        service.update(Map.of(AnalyticsRuntimeSettingsService.KEY_TIME_ROLLUP_ENABLED, "false"), "test");

        assertFalse(service.getBoolean(AnalyticsRuntimeSettingsService.KEY_TIME_ROLLUP_ENABLED, true));
        AnalyticsRuntimeSettingsService.SettingView setting = findSetting(
            service.view(),
            AnalyticsRuntimeSettingsService.KEY_TIME_ROLLUP_ENABLED
        );
        assertEquals("false", setting.value());
        assertTrue(setting.custom());
    }

    @Test
    void rejectsIntegerBelowMinimumInsteadOfClampingSilently() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.update(Map.of(AnalyticsRuntimeSettingsService.KEY_RAW_RETENTION_DAYS, "0"), "test")
        );

        assertTrue(ex.getMessage().contains("7"));
    }

    @Test
    void rejectsNonIntegerValue() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.update(Map.of(AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_BATCH_SIZE, "abc"), "test")
        );

        assertTrue(ex.getMessage() != null && !ex.getMessage().isBlank());
    }

    @Test
    void rejectsBlankIntegerValueInsteadOfResettingToDefault() {
        service.update(Map.of(AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_BATCH_SIZE, "7"), "test");

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.update(Map.of(AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_BATCH_SIZE, ""), "test")
        );

        assertTrue(ex.getMessage().contains(AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_BATCH_SIZE));
        assertEquals(7, service.getInt(AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_BATCH_SIZE, 20, 1, 500));
    }

    @Test
    void rejectsUnknownEnumValue() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.update(Map.of(AnalyticsRuntimeSettingsService.KEY_COLD_PROVIDER, "FTP"), "test")
        );

        assertTrue(ex.getMessage() != null && !ex.getMessage().isBlank());
    }

    @Test
    void loggingSettingsAreExposedAndPolicyUsesRuntimeValues() {
        AnalyticsRuntimeSettingsService.SettingView enabled = findSetting(
            service.view(),
            AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_ENABLED
        );
        AnalyticsRuntimeSettingsService.SettingView level = findSetting(
            service.view(),
            AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_LEVEL
        );
        AnalyticsLoggingPolicy policy = new AnalyticsLoggingPolicy(service);

        assertEquals("BOOLEAN", enabled.kind());
        assertEquals("true", enabled.defaultValue());
        assertTrue(enabled.label().contains("Analytics"));
        assertEquals("ENUM", level.kind());
        assertTrue(policy.isInfoEnabled());
        assertTrue(policy.isControllerEnabled());

        service.update(Map.of(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_LEVEL, "WARN"), "test");
        assertFalse(policy.isInfoEnabled());
        assertTrue(policy.isWarnEnabled());
        assertTrue(policy.isErrorEnabled());

        service.update(Map.of(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_ENABLED, "false"), "test");
        assertFalse(policy.isControllerEnabled());
        assertFalse(policy.isDatabaseEnabled());
        assertTrue(policy.isStrictWarningsEnabled());

        service.update(Map.of(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_LEVEL, "ERROR"), "test");
        assertFalse(policy.isStrictWarningsEnabled());

        service.update(Map.of(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_STRICT_WARNINGS_ENABLED, "false"), "test");
        assertFalse(policy.isStrictWarningsEnabled());
    }

    private static AnalyticsRuntimeSettingsService.SettingView findSetting(
        AnalyticsRuntimeSettingsService.SettingsView view,
        String key
    ) {
        return view.groups().stream()
            .flatMap(group -> group.settings().stream())
            .filter(setting -> setting.key().equals(key))
            .findFirst()
            .orElseThrow();
    }
}

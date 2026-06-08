package com.example.gqw.config;

import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsSystemFlagsSchemaPatchConfig {

    private static final List<String> SYSTEM_STAGE_CODES = List.of(
        "CONTROLLER",
        "SERVICE",
        "DATABASE",
        "FRONTEND"
    );

    private static final List<String> SYSTEM_ATTRIBUTE_CODES = List.of(
        "HTTP_METHOD",
        "HTTP_PATH",
        "HTTP_STATUS",
        "ERROR_CODE",
        "ERROR_CLASS",
        "CLIENT_TYPE",
        "USER_AGENT",
        "REFERRER",
        "SESSION_ID_HASH",
        "USER_ID_HASH",
        "REQUEST_ID"
    );

    private static final List<String> SYSTEM_METRIC_CODES = List.of(
        "DB_QUERY_COUNT",
        "RESPONSE_SIZE_BYTES",
        "RETRY_COUNT",
        "ERROR_CODE",
        "ERROR_CLASS",
        "ITEM_COUNT",
        "PAYLOAD_SIZE_BYTES",
        "VALIDATION_ERROR_COUNT",
        "FRONTEND_TTFB_MS",
        "FRONTEND_DOM_INTERACTIVE_MS",
        "FRONTEND_DOM_CONTENT_LOADED_MS",
        "FRONTEND_LOAD_EVENT_MS",
        "FRONTEND_TRANSFER_SIZE_BYTES",
        "FRONTEND_LCP_MS",
        "FRONTEND_INP_MS",
        "FRONTEND_CLS_SCORE",
        "FRONTEND_API_DURATION_MS",
        "FRONTEND_RENDER_AFTER_API_MS",
        "FRONTEND_HTTP_STATUS",
        "FRONTEND_PAGE_URL",
        "FRONTEND_NAV_TYPE",
        "FRONTEND_API_URL",
        "FRONTEND_API_METHOD",
        "FRONTEND_NETWORK_ERROR",
        "FRONTEND_ERROR_MESSAGE",
        "FRONTEND_TRACE_ID",
        "FRONTEND_CUSTOM_ATTRS_JSON"
    );

    private static final List<String> SYSTEM_EVENT_CODES = List.of(
        "FRONTEND_PAGE_LOAD",
        "FRONTEND_WEB_VITALS",
        "FRONTEND_API_CALL",
        "FRONTEND_JS_ERROR",
        "HTTP_REQUEST_ERROR"
    );

    @Bean
    @Order(90)
    CommandLineRunner patchAnalyticsSystemFlagsSchema(@Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!schemaExists(jdbcTemplate, "analytics")) {
                return;
            }

            patchStageMetricTypeSchema(jdbcTemplate);
            patchTable(jdbcTemplate, "analytics.event_type", "code", SYSTEM_EVENT_CODES);
            patchHistoricalSystemEventTypes(jdbcTemplate);
            patchTable(jdbcTemplate, "analytics.event_attribute_type", "code", SYSTEM_ATTRIBUTE_CODES);
            patchTable(jdbcTemplate, "analytics.stage_type", "code", SYSTEM_STAGE_CODES);
            patchTable(jdbcTemplate, "analytics.stage_metric_type", "code", SYSTEM_METRIC_CODES);
        };
    }

    private void patchTable(
        JdbcTemplate jdbcTemplate,
        String tableName,
        String codeColumn,
        List<String> systemCodes
    ) {
        if (!tableExists(jdbcTemplate, tableName)) {
            return;
        }
        jdbcTemplate.execute("alter table " + tableName + " add column if not exists is_system boolean");
        jdbcTemplate.execute("update " + tableName + " set is_system = false where is_system is null");
        jdbcTemplate.execute("alter table " + tableName + " alter column is_system set not null");
        jdbcTemplate.execute("alter table " + tableName + " alter column is_system set default false");
        for (String code : systemCodes) {
            jdbcTemplate.update(
                "update " + tableName + " set is_system = true where " + codeColumn + " = ?",
                code
            );
        }
    }

    private void patchHistoricalSystemEventTypes(JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "analytics.event_type") || !tableExists(jdbcTemplate, "analytics.event")) {
            return;
        }
        jdbcTemplate.execute(
            """
                update analytics.event_type t
                   set is_system = true
                 where exists (
                       select 1
                         from analytics.event e
                        where e.event_type_code = t.code
                          and (
                               lower(coalesce(e.request_path, '')) in ('/favicon.ico', '/robots.txt', '/error')
                               or lower(coalesce(e.request_path, '')) like '/static/%'
                               or lower(coalesce(e.request_path, '')) like '/css/%'
                               or lower(coalesce(e.request_path, '')) like '/js/%'
                               or lower(coalesce(e.request_path, '')) like '/images/%'
                               or lower(coalesce(e.request_path, '')) like '/img/%'
                               or lower(coalesce(e.request_path, '')) like '/webjars/%'
                               or lower(coalesce(e.request_path, '')) like '/actuator/%'
                               or (
                                   e.status_code = 404
                                   and lower(coalesce(e.request_path, '')) ~ '\\.(css|js|map|ico|png|jpg|jpeg|svg|gif|webp|woff|woff2|ttf)(\\?.*)?$'
                               )
                          )
                 )
                """
        );
    }

    private void patchStageMetricTypeSchema(JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "analytics.stage_metric_type")) {
            return;
        }
        jdbcTemplate.execute(
            "alter table analytics.stage_metric_type add column if not exists reading_guide varchar(2048)"
        );
    }

    private static boolean tableExists(JdbcTemplate jdbcTemplate, String regclass) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select to_regclass(?) is not null",
            Boolean.class,
            regclass
        );
        return Boolean.TRUE.equals(exists);
    }

    private static boolean schemaExists(JdbcTemplate jdbcTemplate, String schemaName) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select exists(select 1 from information_schema.schemata where schema_name = ?)",
            Boolean.class,
            schemaName
        );
        return Boolean.TRUE.equals(exists);
    }
}

package com.example.gqw.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsPerformanceIndexesConfig {

    @Bean
    @Order(210)
    CommandLineRunner ensureAnalyticsPerformanceIndexes(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!tableExists(jdbcTemplate, "analytics.event") || !tableExists(jdbcTemplate, "analytics.event_attribute")) {
                return;
            }

            if (columnExists(jdbcTemplate, "analytics", "event", "started_at")
                && columnExists(jdbcTemplate, "analytics", "event", "module_code")
                && columnExists(jdbcTemplate, "analytics", "event", "event_type_code")) {
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_event_started_module_type
                        on analytics.event (started_at, module_code, event_type_code)
                    """
                );
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_event_type_started
                        on analytics.event (event_type_code, started_at desc, id desc)
                    """
                );
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_event_module_started
                        on analytics.event (module_code, started_at desc, id desc)
                    """
                );
            }

            if (columnExists(jdbcTemplate, "analytics", "event", "started_at")
                && columnExists(jdbcTemplate, "analytics", "event", "request_path")) {
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_event_started_path
                        on analytics.event (started_at, request_path)
                    """
                );
            }

            if (columnExists(jdbcTemplate, "analytics", "event", "started_at")
                && columnExists(jdbcTemplate, "analytics", "event", "is_error")) {
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_event_error_started
                        on analytics.event (is_error, started_at desc, id desc)
                    """
                );
            }

            if (columnExists(jdbcTemplate, "analytics", "event", "started_at")
                && columnExists(jdbcTemplate, "analytics", "event", "status_code")) {
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_event_status_started
                        on analytics.event (status_code, started_at desc, id desc)
                    """
                );
            }

            if (tableExists(jdbcTemplate, "analytics.event_type")
                && columnExists(jdbcTemplate, "analytics", "event_type", "is_system")
                && columnExists(jdbcTemplate, "analytics", "event_type", "code")) {
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_event_type_system_code
                        on analytics.event_type (is_system, code)
                    """
                );
            }

            if (tableExists(jdbcTemplate, "analytics.stage")
                && columnExists(jdbcTemplate, "analytics", "stage", "event_id")) {
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_stage_event_order
                        on analytics.stage (event_id, stage_order)
                    """
                );
            }

            if (tableExists(jdbcTemplate, "analytics.stage_metric")
                && columnExists(jdbcTemplate, "analytics", "stage_metric", "stage_id")
                && columnExists(jdbcTemplate, "analytics", "stage_metric", "metric_type_code")
                && columnExists(jdbcTemplate, "analytics", "stage_metric", "metric_value_num")) {
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_stage_metric_filter
                        on analytics.stage_metric (stage_id, metric_type_code, metric_value_num)
                    """
                );
            }

            if (tableExists(jdbcTemplate, "analytics.stage_metric")
                && columnExists(jdbcTemplate, "analytics", "stage_metric", "recorded_at")
                && columnExists(jdbcTemplate, "analytics", "stage_metric", "metric_type_code")
                && columnExists(jdbcTemplate, "analytics", "stage_metric", "stage_id")) {
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_stage_metric_type_recorded_stage
                        on analytics.stage_metric (metric_type_code, recorded_at, stage_id)
                    """
                );
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_stage_metric_recorded_stage
                        on analytics.stage_metric (recorded_at, stage_id)
                    """
                );
            }

            if (columnExists(jdbcTemplate, "analytics", "event_attribute", "attribute_type_code")
                && columnExists(jdbcTemplate, "analytics", "event_attribute", "event_id")) {
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_event_attribute_code_event
                        on analytics.event_attribute (attribute_type_code, event_id)
                    """
                );
            }

            if (columnExists(jdbcTemplate, "analytics", "event_attribute", "attribute_type_code")
                && columnExists(jdbcTemplate, "analytics", "event_attribute", "attr_value")
                && columnExists(jdbcTemplate, "analytics", "event_attribute", "event_id")) {
                jdbcTemplate.execute(
                    """
                        create index if not exists idx_analytics_event_attribute_code_value_event
                        on analytics.event_attribute (attribute_type_code, attr_value, event_id)
                    """
                );
            }
        };
    }

    private static boolean tableExists(JdbcTemplate jdbcTemplate, String regclass) {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                "select to_regclass(?) is not null",
                Boolean.class,
                regclass
            );
            return Boolean.TRUE.equals(exists);
        } catch (DataAccessException ex) {
            String[] parts = regclass == null ? new String[0] : regclass.split("\\.", 2);
            if (parts.length != 2) {
                return false;
            }
            try {
                Boolean exists = jdbcTemplate.queryForObject(
                    """
                        select exists(
                            select 1
                            from information_schema.tables
                            where lower(table_schema) = lower(?)
                              and lower(table_name) = lower(?)
                        )
                    """,
                    Boolean.class,
                    parts[0],
                    parts[1]
                );
                return Boolean.TRUE.equals(exists);
            } catch (DataAccessException ignored) {
                return false;
            }
        }
    }

    private static boolean columnExists(
        JdbcTemplate jdbcTemplate,
        String schema,
        String table,
        String column
    ) {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                """
                    select exists(
                        select 1
                        from information_schema.columns
                        where lower(table_schema) = lower(?)
                          and lower(table_name) = lower(?)
                          and lower(column_name) = lower(?)
                    )
                """,
                Boolean.class,
                schema,
                table,
                column
            );
            return Boolean.TRUE.equals(exists);
        } catch (DataAccessException ex) {
            return false;
        }
    }
}


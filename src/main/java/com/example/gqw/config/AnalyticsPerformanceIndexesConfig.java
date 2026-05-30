package com.example.gqw.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsPerformanceIndexesConfig {

    @Bean
    CommandLineRunner ensureAnalyticsPerformanceIndexes(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!tableExists(jdbcTemplate, "analytics.event") || !tableExists(jdbcTemplate, "analytics.event_attribute")) {
                return;
            }

            jdbcTemplate.execute(
                """
                    create index if not exists idx_analytics_event_started_module_type
                    on analytics.event (started_at, module_code, event_type_code)
                """
            );
            jdbcTemplate.execute(
                """
                    create index if not exists idx_analytics_event_started_path
                    on analytics.event (started_at, request_path)
                """
            );
            jdbcTemplate.execute(
                """
                    create index if not exists idx_analytics_event_attribute_code_event
                    on analytics.event_attribute (attribute_type_code, event_id)
                """
            );
            jdbcTemplate.execute(
                """
                    create index if not exists idx_analytics_event_attribute_code_value_event
                    on analytics.event_attribute (attribute_type_code, attr_value, event_id)
                """
            );
        };
    }

    private static boolean tableExists(JdbcTemplate jdbcTemplate, String regclass) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select to_regclass(?) is not null",
            Boolean.class,
            regclass
        );
        return Boolean.TRUE.equals(exists);
    }
}


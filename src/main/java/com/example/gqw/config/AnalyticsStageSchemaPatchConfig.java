package com.example.gqw.config;

import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsStageSchemaPatchConfig {

    @Bean
    CommandLineRunner patchAnalyticsStageSchema(@Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!tableExists(jdbcTemplate, "analytics.stage")) {
                return;
            }
            jdbcTemplate.execute("alter table analytics.stage add column if not exists log_started_at timestamp with time zone");
            jdbcTemplate.execute("alter table analytics.stage add column if not exists log_ended_at timestamp with time zone");
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

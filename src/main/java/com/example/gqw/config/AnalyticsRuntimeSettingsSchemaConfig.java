package com.example.gqw.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsRuntimeSettingsSchemaConfig {

    @Bean
    @Order(206)
    CommandLineRunner ensureAnalyticsRuntimeSettingsSchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!schemaExists(jdbcTemplate, "analytics")) {
                return;
            }
            jdbcTemplate.execute(
                """
                    create table if not exists analytics.runtime_setting (
                        setting_key varchar(160) not null primary key,
                        setting_value varchar(2048),
                        updated_at timestamp with time zone not null default now(),
                        updated_by varchar(128)
                    )
                """
            );
        };
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


package com.example.gqw.config;

import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsModuleSchemaPatchConfig {

    private static final String DEFAULT_MODULE_CODE = "DEFAULT";

    @Bean
    @Order(110)
    CommandLineRunner patchAnalyticsModuleSchema(@Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!schemaExists(jdbcTemplate, "analytics")) {
                return;
            }

            jdbcTemplate.execute("""
                create table if not exists analytics.module_type (
                    code varchar(64) primary key,
                    name varchar(128) not null,
                    description varchar(512),
                    is_active boolean not null default true
                )
                """);

            jdbcTemplate.execute("""
                insert into analytics.module_type(code, name, description, is_active)
                values ('DEFAULT', 'Общий', 'Модуль по умолчанию для универсальных событий', true)
                on conflict (code) do update
                set name = excluded.name,
                    description = excluded.description,
                    is_active = true
                """);

            if (tableExists(jdbcTemplate, "analytics.event_type")) {
                jdbcTemplate.execute("alter table analytics.event_type add column if not exists module_code varchar(64)");
                jdbcTemplate.execute("update analytics.event_type set module_code = '" + DEFAULT_MODULE_CODE + "' where module_code is null");
                jdbcTemplate.execute("alter table analytics.event_type alter column module_code set not null");
            }

            if (tableExists(jdbcTemplate, "analytics.event")) {
                jdbcTemplate.execute("alter table analytics.event add column if not exists module_code varchar(64)");
                jdbcTemplate.execute("update analytics.event set module_code = '" + DEFAULT_MODULE_CODE + "' where module_code is null");
                jdbcTemplate.execute("create index if not exists idx_analytics_event_module on analytics.event(module_code)");
            }

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

    private static boolean schemaExists(JdbcTemplate jdbcTemplate, String schemaName) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select exists(select 1 from information_schema.schemata where schema_name = ?)",
            Boolean.class,
            schemaName
        );
        return Boolean.TRUE.equals(exists);
    }
}

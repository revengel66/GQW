package com.example.gqw.config;

import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsFilterRollupSchemaConfig {

    @Bean
    CommandLineRunner ensureAnalyticsFilterRollupSchema(@Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute(
                """
                    create table if not exists analytics.filter_event_type_day (
                        day_start date not null,
                        module_code varchar(64) not null,
                        event_type_code varchar(64) not null,
                        sample_count bigint not null,
                        primary key (day_start, module_code, event_type_code)
                    )
                """
            );

            jdbcTemplate.execute(
                """
                    create table if not exists analytics.filter_attr_value_day (
                        day_start date not null,
                        module_code varchar(64) not null,
                        event_type_code varchar(64) not null,
                        attribute_type_code varchar(64) not null,
                        attribute_value varchar(255) not null,
                        sample_count bigint not null,
                        primary key (day_start, module_code, event_type_code, attribute_type_code, attribute_value)
                    )
                """
            );

            jdbcTemplate.execute(
                """
                    create index if not exists idx_filter_event_type_day_scope
                    on analytics.filter_event_type_day (day_start, module_code, event_type_code)
                """
            );
            jdbcTemplate.execute(
                """
                    create index if not exists idx_filter_attr_value_day_scope
                    on analytics.filter_attr_value_day (day_start, module_code, event_type_code, attribute_type_code)
                """
            );
        };
    }
}


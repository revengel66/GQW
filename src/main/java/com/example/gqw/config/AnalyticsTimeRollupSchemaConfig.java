package com.example.gqw.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsTimeRollupSchemaConfig {

    @Bean
    @Order(205)
    CommandLineRunner ensureAnalyticsTimeRollupSchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!tableExists(jdbcTemplate, "analytics.event") || !tableExists(jdbcTemplate, "analytics.stage")) {
                return;
            }

            jdbcTemplate.execute(
                """
                    create table if not exists analytics.time_rollup_watermark (
                        scope_code varchar(32) not null,
                        granularity_minutes integer not null,
                        watermark_at timestamp with time zone,
                        updated_at timestamp with time zone not null default now(),
                        primary key (scope_code, granularity_minutes)
                    )
                """
            );

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

            jdbcTemplate.execute(
                """
                    create table if not exists analytics.stage_rollup_bucket (
                        bucket_start timestamp with time zone not null,
                        granularity_minutes integer not null,
                        module_code varchar(64) not null,
                        event_type_code varchar(64) not null,
                        stage_type_code varchar(64) not null,
                        sample_count bigint not null,
                        error_count bigint not null,
                        duration_sum bigint not null,
                        avg_ms numeric(12, 3) not null,
                        p95_ms numeric(12, 3) not null,
                        p99_ms numeric(12, 3) not null,
                        max_ms numeric(12, 3) not null,
                        primary key (bucket_start, granularity_minutes, module_code, event_type_code, stage_type_code)
                    )
                """
            );

            jdbcTemplate.execute(
                """
                    create index if not exists idx_event_rollup_scope_bucket
                    on analytics.event_rollup_bucket (granularity_minutes, module_code, event_type_code, bucket_start)
                """
            );
            jdbcTemplate.execute(
                """
                    create index if not exists idx_event_rollup_bucket_only
                    on analytics.event_rollup_bucket (granularity_minutes, bucket_start)
                """
            );
            jdbcTemplate.execute(
                """
                    create index if not exists idx_stage_rollup_scope_bucket
                    on analytics.stage_rollup_bucket (granularity_minutes, module_code, event_type_code, stage_type_code, bucket_start)
                """
            );
            jdbcTemplate.execute(
                """
                    create index if not exists idx_stage_rollup_bucket_only
                    on analytics.stage_rollup_bucket (granularity_minutes, bucket_start)
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

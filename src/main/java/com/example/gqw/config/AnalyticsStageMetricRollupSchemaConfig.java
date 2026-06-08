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
public class AnalyticsStageMetricRollupSchemaConfig {

    @Bean
    @Order(206)
    CommandLineRunner ensureAnalyticsStageMetricRollupSchema(@Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!tableExists(jdbcTemplate, "analytics.event")
                || !tableExists(jdbcTemplate, "analytics.stage")
                || !tableExists(jdbcTemplate, "analytics.stage_metric")) {
                return;
            }

            jdbcTemplate.execute(
                """
                    create table if not exists analytics.stage_metric_rollup_bucket (
                        bucket_start timestamp with time zone not null,
                        granularity_minutes integer not null,
                        module_code varchar(64) not null,
                        event_type_code varchar(64) not null,
                        stage_type_code varchar(64) not null,
                        metric_type_code varchar(64) not null,
                        unit varchar(32),
                        sample_count bigint not null,
                        numeric_count bigint not null,
                        numeric_sum numeric(20, 3) not null,
                        p95_value numeric(20, 3) not null,
                        min_value numeric(20, 3),
                        max_value numeric(20, 3),
                        primary key (
                            bucket_start,
                            granularity_minutes,
                            module_code,
                            event_type_code,
                            stage_type_code,
                            metric_type_code
                        )
                    )
                """
            );

            jdbcTemplate.execute(
                """
                    create index if not exists idx_stage_metric_rollup_scope_bucket
                    on analytics.stage_metric_rollup_bucket (
                        granularity_minutes,
                        module_code,
                        event_type_code,
                        stage_type_code,
                        metric_type_code,
                        bucket_start
                    )
                """
            );
            jdbcTemplate.execute(
                """
                    create unique index if not exists stage_metric_rollup_bucket_pkey
                    on analytics.stage_metric_rollup_bucket (
                        bucket_start,
                        granularity_minutes,
                        module_code,
                        event_type_code,
                        stage_type_code,
                        metric_type_code
                    )
                """
            );
            jdbcTemplate.execute(
                """
                    create index if not exists idx_stage_metric_rollup_bucket_only
                    on analytics.stage_metric_rollup_bucket (granularity_minutes, bucket_start)
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

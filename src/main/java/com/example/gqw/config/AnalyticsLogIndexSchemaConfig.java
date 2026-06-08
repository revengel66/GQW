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
public class AnalyticsLogIndexSchemaConfig {

    @Bean
    @Order(207)
    CommandLineRunner ensureAnalyticsLogIndexSchema(@Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!schemaExists(jdbcTemplate, "analytics")) {
                return;
            }
            jdbcTemplate.execute(
                """
                    create table if not exists analytics.log_file_index (
                        id bigserial primary key,
                        module_code varchar(64) not null,
                        file_name varchar(512) not null,
                        file_path varchar(2048) not null unique,
                        compressed boolean not null default false,
                        from_ts timestamp with time zone,
                        to_ts timestamp with time zone,
                        line_count bigint not null default 0,
                        size_bytes bigint not null default 0,
                        checksum varchar(128),
                        last_modified_at timestamp with time zone,
                        indexed_at timestamp with time zone not null default now(),
                        expires_at timestamp with time zone,
                        status varchar(32) not null default 'ARCHIVED',
                        error_count bigint not null default 0,
                        warn_count bigint not null default 0,
                        trace_count bigint not null default 0
                    )
                """
            );
            jdbcTemplate.execute(
                """
                    create table if not exists analytics.log_trace_index (
                        id bigserial primary key,
                        trace_id varchar(128) not null,
                        event_id varchar(128),
                        module_code varchar(64) not null,
                        file_id bigint not null references analytics.log_file_index(id) on delete cascade,
                        first_ts timestamp with time zone,
                        last_ts timestamp with time zone,
                        line_count bigint not null default 0,
                        error_count bigint not null default 0,
                        warn_count bigint not null default 0,
                        has_error boolean not null default false,
                        top_sources varchar(1024),
                        summary varchar(2048),
                        indexed_at timestamp with time zone not null default now()
                    )
                """
            );
            jdbcTemplate.execute(
                """
                    create table if not exists analytics.log_problem_excerpt (
                        id bigserial primary key,
                        trace_id varchar(128) not null,
                        event_id varchar(128),
                        module_code varchar(64) not null,
                        file_id bigint not null references analytics.log_file_index(id) on delete cascade,
                        timestamp timestamp with time zone,
                        level varchar(32),
                        source varchar(512),
                        message_short varchar(512),
                        excerpt varchar(4096),
                        line_number bigint
                    )
                """
            );
            jdbcTemplate.execute("create index if not exists idx_log_file_index_module on analytics.log_file_index (module_code)");
            jdbcTemplate.execute("create index if not exists idx_log_file_index_status on analytics.log_file_index (status)");
            jdbcTemplate.execute("create index if not exists idx_log_trace_index_trace on analytics.log_trace_index (trace_id)");
            jdbcTemplate.execute("create index if not exists idx_log_trace_index_event on analytics.log_trace_index (event_id)");
            jdbcTemplate.execute("create index if not exists idx_log_trace_index_module_trace on analytics.log_trace_index (module_code, trace_id)");
            jdbcTemplate.execute("create index if not exists idx_log_trace_index_file on analytics.log_trace_index (file_id)");
            jdbcTemplate.execute("create index if not exists idx_log_problem_excerpt_trace on analytics.log_problem_excerpt (trace_id)");
            jdbcTemplate.execute("create index if not exists idx_log_problem_excerpt_file on analytics.log_problem_excerpt (file_id)");
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

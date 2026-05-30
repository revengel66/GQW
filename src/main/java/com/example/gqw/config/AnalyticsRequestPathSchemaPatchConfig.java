package com.example.gqw.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsRequestPathSchemaPatchConfig {

    @Bean
    CommandLineRunner patchAnalyticsRequestPathSchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!tableExists(jdbcTemplate, "analytics.event")) {
                return;
            }

            String requestPathDataType = jdbcTemplate.query(
                """
                    select c.data_type
                    from information_schema.columns c
                    where c.table_schema = 'analytics'
                      and c.table_name = 'event'
                      and c.column_name = 'request_path'
                    """,
                rs -> rs.next() ? rs.getString(1) : null
            );

            if (requestPathDataType == null) {
                return;
            }

            if ("bytea".equalsIgnoreCase(requestPathDataType)) {
                jdbcTemplate.execute(
                    """
                        alter table analytics.event
                        alter column request_path
                        type varchar(1024)
                        using nullif(left(encode(request_path, 'escape'), 1024), '')
                    """
                );
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
}

package com.example.gqw.analytics.config;

import org.springframework.beans.factory.annotation.Qualifier;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

@Configuration
@ConditionalOnProperty(
    value = "app.analytics.event-codes-migration-enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsEventCodesMigrationConfig {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventCodesMigrationConfig.class);

    @Bean
    CommandLineRunner migrateAnalyticsEventCodes(
        @Qualifier("analyticsDataSource") DataSource dataSource,
        @Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate
    ) {
        return args -> {
            if (!schemaExists(jdbcTemplate, "analytics")) {
                return;
            }
            if (!tableExists(jdbcTemplate, "analytics.event_type")) {
                return;
            }

            var script = new ClassPathResource("db/patches/analytics-event-codes-migration.sql");
            try (var connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, script);
                log.info("Applied analytics event codes migration script: {}", script.getPath());
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

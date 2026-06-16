package com.example.gqw.analytics.config;

import com.example.gqw.analytics.service.AnalyticsRollupBootstrapState;
import com.example.gqw.analytics.service.AnalyticsScheduledJobsPolicy;
import java.sql.Connection;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

@Configuration
@ConditionalOnProperty(
    value = "app.analytics.rollup-schema-init-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AnalyticsRollupSchemaConfig {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRollupSchemaConfig.class);
    private static final String ROLLUP_SCHEMA_SCRIPT = "db/analytics-rollup-schema.sql";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    CommandLineRunner initializeAnalyticsRollupSchema(
        @Qualifier("analyticsDataSource") DataSource dataSource,
        @Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate,
        AnalyticsScheduledJobsPolicy scheduledJobsPolicy,
        AnalyticsRollupBootstrapState bootstrapState
    ) {
        return args -> {
            ClassPathResource script = new ClassPathResource(ROLLUP_SCHEMA_SCRIPT);
            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, script);
            }
            if (scheduledJobsPolicy.isEnabled()) {
                bootstrapState.prepare(hasUsableRollupData(jdbcTemplate));
            }
            log.info("Analytics rollup schema initialized: {}", ROLLUP_SCHEMA_SCRIPT);
        };
    }

    private static boolean hasUsableRollupData(JdbcTemplate jdbcTemplate) {
        Boolean result = jdbcTemplate.queryForObject(
            """
            select (
                not exists(select 1 from analytics.event limit 1)
                or (
                    exists(select 1 from analytics.event_rollup_bucket limit 1)
                    and exists(select 1 from analytics.stage_rollup_bucket limit 1)
                    and exists(select 1 from analytics.time_rollup_watermark limit 1)
                )
            )
            and (
                not exists(select 1 from analytics.stage_metric limit 1)
                or exists(select 1 from analytics.stage_metric_rollup_bucket limit 1)
            )
            and (
                not exists(select 1 from analytics.event limit 1)
                or exists(select 1 from analytics.filter_event_type_day limit 1)
            )
            and (
                not exists(select 1 from analytics.event_attribute limit 1)
                or exists(select 1 from analytics.filter_attr_value_day limit 1)
            )
            """,
            Boolean.class
        );
        return Boolean.TRUE.equals(result);
    }
}

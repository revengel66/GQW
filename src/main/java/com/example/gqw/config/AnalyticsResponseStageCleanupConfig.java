package com.example.gqw.config;

import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsResponseStageCleanupConfig {

    @Bean
    CommandLineRunner cleanupResponseStageData(@Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!tableExists(jdbcTemplate, "analytics.stage_type")) {
                return;
            }
            Integer responseStageTypeCount = jdbcTemplate.queryForObject(
                "select count(*) from analytics.stage_type where code = 'RESPONSE'",
                Integer.class
            );
            if (responseStageTypeCount == null || responseStageTypeCount == 0) {
                return;
            }

            if (tableExists(jdbcTemplate, "analytics.stage_metric") && tableExists(jdbcTemplate, "analytics.stage")) {
                jdbcTemplate.update(
                    """
                        delete from analytics.stage_metric sm
                        where sm.stage_id in (
                            select s.id from analytics.stage s where s.stage_type_code = 'RESPONSE'
                        )
                    """
                );
            }
            if (tableExists(jdbcTemplate, "analytics.stage")) {
                jdbcTemplate.update("delete from analytics.stage where stage_type_code = 'RESPONSE'");
            }
            if (tableExists(jdbcTemplate, "analytics.aggregated_metric")) {
                jdbcTemplate.update("delete from analytics.aggregated_metric where stage_type_code = 'RESPONSE'");
            }
            jdbcTemplate.update("delete from analytics.stage_type where code = 'RESPONSE'");
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

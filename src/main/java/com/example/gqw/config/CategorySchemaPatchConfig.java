package com.example.gqw.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class CategorySchemaPatchConfig {

    @Bean
    CommandLineRunner patchCategorySchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!tableExists(jdbcTemplate, "shop.category")) {
                return;
            }
            jdbcTemplate.execute("alter table shop.category add column if not exists is_published boolean");
            jdbcTemplate.execute("update shop.category set is_published = true where is_published is null");
            jdbcTemplate.execute("alter table shop.category alter column is_published set default true");
            jdbcTemplate.execute("alter table shop.category alter column is_published set not null");
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

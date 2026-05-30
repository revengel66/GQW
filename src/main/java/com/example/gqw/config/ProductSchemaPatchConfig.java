package com.example.gqw.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class ProductSchemaPatchConfig {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    CommandLineRunner patchProductSchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!tableExists(jdbcTemplate, "shop.product")) {
                return;
            }

            jdbcTemplate.execute("alter table shop.product add column if not exists article varchar(64)");
            jdbcTemplate.execute("alter table shop.product add column if not exists is_published boolean");
            jdbcTemplate.execute("alter table shop.product add column if not exists in_stock boolean");
            jdbcTemplate.execute("update shop.product set is_published = true where is_published is null");
            jdbcTemplate.execute("update shop.product set in_stock = true where in_stock is null");
            jdbcTemplate.execute("alter table shop.product alter column is_published set default true");
            jdbcTemplate.execute("alter table shop.product alter column is_published set not null");
            jdbcTemplate.execute("alter table shop.product alter column in_stock set default true");
            jdbcTemplate.execute("alter table shop.product alter column in_stock set not null");
            jdbcTemplate.execute("update shop.product set article = upper(trim(article)) where article is not null");
            jdbcTemplate.execute("update shop.product set article = null where article is not null and trim(article) = ''");
            jdbcTemplate.execute("""
                update shop.product
                set article = 'ART-' || lpad(id::text, 6, '0')
                where article is null
                """);
            jdbcTemplate.execute("""
                with ranked as (
                    select id, article, row_number() over (partition by article order by id) rn
                    from shop.product
                    where article is not null
                )
                update shop.product p
                set article = p.article || '-' || p.id
                from ranked r
                where p.id = r.id
                  and r.rn > 1
                """);
            jdbcTemplate.execute("""
                create unique index if not exists uk_product_article
                on shop.product(article)
                where article is not null
                """);
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

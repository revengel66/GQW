package com.example.gqw.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class WishlistSchemaPatchConfig {

    @Bean
    @Order(60)
    CommandLineRunner patchWishlistSchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            patchWishlist(jdbcTemplate);
            patchCart(jdbcTemplate);
        };
    }

    private static void patchWishlist(JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "shop.wishlist_item")) {
            return;
        }
        jdbcTemplate.execute("alter table shop.wishlist_item add column if not exists session_id varchar(128)");
        jdbcTemplate.execute("alter table shop.wishlist_item alter column user_id drop not null");
        jdbcTemplate.execute("create index if not exists idx_wishlist_item_session_id on shop.wishlist_item(session_id)");

        jdbcTemplate.execute("""
            with ranked as (
                select id, row_number() over(partition by user_id, product_id order by id) rn
                from shop.wishlist_item
                where user_id is not null
            )
            delete from shop.wishlist_item w
            using ranked
            where w.id = ranked.id
              and ranked.rn > 1
            """);
        jdbcTemplate.execute("""
            with ranked as (
                select id, row_number() over(partition by session_id, product_id order by id) rn
                from shop.wishlist_item
                where session_id is not null and session_id <> ''
            )
            delete from shop.wishlist_item w
            using ranked
            where w.id = ranked.id
              and ranked.rn > 1
            """);
        jdbcTemplate.execute("""
            create unique index if not exists uk_wishlist_item_user_product
            on shop.wishlist_item(user_id, product_id)
            where user_id is not null
            """);
        jdbcTemplate.execute("""
            create unique index if not exists uk_wishlist_item_session_product
            on shop.wishlist_item(session_id, product_id)
            where session_id is not null and session_id <> ''
            """);
    }

    private static void patchCart(JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "shop.cart_item")) {
            return;
        }
        jdbcTemplate.execute("alter table shop.cart_item add column if not exists session_id varchar(128)");
        jdbcTemplate.execute("alter table shop.cart_item alter column user_id drop not null");
        jdbcTemplate.execute("create index if not exists idx_cart_item_session_id on shop.cart_item(session_id)");

        jdbcTemplate.execute("""
            with ranked as (
                select id,
                       row_number() over(partition by user_id, product_id order by id) rn,
                       max(quantity) over(partition by user_id, product_id) max_qty
                from shop.cart_item
                where user_id is not null
            )
            update shop.cart_item c
            set quantity = ranked.max_qty
            from ranked
            where c.id = ranked.id
              and ranked.rn = 1
            """);
        jdbcTemplate.execute("""
            with ranked as (
                select id, row_number() over(partition by user_id, product_id order by id) rn
                from shop.cart_item
                where user_id is not null
            )
            delete from shop.cart_item c
            using ranked
            where c.id = ranked.id
              and ranked.rn > 1
            """);

        jdbcTemplate.execute("""
            with ranked as (
                select id,
                       row_number() over(partition by session_id, product_id order by id) rn,
                       max(quantity) over(partition by session_id, product_id) max_qty
                from shop.cart_item
                where session_id is not null and session_id <> ''
            )
            update shop.cart_item c
            set quantity = ranked.max_qty
            from ranked
            where c.id = ranked.id
              and ranked.rn = 1
            """);
        jdbcTemplate.execute("""
            with ranked as (
                select id, row_number() over(partition by session_id, product_id order by id) rn
                from shop.cart_item
                where session_id is not null and session_id <> ''
            )
            delete from shop.cart_item c
            using ranked
            where c.id = ranked.id
              and ranked.rn > 1
            """);

        jdbcTemplate.execute("""
            create unique index if not exists uk_cart_item_user_product
            on shop.cart_item(user_id, product_id)
            where user_id is not null
            """);
        jdbcTemplate.execute("""
            create unique index if not exists uk_cart_item_session_product
            on shop.cart_item(session_id, product_id)
            where session_id is not null and session_id <> ''
            """);
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

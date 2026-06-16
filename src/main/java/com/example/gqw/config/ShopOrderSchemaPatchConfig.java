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
public class ShopOrderSchemaPatchConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    CommandLineRunner patchShopOrderSchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            patchOrderStatusConstraint(jdbcTemplate);
            patchOrderStatusHistoryConstraint(jdbcTemplate);
        };
    }

    private static void patchOrderStatusConstraint(JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "shop.shop_order")) {
            return;
        }

        dropStatusCheckConstraints(jdbcTemplate, "shop.shop_order", "shop_order_status_check");

        jdbcTemplate.execute("""
            update shop.shop_order
            set status = case upper(trim(coalesce(status, '')))
                when 'NEW' then 'NEW'
                when 'CREATED' then 'NEW'
                when 'PENDING' then 'NEW'
                when 'ACCEPTED' then 'ACCEPTED'
                when 'PROCESSING' then 'ACCEPTED'
                when 'CONFIRMED' then 'ACCEPTED'
                when 'ASSEMBLED' then 'ASSEMBLED'
                when 'PACKED' then 'ASSEMBLED'
                when 'WAITING_PICKUP' then 'WAITING_PICKUP'
                when 'READY' then 'WAITING_PICKUP'
                when 'DELIVERED' then 'DELIVERED'
                when 'REJECTED' then 'REJECTED'
                when 'CANCELLED' then 'REJECTED'
                when 'CANCELED' then 'REJECTED'
                when 'DECLINED' then 'REJECTED'
                else trim(status)
            end
            """);

        jdbcTemplate.execute("""
            update shop.shop_order
            set status = 'DELIVERED'
            where upper(trim(coalesce(delivery_type, ''))) = 'DELIVERY'
              and upper(trim(coalesce(status, ''))) = 'WAITING_PICKUP'
            """);

        jdbcTemplate.execute("""
            update shop.shop_order
            set status = 'NEW'
            where status is null
               or upper(trim(status)) not in ('NEW', 'ACCEPTED', 'REJECTED', 'ASSEMBLED', 'WAITING_PICKUP', 'DELIVERED')
            """);

        jdbcTemplate.execute("""
            alter table shop.shop_order
            add constraint shop_order_status_check
            check (upper(trim(status)) in ('NEW', 'ACCEPTED', 'REJECTED', 'ASSEMBLED', 'WAITING_PICKUP', 'DELIVERED'))
            """);
    }

    private static void patchOrderStatusHistoryConstraint(JdbcTemplate jdbcTemplate) {
        if (!tableExists(jdbcTemplate, "shop.order_status_history")) {
            return;
        }

        dropStatusCheckConstraints(jdbcTemplate, "shop.order_status_history", "order_status_history_status_check");

        jdbcTemplate.execute("""
            update shop.order_status_history
            set status = case upper(trim(coalesce(status, '')))
                when 'NEW' then 'NEW'
                when 'CREATED' then 'NEW'
                when 'PENDING' then 'NEW'
                when 'ACCEPTED' then 'ACCEPTED'
                when 'PROCESSING' then 'ACCEPTED'
                when 'CONFIRMED' then 'ACCEPTED'
                when 'ASSEMBLED' then 'ASSEMBLED'
                when 'PACKED' then 'ASSEMBLED'
                when 'WAITING_PICKUP' then 'WAITING_PICKUP'
                when 'READY' then 'WAITING_PICKUP'
                when 'DELIVERED' then 'DELIVERED'
                when 'REJECTED' then 'REJECTED'
                when 'CANCELLED' then 'REJECTED'
                when 'CANCELED' then 'REJECTED'
                when 'DECLINED' then 'REJECTED'
                else trim(status)
            end
            """);

        jdbcTemplate.execute("""
            update shop.order_status_history
            set status = 'NEW'
            where status is null
               or upper(trim(status)) not in ('NEW', 'ACCEPTED', 'REJECTED', 'ASSEMBLED', 'WAITING_PICKUP', 'DELIVERED')
            """);

        jdbcTemplate.execute("""
            alter table shop.order_status_history
            add constraint order_status_history_status_check
            check (upper(trim(status)) in ('NEW', 'ACCEPTED', 'REJECTED', 'ASSEMBLED', 'WAITING_PICKUP', 'DELIVERED'))
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

    private static void dropStatusCheckConstraints(
        JdbcTemplate jdbcTemplate,
        String qualifiedTable,
        String canonicalConstraintName
    ) {
        jdbcTemplate.execute("""
            do $$
            declare constraint_row record;
            begin
              for constraint_row in
                select c.conname
                from pg_constraint c
                where c.conrelid = '%s'::regclass
                  and c.contype = 'c'
                  and (
                    c.conname = '%s'
                    or pg_get_constraintdef(c.oid) ilike '%%status%%'
                  )
              loop
                execute format('alter table %s drop constraint if exists %%I', constraint_row.conname);
              end loop;
            end $$;
            """.formatted(qualifiedTable, canonicalConstraintName, qualifiedTable));
    }
}

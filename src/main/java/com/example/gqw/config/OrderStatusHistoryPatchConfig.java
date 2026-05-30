package com.example.gqw.config;

import com.example.gqw.shop.service.OrderService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class OrderStatusHistoryPatchConfig {

    @Bean
    CommandLineRunner patchOrderStatusHistory(OrderService orderService) {
        return args -> orderService.ensureStatusHistoryForAllOrders();
    }
}

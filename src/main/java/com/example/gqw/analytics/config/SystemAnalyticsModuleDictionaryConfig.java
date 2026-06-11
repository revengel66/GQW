package com.example.gqw.analytics.config;

import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.repository.ModuleTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(
    value = "app.analytics.system-modules-seed-enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class SystemAnalyticsModuleDictionaryConfig {

    @Bean
    @Order(390)
    CommandLineRunner seedSystemAnalyticsModules(ModuleTypeRepository repository) {
        return args -> repository.upsert(
            EventType.DEFAULT_MODULE_CODE,
            "Общий",
            "Технический модуль по умолчанию для событий без пользовательской привязки к модулю.",
            true
        );
    }
}

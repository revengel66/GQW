package com.example.gqw.analytics.config;

import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import com.example.gqw.analytics.support.SystemMetricReadingGuides;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class SystemAnalyticsMetricReadingGuideConfig implements CommandLineRunner {

    private final StageMetricTypeRepository stageMetricTypeRepository;

    public SystemAnalyticsMetricReadingGuideConfig(StageMetricTypeRepository stageMetricTypeRepository) {
        this.stageMetricTypeRepository = stageMetricTypeRepository;
    }

    @Override
    public void run(String... args) {
        applyGuides();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onApplicationReady() {
        applyGuides();
    }

    private void applyGuides() {
        SystemMetricReadingGuides.guides()
            .forEach(stageMetricTypeRepository::updateReadingGuideByCode);
    }
}

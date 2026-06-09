package com.example.gqw.analytics.config;

import com.example.gqw.analytics.service.DictionarySyncService;
import com.example.gqw.analytics.service.AnalyticsEventTypeMaintenanceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "app.analytics.bootstrap-enabled", havingValue = "true", matchIfMissing = false)
public class AnalyticsBootstrap {

    private final DictionarySyncService dictionarySyncService;
    private final AnalyticsEventTypeMaintenanceService analyticsEventTypeMaintenanceService;

    public AnalyticsBootstrap(
        DictionarySyncService dictionarySyncService,
        AnalyticsEventTypeMaintenanceService analyticsEventTypeMaintenanceService
    ) {
        this.dictionarySyncService = dictionarySyncService;
        this.analyticsEventTypeMaintenanceService = analyticsEventTypeMaintenanceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        dictionarySyncService.syncAll();
        analyticsEventTypeMaintenanceService.maintainEventTypes();
    }
}


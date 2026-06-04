package com.example.gqw.analytics.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsTimeRollupLifecycle {

    private final AnalyticsTimeRollupService rollupService;

    public AnalyticsTimeRollupLifecycle(AnalyticsTimeRollupService rollupService) {
        this.rollupService = rollupService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpRollups() {
        rollupService.initializeIfNeeded();
    }
}

package com.example.gqw.analytics.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsTimeRollupLifecycle {

    private final AnalyticsTimeRollupService rollupService;
    private final AnalyticsScheduledJobsPolicy scheduledJobsPolicy;

    public AnalyticsTimeRollupLifecycle(
        AnalyticsTimeRollupService rollupService,
        AnalyticsScheduledJobsPolicy scheduledJobsPolicy
    ) {
        this.rollupService = rollupService;
        this.scheduledJobsPolicy = scheduledJobsPolicy;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpRollups() {
        if (!scheduledJobsPolicy.isEnabled()) {
            return;
        }
        rollupService.initializeIfNeeded();
    }
}

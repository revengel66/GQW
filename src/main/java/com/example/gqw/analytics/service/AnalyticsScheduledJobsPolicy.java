package com.example.gqw.analytics.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsScheduledJobsPolicy {

    private final boolean enabled;

    public AnalyticsScheduledJobsPolicy(
        @Value("${app.analytics.scheduled-jobs.enabled:true}") boolean enabled
    ) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

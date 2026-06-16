package com.example.gqw.analytics.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsScheduledJobsPolicy {

    private final boolean enabled;
    private final AnalyticsDataSourcePoolDiagnostics poolDiagnostics;

    public AnalyticsScheduledJobsPolicy(
        @Value("${app.analytics.scheduled-jobs.enabled:true}") boolean enabled,
        AnalyticsDataSourcePoolDiagnostics poolDiagnostics
    ) {
        this.enabled = enabled;
        this.poolDiagnostics = poolDiagnostics;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean canRun(String jobName) {
        if (!enabled) {
            return false;
        }
        if (poolDiagnostics.shouldSkipScheduledJob()) {
            poolDiagnostics.logScheduledSkip(jobName);
            return false;
        }
        return true;
    }
}

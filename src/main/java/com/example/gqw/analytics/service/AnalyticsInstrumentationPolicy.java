package com.example.gqw.analytics.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsInstrumentationPolicy {

    private final boolean enabled;

    public AnalyticsInstrumentationPolicy(
        @Value("${app.analytics.instrumentation.enabled:true}") boolean enabled
    ) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

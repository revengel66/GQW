package com.example.gqw.analytics.config;

import com.example.gqw.analytics.service.DictionarySyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "app.analytics.bootstrap-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsBootstrap {

    private final DictionarySyncService dictionarySyncService;

    public AnalyticsBootstrap(DictionarySyncService dictionarySyncService) {
        this.dictionarySyncService = dictionarySyncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        dictionarySyncService.syncAll();
    }
}


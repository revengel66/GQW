package com.example.gqw.analytics.config;

import com.example.gqw.analytics.service.AnalyticsCurrentUserProvider;
import com.example.gqw.analytics.service.NoopAnalyticsCurrentUserProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyticsCurrentUserProviderConfig {

    @Bean
    @ConditionalOnMissingBean(AnalyticsCurrentUserProvider.class)
    AnalyticsCurrentUserProvider analyticsCurrentUserProvider() {
        return new NoopAnalyticsCurrentUserProvider();
    }
}

package com.example.gqw.analytics.service;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(AnalyticsCurrentUserProvider.class)
public class NoopAnalyticsCurrentUserProvider implements AnalyticsCurrentUserProvider {

    @Override
    public Optional<String> currentUserId() {
        return Optional.empty();
    }

    @Override
    public Optional<String> currentUsername() {
        return Optional.empty();
    }

    @Override
    public Optional<String> currentUserRole() {
        return Optional.empty();
    }
}

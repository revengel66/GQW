package com.example.gqw.analytics.service;

import java.util.Optional;

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

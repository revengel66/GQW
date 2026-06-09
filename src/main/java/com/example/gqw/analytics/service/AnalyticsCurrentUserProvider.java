package com.example.gqw.analytics.service;

import java.util.Optional;

public interface AnalyticsCurrentUserProvider {

    Optional<String> currentUserId();

    Optional<String> currentUsername();

    Optional<String> currentUserRole();
}

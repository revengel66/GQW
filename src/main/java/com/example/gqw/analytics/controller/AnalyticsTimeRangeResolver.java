package com.example.gqw.analytics.controller;

import java.time.Duration;
import java.time.Instant;

final class AnalyticsTimeRangeResolver {

    private AnalyticsTimeRangeResolver() {
    }

    static TimeRange resolveRange(Instant from, Instant to, Duration fallbackDuration) {
        Instant now = Instant.now();
        Instant resolvedFrom = from;
        Instant resolvedTo = to;
        if (resolvedFrom == null && resolvedTo == null) {
            resolvedTo = now;
            resolvedFrom = resolvedTo.minus(fallbackDuration);
        } else if (resolvedFrom == null) {
            resolvedFrom = resolvedTo.minus(fallbackDuration);
        } else if (resolvedTo == null) {
            resolvedTo = now;
        }
        return new TimeRange(resolvedFrom, resolvedTo);
    }

    record TimeRange(Instant from, Instant to) {
    }
}

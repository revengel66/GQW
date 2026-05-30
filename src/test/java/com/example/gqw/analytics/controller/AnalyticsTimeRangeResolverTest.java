package com.example.gqw.analytics.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnalyticsTimeRangeResolverTest {

    @Test
    void resolveRangeUsesExplicitFromAndToWhenTheyAreValid() {
        Instant from = Instant.parse("2026-05-17T10:00:00Z");
        Instant to = Instant.parse("2026-05-17T11:00:00Z");

        AnalyticsTimeRangeResolver.TimeRange range = AnalyticsTimeRangeResolver.resolveRange(from, to, Duration.ofHours(1));

        assertEquals(from, range.from());
        assertEquals(to, range.to());
    }

    @Test
    void resolveRangeUsesFallbackWindowWhenFromIsAfterTo() {
        Instant from = Instant.parse("2026-05-17T12:00:00Z");
        Instant to = Instant.parse("2026-05-17T11:00:00Z");
        Duration fallback = Duration.ofMinutes(30);

        AnalyticsTimeRangeResolver.TimeRange range = AnalyticsTimeRangeResolver.resolveRange(from, to, fallback);

        assertEquals(to, range.to());
        assertEquals(to.minus(fallback), range.from());
    }

    @Test
    void resolveRangeUsesNowWhenToIsMissing() {
        Duration fallback = Duration.ofMinutes(15);

        AnalyticsTimeRangeResolver.TimeRange range = AnalyticsTimeRangeResolver.resolveRange(null, null, fallback);

        assertEquals(fallback, Duration.between(range.from(), range.to()));
        assertTrue(!range.to().isAfter(Instant.now().plusSeconds(1)));
    }
}


package com.example.gqw.analytics.service;

import java.time.Instant;

final class AnalyticsSeriesTime {

    private AnalyticsSeriesTime() {
    }

    static Instant displayTimeForBucket(Instant bucketStart, Instant to, long stepSeconds) {
        Instant bucketEnd = bucketStart.plusSeconds(stepSeconds);
        return bucketEnd.isBefore(to) ? bucketStart : to;
    }
}

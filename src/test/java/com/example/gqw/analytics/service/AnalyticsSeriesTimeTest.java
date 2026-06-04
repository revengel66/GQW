package com.example.gqw.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnalyticsSeriesTimeTest {

    private static final Instant FILTER_TO = Instant.parse("2026-06-02T11:08:00Z");

    @Test
    void usesFilterToForLastThreeHourBucket() {
        Instant bucketStart = Instant.parse("2026-06-02T09:00:00Z");

        Instant displayTime = AnalyticsSeriesTime.displayTimeForBucket(bucketStart, FILTER_TO, 3 * 60L * 60L);

        assertThat(displayTime).isEqualTo(FILTER_TO);
    }

    @Test
    void usesFilterToForLastSixHourBucket() {
        Instant bucketStart = Instant.parse("2026-06-02T06:00:00Z");

        Instant displayTime = AnalyticsSeriesTime.displayTimeForBucket(bucketStart, FILTER_TO, 6 * 60L * 60L);

        assertThat(displayTime).isEqualTo(FILTER_TO);
    }

    @Test
    void usesFilterToForLastDailyBucket() {
        Instant bucketStart = Instant.parse("2026-06-02T00:00:00Z");

        Instant displayTime = AnalyticsSeriesTime.displayTimeForBucket(bucketStart, FILTER_TO, 24 * 60L * 60L);

        assertThat(displayTime).isEqualTo(FILTER_TO);
    }

    @Test
    void keepsStartTimeForClosedIntermediateBucket() {
        Instant bucketStart = Instant.parse("2026-06-01T00:00:00Z");

        Instant displayTime = AnalyticsSeriesTime.displayTimeForBucket(bucketStart, FILTER_TO, 24 * 60L * 60L);

        assertThat(displayTime).isEqualTo(bucketStart);
    }
}

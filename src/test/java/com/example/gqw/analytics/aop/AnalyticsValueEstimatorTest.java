package com.example.gqw.analytics.aop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class AnalyticsValueEstimatorTest {

    @Test
    void estimatePayloadBytesForStringUsesUtf16Approximation() {
        BigDecimal actual = AnalyticsValueEstimator.estimatePayloadBytes("abcd");

        assertEquals(BigDecimal.valueOf(8L), actual);
    }

    @Test
    void estimatePayloadBytesForCollectionDependsOnSize() {
        BigDecimal actual = AnalyticsValueEstimator.estimatePayloadBytes(List.of(1, 2, 3));

        assertEquals(BigDecimal.valueOf(64L + 3L * 96L), actual);
    }

    @Test
    void estimatePayloadBytesUnwrapsResponseEntityAndOptional() {
        Object wrapped = ResponseEntity.ok(Optional.of(Map.of("k1", 1, "k2", 2)));

        BigDecimal actual = AnalyticsValueEstimator.estimatePayloadBytes(wrapped);

        assertEquals(BigDecimal.valueOf(64L + 2L * 128L), actual);
    }

    @Test
    void estimateItemCountForArrayAndMap() {
        assertEquals(BigDecimal.valueOf(4L), AnalyticsValueEstimator.estimateItemCount(new int[] {1, 2, 3, 4}));
        assertEquals(BigDecimal.valueOf(2L), AnalyticsValueEstimator.estimateItemCount(Map.of("a", 1, "b", 2)));
    }

    @Test
    void estimateItemCountReturnsZeroForSingleObject() {
        BigDecimal actual = AnalyticsValueEstimator.estimateItemCount("single");

        assertEquals(BigDecimal.ZERO, actual);
    }

    @Test
    void estimatePayloadBytesReturnsAtLeast64ForObjectToString() {
        BigDecimal actual = AnalyticsValueEstimator.estimatePayloadBytes(new Object());

        assertTrue(actual.compareTo(BigDecimal.valueOf(64L)) >= 0);
    }
}


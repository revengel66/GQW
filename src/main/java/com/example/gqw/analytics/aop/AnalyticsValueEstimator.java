package com.example.gqw.analytics.aop;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Slice;

final class AnalyticsValueEstimator {

    private AnalyticsValueEstimator() {
    }

    static BigDecimal estimatePayloadBytes(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        Object unwrapped = unwrapResponse(value);
        if (unwrapped == null) {
            return BigDecimal.ZERO;
        }
        if (unwrapped instanceof String stringValue) {
            return BigDecimal.valueOf(Math.max(0L, (long) stringValue.length() * 2L));
        }
        if (unwrapped instanceof Number || unwrapped instanceof Boolean || unwrapped instanceof Character) {
            return BigDecimal.valueOf(16L);
        }
        if (unwrapped instanceof Collection<?> collection) {
            return BigDecimal.valueOf(64L + (long) collection.size() * 96L);
        }
        if (unwrapped instanceof Map<?, ?> map) {
            return BigDecimal.valueOf(64L + (long) map.size() * 128L);
        }
        if (unwrapped.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(unwrapped);
            return BigDecimal.valueOf(64L + (long) length * 96L);
        }
        return BigDecimal.valueOf(Math.max(64L, (long) unwrapped.toString().length() * 2L + 32L));
    }

    static BigDecimal estimateItemCount(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        Object unwrapped = unwrapResponse(value);
        if (unwrapped == null) {
            return BigDecimal.ZERO;
        }
        if (unwrapped instanceof Collection<?> collection) {
            return BigDecimal.valueOf(collection.size());
        }
        if (unwrapped instanceof Map<?, ?> map) {
            return BigDecimal.valueOf(map.size());
        }
        if (unwrapped.getClass().isArray()) {
            return BigDecimal.valueOf(java.lang.reflect.Array.getLength(unwrapped));
        }
        if (unwrapped instanceof Slice<?> slice) {
            return BigDecimal.valueOf(slice.getNumberOfElements());
        }
        if (unwrapped instanceof Iterable<?> iterable) {
            long count = 0L;
            for (Object ignored : iterable) {
                count++;
            }
            return BigDecimal.valueOf(count);
        }
        return BigDecimal.ZERO;
    }

    private static Object unwrapResponse(Object value) {
        Object current = value;
        if (current instanceof ResponseEntity<?> responseEntity) {
            current = responseEntity.getBody();
        }
        if (current instanceof Optional<?> optional) {
            current = optional.orElse(null);
        }
        return current;
    }
}

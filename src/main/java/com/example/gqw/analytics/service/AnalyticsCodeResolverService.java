package com.example.gqw.analytics.service;

import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsCodeResolverService {

    @Transactional(readOnly = true)
    public String resolveEventTypeCode(String code) {
        return normalizeCode(code);
    }

    @Transactional(readOnly = true)
    public String resolveAttributeTypeCode(String code) {
        return normalizeCode(code);
    }

    @Transactional(readOnly = true)
    public String resolveMetricTypeCode(String code) {
        return normalizeCode(code);
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }
}


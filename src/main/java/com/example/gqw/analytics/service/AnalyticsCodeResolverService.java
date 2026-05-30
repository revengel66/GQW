package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsCodeAlias;
import com.example.gqw.analytics.entity.AnalyticsCodeAliasType;
import com.example.gqw.analytics.repository.AnalyticsCodeAliasRepository;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsCodeResolverService {

    private final AnalyticsCodeAliasRepository aliasRepository;

    public AnalyticsCodeResolverService(AnalyticsCodeAliasRepository aliasRepository) {
        this.aliasRepository = aliasRepository;
    }

    @Transactional(readOnly = true)
    public String resolveEventTypeCode(String code) {
        return resolveAlias(AnalyticsCodeAliasType.EVENT, code);
    }

    @Transactional(readOnly = true)
    public String resolveAttributeTypeCode(String code) {
        return resolveAlias(AnalyticsCodeAliasType.ATTRIBUTE, code);
    }

    @Transactional(readOnly = true)
    public String resolveMetricTypeCode(String code) {
        return resolveAlias(AnalyticsCodeAliasType.METRIC, code);
    }

    private String resolveAlias(AnalyticsCodeAliasType aliasType, String rawCode) {
        String current = normalizeCode(rawCode);
        if (current == null) {
            return null;
        }
        Set<String> visited = new HashSet<>();
        for (int depth = 0; depth < 16; depth++) {
            if (!visited.add(current)) {
                break;
            }
            AnalyticsCodeAlias alias = aliasRepository
                .findByAliasTypeAndSourceCodeAndIsActiveTrue(aliasType, current)
                .orElse(null);
            if (alias == null) {
                return current;
            }
            String target = normalizeCode(alias.getTargetCode());
            if (target == null) {
                return current;
            }
            current = target;
        }
        return current;
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


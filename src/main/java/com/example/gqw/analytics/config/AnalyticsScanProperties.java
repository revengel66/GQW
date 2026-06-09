package com.example.gqw.analytics.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.analytics")
public class AnalyticsScanProperties {

    private List<String> scanPackages = new ArrayList<>(List.of("com.example.gqw"));

    public List<String> getScanPackages() {
        return scanPackages;
    }

    public void setScanPackages(List<String> scanPackages) {
        this.scanPackages = scanPackages == null ? new ArrayList<>() : new ArrayList<>(scanPackages);
    }

    public boolean matches(Class<?> type) {
        if (type == null) {
            return false;
        }
        Package typePackage = type.getPackage();
        return matches(typePackage == null ? "" : typePackage.getName());
    }

    public boolean matches(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return false;
        }
        for (String scanPackage : scanPackages) {
            String normalized = normalize(scanPackage);
            if (normalized == null) {
                continue;
            }
            if (packageName.equals(normalized) || packageName.startsWith(normalized + ".")) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}

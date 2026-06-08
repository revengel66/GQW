package com.example.gqw.analytics.service;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsLoggingPolicy {

    public enum BuiltInLogLevel {
        INFO,
        WARN,
        ERROR,
        OFF
    }

    private final AnalyticsRuntimeSettingsService runtimeSettingsService;
    private final AnalyticsInstrumentationPolicy instrumentationPolicy;

    public AnalyticsLoggingPolicy(AnalyticsRuntimeSettingsService runtimeSettingsService) {
        this(runtimeSettingsService, new AnalyticsInstrumentationPolicy(true));
    }

    @Autowired
    public AnalyticsLoggingPolicy(
        AnalyticsRuntimeSettingsService runtimeSettingsService,
        AnalyticsInstrumentationPolicy instrumentationPolicy
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.instrumentationPolicy = instrumentationPolicy;
    }

    public boolean isBuiltInEnabled() {
        return instrumentationPolicy.isEnabled()
            && runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_ENABLED, true)
            && level() != BuiltInLogLevel.OFF;
    }

    public boolean isInfoEnabled() {
        return isLevelEnabled(BuiltInLogLevel.INFO);
    }

    public boolean isWarnEnabled() {
        return isLevelEnabled(BuiltInLogLevel.WARN);
    }

    public boolean isErrorEnabled() {
        return isLevelEnabled(BuiltInLogLevel.ERROR);
    }

    public boolean isLevelEnabled(BuiltInLogLevel messageLevel) {
        if (!instrumentationPolicy.isEnabled()) {
            return false;
        }
        if (!runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_ENABLED, true)) {
            return false;
        }
        BuiltInLogLevel configured = level();
        if (configured == BuiltInLogLevel.OFF) {
            return false;
        }
        return isAllowedByConfiguredLevel(messageLevel, configured);
    }

    public boolean isControllerEnabled() {
        return isBuiltInEnabled()
            && runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_CONTROLLER_ENABLED, true);
    }

    public boolean isServiceEnabled() {
        return isBuiltInEnabled()
            && runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_SERVICE_ENABLED, true);
    }

    public boolean isDatabaseEnabled() {
        return isBuiltInEnabled()
            && runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_DATABASE_ENABLED, true);
    }

    public boolean isCustomLayerEnabled() {
        return isBuiltInEnabled()
            && runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_CUSTOM_LAYER_ENABLED, true);
    }

    public boolean isUserLogCaptureEnabled() {
        return instrumentationPolicy.isEnabled()
            && runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_USER_LOG_CAPTURE_ENABLED, true);
    }

    public boolean isStrictWarningsEnabled() {
        return instrumentationPolicy.isEnabled()
            && runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_STRICT_WARNINGS_ENABLED, true)
            && isAllowedByConfiguredLevel(BuiltInLogLevel.WARN, level());
    }

    public boolean isLayerEnabled(String layer) {
        return switch (normalize(layer)) {
            case "CONTROLLER" -> isControllerEnabled();
            case "SERVICE" -> isServiceEnabled();
            case "DATABASE", "REPOSITORY" -> isDatabaseEnabled();
            default -> isCustomLayerEnabled();
        };
    }

    private BuiltInLogLevel level() {
        String raw = runtimeSettingsService.getText(AnalyticsRuntimeSettingsService.KEY_ANALYTICS_LOGGING_LEVEL, "INFO");
        try {
            return BuiltInLogLevel.valueOf(normalize(raw));
        } catch (RuntimeException ignored) {
            return BuiltInLogLevel.INFO;
        }
    }

    private static int severity(BuiltInLogLevel level) {
        return switch (level) {
            case INFO -> 1;
            case WARN -> 2;
            case ERROR -> 3;
            case OFF -> 4;
        };
    }

    private static boolean isAllowedByConfiguredLevel(BuiltInLogLevel messageLevel, BuiltInLogLevel configured) {
        return configured != BuiltInLogLevel.OFF && severity(messageLevel) >= severity(configured);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}

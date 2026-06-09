package com.example.gqw.analytics.controller;

import com.example.gqw.analytics.service.AnalyticsAdminAuthService;
import com.example.gqw.analytics.service.AnalyticsRuntimeDiagnosticsService;
import com.example.gqw.analytics.service.AnalyticsRuntimeOperationsService;
import com.example.gqw.analytics.service.AnalyticsRuntimeSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics-admin/api/runtime-settings")
public class AnalyticsRuntimeSettingsController {

    private final AnalyticsRuntimeSettingsService runtimeSettingsService;
    private final AnalyticsRuntimeDiagnosticsService runtimeDiagnosticsService;
    private final AnalyticsRuntimeOperationsService runtimeOperationsService;

    public AnalyticsRuntimeSettingsController(
        AnalyticsRuntimeSettingsService runtimeSettingsService,
        AnalyticsRuntimeDiagnosticsService runtimeDiagnosticsService,
        AnalyticsRuntimeOperationsService runtimeOperationsService
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.runtimeDiagnosticsService = runtimeDiagnosticsService;
        this.runtimeOperationsService = runtimeOperationsService;
    }

    @GetMapping
    public AnalyticsRuntimeSettingsService.SettingsView view() {
        return runtimeSettingsService.view();
    }

    @GetMapping("/diagnostics")
    public AnalyticsRuntimeDiagnosticsService.DiagnosticsView diagnostics() {
        return runtimeDiagnosticsService.view();
    }

    @PostMapping(path = "/operations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnalyticsRuntimeOperationsService.OperationResult runOperation(
        @RequestBody RuntimeOperationRequest request,
        HttpServletRequest servletRequest
    ) {
        String requestedBy = "analytics-admin";
        Object sessionUsername = servletRequest.getSession(true)
            .getAttribute(AnalyticsAdminAuthService.SESSION_KEY_USERNAME);
        if (sessionUsername != null) {
            requestedBy = sessionUsername.toString();
        }
        String action = request == null ? "" : request.action();
        return runtimeOperationsService.runOperation(action, requestedBy);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnalyticsRuntimeSettingsService.SettingsView update(
        @RequestBody UpdateRuntimeSettingsRequest request,
        HttpServletRequest servletRequest
    ) {
        String updatedBy = "analytics-admin";
        Object sessionUsername = servletRequest.getSession(true)
            .getAttribute(AnalyticsAdminAuthService.SESSION_KEY_USERNAME);
        if (sessionUsername != null) {
            updatedBy = sessionUsername.toString();
        }
        Map<String, String> values = request == null ? Map.of() : request.values();
        return runtimeSettingsService.update(values, updatedBy);
    }

    public record UpdateRuntimeSettingsRequest(
        Map<String, String> values
    ) {
    }

    public record RuntimeOperationRequest(
        String action
    ) {
    }
}

package com.example.gqw.analytics.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsRuntimeOperationsService {

    private final AnalyticsTimeRollupService timeRollupService;
    private final AnalyticsStageMetricRollupService stageMetricRollupService;
    private final AnalyticsFilterRollupService filterRollupService;
    private final AnalyticsDataLifecycleService lifecycleService;
    private final AnalyticsRuntimeDiagnosticsService diagnosticsService;
    private final AnalyticsLogArchiveIndexService logArchiveIndexService;
    private final Clock clock;

    public AnalyticsRuntimeOperationsService(
        AnalyticsTimeRollupService timeRollupService,
        AnalyticsStageMetricRollupService stageMetricRollupService,
        AnalyticsFilterRollupService filterRollupService,
        AnalyticsDataLifecycleService lifecycleService,
        AnalyticsRuntimeDiagnosticsService diagnosticsService,
        AnalyticsLogArchiveIndexService logArchiveIndexService
    ) {
        this.timeRollupService = timeRollupService;
        this.stageMetricRollupService = stageMetricRollupService;
        this.filterRollupService = filterRollupService;
        this.lifecycleService = lifecycleService;
        this.diagnosticsService = diagnosticsService;
        this.logArchiveIndexService = logArchiveIndexService;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public OperationResult runOperation(String rawAction, String requestedBy) {
        String action = normalizeAction(rawAction);
        Instant startedAt = Instant.now(clock);
        List<String> steps = new ArrayList<>();

        switch (action) {
            case "refresh_now" -> {
                timeRollupService.initializeIfNeeded();
                steps.add("time-rollup refresh выполнен");
                stageMetricRollupService.initializeIfNeeded();
                steps.add("stage-metric rollup refresh выполнен");
                filterRollupService.refreshRecentNow();
                steps.add("filter-rollup recent refresh выполнен");
            }
            case "backfill_now" -> {
                timeRollupService.initializeIfNeeded();
                steps.add("time/stage rollup backfill выполнен");
                stageMetricRollupService.initializeIfNeeded();
                steps.add("stage-metric rollup backfill выполнен");
                filterRollupService.rebuildAllNow();
                steps.add("filter-rollup full rebuild выполнен");
            }
            case "lifecycle_now" -> {
                lifecycleService.runMaintenanceNow();
                steps.add("lifecycle cleanup выполнен");
            }
            case "index_logs_now" -> {
                AnalyticsLogArchiveIndexService.LogIndexRunResult result = logArchiveIndexService.indexAvailableFilesNow();
                steps.add("log index: discovered=" + result.discoveredFiles()
                    + ", indexed=" + result.indexedFiles()
                    + ", skipped=" + result.skippedFiles()
                    + ", errors=" + result.errorFiles());
                steps.addAll(result.notes());
            }
            case "cleanup_logs_now" -> {
                AnalyticsLogArchiveIndexService.LogRetentionCleanupResult result = logArchiveIndexService.cleanupOldLogsNow();
                steps.add("log retention: candidates=" + result.candidates()
                    + ", deletedFiles=" + result.deletedFiles()
                    + ", skippedActive=" + result.skippedActive()
                    + ", skippedNotIndexed=" + result.skippedNotIndexed()
                    + ", deletedIndexRows=" + result.deletedIndexRows()
                    + ", safeMode=" + result.safeMode());
                steps.addAll(result.notes());
            }
            default -> throw new IllegalArgumentException("Unsupported action: " + rawAction);
        }

        Instant finishedAt = Instant.now(clock);
        long tookMs = Math.max(0L, Duration.between(startedAt, finishedAt).toMillis());
        AnalyticsRuntimeDiagnosticsService.DiagnosticsView diagnostics = diagnosticsService.view();

        return new OperationResult(
            action,
            requestedBy == null || requestedBy.isBlank() ? "analytics-admin" : requestedBy,
            startedAt,
            finishedAt,
            tookMs,
            steps,
            diagnostics
        );
    }

    private String normalizeAction(String rawAction) {
        String normalized = rawAction == null ? "" : rawAction.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized;
    }

    public record OperationResult(
        String action,
        String requestedBy,
        Instant startedAt,
        Instant finishedAt,
        long tookMs,
        List<String> steps,
        AnalyticsRuntimeDiagnosticsService.DiagnosticsView diagnostics
    ) {
    }
}



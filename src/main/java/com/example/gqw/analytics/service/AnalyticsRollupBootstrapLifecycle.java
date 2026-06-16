package com.example.gqw.analytics.service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AnalyticsRollupBootstrapLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRollupBootstrapLifecycle.class);

    private final AnalyticsTimeRollupService timeRollupService;
    private final AnalyticsStageMetricRollupService stageMetricRollupService;
    private final AnalyticsFilterRollupService filterRollupService;
    private final AnalyticsScheduledJobsPolicy scheduledJobsPolicy;
    private final AnalyticsRollupBootstrapState bootstrapState;
    private final ExecutorService bootstrapExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "analytics-rollup-bootstrap");
        thread.setDaemon(true);
        return thread;
    });

    public AnalyticsRollupBootstrapLifecycle(
        AnalyticsTimeRollupService timeRollupService,
        AnalyticsStageMetricRollupService stageMetricRollupService,
        AnalyticsFilterRollupService filterRollupService,
        AnalyticsScheduledJobsPolicy scheduledJobsPolicy,
        AnalyticsRollupBootstrapState bootstrapState
    ) {
        this.timeRollupService = timeRollupService;
        this.stageMetricRollupService = stageMetricRollupService;
        this.filterRollupService = filterRollupService;
        this.scheduledJobsPolicy = scheduledJobsPolicy;
        this.bootstrapState = bootstrapState;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpRollups() {
        if (!scheduledJobsPolicy.isEnabled()) {
            return;
        }
        if (!bootstrapState.isInitializing()) {
            log.info("Analytics rollup background bootstrap skipped: usable rollup data already exists");
            return;
        }
        try {
            bootstrapExecutor.submit(() -> {
                long startedAt = System.nanoTime();
                Throwable failure = null;
                log.info("Analytics rollup background bootstrap started");
                try {
                    timeRollupService.initializeIfNeeded();
                    stageMetricRollupService.initializeIfNeeded();
                    filterRollupService.initializeIfNeeded();
                    log.info(
                        "Analytics rollup background bootstrap completed in {} ms",
                        (System.nanoTime() - startedAt) / 1_000_000L
                    );
                } catch (Throwable throwable) {
                    failure = throwable;
                    bootstrapState.fail(throwable);
                    log.error("Analytics rollup background bootstrap failed", throwable);
                } finally {
                    if (failure == null) {
                        bootstrapState.complete();
                    } else if (bootstrapState.isInitializing()) {
                        bootstrapState.fail(failure);
                    }
                }
            });
        } catch (RuntimeException exception) {
            bootstrapState.fail(exception);
            log.error("Analytics rollup background bootstrap could not be scheduled", exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        bootstrapExecutor.shutdownNow();
    }
}

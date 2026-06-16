package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AggregationGranularity;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AggregationScheduler {

    private final AggregationService aggregationService;
    private final AnalyticsScheduledJobsPolicy scheduledJobsPolicy;

    public AggregationScheduler(
        AggregationService aggregationService,
        AnalyticsScheduledJobsPolicy scheduledJobsPolicy
    ) {
        this.aggregationService = aggregationService;
        this.scheduledJobsPolicy = scheduledJobsPolicy;
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void runHourly() {
        if (!scheduledJobsPolicy.canRun("aggregation-hourly")) {
            return;
        }
        Instant end = Instant.now();
        Instant start = end.minusSeconds(3600);
        aggregationService.runAggregation(AggregationGranularity.HOUR, start, end);
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runDaily() {
        if (!scheduledJobsPolicy.canRun("aggregation-daily")) {
            return;
        }
        Instant end = Instant.now();
        Instant start = end.minusSeconds(86400);
        aggregationService.runAggregation(AggregationGranularity.DAY, start, end);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runMonthly() {
        if (!scheduledJobsPolicy.canRun("aggregation-monthly")) {
            return;
        }
        Instant end = Instant.now();
        Instant start = end.minusSeconds(2592000);
        aggregationService.runAggregation(AggregationGranularity.MONTH, start, end);
    }
}


package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AggregationGranularity;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AggregationScheduler {

    private final AggregationService aggregationService;

    public AggregationScheduler(AggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void runHourly() {
        Instant end = Instant.now();
        Instant start = end.minusSeconds(3600);
        aggregationService.runAggregation(AggregationGranularity.HOUR, start, end);
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runDaily() {
        Instant end = Instant.now();
        Instant start = end.minusSeconds(86400);
        aggregationService.runAggregation(AggregationGranularity.DAY, start, end);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runMonthly() {
        Instant end = Instant.now();
        Instant start = end.minusSeconds(2592000);
        aggregationService.runAggregation(AggregationGranularity.MONTH, start, end);
    }
}


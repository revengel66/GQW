package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AggregatedMetric;
import com.example.gqw.analytics.entity.AggregationGranularity;
import com.example.gqw.analytics.entity.AggregationRun;
import com.example.gqw.analytics.entity.AggregationRunType;
import com.example.gqw.analytics.entity.AggregationStatus;
import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.repository.AggregatedMetricRepository;
import com.example.gqw.analytics.repository.AggregationRunRepository;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AggregationService {

    private final AggregationRunRepository aggregationRunRepository;
    private final AggregatedMetricRepository aggregatedMetricRepository;
    private final AnalyticsEventRepository eventRepository;

    public AggregationService(
        AggregationRunRepository aggregationRunRepository,
        AggregatedMetricRepository aggregatedMetricRepository,
        AnalyticsEventRepository eventRepository
    ) {
        this.aggregationRunRepository = aggregationRunRepository;
        this.aggregatedMetricRepository = aggregatedMetricRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void runAggregation(AggregationGranularity granularity, Instant periodStart, Instant periodEnd) {
        AggregationRun run = new AggregationRun();
        run.setRunType(AggregationRunType.SCHEDULED);
        run.setGranularity(granularity);
        run.setPeriodStart(periodStart);
        run.setPeriodEnd(periodEnd);
        run.setStatus(AggregationStatus.SUCCESS);
        run.setStartedAt(Instant.now());
        run = aggregationRunRepository.save(run);

        try {
            List<AnalyticsEvent> events = eventRepository.findAllForAggregation(periodStart, periodEnd, null);
            Map<String, List<AnalyticsEvent>> byType = events.stream()
                .collect(Collectors.groupingBy(AnalyticsEvent::getEventTypeCode));

            aggregatedMetricRepository.deleteByPeriod(periodStart, periodEnd);

            List<AggregatedMetric> metrics = new ArrayList<>();
            for (Map.Entry<String, List<AnalyticsEvent>> entry : byType.entrySet()) {
                List<AnalyticsEvent> values = entry.getValue();
                if (values.isEmpty()) {
                    continue;
                }
                List<Integer> durations = values.stream()
                    .map(AnalyticsEvent::getDurationMs)
                    .filter(v -> v != null && v >= 0)
                    .sorted(Comparator.naturalOrder())
                    .toList();

                if (durations.isEmpty()) {
                    continue;
                }

                AggregatedMetric metric = new AggregatedMetric();
                metric.setAggregationRunId(run.getId());
                metric.setEventTypeCode(entry.getKey());
                metric.setStageTypeCode(null);
                metric.setPeriodStart(periodStart);
                metric.setPeriodEnd(periodEnd);
                metric.setSampleCount((long) durations.size());
                metric.setAvgMs(avg(durations));
                metric.setP50Ms(percentile(durations, 0.50));
                metric.setP95Ms(percentile(durations, 0.95));
                metric.setP99Ms(percentile(durations, 0.99));
                metric.setMaxMs(BigDecimal.valueOf(durations.get(durations.size() - 1)).setScale(3, RoundingMode.HALF_UP));
                long errorCount = values.stream().filter(v -> Boolean.TRUE.equals(v.getIsError())).count();
                metric.setErrorRate(BigDecimal.valueOf(errorCount)
                    .divide(BigDecimal.valueOf(values.size()), 3, RoundingMode.HALF_UP));
                metrics.add(metric);
            }

            aggregatedMetricRepository.saveAll(metrics);
            run.setRowsWritten(metrics.size());
            run.setStatus(AggregationStatus.SUCCESS);
            run.setFinishedAt(Instant.now());
            aggregationRunRepository.save(run);
        } catch (Exception ex) {
            run.setStatus(AggregationStatus.FAILED);
            run.setErrorMessage(ex.getMessage());
            run.setFinishedAt(Instant.now());
            aggregationRunRepository.save(run);
            throw ex;
        }
    }

    public Instant currentPeriodStart(AggregationGranularity granularity) {
        Instant now = Instant.now();
        return switch (granularity) {
            case HOUR -> now.truncatedTo(ChronoUnit.HOURS);
            case DAY -> now.truncatedTo(ChronoUnit.DAYS);
            case MONTH -> now.minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        };
    }

    private BigDecimal avg(List<Integer> values) {
        double avg = values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        return BigDecimal.valueOf(avg).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal percentile(List<Integer> values, double p) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int index = (int) Math.ceil(p * values.size()) - 1;
        index = Math.max(0, Math.min(index, values.size() - 1));
        return BigDecimal.valueOf(values.get(index)).setScale(3, RoundingMode.HALF_UP);
    }
}

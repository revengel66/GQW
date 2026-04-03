package com.example.gqw.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "aggregated_metric",
    schema = "analytics",
    indexes = {
        @Index(name = "idx_analytics_aggregated_metric_run", columnList = "aggregation_run_id"),
        @Index(name = "idx_analytics_aggregated_metric_type", columnList = "event_type_code,stage_type_code"),
        @Index(name = "idx_analytics_aggregated_metric_period", columnList = "period_start,period_end")
    }
)
public class AggregatedMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long aggregationRunId;

    @Column(nullable = false, length = 64)
    private String eventTypeCode;

    @Column(length = 64)
    private String stageTypeCode;

    @Column(nullable = false)
    private Instant periodStart;

    @Column(nullable = false)
    private Instant periodEnd;

    @Column(nullable = false)
    private Long sampleCount;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal avgMs;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal p50Ms;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal p95Ms;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal p99Ms;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal maxMs;

    @Column(precision = 6, scale = 3)
    private BigDecimal errorRate;
}


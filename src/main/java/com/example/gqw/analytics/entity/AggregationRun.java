package com.example.gqw.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "aggregation_run",
    schema = "analytics",
    indexes = {
        @Index(name = "idx_analytics_agg_run_period", columnList = "period_start,period_end"),
        @Index(name = "idx_analytics_agg_run_status", columnList = "status")
    }
)
public class AggregationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AggregationRunType runType = AggregationRunType.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AggregationGranularity granularity = AggregationGranularity.HOUR;

    @Column(nullable = false)
    private Instant periodStart;

    @Column(nullable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AggregationStatus status = AggregationStatus.SUCCESS;

    @Column(nullable = false)
    private Instant startedAt;

    @Column
    private Instant finishedAt;

    @Column(nullable = false)
    private Integer rowsWritten = 0;

    @Column(length = 2048)
    private String errorMessage;

    @PrePersist
    public void prePersist() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}


package com.example.gqw.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
    name = "stage_metric",
    schema = "analytics",
    indexes = {
        @Index(name = "idx_analytics_stage_metric_stage", columnList = "stage_id"),
        @Index(name = "idx_analytics_stage_metric_type", columnList = "metric_type_code")
    },
    uniqueConstraints = @UniqueConstraint(name = "uq_analytics_stage_metric_stage_type", columnNames = {"stage_id", "metric_type_code"})
)
public class AnalyticsStageMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long stageId;

    @Column(nullable = false, length = 64)
    private String metricTypeCode;

    @Column(precision = 14, scale = 3)
    private BigDecimal metricValueNum;

    @Column(length = 1024)
    private String metricValueText;

    @Column(length = 32)
    private String unit;

    @Column(nullable = false)
    private Instant recordedAt;

    @PrePersist
    public void prePersist() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }
}


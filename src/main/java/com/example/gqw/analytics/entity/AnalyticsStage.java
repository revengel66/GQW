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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "stage",
    schema = "analytics",
    indexes = {
        @Index(name = "idx_analytics_stage_event", columnList = "event_id"),
        @Index(name = "idx_analytics_stage_type", columnList = "stage_type_code")
    },
    uniqueConstraints = @UniqueConstraint(name = "uq_analytics_stage_event_order", columnNames = {"event_id", "stage_order"})
)
public class AnalyticsStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 64)
    private String stageTypeCode;

    @Column(nullable = false)
    private Integer stageOrder;

    @Column(nullable = false)
    private Instant startedAt;

    @Column
    private Instant endedAt;

    @Column
    private Integer durationMs;

    @Column
    private Instant logStartedAt;

    @Column
    private Instant logEndedAt;

    @Column(nullable = false)
    private Boolean isError = false;

    @Column(length = 2048)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}


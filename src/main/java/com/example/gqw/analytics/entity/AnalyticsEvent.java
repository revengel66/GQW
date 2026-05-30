package com.example.gqw.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "event",
    schema = "analytics",
    indexes = {
        @Index(name = "idx_analytics_event_uid", columnList = "event_uid"),
        @Index(name = "idx_analytics_event_type", columnList = "event_type_code"),
        @Index(name = "idx_analytics_event_module", columnList = "module_code"),
        @Index(name = "idx_analytics_event_started_at", columnList = "started_at")
    }
)
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID eventUid;

    @Column
    private Long userId;

    @Column(length = 128)
    private String sessionId;

    @Column(nullable = false, length = 64)
    private String eventTypeCode;

    @Column(nullable = false, length = 64)
    private String moduleCode = EventType.DEFAULT_MODULE_CODE;

    @Column(length = 1024)
    private String requestPath;

    @Column(length = 16)
    private String httpMethod;

    @Column(length = 64)
    private String traceId;

    @Column
    private Integer statusCode;

    @Column(nullable = false)
    private Boolean isError = false;

    @Column(length = 2048)
    private String errorMessage;

    @Column(nullable = false)
    private Instant startedAt;

    @Column
    private Instant endedAt;

    @Column
    private Integer durationMs;

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
        if (eventUid == null) {
            eventUid = UUID.randomUUID();
        }
    }
}


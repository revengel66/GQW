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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "event_attribute",
    schema = "analytics",
    indexes = {
        @Index(name = "idx_analytics_event_attribute_event", columnList = "event_id"),
        @Index(name = "idx_analytics_event_attribute_type", columnList = "attribute_type_code")
    }
)
public class AnalyticsEventAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 64)
    private String attributeTypeCode;

    @Column(length = 2048)
    private String attrValue;

    @Column(length = 4000)
    private String attrValueJson;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}


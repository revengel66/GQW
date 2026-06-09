package com.example.gqw.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "event_type", schema = "analytics")
public class EventType {

    public static final String DEFAULT_MODULE_CODE = "DEFAULT";

    @Id
    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(nullable = false, length = 64)
    private String moduleCode = DEFAULT_MODULE_CODE;

    @Column(nullable = false)
    private Boolean isSystem = false;

    @Column(nullable = false)
    private Boolean isActive = true;
}


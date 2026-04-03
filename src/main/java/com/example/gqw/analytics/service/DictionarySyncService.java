package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.EventAttributeType;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.entity.StageMetricType;
import com.example.gqw.analytics.entity.StageType;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import com.example.gqw.analytics.repository.EventTypeRepository;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import com.example.gqw.analytics.repository.StageTypeRepository;
import com.example.gqw.analytics.source.EventAttributeTypeSource;
import com.example.gqw.analytics.source.EventTypeSource;
import com.example.gqw.analytics.source.StageMetricTypeSource;
import com.example.gqw.analytics.source.StageTypeSource;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictionarySyncService {

    private final List<EventTypeSource> eventTypeSources;
    private final List<StageTypeSource> stageTypeSources;
    private final List<StageMetricTypeSource> stageMetricTypeSources;
    private final List<EventAttributeTypeSource> eventAttributeTypeSources;
    private final EventTypeRepository eventTypeRepository;
    private final StageTypeRepository stageTypeRepository;
    private final StageMetricTypeRepository stageMetricTypeRepository;
    private final EventAttributeTypeRepository eventAttributeTypeRepository;

    public DictionarySyncService(
        List<EventTypeSource> eventTypeSources,
        List<StageTypeSource> stageTypeSources,
        List<StageMetricTypeSource> stageMetricTypeSources,
        List<EventAttributeTypeSource> eventAttributeTypeSources,
        EventTypeRepository eventTypeRepository,
        StageTypeRepository stageTypeRepository,
        StageMetricTypeRepository stageMetricTypeRepository,
        EventAttributeTypeRepository eventAttributeTypeRepository
    ) {
        this.eventTypeSources = eventTypeSources;
        this.stageTypeSources = stageTypeSources;
        this.stageMetricTypeSources = stageMetricTypeSources;
        this.eventAttributeTypeSources = eventAttributeTypeSources;
        this.eventTypeRepository = eventTypeRepository;
        this.stageTypeRepository = stageTypeRepository;
        this.stageMetricTypeRepository = stageMetricTypeRepository;
        this.eventAttributeTypeRepository = eventAttributeTypeRepository;
    }

    @Transactional
    public void syncAll() {
        syncEventTypes();
        syncStageTypes();
        syncMetricTypes();
        syncEventAttributeTypes();
    }

    @Transactional
    public void syncEventTypes() {
        for (EventTypeSource source : eventTypeSources) {
            for (EventType type : source.eventTypes()) {
                validateEventType(type);
                eventTypeRepository.upsert(type.getCode(), type.getName(), type.getDescription(), Boolean.TRUE.equals(type.getIsActive()));
            }
        }
    }

    @Transactional
    public void syncStageTypes() {
        for (StageTypeSource source : stageTypeSources) {
            for (StageType type : source.stageTypes()) {
                validateStageType(type);
                stageTypeRepository.upsert(type.getCode(), type.getName(), type.getDescription(), Boolean.TRUE.equals(type.getIsActive()));
            }
        }
    }

    @Transactional
    public void syncMetricTypes() {
        for (StageMetricTypeSource source : stageMetricTypeSources) {
            for (StageMetricType type : source.stageMetricTypes()) {
                validateMetricType(type);
                stageMetricTypeRepository.upsert(
                    type.getCode(),
                    type.getName(),
                    type.getDescription(),
                    type.getValueKind().name(),
                    type.getUnitDefault(),
                    Boolean.TRUE.equals(type.getIsActive())
                );
            }
        }
    }

    @Transactional
    public void syncEventAttributeTypes() {
        for (EventAttributeTypeSource source : eventAttributeTypeSources) {
            for (EventAttributeType type : source.eventAttributeTypes()) {
                validateEventAttributeType(type);
                eventAttributeTypeRepository.upsert(
                    type.getCode(),
                    type.getName(),
                    type.getDescription(),
                    type.getValueKind().name(),
                    type.getUnitDefault(),
                    Boolean.TRUE.equals(type.getIsActive())
                );
            }
        }
    }

    private void validateEventType(EventType type) {
        if (type.getCode() == null || type.getCode().isBlank()) {
            throw new IllegalArgumentException("EventType.code is required");
        }
        if (type.getName() == null || type.getName().isBlank()) {
            throw new IllegalArgumentException("EventType.name is required");
        }
    }

    private void validateStageType(StageType type) {
        if (type.getCode() == null || type.getCode().isBlank()) {
            throw new IllegalArgumentException("StageType.code is required");
        }
        if (type.getName() == null || type.getName().isBlank()) {
            throw new IllegalArgumentException("StageType.name is required");
        }
    }

    private void validateMetricType(StageMetricType type) {
        if (type.getCode() == null || type.getCode().isBlank()) {
            throw new IllegalArgumentException("StageMetricType.code is required");
        }
        if (type.getName() == null || type.getName().isBlank()) {
            throw new IllegalArgumentException("StageMetricType.name is required");
        }
        if (type.getValueKind() == null) {
            throw new IllegalArgumentException("StageMetricType.valueKind is required");
        }
    }

    private void validateEventAttributeType(EventAttributeType type) {
        if (type.getCode() == null || type.getCode().isBlank()) {
            throw new IllegalArgumentException("EventAttributeType.code is required");
        }
        if (type.getName() == null || type.getName().isBlank()) {
            throw new IllegalArgumentException("EventAttributeType.name is required");
        }
        if (type.getValueKind() == null) {
            throw new IllegalArgumentException("EventAttributeType.valueKind is required");
        }
    }
}


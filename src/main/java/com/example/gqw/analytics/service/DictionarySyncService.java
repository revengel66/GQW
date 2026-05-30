package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.EventAttributeType;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.entity.ModuleType;
import com.example.gqw.analytics.entity.StageMetricType;
import com.example.gqw.analytics.entity.StageType;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import com.example.gqw.analytics.repository.EventTypeRepository;
import com.example.gqw.analytics.repository.ModuleTypeRepository;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import com.example.gqw.analytics.repository.StageTypeRepository;
import com.example.gqw.analytics.source.EventAttributeTypeSource;
import com.example.gqw.analytics.source.EventTypeSource;
import com.example.gqw.analytics.source.ModuleTypeSource;
import com.example.gqw.analytics.source.StageMetricTypeSource;
import com.example.gqw.analytics.source.StageTypeSource;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictionarySyncService {

    private final List<ModuleTypeSource> moduleTypeSources;
    private final List<EventTypeSource> eventTypeSources;
    private final List<StageTypeSource> stageTypeSources;
    private final List<StageMetricTypeSource> stageMetricTypeSources;
    private final List<EventAttributeTypeSource> eventAttributeTypeSources;
    private final ModuleTypeRepository moduleTypeRepository;
    private final EventTypeRepository eventTypeRepository;
    private final StageTypeRepository stageTypeRepository;
    private final StageMetricTypeRepository stageMetricTypeRepository;
    private final EventAttributeTypeRepository eventAttributeTypeRepository;

    public DictionarySyncService(
        List<ModuleTypeSource> moduleTypeSources,
        List<EventTypeSource> eventTypeSources,
        List<StageTypeSource> stageTypeSources,
        List<StageMetricTypeSource> stageMetricTypeSources,
        List<EventAttributeTypeSource> eventAttributeTypeSources,
        ModuleTypeRepository moduleTypeRepository,
        EventTypeRepository eventTypeRepository,
        StageTypeRepository stageTypeRepository,
        StageMetricTypeRepository stageMetricTypeRepository,
        EventAttributeTypeRepository eventAttributeTypeRepository
    ) {
        this.moduleTypeSources = moduleTypeSources;
        this.eventTypeSources = eventTypeSources;
        this.stageTypeSources = stageTypeSources;
        this.stageMetricTypeSources = stageMetricTypeSources;
        this.eventAttributeTypeSources = eventAttributeTypeSources;
        this.moduleTypeRepository = moduleTypeRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.stageTypeRepository = stageTypeRepository;
        this.stageMetricTypeRepository = stageMetricTypeRepository;
        this.eventAttributeTypeRepository = eventAttributeTypeRepository;
    }

    @Transactional
    public void syncAll() {
        syncModuleTypes();
        syncEventTypes();
        syncStageTypes();
        syncMetricTypes();
        syncEventAttributeTypes();
    }

    @Transactional
    public void syncModuleTypes() {
        for (ModuleTypeSource source : moduleTypeSources) {
            for (ModuleType type : source.moduleTypes()) {
                validateModuleType(type);
                if (moduleTypeRepository.existsById(type.getCode())) {
                    continue;
                }
                moduleTypeRepository.save(type);
            }
        }
    }

    @Transactional
    public void syncEventTypes() {
        for (EventTypeSource source : eventTypeSources) {
            for (EventType type : source.eventTypes()) {
                validateEventType(type);
                if (!moduleTypeRepository.existsById(type.getModuleCode())) {
                    throw new IllegalArgumentException("Unknown module for event type: " + type.getModuleCode());
                }
                if (eventTypeRepository.existsById(type.getCode())) {
                    continue;
                }
                eventTypeRepository.save(type);
            }
        }
    }

    @Transactional
    public void syncStageTypes() {
        for (StageTypeSource source : stageTypeSources) {
            for (StageType type : source.stageTypes()) {
                validateStageType(type);
                if (stageTypeRepository.existsById(type.getCode())) {
                    continue;
                }
                stageTypeRepository.save(type);
            }
        }
    }

    @Transactional
    public void syncMetricTypes() {
        for (StageMetricTypeSource source : stageMetricTypeSources) {
            for (StageMetricType type : source.stageMetricTypes()) {
                validateMetricType(type);
                if (stageMetricTypeRepository.existsById(type.getCode())) {
                    continue;
                }
                stageMetricTypeRepository.save(type);
            }
        }
    }

    @Transactional
    public void syncEventAttributeTypes() {
        for (EventAttributeTypeSource source : eventAttributeTypeSources) {
            for (EventAttributeType type : source.eventAttributeTypes()) {
                validateEventAttributeType(type);
                if (eventAttributeTypeRepository.existsById(type.getCode())) {
                    continue;
                }
                eventAttributeTypeRepository.save(type);
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
        if (type.getModuleCode() == null || type.getModuleCode().isBlank()) {
            type.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        }
        if (type.getIsSystem() == null) {
            type.setIsSystem(false);
        }
    }

    private void validateModuleType(ModuleType type) {
        if (type.getCode() == null || type.getCode().isBlank()) {
            throw new IllegalArgumentException("ModuleType.code is required");
        }
        if (type.getName() == null || type.getName().isBlank()) {
            throw new IllegalArgumentException("ModuleType.name is required");
        }
    }

    private void validateStageType(StageType type) {
        if (type.getCode() == null || type.getCode().isBlank()) {
            throw new IllegalArgumentException("StageType.code is required");
        }
        if (type.getName() == null || type.getName().isBlank()) {
            throw new IllegalArgumentException("StageType.name is required");
        }
        if (type.getIsSystem() == null) {
            type.setIsSystem(false);
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
        if (type.getIsSystem() == null) {
            type.setIsSystem(false);
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
        if (type.getIsSystem() == null) {
            type.setIsSystem(false);
        }
    }
}


package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsCodeAlias;
import com.example.gqw.analytics.entity.AnalyticsCodeAliasType;
import com.example.gqw.analytics.entity.EventAttributeType;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.entity.ModuleType;
import com.example.gqw.analytics.entity.StageType;
import com.example.gqw.analytics.entity.StageMetricType;
import com.example.gqw.analytics.repository.AnalyticsEventAttributeRepository;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.analytics.repository.AnalyticsStageMetricRepository;
import com.example.gqw.analytics.repository.AnalyticsStageRepository;
import com.example.gqw.analytics.repository.AnalyticsCodeAliasRepository;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import com.example.gqw.analytics.repository.EventTypeRepository;
import com.example.gqw.analytics.repository.ModuleTypeRepository;
import com.example.gqw.analytics.repository.StageTypeRepository;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsDictionaryAdminService {
    private final EventTypeRepository eventTypeRepository;
    private final ModuleTypeRepository moduleTypeRepository;
    private final EventAttributeTypeRepository attributeTypeRepository;
    private final StageTypeRepository stageTypeRepository;
    private final StageMetricTypeRepository metricTypeRepository;
    private final AnalyticsStageRepository analyticsStageRepository;
    private final AnalyticsCodeAliasRepository aliasRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final AnalyticsEventAttributeRepository analyticsEventAttributeRepository;
    private final AnalyticsStageMetricRepository analyticsStageMetricRepository;
    private final AnalyticsEventTypeMaintenanceService analyticsEventTypeMaintenanceService;
    private final JdbcTemplate jdbcTemplate;

    public AnalyticsDictionaryAdminService(
        EventTypeRepository eventTypeRepository,
        ModuleTypeRepository moduleTypeRepository,
        EventAttributeTypeRepository attributeTypeRepository,
        StageTypeRepository stageTypeRepository,
        StageMetricTypeRepository metricTypeRepository,
        AnalyticsStageRepository analyticsStageRepository,
        AnalyticsCodeAliasRepository aliasRepository,
        AnalyticsEventRepository analyticsEventRepository,
        AnalyticsEventAttributeRepository analyticsEventAttributeRepository,
        AnalyticsStageMetricRepository analyticsStageMetricRepository,
        AnalyticsEventTypeMaintenanceService analyticsEventTypeMaintenanceService,
        JdbcTemplate jdbcTemplate
    ) {
        this.eventTypeRepository = eventTypeRepository;
        this.moduleTypeRepository = moduleTypeRepository;
        this.attributeTypeRepository = attributeTypeRepository;
        this.stageTypeRepository = stageTypeRepository;
        this.metricTypeRepository = metricTypeRepository;
        this.analyticsStageRepository = analyticsStageRepository;
        this.aliasRepository = aliasRepository;
        this.analyticsEventRepository = analyticsEventRepository;
        this.analyticsEventAttributeRepository = analyticsEventAttributeRepository;
        this.analyticsStageMetricRepository = analyticsStageMetricRepository;
        this.analyticsEventTypeMaintenanceService = analyticsEventTypeMaintenanceService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<ModuleType> allModules() {
        return moduleTypeRepository.findAll().stream()
            .filter(module -> !EventType.DEFAULT_MODULE_CODE.equalsIgnoreCase(module.getCode()))
            .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<EventType> allEventTypes(String moduleCodeRaw) {
        String moduleCode = normalizeCode(moduleCodeRaw, false);
        List<EventType> source = moduleCode == null
            ? eventTypeRepository.findAll()
            : eventTypeRepository.findByModuleCodeOrderByCodeAsc(moduleCode);
        return source.stream()
            .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> eventTypeEventCounts(List<EventType> eventTypes) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (eventTypes == null) {
            return counts;
        }
        for (EventType type : eventTypes) {
            if (type == null || type.getCode() == null || type.getCode().isBlank()) {
                continue;
            }
            counts.put(type.getCode(), analyticsEventRepository.countByEventTypeCode(type.getCode()));
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> eventAttributeValueCounts(List<EventAttributeType> attributeTypes) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (attributeTypes == null) {
            return counts;
        }
        for (EventAttributeType type : attributeTypes) {
            if (type == null || type.getCode() == null || type.getCode().isBlank()) {
                continue;
            }
            counts.put(type.getCode(), analyticsEventAttributeRepository.countByAttributeTypeCode(type.getCode()));
        }
        return counts;
    }

    @Transactional
    public void createOrUpdateModuleType(
        String originalCodeRaw,
        String codeRaw,
        String nameRaw,
        String descriptionRaw
    ) {
        String originalCode = normalizeCode(originalCodeRaw, false);
        String code = normalizeCode(codeRaw, true);
        String name = normalizeName(nameRaw);
        String description = normalizeDescription(descriptionRaw);
        if (EventType.DEFAULT_MODULE_CODE.equals(code)) {
            throw new IllegalArgumentException("Системный модуль DEFAULT недоступен для редактирования");
        }

        ModuleType existing = moduleTypeRepository.findById(code).orElse(null);
        if (originalCode != null && !originalCode.equals(code)) {
            ModuleType old = moduleTypeRepository.findById(originalCode)
                .orElseThrow(() -> new IllegalArgumentException("Модуль не найден: " + originalCode));
            if (existing != null) {
                throw new IllegalArgumentException("Код модуля уже существует: " + code);
            }
            old.setIsActive(false);
            moduleTypeRepository.save(old);
        }

        ModuleType entity = existing != null ? existing : new ModuleType();
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription(description);
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        moduleTypeRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<EventAttributeType> allEventAttributeTypes() {
        return attributeTypeRepository.findAll().stream()
            .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<StageType> allStageTypes() {
        return stageTypeRepository.findAll().stream()
            .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<StageMetricType> allStageMetricTypes() {
        return metricTypeRepository.findAll().stream()
            .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsCodeAlias> allAliases(AnalyticsCodeAliasType aliasType) {
        return aliasRepository.findAllByAliasTypeOrderBySourceCodeAsc(aliasType);
    }

    @Transactional
    public void createOrUpdateEventType(
        String originalCodeRaw,
        String codeRaw,
        String moduleCodeRaw,
        String nameRaw,
        String descriptionRaw
    ) {
        String originalCode = normalizeCode(originalCodeRaw, false);
        String code = normalizeCode(codeRaw, true);
        String moduleCode = normalizeCode(moduleCodeRaw, false);
        if (moduleCode == null) {
            moduleCode = EventType.DEFAULT_MODULE_CODE;
        }
        String name = normalizeName(nameRaw);
        String description = normalizeDescription(descriptionRaw);
        ensureModuleExists(moduleCode);

        EventType existing = eventTypeRepository.findById(code).orElse(null);
        if (originalCode != null && !originalCode.equals(code)) {
            EventType old = eventTypeRepository.findById(originalCode)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + originalCode));
            if (existing != null && !originalCode.equals(code)) {
                throw new IllegalArgumentException("Код события уже существует: " + code);
            }
            old.setIsActive(false);
            eventTypeRepository.save(old);
            upsertAlias(AnalyticsCodeAliasType.EVENT, originalCode, code);
        }

        EventType entity = existing != null ? existing : new EventType();
        entity.setCode(code);
        entity.setModuleCode(moduleCode);
        entity.setName(name);
        entity.setDescription(description);
        if (entity.getIsSystem() == null) {
            entity.setIsSystem(false);
        }
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        eventTypeRepository.save(entity);
        analyticsEventRepository.bulkUpdateModuleCodeByEventTypeCode(code, moduleCode);
        if (originalCode != null && !originalCode.equals(code)) {
            analyticsEventRepository.bulkUpdateModuleCodeByEventTypeCode(originalCode, moduleCode);
        }
    }

    @Transactional
    public void createOrUpdateEventAttributeType(
        String originalCodeRaw,
        String codeRaw,
        String nameRaw,
        String descriptionRaw,
        String valueKindRaw,
        String unitDefaultRaw
    ) {
        String originalCode = normalizeCode(originalCodeRaw, false);
        String code = normalizeCode(codeRaw, true);
        if (isSystemEventAttributeCode(code)
            || (originalCode != null && isSystemEventAttributeCode(originalCode))) {
            throw new IllegalArgumentException("Системный атрибут из коробки нельзя редактировать: " + code);
        }
        String name = normalizeName(nameRaw);
        String description = normalizeDescription(descriptionRaw);
        MetricValueKind valueKind = parseValueKind(valueKindRaw);
        String unitDefault = normalizeUnit(unitDefaultRaw);

        EventAttributeType existing = attributeTypeRepository.findById(code).orElse(null);
        if (originalCode != null && !originalCode.equals(code)) {
            EventAttributeType old = attributeTypeRepository.findById(originalCode)
                .orElseThrow(() -> new IllegalArgumentException("Атрибут не найден: " + originalCode));
            if (existing != null) {
                throw new IllegalArgumentException("Код атрибута уже существует: " + code);
            }
            old.setIsActive(false);
            attributeTypeRepository.save(old);
            upsertAlias(AnalyticsCodeAliasType.ATTRIBUTE, originalCode, code);
        }

        EventAttributeType entity = existing != null ? existing : new EventAttributeType();
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription(description);
        entity.setValueKind(valueKind);
        entity.setUnitDefault(unitDefault);
        if (entity.getIsSystem() == null) {
            entity.setIsSystem(false);
        }
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        attributeTypeRepository.save(entity);
    }

    @Transactional
    public void createOrUpdateStageMetricType(
        String originalCodeRaw,
        String codeRaw,
        String nameRaw,
        String descriptionRaw,
        String readingGuideRaw,
        String valueKindRaw,
        String unitDefaultRaw
    ) {
        String originalCode = normalizeCode(originalCodeRaw, false);
        String code = normalizeCode(codeRaw, true);
        if (isSystemStageMetricCode(code)
            || (originalCode != null && isSystemStageMetricCode(originalCode))) {
            throw new IllegalArgumentException("Системную метрику из коробки нельзя редактировать: " + code);
        }
        String name = normalizeName(nameRaw);
        String description = normalizeDescription(descriptionRaw);
        String readingGuide = normalizeDescription(readingGuideRaw);
        MetricValueKind valueKind = parseValueKind(valueKindRaw);
        String unitDefault = normalizeUnit(unitDefaultRaw);

        StageMetricType existing = metricTypeRepository.findById(code).orElse(null);
        if (originalCode != null && !originalCode.equals(code)) {
            StageMetricType old = metricTypeRepository.findById(originalCode)
                .orElseThrow(() -> new IllegalArgumentException("Метрика не найдена: " + originalCode));
            if (existing != null) {
                throw new IllegalArgumentException("Код метрики уже существует: " + code);
            }
            old.setIsActive(false);
            metricTypeRepository.save(old);
            upsertAlias(AnalyticsCodeAliasType.METRIC, originalCode, code);
        }

        StageMetricType entity = existing != null ? existing : new StageMetricType();
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription(description);
        entity.setReadingGuide(readingGuide);
        entity.setValueKind(valueKind);
        entity.setUnitDefault(unitDefault);
        if (entity.getIsSystem() == null) {
            entity.setIsSystem(false);
        }
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        metricTypeRepository.save(entity);
    }

    @Transactional
    public void createOrUpdateStageType(
        String originalCodeRaw,
        String codeRaw,
        String nameRaw,
        String descriptionRaw
    ) {
        String originalCode = normalizeCode(originalCodeRaw, false);
        String code = normalizeCode(codeRaw, true);
        if (isSystemStageCode(code)
            || (originalCode != null && isSystemStageCode(originalCode))) {
            throw new IllegalArgumentException("Системный этап из коробки нельзя редактировать: " + code);
        }
        String name = normalizeName(nameRaw);
        String description = normalizeDescription(descriptionRaw);

        StageType existing = stageTypeRepository.findById(code).orElse(null);
        if (originalCode != null && !originalCode.equals(code)) {
            StageType old = stageTypeRepository.findById(originalCode)
                .orElseThrow(() -> new IllegalArgumentException("Этап не найден: " + originalCode));
            if (existing != null) {
                throw new IllegalArgumentException("Код этапа уже существует: " + code);
            }
            old.setIsActive(false);
            stageTypeRepository.save(old);
        }

        StageType entity = existing != null ? existing : new StageType();
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription(description);
        if (entity.getIsSystem() == null) {
            entity.setIsSystem(false);
        }
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        stageTypeRepository.save(entity);
    }

    @Transactional
    public boolean toggleEventType(String codeRaw) {
        String code = normalizeCode(codeRaw, true);
        EventType entity = eventTypeRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + code));
        boolean nextState = !Boolean.TRUE.equals(entity.getIsActive());
        entity.setIsActive(nextState);
        eventTypeRepository.save(entity);
        return nextState;
    }

    @Transactional
    public boolean toggleModuleType(String codeRaw) {
        String code = normalizeCode(codeRaw, true);
        if (EventType.DEFAULT_MODULE_CODE.equals(code)) {
            throw new IllegalArgumentException("Системный модуль DEFAULT недоступен для изменения");
        }
        ModuleType entity = moduleTypeRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Модуль не найден: " + code));
        boolean nextState = !Boolean.TRUE.equals(entity.getIsActive());
        entity.setIsActive(nextState);
        moduleTypeRepository.save(entity);
        return nextState;
    }

    @Transactional
    public boolean toggleEventAttributeType(String codeRaw) {
        String code = normalizeCode(codeRaw, true);
        EventAttributeType entity = attributeTypeRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Атрибут не найден: " + code));
        boolean nextState = !Boolean.TRUE.equals(entity.getIsActive());
        entity.setIsActive(nextState);
        attributeTypeRepository.save(entity);
        return nextState;
    }

    @Transactional
    public boolean toggleStageMetricType(String codeRaw) {
        String code = normalizeCode(codeRaw, true);
        StageMetricType entity = metricTypeRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Метрика не найдена: " + code));
        boolean nextState = !Boolean.TRUE.equals(entity.getIsActive());
        entity.setIsActive(nextState);
        metricTypeRepository.save(entity);
        return nextState;
    }

    @Transactional
    public boolean toggleStageType(String codeRaw) {
        String code = normalizeCode(codeRaw, true);
        StageType entity = stageTypeRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Этап не найден: " + code));
        boolean nextState = !Boolean.TRUE.equals(entity.getIsActive());
        entity.setIsActive(nextState);
        stageTypeRepository.save(entity);
        return nextState;
    }

    @Transactional
    public void upsertAlias(AnalyticsCodeAliasType aliasType, String sourceCodeRaw, String targetCodeRaw) {
        String sourceCode = normalizeCode(sourceCodeRaw, true);
        String targetCode = normalizeCode(targetCodeRaw, true);
        if (sourceCode.equals(targetCode)) {
            return;
        }
        AnalyticsCodeAlias alias = aliasRepository.findByAliasTypeAndSourceCode(aliasType, sourceCode)
            .orElseGet(AnalyticsCodeAlias::new);
        alias.setAliasType(aliasType);
        alias.setSourceCode(sourceCode);
        alias.setTargetCode(targetCode);
        alias.setIsActive(true);
        aliasRepository.save(alias);
    }

    @Transactional
    public boolean toggleAlias(Long aliasId) {
        AnalyticsCodeAlias alias = aliasRepository.findById(aliasId)
            .orElseThrow(() -> new IllegalArgumentException("Alias не найден"));
        boolean nextState = !Boolean.TRUE.equals(alias.getIsActive());
        alias.setIsActive(nextState);
        aliasRepository.save(alias);
        return nextState;
    }

    @Transactional
    public void deleteModuleType(String codeRaw) {
        ModuleDeletePrecheck precheck = precheckDeleteModuleType(codeRaw);
        if (!precheck.deletable()) {
            throw new IllegalArgumentException(precheck.reason());
        }

        List<EventType> moduleEventTypes = eventTypeRepository.findByModuleCodeOrderByCodeAsc(precheck.code());
        for (EventType eventType : moduleEventTypes) {
            if (eventType == null || eventType.getCode() == null || eventType.getCode().isBlank()) {
                continue;
            }
            deleteEventType(eventType.getCode());
        }

        jdbcTemplate.update(
            """
                delete from analytics.stage_metric sm
                using analytics.stage s, analytics.event e
                where sm.stage_id = s.id
                  and s.event_id = e.id
                  and e.module_code = ?
                """,
            precheck.code()
        );
        jdbcTemplate.update(
            """
                delete from analytics.stage s
                using analytics.event e
                where s.event_id = e.id
                  and e.module_code = ?
                """,
            precheck.code()
        );
        jdbcTemplate.update(
            """
                delete from analytics.event_attribute ea
                using analytics.event e
                where ea.event_id = e.id
                  and e.module_code = ?
                """,
            precheck.code()
        );
        jdbcTemplate.update("delete from analytics.event where module_code = ?", precheck.code());
        moduleTypeRepository.delete(precheck.moduleType());
    }

    @Transactional(readOnly = true)
    public ModuleDeletePrecheck precheckDeleteModuleType(String codeRaw) {
        String code = normalizeCode(codeRaw, true);
        if (EventType.DEFAULT_MODULE_CODE.equals(code)) {
            return new ModuleDeletePrecheck(
                null,
                code,
                false,
                "Нельзя удалить системный модуль DEFAULT",
                List.of(),
                0L,
                0L
            );
        }
        ModuleType moduleType = moduleTypeRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Модуль не найден: " + code));

        long eventTypeCount = eventTypeRepository.countByModuleCode(code);
        long eventCount = analyticsEventRepository.countByModuleCode(code);

        List<String> usages = new java.util.ArrayList<>();
        if (eventTypeCount > 0) {
            List<EventType> moduleEventTypes = eventTypeRepository.findByModuleCodeOrderByCodeAsc(code);
            for (EventType eventType : moduleEventTypes) {
                if (eventType == null || eventType.getCode() == null || eventType.getCode().isBlank()) {
                    continue;
                }
                List<String> eventUsages = analyticsEventTypeMaintenanceService.findTrackedEventUsages(eventType.getCode());
                for (String usage : eventUsages) {
                    usages.add(eventType.getCode() + " → " + usage);
                }
            }
        }

        boolean deletable = usages.isEmpty();
        String reason = "";
        if (!usages.isEmpty()) {
            int limit = Math.min(usages.size(), 8);
            String joined = String.join("; ", usages.subList(0, limit));
            String suffix = usages.size() > limit
                ? "; ... и ещё " + (usages.size() - limit)
                : "";
            reason = "Нельзя удалить модуль: он используется в коде (" + usages.size() + "): " + joined + suffix;
        }

        return new ModuleDeletePrecheck(moduleType, code, deletable, reason, usages, eventTypeCount, eventCount);
    }

    @Transactional
    public void deleteEventType(String codeRaw) {
        String code = normalizeCode(codeRaw, true);
        EventType eventTypeForDelete = eventTypeRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Event type not found: " + code));
        if (Boolean.TRUE.equals(eventTypeForDelete.getIsSystem())) {
            throw new IllegalArgumentException("System event types cannot be deleted. Disable tracking instead.");
        }
        List<String> usages = analyticsEventTypeMaintenanceService.findTrackedEventUsages(code);
        if (!usages.isEmpty()) {
            int limit = Math.min(usages.size(), 8);
            String joined = String.join("; ", usages.subList(0, limit));
            String suffix = usages.size() > limit
                ? "; ... и ещё " + (usages.size() - limit)
                : "";
            throw new IllegalArgumentException(
                "Нельзя удалить событие: оно используется в коде (" + usages.size() + "): " + joined + suffix
            );
        }
        EventType eventType = eventTypeRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + code));

        jdbcTemplate.update(
            """
                delete from analytics.stage_metric sm
                using analytics.stage s, analytics.event e
                where sm.stage_id = s.id
                  and s.event_id = e.id
                  and e.event_type_code = ?
                """,
            code
        );
        jdbcTemplate.update(
            """
                delete from analytics.stage s
                using analytics.event e
                where s.event_id = e.id
                  and e.event_type_code = ?
                """,
            code
        );
        jdbcTemplate.update(
            """
                delete from analytics.event_attribute ea
                using analytics.event e
                where ea.event_id = e.id
                  and e.event_type_code = ?
                """,
            code
        );
        jdbcTemplate.update("delete from analytics.event where event_type_code = ?", code);
        jdbcTemplate.update("delete from analytics.aggregated_metric where event_type_code = ?", code);
        jdbcTemplate.update(
            """
                delete from analytics.code_alias
                where alias_type = 'EVENT'
                  and (source_code = ? or target_code = ?)
                """,
            code,
            code
        );
        eventTypeRepository.delete(eventType);
    }

    @Transactional
    public void deleteEventAttributeType(String codeRaw) {
        String code = normalizeCode(codeRaw, true);
        if (isSystemEventAttributeCode(code)) {
            throw new IllegalArgumentException("Нельзя удалить системный атрибут из коробки: " + code);
        }
        List<String> usages = analyticsEventTypeMaintenanceService.findTrackedAttributeUsages(code);
        if (!usages.isEmpty()) {
            int limit = Math.min(usages.size(), 8);
            String joined = String.join("; ", usages.subList(0, limit));
            String suffix = usages.size() > limit
                ? "; ... и ещё " + (usages.size() - limit)
                : "";
            throw new IllegalArgumentException(
                "Нельзя удалить атрибут: он используется в коде (" + usages.size() + "): " + joined + suffix
            );
        }
        EventAttributeType attributeType = attributeTypeRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Атрибут не найден: " + code));
        jdbcTemplate.update("delete from analytics.event_attribute where attribute_type_code = ?", code);
        jdbcTemplate.update(
            """
                delete from analytics.code_alias
                where alias_type = 'ATTRIBUTE'
                  and (source_code = ? or target_code = ?)
                """,
            code,
            code
        );
        attributeTypeRepository.delete(attributeType);
    }

    @Transactional
    public void deleteStageMetricType(String codeRaw) {
        String code = normalizeCode(codeRaw, true);
        if (isSystemStageMetricCode(code)) {
            throw new IllegalArgumentException("Нельзя удалить системную метрику из коробки: " + code);
        }
        StageMetricType metricType = metricTypeRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Метрика не найдена: " + code));
        long usageCount = analyticsStageMetricRepository.countByMetricTypeCode(code);
        if (usageCount > 0) {
            throw new IllegalArgumentException("Нельзя удалить метрику: есть связанные значения (" + usageCount + ")");
        }
        metricTypeRepository.delete(metricType);
    }

    @Transactional
    public void deleteStageType(String codeRaw) {
        String code = normalizeCode(codeRaw, true);
        if (isSystemStageCode(code)) {
            throw new IllegalArgumentException("Нельзя удалить системный этап из коробки: " + code);
        }
        StageType stageType = stageTypeRepository.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Этап не найден: " + code));
        long stageUsageCount = analyticsStageRepository.countByStageTypeCode(code);
        if (stageUsageCount > 0) {
            throw new IllegalArgumentException("Нельзя удалить этап: есть связанные вызовы (" + stageUsageCount + ")");
        }
        Integer aggregateUsageCount = jdbcTemplate.queryForObject(
            "select count(*) from analytics.aggregated_metric where stage_type_code = ?",
            Integer.class,
            code
        );
        if (aggregateUsageCount != null && aggregateUsageCount > 0) {
            throw new IllegalArgumentException(
                "Нельзя удалить этап: есть связанные агрегированные метрики (" + aggregateUsageCount + ")"
            );
        }
        stageTypeRepository.delete(stageType);
    }

    @Transactional
    public void deleteAlias(Long aliasId) {
        AnalyticsCodeAlias alias = aliasRepository.findById(aliasId)
            .orElseThrow(() -> new IllegalArgumentException("Alias не найден"));
        aliasRepository.delete(alias);
    }

    @Transactional(readOnly = true)
    public Set<String> builtInStageMetricCodes() {
        return metricTypeRepository.findByIsSystemTrueOrderByCodeAsc().stream()
            .map(StageMetricType::getCode)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public Set<String> builtInStageCodes() {
        return stageTypeRepository.findByIsSystemTrueOrderByCodeAsc().stream()
            .map(StageType::getCode)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public Set<String> builtInEventAttributeCodes() {
        return attributeTypeRepository.findByIsSystemTrueOrderByCodeAsc().stream()
            .map(EventAttributeType::getCode)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private boolean isSystemEventAttributeCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return attributeTypeRepository.findById(code)
            .map(EventAttributeType::getIsSystem)
            .orElse(false);
    }

    private boolean isSystemStageMetricCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return metricTypeRepository.findById(code)
            .map(StageMetricType::getIsSystem)
            .orElse(false);
    }

    private boolean isSystemStageCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return stageTypeRepository.findById(code)
            .map(StageType::getIsSystem)
            .orElse(false);
    }

    public record ModuleDeletePrecheck(
        ModuleType moduleType,
        String code,
        boolean deletable,
        String reason,
        List<String> usages,
        long eventTypeCount,
        long eventCount
    ) {
    }

    private static MetricValueKind parseValueKind(String valueKindRaw) {
        if (valueKindRaw == null || valueKindRaw.isBlank()) {
            return MetricValueKind.TEXT;
        }
        try {
            return MetricValueKind.valueOf(valueKindRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Некорректный тип значения: " + valueKindRaw);
        }
    }

    private static String normalizeCode(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("Код обязателен");
            }
            return null;
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (code.length() < 2 || code.length() > 64) {
            throw new IllegalArgumentException("Код должен быть от 2 до 64 символов");
        }
        return code;
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Название обязательно");
        }
        String name = value.trim();
        if (name.length() < 2 || name.length() > 128) {
            throw new IllegalArgumentException("Название должно быть от 2 до 128 символов");
        }
        return name;
    }

    private static String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        String description = value.trim();
        if (description.isEmpty()) {
            return null;
        }
        if (description.length() > 512) {
            throw new IllegalArgumentException("Описание не должно превышать 512 символов");
        }
        return description;
    }

    private static String normalizeUnit(String value) {
        if (value == null) {
            return null;
        }
        String unit = value.trim();
        if (unit.isEmpty()) {
            return null;
        }
        if (unit.length() > 32) {
            throw new IllegalArgumentException("Единица измерения не должна превышать 32 символа");
        }
        return unit;
    }

    private void ensureModuleExists(String moduleCode) {
        moduleTypeRepository.findById(moduleCode)
            .orElseThrow(() -> new IllegalArgumentException("Модуль не найден: " + moduleCode));
    }
}

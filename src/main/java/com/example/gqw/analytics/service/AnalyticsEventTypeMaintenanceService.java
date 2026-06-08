package com.example.gqw.analytics.service;

import org.springframework.beans.factory.annotation.Qualifier;

import com.example.gqw.analytics.aop.TrackAnalyticsEvent;
import com.example.gqw.analytics.aop.TrackAnalyticsAttribute;
import com.example.gqw.analytics.aop.TrackAnalyticsMetric;
import com.example.gqw.analytics.aop.TrackAnalyticsStageMetric;
import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.repository.EventTypeRepository;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Service
public class AnalyticsEventTypeMaintenanceService {

    private static final String AUTO_PREFIX_RU = "Автоматически создано системой аналитики";
    private static final String AUTO_PREFIX_EN = "Automatically created by analytics system";
    private static final String HTTP_REQUEST_ERROR_CODE = "HTTP_REQUEST_ERROR";
    private static final String HTTP_REQUEST_ERROR_DESCRIPTION =
        "Техническая ошибка HTTP-запроса: фиксирует неуспешные запросы, статус ответа, путь и traceId.";
    private static final List<String> AUTO_ACTION_SUFFIXES = List.of(
        "DETAIL_VIEW",
        "EDIT_VIEW",
        "CREATE_VIEW",
        "VIEW",
        "CREATE",
        "UPDATE",
        "DELETE",
        "DUPLICATE",
        "ACTION"
    );
    private static final Map<String, GeneratedPresentation> FRIENDLY_OVERRIDES = Map.ofEntries(
        Map.entry("DASHBOARD_VIEW", new GeneratedPresentation(
            "Открытие страницы дашборда админ-панели",
            "Открытие страницы дашборда админ-панели."
        )),
        Map.entry("CART_VIEW", new GeneratedPresentation(
            "Открытие страницы корзины",
            "Открытие страницы корзины."
        )),
        Map.entry("CATALOG_VIEW", new GeneratedPresentation(
            "Открытие страницы каталога",
            "Открытие страницы каталога."
        )),
        Map.entry("LOGIN_VIEW", new GeneratedPresentation(
            "Открытие страницы входа",
            "Открытие страницы входа в аккаунт."
        )),
        Map.entry("PRODUCT_VIEW", new GeneratedPresentation(
            "Открытие страницы товара",
            "Открытие страницы товара."
        )),
        Map.entry("PRODUCT_LIST_VIEW", new GeneratedPresentation(
            "Открытие страницы списка товаров в админ-панели",
            "Открытие страницы списка товаров в админ-панели."
        )),
        Map.entry("PRODUCT_CREATE", new GeneratedPresentation(
            "Создание товара в админ-панели",
            "Создание нового товара в админ-панели."
        )),
        Map.entry("PRODUCT_UPDATE", new GeneratedPresentation(
            "Изменение товара в админ-панели",
            "Изменение данных товара в админ-панели."
        )),
        Map.entry("PRODUCT_DELETE", new GeneratedPresentation(
            "Удаление товара в админ-панели",
            "Удаление товара в админ-панели."
        )),
        Map.entry("PRODUCT_DUPLICATE", new GeneratedPresentation(
            "Дублирование товара в админ-панели",
            "Создание копии товара в админ-панели."
        )),
        Map.entry("CATEGORY_LIST_VIEW", new GeneratedPresentation(
            "Открытие страницы списка категорий в админ-панели",
            "Открытие страницы списка категорий в админ-панели."
        )),
        Map.entry("CATEGORY_CREATE", new GeneratedPresentation(
            "Создание категории в админ-панели",
            "Создание новой категории в админ-панели."
        )),
        Map.entry("CATEGORY_UPDATE", new GeneratedPresentation(
            "Изменение категории в админ-панели",
            "Изменение данных категории в админ-панели."
        )),
        Map.entry("CATEGORY_DELETE", new GeneratedPresentation(
            "Удаление категории в админ-панели",
            "Удаление категории в админ-панели."
        )),
        Map.entry("SHOPMODELATTRIBUTESADVICE_VIEW", new GeneratedPresentation(
            "Подготовка общих данных для страниц магазина",
            "Служебная подготовка общих данных для рендера страниц магазина."
        )),
        Map.entry("SHOP_MODEL_ATTRIBUTES_ADVICE_VIEW", new GeneratedPresentation(
            "Подготовка общих данных для страниц магазина",
            "Служебная подготовка общих данных для рендера страниц магазина."
        )),
        Map.entry("FILTER_LIST_VIEW", new GeneratedPresentation(
            "Открытие страницы списка фильтров в админ-панели",
            "Открытие страницы фильтров каталога товаров в админ-панели."
        )),
        Map.entry("FILTER_CREATE", new GeneratedPresentation(
            "Создание фильтра в админ-панели",
            "Создание нового фильтра в админ-панели."
        )),
        Map.entry("FILTER_UPDATE", new GeneratedPresentation(
            "Изменение фильтра в админ-панели",
            "Изменение данных фильтра в админ-панели."
        )),
        Map.entry("FILTER_DELETE", new GeneratedPresentation(
            "Удаление фильтра в админ-панели",
            "Удаление фильтра в админ-панели."
        )),
        Map.entry("ORDER_LIST_VIEW", new GeneratedPresentation(
            "Открытие страницы списка заказов в админ-панели",
            "Открытие страницы списка заказов в админ-панели."
        )),
        Map.entry("REVIEW_LIST_VIEW", new GeneratedPresentation(
            "Открытие страницы списка отзывов в админ-панели",
            "Открытие страницы списка отзывов в админ-панели."
        )),
        Map.entry("USER_LIST_VIEW", new GeneratedPresentation(
            "Открытие страницы списка пользователей в админ-панели",
            "Открытие страницы списка пользователей в админ-панели."
        )),
        Map.entry("SUPPORT_LIST_VIEW", new GeneratedPresentation(
            "Открытие страницы заявок в поддержку в админ-панели",
            "Открытие страницы заявок в поддержку в админ-панели."
        )),
        Map.entry("SUPPORT_PAGE_VIEW", new GeneratedPresentation(
            "Открытие страницы поддержки",
            "Пользователь открыл страницу поддержки."
        )),
        Map.entry("CREDENTIALS_UPDATE", new GeneratedPresentation(
            "Обновление учётных данных админки",
            "Администратор изменил логин или пароль админ-панели."
        )),
        Map.entry("ACCOUNT_VIEW", new GeneratedPresentation(
            "Открытие страницы личного кабинета",
            "Пользователь открыл страницу личного кабинета."
        )),
        Map.entry("ACCOUNT_PROFILE_UPDATE", new GeneratedPresentation(
            "Обновление профиля в личном кабинете",
            "Пользователь обновил данные профиля в личном кабинете."
        )),
        Map.entry("ACCOUNT_ADDRESS_UPDATE", new GeneratedPresentation(
            "Обновление адреса в личном кабинете",
            "Пользователь обновил адрес доставки в личном кабинете."
        )),
        Map.entry("ACCOUNT_ORDER_CANCEL", new GeneratedPresentation(
            "Отмена заказа из личного кабинета",
            "Пользователь отменил заказ из личного кабинета."
        )),
        Map.entry("ACCOUNT_ORDER_UPDATE", new GeneratedPresentation(
            "Изменение заказа из личного кабинета",
            "Пользователь изменил параметры заказа из личного кабинета."
        )),
        Map.entry("ACCOUNT_SUPPORT_CREATE", new GeneratedPresentation(
            "Создание обращения в поддержку из личного кабинета",
            "Пользователь создал обращение в поддержку из личного кабинета."
        )),
        Map.entry("ACCOUNT_DELETE", new GeneratedPresentation(
            "Удаление аккаунта",
            "Пользователь удалил свой аккаунт."
        ))
    );

    private final EventTypeRepository eventTypeRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationContext applicationContext;
    private final AnalyticsSystemEventClassifier systemEventClassifier;

    public AnalyticsEventTypeMaintenanceService(
        EventTypeRepository eventTypeRepository,
        @Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate,
        ApplicationContext applicationContext,
        AnalyticsSystemEventClassifier systemEventClassifier
    ) {
        this.eventTypeRepository = eventTypeRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.applicationContext = applicationContext;
        this.systemEventClassifier = systemEventClassifier;
    }

    @Transactional(transactionManager = "analyticsTransactionManager")
    public void maintainEventTypes() {
        migrateLegacyActionCodes();
        migratePrefixedCodes();
        syncControllerEventTypes();
        normalizeAutoGeneratedEventTypes();
        applyFriendlyOverrides();
        applySystemEventDescriptionsIfBlank();
        classifySystemEventTypes();
    }

    @Transactional(transactionManager = "analyticsTransactionManager")
    public void seedMissingEventTypesOnly() {
        mergeEventTypeCode("PRODUCTS_ACTION", "PRODUCT_DUPLICATE");
        Map<String, GeneratedPresentation> autoPresentations = new LinkedHashMap<>();
        for (BeanMethod beanMethod : collectApplicationMethods().values()) {
            Method method = beanMethod.method();
            TrackAnalyticsEvent track = AnnotationUtils.findAnnotation(method, TrackAnalyticsEvent.class);
            if (track != null) {
                String manualCode = stripModulePrefix(normalizeCode(track.code()));
                if (manualCode != null && !manualCode.isBlank()) {
                    ensureEventTypeExistsMissingOnly(manualCode, null);
                }
                continue;
            }

            AutoEvent autoEvent = buildAutoEvent(method);
            if (autoEvent == null || autoEvent.code() == null || autoEvent.code().isBlank()) {
                continue;
            }
            autoPresentations.putIfAbsent(autoEvent.code(), autoEvent.presentation());
        }

        for (Map.Entry<String, GeneratedPresentation> entry : autoPresentations.entrySet()) {
            ensureEventTypeExistsMissingOnly(entry.getKey(), entry.getValue());
        }
    }

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
    public Set<String> collectTrackedEventCodes() {
        Set<String> result = new LinkedHashSet<>();
        for (BeanMethod beanMethod : collectApplicationMethods().values()) {
            Method method = beanMethod.method();
            TrackAnalyticsEvent track = AnnotationUtils.findAnnotation(method, TrackAnalyticsEvent.class);
            if (track != null) {
                String manualCode = stripModulePrefix(normalizeCode(track.code()));
                if (manualCode != null && !manualCode.isBlank()) {
                    result.add(manualCode);
                }
                continue;
            }
            AutoEvent autoEvent = buildAutoEvent(method);
            if (autoEvent != null && autoEvent.code() != null && !autoEvent.code().isBlank()) {
                result.add(autoEvent.code());
            }
        }
        return result;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
    public List<String> findTrackedEventUsages(String eventCodeRaw) {
        String normalizedCode = stripModulePrefix(normalizeCode(eventCodeRaw));
        if (normalizedCode == null || normalizedCode.isBlank()) {
            return List.of();
        }
        List<String> usages = new ArrayList<>();
        for (BeanMethod beanMethod : collectApplicationMethods().values()) {
            Method method = beanMethod.method();
            TrackAnalyticsEvent track = AnnotationUtils.findAnnotation(method, TrackAnalyticsEvent.class);
            if (track != null) {
                String manualCode = stripModulePrefix(normalizeCode(track.code()));
                if (normalizedCode.equals(manualCode)) {
                    usages.add(formatUsage(beanMethod, method, "manual"));
                }
                continue;
            }
            AutoEvent autoEvent = buildAutoEvent(method);
            if (autoEvent != null && normalizedCode.equals(autoEvent.code())) {
                usages.add(formatUsage(beanMethod, method, "auto"));
            }
        }
        return usages;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
    public List<String> findTrackedAttributeUsages(String attributeCodeRaw) {
        String normalizedCode = normalizeCode(attributeCodeRaw);
        if (normalizedCode == null || normalizedCode.isBlank()) {
            return List.of();
        }
        List<String> usages = new ArrayList<>();
        for (BeanMethod beanMethod : collectApplicationMethods().values()) {
            Method method = beanMethod.method();
            TrackAnalyticsEvent track = AnnotationUtils.findAnnotation(method, TrackAnalyticsEvent.class);
            if (track == null) {
                continue;
            }

            boolean used = false;
            for (TrackAnalyticsAttribute attribute : track.attributes()) {
                String code = normalizeCode(attribute.code());
                if (normalizedCode.equals(code)) {
                    used = true;
                    break;
                }
            }
            if (!used && "ENTITY_TYPE".equals(normalizedCode)
                && track.entityType() != null && !track.entityType().isBlank()) {
                used = true;
            }
            if (!used && "ENTITY_ID".equals(normalizedCode)
                && track.entityId() != null && !track.entityId().isBlank()) {
                used = true;
            }
            if (used) {
                usages.add(formatUsage(beanMethod, method, "manual-attribute"));
            }
        }
        return usages;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
    public List<String> findTrackedMetricUsages(String metricCodeRaw) {
        String normalizedCode = normalizeCode(metricCodeRaw);
        if (normalizedCode == null || normalizedCode.isBlank()) {
            return List.of();
        }
        List<String> usages = new ArrayList<>();
        for (BeanMethod beanMethod : collectApplicationMethods().values()) {
            Method method = beanMethod.method();
            TrackAnalyticsEvent track = AnnotationUtils.findAnnotation(method, TrackAnalyticsEvent.class);
            boolean used = false;
            if (track != null) {
                used = containsMetricCode(track.metrics(), normalizedCode);
            }

            TrackAnalyticsStageMetric stageMetric = AnnotationUtils.findAnnotation(method, TrackAnalyticsStageMetric.class);
            if (!used && stageMetric != null) {
                String oldStyleCode = normalizeCode(stageMetric.code());
                used = normalizedCode.equals(oldStyleCode) || containsMetricCode(stageMetric.metrics(), normalizedCode);
            }

            if (used) {
                usages.add(formatUsage(beanMethod, method, "metric"));
            }
        }
        return usages;
    }

    private void migrateLegacyActionCodes() {
        mergeEventTypeCode("ADMINDASHBOARD_VIEW", "DASHBOARD_VIEW");
        mergeEventTypeCode("ADMIN_CREDENTIALS_UPDATE", "CREDENTIALS_UPDATE");
        mergeEventTypeCode("PRODUCTS_ACTION", "PRODUCT_DUPLICATE");
        mergeEventTypeCode("PRODUCTS_VIEW", "PRODUCT_LIST_VIEW");
        mergeEventTypeCode("PRODUCTS_SAVE", "PRODUCT_CREATE");
        mergeEventTypeCode("PRODUCTS_CREATE", "PRODUCT_CREATE");
        mergeEventTypeCode("PRODUCTS_UPDATE", "PRODUCT_UPDATE");
        mergeEventTypeCode("PRODUCTS_DELETE", "PRODUCT_DELETE");
        mergeEventTypeCode("PRODUCT_CHARACTERISTICS_SAVE", "PRODUCT_CHARACTERISTIC_CREATE");
        mergeEventTypeCode("PRODUCT_CHARACTERISTICS_UPDATE", "PRODUCT_CHARACTERISTIC_UPDATE");
        mergeEventTypeCode("PRODUCT_CHARACTERISTICS_DELETE", "PRODUCT_CHARACTERISTIC_DELETE");
        mergeEventTypeCode("PRODUCT_FILTER_OPTIONS_SAVE", "PRODUCT_FILTER_OPTION_CREATE");
        mergeEventTypeCode("PRODUCT_FILTER_OPTIONS_UPDATE", "PRODUCT_FILTER_OPTION_UPDATE");
        mergeEventTypeCode("PRODUCT_FILTER_OPTIONS_DELETE", "PRODUCT_FILTER_OPTION_DELETE");
        mergeEventTypeCode("PRODUCT_REVIEW_REPLY", "PRODUCT_REVIEW_REPLY_CREATE");
        mergeEventTypeCode("CATEGORIES_VIEW", "CATEGORY_LIST_VIEW");
        mergeEventTypeCode("USERS_UPDATE", "USER_UPDATE");
        mergeEventTypeCode("USERS_VIEW", "USER_LIST_VIEW");
        mergeEventTypeCode("ORDERS_DELETE", "ORDER_DELETE");
        mergeEventTypeCode("ORDERS_UPDATE", "ORDER_UPDATE");
        mergeEventTypeCode("ORDERS_VIEW", "ORDER_LIST_VIEW");
        mergeEventTypeCode("FILTERS_DELETE", "FILTER_DELETE");
        mergeEventTypeCode("FILTERS_VIEW", "FILTER_LIST_VIEW");
        mergeEventTypeCode("FILTERS_SAVE", "FILTER_UPDATE");
        mergeEventTypeCode("FILTER_SAVE", "FILTER_UPDATE");
        mergeEventTypeCode("FILES_VIEW", "FILE_LIST_VIEW");
        mergeEventTypeCode("REVIEWS_VIEW", "REVIEW_LIST_VIEW");
        mergeEventTypeCode("REVIEWS_MODERATE", "REVIEW_MODERATE");
        mergeEventTypeCode("REVIEWS_DELETE", "REVIEW_DELETE");
        mergeEventTypeCode("SUPPORT_DETAILS_VIEW", "SUPPORT_DETAIL_VIEW");
        mergeEventTypeCode("CART_ITEM_UPDATE", "CART_UPDATE");
        mergeEventTypeCode("VIEW_CATALOG", "CATALOG_VIEW");
        mergeEventTypeCode("VIEW_CATEGORY", "CATEGORY_VIEW");
        mergeEventTypeCode("VIEW_PRODUCT", "PRODUCT_VIEW");
        mergeEventTypeCode("REMOVE_FROM_WISHLIST", "WISHLIST_REMOVE");
        mergeEventTypeCode("ACCOUNT_CREATE", "ACCOUNT_SUPPORT_CREATE");
        mergeEventTypeCode("CATEGORIES_CREATE", "CATEGORY_CREATE");
        mergeEventTypeCode("CATEGORIES_UPDATE", "CATEGORY_UPDATE");
        mergeEventTypeCode("CATEGORIES_DELETE", "CATEGORY_DELETE");
    }

    private void applyFriendlyOverrides() {
        for (Map.Entry<String, GeneratedPresentation> entry : FRIENDLY_OVERRIDES.entrySet()) {
            String code = entry.getKey();
            GeneratedPresentation preset = entry.getValue();
            EventType type = eventTypeRepository.findById(code).orElse(null);
            if (type == null) {
                continue;
            }
            boolean changed = false;
            if (!preset.name().equals(type.getName())) {
                type.setName(preset.name());
                changed = true;
            }
            if (!preset.description().equals(type.getDescription())) {
                type.setDescription(preset.description());
                changed = true;
            }
            if (changed) {
                eventTypeRepository.save(type);
            }
        }
    }

    private void applySystemEventDescriptionsIfBlank() {
        EventType httpRequestError = eventTypeRepository.findById(HTTP_REQUEST_ERROR_CODE).orElse(null);
        if (httpRequestError == null || !shouldReplaceDescription(httpRequestError.getDescription())) {
            return;
        }
        httpRequestError.setDescription(HTTP_REQUEST_ERROR_DESCRIPTION);
        eventTypeRepository.save(httpRequestError);
    }

    private void migratePrefixedCodes() {
        for (EventType source : eventTypeRepository.findAll()) {
            String code = normalizeCode(source.getCode());
            if (code == null) {
                continue;
            }
            String target = stripModulePrefix(code);
            if (target.equals(code)) {
                continue;
            }
            mergeEventTypeCode(code, target);
        }
    }

    private void mergeEventTypeCode(String sourceCode, String targetCode) {
        EventType source = eventTypeRepository.findById(sourceCode).orElse(null);
        if (source == null) {
            return;
        }
        EventType target = eventTypeRepository.findById(targetCode).orElse(null);
        if (target == null) {
            target = new EventType();
            target.setCode(targetCode);
            target.setName(source.getName());
            target.setDescription(source.getDescription());
            target.setModuleCode(EventType.DEFAULT_MODULE_CODE);
            target.setIsSystem(Boolean.TRUE.equals(source.getIsSystem())
                || systemEventClassifier.isSystemEventType(source)
                || systemEventClassifier.isSystemEvent(targetCode, source.getName(), null, null));
            target.setIsActive(Boolean.TRUE.equals(source.getIsActive()));
            eventTypeRepository.save(target);
        } else if (Boolean.TRUE.equals(source.getIsSystem()) && !Boolean.TRUE.equals(target.getIsSystem())) {
            target.setIsSystem(true);
            eventTypeRepository.save(target);
        }

        jdbcTemplate.update("update analytics.event set event_type_code = ? where event_type_code = ?", targetCode, sourceCode);
        jdbcTemplate.update("update analytics.aggregated_metric set event_type_code = ? where event_type_code = ?", targetCode, sourceCode);
        jdbcTemplate.update(
            """
                update analytics.code_alias
                   set source_code = ?
                 where alias_type = 'EVENT'
                   and source_code = ?
                   and not exists (
                        select 1
                          from analytics.code_alias c2
                         where c2.alias_type = 'EVENT'
                           and c2.source_code = ?
                   )
                """,
            targetCode,
            sourceCode,
            targetCode
        );
        jdbcTemplate.update("update analytics.code_alias set target_code = ? where alias_type = 'EVENT' and target_code = ?", targetCode, sourceCode);
        eventTypeRepository.deleteById(sourceCode);
    }

    private void syncControllerEventTypes() {
        Map<String, GeneratedPresentation> autoPresentations = new LinkedHashMap<>();
        for (BeanMethod beanMethod : collectControllerMethods().values()) {
            Method method = beanMethod.method();
            TrackAnalyticsEvent track = AnnotationUtils.findAnnotation(method, TrackAnalyticsEvent.class);
            if (track != null) {
                String manualCode = stripModulePrefix(normalizeCode(track.code()));
                if (manualCode != null && !manualCode.isBlank()) {
                    ensureEventTypeExists(manualCode, null);
                }
                continue;
            }

            AutoEvent autoEvent = buildAutoEvent(method);
            if (autoEvent == null || autoEvent.code() == null || autoEvent.code().isBlank()) {
                continue;
            }
            autoPresentations.putIfAbsent(autoEvent.code(), autoEvent.presentation());
        }

        for (Map.Entry<String, GeneratedPresentation> entry : autoPresentations.entrySet()) {
            ensureEventTypeExists(entry.getKey(), entry.getValue());
        }
    }

    private void ensureEventTypeExists(String code, GeneratedPresentation autoPresentation) {
        EventType existing = eventTypeRepository.findById(code).orElse(null);
        if (existing == null) {
            throw new IllegalStateException("Unknown analytics event type code in application code: " + code);
        }

        boolean changed = false;
        if (existing.getModuleCode() == null || existing.getModuleCode().isBlank()) {
            existing.setModuleCode(EventType.DEFAULT_MODULE_CODE);
            changed = true;
        }
        if (!Boolean.TRUE.equals(existing.getIsSystem()) && systemEventClassifier.isSystemEventType(existing)) {
            existing.setIsSystem(true);
            changed = true;
        }

        if (isAutoCrudCode(code)) {
            GeneratedPresentation generated = autoPresentation != null ? autoPresentation : generatedPresentationForCode(code);
            if (!generated.name().equals(existing.getName())) {
                existing.setName(generated.name());
                changed = true;
            }
            if (!generated.description().equals(existing.getDescription())) {
                existing.setDescription(generated.description());
                changed = true;
            }
        } else {
            if (shouldReplaceName(existing.getName(), code)) {
                existing.setName(generatedPresentationForCode(code).name());
                changed = true;
            }
            if (shouldReplaceDescription(existing.getDescription())) {
                existing.setDescription(generatedPresentationForCode(code).description());
                changed = true;
            }
        }

        if (changed) {
            eventTypeRepository.save(existing);
        }
    }

    private void ensureEventTypeExistsMissingOnly(String code, GeneratedPresentation autoPresentation) {
        EventType existing = eventTypeRepository.findById(code).orElse(null);
        if (existing != null) {
            return;
        }
        throw new IllegalStateException("Unknown analytics event type code in application code: " + code);
    }

    private void normalizeAutoGeneratedEventTypes() {
        for (EventType eventType : eventTypeRepository.findAll()) {
            String rawCode = eventType.getCode();
            if (rawCode == null || rawCode.isBlank()) {
                continue;
            }
            String normalizedCode = stripModulePrefix(normalizeCode(rawCode));
            if (!normalizedCode.equals(rawCode)) {
                mergeEventTypeCode(rawCode, normalizedCode);
                continue;
            }

            boolean changed = false;
            if (eventType.getModuleCode() == null || eventType.getModuleCode().isBlank()) {
                eventType.setModuleCode(EventType.DEFAULT_MODULE_CODE);
                changed = true;
            }
            if (!Boolean.TRUE.equals(eventType.getIsSystem()) && systemEventClassifier.isSystemEventType(eventType)) {
                eventType.setIsSystem(true);
                changed = true;
            }

            if (isAutoCrudCode(normalizedCode)) {
                GeneratedPresentation generated = generatedPresentationForCode(normalizedCode);
                if (!generated.name().equals(eventType.getName())) {
                    eventType.setName(generated.name());
                    changed = true;
                }
                if (!generated.description().equals(eventType.getDescription())) {
                    eventType.setDescription(generated.description());
                    changed = true;
                }
            } else {
                if (shouldReplaceName(eventType.getName(), normalizedCode)) {
                    eventType.setName(generatedPresentationForCode(normalizedCode).name());
                    changed = true;
                }
                if (shouldReplaceDescription(eventType.getDescription())) {
                    eventType.setDescription(generatedPresentationForCode(normalizedCode).description());
                    changed = true;
                }
            }

            if (changed) {
                eventTypeRepository.save(eventType);
            }
        }
    }

    private void classifySystemEventTypes() {
        for (EventType eventType : eventTypeRepository.findAll()) {
            if (eventType == null || Boolean.TRUE.equals(eventType.getIsSystem())) {
                continue;
            }
            if (systemEventClassifier.isSystemEventType(eventType)) {
                eventType.setIsSystem(true);
                eventTypeRepository.save(eventType);
            }
        }
        classifyHistoricalSystemEventTypesBySql();
    }

    private void classifyHistoricalSystemEventTypesBySql() {
        if (!tableExists("analytics.event_type") || !tableExists("analytics.event")) {
            return;
        }
        jdbcTemplate.execute(
            """
                update analytics.event_type t
                   set is_system = true
                 where coalesce(t.is_system, false) = false
                   and exists (
                       select 1
                         from analytics.event e
                        where e.event_type_code = t.code
                          and (
                               lower(coalesce(e.request_path, '')) in ('/favicon.ico', '/robots.txt', '/error')
                               or lower(coalesce(e.request_path, '')) like '/static/%'
                               or lower(coalesce(e.request_path, '')) like '/css/%'
                               or lower(coalesce(e.request_path, '')) like '/js/%'
                               or lower(coalesce(e.request_path, '')) like '/images/%'
                               or lower(coalesce(e.request_path, '')) like '/img/%'
                               or lower(coalesce(e.request_path, '')) like '/webjars/%'
                               or lower(coalesce(e.request_path, '')) like '/actuator/%'
                               or (
                                   e.status_code = 404
                                   and lower(coalesce(e.request_path, '')) ~ '\\.(css|js|map|ico|png|jpg|jpeg|svg|gif|webp|woff|woff2|ttf)(\\?.*)?$'
                               )
                          )
                   )
                """
        );
    }

    private boolean tableExists(String regclass) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select to_regclass(?) is not null",
            Boolean.class,
            regclass
        );
        return Boolean.TRUE.equals(exists);
    }

    private Map<String, BeanMethod> collectControllerMethods() {
        Map<String, BeanMethod> result = new LinkedHashMap<>();
        Map<String, Object> controllers = new HashMap<>();
        controllers.putAll(applicationContext.getBeansWithAnnotation(Controller.class));
        controllers.putAll(applicationContext.getBeansWithAnnotation(RestController.class));
        for (Object bean : controllers.values()) {
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (targetClass == null) {
                continue;
            }
            String packageName = targetClass.getPackageName();
            if (!packageName.contains(".admin.controller") && !packageName.contains(".shop.controller")) {
                continue;
            }
            for (Method method : targetClass.getMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (!isRequestMapped(method)) {
                    continue;
                }
                result.put(targetClass.getName() + "#" + method.getName(), new BeanMethod(targetClass, method));
            }
        }
        return result;
    }

    private Map<String, BeanMethod> collectApplicationMethods() {
        Map<String, BeanMethod> result = new LinkedHashMap<>(collectControllerMethods());
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType;
            try {
                beanType = applicationContext.getType(beanName);
            } catch (RuntimeException ignored) {
                continue;
            }
            collectTypeMethods(result, beanType);
        }
        return result;
    }

    private void collectTypeMethods(Map<String, BeanMethod> result, Class<?> type) {
        if (type == null || !isApplicationClass(type)) {
            return;
        }
        Class<?> current = type;
        while (current != null && current != Object.class && isApplicationClass(current)) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                if (AnnotationUtils.findAnnotation(method, TrackAnalyticsEvent.class) == null
                    && AnnotationUtils.findAnnotation(method, TrackAnalyticsStageMetric.class) == null) {
                    continue;
                }
                result.put(methodKey(current, method), new BeanMethod(current, method));
            }
            current = current.getSuperclass();
        }
        for (Class<?> iface : type.getInterfaces()) {
            collectTypeMethods(result, iface);
        }
    }

    private boolean isApplicationClass(Class<?> type) {
        Package typePackage = type.getPackage();

        String packageName = typePackage == null ? "" : typePackage.getName();

        return packageName.startsWith("com.example.gqw");
    }

    private String methodKey(Class<?> type, Method method) {
        return type.getName() + "#" + method.getName() + Arrays.toString(method.getParameterTypes());
    }

    private boolean containsMetricCode(TrackAnalyticsMetric[] metrics, String normalizedCode) {
        if (metrics == null || metrics.length == 0 || normalizedCode == null || normalizedCode.isBlank()) {
            return false;
        }
        for (TrackAnalyticsMetric metric : metrics) {
            String code = normalizeCode(metric.code());
            if (normalizedCode.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private AutoEvent buildAutoEvent(Method method) {
        String httpMethod = resolveHttpMethod(method);
        String path = resolvePath(method);
        if (httpMethod == null || path == null || path.isBlank()) {
            return null;
        }

        String actionCode = resolveAction(httpMethod, path, method.getName());
        String specialEventCode = resolveSpecialEventCode(path, httpMethod, method.getName());
        if (specialEventCode != null && !specialEventCode.isBlank()) {
            return new AutoEvent(specialEventCode, generatedPresentationForCode(specialEventCode));
        }
        String entityCode = resolveEntity(path, method);
        if (shouldUseSingularEntity(actionCode, path)) {
            entityCode = singularizeEntityCode(entityCode);
        }
        String code = (entityCode + "_" + actionCode).toUpperCase(Locale.ROOT);
        return new AutoEvent(code, generatedPresentationForCode(code));
    }

    private String resolveSpecialEventCode(String path, String httpMethod, String methodName) {
        if (path == null || httpMethod == null) {
            return null;
        }
        String lowerPath = path.trim().toLowerCase(Locale.ROOT);
        String lowerMethod = methodName == null ? "" : methodName.toLowerCase(Locale.ROOT);

        if ("/account".equals(lowerPath) && "GET".equalsIgnoreCase(httpMethod)) {
            return "ACCOUNT_VIEW";
        }
        if (("/account/profile".equals(lowerPath) || lowerMethod.contains("profile"))
            && "POST".equalsIgnoreCase(httpMethod)) {
            return "ACCOUNT_PROFILE_UPDATE";
        }
        if (("/account/address".equals(lowerPath) || lowerMethod.contains("address"))
            && "POST".equalsIgnoreCase(httpMethod)) {
            return "ACCOUNT_ADDRESS_UPDATE";
        }
        if (("/account/delete".equals(lowerPath) || lowerMethod.contains("deleteaccount"))
            && "POST".equalsIgnoreCase(httpMethod)) {
            return "ACCOUNT_DELETE";
        }
        if ((lowerPath.contains("/account/orders/") && lowerPath.endsWith("/cancel"))
            || lowerMethod.contains("cancelorder")) {
            return "ACCOUNT_ORDER_CANCEL";
        }
        if ((lowerPath.contains("/account/orders/") && lowerPath.endsWith("/update"))
            || lowerMethod.contains("updateorder")) {
            return "ACCOUNT_ORDER_UPDATE";
        }
        if (lowerPath.startsWith("/account/support/") || lowerMethod.contains("supportticket")) {
            return "ACCOUNT_SUPPORT_CREATE";
        }
        return null;
    }

    private boolean isRequestMapped(Method method) {
        return AnnotationUtils.findAnnotation(method, GetMapping.class) != null
            || AnnotationUtils.findAnnotation(method, PostMapping.class) != null
            || AnnotationUtils.findAnnotation(method, PutMapping.class) != null
            || AnnotationUtils.findAnnotation(method, PatchMapping.class) != null
            || AnnotationUtils.findAnnotation(method, DeleteMapping.class) != null
            || AnnotationUtils.findAnnotation(method, RequestMapping.class) != null;
    }

    private String resolveHttpMethod(Method method) {
        if (AnnotationUtils.findAnnotation(method, GetMapping.class) != null) {
            return "GET";
        }
        if (AnnotationUtils.findAnnotation(method, PostMapping.class) != null) {
            return "POST";
        }
        if (AnnotationUtils.findAnnotation(method, PutMapping.class) != null) {
            return "PUT";
        }
        if (AnnotationUtils.findAnnotation(method, PatchMapping.class) != null) {
            return "PATCH";
        }
        if (AnnotationUtils.findAnnotation(method, DeleteMapping.class) != null) {
            return "DELETE";
        }
        RequestMapping mapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
        if (mapping != null && mapping.method().length > 0) {
            return mapping.method()[0].name();
        }
        return null;
    }

    private String resolvePath(Method method) {
        String[] paths = extractPaths(method);
        if (paths.length > 0 && paths[0] != null && !paths[0].isBlank()) {
            return paths[0];
        }
        return "";
    }

    private String[] extractPaths(Method method) {
        GetMapping get = AnnotationUtils.findAnnotation(method, GetMapping.class);
        if (get != null && get.value().length > 0) {
            return get.value();
        }
        PostMapping post = AnnotationUtils.findAnnotation(method, PostMapping.class);
        if (post != null && post.value().length > 0) {
            return post.value();
        }
        PutMapping put = AnnotationUtils.findAnnotation(method, PutMapping.class);
        if (put != null && put.value().length > 0) {
            return put.value();
        }
        PatchMapping patch = AnnotationUtils.findAnnotation(method, PatchMapping.class);
        if (patch != null && patch.value().length > 0) {
            return patch.value();
        }
        DeleteMapping delete = AnnotationUtils.findAnnotation(method, DeleteMapping.class);
        if (delete != null && delete.value().length > 0) {
            return delete.value();
        }
        RequestMapping mapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
        if (mapping != null && mapping.value().length > 0) {
            return mapping.value();
        }
        return new String[0];
    }

    private String resolveEntity(String path, Method method) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.isEmpty()) {
            return method.getDeclaringClass().getSimpleName().replace("Controller", "").toUpperCase(Locale.ROOT);
        }
        String[] parts = normalized.replaceAll("^/+", "").split("/");
        for (String part : parts) {
            String lower = part.toLowerCase(Locale.ROOT);
            if (lower.isBlank() || lower.equals("api") || lower.equals("admin")) {
                continue;
            }
            if (lower.startsWith("{") && lower.endsWith("}")) {
                continue;
            }
            return normalizeToken(lower);
        }
        return method.getDeclaringClass().getSimpleName().replace("Controller", "").toUpperCase(Locale.ROOT);
    }

    private String resolveAction(String httpMethod, String path, String methodName) {
        String lowerPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        String lowerMethod = methodName == null ? "" : methodName.toLowerCase(Locale.ROOT);

        if ("GET".equals(httpMethod)) {
            if (lowerPath.endsWith("/new") || lowerPath.contains("/new/")) {
                return "CREATE_VIEW";
            }
            if (lowerPath.endsWith("/edit") || lowerPath.contains("/edit/")) {
                return "EDIT_VIEW";
            }
            if (lowerPath.contains("/{") || lowerPath.matches(".*/\\d+($|/.*)")) {
                return "DETAIL_VIEW";
            }
            return "VIEW";
        }
        if ("DELETE".equals(httpMethod)) {
            return "DELETE";
        }
        if ("PUT".equals(httpMethod) || "PATCH".equals(httpMethod)) {
            return "UPDATE";
        }
        if (lowerPath.contains("/duplicate") || lowerMethod.contains("duplicate")) {
            return "DUPLICATE";
        }
        if (lowerPath.contains("/delete") || lowerMethod.contains("delete") || lowerMethod.contains("remove")) {
            return "DELETE";
        }
        if (lowerPath.contains("/update") || lowerMethod.contains("update")
            || lowerPath.contains("/status") || lowerMethod.contains("status")
            || lowerPath.contains("/moderate") || lowerMethod.contains("moderate")
            || lowerPath.contains("/reply") || lowerMethod.contains("reply")
            || lowerPath.contains("/toggle") || lowerMethod.contains("toggle")
            || lowerPath.contains("/increment") || lowerPath.contains("/decrement")
            || lowerPath.contains("/cancel") || lowerMethod.contains("cancel")
            || lowerPath.contains("/processed")) {
            return "UPDATE";
        }
        if (lowerPath.contains("/create") || lowerPath.contains("/add")
            || lowerMethod.contains("create") || lowerMethod.contains("add")
            || lowerMethod.contains("save") || lowerPath.endsWith("/save")
            || lowerPath.contains("/upload") || lowerPath.contains("/duplicate")) {
            return "CREATE";
        }
        return "ACTION";
    }

    private String formatUsage(BeanMethod beanMethod, Method method, String mode) {
        String http = resolveHttpMethod(method);
        String path = resolvePath(method);
        String className = beanMethod.clazz().getSimpleName();
        String methodName = method.getName();
        String route = ((http == null || http.isBlank()) ? "?" : http) + " " + (path == null ? "" : path);
        return className + "#" + methodName + " [" + route.trim() + "] (" + mode + ")";
    }

    private boolean shouldUseSingularEntity(String actionCode, String path) {
        if (actionCode == null) {
            return false;
        }
        if ("VIEW".equals(actionCode)) {
            return false;
        }
        if (path != null && (path.contains("/{") || path.matches(".*/\\d+($|/.*)"))) {
            return true;
        }
        return "DETAIL_VIEW".equals(actionCode)
            || "EDIT_VIEW".equals(actionCode)
            || "CREATE_VIEW".equals(actionCode)
            || "CREATE".equals(actionCode)
            || "UPDATE".equals(actionCode)
            || "DELETE".equals(actionCode);
    }

    private String normalizeToken(String token) {
        String normalized = token.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            return "ENTITY";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String singularizeEntityCode(String entityCode) {
        if (entityCode == null || entityCode.isBlank()) {
            return entityCode;
        }
        String[] parts = entityCode.split("_");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = singularizeToken(parts[i]);
        }
        return String.join("_", parts);
    }

    private String singularizeToken(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        if (lower.endsWith("ies") && lower.length() > 3) {
            return (lower.substring(0, lower.length() - 3) + "y").toUpperCase(Locale.ROOT);
        }
        if (lower.endsWith("s") && lower.length() > 1) {
            return lower.substring(0, lower.length() - 1).toUpperCase(Locale.ROOT);
        }
        return token.toUpperCase(Locale.ROOT);
    }

    private GeneratedPresentation generatedPresentationForCode(String code) {
        String normalized = normalizeCode(code);
        if (normalized == null || normalized.isBlank()) {
            return new GeneratedPresentation("Событие", "Событие приложения.");
        }
        ActionAndEntity actionAndEntity = splitAutoCode(normalized);
        if (actionAndEntity == null) {
            String title = humanizeWords(normalized);
            return new GeneratedPresentation(title, "Событие приложения: " + title.toLowerCase(Locale.ROOT) + ".");
        }

        String action = actionAndEntity.action();
        String entityCode = actionAndEntity.entityCode();
        String entityList = humanizeEntity(entityCode, false);
        String entitySingle = humanizeEntity(entityCode, true);

        return switch (action) {
            case "VIEW" -> new GeneratedPresentation(
                "Открытие страницы: " + entityList,
                "Открытие страницы «" + entityList + "»."
            );
            case "DETAIL_VIEW" -> new GeneratedPresentation(
                "Открытие карточки: " + entitySingle,
                "Открытие карточки «" + entitySingle + "»."
            );
            case "CREATE_VIEW" -> new GeneratedPresentation(
                "Открытие формы создания: " + entitySingle,
                "Открытие формы создания «" + entitySingle + "»."
            );
            case "EDIT_VIEW" -> new GeneratedPresentation(
                "Открытие формы редактирования: " + entitySingle,
                "Открытие формы редактирования «" + entitySingle + "»."
            );
            case "CREATE" -> new GeneratedPresentation(
                "Создание: " + entitySingle,
                "Создание сущности «" + entitySingle + "»."
            );
            case "UPDATE" -> new GeneratedPresentation(
                "Изменение: " + entitySingle,
                "Изменение сущности «" + entitySingle + "»."
            );
            case "DELETE" -> new GeneratedPresentation(
                "Удаление: " + entitySingle,
                "Удаление сущности «" + entitySingle + "»."
            );
            default -> new GeneratedPresentation(
                "Действие: " + entityList,
                "Выполнение операции для «" + entityList + "»."
            );
        };
    }

    private ActionAndEntity splitAutoCode(String code) {
        for (String suffix : AUTO_ACTION_SUFFIXES) {
            String marker = "_" + suffix;
            if (code.endsWith(marker) && code.length() > marker.length()) {
                return new ActionAndEntity(code.substring(0, code.length() - marker.length()), suffix);
            }
        }
        return null;
    }

    private String humanizeEntity(String entityCode, boolean singular) {
        if (entityCode == null || entityCode.isBlank()) {
            return "сущность";
        }

        String normalized = entityCode.toUpperCase(Locale.ROOT);
        Map<String, String> exact = singular ? EXACT_ENTITY_SINGULAR : EXACT_ENTITY_PLURAL;
        if (exact.containsKey(normalized)) {
            return exact.get(normalized);
        }

        String[] parts = normalized.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(translateToken(part, singular));
        }
        String fallback = sb.toString().trim();
        if (fallback.isBlank()) {
            return humanizeWords(entityCode);
        }
        return fallback;
    }

    private String translateToken(String token, boolean singular) {
        String t = token.toUpperCase(Locale.ROOT);
        return switch (t) {
            case "PRODUCT", "PRODUCTS" -> singular ? "товар" : "товары";
            case "CATEGORY", "CATEGORIES" -> singular ? "категория" : "категории";
            case "FILTER", "FILTERS" -> singular ? "фильтр" : "фильтры";
            case "OPTION", "OPTIONS" -> singular ? "опция" : "опции";
            case "CHARACTERISTIC", "CHARACTERISTICS" -> singular ? "характеристика" : "характеристики";
            case "ORDER", "ORDERS" -> singular ? "заказ" : "заказы";
            case "USER", "USERS" -> singular ? "пользователь" : "пользователи";
            case "REVIEW", "REVIEWS" -> singular ? "отзыв" : "отзывы";
            case "SUPPORT" -> singular ? "заявка в поддержку" : "заявки в поддержку";
            case "ACCOUNT_SUPPORT" -> singular ? "обращение в поддержку из личного кабинета" : "обращения в поддержку из личного кабинета";
            case "FILE", "FILES" -> singular ? "файл" : "файлы";
            case "IMAGE", "IMAGES" -> singular ? "изображение" : "изображения";
            case "CART" -> "корзина";
            case "CATALOG" -> "каталог";
            case "WISHLIST" -> "избранное";
            case "CHECKOUT" -> "оформление заказа";
            case "ACCOUNT" -> "личный кабинет";
            case "LOGIN" -> "вход";
            case "REGISTER" -> "регистрация";
            case "CONTACTS" -> "контакты";
            case "DELIVERY" -> "доставка и оплата";
            case "ABOUT" -> "о компании";
            case "DASHBOARD", "ADMINDASHBOARD" -> "дашборд админ-панели";
            case "SHOPMODELATTRIBUTESADVICE" -> "служебные данные шаблонов магазина";
            case "MODEL" -> "модель";
            case "ATTRIBUTES" -> singular ? "атрибут" : "атрибуты";
            case "ADVICE" -> "подготовка данных";
            case "STATUS" -> "статус";
            case "REPLY" -> "ответ";
            default -> humanizeWords(token);
        };
    }

    private static final Map<String, String> EXACT_ENTITY_SINGULAR = Map.ofEntries(
        Map.entry("PRODUCT_FILTER_OPTION", "опция фильтра товара"),
        Map.entry("PRODUCT_CHARACTERISTIC", "характеристика товара"),
        Map.entry("PRODUCT_IMAGE", "изображение товара"),
        Map.entry("PRODUCT_REVIEW", "отзыв о товаре"),
        Map.entry("PRODUCT_REVIEW_REPLY", "ответ на отзыв о товаре")
    );

    private static final Map<String, String> EXACT_ENTITY_PLURAL = Map.ofEntries(
        Map.entry("PRODUCT_FILTER_OPTION", "опции фильтра товара"),
        Map.entry("PRODUCT_CHARACTERISTIC", "характеристики товара"),
        Map.entry("PRODUCT_IMAGE", "изображения товара"),
        Map.entry("PRODUCT_REVIEW", "отзывы о товаре"),
        Map.entry("PRODUCT_REVIEW_REPLY", "ответы на отзывы о товаре")
    );

    private String humanizeWords(String code) {
        String normalized = code.trim().replaceAll("_+", "_");
        String[] parts = normalized.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            String lower = part.toLowerCase(Locale.ROOT);
            result.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                result.append(lower.substring(1));
            }
        }
        return result.isEmpty() ? normalized : result.toString();
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim()
            .replaceAll("[^A-Za-z0-9_]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "")
            .toUpperCase(Locale.ROOT);
    }

    private String stripModulePrefix(String code) {
        if (code == null) {
            return null;
        }
        if (code.startsWith("ADMIN_")) {
            return code.substring("ADMIN_".length());
        }
        if (code.startsWith("SHOP_")) {
            return code.substring("SHOP_".length());
        }
        return code;
    }

    private boolean isAutoCrudCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return splitAutoCode(code) != null;
    }

    private boolean shouldReplaceName(String currentName, String code) {
        if (currentName == null || currentName.isBlank()) {
            return true;
        }
        String trimmed = currentName.trim();
        if (trimmed.startsWith("Admin ") || trimmed.startsWith("Shop ")) {
            return true;
        }
        if (trimmed.startsWith("Auto ") || trimmed.startsWith("Авто ")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase(humanizeWords(code))) {
            return true;
        }
        if (trimmed.startsWith("Просмотр: ") || trimmed.startsWith("Создание: ")
            || trimmed.startsWith("Изменение: ") || trimmed.startsWith("Удаление: ")
            || trimmed.startsWith("Действие: ")) {
            return true;
        }
        return false;
    }

    private boolean shouldReplaceDescription(String currentDescription) {
        if (currentDescription == null || currentDescription.isBlank()) {
            return true;
        }
        String lowered = currentDescription.toLowerCase(Locale.ROOT);
        if (lowered.startsWith(AUTO_PREFIX_RU.toLowerCase(Locale.ROOT))
            || lowered.startsWith(AUTO_PREFIX_EN.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (lowered.contains("сущности \"") || lowered.contains("в приложении.")) {
            return true;
        }
        return false;
    }

    private record BeanMethod(Class<?> clazz, Method method) {
    }

    private record GeneratedPresentation(String name, String description) {
    }

    private record AutoEvent(String code, GeneratedPresentation presentation) {
    }

    private record ActionAndEntity(String entityCode, String action) {
    }
}

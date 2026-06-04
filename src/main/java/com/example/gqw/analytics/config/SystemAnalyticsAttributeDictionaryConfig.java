package com.example.gqw.analytics.config;

import com.example.gqw.analytics.entity.EventAttributeType;
import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(
    value = "app.analytics.system-attributes-seed-enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class SystemAnalyticsAttributeDictionaryConfig {

    @Bean
    @Order(400)
    CommandLineRunner seedSystemAnalyticsAttributes(EventAttributeTypeRepository repository) {
        return args -> {
            List<AttrSeed> system = List.of(
                new AttrSeed("ENTITY_TYPE", "Entity type", "Тип бизнес-сущности, к которой относится событие (например, PRODUCT, CATEGORY, ORDER). Нужен для группировки и фильтрации событий по доменной области.", false),
                new AttrSeed("ENTITY_ID", "Entity id", "Идентификатор конкретной бизнес-сущности в рамках ENTITY_TYPE. Нужен для точечного расследования одного объекта: карточки товара, категории, заказа.", false),
                new AttrSeed("HTTP_METHOD", "HTTP method", "HTTP-метод запроса (GET/POST/PUT/DELETE). Помогает понять, это чтение или изменение данных, и быстро локализовать проблемный сценарий.", true),
                new AttrSeed("HTTP_PATH", "HTTP path", "Путь запроса без домена. Нужен для анализа конкретного экрана/endpoint и сравнения поведения одного и того же пути между периодами.", true),
                new AttrSeed("HTTP_STATUS", "HTTP status", "HTTP-статус ответа (200, 404, 500 и т.д.). Используется для отделения пользовательских ошибок от серверных и для поиска аномальных всплесков по кодам.", true),
                new AttrSeed("ERROR_CODE", "Error code", "Код ошибки приложения из бизнес-логики или инфраструктуры. Нужен для точного поиска повторяющегося сбоя и связывания с конкретной веткой кода.", true),
                new AttrSeed("ERROR_CLASS", "Error class", "Класс ошибки верхнего уровня (например, BUSINESS, SYSTEM, HTTP_REQUEST_ERROR). Помогает быстро понять природу проблемы и приоритет реагирования.", true),
                new AttrSeed("CLIENT_TYPE", "Client type", "Источник запроса по типу клиента (WEB, MOBILE, API). Нужен для сравнения качества работы между каналами и поиска платформенно-специфичных проблем.", true),
                new AttrSeed("USER_AGENT", "User agent", "Сигнатура браузера/клиента. Полезна для диагностики багов, зависящих от устройства, ОС или версии браузера.", true),
                new AttrSeed("REFERRER", "Referrer", "Источник перехода пользователя (страница, с которой пришёл запрос). Помогает понять пользовательский путь перед ошибкой или деградацией.", true),
                new AttrSeed("SESSION_ID_HASH", "Session hash", "Анонимизированный идентификатор сессии. Нужен для связывания цепочки действий одного визита без хранения персональных данных.", true),
                new AttrSeed("USER_ID_HASH", "User hash", "Анонимизированный идентификатор пользователя. Нужен для анализа повторяемости проблемы у одного пользователя без раскрытия личности.", true),
                new AttrSeed("REQUEST_ID", "Request id", "Внешний идентификатор запроса для сквозной трассировки между сервисами и логами. Ускоряет поиск первопричины по технической цепочке.", true)
            );
            for (AttrSeed seed : system) {
                repository.upsert(
                    seed.code(),
                    seed.name(),
                    seed.description(),
                    MetricValueKind.TEXT.name(),
                    null,
                    seed.isSystem(),
                    true
                );
            }
            markLegacyAttribute(repository, "PRODUCT_ID", "Product id (legacy)");
            markLegacyAttribute(repository, "CATEGORY_ID", "Category id (legacy)");
            markLegacyAttribute(repository, "ORDER_ID", "Order id (legacy)");
        };
    }

    private void markLegacyAttribute(EventAttributeTypeRepository repository, String code, String legacyName) {
        EventAttributeType type = repository.findById(code).orElse(null);
        if (type == null) {
            return;
        }
        type.setName(legacyName);
        type.setDescription("Legacy-атрибут: заменён на связку ENTITY_TYPE + ENTITY_ID");
        type.setIsSystem(false);
        type.setIsActive(false);
        repository.save(type);
    }

    private record AttrSeed(String code, String name, String description, boolean isSystem) {
    }
}

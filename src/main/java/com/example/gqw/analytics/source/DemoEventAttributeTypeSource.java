package com.example.gqw.analytics.source;

import com.example.gqw.analytics.entity.EventAttributeType;
import com.example.gqw.analytics.entity.MetricValueKind;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DemoEventAttributeTypeSource implements EventAttributeTypeSource {

    @Override
    public List<EventAttributeType> eventAttributeTypes() {
        return List.of(
            text("ENTITY_TYPE", "Entity type", "Тип сущности, к которой относится событие"),
            text("ENTITY_ID", "Entity id", "Идентификатор сущности в событии"),
            text("HTTP_METHOD", "HTTP method", "HTTP-метод запроса"),
            text("HTTP_PATH", "HTTP path", "Путь HTTP-запроса"),
            text("HTTP_STATUS", "HTTP status", "HTTP-статус ответа"),
            text("ERROR_CODE", "Error code", "Код ошибки приложения"),
            text("ERROR_CLASS", "Error class", "Класс ошибки (BUSINESS/SYSTEM/HTTP_REQUEST_ERROR)"),
            text("CLIENT_TYPE", "Client type", "Тип клиента (WEB/MOBILE/API)"),
            text("USER_AGENT", "User agent", "User-Agent клиента"),
            text("REFERRER", "Referrer", "Источник перехода (Referer)"),
            text("SESSION_ID_HASH", "Session hash", "Хэш идентификатора сессии"),
            text("USER_ID_HASH", "User hash", "Хэш идентификатора пользователя"),
            text("REQUEST_ID", "Request id", "Внешний идентификатор запроса"),
            text("PRODUCT_ID", "Product id", "Идентификатор товара"),
            text("CATEGORY_ID", "Category id", "Идентификатор категории"),
            text("ORDER_ID", "Order id", "Идентификатор заказа"),
            text("SORT_TYPE", "Sort type", "Тип сортировки"),
            text("CATEGORY_SLUG", "Category slug", "Slug категории"),
            text("CATEGORY_NAME", "Category name", "Название категории"),
            text("SEARCH_QUERY", "Search query", "Поисковая строка"),
            text("PAGE_INDEX", "Page index", "Номер страницы"),
            text("PAGE_SIZE", "Page size", "Размер страницы"),
            text("IN_STOCK_ONLY", "In stock only", "Флаг фильтра в наличии"),
            text("OPTION_IDS_COUNT", "Option count", "Количество выбранных опций фильтра"),
            text("MIN_PRICE", "Min price", "Минимальная цена фильтра"),
            text("MAX_PRICE", "Max price", "Максимальная цена фильтра"),
            text("QUANTITY", "Quantity", "Количество товара в действии"),
            text("DELIVERY_TYPE", "Delivery type", "Тип получения заказа"),
            text("SUPPORT_TOPIC", "Support topic", "Тема обращения в поддержку"),
            text("RATING", "Rating", "Оценка отзыва"),
            text("REVIEW_IMAGES_COUNT", "Review images", "Количество изображений в отзыве"),
            text("EMAIL_DOMAIN", "Email domain", "Домен email"),
            text("AUTH_RESULT", "Auth result", "Результат аутентификации"),
            text("FAILURE_REASON", "Failure reason", "Причина ошибки/отказа"),
            text("DEMO_FAULT", "Demo fault", "Флаг искусственной бизнес-ошибки")
        );
    }

    private static EventAttributeType text(String code, String name, String description) {
        EventAttributeType type = new EventAttributeType();
        type.setCode(code);
        type.setName(name);
        type.setDescription(description);
        type.setValueKind(MetricValueKind.TEXT);
        type.setUnitDefault(null);
        type.setIsActive(true);
        return type;
    }
}


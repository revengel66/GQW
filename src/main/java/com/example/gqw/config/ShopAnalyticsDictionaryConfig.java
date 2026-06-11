package com.example.gqw.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(value = "app.analytics.shop-dictionary-seed-enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class ShopAnalyticsDictionaryConfig {

    @Bean
    @Order(450)
    CommandLineRunner seedShopAnalyticsDictionary(@Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return args -> {
            seedEventTypes(jdbcTemplate);
            seedAttributeTypes(jdbcTemplate);
            seedStageTypes(jdbcTemplate);
        };
    }

    private void seedEventTypes(JdbcTemplate jdbcTemplate) {
        for (EventSeed seed : eventSeeds()) {
            jdbcTemplate.update(
                """
                    insert into analytics.event_type(code, name, description, module_code, is_system, is_active)
                    values (?, ?, ?, ?, false, true)
                    on conflict (code) do update
                    set name = excluded.name,
                        description = excluded.description,
                        module_code = excluded.module_code,
                        is_system = false,
                        is_active = true
                    """,
                seed.code(),
                seed.name(),
                seed.description(),
                seed.moduleCode()
            );
        }
    }

    private void seedAttributeTypes(JdbcTemplate jdbcTemplate) {
        for (AttributeSeed seed : attributeSeeds()) {
            jdbcTemplate.update(
                """
                    insert into analytics.event_attribute_type(code, name, description, value_kind, unit_default, is_system, is_active)
                    values (?, ?, ?, 'TEXT', null, false, true)
                    on conflict (code) do update
                    set name = excluded.name,
                        description = excluded.description,
                        value_kind = excluded.value_kind,
                        unit_default = excluded.unit_default,
                        is_system = false,
                        is_active = true
                    """,
                seed.code(),
                seed.name(),
                seed.description()
            );
        }
    }

    private void seedStageTypes(JdbcTemplate jdbcTemplate) {
        for (StageSeed seed : stageSeeds()) {
            jdbcTemplate.update(
                """
                    insert into analytics.stage_type(code, name, description, is_system, is_active)
                    values (?, ?, ?, false, true)
                    on conflict (code) do update
                    set name = excluded.name,
                        description = excluded.description,
                        is_system = false,
                        is_active = true
                    """,
                seed.code(),
                seed.name(),
                seed.description()
            );
        }
    }

    private static List<EventSeed> eventSeeds() {
        return List.of(
            shop("ABOUT_VIEW", "Страница о компании", "Пользователь открыл страницу «О компании»."),
            shop("ACCOUNT_ADDRESS_UPDATE", "Обновление адреса в ЛК", "Пользователь обновил адрес в личном кабинете."),
            shop("ACCOUNT_DELETE", "Удаление аккаунта", "Пользователь удалил свой аккаунт."),
            shop("ACCOUNT_ORDER_CANCEL", "Отмена заказа в ЛК", "Пользователь отменил заказ из личного кабинета."),
            shop("ACCOUNT_ORDER_UPDATE", "Обновление заказа в ЛК", "Пользователь изменил параметры заказа из личного кабинета."),
            shop("ACCOUNT_PROFILE_UPDATE", "Обновление профиля в ЛК", "Пользователь обновил профиль в личном кабинете."),
            shop("ACCOUNT_SUPPORT_CREATE", "Обращение в поддержку из ЛК", "Пользователь создал обращение в поддержку из личного кабинета."),
            shop("ACCOUNT_VIEW", "Страница личного кабинета", "Пользователь открыл страницу личного кабинета."),
            shop("ADD_TO_CART", "Добавление в корзину", "Пользователь добавил товар в корзину."),
            shop("ADD_TO_WISHLIST", "Добавление в избранное", "Пользователь добавил товар в избранное."),
            shop("CART_UPDATE", "Изменение количества в корзине", "Пользователь изменил количество товара в корзине."),
            shop("CART_VIEW", "Страница корзины", "Пользователь открыл страницу корзины."),
            shop("CATALOG_VIEW", "Страница каталога", "Пользователь открыл страницу каталога."),
            shop("CATEGORY_VIEW", "Страница категории", "Пользователь открыл страницу категории каталога."),
            shop("CHECKOUT_SUBMIT", "Оформление заказа", "Пользователь подтвердил оформление заказа."),
            shop("CHECKOUT_VIEW", "Страница оформления заказа", "Пользователь открыл страницу оформления заказа."),
            shop("CONTACTS_VIEW", "Страница контактов", "Пользователь открыл страницу контактов."),
            shop("DELIVERY_VIEW", "Страница доставки и оплаты", "Пользователь открыл страницу «Доставка и оплата»."),
            shop("HOME_VIEW", "Главная страница", "Пользователь открыл главную страницу магазина."),
            shop("LOGIN", "Вход", "Выполнена попытка входа пользователя в систему."),
            shop("LOGIN_VIEW", "Страница входа", "Пользователь открыл страницу входа."),
            shop("PRODUCT_VIEW", "Страница товара", "Пользователь открыл страницу товара."),
            shop("REGISTER", "Регистрация", "Пользователь зарегистрировал новый аккаунт."),
            shop("REGISTER_VIEW", "Страница регистрации", "Пользователь открыл страницу регистрации."),
            shop("REMOVE_TO_CART", "Удаление из корзины", "Пользователь удалил товар из корзины."),
            shop("REVIEW_ADD", "Добавление отзыва", "Пользователь добавил отзыв к товару."),
            shop("REVIEW_REPLY", "Ответ на отзыв", "Пользователь добавил ответ на отзыв."),
            shop("REVIEWS_PAGE_VIEW", "Страница отзывов", "Пользователь открыл страницу отзывов."),
            shop("SUPPORT_PAGE_VIEW", "Страница поддержки", "Пользователь открыл страницу поддержки."),
            shop("SUPPORT_REQUEST", "Заявка в поддержку", "Пользователь отправил заявку через форму поддержки."),
            shop("WISHLIST_REMOVE", "Удаление из избранного", "Пользователь удалил товар из избранного."),
            shop("WISHLIST_VIEW", "Страница избранного", "Пользователь открыл страницу избранного.")
        );
    }

    private static List<AttributeSeed> attributeSeeds() {
        return List.of(
            attr("IN_STOCK_ONLY", "Only in stock", "Фильтр наличия товара."),
            attr("MAX_PRICE", "Max price", "Максимальная цена в фильтре."),
            attr("MIN_PRICE", "Min price", "Минимальная цена в фильтре."),
            attr("OPTION_IDS_COUNT", "Option ids count", "Количество выбранных опций фильтра."),
            attr("PAGE_INDEX", "Page index", "Номер страницы выдачи."),
            attr("CATEGORY_NAME", "Category name", "Название категории."),
            attr("CATEGORY_SLUG", "Category slug", "Slug категории."),
            attr("DELIVERY_TYPE", "Delivery type", "Тип доставки заказа."),
            attr("DEMO_FAULT", "Demo fault", "Тип демонстрационной ошибки."),
            attr("PAGE_SIZE", "Page size", "Размер страницы выдачи."),
            attr("QUANTITY", "Quantity", "Количество товара."),
            attr("RATING", "Rating", "Оценка отзыва."),
            attr("EMAIL_DOMAIN", "Email domain", "Домен email пользователя."),
            attr("FAILURE_REASON", "Failure reason", "Причина неуспешного действия."),
            attr("REVIEW_IMAGES_COUNT", "Review images count", "Количество изображений в отзыве."),
            attr("SEARCH_QUERY", "Search query", "Поисковая строка."),
            attr("SORT_TYPE", "Sort type", "Тип сортировки."),
            attr("SUPPORT_TOPIC", "Support topic", "Тема обращения в поддержку."),
            attr("AUTH_RESULT", "Auth result", "Результат авторизации."),
            attr("ENTITY_TYPE", "Entity type", "Тип бизнес-сущности."),
            attr("ENTITY_ID", "Entity id", "Идентификатор бизнес-сущности.")
        );
    }

    private static List<StageSeed> stageSeeds() {
        return List.of(
            new StageSeed("FACADE", "Facade", "Координационный слой сценария между контроллером и сервисами."),
            new StageSeed("PERSISTENCE", "Persistence Layer", "Пользовательский слой доступа к данным приложения.")
        );
    }

    private static EventSeed shop(String code, String name, String description) {
        return new EventSeed(code, name, description, "DEFAULT");
    }

    private static AttributeSeed attr(String code, String name, String description) {
        return new AttributeSeed(code, name, description);
    }

    private record EventSeed(String code, String name, String description, String moduleCode) {
    }

    private record AttributeSeed(String code, String name, String description) {
    }

    private record StageSeed(String code, String name, String description) {
    }
}

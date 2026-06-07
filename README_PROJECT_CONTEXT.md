# Контекст проекта GQW для нового ChatGPT-чата

Документ собран по текущему состоянию репозитория. Его цель - быстро передать новому чату архитектуру, кодовую базу, реализованные функции, риски и дальнейший план работ по ВКР. Код приложения при подготовке документа не менялся.

Важно: в рабочем дереве на момент анализа уже были незакоммиченные изменения в backend/frontend аналитики и логах. Этот README описывает текущую рабочую копию, а не обязательно чистый commit.

## 1. Краткое описание проекта

Проект `gqw` - Spring Boot приложение интернет-магазина с отдельным модулем объединенной аналитики пользовательских и системных событий. Магазин используется как демонстрационная предметная система: он генерирует реальные пользовательские сценарии, HTTP-запросы, ошибки, задержки, обращения к БД и frontend-события, на которых можно показать работу аналитического модуля.

Тема ВКР по смыслу проекта: разработка web-системы интернет-магазина с модулем сбора, агрегации и визуального анализа пользовательских событий. Точная формулировка темы в коде не найдена, поэтому это описание является реконструкцией по структуре проекта.

Что разрабатывается:

- интернет-магазин с каталогом, карточками товаров, корзиной, оформлением заказа, избранным, отзывами, личным кабинетом и административной панелью;
- аналитический backend, который регистрирует пользовательское действие как единую сущность события;
- разбиение события на этапы обработки: controller, service, database, frontend и другие stage-типы;
- сбор числовых и текстовых метрик этапов;
- REST API для агрегатов, raw-событий, детализации, справочников и сравнений;
- админский аналитический dashboard на Chart.js с вкладками Overview, Universal, Raw, Metrics, System events и Compare.

Решаемая проблема: в обычном web-приложении пользовательские действия, backend-логи, frontend-ошибки, задержки, SQL/репозиторные операции и бизнес-ошибки разнесены по разным источникам. Проект объединяет их вокруг одного `eventUid` и дает возможность расследовать инциденты: увидеть всплеск count/error rate/P95, перейти к топовым event type, drilldown в raw-события и trace-логи.

Почему интернет-магазин подходит как демонстрационная система:

- у магазина есть понятные бизнес-сценарии: просмотр каталога, фильтрация, карточка товара, корзина, checkout, отзывы, поддержка;
- легко генерируются разные классы событий: успешные действия, ошибки валидации, бизнес-ошибки, системные ошибки, тяжелые запросы;
- есть admin-операции, которые отличаются от пользовательских;
- можно нагрузочно воспроизводить spike и error-wave сценарии через `scripts/analytics-load.js`.

## 2. Общая архитектура

Архитектура монолитная: один Spring Boot backend одновременно обслуживает магазин, админку и аналитику.

Основные части:

- Backend: `src/main/java/com/example/gqw`.
- Shop frontend: Thymeleaf templates и статические ресурсы в `src/main/resources/shop`.
- Admin frontend магазина: `src/main/resources/admin`.
- Analytics Admin frontend: отдельный ресурсный модуль в `src/main/resources/META-INF/gqw-analytics`.
- DB: PostgreSQL в runtime, H2 в тестах.
- ORM: Spring Data JPA/Hibernate, схемы `shop` и `analytics`.
- Analytics UI: обычный HTML/CSS/JavaScript без React/Vue, графики через Chart.js.

Словесная схема взаимодействия:

1. Пользователь или админ открывает страницу магазина/админки.
2. Controller метода, помеченного `@TrackAnalyticsEvent`, перехватывается `AnalyticsEventAspect`.
3. Создается `analytics.event` с `eventUid`, module/event type, path, method, traceId, user/session, start time.
4. Внутри события аспекты service/repository создают stages и записывают метрики.
5. Frontend-скрипт `analytics-web.js` дополнительно отправляет page load, web vitals, API calls, JS errors в `/api/analytics/frontend/ingest`.
6. Данные сохраняются в таблицах `analytics.event`, `analytics.stage`, `analytics.stage_metric`, `analytics.event_attribute`.
7. Rollup-сервисы периодически агрегируют данные в bucket-таблицы и справочники фильтров.
8. Analytics Admin dashboard вызывает REST endpoints `/analytics-admin/api/...`.
9. `analytics-dashboard.js` строит mini/expanded графики, tables, scenario detail panel и Raw-события.

## 3. Технологический стек

Backend:

- Java 21, toolchain задан в `build.gradle`.
- Spring Boot `4.0.4`.
- Spring WebMVC.
- Spring Data JPA.
- Spring Security.
- Thymeleaf.
- Validation.
- Spring AOP / AspectJ starter.
- Lombok.

Build:

- Gradle wrapper: `gradlew`, `gradlew.bat`.
- `settings.gradle`: `rootProject.name = 'gqw'`.
- `build.gradle` переносит build directory в `${user.home}/.gqw-build/${rootProject.name}`, чтобы обходить проблемы с кириллицей в пути.

DB:

- PostgreSQL runtime, `docker-compose.yml` поднимает `postgres:17-alpine`, БД `gqw`, пользователь `postgres`, пароль `postgres`.
- H2 для тестов, `MODE=PostgreSQL`, схемы `analytics` и `shop` создаются через `INIT`.
- Hibernate `spring.jpa.hibernate.ddl-auto=update` в runtime и `create-drop` в test.

Frontend:

- Thymeleaf server-rendered pages.
- Vanilla JavaScript.
- Chart.js `4.4.3` через CDN в analytics templates.
- Bootstrap classes и Bootstrap Icons используются в UI.
- Отдельного frontend bundler, npm package и React/Vue не найдено.

Тесты:

- JUnit 5 через Spring Boot test.
- Spring Security test.
- H2 runtime для интеграционных тестов.

## 4. Структура проекта

Основные директории:

- `src/main/java/com/example/gqw` - Java backend.
- `src/main/java/com/example/gqw/shop` - доменная логика интернет-магазина.
- `src/main/java/com/example/gqw/admin` - административная панель магазина.
- `src/main/java/com/example/gqw/analytics` - объединенная аналитика.
- `src/main/java/com/example/gqw/config` - общая конфигурация, security, datasource, ошибки, resource handlers.
- `src/main/resources/application.properties` - runtime конфигурация.
- `src/test/resources/application.properties` - test конфигурация на H2.
- `src/main/resources/shop/templates` - страницы магазина.
- `src/main/resources/shop/static` - CSS/JS магазина.
- `src/main/resources/admin/templates` - страницы админки магазина.
- `src/main/resources/META-INF/gqw-analytics/templates` - страницы Analytics Admin и публичной analytics page.
- `src/main/resources/META-INF/gqw-analytics/static/js` - JS analytics frontend.
- `src/main/resources/META-INF/gqw-analytics/static/css/app.css` - CSS analytics frontend.
- `src/test/java` - тесты.
- `docs` - рабочие отчеты по аналитическому dashboard и workflow.
- `scripts/analytics-load.js` - k6 нагрузочный сценарий для аналитики.
- `docker-compose.yml` - PostgreSQL для локального запуска.
- `logs` - runtime-логи, включая модульные analytics logs.

## 5. Backend

Главная точка входа:

- `src/main/java/com/example/gqw/GqwApplication.java`.

Основные packages:

- `com.example.gqw.shop.controller`, `service`, `repository`, `entity`, `dto`.
- `com.example.gqw.admin.controller`, `service`.
- `com.example.gqw.analytics.controller`, `service`, `repository`, `entity`, `aop`, `logging`, `config`, `web.dto`.
- `com.example.gqw.config`.

Security/config:

- `SecurityConfig`:
  - публичные routes магазина и статические ресурсы разрешены всем;
  - `/admin/**` требует `ROLE_ADMIN`;
  - `/analytics`, `/analytics/api/**` требуют `ROLE_ADMIN`;
  - `/analytics-admin/**` разрешен отдельно и имеет собственную auth-логику через analytics admin controller/interceptor;
  - `/api/analytics/frontend/**` исключен из CSRF, чтобы best-effort frontend ingest не ломал клиентский flow;
  - login success/failure дополнительно трекаются через `LoginAnalyticsService`.
- `DataSourceConfig` - конфигурация datasource/auto-create DB.
- `TraceIdFilter` - traceId для логов и аналитики.
- `StaticResourceConfig`, `AnalyticsAdminWebConfig`, `AnalyticsUiTemplateConfig` - выдача статических ресурсов и templates.
- `AppGlobalExceptionHandler`, `AppErrorController`, `AppErrorSupport` - обработка ошибок.

DTO:

- Shop DTO: `RegisterRequest`, `CheckoutRequest`, `SupportRequestForm`.
- Analytics DTO:
  - `AnalyticsApiDto` для REST API dashboard;
  - `FrontendAnalyticsIngestRequest` для frontend ingest;
  - старые/service DTO в `analytics/service/dto`: `DashboardResponseDto`, `EventDashboardResponseDto`, `EventListResponseDto`, `EventDetailsResponseDto`, `ChartPointDto`.

Repositories:

- Shop repositories находятся в `src/main/java/com/example/gqw/shop/repository`.
- Analytics repositories находятся в `src/main/java/com/example/gqw/analytics/repository`.
- Для сложных analytics агрегатов активно используется `NamedParameterJdbcTemplate` внутри сервисов, особенно `AnalyticsInsightsService`, rollup-сервисы и log-view сервисы.

Ошибки:

- HTTP/security ошибки проходят через общую обработку в `config`.
- Для аналитики есть `AnalyticsHttpErrorTrackingService`, который пытается связать fallback/error-view с существующим или новым analytics event.

## 6. Интернет-магазин

### Каталог, категории, карточки товаров

Код:

- `CatalogController`
- `CatalogService`
- `CategoryRepository`, `ProductRepository`, filter repositories
- templates: `shop/index.html`, `shop/catalog.html`, `shop/category.html`, `shop/product.html`

Endpoints:

- `GET /` - главная.
- `GET /catalog` - общий каталог.
- `GET /category/{slug}` - категория с фильтрами, поиском, сортировкой, ценовым диапазоном.
- `GET /product/{slug}` - карточка товара.
- `GET /contacts`, `/delivery`, `/about`, `/reviews`.

Сущности:

- `Category`
- `Product`
- `ProductImage`
- `ProductCharacteristic`
- `CategoryFilter`
- `ProductFilter`
- `FilterOption`
- `ProductFilterOption`

Analytics:

- `HOME_VIEW`, `CATALOG_VIEW`, `CATEGORY_VIEW`, `PRODUCT_VIEW`, `CONTACTS_VIEW`, `DELIVERY_VIEW`, `ABOUT_VIEW`, `REVIEWS_PAGE_VIEW`.
- Для `CATEGORY_VIEW` записываются атрибуты вроде `CATEGORY_SLUG`, `CATEGORY_NAME`, `SORT_TYPE`, `SEARCH_QUERY`, `PAGE_INDEX`, `PAGE_SIZE`, `IN_STOCK_ONLY`, `OPTION_IDS_COUNT`, `MIN_PRICE`, `MAX_PRICE`.

### Корзина

Код:

- `CartController`
- `CartService`
- `CartItemRepository`
- template: `shop/cart.html`
- JS: `src/main/resources/shop/static/js/store.js`

Endpoints:

- `GET /cart`
- `POST /cart/add`
- `POST /api/cart/add`
- `POST /api/cart/increment`
- `POST /api/cart/decrement`
- `POST /api/cart/toggle-one`
- `GET /api/cart/count`
- `POST /cart/remove`
- `POST /cart/update`

Сущность:

- `CartItem`

Analytics:

- `CART_VIEW`, `ADD_TO_CART`, `REMOVE_TO_CART`, `CART_UPDATE`.

### Checkout / заказ

Код:

- `CheckoutController`
- `OrderService`
- `CheckoutRequest`
- template: `shop/checkout.html`

Endpoints:

- `GET /checkout`
- `POST /checkout`

Сущности:

- `ShopOrder`
- `OrderItem`
- `OrderStatusHistory`
- enum `OrderStatus`

Analytics:

- `CHECKOUT_VIEW`, `CHECKOUT_SUBMIT`.
- Для checkout есть demo fault через header `X-Demo-Fault`, например `CHECKOUT_RESERVATION_FAIL`.

### Избранное

Код:

- `WishlistController`
- `WishlistService`
- `WishlistItemRepository`
- template: `shop/wishlist.html`

Endpoints:

- `GET /wishlist`
- `POST /api/wishlist/toggle`
- `POST /wishlist/add`
- `POST /wishlist/remove`

Сущность:

- `WishlistItem`

Analytics:

- `WISHLIST_VIEW`, `ADD_TO_WISHLIST`, `WISHLIST_REMOVE`.

### Сравнение товаров

Отдельный функционал сравнения товаров в shop-коде не найден. Поиск по `compare` показывает аналитику сравнения периодов/графиков и обычные Java `compareTo`, но не shop-level compare controller/entity/template.

### Отзывы

Код:

- `ReviewController`
- `ReviewService`
- `ReviewImageStorageService`
- admin moderation: `AdminReviewsController`, часть действий в `AdminProductsController`
- templates: `shop/reviews.html`, блоки в `shop/product.html`, admin `reviews.html`

Endpoints:

- `POST /review/add`
- `POST /review/reply`
- `GET /admin/reviews`
- `POST /admin/reviews/{id}/moderate`
- `POST /admin/reviews/{id}/delete`
- product-scoped review admin endpoints в `AdminProductsController`.

Сущности:

- `Review`
- `ReviewImage`

Analytics:

- `REVIEW_ADD`, `REVIEW_REPLY`, `REVIEW_LIST_VIEW`, `REVIEW_MODERATE`, `REVIEW_DELETE`, `PRODUCT_REVIEW_*`.

### Личный кабинет

Код:

- `AccountController`
- `CurrentUserService`, `UserService`, `OrderService`, `SupportService`
- template: `shop/account.html`

Endpoints:

- `GET /account`
- `POST /account/profile`
- `POST /account/address`
- `POST /account/delete`
- `POST /account/orders/{orderId}/cancel`
- `POST /account/orders/{orderId}/update`
- `POST /account/support/create`

Сущность пользователя:

- `ShopUser`

Analytics:

- `ACCOUNT_VIEW`, `ACCOUNT_PROFILE_UPDATE`, `ACCOUNT_ADDRESS_UPDATE`, `ACCOUNT_DELETE`, `ACCOUNT_ORDER_CANCEL`, `ACCOUNT_ORDER_UPDATE`, `ACCOUNT_SUPPORT_CREATE`.

### Авторизация и регистрация

Код:

- `AuthController`
- `UserService`
- `RegisterRequest`
- templates: `shop/login.html`, `shop/register.html`
- Security: `SecurityConfig`

Endpoints:

- `GET /login`
- `GET /register`
- `POST /register`
- login submit обрабатывает Spring Security.

Analytics:

- `LOGIN_VIEW`, `REGISTER_VIEW`, `REGISTER`.
- Login success/failure трекает `LoginAnalyticsService`.

### Поддержка

Код:

- `SupportController`
- `SupportService`
- `AdminSupportController`
- `SupportRequestForm`
- templates: `shop/support.html`, admin `support.html`, `support-details.html`

Endpoints:

- `POST /support/request`
- `GET /support`
- `GET /admin/support`
- `GET /admin/support/{id}`
- `POST /admin/support/{id}/status`
- `POST /admin/support/{id}/reply`
- `POST /admin/support/{id}/processed`

Сущность:

- `SupportRequest`

Analytics:

- `SUPPORT_REQUEST`, `SUPPORT_PAGE_VIEW`, `SUPPORT_LIST_VIEW`, `SUPPORT_DETAIL_VIEW`, `SUPPORT_STATUS_UPDATE`, `SUPPORT_REPLY_CREATE`, `SUPPORT_PROCESSED_UPDATE`.

### Админские функции магазина

Код:

- `AdminDashboardController`
- `AdminProductsController`
- `AdminCategoriesController`
- `AdminOrdersController`
- `AdminUsersController`
- `AdminReviewsController`
- `AdminSupportController`
- `AdminFiltersController`
- `AdminFilesController`
- `AdminService`

Templates:

- `src/main/resources/admin/templates/admin/*.html`

Endpoints включают:

- `/admin`
- `/admin/products`, create/edit/save/delete/duplicate/images/reviews
- `/admin/categories`
- `/admin/orders`
- `/admin/users`
- `/admin/reviews`
- `/admin/support`
- `/admin/filters`
- `/admin/files`

Analytics:

- Большинство admin endpoints помечены `@TrackAnalyticsEvent`, например `DASHBOARD_VIEW`, `PRODUCT_LIST_VIEW`, `PRODUCT_CREATE`, `ORDER_UPDATE`, `USER_UPDATE`, `FILE_CREATE`, `FILTER_UPDATE`.

## 7. Модуль объединенной аналитики

Это центральный модуль проекта. Код находится в `src/main/java/com/example/gqw/analytics` и `src/main/resources/META-INF/gqw-analytics`.

### Назначение

Модуль связывает бизнес-действие пользователя/админа, backend processing, frontend performance, ошибки и логи в одну аналитическую модель:

- event - верхнеуровневое пользовательское или системное действие;
- stage - этап обработки event;
- metric - числовые/текстовые измерения stage;
- attribute - бизнес/контекстный атрибут event;
- dictionary - справочники event/module/stage/metric/attribute;
- rollup - предагрегация временных bucket;
- dashboard - визуализация и расследование.

### Ключевые сущности

Файлы:

- `AnalyticsEvent` -> table `analytics.event`
- `AnalyticsStage` -> table `analytics.stage`
- `AnalyticsStageMetric` -> table `analytics.stage_metric`
- `AnalyticsEventAttribute` -> table `analytics.event_attribute`
- `EventType` -> `analytics.event_type`
- `ModuleType` -> `analytics.module_type`
- `StageType` -> `analytics.stage_type`
- `StageMetricType` -> `analytics.stage_metric_type`
- `EventAttributeType` -> `analytics.event_attribute_type`
- `AnalyticsCodeAlias` -> aliases для кодов
- `AggregatedMetric`, `AggregationRun` -> batch aggregation metadata
- enums: `MetricValueKind`, `AggregationGranularity`, `AggregationStatus`, `AggregationRunType`, `AnalyticsCodeAliasType`

Главные поля `AnalyticsEvent`:

- `eventUid`
- `userId`
- `sessionId`
- `eventTypeCode`
- `moduleCode`
- `requestPath`
- `httpMethod`
- `traceId`
- `statusCode`
- `isError`
- `errorMessage`
- `startedAt`, `endedAt`, `durationMs`

Главные поля `AnalyticsStage`:

- `eventId`
- `stageTypeCode`
- `stageOrder`
- `startedAt`, `endedAt`, `durationMs`
- `logStartedAt`, `logEndedAt`
- `isError`, `errorMessage`

Главные поля `AnalyticsStageMetric`:

- `stageId`
- `metricTypeCode`
- `metricValueNum`
- `metricValueText`
- `unit`
- `recordedAt`

### Регистрация событий

Основной путь:

- Аннотация `TrackAnalyticsEvent`.
- Атрибуты: `TrackAnalyticsAttribute`.
- Аспект: `AnalyticsEventAspect`.
- Контекст: `AnalyticsEventContext`, `AnalyticsEventContextHolder`.
- API записи: `AnalyticsTrackingApi`.
- Facade: `AnalyticsTrackerFacade`.
- Сервисы записи: `AnalyticsEventService`, `AnalyticsStageService`, `AnalyticsStageMetricService`, `AnalyticsEventAttributeService`.

Как это работает:

1. Controller/service метод помечается `@TrackAnalyticsEvent(code = "...")`.
2. `AnalyticsEventAspect` вокруг метода создает event через `startEvent`.
3. Для controller stage создается stage `CONTROLLER`.
4. Атрибуты из SpEL выражений записываются через `addAttribute`.
5. При успехе вызываются `finishStageSuccess` и `finishEventSuccess`.
6. При исключении записываются `ERROR_CODE`, `ERROR_CLASS`, stage/event помечаются как error.
7. Service/repository аспекты добавляют дополнительные stages.

Дополнительные пути:

- `AnalyticsServiceStageAspect` - stage `SERVICE`, считает duration, item count, ошибки.
- `AnalyticsRepositoryStageAspect` - stage `DATABASE`, пишет `DB_QUERY_COUNT`, `RESPONSE_SIZE_BYTES`, `ITEM_COUNT`, ошибки.
- `AutoCrudAnalyticsEventAspect` - опциональная auto CRUD аналитика, управляется property `app.analytics.auto-crud.enabled`.
- `AnalyticsMethodLoggingAspect` - методное логирование, управляется `app.analytics.method-logging.*`.
- `FrontendAnalyticsIngestService` - frontend events.
- `AnalyticsHttpErrorTrackingService` - связывание HTTP error/fallback с analytics event.

### Frontend ingestion

Файлы:

- `analytics-web.js`
- `FrontendAnalyticsIngestController`
- `FrontendAnalyticsIngestRequest`
- `FrontendAnalyticsIngestService`
- `FrontendAnalyticsDictionaryConfig`

Endpoint:

- `POST /api/analytics/frontend/ingest`

События, которые собирает frontend:

- `FRONTEND_PAGE_LOAD`
- `FRONTEND_WEB_VITALS`
- `FRONTEND_JS_ERROR`
- `FRONTEND_API_CALL`
- declarative DOM events через `data-analytics-event`

Метрики:

- `FRONTEND_TTFB_MS`
- `FRONTEND_DOM_INTERACTIVE_MS`
- `FRONTEND_DOM_CONTENT_LOADED_MS`
- `FRONTEND_LOAD_EVENT_MS`
- `FRONTEND_TRANSFER_SIZE_BYTES`
- `FRONTEND_LCP_MS`
- `FRONTEND_INP_MS`
- `FRONTEND_CLS_SCORE`
- `FRONTEND_API_DURATION_MS`
- `FRONTEND_RENDER_AFTER_API_MS`
- `FRONTEND_HTTP_STATUS`
- текстовые: URL, method, traceId, nav type, error message, custom attrs JSON.

Ingest best-effort: controller всегда возвращает `202 Accepted`; ошибки логируются warning и не ломают клиентский поток.

### Агрегация

Файлы:

- `AnalyticsInsightsService` - основной read/aggregate API для dashboard.
- `AnalyticsTimeRollupService` - временные rollup bucket по events/stages.
- `AnalyticsFilterRollupService` - rollup доступных filter options.
- `AnalyticsStageMetricRollupService` - rollup stage metrics.
- `AggregationService`, `AggregationScheduler`.
- Lifecycle классы: `AnalyticsTimeRollupLifecycle`, `AnalyticsFilterRollupLifecycle`, `AnalyticsStageMetricRollupLifecycle`.

Properties:

- `app.analytics.filter-rollup.enabled=true`
- `app.analytics.time-rollup.enabled=true`
- `app.analytics.stage-metric-rollup.enabled=true`
- cron/overlap/bootstrap параметры в `application.properties`.

Ограничение: для некоторых multi-event expanded overview графиков frontend делает N запросов `/overview` или `/stages`, потому что эти endpoints принимают одиночный `eventTypeCode`. `UniversalController` уже принимает `List<String> eventTypeCode`.

### API аналитики

Controllers:

- `AnalyticsOverviewController`
- `AnalyticsEventController`
- `AnalyticsStageController`
- `AnalyticsUniversalController`
- `AnalyticsCompareController`
- `AnalyticsFilterOptionsController`
- `AnalyticsRangeController`
- `AnalyticsDictionaryController`
- `AnalyticsRuntimeSettingsController`
- `FrontendAnalyticsIngestController`
- `AnalyticsAdminController`
- `AnalyticsPageController`

### Visualization

Файлы:

- `analytics-dashboard.js` - основной UI dashboard.
- `app.css` - стили dashboard.
- `admin-dashboard.html`, `dashboard.html`, `admin-dictionaries.html`, `fragments/admin-header.html`.

Основные вкладки Analytics Admin:

- `overview` / Дашборд
- `universal` / Универсальный анализ
- `raw` / Raw события
- `metrics` / Метрики
- `system` / Служебные события
- `compare` / Сравнение
- `dictionaries` / Справочники

### Ограничения

- Большой файл `analytics-dashboard.js` содержит много состояний и UI логики в одном IIFE.
- Нет frontend unit tests.
- Multi-event overview строится frontend-агрегацией через несколько запросов.
- Chart.js zoom plugin не используется; zoom сделан кастомно через CSS width/height, scroll и range inputs.
- Сценарии analytics dashboard реализованы в JS, backend scenario entity не найден.

## 8. Сценарии аналитики

Сценарии описаны на frontend в `analytics-dashboard.js`:

- `CHART_SCENARIOS_BY_CANVAS`
- `ANALYTICS_SCENARIO_REGISTRY`
- `OVERVIEW_INVESTIGATION_SCENARIOS_BY_CANVAS`

Для трех основных expanded Overview графиков сейчас используется компактный toggle/checkbox вместо popup:

- `chart-events-count` -> "Топ событий"
- `chart-latency` -> "Топ задержек"
- `chart-error-rate` -> "Топ ошибок"

Текущая концепция:

- mini Overview графики display-only: график, tooltip, expand, help `?`; scenario/compare controls на mini не должны появляться;
- expanded графики имеют локальные controls: период, preset, compare, bucket, event filter, metric selector для latency, interval selection, reset, close;
- включение сценария активирует top event types, отрисовывает multi-event линии и открывает scenario detail panel;
- event filter в expanded становится локальной коррекцией списка видимых линий;
- active interval/chip при сценариях не сбрасывается;
- "Без сценария" скрывает detail panel, очищает drilldown и локальный scenario event filter;
- detail panel показывает top rows, кнопки `Выбрать`, `Raw`, в drilldown-состоянии `Назад`.

Реализованные сценарии Overview:

- Поток событий: top events за период.
- Latency trend: top events по задержке.
- Error rate trend: top events по ошибкам.

Что поддержано:

- scenario detail panel под expanded-графиком;
- drilldown в выбранное событие;
- back к полному списку сценария;
- Raw переход/фильтрация из панели;
- multi-event линии с palette;
- stale guard через `state.scenarioDetailRequestId`;
- interval selection с `state.activeAnalysisInterval`;
- compare modes `off`, `split`, `overlay`;
- expanded event filter;
- help modal.

Частично/требует проверки:

- batch backend endpoint для top events не найден; frontend собирает данные через текущие endpoints;
- frontend тестов для сценариев нет;
- часть старого scenario popup кода оставлена для других графиков/legacy, не удалена глобально;
- в docs есть история исправлений вокруг bucket/zoom/compare, поэтому эти области требуют ручной регрессии после изменений.

Нагрузочные сценарии:

- `scripts/analytics-load.js` и `docs/analytics-load-testing.md`.
- Фазы: `baseline`, `spike`, `error_wave`.
- В `spike` header `X-Load-Phase: spike` используется для эмуляции тяжелого хвоста БД.

## 9. Frontend и dashboard

### Основные файлы

- `src/main/resources/META-INF/gqw-analytics/static/js/analytics-dashboard.js`
- `src/main/resources/META-INF/gqw-analytics/static/js/analytics-web.js`
- `src/main/resources/META-INF/gqw-analytics/static/js/analytics-settings.js`
- `src/main/resources/META-INF/gqw-analytics/static/css/app.css`
- `src/main/resources/META-INF/gqw-analytics/templates/analytics/admin-dashboard.html`
- `src/main/resources/META-INF/gqw-analytics/templates/analytics/dashboard.html`
- `src/main/resources/META-INF/gqw-analytics/templates/analytics/fragments/admin-header.html`

### `analytics-dashboard.js`

Файл реализован как IIFE. В нем нет импортов ES modules.

Ключевые state поля:

- `state.charts`
- `state.chartConfigs`
- `state.expandedChart`
- `state.globalCompareMode`
- `state.inlineCompareModeBySource`
- `state.expandedCompareModeBySource`
- `state.expandedRangesBySource`
- `state.expandedBucketBySource`
- `state.expandedEventFilterBySource`
- `state.expandedLatencyMetricBySource`
- `state.scenarioBySource`
- `state.scenarioOverriddenBySource`
- `state.scenarioDetailRequestId`
- `state.scenarioDetailDrilldownBySource`
- `state.scenarioDetailBeforeDrilldownBySource`
- `state.activeAnalysisInterval`
- `state.activeAnalysisIntervalHistory`

Ключевые функции:

- `initRefs` - собирает DOM refs.
- `bindEvents` - навешивает события форм/кнопок.
- `api(path)` - строит URL относительно `/analytics/api` или `/analytics-admin/api`.
- `fetchJson` - общий fetch wrapper.
- `loadOverview` - загружает Overview KPI/series/eventBreakdown.
- `loadStages` - stage breakdown.
- `loadEvents` - Raw events.
- `loadUniversal` / блок universal-функций - универсальный анализ.
- `upsertChart` - lifecycle Chart.js.
- `renderExpandedChartClone` - открытие/отрисовка expanded chart.
- `setupExpandedGraphControls` - toolbar expanded графиков.
- `renderExpandedChartByRanges` - перерисовка expanded по ranges/compare.
- `buildChartConfigByRange` - строит config по canvasId/range/bucket/options.
- `buildExpandedEventSeriesChartConfig` - multi-event series.
- `applyChartScenario` - включение/выключение сценария.
- `applyOverviewScenarioEventFilter` - активация top events сценария.
- `renderScenarioDetailTable` и связанные функции - detail panel.
- `setupExpandedZoomControls` - X/Y zoom через range inputs.
- interval functions около `state.activeAnalysisInterval` - drag interval, floating panel, apply/reset/undo.

### Overview графики

Mini:

- `chart-events-count`
- `chart-latency`
- `chart-error-rate`
- `chart-event-kpi`
- `chart-stage-latency`
- `chart-stage-errors`

Expanded:

- строятся inline рядом с исходным графиком;
- source canvas получает состояние expanded source;
- для compare split может создаваться compare canvas;
- для overlay строятся ghost/baseline datasets;
- для KPI используется bar chart; для count/latency/error line charts.

### Controls

Global top filters:

- from/to
- bucket
- quick preset
- module
- event type
- compare mode
- attribute/metric filters
- reset

Expanded controls:

- local from/to
- preset
- compare mode
- bucket
- scenario toggle для трех Overview investigation графиков
- event filter popup
- latency metric select
- interval selection button
- interval cancel/undo
- reset local filter
- close
- zoom X/Y

Raw events:

- период/preset
- status/error class
- event type
- sort by/sort dir
- advanced filters by metric/attribute
- load more
- event details modal

UX решения:

- mini Overview display-only;
- сценарии только в expanded;
- scenario detail panel занимает место в layout под графиком;
- active interval chip над Overview и связь с Raw;
- help modal для графиков и параметров;
- compact controls в toolbar;
- system events имеют отдельный toolbar и локальный interval/undo, их не нужно путать с Overview active interval.

### CSS

`app.css` содержит стили магазина, админки и аналитики. В analytics части важны блоки:

- `.analytics-chart-wrap`
- `.analytics-chart-actions`
- `.analytics-expanded-block`
- `.analytics-expanded-graph-controls`
- `.analytics-expanded-actions`
- `.analytics-expanded-scroll`
- `.analytics-expanded-scenario-detail`
- `.analytics-scenario-detail-actions-cell`
- `.analytics-expanded-interval-*`
- `.analytics-active-interval-chip`
- `.analytics-universal-*`
- `.analytics-events-*`
- `.analytics-system-chart-*`

## 10. REST API

Базовые пути:

- публичная analytics page: `/analytics/api/...`
- Analytics Admin: `/analytics-admin/api/...`

| Метод | Путь | Назначение | Основные параметры | Ответ | Controller / service | Frontend |
|---|---|---|---|---|---|---|
| GET | `/analytics/api/overview`, `/analytics-admin/api/overview` | Overview KPI, event breakdown, time series | `from`, `to`, `moduleCode`, `eventTypeCode`, `requestPath`, metric/attribute filters, `bucketMinutes` | `OverviewResponse` | `AnalyticsOverviewController.overview` -> `AnalyticsInsightsService.overview` | `loadOverview`, expanded count/latency/error/event-kpi |
| GET | `/analytics/api/overview/compare`, `/analytics-admin/api/overview/compare` | before/after Overview | `beforeFrom`, `beforeTo`, `afterFrom`, `afterTo`, те же filters, `bucketMinutes` | `OverviewCompareResponse` | `AnalyticsOverviewController.overviewCompare` | Есть endpoint, но frontend часто делает парные `/overview` |
| GET | `/analytics/api/stages`, `/analytics-admin/api/stages` | breakdown по stage/layer | `from`, `to`, `moduleCode`, `eventTypeCode`, filters, `bucketMinutes` | `StageBreakdownResponse` | `AnalyticsStageController.stages` -> `stageBreakdown` | `loadStages`, stage charts |
| GET | `/analytics/api/stages/compare`, `/analytics-admin/api/stages/compare` | before/after stage breakdown | before/after dates, filters, `bucketMinutes` | `StageBreakdownCompareResponse` | `AnalyticsStageController.stagesCompare` | Может использоваться, но часто frontend делает pair requests |
| GET | `/analytics/api/stage-metrics`, `/analytics-admin/api/stage-metrics` | stage metric summaries, top values, numeric series | `from`, `to`, `moduleCode`, `eventTypeCode`, `stageTypeCode`, `metricTypeCode`, filters, `bucketMinutes`, include flags | `StageMetricResponse` | `AnalyticsStageController.stageMetrics` | вкладка Metrics |
| GET | `/analytics/api/stage-metrics/compare`, `/analytics-admin/api/stage-metrics/compare` | before/after stage metrics | before/after dates, filters, include flags | `StageMetricCompareResponse` | `AnalyticsStageController.stageMetricsCompare` | Metrics compare |
| GET | `/analytics/api/events`, `/analytics-admin/api/events` | Raw events list | `from`, `to`, `moduleCode`, `eventTypeCode` list, `isError`, `errorClass`, `minDurationMs`, path, attr/metric filters, sort, page/size, `systemEventsOnly` | `EventListResponse` | `AnalyticsEventController.events` -> `AnalyticsInsightsService.events` | Raw tab, scenario Raw links, system events |
| GET | `/analytics/api/events/{eventUid}` | details by UUID | `eventUid` path | `EventDetailsResponse` | `AnalyticsEventController.eventDetails` | event details modal |
| GET | `/analytics/api/events/by-id/{eventId}` | details by numeric id | `eventId` path | `EventDetailsResponse` | `AnalyticsEventController.eventDetailsById` | event details modal |
| GET | `/analytics/api/universal`, `/analytics-admin/api/universal` | универсальный анализ | `from`, `to`, `allTime`, `moduleCode`, `eventTypeCode` list, attr, stage, filters, `bucketMinutes`, `includeEventStageBreakdown`, `systemEventsOnly`, `isError` | `UniversalResponse` | `AnalyticsUniversalController.universal` | Universal tab, system chart |
| GET | `/analytics/api/universal/compare`, `/analytics-admin/api/universal/compare` | universal before/after | before/after dates, `afterAllTime`, filters, list event types | `UniversalCompareResponse` | `AnalyticsUniversalController.universalCompare` | Universal/system compare |
| GET | `/analytics/api/compare`, `/analytics-admin/api/compare` | отдельная карточка delta до/после | `baselineFrom`, `baselineTo`, `targetFrom`, `targetTo`, `moduleCode`, `eventTypeCode`, `requestPath` | `CompareResponse` | `AnalyticsCompareController.compare` | Compare delta block |
| GET | `/analytics/api/filter-options`, `/analytics-admin/api/filter-options` | options для фильтров | `from`, `to`, `moduleCode`, `eventTypeCode`, `requestPath`, `attributeCode` | `FilterOptionsResponse` | `AnalyticsFilterOptionsController` | top filters, expanded event options |
| GET | `/analytics/api/range-start`, `/analytics-admin/api/range-start` | начало доступного диапазона | нет обязательных | `RangeStartResponse` | `AnalyticsRangeController.rangeStart` | preset `all` |
| GET | `/analytics/api/dictionaries`, `/analytics-admin/api/dictionaries` | справочники | не найдено обязательных params | `DictionariesResponse` | `AnalyticsDictionaryController` | init dictionaries |
| POST | `/api/analytics/frontend/ingest` | frontend events batch | body `FrontendAnalyticsIngestRequest` | `202 Accepted`, empty | `FrontendAnalyticsIngestController` -> `FrontendAnalyticsIngestService` | `analytics-web.js` |
| GET/POST | `/analytics-admin/api/runtime-settings` и подroutes | runtime settings/diagnostics/operations | JSON/settings | settings DTO/diagnostics | `AnalyticsRuntimeSettingsController` | `analytics-settings.js` |

Форматы основных ответов описаны в `AnalyticsApiDto`.

## 11. База данных

Runtime DB:

- PostgreSQL `gqw`, схема `shop`, схема `analytics`.
- `spring.jpa.hibernate.ddl-auto=update`.
- `hibernate.jdbc.time_zone=UTC`.

Test DB:

- H2 in-memory `jdbc:h2:mem:gqw;MODE=PostgreSQL`.
- `INIT=CREATE SCHEMA IF NOT EXISTS analytics;CREATE SCHEMA IF NOT EXISTS shop`.
- `ddl-auto=create-drop`.

Shop tables/entities:

- `shop.category` -> `Category`
- `shop.product` -> `Product`
- `shop.product_image` -> `ProductImage`
- `shop.product_characteristic` -> `ProductCharacteristic`
- `shop.category_filter` -> `CategoryFilter`
- `shop.product_filter` -> `ProductFilter`
- `shop.filter_option` -> `FilterOption`
- `shop.product_filter_option` -> `ProductFilterOption`
- `shop.cart_item` -> `CartItem`
- `shop.shop_order` -> `ShopOrder`
- `shop.order_item` -> `OrderItem`
- `shop.order_status_history` -> `OrderStatusHistory`
- `shop.wishlist_item` -> `WishlistItem`
- `shop.review` -> `Review`
- `shop.review_image` -> `ReviewImage`
- `shop.support_request` -> `SupportRequest`
- `shop.shop_user` -> `ShopUser`

Analytics tables/entities:

- `analytics.event`
- `analytics.stage`
- `analytics.stage_metric`
- `analytics.event_attribute`
- `analytics.event_type`
- `analytics.module_type`
- `analytics.stage_type`
- `analytics.stage_metric_type`
- `analytics.event_attribute_type`
- `analytics.code_alias`
- `analytics.aggregated_metric`
- `analytics.aggregation_run`
- `analytics.admin_user`

Rollup/index tables are partly created/managed by configs/services, not all represented as JPA entities. SQL references found in services include:

- `analytics.event_rollup_bucket`
- `analytics.stage_rollup_bucket`
- watermarks for rollup scopes
- filter rollup tables
- stage metric rollup tables

Индексы:

- Entity annotations задают индексы на `event_uid`, `event_type_code`, `module_code`, `started_at`, `stage.event_id`, `stage_type_code`, `stage_metric.stage_id`, `metric_type_code`, period fields in aggregates.
- Есть config `AnalyticsPerformanceIndexesConfig`, который содержит performance indexes для analytics queries.

## 12. Тестирование

Тестовые настройки:

- `src/test/resources/application.properties`
- H2 PostgreSQL mode.
- startup runners, seed, rollups, lifecycle выключены.

Тесты:

- `GqwApplicationTests` - smoke context.
- `AnalyticsIntegrationTest` - интеграционные проверки аналитики.
- `AnalyticsOnlyTests` - test suite/entry для analytics-only набора.
- `AppErrorSupportTest` - классификация/поддержка ошибок приложения.
- `ErrorClassClassifierTest` - классификатор error class.
- `AnalyticsSystemEventClassifierTest` - классификация system events.
- `AnalyticsSeriesTimeTest` - временные labels/displayTime для buckets.
- `AnalyticsRuntimeSettingsServiceTest` - runtime settings.
- `AnalyticsLogArchiveIndexServiceTest` - archive/index log service.
- `AnalyticsTimeRangeResolverTest` - default/invalid/custom time range.
- `AnalyticsValueEstimatorTest` - оценка размеров/значений в аспектах.

Как запускать:

```powershell
.\gradlew.bat test
```

Быстрые build/resource проверки:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat processResources
```

Что стоит добавить:

- integration tests для `/overview`, `/overview/compare`, `/stages`, `/stage-metrics`, `/events` с реальными фильтрами;
- tests для bucket resolver и rollup selection;
- tests для multi-event overview, если backend получит batch eventType support;
- frontend smoke tests через Playwright: открыть dashboard, сменить period/bucket/event, expanded, compare split/overlay, scenario toggle, interval selection, Raw details;
- regression tests для scenario drilldown/back и active interval chip.

## 13. Как запустить проект

Требования:

- JDK 21.
- Docker Desktop или локальный PostgreSQL.
- Windows PowerShell для существующих команд в README/workflow.

DB:

```powershell
docker compose up -d postgres
```

Runtime env по умолчанию:

- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/gqw`
- `SPRING_DATASOURCE_USERNAME=postgres`
- `SPRING_DATASOURCE_PASSWORD=postgres`
- upload dirs: `uploads/products`, `uploads/categories`, `uploads/library`, `uploads/reviews`
- logs: `logs/gqw.log`

Запуск:

```powershell
.\gradlew.bat bootRun
```

В `build.gradle` есть task `freePort8080`, и `bootRun` зависит от него. Он на Windows пытается освободить порт 8080.

Основные URL:

- Магазин: `http://localhost:8080/`
- Admin магазина: `http://localhost:8080/admin`
- Analytics public/admin-gated page: `http://localhost:8080/analytics`
- Analytics Admin: `http://localhost:8080/analytics-admin/dashboard`
- Analytics dictionaries: `http://localhost:8080/analytics-admin/dictionaries`

Остановка Gradle daemon:

```powershell
.\gradlew.bat --stop
```

Частые проблемы:

- PostgreSQL не запущен или занят порт 5432.
- Порт 8080 занят старым bootRun.
- Изменения analytics static не видны из-за classpath copy/cache: см. `docs/analytics-frontend-workflow.md`.
- В templates нужно bump cache-buster query для JS/CSS, если менялись ресурсы analytics UI.
- На Windows кириллический путь проекта компенсируется buildDir в `%USERPROFILE%\.gqw-build\gqw`.

## 14. Текущий статус проекта

Реализовано:

- основной интернет-магазин;
- админка магазина;
- регистрация analytics events через `@TrackAnalyticsEvent`;
- stages для controller/service/database/frontend;
- числовые и текстовые stage metrics;
- frontend ingest;
- raw events и details modal;
- Overview dashboard с KPI, timelines, event KPI, stages;
- expanded charts, compare off/split/overlay, bucket, local filters;
- active interval selection/chip для Overview;
- scenario toggles и scenario detail panel для трех Overview графиков;
- Universal analysis;
- Metrics tab;
- System events chart;
- Runtime settings/dictionaries pages;
- rollup services и performance-related analytics configs;
- нагрузочный сценарий k6.

Стабильно/близко к стабильному:

- backend CRUD магазина;
- базовая запись events/stages/metrics;
- основные endpoints аналитики;
- H2 тесты базовой аналитики;
- Chart.js rendering в dashboard.

Частично/нужно проверять вручную:

- сложные UX сценарии expanded charts: scenario + compare + bucket + interval + event filter;
- zoom и bucket semantics после выделения интервала;
- system events имеют отдельную interval/undo логику, ее нельзя случайно смешивать с Overview;
- frontend state распределен по многим maps, высок риск неполной синхронизации;
- docs содержат историю недавних UI правок, значит dashboard активно менялся.

Технический долг:

- большой `analytics-dashboard.js` без модульного разделения;
- нет frontend tests;
- часть сценариев/помощи захардкожена в JS;
- `/overview` и `/stages` не принимают list eventTypeCode, поэтому multi-event строится несколькими запросами;
- есть старые/legacy scenario popup pieces;
- runtime logs находятся в репозитории и создают шум в `git status`;
- некоторые документы/templates в терминальном выводе отображаются mojibake, нужно проверить encoding вручную в IDE.

## 15. Что важно для дипломной записки

Объект исследования:

- web-приложение интернет-магазина и процессы пользовательского взаимодействия с ним.

Предмет исследования:

- методы сбора, агрегации и визуального анализа пользовательских, backend и frontend событий в web-приложении.

Цель:

- разработать программный модуль объединенной аналитики пользовательских событий, встроенный в интернет-магазин и позволяющий выявлять аномалии, ошибки и деградации производительности.

Задачи:

- спроектировать модель события, этапа, метрики и атрибута;
- реализовать сбор событий на backend через AOP;
- реализовать сбор frontend performance/API/error событий;
- разработать хранилище и справочники;
- реализовать REST API агрегатов и raw-деталей;
- разработать dashboard с графиками и сценариями анализа;
- провести функциональное, интеграционное и нагрузочное тестирование;
- сравнить решение с существующими инструментами.

Актуальность:

- web-приложения требуют не только логирования ошибок, но и привязки ошибок/latency к действиям пользователя и бизнес-контексту;
- готовые инструменты часто разнесены: логи, метрики, frontend analytics и бизнес-события живут отдельно;
- для малых/учебных систем важно иметь встроенный механизм без тяжелой внешней инфраструктуры.

Отличие от Prometheus/Jaeger/Google Analytics/Datadog:

- Prometheus хорошо собирает time-series метрики, но сам по себе не хранит бизнес-сущность пользовательского действия с attributes/stages/raw details;
- Jaeger ориентирован на distributed tracing, но в проекте акцент на бизнес-event, stage metrics и dashboard для предметной системы;
- Google Analytics анализирует frontend/user behavior, но не связывает это с backend stages, DB metrics и Java exceptions внутри приложения;
- Datadog близок по возможностям, но является внешней платформой; в ВКР реализован встроенный демонстрационный модуль с контролем модели данных и UI.

Функциональные требования:

- регистрация пользовательских событий;
- запись stages и metrics;
- запись attributes;
- фильтрация по периоду, module, event type, attributes, metrics;
- KPI и временные ряды;
- raw events и details;
- compare before/after;
- scenario-based analysis;
- active interval selection;
- справочники и runtime settings.

Нефункциональные требования:

- сбор аналитики не должен ломать пользовательский flow;
- frontend ingest best-effort;
- reasonable performance за счет rollups/indexes;
- security: admin-only доступ к аналитике;
- расширяемость словарей event/stage/metric/attribute.

Диаграммы для ВКР:

- диаграмма компонентов;
- sequence diagram регистрации события через AOP;
- ER diagram `shop` и `analytics`;
- use case diagram для пользователя, администратора магазина, аналитика;
- activity diagram расследования инцидента;
- deployment diagram local app + PostgreSQL;
- class diagram ключевых analytics entities/services.

Глава 2:

- требования;
- архитектура;
- модель данных;
- проектирование analytics event/stage/metric;
- проектирование UI dashboard и сценариев.

Глава 3:

- реализация backend AOP;
- реализация frontend ingest;
- REST API;
- dashboard на Chart.js;
- rollups/indexes;
- security и admin panel.

Тестирование:

- unit tests классификаторов/time range/value estimator;
- integration tests analytics flow;
- ручные UI сценарии dashboard;
- k6 нагрузочный сценарий baseline/spike/error_wave;
- проверка trace logs и raw details.

## 16. План дальнейшей работы

По коду:

- стабилизировать `analytics-dashboard.js`, выделив чистые helpers для range/bucket/compare/event filter;
- проверить и зафиксировать semantics zoom + bucket на expanded Count/Latency/Error;
- рассмотреть backend list `eventTypeCode` для `/overview` и `/stages`;
- уменьшить зависимость сценариев от ad-hoc frontend state.

По dashboard:

- добавить Playwright smoke tests;
- проверить responsive layout для Overview, Raw, Universal, System;
- зафиксировать ручной чеклист для scenario + compare + interval;
- улучшить accessibility labels для icon/toggle controls.

По аналитическим сценариям:

- формализовать сценарии в отдельном registry/DTO или хотя бы вынести из большого JS блока;
- добавить сценарии для stage bottleneck и frontend metrics;
- добавить объяснения "почему событие попало в top".

По тестам:

- покрыть `/overview` bucket calculation;
- покрыть `/events` filters;
- покрыть frontend ingest service;
- добавить regression tests для runtime settings и rollup services.

По дипломной записке:

- оформить точную тему и границы исследования;
- подготовить ER/sequence/component diagrams;
- описать отличие от Prometheus/Jaeger/GA/Datadog;
- включить screenshots dashboard и сценарий расследования spike/error wave;
- оформить результаты нагрузочного тестирования.

## 17. Карта важных файлов

| Путь | Назначение | Почему важен | Ключевые классы/функции |
|---|---|---|---|
| `build.gradle` | Gradle config | Java 21, Spring Boot, deps, buildDir, bootRun/freePort8080 | plugins, dependencies, tasks |
| `docker-compose.yml` | PostgreSQL локально | Быстрый runtime DB | service `postgres` |
| `src/main/resources/application.properties` | Runtime config | Datasource, Hibernate, analytics flags, logs | `app.analytics.*`, datasource |
| `src/test/resources/application.properties` | Test config | H2, disabled runners/rollups | H2 PostgreSQL mode |
| `src/main/java/com/example/gqw/GqwApplication.java` | Spring Boot entry | Запуск приложения | `main` |
| `src/main/java/com/example/gqw/config/SecurityConfig.java` | Security | Роли, login, CSRF исключение ingest | `securityFilterChain` |
| `src/main/java/com/example/gqw/config/TraceIdFilter.java` | Trace context | Связь logs/analytics | filter |
| `src/main/java/com/example/gqw/shop/controller/CatalogController.java` | Catalog routes | Главный пользовательский flow | category/product endpoints, analytics annotations |
| `src/main/java/com/example/gqw/shop/controller/CartController.java` | Cart routes/API | Cart events and AJAX | add/increment/decrement/update |
| `src/main/java/com/example/gqw/shop/controller/CheckoutController.java` | Checkout | Заказы и demo fault | `CHECKOUT_SUBMIT` |
| `src/main/java/com/example/gqw/shop/controller/AccountController.java` | Account | Личный кабинет | profile/address/orders/support |
| `src/main/java/com/example/gqw/shop/service/CatalogService.java` | Catalog business logic | Фильтры, поиск, category/product data | catalog methods |
| `src/main/java/com/example/gqw/shop/service/OrderService.java` | Orders | Checkout/order lifecycle | create/update/cancel |
| `src/main/java/com/example/gqw/admin/service/AdminService.java` | Admin logic | Большая часть admin dashboard CRUD summaries | admin operations |
| `src/main/java/com/example/gqw/analytics/aop/TrackAnalyticsEvent.java` | Annotation | Вход в backend analytics | `code`, attributes |
| `src/main/java/com/example/gqw/analytics/aop/AnalyticsEventAspect.java` | Event AOP | Создает/закрывает analytics event | around advice, record attributes/metrics |
| `src/main/java/com/example/gqw/analytics/aop/AnalyticsServiceStageAspect.java` | Service stages | Измеряет service layer | start/finish SERVICE |
| `src/main/java/com/example/gqw/analytics/aop/AnalyticsRepositoryStageAspect.java` | DB stages | Измеряет repository layer | DB_QUERY_COUNT, ITEM_COUNT |
| `src/main/java/com/example/gqw/analytics/service/AnalyticsTrackingApi.java` | Tracking contract | Единый API записи | startEvent, startStage, recordMetric |
| `src/main/java/com/example/gqw/analytics/service/AnalyticsTrackerFacade.java` | Tracking facade | Склеивает event/stage/metric services | implements API |
| `src/main/java/com/example/gqw/analytics/service/AnalyticsEventService.java` | Event persistence | Создание/завершение events | start/finish |
| `src/main/java/com/example/gqw/analytics/service/AnalyticsStageService.java` | Stage persistence | Создание/завершение stages | start/finish stage |
| `src/main/java/com/example/gqw/analytics/service/AnalyticsStageMetricService.java` | Metric persistence | Числовые/текстовые metrics | recordMetricNum/Text |
| `src/main/java/com/example/gqw/analytics/service/AnalyticsInsightsService.java` | Analytics read API | Центральная агрегация dashboard | overview, events, stages, universal |
| `src/main/java/com/example/gqw/analytics/service/AnalyticsTimeRollupService.java` | Time rollups | Быстрые time buckets | refresh rollups, bucket logic |
| `src/main/java/com/example/gqw/analytics/service/AnalyticsFilterRollupService.java` | Filter rollups | Быстрые options | date ranges/filter options |
| `src/main/java/com/example/gqw/analytics/service/AnalyticsStageMetricRollupService.java` | Metric rollups | Stage metrics aggregation | summaries/series |
| `src/main/java/com/example/gqw/analytics/service/FrontendAnalyticsIngestService.java` | Frontend ingest | Сохраняет browser events | ingest, startFrontendStage |
| `src/main/java/com/example/gqw/analytics/service/AnalyticsHttpErrorTrackingService.java` | Error tracking | Связь error pages/logs/events | fallback error handling |
| `src/main/java/com/example/gqw/analytics/web/dto/AnalyticsApiDto.java` | REST DTO | Contract dashboard API | OverviewResponse, EventListResponse, UniversalResponse |
| `src/main/java/com/example/gqw/analytics/web/dto/FrontendAnalyticsIngestRequest.java` | Frontend ingest DTO | Contract browser telemetry | `FrontendEventPayload` |
| `src/main/java/com/example/gqw/analytics/controller/AnalyticsOverviewController.java` | Overview REST | Count/latency/error/KPI data | `/overview`, `/overview/compare` |
| `src/main/java/com/example/gqw/analytics/controller/AnalyticsEventController.java` | Raw REST | Raw list/details | `/events`, details |
| `src/main/java/com/example/gqw/analytics/controller/AnalyticsStageController.java` | Stages/metrics REST | Stage charts and metrics tab | `/stages`, `/stage-metrics` |
| `src/main/java/com/example/gqw/analytics/controller/AnalyticsUniversalController.java` | Universal REST | Multi-event/stage/attribute analysis | `/universal`, `/universal/compare` |
| `src/main/java/com/example/gqw/analytics/controller/FrontendAnalyticsIngestController.java` | Browser telemetry endpoint | Best-effort frontend ingest | `/api/analytics/frontend/ingest` |
| `src/main/resources/META-INF/gqw-analytics/static/js/analytics-dashboard.js` | Analytics dashboard frontend | Самый большой и важный UI файл | state, loadOverview, renderExpandedChart, scenarios |
| `src/main/resources/META-INF/gqw-analytics/static/js/analytics-web.js` | Browser telemetry client | fetch/XHR/page load/vitals/errors | `AnalyticsWeb.init`, queue/flush |
| `src/main/resources/META-INF/gqw-analytics/static/js/analytics-settings.js` | Runtime settings UI | Настройки analytics admin | settings fetch/save |
| `src/main/resources/META-INF/gqw-analytics/static/css/app.css` | Analytics CSS | Layout всех dashboard вкладок | expanded/scenario/system/raw styles |
| `src/main/resources/META-INF/gqw-analytics/templates/analytics/admin-dashboard.html` | Analytics Admin page | Основной dashboard template | tabs, forms, canvases |
| `src/main/resources/META-INF/gqw-analytics/templates/analytics/dashboard.html` | Analytics page | Public/admin-gated analytics template | Chart.js scripts |
| `src/main/resources/META-INF/gqw-analytics/templates/analytics/fragments/admin-header.html` | Analytics nav | Верхние вкладки Analytics Admin | overview/universal/raw/metrics/system/compare |
| `src/main/resources/META-INF/gqw-analytics/templates/analytics/admin-dictionaries.html` | Dictionaries UI | Управление справочниками | events/modules/attributes/stages/metrics |
| `src/main/resources/shop/static/js/store.js` | Shop frontend JS | AJAX cart/wishlist/UI | cart and shop interactions |
| `src/main/resources/shop/templates/shop/*.html` | Shop pages | Пользовательский frontend | catalog/cart/checkout/account |
| `src/main/resources/admin/templates/admin/*.html` | Admin pages | Admin frontend | products/orders/users/support |
| `docs/analytics-dashboard-current-state.md` | Dashboard state report | Технический обзор текущей реализации | overview charts/scenarios/bucket/zoom |
| `docs/analytics-frontend-workflow.md` | Frontend workflow | Как править/проверять analytics resources | cache-buster, processResources |
| `docs/analytics-load-testing.md` | Load testing guide | k6 сценарий и чеклист расследования | baseline/spike/error_wave |
| `scripts/analytics-load.js` | k6 load script | Генерирует реальные HTTP события | phases and headers |


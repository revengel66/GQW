# Analytics Module - техническая сводка для ВКР

Документ описывает Analytics Module как основной объект выпускной квалификационной работы. Интернет-магазин в проекте рассматривается только как демонстрационное приложение-носитель: он дает реальные HTTP-запросы, контроллеры, сервисы, репозитории и пользовательские сценарии, на которых проверяется аналитический модуль.

Дата актуализации: 08.06.2026.

## 1. Назначение системы

Analytics Module - встраиваемый модуль прикладной аналитики для Spring Boot приложения. Он фиксирует пользовательские и служебные события, строит цепочку этапов выполнения запроса, сохраняет атрибуты и метрики, агрегирует данные и предоставляет административный интерфейс для анализа нагрузки, ошибок и производительности.

Основная задача модуля - дать разработчику и администратору приложения инструмент для ответа на вопросы:

- какие пользовательские действия происходят чаще всего;
- какие сценарии деградируют по времени отклика;
- где в цепочке выполнения появляется задержка;
- какие ошибки возникают и с чем они связаны;
- как изменились показатели до и после релиза или настройки;
- какие трассировки, логи и этапы соответствуют конкретному событию.

Проблема, которую устраняет модуль: в типовом веб-приложении технические логи, бизнес-события, метрики производительности и ошибки часто разрознены. Для расследования приходится вручную сопоставлять timestamp, traceId, HTTP path, SQL-запросы и логи. Analytics Module связывает эти данные в единую модель: событие -> этапы -> атрибуты -> метрики -> логи трассировки -> агрегаты -> интерфейс анализа.

Пользователи модуля:

- разработчик, который подключает аннотации и анализирует проблемные сценарии;
- администратор системы, который смотрит дашборды, журнал событий, справочники и параметры;
- технический аналитик или инженер сопровождения, который расследует деградации, ошибки и изменение нагрузки.

Поддерживаемые процессы:

- регистрация пользовательских событий приложения;
- сбор технической телеметрии frontend/backend;
- анализ производительности HTTP-сценариев;
- диагностика ошибок;
- сравнение периодов до/после;
- управление справочниками событий, атрибутов, этапов и метрик;
- просмотр трассировок и логов;
- подготовка агрегатов для быстрых графиков.

## 2. Архитектурная модель

Проект является монолитным Spring Boot приложением, внутри которого Analytics Module выделен отдельным пакетом и набором ресурсов. Модуль интегрируется в приложение через AOP, servlet-фильтры, JPA/JdbcTemplate, собственные контроллеры и static/templates resources.

Общая схема потока:

```text
HTTP request
  -> TraceIdFilter
  -> @TrackAnalyticsEvent / frontend ingest / fallback error tracker
  -> AnalyticsEvent
  -> AnalyticsStage: CONTROLLER, SERVICE, DATABASE, custom layers
  -> AnalyticsEventAttribute
  -> AnalyticsStageMetric
  -> runtime logs with MDC traceId/eventUid/module
  -> log index
  -> rollup tables
  -> REST API
  -> Analytics Admin UI
```

Ключевые архитектурные решения:

- AOP-инструментация бизнес-кода через аннотации.
- Отдельная строгая модель справочников: неизвестные коды не создаются автоматически.
- Возможность отдельной PostgreSQL БД для аналитики через `app.analytics.datasource.*`.
- Разделение сырых данных и агрегатов.
- Runtime settings для части фоновых задач и логирования.
- Admin UI внутри модуля без внешнего frontend-фреймворка.
- MDC-трассировка для связи обычных log-line сообщений с событием.

## 3. Основные модули кода

Главный пакет: `src/main/java/com/example/gqw/analytics`.

| Пакет | Назначение |
|---|---|
| `analytics.aop` | Аннотации и аспекты сбора событий, этапов, метрик и пользовательских слоев. |
| `analytics.config` | Конфигурация datasource, web MVC, авторизации, шаблонов, seed/patch конфигураций. |
| `analytics.controller` | Admin pages и JSON API для дашборда, журнала, диагностики, справочников, параметров. |
| `analytics.entity` | JPA-сущности аналитики и справочников. |
| `analytics.logging` | Методное логирование, MDC appModule, описание операций. |
| `analytics.repository` | JPA repositories аналитических сущностей. |
| `analytics.service` | Бизнес-логика сбора, агрегации, диагностики, runtime settings, логов и справочников. |
| `analytics.source` | Источники начального наполнения справочников. |
| `analytics.support` | Вспомогательные справочники и reading guides. |
| `analytics.web.dto` | DTO для frontend ingest и API. |

Ресурсы модуля:

| Путь | Назначение |
|---|---|
| `src/main/resources/META-INF/gqw-analytics/templates/analytics` | HTML-шаблоны Analytics Admin. |
| `src/main/resources/META-INF/gqw-analytics/static/js` | JS дашборда, настроек и frontend telemetry. |
| `src/main/resources/META-INF/gqw-analytics/static/css/app.css` | Основные стили интерфейса Analytics Admin. |

## 4. Технологический стек

| Область | Используется |
|---|---|
| Язык | Java 21, JavaScript, HTML, CSS |
| Backend | Spring Boot 4.0.4 |
| Web | Spring Web MVC, Thymeleaf |
| AOP | Spring Boot starter AspectJ |
| Persistence | Spring Data JPA, Hibernate, JdbcTemplate, NamedParameterJdbcTemplate |
| СУБД | PostgreSQL как целевая БД, H2 PostgreSQL mode в тестах |
| Безопасность | Spring Security, BCryptPasswordEncoder, session-based Analytics Admin auth |
| Frontend | Vanilla JS, Bootstrap 5.3.3 CDN, Bootstrap Icons 1.11.3 CDN, Chart.js 4.4.3 CDN |
| Логирование | Logback, RollingFileAppender, SiftingAppender, MDC |
| Сборка | Gradle Wrapper |
| Тестирование | JUnit/Spring Boot Test, H2, Spring Security Test |
| Нагрузка | k6 scripts в `scripts/` |

## 5. База данных и хранение

Analytics Module поддерживает два режима datasource.

1. Отдельная аналитическая БД:

```properties
app.analytics.datasource.enabled=true
app.analytics.datasource.url=jdbc:postgresql://localhost:5432/gqw_analytics
app.analytics.datasource.username=postgres
app.analytics.datasource.password=postgres
app.analytics.datasource.driver-class-name=org.postgresql.Driver
```

В этом режиме бизнес-приложение использует свой datasource, а Analytics Module использует `analyticsDataSource`, `analyticsEntityManagerFactory`, `analyticsJdbcTemplate` и `analyticsTransactionManager`.

2. Legacy-режим:

```properties
app.analytics.datasource.enabled=false
```

В этом режиме аналитика использует основной datasource приложения. Он оставлен для обратной совместимости, но для переносимости модуля предпочтителен отдельный PostgreSQL datasource.

Целевая СУБД - PostgreSQL. H2 применяется в тестах в режиме совместимости с PostgreSQL. NoSQL-хранилища модулем не поддерживаются, потому что используются JPA repositories, JdbcTemplate, SQL patch configs, индексы и PostgreSQL-ориентированные SQL-операции.

### Основные таблицы аналитики

| Таблица | Назначение |
|---|---|
| `analytics.event` | Сырые события. Хранит event UID, код события, модуль, время, длительность, статус, ошибку, traceId. |
| `analytics.stage` | Этапы выполнения события: контроллер, сервис, БД, пользовательские слои. |
| `analytics.event_attribute` | Атрибуты события: HTTP method, HTTP path, client type, user agent, entity id и пользовательские атрибуты. |
| `analytics.stage_metric` | Метрики этапов: длительность, count, size, custom metrics. |
| `analytics.module_type` | Справочник модулей приложения. |
| `analytics.event_type` | Справочник типов событий. |
| `analytics.event_attribute_type` | Справочник типов атрибутов. |
| `analytics.stage_type` | Справочник этапов и пользовательских слоев. |
| `analytics.stage_metric_type` | Справочник метрик этапов. |
| `analytics.aggregated_metric` | Исторический слой агрегатов старого типа. |
| `analytics.aggregation_run` | История запусков агрегации. |
| `analytics.runtime_setting` | Runtime-параметры модуля. |
| `analytics.event_rollup_bucket` | Агрегаты событий по временным интервалам. |
| `analytics.stage_rollup_bucket` | Агрегаты этапов по временным интервалам. |
| `analytics.stage_metric_rollup_bucket` | Агрегаты метрик этапов по временным интервалам. |
| `analytics.filter_event_type_day` | Подготовленные данные для быстрых фильтров по событиям. |
| `analytics.filter_attr_value_day` | Подготовленные данные для быстрых фильтров по атрибутам. |
| `analytics.log_file_index` | Индекс файлов логов. |
| `analytics.log_trace_index` | Индекс трассировок в логах. |
| `analytics.log_problem_excerpt` | Важные фрагменты логов. |
| `analytics.admin_user` | Учетная запись администратора Analytics Admin. |
| `analytics.code_alias` | Legacy-таблица алиасов. В strict-модели runtime alias mapping не применяется. |

Связи:

- `event` является корневой записью события.
- `stage.event_id` связывает этап с событием.
- `event_attribute.event_id` связывает атрибут с событием.
- `stage_metric.stage_id` связывает метрику с этапом.
- Справочники используются по стабильным строковым кодам.
- Rollup-таблицы группируют сырые данные по интервалам, типам событий, этапам, метрикам и признакам.

## 6. Strict-модель справочников

Модуль работает в строгой модели кодов.

Правила:

- код события должен существовать в `event_type`;
- код атрибута должен существовать в `event_attribute_type`;
- код метрики должен существовать в `stage_metric_type`;
- код модуля должен существовать в `module_type`;
- код этапа или пользовательского слоя должен существовать в `stage_type`;
- inactive-сущности не используются;
- неизвестные коды не создаются автоматически;
- alias mapping не применяется в runtime.

Если разработчик указал неизвестный или inactive код, бизнес-метод не должен падать, но аналитическая запись пропускается и пишется WARN. Это важно для эксплуатации: ошибка конфигурации видна в логах, но пользовательский сценарий приложения не ломается.

Основные классы strict-модели:

- `AnalyticsCodeResolverService` - нормализация кодов без применения алиасов.
- `AnalyticsEventService` - проверка типа события и модуля.
- `AnalyticsEventAttributeService` - проверка типа атрибута.
- `AnalyticsStageMetricService` - проверка типа метрики.
- `AnalyticsStageService` - проверка типа этапа.
- `AnalyticsLayerStageAspect` - проверка пользовательских слоев.
- `AnalyticsDictionaryAdminService` - управление справочниками и precheck удаления.

## 7. Регистрация пользовательских событий

Основная интеграционная аннотация:

```java
@TrackAnalyticsEvent(
    code = "HOME_VIEW",
    attributes = {
        @TrackAnalyticsAttribute(code = "HTTP_PATH", value = "'/'")
    },
    metrics = {
        @TrackAnalyticsMetric(code = "ITEM_COUNT", value = "#result.size()", unit = "count")
    }
)
```

Поток обработки:

1. `AnalyticsEventAspect` перехватывает метод с `@TrackAnalyticsEvent`.
2. Проверяется `app.analytics.instrumentation.enabled`.
3. Создается `AnalyticsEvent`.
4. В MDC устанавливаются `traceId`, `analyticsEventUid`, `appModule`.
5. Создается этап `CONTROLLER`.
6. Сохраняются системные атрибуты запроса.
7. Вычисляются declared attributes и metrics из аннотации.
8. Выполняется бизнес-метод.
9. Фиксируются статус, длительность, ошибка при наличии.
10. Событие и этап завершаются.

События могут стартовать:

- через `@TrackAnalyticsEvent`;
- через frontend ingest endpoint;
- через fallback tracking HTTP-ошибок;
- через `AnalyticsTrackingApi`, если разработчик вызывает API вручную.

Минимальная точка интеграции - любой Spring Bean или публичный API `AnalyticsTrackingApi`. Spring MVC controller не является обязательной архитектурной точкой, но часть HTTP-атрибутов доступна только при активном HTTP request.

## 8. Этапы выполнения

Этап - часть обработки события. В деталях события этапы показывают, где именно было потрачено время.

Системные этапы:

- `CONTROLLER` - обработка HTTP-контроллера;
- `SERVICE` - сервисный слой;
- `DATABASE` - вызовы Spring Data repositories;
- frontend/system stages - если событие пришло из клиентской телеметрии.

Пользовательские этапы:

```java
@TrackAnalyticsLayer(code = "FACADE")
public class CatalogFacade {
}
```

```java
@TrackAnalyticsLayer(code = "PERSISTENCE")
public class CatalogPersistence {
}
```

Для custom layer требуется запись в справочнике этапов. Если этап неизвестен или inactive, он пропускается с WARN.

Классы:

- `AnalyticsServiceStageAspect` - этапы сервисов.
- `AnalyticsRepositoryStageAspect` - этапы БД.
- `AnalyticsLayerStageAspect` - пользовательские слои.
- `AnalyticsStageService` - создание и завершение этапов.

Особенность: если класс или метод имеет `@TrackAnalyticsLayer`, service aspect не должен дублировать `SERVICE` stage для того же вызова.

## 9. Метрики и атрибуты

Атрибуты события описывают контекст:

- HTTP method;
- HTTP path;
- client type;
- user agent;
- referrer;
- request id;
- session/user hash;
- entity type/id;
- пользовательские атрибуты.

Метрики этапов описывают измеримые показатели:

- длительность;
- количество элементов;
- размер ответа;
- количество DB-запросов;
- пользовательские numeric/text metrics;
- frontend page load metrics;
- web vitals.

Способы записи:

- declared attributes/metrics внутри `@TrackAnalyticsEvent`;
- `@TrackAnalyticsStageMetric`;
- автоматические системные метрики в аспектах;
- frontend ingest;
- ручной вызов `AnalyticsTrackingApi`.

## 10. Frontend telemetry

Файл `analytics-web.js` собирает клиентскую телеметрию и отправляет ее в:

```text
POST /api/analytics/frontend/ingest
```

Назначение:

- фиксировать загрузку страниц;
- фиксировать Web Vitals;
- фиксировать JavaScript ошибки;
- фиксировать frontend API calls;
- связать клиентскую телеметрию с traceId.

Контроллер: `FrontendAnalyticsIngestController`.

Сервис: `FrontendAnalyticsIngestService`.

При `app.analytics.instrumentation.enabled=false` endpoint возвращает успешный no-op ответ и не сохраняет данные.

## 11. Логи и трассировки

Модуль использует стандартный Logback и MDC.

Ключи MDC:

- `traceId`;
- `analyticsEventUid`;
- `appModule`.

Фильтры и логирование:

- `TraceIdFilter` создает или принимает `X-Trace-Id`, кладет его в MDC и response header.
- `AppModuleMdcFilter` устанавливает модуль приложения.
- `AnalyticsMethodLoggingAspect` пишет встроенные log-line сообщения по методам.
- Logback пишет общий файл `logs/gqw.log`.
- SiftingAppender пишет модульные логи в `logs/analytics/modules/{module}.log`.

Обычный пользовательский лог разработчика:

```java
log.info("Custom business log: orderId={}, status={}", orderId, status);
```

попадает в файлы логов как обычный Logback log-line. Если он выполнен внутри активного analytics-события, MDC содержит traceId/eventUid, поэтому строка может быть найдена в trace logs после индексации.

Индекс логов:

- `AnalyticsLogArchiveIndexService` сканирует текущие и архивные логи.
- `AnalyticsLogViewService` отдает данные для UI деталей события и логов трассировки.
- Runtime settings управляют папкой логов, лимитами, уровнями важных фрагментов и сроками хранения.

## 12. Агрегация и фоновые задачи

Сырые события удобны для точных деталей, но графики и фильтры требуют быстрых агрегатов. Для этого модуль содержит фоновые rollup-задачи.

Scheduled-задачи:

| Класс | Назначение |
|---|---|
| `AggregationScheduler` | Периодическая агрегация старого слоя `aggregated_metric`. |
| `AnalyticsTimeRollupService` | Агрегаты событий и этапов по временным интервалам. |
| `AnalyticsStageMetricRollupService` | Агрегаты метрик этапов. |
| `AnalyticsFilterRollupService` | Быстрые данные для фильтров по событиям и атрибутам. |
| `AnalyticsLogArchiveIndexService` | Индексация текущих и архивных логов. |
| `AnalyticsDataLifecycleService` | Обслуживание и очистка данных по runtime settings. |
| `AnalyticsHttpErrorTrackingService` | Fallback-дозапись HTTP-ошибок, если они не были покрыты пользовательским событием. |

Основные флаги:

| Property | Default | Назначение |
|---|---:|---|
| `app.analytics.instrumentation.enabled` | `true` | Полное включение/выключение instrumentation на уровне аспектов и ingest. |
| `app.analytics.method-logging.enabled` | `true` | Встроенное методное логирование. |
| `app.analytics.method-logging.controller-enabled` | `true` | Логи контроллеров. |
| `app.analytics.method-logging.service-enabled` | `true` | Логи сервисов. |
| `app.analytics.method-logging.repository-enabled` | `false` | Логи репозиториев. |
| `app.analytics.datasource.enabled` | `true` | Использовать отдельный datasource аналитики. |
| `app.analytics.auto-crud.enabled` | `false` | Автоматические CRUD-события. |
| `app.analytics.bootstrap-enabled` | `false` | Bootstrap-инициализация. |
| `app.analytics.filter-rollup.enabled` | `true` | Rollup фильтров. |
| `app.analytics.time-rollup.enabled` | `true` | Rollup временных рядов. |
| `app.analytics.stage-metric-rollup.enabled` | `true` | Rollup метрик этапов. |
| `app.analytics.lifecycle.enabled` | `true` | Lifecycle/cleanup задачи. |
| `app.startup.runners-enabled` | `true` | Schema patch и startup runners. |

Часть настроек доступна в UI раздела "Параметры" и хранится в `analytics.runtime_setting`.

## 13. Analytics Admin UI

Основной интерфейс:

```text
/analytics-admin/dashboard
```

Шаблоны:

- `admin-dashboard.html` - дашборд, диагностика, журнал, служебные события, сравнение.
- `admin-dictionaries.html` - справочники и учетные данные.
- `admin-instruction.html` - встроенная инструкция.
- `admin-login.html` - вход.
- `admin-setup.html` - первичная настройка.
- `fragments/admin-header.html` - общая шапка.

Клиентский код:

- `analytics-dashboard.js` - основная логика UI, графиков, фильтров, таблиц, модалок.
- `analytics-settings.js` - параметры и runtime diagnostics.
- `analytics-web.js` - frontend telemetry для приложения.
- `app.css` - стили.

Разделы UI:

1. Дашборд
   - KPI: количество, AVG, P95, P99, доля ошибок.
   - Графики: поток событий, динамика производительности, динамика ошибок, события.
   - Expanded chart с bucket, zoom interval, comparison mode, sliders.

2. Диагностика
   - универсальный анализ по периоду, событиям и этапам;
   - топ событий;
   - RCA и breakdown по маршрутам, источникам, клиентам, ошибкам, модулям;
   - переход в журнал.

3. Журнал событий
   - список сырых событий;
   - фильтры по периоду, статусу, событиям, метрикам, атрибутам;
   - сортировка;
   - детали события.

4. Служебные события
   - отдельный аналитический экран системных HTTP, ресурсных и технических событий;
   - локальные фильтры;
   - график служебных событий;
   - таблица и детали.

5. Сравнение
   - сравнение периода "до" и "после";
   - KPI-карточки;
   - вывод сравнения;
   - таблицы ухудшений и улучшений.

6. Справочники
   - модули;
   - события;
   - атрибуты;
   - этапы;
   - метрики;
   - precheck удаления;
   - strict-модель кодов.

7. Параметры
   - runtime settings;
   - диагностика состояния агрегатов, логов, lifecycle;
   - операции обслуживания.

8. Инструкция
   - встроенная документация пользователя с якорным меню.

## 14. REST API

Основные контроллеры:

| Контроллер | Endpoint | Назначение |
|---|---|---|
| `AnalyticsAdminController` | `/analytics-admin/**` | HTML-страницы, login/setup/logout, справочники, учетные данные. |
| `AnalyticsAdminInstructionController` | `/analytics-admin/instruction` | Страница инструкции. |
| `FrontendAnalyticsIngestController` | `/api/analytics/frontend/ingest` | Прием frontend telemetry. |
| `AnalyticsEventController` | `/analytics-admin/api/events` | Сырые события и детали события. |
| `AnalyticsOverviewController` | `/analytics-admin/api/overview` | KPI и обзорные графики. |
| `AnalyticsUniversalController` | `/analytics-admin/api/universal` | Диагностика, RCA, breakdown, сравнение. |
| `AnalyticsStageController` | `/analytics-admin/api/stages`, `/stage-metrics` | Данные по этапам и метрикам этапов. |
| `AnalyticsCompareController` | `/analytics-admin/api/compare` | Сравнение периодов. |
| `AnalyticsDictionaryController` | `/analytics-admin/api/dictionaries` | Справочные данные для UI. |
| `AnalyticsFilterOptionsController` | `/analytics-admin/api/filter-options` | Значения фильтров. |
| `AnalyticsRangeController` | `/analytics-admin/api/range-start` | Расчет начала диапазона. |
| `AnalyticsRuntimeSettingsController` | `/analytics-admin/api/runtime-settings` | Runtime settings, diagnostics, operations. |

Для совместимости часть API также доступна под `/analytics/api/**`, но основной пользовательский UI работает через `/analytics-admin/**`.

## 15. Безопасность

Spring Security в основном приложении не закрывает `/analytics-admin/**` напрямую. За доступ отвечает собственный interceptor модуля:

- `AnalyticsAdminWebConfig`;
- `AnalyticsAdminAuthInterceptor`;
- `AnalyticsAdminAuthService`;
- `AnalyticsAdminUser`.

Механика:

1. Если admin user не существует, доступна страница setup.
2. После создания учетной записи используется login/password.
3. Пароль хранится как BCrypt hash.
4. После входа в session кладутся:
   - `analyticsAdminAuthenticated`;
   - `analyticsAdminUserId`;
   - `analyticsAdminUsername`.
5. Все страницы `/analytics-admin/**`, кроме login/setup/logout, требуют session auth.

JWT в Analytics Module не используется.

## 16. Системные события

Модуль различает пользовательские и служебные события. Служебные события нужны для технической телеметрии, которая не всегда относится к бизнес-сценарию.

Текущий набор системных событий:

- `FRONTEND_API_CALL` - клиентский HTTP/API вызов;
- `FRONTEND_JS_ERROR` - JavaScript ошибка;
- `FRONTEND_PAGE_LOAD` - загрузка страницы;
- `FRONTEND_WEB_VITALS` - UX-метрики клиента;
- `HTTP_REQUEST_ERROR` - HTTP-ошибка backend, если она не покрыта пользовательским событием.

События безопасности и конфигурации не добавлялись как отдельные системные события, потому что часть ошибок логичнее фиксировать внутри пользовательских событий или обычных логов. Для текущей модели этот набор считается достаточным: он покрывает frontend telemetry и fallback HTTP-ошибки.

## 17. Рекомендательная система

В Analytics Module рекомендательная система не реализована. Если в демонстрационном интернет-магазине присутствуют товарные рекомендации, они не относятся к предмету этого документа и не являются функциональностью аналитического модуля.

## 18. Тестирование

Реализованные тестовые направления:

- отключение instrumentation: `AnalyticsInstrumentationDisabledTest`;
- интеграционный сбор событий: `AnalyticsIntegrationTest`;
- отдельный datasource аналитики: `AnalyticsSeparateDataSourceTest`;
- чтение аннотаций MVC и declared attributes/metrics;
- пользовательские слои;
- stage metric annotations;
- расчет временных диапазонов;
- runtime settings;
- log archive index;
- log view;
- series time utilities;
- system event classifier;
- error class classifier;
- value estimator.

Тестовая БД:

- H2 в PostgreSQL mode;
- схемы `analytics` и `shop`;
- `ddl-auto=create-drop`;
- startup runners и rollups в тестах выключены, чтобы тесты были детерминированными.

Команды проверки:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test
.\gradlew.bat processResources
git diff --check
.\gradlew.bat --stop
```

## 19. Нагрузочное тестирование

В проекте есть k6-сценарии:

- `scripts/analytics-load.js`;
- `scripts/analytics-business-focused-30m.js`;
- `scripts/analytics-demo-hour.js`;
- `scripts/analytics-ingest-stability.js`;
- `scripts/analytics-live-background-lite.js`;
- `scripts/analytics-live-user-actions.js`.

Ключевой сценарий `analytics-load.js` проверяет:

- просмотр главной и каталога;
- карточки товаров;
- волны ошибок через несуществующие product URL;
- стабильность RPS;
- p95;
- error rate;
- dropped iterations.

Для корректного сравнения нужны два режима:

1. Baseline без instrumentation:

```powershell
$env:APP_ANALYTICS_INSTRUMENTATION_ENABLED="false"
.\gradlew.bat bootRun
```

2. Analytics ON:

```powershell
$env:APP_ANALYTICS_INSTRUMENTATION_ENABLED="true"
$env:APP_ANALYTICS_DATASOURCE_ENABLED="true"
$env:APP_ANALYTICS_DATASOURCE_URL="jdbc:postgresql://localhost:5432/gqw_analytics"
.\gradlew.bat bootRun
```

Метрики эксперимента:

- RPS;
- throughput;
- average response time;
- median;
- p95;
- p99;
- max response time;
- error rate;
- dropped iterations;
- CPU/RAM;
- Hikari pool usage;
- количество записей `event`, `stage`, `event_attribute`, `stage_metric`;
- среднее число stages/metrics на событие;
- размер логов.

Предварительное наблюдение по нагрузке: при включенной Analytics основная деградация может возникать из-за большого числа синхронных записей в БД и большого количества этапов на один пользовательский запрос, особенно если трекаются частые service/repository вызовы. Для чистого эксперимента Analytics datasource должен быть отдельным, а фоновые rollup/log-index задачи должны быть либо одинаково включены в обоих режимах, либо отдельно зафиксированы в методике.

## 20. Производительность и оптимизации

Уже реализованные оптимизации:

- rollup-таблицы для графиков вместо чтения только сырых событий;
- filter rollup для быстрых значений фильтров;
- lazy delete precheck в справочниках вместо массового подсчета usage при открытии страницы;
- отдельный analytics datasource;
- возможность выключить instrumentation для baseline;
- runtime settings для лог-индекса, retention и rollup;
- исключение analytics-пакетов из service/repository aspects, чтобы не трекать внутреннюю работу аналитики самой аналитикой;
- отключенный по умолчанию repository method logging;
- strict model без autocreate неизвестных кодов.

Потенциальные точки деградации:

- синхронная запись событий и этапов;
- большое число `DATABASE` stages при множестве repository вызовов;
- запись stage metrics;
- log file IO;
- log indexing;
- rollup jobs при активной нагрузке;
- размер Hikari pools;
- транзакции `REQUIRES_NEW` в `AnalyticsTrackerFacade`;
- крупные страницы деталей события.

## 21. Настройки интеграции

Минимальные параметры для штатного режима:

```properties
app.analytics.instrumentation.enabled=true
app.analytics.datasource.enabled=true
app.analytics.datasource.url=jdbc:postgresql://localhost:5432/gqw_analytics
app.analytics.datasource.username=postgres
app.analytics.datasource.password=postgres
app.analytics.datasource.driver-class-name=org.postgresql.Driver
```

Если модуль используется в стороннем приложении, рекомендуется:

1. Создать отдельную PostgreSQL БД для аналитики.
2. Импортировать или создать schema `analytics`.
3. Включить `app.analytics.datasource.enabled=true`.
4. Завести справочники событий, атрибутов, этапов и метрик.
5. Использовать точные коды из справочников в аннотациях.
6. Не использовать alias-коды.
7. Не полагаться на autocreate неизвестных runtime-кодов.

## 22. Структура проекта в разрезе Analytics Module

```text
src/main/java/com/example/gqw/analytics
  aop/          аннотации и аспекты instrumentation
  config/       datasource, web, auth, seed/patch config
  controller/   HTML controllers и JSON API
  entity/       JPA-сущности analytics schema
  logging/      method logging, MDC, operation descriptions
  repository/   JPA repositories аналитики
  service/      сбор событий, rollup, RCA, settings, logs
  source/       источники справочников
  support/      вспомогательные справочники
  web/dto/      DTO внешнего ingest/API

src/main/resources/META-INF/gqw-analytics
  templates/analytics/   страницы Analytics Admin
  static/js/             dashboard/settings/frontend telemetry JS
  static/css/            стили UI

src/test/java/com/example/gqw/analytics
  ...                    unit/integration tests аналитики

scripts/
  analytics-*.js         k6-сценарии нагрузки
```

## 23. Ключевые функции для ВКР

- Автоматическая регистрация пользовательских событий через аннотации.
- Построение цепочки выполнения события по слоям.
- Поддержка пользовательских слоев через `@TrackAnalyticsLayer`.
- Сбор атрибутов и метрик без изменения бизнес-логики.
- Строгие справочники кодов.
- Отдельная аналитическая PostgreSQL БД.
- Runtime-параметры обслуживания и логирования.
- Журнал событий с деталями и трассировками.
- Дашборды и графики производительности.
- Диагностика и RCA.
- Сравнение периодов.
- Служебные события frontend/backend.
- Индексация логов и связь log-line сообщений с traceId.
- Нагрузочное тестирование влияния аналитики.

## 24. Практическая значимость

Модуль позволяет встроить наблюдаемость в прикладную систему без внедрения внешней платформы мониторинга. Он полезен для небольших и средних Spring Boot приложений, где нужна прикладная аналитика на уровне бизнес-событий, но при этом важно видеть технические этапы выполнения, ошибки и производительность.

Практический эффект:

- сокращение времени расследования ошибок;
- выявление деградаций после релиза;
- анализ пользовательской нагрузки;
- контроль качества пользовательских сценариев;
- проверка влияния instrumentation на производительность.

## 25. Научная новизна и инженерная ценность

Для ВКР можно выделить следующие результаты:

- объединение пользовательской аналитики и технической трассировки в одной модели данных;
- strict-модель справочников, исключающая неоднозначность кодов;
- поддержка пользовательских архитектурных слоев в цепочке выполнения;
- отдельный analytics datasource для переносимости модуля;
- встроенный UI диагностики без внешней BI-системы;
- сравнение периодов и RCA на основе собранных событий, этапов, атрибутов и метрик;
- методика нагрузочного сравнения приложения с аналитикой и без нее.

## 26. Изменения архитектуры по сравнению с ранней моделью

В процессе разработки модуль был переработан:

- удалена пользовательская вкладка алиасов;
- alias mapping отключен в runtime;
- unknown code autocreate заменен strict-моделью;
- добавлен отдельный analytics datasource;
- добавлен флаг `app.analytics.instrumentation.enabled` для baseline-нагрузки;
- справочники ускорены за счет lazy delete precheck;
- инструкция и UI переведены в documentation-style;
- расширены служебные события, сравнение, диагностика и журнал;
- добавлена поддержка пользовательских слоев FACADE/PERSISTENCE;
- переработаны header, help mode и runtime settings UI.

## 27. Ограничения и технический долг

Текущие ограничения:

- целевая БД - PostgreSQL;
- NoSQL не поддерживается;
- database-agnostic диалекты не реализованы;
- часть legacy-кода alias-механизма физически остается в проекте, хотя runtime mapping отключен;
- часть schema patch configs привязана к SQL и требует проверки при переносе;
- крупный файл `analytics-dashboard.js` содержит много UI-логики и требует осторожного изменения;
- фоновые rollup/log-index задачи должны учитываться при нагрузочных тестах;
- высокая детализация service/repository stages может создавать заметный overhead под нагрузкой.

Отдельно для финальной подготовки стоит проверить кодировку пользовательских текстов в нескольких старых файлах и документах, где ранее встречались признаки mojibake.

## 28. Блок для изменений по итогам тестирования

Этот раздел предназначен для фиксации правок, которые будут внесены после текущего цикла нагрузочного и интеграционного тестирования.

Формат записи:

| Дата | Обнаружено | Изменено | Проверки | Влияние на ВКР |
|---|---|---|---|---|
|  |  |  |  |  |

Пока новых изменений по итогам текущего тестирования в этот документ не внесено.

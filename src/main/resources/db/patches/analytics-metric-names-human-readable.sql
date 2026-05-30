-- Human-readable metric names for analytics dictionaries.
-- Run once on target DB.

update analytics.stage_metric_type
set name = 'Количество SQL-запросов'
where code = 'DB_QUERY_COUNT';

update analytics.stage_metric_type
set name = 'Код ошибки'
where code = 'ERROR_CODE';

update analytics.stage_metric_type
set name = 'Длительность API (мс)'
where code = 'FRONTEND_API_DURATION_MS';

update analytics.stage_metric_type
set name = 'HTTP-метод'
where code = 'FRONTEND_API_METHOD';

update analytics.stage_metric_type
set name = 'Адрес API-запроса'
where code = 'FRONTEND_API_URL';

update analytics.stage_metric_type
set name = 'CLS'
where code = 'FRONTEND_CLS_SCORE';

update analytics.stage_metric_type
set name = 'Пользовательские атрибуты'
where code = 'FRONTEND_CUSTOM_ATTRS_JSON';

update analytics.stage_metric_type
set name = 'DOM загружен (DOMContentLoaded, мс)'
where code = 'FRONTEND_DOM_CONTENT_LOADED_MS';

update analytics.stage_metric_type
set name = 'Готовность DOM к взаимодействию (DOM Interactive, мс)'
where code = 'FRONTEND_DOM_INTERACTIVE_MS';

update analytics.stage_metric_type
set name = 'Текст ошибки'
where code = 'FRONTEND_ERROR_MESSAGE';

update analytics.stage_metric_type
set name = 'HTTP-статус'
where code = 'FRONTEND_HTTP_STATUS';

update analytics.stage_metric_type
set name = 'Задержка отклика интерфейса (INP, мс)'
where code = 'FRONTEND_INP_MS';

update analytics.stage_metric_type
set name = 'Отрисовка самого крупного элемента (LCP, мс)'
where code = 'FRONTEND_LCP_MS';

update analytics.stage_metric_type
set name = 'Завершение загрузки страницы (Load Event, мс)'
where code = 'FRONTEND_LOAD_EVENT_MS';

update analytics.stage_metric_type
set name = 'Тип навигации'
where code = 'FRONTEND_NAV_TYPE';

update analytics.stage_metric_type
set name = 'Сетевая ошибка'
where code = 'FRONTEND_NETWORK_ERROR';

update analytics.stage_metric_type
set name = 'URL страницы'
where code = 'FRONTEND_PAGE_URL';

update analytics.stage_metric_type
set name = 'Дорендер после API (мс)'
where code = 'FRONTEND_RENDER_AFTER_API_MS';

update analytics.stage_metric_type
set name = 'Идентификатор трассировки (Trace ID)'
where code = 'FRONTEND_TRACE_ID';

update analytics.stage_metric_type
set name = 'Размер передачи (байт)'
where code = 'FRONTEND_TRANSFER_SIZE_BYTES';

update analytics.stage_metric_type
set name = 'Время до первого байта (TTFB, мс)'
where code = 'FRONTEND_TTFB_MS';

update analytics.stage_metric_type
set name = 'Количество элементов'
where code = 'ITEM_COUNT';

update analytics.stage_metric_type
set name = 'Размер полезных данных ответа (байт)'
where code = 'PAYLOAD_SIZE_BYTES';

update analytics.stage_metric_type
set name = 'Полный размер ответа (байт)'
where code = 'RESPONSE_SIZE_BYTES';

update analytics.stage_metric_type
set name = 'Повторные попытки'
where code = 'RETRY_COUNT';

update analytics.stage_metric_type
set name = 'Ошибки валидации'
where code = 'VALIDATION_ERROR_COUNT';

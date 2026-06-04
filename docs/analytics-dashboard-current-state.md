# Текущее состояние аналитического дашборда и графиков

Отчет описывает текущее состояние рабочей копии проекта без рефакторинга и исправлений. Основной frontend дашборда реализован в одном файле:

- `src/main/resources/META-INF/gqw-analytics/static/js/analytics-dashboard.js`

Шаблоны страниц:

- `src/main/resources/META-INF/gqw-analytics/templates/analytics/dashboard.html`
- `src/main/resources/META-INF/gqw-analytics/templates/analytics/admin-dashboard.html`

Backend API и DTO:

- `src/main/java/com/example/gqw/analytics/controller/*`
- `src/main/java/com/example/gqw/analytics/service/AnalyticsInsightsService.java`
- `src/main/java/com/example/gqw/analytics/service/AnalyticsTimeRollupService.java`
- `src/main/java/com/example/gqw/analytics/web/dto/AnalyticsApiDto.java`

## 1. Где находится вкладка "Обзор"

### Роуты и страницы

- Публичная страница аналитики:
  - route: `GET /analytics`
  - controller: `AnalyticsPageController.analyticsDashboard`
  - template: `analytics/dashboard`
  - API base: `/analytics/api`

- Админская страница аналитики:
  - route: `GET /analytics-admin/dashboard`
  - controller: `AnalyticsAdminController.dashboard`
  - template: `analytics/admin-dashboard`
  - API base: `/analytics-admin/api`
  - параметр `tab` нормализуется в `overview`, `universal`, `raw`, `metrics`, `compare`; неизвестные значения сбрасываются в `overview`.

Вкладка определяется DOM-атрибутом:

- кнопка: `data-analytics-tab="overview"`
- секции: `data-analytics-view="overview"`

Переключение вкладок реализовано во frontend:

- `initDashboardViewMode`
- `setDashboardViewTab`
- обработчики `refs.analyticsTabButtons` и `refs.analyticsTopTabButtons`

### Основные frontend-компоненты

Формальных React/Vue-компонентов нет. Компонентность реализована функциями и DOM-секциями внутри `analytics-dashboard.js`.

Ключевые блоки:

- Верхняя панель фильтров: `#analytics-main-form`
- KPI-карточки: `#kpi-total-events`, `#kpi-avg-ms`, `#kpi-p95-ms`, `#kpi-p99-ms`, `#kpi-error-rate`, `#kpi-errors`
- Малые графики обзора:
  - `#chart-events-count`
  - `#chart-latency`
  - `#chart-error-rate`
  - `#chart-event-kpi`
  - `#chart-stage-latency`
  - `#chart-stage-errors`
- Таблица этапов:
  - `#analytics-stage-table`
- Блок сравнения до/после:
  - `#analytics-compare-form`
  - `#chart-compare-delta`

### Компоненты маленьких графиков

Малые графики строятся через:

- `loadOverview`:
  - `chart-events-count`
  - `chart-latency`
  - `chart-error-rate`
  - `chart-event-kpi`
- `loadStages`:
  - `chart-stage-latency`
  - `chart-stage-errors`
- `loadCompare`:
  - `chart-compare-delta`
- `upsertChart`:
  - общий lifecycle Chart.js: create/update/destroy
  - хранит конфиги в `state.chartConfigs`
  - применяет сценарии через `applyScenarioToChartConfig`
  - обновляет увеличенный график, если он связан с малым

### Компоненты увеличенных графиков

Увеличенный график создается не как отдельная страница, а inline DOM-блоком рядом с исходным графиком:

- `initChartExpandUi`
- `toggleExpandedChart`
- `renderExpandedChartClone`
- `renderExpandedChartByRanges`
- `buildChartConfigByRange`
- `setupExpandedGraphControls`
- `setupExpandedZoomControls`
- `collapseExpandedChart`

Состояние увеличенного графика хранится в:

```js
state.expandedChart = {
  sourceCanvasId,
  instance,
  compareInstance,
  containerEl,
  customRangeActive
}
```

Дополнительное состояние:

- `state.expandedRangesBySource`
- `state.expandedBucketBySource`
- `state.expandedEventFilterBySource`
- `state.expandedEventOptionsBySource`
- `state.expandedLatencyMetricBySource`
- `state.expandedStageLatencyEventMetricBySource`

## 2. Графики на вкладке "Обзор"

### Поток событий

- Название в DOM: `chart-events-count`
- Файл: `analytics-dashboard.js`
- Малый график: `loadOverview`
- Увеличенный график: `toggleExpandedChart`, `renderExpandedChartByRanges`, `buildChartConfigByRange`
- Тип Chart.js: `line`
- Данные:
  - `OverviewResponse.series[].count`
- API:
  - `GET {API_BASE}/overview`
  - для event-filter в увеличенном режиме может делать несколько запросов `/overview` по каждому `eventTypeCode`
- Props в классическом смысле нет. Конфиг строится из:
  - `mainParams`
  - `state.inlineCompare*`
  - `state.expanded*`
  - `options` в `buildChartConfigByRange`: `includeOverall`, `eventCodes`
- Фильтры:
  - `from`, `to`
  - `moduleCode`
  - `eventTypeCode`
  - `requestPath`
  - `bucketMinutes`
  - глобальный attribute/metric filter через `appendGlobalMetricFilterParams`
  - в увеличенном режиме: мультивыбор событий и "общая статистика"
- Сценарии: есть (`traffic_spike`, `traffic_drop`, `event_mix_shift`, плюс registry-переопределения)
- Режим сравнения: есть
  - `off`
  - `split`
  - `overlay`
- Bucket: есть, передается как `bucketMinutes`
- Zoom X/Y: есть только в увеличенном режиме, реализован CSS-размерами и scroll, не Chart.js zoom plugin
- Увеличенный график: есть

### Latency trend

- Название в DOM: `chart-latency`
- Файл: `analytics-dashboard.js`
- Малый график: `loadOverview`
- Увеличенный график: `renderExpandedChartByRanges`, `buildChartConfigByRange`
- Тип Chart.js: `line`
- Данные:
  - `OverviewResponse.series[].avgMs`
  - `OverviewResponse.series[].p95Ms`
  - `OverviewResponse.series[].p99Ms`
- API:
  - `GET {API_BASE}/overview`
  - в увеличенном режиме с выбранными событиями: несколько `/overview` с разными `eventTypeCode`
- Фильтры:
  - все фильтры `mainParams`
  - в увеличенном режиме: события, bucket, период, compare mode
  - выбор метрики для нескольких событий: `AVG`, `P95`, `P99`
- Сценарии: есть (`p95_growth`, `stable_load_degradation`, `avg_p95_p99_gap` и др.)
- Режим сравнения: есть
- Bucket: есть
- Zoom X/Y: есть в увеличенном режиме
- Увеличенный график: есть

Особенность: если выбран один event type в увеличенном фильтре, строятся три линии `AVG/P95/P99` только для этого события. Если выбрано несколько событий, строится одна выбранная latency-метрика по каждому событию.

### Error rate trend

- Название в DOM: `chart-error-rate`
- Файл: `analytics-dashboard.js`
- Малый график: `loadOverview`
- Увеличенный график: `renderExpandedChartByRanges`, `buildChartConfigByRange`
- Тип Chart.js: `line`
- Данные:
  - `OverviewResponse.series[].errorRate`, конвертация в проценты через `toPercentNumber`
- API:
  - `GET {API_BASE}/overview`
  - в event-filter увеличенного режима: несколько `/overview`
- Фильтры:
  - все фильтры `mainParams`
  - события в увеличенном режиме
  - bucket, период, compare mode
- Сценарии: есть (`error_growth`, `error_spike`, `recovery_after_fix` и др.)
- Режим сравнения: есть
- Bucket: есть
- Zoom X/Y: есть в увеличенном режиме
- Увеличенный график: есть

### KPI по типам событий

- Название в DOM: `chart-event-kpi`
- Файл: `analytics-dashboard.js`
- Малый график:
  - `loadOverview`
  - `renderMiniEventKpiFromOverview`
  - `buildEventKpiRows`
  - `buildEventKpiSingleChartConfig`
  - `buildEventKpiOverlayChartConfig`
- Увеличенный график:
  - `renderExpandedChartByRanges`
  - `loadExpandedEventKpiRowsForRange`
  - `fetchEventKpiRowsByRange`
- Тип Chart.js: `bar`
- Данные:
  - `OverviewResponse.eventBreakdown[]`
  - метрики: `count`, `p95Ms`, `errorRate`
- API:
  - `GET {API_BASE}/overview`
- Фильтры:
  - все фильтры `mainParams`
  - период/bucket в увеличенном режиме
  - отдельный event-filter в увеличенном режиме фактически не используется для KPI, потому что KPI сам является разрезом по событиям
- Сценарии: есть (`event_p95_degradation`, `event_load_growth`, `event_error_growth`, `rare_slow_event`)
- Режим сравнения:
  - есть, но с ограничением
  - в mini-версии `split` принудительно отображается как `overlay`
  - в увеличенном графике `split` доступен
- Bucket:
  - endpoint принимает `bucketMinutes`, но сам KPI строится по `eventBreakdown`, то есть bucket не влияет на разбиение столбцов; он влияет только на response metadata/series, если параллельно используется
- Zoom X/Y: есть в увеличенном режиме; для KPI ширина/высота дополнительно рассчитываются по количеству labels
- Увеличенный график: есть

### Этапы выполнения по слоям

- Название в DOM: `chart-stage-latency`
- Файл: `analytics-dashboard.js`
- Малый график: `loadStages`
- Увеличенный график: `renderExpandedChartByRanges`, `buildChartConfigByRange`
- Тип Chart.js: `bar`
- Данные:
  - `StageBreakdownResponse.stages[].avgMs`
  - `StageBreakdownResponse.stages[].p95Ms`
- API:
  - `GET {API_BASE}/stages`
  - в увеличенном режиме с выбранными событиями: несколько `/stages` по каждому `eventTypeCode`
- Фильтры:
  - все фильтры `mainParams`
  - события в увеличенном режиме
  - bucket, период, compare mode
  - выбор метрики для нескольких событий: `AVG` или `P95`
- Сценарии: есть (`layer_bottleneck`, `database_degradation`, `layer_avg_p95`)
- Режим сравнения: есть
- Bucket:
  - backend возвращает `bucketMinutes` и `series`, но малый stage latency использует агрегированный `stages`, не временную серию
  - bucket влияет на серверный расчет series и rollup path, но не на форму этого bar-графика по слоям
- Zoom X/Y: есть в увеличенном режиме
- Увеличенный график: есть

### Error rate / P95 по слоям

Сейчас это не один отдельный комбинированный компонент, а два отдельных canvas:

- `chart-stage-latency`: AVG/P95 по слоям
- `chart-stage-errors`: Error rate по слоям

Для `chart-stage-errors`:

- Файл: `analytics-dashboard.js`
- Малый график: `loadStages`
- Увеличенный график: `renderExpandedChartByRanges`, `buildChartConfigByRange`
- Тип Chart.js: `bar`
- Данные:
  - `StageBreakdownResponse.stages[].errorRate`
- API:
  - `GET {API_BASE}/stages`
- Фильтры:
  - все фильтры `mainParams`
  - события в увеличенном режиме
  - bucket, период, compare mode
- Сценарии: есть (`stage_error_growth`, `database_errors`)
- Режим сравнения: есть
- Bucket: поддержан параметром API, но bar-график использует агрегированный список `stages`
- Zoom X/Y: есть в увеличенном режиме
- Увеличенный график: есть

## 3. Как сейчас устроены сценарии

### Где описаны

Сценарии описаны во frontend:

- `CHART_SCENARIOS_BY_CANVAS`
- `ANALYTICS_SCENARIO_REGISTRY`
- маппинг глобального сценария на графики: `globalScenarioMapping`

Сценарии есть для:

- `chart-events-count`
- `chart-latency`
- `chart-error-rate`
- `chart-event-kpi`
- `chart-stage-latency`
- `chart-stage-errors`
- stage metric charts
- universal charts
- `chart-compare-delta`

### Как открывается меню сценариев

Для каждого canvas `initChartExpandUi` вызывает:

- `ensureChartActionsBar`
- `ensureChartScenarioPicker`

`ensureChartScenarioPicker` добавляет кнопку/попап сценариев в action bar графика. Для глобального сценария используется:

- `upgradeGlobalScenarioSelect`
- `.analytics-global-scenario-picker`

### Какие сценарии есть сейчас

Глобальные сценарии включают:

- `traffic_spike`
- `tail_latency`
- `error_burst`
- `release_compare`
- `layer_bottleneck`
- `error_without_load` / `errors_without_load`

Примеры сценариев графиков:

- Поток событий:
  - `traffic_spike`
  - `traffic_drop`
  - `event_mix_shift`
- Latency:
  - `p95_growth`
  - `stable_load_degradation`
  - `avg_p95_p99_gap`
- Error rate:
  - `error_growth`
  - `error_spike`
  - `recovery_after_fix`
- KPI по событиям:
  - `event_p95_degradation`
  - `event_load_growth`
  - `event_error_growth`
  - `rare_slow_event`
- Слои:
  - `layer_bottleneck`
  - `database_degradation`
  - `layer_avg_p95`
  - `stage_error_growth`
  - `database_errors`
- Compare delta:
  - `release_delta`
  - `latency_delta`

### Какие компоненты показывают подсказки/плашки/заголовки

- Help modal:
  - `openHelpModal`
  - `buildChartHelpHtml`
  - `renderScenarioHelpCards`
  - `openChartScenarioHelpModal`
  - `openGlobalScenarioHelpModal`
- Плашка сценария на графике:
  - `scenarioSummaryText`
  - `ensureScenarioSummaryEl`
  - `syncChartScenarioSummary`
  - `syncAllScenarioSummaries`
- Subtitle внутри Chart.js:
  - `applyScenarioToChartConfig` добавляет `config.options.plugins.subtitle`

### Как сценарий влияет на данные или отображение

Сценарий не передается в API и не меняет backend-запросы. Влияние только frontend:

- добавляет subtitle к графику;
- выделяет подходящие datasets через `borderWidth`, `pointRadius`, `order`;
- приглушает остальные datasets через alpha;
- сценарии с `compare`, `release`, `delta`, `before_after` могут включить overlay-сравнение через `shouldScenarioPreferCompareOverlay` и `applyInlineCompareMode(..., "overlay")`.

## 4. Верхняя панель увеличенного графика

Панель создается функцией `setupExpandedGraphControls(container, canvasId)`.

### События

- DOM:
  - `[data-event-popup-toggle]`
  - `[data-event-popup]`
  - `[data-event-codes]`
  - `[data-event-overall-toggle]`
- Поддерживается для:
  - `chart-events-count`
  - `chart-latency`
  - `chart-error-rate`
  - `chart-stage-latency`
  - `chart-stage-errors`
- Состояние:
  - `state.expandedEventFilterBySource[canvasId].includeOverall`
  - `state.expandedEventFilterBySource[canvasId].codes`
  - `state.expandedEventOptionsBySource[canvasId]`
- Пересчет:
  - `applyExpandedEventFilter`
  - `renderExpandedChartByRanges`
  - `buildChartConfigByRange`
- Зависимости:
  - текущие ranges из UI
  - `resolveExpandedBucket`
  - `getExpandedLatencyMetricMode`
  - `getExpandedStageLatencyEventMetricMode`

### Пресет

- DOM: `[data-expanded-preset]`
- Состояние:
  - `state.inlineComparePresetBySource[canvasId]`
  - `state.inlineComparePresetOverriddenBySource[canvasId]`
  - `state.expandedRangesBySource[canvasId]`
- Пересчет:
  - `expandedRangesFromPresetNow`
  - `writeRangesToUi`
  - `applyInlineComparePresetToChart`
  - `applyStoredExpandedRangesToCharts`
  - `renderExpandedChartClone`
  - при активном event-filter: `rerenderExpandedEventFilterIfNeeded`

### Даты from/to

- DOM:
  - `[data-range='after-from']`
  - `[data-range='after-to']`
  - `[data-range='after-from-compare']`
  - `[data-range='after-to-compare']`
  - элементы `before-*` частично есть в старой функции `setupExpandedCompareRangeControls`, но основной новый toolbar использует расчет before из after
- Состояние:
  - `state.expandedRangesBySource[canvasId]`
- Пересчет:
  - debounce `scheduleApplyRanges` на 180 ms
  - `applyRangesFromInputs`
  - `normalizeCompareRangesByAfter`
  - `buildChartConfigByRange`
  - `renderExpandedChartByRanges`

### Bucket

- DOM: `[data-expanded-bucket]`
- Состояние:
  - `state.expandedBucketBySource[canvasId]`
  - fallback: `refs.bucket.value`
- Пересчет:
  - `resolveExpandedBucket`
  - `applyInlineComparePresetToChart`
  - `applyStoredExpandedRangesToCharts`
  - `refreshExpandedEventOptions`
  - `renderExpandedChartClone`
  - `rerenderExpandedEventFilterIfNeeded`

### Режим сравнения

- DOM: `[data-expanded-compare-mode]`
- Состояние:
  - для обычных графиков: `state.inlineCompareModeBySource[canvasId]`
  - override flag: `state.inlineCompareModeOverriddenBySource[canvasId]`
  - для KPI есть отдельная логика raw/mini/expanded через `resolveEventKpiCompareModeRaw`
- Пересчет:
  - `applyInlineCompareMode`
  - `enableInlineCompareLayout` / `disableInlineCompareLayout`
  - `applyInlineComparePresetToChart`
  - `applyStoredExpandedRangesToCharts`
  - `rebuildExpandedChartForCurrentMode`

### Выбор метрики

- Latency event metric:
  - DOM: `[data-expanded-latency-metric]`
  - состояние: `state.expandedLatencyMetricBySource[canvasId]`
  - значения: `avg`, `p95`, `p99`
  - применяется для `chart-latency` при нескольких выбранных events

- Stage latency event metric:
  - DOM: `[data-expanded-stage-latency-event-metric]`
  - состояние: `state.expandedStageLatencyEventMetricBySource[canvasId]`
  - значения: `avg`, `p95`
  - применяется для `chart-stage-latency` при нескольких выбранных events

### Reset

- DOM: `[data-expanded-reset]`
- Состояние:
  - сбрасывает local scenario
  - сбрасывает compare override
  - сбрасывает preset override
  - удаляет `state.expandedRangesBySource[canvasId]`
  - удаляет `state.expandedBucketBySource[canvasId]`
  - для event-filter внутри handler дополнительно сбрасывает `includeOverall/codes`
- Пересчет:
  - `resetChartLocalOverride`
  - `applyInlineComparePresetToChart`
  - `applyStoredExpandedRangesToCharts`
  - `renderExpandedChartClone`

### Close

- DOM: `.analytics-expanded-close-btn`
- Логика:
  - создается в `toggleExpandedChart`
  - переносится в actions внутри `setupExpandedGraphControls`
  - закрывает через `collapseExpandedChart`

### Zoom X/Y

- DOM:
  - `.analytics-expanded-zoom-range-x`
  - `.analytics-expanded-zoom-range-y`
- Состояние:
  - отдельного бизнес-state почти нет; значения живут в input range
  - для universal charts синхронизируется с `state.universalZoomBaseByCanvas`
- Пересчет:
  - `setupExpandedZoomControls`
  - `applyZoomX`
  - `applyZoomY`
  - `chartInstance.resize()`
  - `chartInstance.update("none")`

## 5. Режим сравнения

### Где реализована логика

Основные функции:

- `isValidInlineCompareMode`
- `normalizeCompareMode`
- `setGlobalCompareMode`
- `resolveGlobalInlineCompareMode`
- `resolveInlineCompareMode`
- `resolveExpandedCompareMode`
- `applyInlineCompareMode`
- `applyGlobalCompareToAllCharts`
- `applyGlobalBeforeRangeToAllCharts`
- `applyInlineComparePresetToChart`
- `applyStoredExpandedRangesToCharts`
- `enableInlineCompareLayout`
- `disableInlineCompareLayout`

Состояние:

- `state.globalCompareMode`
- `state.globalCompareEnabled`
- `state.inlineCompareEnabled`
- `state.inlineCompareCanvasBySource`
- `state.inlineCompareModeBySource`
- `state.inlineCompareModeOverriddenBySource`
- `state.inlineCompareGhostBySource`
- `state.expandedRangesBySource`

### Выключено

- `mode = "off"`
- `state.globalCompareEnabled = false`
- split layout удаляется через `disableInlineCompareLayout`
- baseline/compare canvas уничтожается
- графики строятся по одному диапазону `from/to`

### Раздельно

- `mode = "split"`
- для обычных overview-графиков создается дополнительный canvas:
  - `${canvasId}-compare-inline`
  - связь хранится в `state.inlineCompareCanvasBySource[canvasId]`
- layout создается `enableInlineCompareLayout`
- данные строятся двумя независимыми запросами:
  - before range
  - after range
- для overview:
  - `loadOverview` делает pair requests на `/overview`
- для stages:
  - `loadStages` делает pair requests на `/stages`
- для expanded:
  - `renderExpandedChartByRanges` создает `state.expandedChart.compareInstance` и `state.expandedChart.instance`

Важное ограничение: mini `chart-event-kpi` не показывает split как два мини-графика. `resolveMiniKpiCompareMode("chart-event-kpi")` превращает `split` в `overlay`. Реальный split доступен только в expanded KPI.

### Наложением

- `mode = "overlay"`
- split canvas обычно не нужен
- before datasets превращаются в ghost datasets:
  - `buildGhostDataset`
  - dashed line для line charts
  - полупрозрачный bar для bar charts
- afterConfig получает дополнительные datasets before периода
- для KPI overlay используется специализированный `buildEventKpiOverlayChartConfig`

### Почему при смене event type в режиме "Раздельно" график может не перерисовываться

Потенциальные причины в текущей архитектуре:

1. Есть два источника истины режима:
   - `resolveInlineCompareMode(canvasId)`
   - `state.inlineCompareEnabled[canvasId]`

   `loadOverview` и `loadStages` решают, нужны ли pair requests для split, через `state.inlineCompareEnabled[...]`, а не напрямую через `resolveInlineCompareMode`. Если mode уже изменен, а layout/state еще не синхронизирован, загрузка может пойти по старой ветке.

2. При изменении верхнего `eventType` вызывается `submitMainFilters`, который делает:
   - `clearAllChartLocalOverrides`
   - `reloadAll`
   - при global compare: `applyGlobalCompareToAllCharts`
   - `syncExpandedGraphFiltersFromTop`

   Но `clearAllChartLocalOverrides` сбрасывает ranges/bucket/compare overrides, а `state.expandedEventFilterBySource` явно не очищает. Если в expanded-графике ранее были выбраны события в локальном меню, `getExpandedEventRenderOptions` может продолжить использовать старые `eventCodes`, и смена верхнего event type не будет видна как ожидается.

3. `syncExpandedGraphFiltersFromTop` обновляет даты в controls и вызывает `applyStoredExpandedRangesToCharts`, но не вызывает `refreshExpandedEventOptions`. Поэтому список доступных событий и локальные selected values в expanded toolbar могут отстать от нового верхнего фильтра.

4. Часть путей после изменения состояния вызывает `renderExpandedChartClone`, то есть перерисовывает expanded-график из текущего `state.chartConfigs`, а не всегда строит конфиг заново по API. Если source chart не был обновлен или был обновлен без compare canvas, expanded может сохранить старую визуальную структуру.

5. Для `chart-event-kpi` есть отдельный snapshot:
   - `state.eventKpiMiniRowsSnapshot`
   - `getEventKpiMiniRowsSnapshotForRanges`

   Snapshot сравнивает ranges и raw compare mode, но бизнес-фильтры не выделены отдельным ключом. Обычно snapshot обновляется в `loadOverview`, но это место чувствительно к порядку async-операций.

Неполные зависимости/зоны риска:

- `syncExpandedGraphFiltersFromTop` не учитывает локальный event-filter.
- `hasChartLocalOverride` учитывает scenario, compare, preset, bucket, range, но не учитывает `state.expandedEventFilterBySource`.
- `loadOverview` / `loadStages` завязаны на `state.inlineCompareEnabled`, а overlay/split mode хранится отдельно.
- `refreshExpandedEventOptions` вызывается внутри expanded controls при изменении bucket/period/event filter, но не является общей частью синхронизации с верхней панелью.

## 6. Bucket и zoom

### Где задается bucket

Верхний bucket:

- DOM: `#analytics-bucket`
- frontend params: `mainParams`
- query param: `bucketMinutes`

Expanded bucket:

- DOM: `[data-expanded-bucket]`
- состояние: `state.expandedBucketBySource[canvasId]`
- fallback: `refs.bucket.value`
- resolver: `resolveExpandedBucket`

Universal/stage metrics имеют свои bucket controls, но для графиков обзора важны `refs.bucket` и expanded bucket.

### Что означает bucket

`bucketMinutes` - длительность временного bucket в минутах. Если значение не задано, backend выбирает bucket автоматически:

- до 360 минут периода: `5`
- до 1440 минут: `15`
- до 4320 минут: `60`
- до 10080 минут: `180`
- до 44640 минут: `360`
- больше: `1440`

Логика: `AnalyticsInsightsService.resolveBucketMinutes`.

### Где рассчитывается шаг агрегации

Raw path:

- `AnalyticsInsightsService.buildSeries`
- `stepSeconds = bucketMinutes * 60`
- `floorToBucket`

Rollup path:

- `AnalyticsTimeRollupService.seriesFromPoints`
- `stepSeconds = bucketMinutes * 60`
- `floorToBucket`
- `AnalyticsAccumulator`

Rollup используется, если нет requestPath, metric/attribute-фильтров и включены rollup-таблицы.

### Как формируются точки графика

Backend возвращает:

```java
TimeSeriesPointDto(
  Instant time,
  long count,
  BigDecimal avgMs,
  BigDecimal p95Ms,
  BigDecimal p99Ms,
  BigDecimal errorRate
)
```

Frontend:

- берет `data.series`
- labels: `formatTime(point.time)`
- datasets:
  - count: `point.count`
  - latency: `point.avgMs`, `point.p95Ms`, `point.p99Ms`
  - error: `toPercentNumber(point.errorRate)`
- ограничивает точки через `downsampleSeries(..., MAX_CHART_POINTS)`

### Как формируются labels оси X

- Backend time:
  - `AnalyticsSeriesTime.displayTimeForBucket(bucketStart, to, stepSeconds)`
  - для последнего bucket может вернуть `to`, а не `bucketStart`
- Frontend label:
  - `formatTime`
  - `formatDateTimeAxisPattern`
  - формат: `dd.MM.yy HH:mm`

### Hover/tooltip

Общие line options:

- `baseChartOptions`
- `interaction: { intersect: false, mode: "index" }`
- tooltip:
  - `mode: "index"`
  - `intersect: false`
  - фильтрует нулевые/нечисловые значения
  - label строится через `resolveTooltipNumeric`

Bar options:

- `barChartOptions`
- `interaction: { intersect: true, mode: "nearest" }`
- tooltip:
  - `mode: "nearest"`
  - `intersect: true`

Почему tooltip может странно привязываться к точкам:

- `mode: "index"` у line charts показывает все datasets по индексу label, а не ближайшую физическую точку конкретной линии.
- После `downsampleSeries` labels и dataset values искусственно прореживаются; индекс остается корректным внутри sampled arrays, но визуально точка может оказаться не там, где ожидал пользователь.
- В multi-event expanded режиме labels берутся из первого ответа (`responses[0]`), а datasets строятся по своим responses. Если backend вернул разные bucket/time наборы для разных eventType, tooltip сопоставляет values по индексу, а не по timestamp.
- `filter` скрывает нулевые значения, поэтому tooltip может показывать не все datasets, хотя индекс общий.
- Последний bucket может иметь label равный `to`, а остальные - `bucketStart`, из-за `displayTimeForBucket`.

### Zoom X/Y

Chart.js zoom plugin не используется. В шаблонах подключен только:

- `chart.js@4.4.3` через CDN

Zoom реализован вручную:

- `setupExpandedZoomControls`
- X:
  - меняет `width` / `minWidth` `.analytics-expanded-zoom-host`
  - для KPI рассчитывает ширину через `resolveKpiChartWidth`
  - вызывает `chart.resize()` и `chart.update("none")`
- Y:
  - меняет `height` / `min-height` / `max-height` `.analytics-chart-wrap-expanded`
  - для KPI рассчитывает высоту по labels через `calcKpiContentHeight`
- При split синхронизируется scroll двух expanded panels.

## 7. Состояние периода

### Где хранятся from/to

Верхний период:

- DOM:
  - `#analytics-from`
  - `#analytics-to`
- refs:
  - `refs.from`
  - `refs.to`
- query params:
  - `from`
  - `to`

Дополнительные периоды:

- raw events:
  - `#events-from`
  - `#events-to`
  - sync state: `state.eventsRangeSynced`
- stage metrics:
  - `#stage-metric-from-a`
  - `#stage-metric-to-a`
  - sync state: `state.stageMetricRangeSynced`
- stage text:
  - `#stage-text-from-a`
  - `#stage-text-to-a`
  - sync state: `state.stageTextRangeSynced`
- expanded charts:
  - `state.expandedRangesBySource[canvasId]`
- global compare:
  - `state.globalCompareBeforeCustom`
  - `resolveGlobalBeforeRange`

### Как работает preset

Основные функции:

- `applyQuickRangePreset`
- `buildQuickRangeFromDate`
- `quickRangeCodeToDurationMs`
- `inferQuickRangeCodeFromValues`
- `syncQuickRangeSelectFromRange`

Значения:

- `15m`
- `30m`
- `1h`
- `3h`
- `6h`
- `12h`
- `24h`
- `1w`
- `1mo`
- `3mo`
- `6mo`
- `1y`
- `all`

Для `all` используется:

- `ensureAllTimeRangeLoaded`
- `GET {API_BASE}/range-start`
- `state.allTimeRange`

### Как меняется период при выборе дат

Изменение `#analytics-from` / `#analytics-to`:

- обновляет quick preset через `syncQuickRangeSelectFromRange`
- обновляет default compare range через `initDefaultCompareRange`
- синхронизирует stage metrics ranges
- вызывает `submitMainFilters`

`submitMainFilters`:

- сбрасывает локальные overrides графиков;
- синхронизирует периоды дочерних панелей;
- вызывает `reloadAll`;
- при включенном global compare вызывает `applyGlobalCompareToAllCharts`;
- синхронизирует открытый expanded chart через `syncExpandedGraphFiltersFromTop`.

### Есть ли общий state вкладки "Обзор"

Единого объекта вида `overviewState` нет. Состояние распределено между:

- DOM inputs (`refs.from`, `refs.to`, `refs.bucket`, `refs.moduleType`, `refs.eventType`)
- global `state`
- chart-local maps (`expandedRangesBySource`, `inlineCompareModeBySource`, `expandedBucketBySource`)

### Можно ли централизованно добавить activeAnalysisInterval

Да. Наиболее безопасно добавить вычисляемый объект на уровне main filter:

```js
activeAnalysisInterval = {
  fromLocal,
  toLocal,
  fromIso,
  toIso,
  bucketMinutes,
  preset,
  compareMode,
  beforeFromLocal,
  beforeToLocal
}
```

Лучшее место интеграции:

- рядом с `mainParams`
- `buildMainRangeKey`
- `resolveSafeAfterRangeFromTop`
- `resolveGlobalBeforeRange`
- `expandedRangesFromTopFilter`

Компоненты, которым нужен этот интервал:

- `loadOverview`
- `loadStages`
- `loadStageMetrics`
- `loadUniversal`
- `loadEvents`
- `loadCompare`
- `setupExpandedGraphControls`
- `applyInlineComparePresetToChart`
- `applyStoredExpandedRangesToCharts`

Важно: вводить постепенно, не заменяя сразу все существующие local states.

## 8. Backend/API endpoints

### `GET /analytics/api/overview` и `/analytics-admin/api/overview`

Controller: `AnalyticsOverviewController.overview`

Параметры:

- `from`
- `to`
- `moduleCode`
- `eventTypeCode`
- `requestPath`
- `filterMetricTypeCode`
- `filterMetricValue`
- `filterMetricMinValue`
- `filterMetricMaxValue`
- `filterAttributeCode`
- `filterAttributeValue`
- `filterAttributeMinValue`
- `filterAttributeMaxValue`
- `bucketMinutes`

Ответ: `OverviewResponse`

- `from`
- `to`
- `bucketMinutes`
- `totals`
- `eventBreakdown`
- `series`

Используется для:

- Поток событий
- Latency trend
- Error rate trend
- KPI по типам событий
- event options в expanded toolbar

Поддерживает фильтрацию по:

- from/to: да
- event type: да, одиночный `eventTypeCode`
- bucket: да
- metric/attribute filter: да
- compare mode: нет отдельного параметра, сравнение собирается отдельными запросами

Backend-доработки для будущего UI:

- Для одиночных/нескольких event types сейчас frontend делает несколько запросов. Backend already supports single `eventTypeCode`, но не batch list для overview.
- Для compare есть `/overview/compare`, но frontend часто строит compare через парные `/overview`. Можно оставить frontend-only, но централизованный compare endpoint снизит риск рассинхронизации.

### `GET /analytics/api/overview/compare`

Controller: `AnalyticsOverviewController.overviewCompare`

Параметры:

- `beforeFrom`
- `beforeTo`
- `afterFrom`
- `afterTo`
- те же фильтры, что `/overview`
- `bucketMinutes`

Ответ: `OverviewCompareResponse`

- `before: OverviewResponse`
- `after: OverviewResponse`

Сейчас для основных overview mini charts frontend чаще делает парные `/overview`, а не использует этот endpoint напрямую.

### `GET /analytics/api/stages` и `/analytics-admin/api/stages`

Controller: `AnalyticsStageController.stages`

Параметры:

- `from`
- `to`
- `moduleCode`
- `eventTypeCode`
- `requestPath`
- metric/attribute filters
- `bucketMinutes`

Ответ: `StageBreakdownResponse`

- `from`
- `to`
- `bucketMinutes`
- `stages`
- `series`

Используется для:

- `chart-stage-latency`
- `chart-stage-errors`
- таблица этапов

Поддерживает:

- from/to: да
- event type: да, одиночный
- bucket: да
- metric/attribute filters: да
- compare mode: нет параметра, сравнение через парные запросы или `/stages/compare`

### `GET /analytics/api/stages/compare`

Controller: `AnalyticsStageController.stagesCompare`

Параметры:

- `beforeFrom`
- `beforeTo`
- `afterFrom`
- `afterTo`
- те же фильтры, что `/stages`
- `bucketMinutes`

Ответ: `StageBreakdownCompareResponse`

- `before`
- `after`

Сейчас frontend для overview stage split чаще делает pair requests на `/stages`.

### `GET /analytics/api/stage-metrics`

Controller: `AnalyticsStageController.stageMetrics`

Параметры:

- `from`
- `to`
- `moduleCode`
- `eventTypeCode`
- `requestPath`
- `stageTypeCode`
- `metricTypeCode`
- metric/attribute filters
- `bucketMinutes`
- `includeSummaries`
- `includeTopValues`
- `includeSeries`

Ответ: `StageMetricResponse`

Используется не для базовых графиков "Обзора" из списка, а для блока stage metrics.

### `GET /analytics/api/universal`

Controller: `AnalyticsUniversalController.universal`

Параметры:

- `from`
- `to`
- `allTime`
- `moduleCode`
- `eventTypeCode` как `List<String>`
- `requestPath`
- `attributeCode`
- `attributeValue`
- metric/attribute filters
- `stageTypeCode`
- `bucketMinutes`
- `includeEventStageBreakdown`

Ответ: `UniversalResponse`

Важно: universal уже поддерживает список `eventTypeCode`, в отличие от overview/stages.

### `GET /analytics/api/compare`

Controller: `AnalyticsCompareController.compare`

Параметры:

- `baselineFrom`
- `baselineTo`
- `targetFrom`
- `targetTo`
- `moduleCode`
- `eventTypeCode`
- `requestPath`

Ответ: `CompareResponse`

Используется для:

- блок "Сравнение до/после"
- `chart-compare-delta`

Не поддерживает:

- `bucketMinutes`
- metric/attribute filters
- compare mode как параметр

### `GET /analytics/api/filter-options`

Controller: `AnalyticsFilterOptionsController.filterOptions`

Параметры:

- `from`
- `to`
- `moduleCode`
- `eventTypeCode`
- `requestPath`
- `attributeCode`

Ответ: `FilterOptionsResponse`

Используется для:

- options event type
- attribute types/values
- scoped filters

### `GET /analytics/api/range-start`

Controller: `AnalyticsRangeController.rangeStart`

Ответ: `RangeStartResponse`

- `from`

Используется для preset `all`.

### Нужны ли backend-доработки

Для базовой реализации синхронизации UI, интервалов, bucket и zoom многое можно сделать на frontend.

Backend-доработки стоит планировать, если нужно:

- единый compare endpoint для всех графиков, чтобы не делать парные запросы вручную;
- batch `eventTypeCode` для `/overview` и `/stages`, чтобы multi-event expanded не делал N запросов;
- endpoint, который возвращает aligned time labels для нескольких event types;
- явный contract для bucket metadata: start/end/label, а не только `time`;
- поддержка metric/attribute filters в `/compare`, если блок compare delta должен соответствовать верхним фильтрам полностью.

## 9. Риски изменений

### Общие компоненты

Самые чувствительные общие функции:

- `mainParams`
- `reloadAll`
- `loadOverview`
- `loadStages`
- `upsertChart`
- `baseChartOptions`
- `barChartOptions`
- `applyInlineCompareMode`
- `applyGlobalCompareToAllCharts`
- `applyInlineComparePresetToChart`
- `applyStoredExpandedRangesToCharts`
- `renderExpandedChartByRanges`
- `buildChartConfigByRange`
- `setupExpandedGraphControls`
- `setupExpandedZoomControls`
- `applyScenarioToChartConfig`

Их нельзя менять точечно без проверки всех вкладок, потому что они используются не только "Обзором", но и:

- Universal
- Stage metrics
- Raw events
- Compare delta
- Expanded chart lifecycle
- Scenario/help UI

### Что нельзя менять без проверки

- Формат `OverviewResponse.series` и `StageBreakdownResponse.stages`.
- Семантику `bucketMinutes`.
- `formatTime` / labels, потому что tooltip и downsampling завязаны на индекс labels.
- `upsertChart`, потому что через него проходят все Chart.js instances.
- `state.inlineCompareEnabled` vs `state.inlineCompareModeBySource`; это сейчас два разных механизма.
- `event-kpi` mini layout, потому что у него отдельные width/height расчеты и snapshot.
- `Chart.js` options tooltip/decimation, потому что они общие для line/bar charts.

### Где лучше добавлять новую логику

Рекомендуемые точки расширения:

- Новый общий interval model:
  - рядом с `mainParams`
  - `resolveSafeAfterRangeFromTop`
  - `resolveGlobalBeforeRange`
  - `expandedRangesFromTopFilter`
- Синхронизация expanded event filter:
  - отдельная функция рядом с `syncExpandedGraphFiltersFromTop`
  - не внутри `loadOverview`
- Bucket normalization:
  - отдельный resolver, который возвращает `{source, bucketMinutes, isAuto}`
  - не размазывать чтение `refs.bucket` по новым местам
- Compare:
  - сначала выровнять mode/layout state (`inlineCompareEnabled` и `inlineCompareModeBySource`)
  - затем менять построение данных
- Multi-event series:
  - лучше добавить alignment по timestamp перед Chart.js dataset assembly

### Тесты и ручные проверки после изменений

Минимальный набор ручных проверок:

1. Верхний фильтр:
   - смена `from/to`
   - смена preset
   - смена bucket
   - смена module
   - смена event type
   - reset фильтров

2. Каждый график обзора:
   - обычный режим
   - expanded режим
   - смена bucket в expanded
   - смена периода в expanded
   - reset expanded
   - close/open expanded повторно

3. Compare:
   - off -> split -> off
   - off -> overlay -> off
   - split -> overlay
   - overlay -> split
   - global compare и local compare override
   - смена event type при active split
   - смена event type при opened expanded split

4. Event filter в expanded:
   - overall only
   - один event
   - несколько events
   - смена event type в верхней панели после локального выбора events

5. KPI по типам событий:
   - mini single
   - mini overlay
   - expanded off
   - expanded split
   - expanded overlay
   - большое количество event labels

6. Tooltip:
   - line charts после downsample
   - multi-event expanded
   - bar charts
   - нулевые значения

7. Сценарии:
   - global scenario
   - local chart scenario
   - reset scenario
   - scenario that enables overlay

Автотесты, которые стоит добавить:

- unit tests для interval/bucket resolver на frontend, если будет вынесен в чистые функции;
- backend tests для `resolveBucketMinutes`;
- integration tests для `/overview`, `/stages`, `/overview/compare`, `/stages/compare`;
- Playwright smoke test для:
  - open dashboard
  - change event type
  - enable split
  - expand chart
  - change bucket
  - verify canvas updated / no blank canvas.

## Рекомендуемые этапы внедрения

1. Зафиксировать единый `activeAnalysisInterval` и использовать его только для чтения, без замены всех старых state за один шаг.
2. Развести compare mode и compare layout: сделать один resolver, который явно возвращает `off/split/overlay`, `needsCompareCanvas`, `needsGhostDatasets`.
3. Добавить очистку/синхронизацию `expandedEventFilterBySource` при изменении верхнего event type/module/range.
4. Выровнять multi-event time series по timestamp, а не по индексу первого ответа.
5. Нормализовать bucket UI: различать `auto` и конкретное число, показывать фактически выбранный backend bucket.
6. После стабилизации frontend рассмотреть backend batch endpoints для multi-event overview/stages и единые compare endpoints.

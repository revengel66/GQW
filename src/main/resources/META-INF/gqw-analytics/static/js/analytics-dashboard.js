(function () {
    const inferredBase = window.location.pathname.startsWith("/analytics-admin")
        ? "/analytics-admin/api"
        : "/analytics/api";
    const API_BASE = (window.analyticsApiBase || inferredBase).replace(/\/+$/, "");
    const state = {
        charts: {},
        chartConfigs: {},
        kpiFullChartConfigs: {},
        kpiMiniTopStatsByCanvas: {},
        kpiRuntimeMetaBySource: {},
        miniKpiCompareModeBySource: {},
        miniKpiCompareModeOverriddenBySource: {},
        expandedCompareModeBySource: {},
        expandedCompareModeOverriddenBySource: {},
        inlineCompareEnabled: {},
        inlineCompareCanvasBySource: {},
        inlineCompareModeBySource: {},
        inlineCompareModeOverriddenBySource: {},
        inlineComparePresetBySource: {},
        inlineComparePresetOverriddenBySource: {},
        inlineCompareGhostBySource: {},
        globalCompareMode: "off",
        globalCompareEnabled: false,
        globalComparePreset: "",
        globalCompareBeforeCustom: false,
        globalCompareNoDataWarningKey: "",
        stageMetricCompareMode: "off",
        stageTextCompareMode: "off",
        globalAttrMetaByCode: {},
        globalMetricMetaByCode: {},
        globalMetricRefreshRequestId: 0,
        globalMetricScopeSignature: "",
        globalScenarioCode: "",
        scenarioBySource: {},
        scenarioOverriddenBySource: {},
        chartScenarioBySource: {},
        chartScenarioBaseConfigs: {},
        authRedirectInProgress: false,
        lastMainRangeKey: "",
        mainReloadRequestId: 0,
        expandedRangesBySource: {},
        expandedBucketBySource: {},
        expandedChart: {
            sourceCanvasId: "",
            instance: null,
            compareInstance: null,
            containerEl: null,
            customRangeActive: false
        },
        dictionaries: {
            eventTypes: [],
            stageTypes: [],
            stageMetricTypes: [],
            eventAttributeTypes: []
        },
        stageMetricSelectedCodes: [],
        stageMetricTextSelectedCodes: [],
        stageTextRangeSynced: {
            fromA: "",
            toA: "",
            fromB: "",
            toB: ""
        },
        stageMetricFilterKey: "",
        stageMetricPrimaryFilterKey: "",
        stageMetricsRequestId: 0,
        stageMetricsAbortController: null,
        stageMetricSeriesCache: new Map(),
        stageMetricPayloadCache: new Map(),
        stageMetricPayloadPromiseByKey: new Map(),
        stageMetricPerfCurrent: null,
        stageMetricPendingPerfAction: "",
        stageMetricRangeSynced: {
            fromA: "",
            toA: "",
            fromB: "",
            toB: ""
        },
        eventsRangeSynced: {
            from: "",
            to: ""
        },
        metricHelpByCode: {},
        globalLoadingDepth: 0,
        panelLoadingDepthByElement: new WeakMap(),
        sectionLocalLoadingDepthByElement: new WeakMap(),
        mainFiltersSubmitting: false,
        mainFiltersSubmitPending: false,
        universalRequestId: 0,
        universalEventScopeCacheKey: "",
        universalEventScopeCachePayload: null,
        universalEventScopeCachePromiseKey: "",
        universalEventScopeCachePromise: null,
        universalAnalysisMode: "overall",
        universalMetricSelectionBeforeSingle: [],
        universalMetricSingleForced: false,
        universalPayloadCacheByKey: new Map(),
        universalPayloadPromiseByKey: new Map(),
        eventKpiMiniLoadId: 0,
        eventKpiMiniRenderLoadId: 0,
        eventKpiMiniRenderPromise: null,
        eventKpiMiniRenderResolve: null,
        eventKpiFirstLoadCompleted: false,
        eventKpiMiniRowsSnapshot: null,
        universalZoomBaseByCanvas: {},
        universalAllTime: false,
        allTimeRange: null,
        expandedEventOptionsBySource: {},
        eventsPage: 0,
        eventsSize: 15,
        eventsHasMore: false,
        cardLoaderHostsByScope: {},
        sectionLoaderTokens: {},
        sectionLoaderScopes: {},
        sectionLoaderHostsByScope: {},
        sectionLoaderBoundsByScope: {}
    };
    const MAX_CHART_POINTS = 220;
    const STAGE_METRIC_SERIES_CACHE_LIMIT = 200;
        const HELP_TEXTS = {
        "kpi-total-events": "Общее число событий за выбранный период. Рост при стабильной ошибке обычно означает рост нагрузки.",
        "kpi-avg-ms": "Среднее время обработки события. Удобно отслеживать общий тренд производительности.",
        "kpi-p95-ms": "95% запросов быстрее этого значения. Главный индикатор пользовательского опыта при нагрузке.",
        "kpi-p99-ms": "99% запросов быстрее этого значения. Показывает редкие, но самые тяжелые случаи.",
        "kpi-error-rate": "Доля событий с ошибкой. Смотрите вместе с количеством событий и HTTP-кодами.",
        "kpi-errors": "Абсолютное число событий с ошибкой за период.",
        "chart-events-count": "График показывает интенсивность трафика по времени. Пики указывают на нагрузку или пользовательские всплески.",
        "chart-latency": "Линии AVG/P95/P99 отражают скорость обработки. Если P95/P99 растут быстрее AVG, есть деградация хвоста распределения.",
        "chart-error-rate": "Доля ошибок по времени. Ищите всплески и сверяйте их с конкретными событиями в Raw.",
        "chart-event-kpi": "Сравнение событий между собой: count, P95 и error rate. Помогает выявить самые рискованные типы событий.",
        "chart-stage-latency": "Время по слоям (CONTROLLER/SERVICE/DATABASE). Показывает, где тратится основная часть времени.",
        "chart-stage-errors": "Ошибки по слоям. Если растет DATABASE, чаще всего проблема в SQL/соединениях/таймаутах.",
        "chart-stage-metric-series": "Временная динамика метрик этапа. При разных единицах график нормализуется в проценты от максимума по каждой метрике.",
        "chart-stage-metric-text": "График показывает распределение выбранной текстовой метрики по наиболее частым значениям. Используйте его, чтобы увидеть, какие URL, HTTP-методы, статусы, traceId или другие текстовые признаки встречаются чаще всего. В режиме сравнения можно сопоставить распределение значений в периодах «До» и «После».",
        "chart-stage-metric-top-values": "Для одной метрики показывает top значений, для нескольких — сравнение P95. Удобно искать аномалии относительно P95/AVG.",
        "analytics-stage-table": "Сводка по этапам: count/avg/p95/p99/error rate. Приоритизируйте оптимизации по p95/p99 и error rate.",
        "analytics-stage-metric-table": "Агрегированная статистика метрик этапов и top значений. Используйте для поиска аномалий на уровне инфраструктуры.",
        "analytics-events-table": "Raw-события для расследования инцидентов: фильтруйте по ошибкам, path, атрибутам, trace и метрикам.",
        "chart-compare-delta": "Сравнение до/после между двумя периодами. Отрицательная дельта по latency/error rate обычно означает улучшение."
    };
    const CHART_HELP_TARGETS = new Set([
        "chart-events-count",
        "chart-latency",
        "chart-error-rate",
        "chart-event-kpi",
        "chart-stage-latency",
        "chart-stage-errors",
        "chart-stage-metric-series",
        "chart-stage-metric-text",
        "chart-stage-metric-top-values",
        "chart-compare-delta"
    ]);
    const INLINE_COMPARE_CHART_IDS = new Set([
        "chart-events-count",
        "chart-latency",
        "chart-error-rate",
        "chart-event-kpi",
        "chart-stage-latency",
        "chart-stage-errors"
    ]);
    const EXPANDED_EVENT_FILTER_CHART_IDS = new Set([
        "chart-events-count",
        "chart-latency",
        "chart-error-rate",
        "chart-stage-latency",
        "chart-stage-errors"
    ]);
    const EXPANDED_SINGLE_EVENT_FILTER_CHART_IDS = new Set([]);
    const NO_EXPAND_CHART_IDS = new Set([
        "chart-universal-event-kpi"
    ]);
    const METRIC_EXPANDED_CONTROLLESS_CHART_IDS = new Set([
        "chart-stage-metric-series",
        "chart-stage-metric-series-compare",
        "chart-stage-metric-text",
        "chart-stage-metric-text-compare",
        "chart-stage-metric-top-values",
        "chart-stage-metric-top-values-compare"
    ]);
    const STAGE_METRIC_PRIMARY_CANVAS_IDS = new Set([
        "chart-stage-metric-series",
        "chart-stage-metric-text"
    ]);
    const STAGE_METRIC_COMPARE_CANVAS_BY_SOURCE = {
        "chart-stage-metric-series": "chart-stage-metric-series-compare",
        "chart-stage-metric-text": "chart-stage-metric-text-compare"
    };
    const STAGE_METRIC_SOURCE_CANVAS_BY_COMPARE = Object.fromEntries(
        Object.entries(STAGE_METRIC_COMPARE_CANVAS_BY_SOURCE).map(([source, compare]) => [compare, source])
    );
    const UNIVERSAL_COMPARE_CHART_IDS = new Set([
        "chart-universal-timeline",
        "chart-universal-stages",
        "chart-universal-event-kpi"
    ]);
    const UNIVERSAL_COMPARE_FOLLOWS_GLOBAL = true;
    const QUICK_RANGE_CUSTOM_LABEL = "—";
    const QUICK_RANGE_MATCH_TOLERANCE_MS = 60_000;
    const INLINE_COMPARE_PRESET_OPTIONS = [
        {value: "15m", label: "15м"},
        {value: "30m", label: "30м"},
        {value: "1h", label: "1ч"},
        {value: "3h", label: "3ч"},
        {value: "6h", label: "6ч"},
        {value: "12h", label: "12ч"},
        {value: "24h", label: "24ч"},
        {value: "1w", label: "1н"},
        {value: "1mo", label: "1мес"},
        {value: "3mo", label: "3мес"},
        {value: "6mo", label: "6мес"},
        {value: "1y", label: "1г"},
        {value: "all", label: "Все время"}
    ];
    const INLINE_COMPARE_MODE_OPTIONS = [
        {value: "off", label: "\u0412\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u043e"},
        {value: "split", label: "\u0420\u0430\u0437\u0434\u0435\u043b\u044c\u043d\u043e"},
        {value: "overlay", label: "\u041d\u0430\u043b\u043e\u0436\u0435\u043d\u0438\u0435\u043c"}
    ];
    const CHART_SCENARIOS_BY_CANVAS = {
        "chart-events-count": [
            {
                id: "traffic_spike",
                label: "Всплеск нагрузки",
                description: "Ищет резкие пики количества событий и помогает сверить их с модулем, типом события и периодом.",
                details: "Смотрите, появился ли пик одновременно на всех событиях или только на одном типе. Если пик локальный, дальше сузьте фильтр по event type и path."
            },
            {
                id: "traffic_drop",
                label: "Просадка потока",
                description: "Показывает интервалы, где поток событий резко упал или стал нулевым.",
                details: "Полезно для поиска недоступности трекинга, выключенного модуля или потери пользовательского трафика."
            },
            {
                id: "load_release_compare",
                label: "Нагрузка до/после",
                description: "Сравнивает интенсивность потока до и после изменения периода.",
                details: "Используйте вместе с режимом сравнения графика: раздельно для двух окон или наложением для быстрого визуального контроля."
            },
            {
                id: "event_mix_shift",
                label: "Смена структуры событий",
                description: "Помогает заметить, что нагрузка сместилась между типами событий или модулями.",
                details: "После выбора сценария проверьте KPI по типам событий и Raw-события для доминирующих event type."
            }
        ],
        "chart-latency": [
            {
                id: "tail_latency",
                label: "Проблема хвоста latency",
                description: "Фокус на P95/P99, когда редкие запросы становятся заметно тяжелее обычных.",
                details: "Если P95/P99 растут быстрее AVG, ищите отдельные тяжелые trace и path, а не среднюю деградацию."
            },
            {
                id: "avg_p95_gap",
                label: "Разрыв AVG и P95",
                description: "Проверяет, насколько хвост отличается от среднего времени.",
                details: "Большой разрыв обычно означает неоднородные сценарии или часть запросов с отдельным bottleneck."
            },
            {
                id: "release_latency",
                label: "Latency после релиза",
                description: "Сценарий для сравнения скорости до и после изменения.",
                details: "Сравнивайте равные окна. Если latency выросла без роста count, вероятна регрессия обработки."
            },
            {
                id: "latency_spikes",
                label: "Пики P95/P99",
                description: "Ищет короткие выбросы задержек.",
                details: "Для коротких пиков переходите в Raw по времени пика и сверяйте trace/error class."
            }
        ],
        "chart-error-rate": [
            {
                id: "error_burst",
                label: "Всплеск ошибок",
                description: "Фокус на резком росте доли ошибок.",
                details: "Сверьте error rate с count. Если count стабилен, а error rate растет, проблема вероятно локальная."
            },
            {
                id: "errors_without_load",
                label: "Ошибки без роста нагрузки",
                description: "Показывает ошибки, которые не объясняются пиком трафика.",
                details: "Проверьте error class, HTTP status и конкретный event type в Raw-событиях."
            },
            {
                id: "repeated_error_peaks",
                label: "Повторяющиеся пики",
                description: "Ищет периодические всплески ошибок.",
                details: "Повторяемость часто указывает на batch, cron, внешний сервис или периодическую деградацию."
            },
            {
                id: "event_module_errors",
                label: "Ошибки по событиям/модулям",
                description: "Помогает найти, какой модуль или event type дает основной вклад в ошибки.",
                details: "Сузьте фильтр по модулю и типу события, затем проверьте Raw и trace."
            }
        ],
        "chart-event-kpi": [
            {
                id: "top_load_events",
                label: "События с максимумом нагрузки",
                description: "Сравнивает event types по count.",
                details: "Высокий count без ошибок может быть нормой. Высокий count вместе с error rate или P95 требует приоритета."
            },
            {
                id: "top_latency_events",
                label: "События с высоким P95",
                description: "Ищет типы событий с тяжелым хвостом latency.",
                details: "Низкий count и высокий P95 часто указывают на редкий, но дорогой сценарий."
            },
            {
                id: "top_error_events",
                label: "События с высоким error rate",
                description: "Выделяет event types, где доля ошибок выше остальных.",
                details: "Проверьте, нет ли малого count: при малой выборке error rate может быть шумным."
            },
            {
                id: "event_compare_shift",
                label: "Сравнение event types до/после",
                description: "Помогает увидеть, какие типы событий изменились между периодами.",
                details: "Используйте режим наложения или раздельное сравнение, затем проверяйте конкретный event type."
            }
        ],
        "chart-stage-latency": [
            {
                id: "layer_bottleneck",
                label: "Узкое место по слоям",
                description: "Сравнивает вклад CONTROLLER, SERVICE, DATABASE и frontend/network этапов.",
                details: "Если один слой стабильно выше остальных, начинайте расследование с него."
            },
            {
                id: "database_degradation",
                label: "Деградация DATABASE",
                description: "Фокус на росте задержек слоя базы данных.",
                details: "Сверьте с DB_QUERY_COUNT, размером ответа и Raw trace проблемных запросов."
            },
            {
                id: "service_degradation",
                label: "Деградация SERVICE",
                description: "Проверяет рост времени бизнес-логики.",
                details: "Ищите новые ветки логики, внешние вызовы и тяжелые операции после релиза."
            },
            {
                id: "layer_avg_p95",
                label: "AVG/P95 по слоям",
                description: "Показывает, где хвост отличается от среднего.",
                details: "Большой разрыв между AVG и P95 в слое означает нерегулярную тяжелую ветку."
            }
        ],
        "chart-stage-errors": [
            {
                id: "stage_error_growth",
                label: "Рост ошибок на этапе",
                description: "Показывает, какой этап дает основной вклад в ошибки.",
                details: "Сначала локализуйте этап, затем переходите в Raw по error class и trace."
            },
            {
                id: "database_errors",
                label: "Ошибки DATABASE",
                description: "Фокус на сбоях базы данных и запросов.",
                details: "Проверьте timeout, connection pool, SQL и нагрузку в тот же период."
            },
            {
                id: "frontend_network_errors",
                label: "FRONTEND/NETWORK проблемы",
                description: "Помогает отделить клиентские и сетевые сбои от backend-сбоев.",
                details: "Сверьте user agent, client type, HTTP status и request path."
            },
            {
                id: "stage_drilldown",
                label: "Drill-down по слоям",
                description: "Сценарий для последовательного сужения от слоя к событию и trace.",
                details: "Выберите проблемный слой, затем фильтруйте Raw по событию, path и error class."
            }
        ],
        "chart-stage-metric-series": [
            {
                id: "db_query_growth",
                label: "Рост SQL-запросов",
                description: "Отслеживает увеличение DB query count во времени.",
                details: "Рост SQL вместе с latency DATABASE обычно указывает на лишние запросы или тяжелые выборки."
            },
            {
                id: "response_size_growth",
                label: "Большой размер ответа",
                description: "Проверяет, тянет ли размер ответа latency вверх.",
                details: "Сравнивайте RESPONSE_SIZE/TRANSFER_SIZE с P95 и количеством элементов."
            },
            {
                id: "retry_growth",
                label: "Рост повторов",
                description: "Ищет увеличение retry/повторных операций.",
                details: "Повторы часто маскируют внешние сбои и увеличивают хвост latency."
            },
            {
                id: "numeric_text_compare",
                label: "Числовые и текстовые метрики",
                description: "Связывает числовые аномалии с URL, методом, статусом или кодом ошибки.",
                details: "После числового пика проверьте текстовые метрики в соседнем блоке."
            }
        ],
        "chart-stage-metric-text": [
            {
                id: "problem_urls",
                label: "Проблемные URL",
                description: "Ищет URL, которые чаще попадают в проблемные события.",
                details: "Сопоставьте URL с latency, error rate и HTTP status."
            },
            {
                id: "http_methods",
                label: "HTTP-методы",
                description: "Показывает, какие методы связаны с ошибками или задержками.",
                details: "POST/PUT чаще связаны с записью и валидацией, GET — с чтением и размером ответа."
            },
            {
                id: "error_codes",
                label: "Коды ошибок",
                description: "Группирует проблемные события по error code/status.",
                details: "Один доминирующий код обычно дает более быстрый путь к первопричине."
            },
            {
                id: "anomalous_sample",
                label: "Аномальная выборка",
                description: "Помогает найти нетипичные значения атрибутов в проблемном периоде.",
                details: "Сравните top значений в обычном и проблемном окне."
            }
        ],
        "chart-universal-timeline": [
            {
                id: "universal_event_analysis",
                label: "Анализ события",
                description: "Фокус на динамике выбранного события во времени.",
                details: "Используйте фильтр event type и смотрите count, P95 и error rate вместе."
            },
            {
                id: "universal_attr_slice",
                label: "Срез по атрибуту",
                description: "Проверяет, как атрибут или его значение влияет на тренд.",
                details: "Сравните тот же период без фильтра атрибута, чтобы отделить общий фон от локального среза."
            },
            {
                id: "universal_correlation",
                label: "Корреляция count/latency/error",
                description: "Помогает понять, связана ли деградация с нагрузкой.",
                details: "Если latency/error растут без count, ищите регрессию или внешний bottleneck."
            },
            {
                id: "universal_before_after",
                label: "До/после по срезу",
                description: "Сравнивает выбранный срез между двумя равными окнами.",
                details: "Используется вместе с глобальным режимом сравнения."
            }
        ],
        "chart-universal-stages": [
            {
                id: "universal_layer_analysis",
                label: "Анализ слоя",
                description: "Показывает, какой слой влияет на выбранный срез universal.",
                details: "Фильтруйте stage type и смотрите, меняется ли картина относительно общей timeline."
            },
            {
                id: "universal_layer_bottleneck",
                label: "Узкое место слоя",
                description: "Ищет слой, который объясняет P95 или ошибки.",
                details: "Дальше переходите к метрикам этапов и Raw trace."
            },
            {
                id: "universal_stage_compare",
                label: "Слои до/после",
                description: "Сравнивает вклад слоев в двух периодах.",
                details: "Удобно после релиза или изменения фильтра."
            }
        ],
        "chart-universal-event-kpi": [
            {
                id: "universal_event_kpi",
                label: "KPI событий в срезе",
                description: "Сравнивает event types внутри выбранного universal-среза.",
                details: "Ищите event type с высоким count, P95 или error rate внутри выбранного фильтра."
            },
            {
                id: "universal_problem_event",
                label: "Проблемный event type",
                description: "Помогает найти событие, которое портит общий срез.",
                details: "После выбора события проверьте timeline и Raw."
            },
            {
                id: "universal_event_shift",
                label: "Смена структуры event types",
                description: "Показывает перекос нагрузки между событиями.",
                details: "Полезно для анализа изменения поведения пользователей или маршрутизации."
            }
        ],
        "chart-compare-delta": [
            {
                id: "release_delta",
                label: "Дельта после релиза",
                description: "Показывает изменение ключевых KPI между двумя периодами.",
                details: "Положительная дельта latency/error rate обычно требует проверки Raw и stage breakdown."
            },
            {
                id: "latency_delta",
                label: "Дельта latency",
                description: "Фокус на изменении AVG/P95/P99.",
                details: "Сравнивайте только равные окна и одинаковые фильтры."
            },
            {
                id: "error_delta",
                label: "Дельта ошибок",
                description: "Показывает изменение ошибок между периодами.",
                details: "Если count не вырос, а error rate вырос, вероятна регрессия качества."
            }
        ]
    };
    CHART_SCENARIOS_BY_CANVAS["chart-stage-metric-series-compare"] = CHART_SCENARIOS_BY_CANVAS["chart-stage-metric-series"];
    CHART_SCENARIOS_BY_CANVAS["chart-stage-metric-text-compare"] = CHART_SCENARIOS_BY_CANVAS["chart-stage-metric-text"];
    [
        "chart-universal-timeline",
        "chart-universal-stages",
        "chart-universal-event-kpi",
        "analytics-stage-table",
        "analytics-stage-metric-table",
        "analytics-events-table"
    ].forEach((chartId) => CHART_HELP_TARGETS.add(chartId));

    const ANALYTICS_TREND_ICON_REGISTRY = {
        trend_up: "M4 16 L9 11 L13 14 L20 6 M15 6 H20 V11",
        trend_down: "M4 7 L9 12 L13 9 L20 17 M15 17 H20 V12",
        spike: "M4 16 L8 15 L11 5 L14 15 L20 14",
        plateau: "M4 14 L8 10 H14 L20 10",
        oscillation: "M3 12 C6 4 9 20 12 12 C15 4 18 20 21 12",
        anomaly_point: "M4 16 L9 13 L14 14 L20 8 M14 14 m-2 0 a2 2 0 1 0 4 0 a2 2 0 1 0 -4 0",
        divergence: "M4 16 C8 10 12 8 20 6 M4 8 C8 10 12 14 20 18",
        overlay_before_after: "M4 15 C9 9 14 13 20 7 M4 18 C9 12 14 16 20 10",
        split_before_after: "M5 6 V18 M12 6 V18 M19 6 V18",
        error_growth: "M4 16 L9 13 L13 15 L20 7 M18 5 L21 8 M21 5 L18 8",
        latency_growth: "M5 16 L9 12 L12 14 L19 7 M5 19 H19",
        volume_growth: "M5 17 V12 M10 17 V9 M15 17 V6 M20 17 V4",
        volume_drop: "M5 7 V17 M10 10 V17 M15 13 V17 M20 15 V17"
    };

    const ANALYTICS_SCENARIO_REGISTRY = {
        global: [
            {
                id: "traffic_spike",
                label: "Пиковая нагрузка",
                description: "Помогает найти резкий рост количества событий и понять, связан ли он с деградацией.",
                details: "Используйте, когда на графике count появился выраженный пик. Сначала проверьте общий поток событий, затем KPI по типам событий, latency и error rate. Если растет только count, это может быть нормальный всплеск трафика; если вместе растут P95 или ошибки, нужен разбор Raw-событий и слоев.",
                checklist: ["Сравнить count с P95 и error rate", "Проверить, один event type вырос или весь поток", "Открыть Raw за время пика"]
            },
            {
                id: "tail_latency",
                label: "Проблема хвоста latency",
                description: "Фокус на P95/P99, когда небольшая часть запросов становится заметно медленнее.",
                details: "Подходит, если AVG почти стабилен, а P95/P99 растут. Смотрите latency trend, KPI по событиям, stage latency и Raw trace. Ложный вывод: считать проблему общей деградацией, хотя тормозит отдельный редкий сценарий.",
                checklist: ["Сравнить AVG, P95 и P99", "Найти событие с высоким P95", "Проверить слой DATABASE/SERVICE/CONTROLLER"]
            },
            {
                id: "error_burst",
                label: "Всплеск ошибок",
                description: "Ищет короткий или устойчивый рост error rate и абсолютного числа ошибок.",
                details: "Используйте вместе с count: рост error rate без роста нагрузки чаще указывает на регрессию, а рост ошибок вместе с count может быть эффектом перегрузки. Дальше проверяйте error class, HTTP status, event type и Raw.",
                checklist: ["Сравнить errors и error rate", "Проверить count", "Открыть Raw по error class/status"]
            },
            {
                id: "release_compare",
                label: "До/после релиза",
                description: "Сценарий для сравнения равных окон до и после изменения.",
                details: "Включайте compare overlay/split и сравнивайте count, P95, error rate, stage latency и состав событий. Важно использовать одинаковые фильтры и сопоставимые интервалы.",
                checklist: ["Включить compare", "Сравнить P95/error rate", "Проверить изменение состава event types"]
            },
            {
                id: "layer_bottleneck",
                label: "Узкое место по слоям",
                description: "Помогает понять, какой слой дает основной вклад в задержку или ошибки.",
                details: "Смотрите stage latency/errors, затем stage metrics и Raw. Если DATABASE растет вместе с DB_QUERY_COUNT или RESPONSE_SIZE, вероятна проблема запроса или объема данных.",
                checklist: ["Сравнить слои", "Проверить stage metrics", "Перейти к Raw trace"]
            },
            {
                id: "error_without_load",
                label: "Ошибки без роста нагрузки",
                description: "Ищет регрессию качества при стабильном количестве событий.",
                details: "Если count стабилен, а error rate растет, причина часто в изменении логики, внешнем сервисе или данных. Проверьте error class, request path и конкретные события.",
                checklist: ["Убедиться, что count стабилен", "Найти доминирующий error class", "Сравнить path/event type"]
            }
        ],
        "chart-event-kpi": [
            {id: "event_error_growth", label: "Рост ошибок по событию", description: "Выделяет event type, где error rate выше остальных.", details: "Смотрите не только процент, но и count. Малый count может давать шумный процент. Подтверждайте вывод Raw-событиями и error class."},
            {id: "event_p95_degradation", label: "Деградация P95", description: "Ищет события с тяжелым хвостом latency.", details: "Высокий P95 у редкого события может быть важнее среднего AVG. Сопоставляйте с latency trend и stage latency."},
            {id: "event_load_growth", label: "Рост нагрузки", description: "Показывает события, которые начали доминировать по count.", details: "Если count вырос без ошибок, это может быть нормальный спрос. Если вместе растут P95 или errors, переходите к Raw и слоям."},
            {id: "event_mix_shift", label: "Смена состава событий", description: "Помогает увидеть, что структура нагрузки изменилась.", details: "Сравните текущий период с предыдущим и проверьте, какие event types появились, исчезли или поменяли долю."},
            {id: "rare_slow_event", label: "Редкое медленное событие", description: "Находит низкий count с высоким P95.", details: "Такие события легко потерять в общей статистике, но они часто указывают на дорогую бизнес-операцию."}
        ],
        "chart-error-rate": [
            {id: "error_growth", label: "Рост ошибок", description: "Фокус на устойчивом росте error rate.", details: "Сравните с count: если нагрузка не растет, вероятна регрессия. Дальше смотрите error class, HTTP status и Raw."},
            {id: "error_spike", label: "Краткий всплеск", description: "Ищет короткий пик ошибок.", details: "Проверьте точное время пика, связанные trace и внешние зависимости."},
            {id: "high_error_plateau", label: "Высокое плато", description: "Показывает период, где ошибки держатся стабильно высоко.", details: "Это чаще системная проблема, чем единичный сбой. Смотрите stage errors и повторяемость event types."},
            {id: "errors_after_load", label: "Ошибки после роста нагрузки", description: "Проверяет, начались ли ошибки после увеличения count.", details: "Если рост ошибок следует за count, проверьте лимиты, connection pool и время DATABASE/SERVICE."},
            {id: "recovery_after_fix", label: "Снижение после исправления", description: "Оценивает, исчезли ли ошибки после изменения.", details: "Используйте compare до/после и проверьте, не изменился ли одновременно объем событий."}
        ],
        "chart-latency": [
            {id: "p95_growth", label: "Рост P95", description: "Фокус на ухудшении пользовательского хвоста.", details: "Сравните AVG/P95/P99. Если растет только P95, ищите отдельные медленные trace и слои."},
            {id: "single_spike", label: "Единичный spike", description: "Ищет короткий выброс latency.", details: "Проверьте Raw в окне spike и не делайте вывод по одному пику без повторяемости."},
            {id: "stable_load_degradation", label: "Деградация при стабильной нагрузке", description: "Latency растет без роста count.", details: "Чаще всего это регрессия кода, внешняя зависимость или база данных."},
            {id: "recovery_after_peak", label: "Восстановление после пика", description: "Показывает, вернулась ли latency к норме.", details: "Если восстановление неполное, сравните stage metrics и error rate."},
            {id: "avg_p95_p99_gap", label: "Разрыв AVG/P95/P99", description: "Показывает неоднородность распределения.", details: "Большой разрыв означает, что среднее скрывает тяжелые случаи."}
        ],
        "chart-events-count": [
            {id: "traffic_spike", label: "Всплеск нагрузки", description: "Резкий рост количества событий.", details: "Проверьте, растут ли одновременно latency и errors. Если нет, это может быть нормальный трафик."},
            {id: "traffic_drop", label: "Просадка потока", description: "Падение количества событий или нулевой поток.", details: "Проверьте трекинг, доступность модуля и фильтры периода."},
            {id: "periodic_load", label: "Периодическая нагрузка", description: "Повторяющиеся пики count.", details: "Часто связаны с batch/cron или регулярным пользовательским поведением."},
            {id: "event_mix_shift", label: "Смена состава событий", description: "Общий count стабилен, но меняется вклад event types.", details: "Смотрите KPI по типам событий и compare до/после."}
        ],
        "chart-universal-timeline": [
            {id: "universal_event_analysis", label: "Анализ выбранного события", description: "Фокусирует чтение Universal на выбранном event type.", details: "Сравните count, P95 и error rate в одном срезе. Если выбран атрибут, проверьте этот же период без атрибута."},
            {id: "universal_attr_slice", label: "Анализ атрибута", description: "Показывает, как значение атрибута связано с метриками.", details: "Полезно для HTTP path/status/client type. Сравните с Raw и stage metrics."},
            {id: "universal_anomaly_segment", label: "Аномальный сегмент", description: "Ищет сегмент, где метрики отличаются от общего фона.", details: "Сначала найдите отличие на timeline, затем сузьте event/stage/attribute."},
            {id: "universal_before_after", label: "Сравнение до/после", description: "Сравнивает выбранный срез в двух окнах.", details: "Используйте overlay или split и держите фильтры одинаковыми."}
        ],
        "chart-universal-stages": [
            {id: "universal_layer_analysis", label: "Анализ выбранного слоя", description: "Показывает вклад слоя в выбранном Universal-срезе.", details: "Если слой доминирует, переходите в stage metrics и Raw trace."},
            {id: "universal_layer_bottleneck", label: "Узкое место слоя", description: "Ищет слой, объясняющий P95 или ошибки.", details: "DATABASE проверяйте вместе с SQL count/response size, SERVICE - с бизнес-логикой."},
            {id: "universal_stage_compare", label: "Слои до/после", description: "Сравнивает вклад слоев между периодами.", details: "Полезно после релиза или изменения фильтра."}
        ],
        "chart-universal-event-kpi": [
            {id: "universal_event_kpi", label: "KPI событий в срезе", description: "Сравнивает события внутри текущего Universal-фильтра.", details: "Ищите высокий count, P95 или error rate и затем открывайте timeline/Raw."},
            {id: "universal_problem_event", label: "Проблемный event type", description: "Находит событие, портящее выбранный срез.", details: "Проверьте, остается ли проблема без атрибутного фильтра."},
            {id: "universal_event_shift", label: "Смена состава событий", description: "Показывает, как меняется структура event types.", details: "Используйте compare и стабильную сортировку, чтобы не принять перестановку за тренд."}
        ],
        "chart-stage-latency": [
            {id: "layer_bottleneck", label: "Узкое место по слоям", description: "Сравнивает latency CONTROLLER/SERVICE/DATABASE.", details: "Смотрите P95/P99 и сопоставляйте со stage metrics и Raw trace."},
            {id: "database_degradation", label: "Деградация DATABASE", description: "Фокус на росте времени базы.", details: "Проверьте DB query count, response size и конкретные SQL/репозитории."},
            {id: "service_degradation", label: "Деградация SERVICE", description: "Рост времени бизнес-логики.", details: "Ищите новые ветки логики, внешние вызовы и рост ошибок."},
            {id: "controller_overhead", label: "Накладные расходы CONTROLLER", description: "Проверяет рост времени обработки запроса на входе.", details: "Сопоставляйте с request path, body size и frontend/network признаками."}
        ],
        "chart-stage-errors": [
            {id: "stage_error_growth", label: "Рост ошибок слоя", description: "Показывает, какой слой дает основной вклад в ошибки.", details: "Сначала локализуйте слой, затем переходите к Raw по error class/status."},
            {id: "database_errors", label: "Ошибки DATABASE", description: "Фокус на сбоях запросов, таймаутах и соединениях.", details: "Смотрите SQL count, latency DATABASE и повторяемость path/event type."},
            {id: "service_errors", label: "Ошибки SERVICE", description: "Фокус на бизнес-ошибках и внешних зависимостях.", details: "Проверяйте error class и trace проблемного события."}
        ],
        "chart-stage-metric-series": [
            {id: "numeric_metric_degradation", label: "Деградация числовой метрики", description: "Показывает рост DB_QUERY_COUNT, RESPONSE_SIZE или другой числовой метрики.", details: "Сравните пик метрики с latency слоя и count событий."},
            {id: "metric_spike", label: "Всплеск значения", description: "Ищет резкий пик выбранной метрики.", details: "Проверьте, не связан ли пик с одним event type или request path."},
            {id: "top_value_shift", label: "Смена top values", description: "Связывает числовой пик с изменением текстовых признаков.", details: "Откройте соседний текстовый график и Raw за тот же период."}
        ],
        "chart-stage-metric-text": [
            {id: "text_distribution_shift", label: "Изменение распределения", description: "Показывает, какие текстовые значения стали чаще.", details: "Смотрите URL, HTTP status, method, error code и сопоставляйте с latency/errors."},
            {id: "problem_urls", label: "Проблемные URL", description: "Находит path, который чаще встречается в проблемном окне.", details: "Сравните с requestPath-фильтром и Raw событиями."},
            {id: "error_codes", label: "Коды ошибок", description: "Группирует события по error code/status.", details: "Один доминирующий код часто дает быстрый путь к причине."}
        ],
        "analytics-events-table": [
            {id: "trace_investigation", label: "Проверка trace", description: "Разбор конкретной цепочки события.", details: "Откройте детали события, проверьте stages, attributes, metrics и связанные логи."},
            {id: "fresh_raw_check", label: "Свежие события", description: "Проверяет события, которые еще не попали в агрегаты.", details: "Используйте Raw как источник истины для последних минут."},
            {id: "error_search", label: "Поиск ошибки", description: "Фильтрует Raw по error class/status/path.", details: "После нахождения паттерна возвращайтесь к агрегатам, чтобы оценить масштаб."}
        ],
        "chart-compare-delta": [
            {id: "release_delta", label: "Дельта после релиза", description: "Сравнивает KPI между двумя периодами.", details: "Положительная дельта latency/error rate требует проверки Raw и stage breakdown."},
            {id: "latency_delta", label: "Дельта latency", description: "Фокус на изменении AVG/P95/P99.", details: "Сравнивайте только равные окна и одинаковые фильтры."},
            {id: "error_delta", label: "Дельта ошибок", description: "Показывает изменение ошибок между периодами.", details: "Проверьте count, чтобы не принять изменение объема за изменение качества."}
        ]
    };

    const ANALYTICS_CHART_HELP_REGISTRY = {
        "chart-event-kpi": {
            title: "KPI по типам событий",
            shortDescription: "Сравнивает типы событий по количеству, P95 и доле ошибок.",
            whatItShows: "График помогает понять, какие event types дают основной вклад в нагрузку, хвост latency и ошибки. Он полезен для приоритизации расследования: сначала смотрим события с большим count, высоким P95 или заметным error rate.",
            howToRead: "Начинайте с count, затем смотрите P95 и error rate. Высокий P95 при малом count означает редкий, но дорогой сценарий. Высокий error rate при большом count обычно приоритетнее, чем единичная ошибка.",
            metrics: [
                {name: "Count", description: "Сколько событий выбранного типа попало в период. Рост без роста ошибок может быть нормальной нагрузкой."},
                {name: "P95", description: "Значение, быстрее которого обработано 95% событий. Показывает хвост, который плохо виден по AVG."},
                {name: "Error rate", description: "Доля событий с ошибкой. Всегда проверяйте вместе с count, чтобы не переоценить малую выборку."}
            ],
            trendPatterns: [
                {icon: "volume_growth", title: "Рост нагрузки", description: "Count растет, latency и ошибки стабильны: чаще всего это нормальный рост трафика."},
                {icon: "latency_growth", title: "Рост P95", description: "Событие становится медленнее; нужно смотреть stage latency и Raw trace."},
                {icon: "error_growth", title: "Ошибки на одном event type", description: "Вероятна локальная регрессия или проблемные входные данные."},
                {icon: "divergence", title: "Смена состава событий", description: "Одни event types растут, другие падают; общий count может скрывать проблему."},
                {icon: "anomaly_point", title: "Редкое медленное событие", description: "Малый count, но высокий P95. Не игнорируйте, если это важная бизнес-операция."}
            ],
            analysisMistakes: ["Сравнивать error rate без учета count.", "Считать высокий AVG общей проблемой без проверки P95.", "Не проверять Raw для редких событий.", "Игнорировать изменение состава event types."],
            relatedCharts: ["Latency trend", "Error rate trend", "Stage latency", "Universal KPI", "Raw события"],
            problemSignals: ["Высокий P95 у одного события", "Рост ошибок при стабильном count", "Резкая смена лидирующего event type", "Появление нового редкого тяжелого события"]
        },
        "chart-error-rate": {
            title: "Error rate trend",
            shortDescription: "Показывает долю ошибок во времени.",
            whatItShows: "График отвечает на вопрос, когда качество обработки ухудшилось и было ли это кратким всплеском или устойчивым состоянием.",
            howToRead: "Смотрите форму линии и обязательно сравнивайте с count. Рост error rate без роста нагрузки чаще указывает на регрессию; рост вместе с count может быть перегрузкой.",
            metrics: [{name: "Error rate, %", description: "Доля ошибочных событий в каждом временном bucket."}, {name: "Errors", description: "Абсолютное число ошибок полезно проверять рядом, чтобы оценить масштаб."}],
            trendPatterns: [
                {icon: "error_growth", title: "Устойчивый рост", description: "Проблема держится несколько bucket подряд."},
                {icon: "spike", title: "Краткий всплеск", description: "Нужен Raw за узкое окно времени."},
                {icon: "plateau", title: "Высокое плато", description: "Вероятна системная деградация."},
                {icon: "divergence", title: "Ошибки без нагрузки", description: "Count стабилен, error rate растет: вероятна регрессия качества."},
                {icon: "trend_down", title: "Восстановление", description: "Проверьте, вернулась ли доля ошибок к базовому уровню."}
            ],
            analysisMistakes: ["Не учитывать абсолютное количество ошибок.", "Смешивать разные event types.", "Считать один spike устойчивой проблемой.", "Не проверять HTTP status/error class."],
            relatedCharts: ["Events count", "KPI по типам событий", "Stage errors", "Raw события"],
            problemSignals: ["Error rate растет при стабильном count", "Ошибки концентрируются в одном event type", "Плато после релиза", "Короткий spike с массовыми 5xx"]
        },
        "chart-latency": {
            title: "Latency trend",
            shortDescription: "Показывает AVG, P95 и P99 времени обработки.",
            whatItShows: "График показывает скорость обработки во времени и помогает отличить общую деградацию от проблемы хвоста.",
            howToRead: "AVG показывает общий фон, P95 и P99 - тяжелые случаи. Если P95/P99 растут быстрее AVG, ищите отдельные медленные trace, path или event type.",
            metrics: [{name: "AVG", description: "Среднее время. Чувствительно к общему фону, но скрывает хвост."}, {name: "P95", description: "Хвост пользовательского опыта. Главная метрика для деградаций."}, {name: "P99", description: "Редкие самые тяжелые случаи. Может быть шумной на малом count."}],
            trendPatterns: [
                {icon: "latency_growth", title: "Рост P95", description: "Хвост становится медленнее."},
                {icon: "spike", title: "Единичный spike", description: "Проверяйте Raw в точном окне."},
                {icon: "divergence", title: "Разрыв AVG/P95", description: "Среднее выглядит нормально, но часть запросов страдает."},
                {icon: "plateau", title: "Долгое плато", description: "Устойчивая деградация, часто после изменения."},
                {icon: "trend_down", title: "Восстановление", description: "Latency снижается после пика или исправления."}
            ],
            analysisMistakes: ["Оценивать только AVG.", "Сравнивать разные объемы трафика без count.", "Не отделять редкие события от массовых.", "Не проверять stage latency."],
            relatedCharts: ["Events count", "Error rate", "Stage latency", "Universal timeline", "Raw события"],
            problemSignals: ["P95 растет без роста count", "P99 резко выше P95", "Latency растет только после релиза", "Spike совпадает с ошибками DATABASE"]
        },
        "chart-universal-timeline": {
            title: "Universal KPI timeline",
            shortDescription: "Показывает count, AVG/P95 и error rate для выбранного среза.",
            whatItShows: "Universal timeline объединяет фильтры события, слоя, атрибута и значения. Он нужен, чтобы проверить гипотезу по конкретному сегменту, а не по всему потоку.",
            howToRead: "Сначала убедитесь, какой срез выбран. Затем сравните count, latency и errors. Если фильтр атрибута сильно меняет картину, проверьте тот же период без фильтра.",
            metrics: [{name: "Count", description: "Объем выбранного среза."}, {name: "AVG/P95", description: "Скорость обработки внутри среза."}, {name: "Error rate", description: "Доля ошибок внутри среза."}],
            trendPatterns: [
                {icon: "volume_growth", title: "Рост сегмента", description: "Срез стал чаще встречаться."},
                {icon: "latency_growth", title: "Деградация сегмента", description: "P95 растет именно в выбранном срезе."},
                {icon: "error_growth", title: "Ошибки сегмента", description: "Ошибка локализована фильтром."},
                {icon: "overlay_before_after", title: "До/после", description: "Compare показывает изменение выбранного среза."},
                {icon: "anomaly_point", title: "Аномальный bucket", description: "Один интервал резко отличается от соседних."}
            ],
            analysisMistakes: ["Забыть, что включен фильтр атрибута.", "Сравнивать разные bucket size.", "Игнорировать event-scope при выбранных событиях.", "Принимать малый count за устойчивый тренд."],
            relatedCharts: ["Universal stages", "Universal event KPI", "Stage metrics", "Raw события"],
            problemSignals: ["Фильтр резко повышает error rate", "P95 растет только для одного path/status", "Compare показывает деградацию после изменения"]
        },
        "chart-stage-metric-series": {
            title: "Числовые метрики этапов",
            shortDescription: "Показывает динамику числовых stage metrics во времени.",
            whatItShows: "График помогает связать latency и ошибки с техническими признаками: количеством SQL-запросов, размером ответа, retry, длительностью внешнего вызова и другими метриками.",
            howToRead: "Сравнивайте пики метрик с stage latency и error rate. При разных единицах график нормализуется, поэтому важнее форма тренда, чем абсолютная высота линий.",
            metrics: [{name: "P95/AVG метрики", description: "Показывают типичный и хвостовой уровень выбранной числовой метрики."}, {name: "Top values", description: "Помогают понять, какие значения дают вклад в пик."}],
            trendPatterns: [
                {icon: "spike", title: "Всплеск метрики", description: "Один bucket резко выделяется."},
                {icon: "trend_up", title: "Постепенный рост", description: "Метрика растет вместе с деградацией."},
                {icon: "divergence", title: "Метрика растет без count", description: "Вероятно изменилась логика или размер данных."},
                {icon: "plateau", title: "Высокое плато", description: "Постоянно дорогой режим работы."},
                {icon: "overlay_before_after", title: "До/после", description: "Сравнение показывает изменение профиля метрик."}
            ],
            analysisMistakes: ["Сравнивать разные единицы как абсолютные значения.", "Не сверять с выбранным stage type.", "Игнорировать текстовые метрики рядом.", "Делать вывод без Raw trace."],
            relatedCharts: ["Stage latency", "Stage errors", "Stage metric text", "Raw события"],
            problemSignals: ["DB_QUERY_COUNT растет вместе с DATABASE P95", "RESPONSE_SIZE растет вместе с latency", "Retry count совпадает с error spike"]
        },
        "analytics-events-table": {
            title: "Raw события",
            shortDescription: "Таблица конкретных событий для расследования.",
            whatItShows: "Raw показывает отдельные события, stages, атрибуты, метрики и trace. Это источник деталей, когда агрегаты показали подозрительный интервал.",
            howToRead: "Фильтруйте по времени пика, event type, error class, path и metric value. Открывайте детали события и сверяйте длительности stages.",
            metrics: [{name: "Duration", description: "Полная длительность события."}, {name: "Error class/status", description: "Тип ошибки или статус ответа."}, {name: "Trace/request id", description: "Связь с логами и стадиями."}],
            trendPatterns: [
                {icon: "anomaly_point", title: "Конкретный trace", description: "Один пример для глубокого разбора."},
                {icon: "error_growth", title: "Повторяемая ошибка", description: "Одинаковый error class встречается много раз."},
                {icon: "volume_growth", title: "Свежий поток", description: "События последних минут еще не полностью отражены в rollup."}
            ],
            analysisMistakes: ["Делать общий вывод по одному trace.", "Не сверять фильтры с агрегатами.", "Игнорировать временную задержку rollup.", "Не смотреть stages внутри события."],
            relatedCharts: ["Все агрегатные графики", "Stage metrics", "Logs/trace"],
            problemSignals: ["Одинаковый error class повторяется", "Один path доминирует в ошибках", "Stage DATABASE/SERVICE сильно выделяется"]
        }
    };

    ANALYTICS_CHART_HELP_REGISTRY["chart-events-count"] = {
        ...ANALYTICS_CHART_HELP_REGISTRY["chart-event-kpi"],
        title: "Events count trend",
        shortDescription: "Показывает объем событий во времени.",
        whatItShows: "График показывает интенсивность потока и помогает отличить рост нагрузки от деградации качества.",
        howToRead: "Смотрите пики и провалы count, затем сравнивайте с latency и error rate за те же интервалы."
    };
    ANALYTICS_CHART_HELP_REGISTRY["chart-stage-latency"] = {
        ...ANALYTICS_CHART_HELP_REGISTRY["chart-latency"],
        title: "Latency по слоям",
        shortDescription: "Показывает, какой слой отвечает за задержку.",
        whatItShows: "График разделяет время обработки между CONTROLLER, SERVICE, DATABASE и другими этапами.",
        howToRead: "Ищите слой, у которого P95/AVG растет сильнее остальных, затем переходите в stage metrics и Raw."
    };
    ANALYTICS_CHART_HELP_REGISTRY["chart-stage-errors"] = {
        ...ANALYTICS_CHART_HELP_REGISTRY["chart-error-rate"],
        title: "Ошибки по слоям",
        shortDescription: "Показывает распределение ошибок между этапами.",
        whatItShows: "График помогает локализовать, где возникает ошибка: на входе, в бизнес-логике, базе или внешнем вызове.",
        howToRead: "Смотрите слой с ростом ошибок и сверяйте с error class/status в Raw."
    };
    ANALYTICS_CHART_HELP_REGISTRY["chart-universal-stages"] = ANALYTICS_CHART_HELP_REGISTRY["chart-stage-latency"];
    ANALYTICS_CHART_HELP_REGISTRY["chart-universal-event-kpi"] = ANALYTICS_CHART_HELP_REGISTRY["chart-event-kpi"];
    ANALYTICS_CHART_HELP_REGISTRY["chart-stage-metric-text"] = {
        ...ANALYTICS_CHART_HELP_REGISTRY["chart-stage-metric-series"],
        title: "Текстовые метрики этапов",
        shortDescription: "Показывает распределение текстовых признаков: path, status, method, error code.",
        whatItShows: "График помогает понять, какие текстовые значения чаще встречаются в выбранном периоде или проблемном окне.",
        howToRead: "Сравнивайте top values с error rate, latency и Raw. Один доминирующий status/path часто ускоряет поиск причины."
    };
    ANALYTICS_CHART_HELP_REGISTRY["analytics-stage-table"] = ANALYTICS_CHART_HELP_REGISTRY["chart-stage-latency"];
    ANALYTICS_CHART_HELP_REGISTRY["analytics-stage-metric-table"] = ANALYTICS_CHART_HELP_REGISTRY["chart-stage-metric-series"];
    ANALYTICS_CHART_HELP_REGISTRY["chart-compare-delta"] = {
        ...ANALYTICS_CHART_HELP_REGISTRY["chart-latency"],
        title: "Compare delta",
        shortDescription: "Показывает изменение KPI между периодами до и после.",
        whatItShows: "График показывает, какие показатели улучшились или ухудшились между двумя окнами.",
        howToRead: "Сравнивайте только равные периоды и одинаковые фильтры. Положительная дельта latency/error rate обычно требует расследования."
    };

    function applyHumanHelpCopy() {
        const metricBasics = [
            {name: "Count", description: "Показывает, сколько раз событие произошло. Сначала проверьте именно Count: если событий мало, проценты ошибок и P95 могут прыгать из-за одного-двух случаев."},
            {name: "AVG", description: "Среднее время дает общий фон, но сглаживает редкие медленные запросы. Если AVG спокойный, а P95 высокий, проблема касается не всех пользователей, а хвоста."},
            {name: "P95", description: "Это граница, быстрее которой завершились 95% событий. P95 помогает увидеть хвост: большинство запросов нормальные, но часть пользователей получает заметно более медленный ответ."},
            {name: "P99", description: "Еще более крайний хвост. Он полезен для редких тяжелых проблем, но на маленькой выборке может быть шумным, поэтому всегда смотрите Count."},
            {name: "Error rate", description: "Доля событий с ошибками. Не читайте ее отдельно от Count: 50% ошибок из двух событий и 5% из тысячи событий означают совершенно разный масштаб."}
        ];
        const commonReadingMistakes = [
            "Не делайте вывод по одному spike, если рядом нет повторения и Count маленький.",
            "Не сравнивайте До/После, пока не проверили, что периоды сопоставимы по нагрузке и фильтрам.",
            "Не оценивайте Error rate без Count: на маленькой выборке процент легко выглядит пугающим.",
            "Не считайте рост Count дефектом, если P95 и Error rate остались стабильными."
        ];
        const commonPatterns = [
            {
                icon: "spike",
                title: "Один резкий spike",
                description: "Если один bucket резко выделился, это может быть реальный сбой, тестовый прогон или единичный тяжелый запрос.",
                howToCheck: "Откройте Raw за точный интервал spike и проверьте Count, event type, path, status и Trace ID.",
                falseAlarm: "Если событий было мало и соседние bucket нормальные, сначала считайте это кандидатом на шум, а не подтвержденным инцидентом."
            },
            {
                icon: "plateau",
                title: "Несколько плохих bucket подряд",
                description: "Если рост держится несколько интервалов, это больше похоже на устойчивую деградацию, чем на случайный выброс.",
                howToCheck: "Сравните тот же период на latency, error rate и stage-графиках. Если проблема повторяется в одном слое, расследование можно сузить.",
                falseAlarm: "Проверьте, не сменился ли фильтр, период или состав тестовых сценариев."
            },
            {
                icon: "divergence",
                title: "P95 растет, Count не растет",
                description: "Нагрузка вряд ли является главной причиной. Чаще это медленный слой, внешний вызов, база данных или изменение логики.",
                howToCheck: "Сначала проверьте stage latency, затем stage metrics вроде DB_QUERY_COUNT и RESPONSE_SIZE, после этого откройте Raw trace.",
                falseAlarm: "На маленьком Count P95 мог измениться из-за одного тяжелого события."
            },
            {
                icon: "error_growth",
                title: "Error rate растет при стабильном Count",
                description: "Это похоже на регрессию качества: событий столько же, но ошибок стало больше.",
                howToCheck: "Откройте Raw за проблемный bucket и проверьте HTTP status, error code, path и повторяемость Trace ID.",
                falseAlarm: "Если Count очень маленький, одна ошибка может дать высокий процент."
            },
            {
                icon: "volume_growth",
                title: "Count растет, P95 и ошибки стабильны",
                description: "Скорее всего, система просто обработала больше нагрузки без ухудшения качества.",
                howToCheck: "Проверьте, не вырос ли один event type или path. Если latency и errors не изменились, это не первоочередная проблема.",
                falseAlarm: "Не записывайте такой рост в дефекты без признаков деградации."
            },
            {
                icon: "overlay_before_after",
                title: "До/После стало хуже",
                description: "Если после изменения вырос P95 или Error rate, это может быть регрессия, но сначала убедитесь, что сравниваются похожие окна.",
                howToCheck: "Сравните Count и состав event types в обоих периодах. Затем проверьте stage-графики и Raw для ухудшившегося среза.",
                falseAlarm: "Разная нагрузка или другой набор тестов легко создают ложную разницу До/После."
            }
        ];
        const relatedChartsHuman = [
            "Raw события — откройте их, когда нужно увидеть конкретные запросы, статусы, path и Trace ID, из которых сложился пик.",
            "Stage metrics — используйте, если нужно понять, какой технический признак вырос вместе с задержкой или ошибками.",
            "Latency trend — помогает понять, проблема была короткой или держалась несколько интервалов подряд.",
            "Error rate trend — нужен, чтобы отличить рост ошибок от роста задержек без ошибок."
        ];
        const enrich = (chartId, patch) => {
            ANALYTICS_CHART_HELP_REGISTRY[chartId] = {
                ...(ANALYTICS_CHART_HELP_REGISTRY[chartId] || {}),
                ...patch
            };
        };

        enrich("chart-event-kpi", {
            whatItShows: "Этот график показывает, какие типы событий дают основной вклад в нагрузку, задержки и ошибки. Он помогает быстро понять, проблема связана с одним действием пользователя или распределена по всему приложению.",
            howToRead: "Сначала посмотрите Count, чтобы понять размер выборки. Затем проверьте P95: нет ли события, которое стало заметно медленнее остальных. После этого смотрите Error rate и подтверждайте вывод Raw-событиями.",
            metrics: metricBasics,
            trendPatterns: commonPatterns,
            actions: ["Начните с Count: достаточно ли событий, чтобы делать вывод.", "Если выделился один event type, отфильтруйте его и проверьте latency, error rate и Raw.", "Если несколько событий ухудшились одновременно, переходите к stages/layers.", "При До/После сначала сравните нагрузку."],
            analysisMistakes: commonReadingMistakes,
            relatedCharts: relatedChartsHuman
        });
        enrich("chart-events-count", {
            whatItShows: "График показывает поток событий во времени. Он нужен, чтобы отделить рост нагрузки от реальной деградации качества.",
            howToRead: "Если Count вырос, сразу проверьте P95 и Error rate в тот же период. Рост Count без роста задержек и ошибок чаще означает нормальную нагрузку, а не дефект.",
            metrics: [metricBasics[0]],
            trendPatterns: commonPatterns,
            actions: ["Найдите интервал пика или провала.", "Сравните этот интервал с latency и error rate.", "Проверьте, один event type изменился или весь поток.", "Если поток исчез, сначала проверьте фильтры, период и сбор аналитики."],
            analysisMistakes: commonReadingMistakes,
            relatedCharts: relatedChartsHuman
        });
        enrich("chart-latency", {
            whatItShows: "Latency trend показывает, как менялась скорость обработки событий. Он помогает понять, деградация затронула все запросы или только хвост.",
            howToRead: "Найдите момент роста задержек, затем сравните AVG и P95. Если P95 растет сильнее AVG, ищите медленные отдельные trace, path или слой, а не общую просадку всего приложения.",
            metrics: metricBasics.filter((item) => ["AVG", "P95", "P99", "Count"].includes(item.name)),
            trendPatterns: commonPatterns,
            actions: ["Найдите bucket, где задержки начали расти.", "Сравните AVG и P95.", "Откройте KPI по типам событий, чтобы найти вклад.", "Проверьте stages/layers, чтобы найти слой.", "Подтвердите причину Raw-событиями."],
            analysisMistakes: commonReadingMistakes,
            relatedCharts: relatedChartsHuman
        });
        enrich("chart-error-rate", {
            whatItShows: "Error rate trend показывает, когда и насколько выросла доля событий с ошибками. Он нужен, чтобы отделить единичные сбои от устойчивой проблемы.",
            howToRead: "Найдите bucket с ростом ошибок и сразу проверьте Count. Если Count маленький, процент может быть шумным. Если выборка нормальная, переходите в Raw и проверяйте status, error code, path и Trace ID.",
            metrics: metricBasics.filter((item) => ["Count", "Error rate"].includes(item.name)),
            trendPatterns: commonPatterns,
            actions: ["Найдите проблемный bucket.", "Проверьте Count в этом же bucket.", "Если Count достаточный, откройте Raw за этот период.", "Проверьте HTTP status, error code, path и Trace ID.", "Сверьте слой ошибки на stage-графиках."],
            analysisMistakes: commonReadingMistakes,
            relatedCharts: relatedChartsHuman
        });
        enrich("chart-universal-timeline", {
            whatItShows: "Universal timeline показывает динамику только для выбранного среза: события, слоя, атрибута или значения. Это удобно, когда нужно проверить конкретную гипотезу, а не весь поток.",
            howToRead: "Сначала убедитесь, какие фильтры включены. Затем сравните Count, P95 и Error rate. Если включено До/После, проверьте, что периоды сопоставимы и фильтры одинаковые.",
            metrics: metricBasics,
            trendPatterns: commonPatterns,
            actions: ["Проверьте активные фильтры события, слоя и атрибута.", "Сравните срез с общей статистикой.", "Если проблема есть только в одном значении атрибута, откройте Raw с тем же фильтром.", "При compare сначала проверьте Count в обоих периодах."],
            analysisMistakes: commonReadingMistakes,
            relatedCharts: relatedChartsHuman
        });
        enrich("chart-stage-metric-series", {
            whatItShows: "Числовые метрики этапов показывают технические причины деградации: количество SQL-запросов, размер ответа, retry, длительность внешнего вызова и похожие признаки.",
            howToRead: "Смотрите, какая метрика растет в тот же момент, что latency или errors. Если DB_QUERY_COUNT растет вместе с DATABASE P95, расследование стоит начинать с запросов и объема данных.",
            metrics: [{name: "DB_QUERY_COUNT", description: "Показывает, не стало ли одно действие выполнять больше запросов к базе."}, {name: "RESPONSE_SIZE", description: "Помогает увидеть, что задержка связана с большим объемом данных."}, {name: "P95 метрики", description: "Показывает хвост значения: не только типичный случай, но и тяжелые запросы."}],
            trendPatterns: commonPatterns,
            actions: ["Сравните пик метрики с stage latency.", "Проверьте выбранный stage type.", "Откройте текстовые метрики рядом: path, status, method или error code.", "Подтвердите конкретным Raw trace."],
            analysisMistakes: commonReadingMistakes,
            relatedCharts: relatedChartsHuman
        });
        enrich("chart-stage-metric-text", {
            whatItShows: "Текстовые метрики показывают top values: какие URL, HTTP-методы, статусы, error code или другие текстовые признаки чаще всего встречались в выбранном периоде.",
            howToRead: "Смотрите не только первое значение, но и изменение состава. Появление нового status или error code может объяснить рост ошибок, а новый URL может объяснить рост задержек.",
            metrics: [{name: "Top values", description: "Самые частые значения выбранной текстовой метрики. Они помогают понять, какой path, status, method или error code сформировал пик."}, {name: "Count значения", description: "Количество появлений значения. Редкое значение может быть важным, но вывод по нему нужно подтверждать Raw."}],
            trendPatterns: commonPatterns,
            actions: ["Выберите текстовую метрику: URL, HTTP status, method или error code.", "Сравните top values До/После.", "Если появилось новое значение, откройте Raw с этим значением.", "Не делайте вывод по редкому значению без проверки Count."],
            analysisMistakes: commonReadingMistakes,
            relatedCharts: relatedChartsHuman
        });
        enrich("analytics-events-table", {
            whatItShows: "Raw-события показывают конкретные запросы, из которых собраны агрегаты: время, статус, path, stages, metrics и Trace ID.",
            howToRead: "Переходите сюда после того, как агрегатный график показал подозрительный bucket. Фильтруйте тот же период и тот же event type, затем открывайте детали событий.",
            metrics: [{name: "Trace ID", description: "Связь с логами и стадиями конкретного запроса."}, {name: "HTTP status / error code", description: "Быстро показывает, какой тип ошибки повторяется."}, {name: "Stages", description: "Помогают увидеть, где именно запрос потратил время или упал."}],
            trendPatterns: commonPatterns,
            actions: ["Откройте Raw за точный интервал пика.", "Отфильтруйте event type, path, status или error code.", "Откройте несколько событий, а не одно.", "Вернитесь к агрегатам, чтобы оценить масштаб."],
            analysisMistakes: ["Не делайте общий вывод по одному trace.", "Не меняйте фильтры относительно графика, который расследуете.", "Не забывайте, что свежие Raw могут появиться раньше rollup."],
            relatedCharts: relatedChartsHuman
        });
    }

    applyHumanHelpCopy();

    Object.entries(ANALYTICS_SCENARIO_REGISTRY).forEach(([chartId, scenarios]) => {
        if (chartId !== "global") {
            CHART_SCENARIOS_BY_CANVAS[chartId] = scenarios;
        }
    });
    CHART_SCENARIOS_BY_CANVAS["chart-stage-metric-series-compare"] = CHART_SCENARIOS_BY_CANVAS["chart-stage-metric-series"];
    CHART_SCENARIOS_BY_CANVAS["chart-stage-metric-text-compare"] = CHART_SCENARIOS_BY_CANVAS["chart-stage-metric-text"];
    Object.entries(ANALYTICS_CHART_HELP_REGISTRY).forEach(([chartId, help]) => {
        HELP_TEXTS[chartId] = help.shortDescription || help.whatItShows || HELP_TEXTS[chartId] || "";
    });
    const ALL_TIME_START_LOCAL = "1970-01-01T00:00";
    const SYSTEM_ATTRIBUTE_HELP = {
        ENTITY_TYPE: "Тип сущности события (например, товар, категория, заказ). Нужен, чтобы быстро сузить анализ до конкретной бизнес-области.",
        ENTITY_ID: "Идентификатор конкретной сущности. Помогает расследовать один объект: один товар, одну категорию, один заказ.",
        HTTP_METHOD: "Метод HTTP-запроса (GET/POST/PUT/DELETE). Показывает, это чтение данных или изменение.",
        HTTP_PATH: "Путь запроса. Используйте для анализа одного и того же endpoint без смешивания разных экранов.",
        HTTP_STATUS: "HTTP-статус ответа. По нему видно, где штатные ответы, а где клиентские или серверные ошибки.",
        ERROR_CODE: "Код ошибки приложения. Нужен для точного поиска повторяющегося сбоя и связи с конкретной логикой.",
        ERROR_CLASS: "Класс ошибки (бизнесовая, системная, ошибка HTTP-запроса). Помогает быстро определить тип инцидента.",
        CLIENT_TYPE: "Тип клиента: WEB, MOBILE, API. Нужен для сравнения поведения разных каналов.",
        USER_AGENT: "Данные о браузере/клиенте. Полезно для поиска проблем, зависящих от устройства и версии браузера.",
        REFERRER: "Источник перехода пользователя. Помогает понять, из какого шага пользователь пришёл к проблемному действию.",
        SESSION_ID_HASH: "Анонимный идентификатор сессии. Позволяет собрать цепочку действий одного визита без персональных данных.",
        USER_ID_HASH: "Анонимный идентификатор пользователя. Нужен, чтобы проверить, повторяется ли проблема у одного и того же пользователя.",
        REQUEST_ID: "Связывает пользовательский запрос с backend-логами и метриками этапов."
    };

    const css = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    const colors = {
        primary: "#6d28d9",
        primarySoft: "#c4b5fd",
        accent: "#7c3aed",
        accentSoft: "#ede9fe",
        teal: "#0f766e",
        red: "#b91c1c",
        amber: "#b45309",
        slate: "#475569",
        border: css("--border") || "#e2e8f0"
    };

    const ANALYTICS_PARAMETER_HELP_REGISTRY = {
        period: {
            title: "Период анализа",
            shortDescription: "Определяет временное окно, из которого берутся события и метрики.",
            whatItDoes: "Поля 'с' и 'по' ограничивают данные для всех расчетов: count, latency, ошибки, метрики этапов и raw-таблицу.",
            whenToUse: "Меняйте период, когда нужно сузить расследование до конкретного инцидента или сравнить поведение на длинном окне.",
            howToUse: ["Начинайте с быстрого пресета, если точное время неизвестно.", "Для инцидента выставляйте границы чуть шире подозрительного пика.", "При сравнении до/после используйте сопоставимые по длине окна."],
            whatChangesOnChart: "Графики пересчитывают bucket, набор точек, KPI и таблицы под выбранное окно.",
            commonMistakes: ["Слишком короткий период может показать случайный шум.", "Слишком длинный период сглаживает короткие сбои.", "Нельзя сравнивать окна разной нагрузки без проверки Count."],
            examples: ["24 часа - быстрый обзор текущего состояния.", "1 неделя - поиск повторяющихся деградаций.", "Точный час инцидента - проверка raw-событий и trace."],
            relatedControls: ["Быстрый период", "Бакет", "Режим сравнения"]
        },
        quickPeriod: {
            title: "Быстрый период",
            shortDescription: "Готовый пресет времени без ручного ввода дат.",
            whatItDoes: "Автоматически выставляет начало и конец периода относительно текущего времени.",
            whenToUse: "Используйте для регулярного просмотра: последние 24 часа, неделя, месяц или 3 месяца.",
            howToUse: ["Выберите пресет.", "При необходимости поправьте даты вручную.", "Для больших периодов учитывайте, что часть графиков будет читать rollup, а raw-таблица может быть тяжелее."],
            whatChangesOnChart: "Меняются все запросы, кэши считаются по новому range, графики перерисовываются.",
            commonMistakes: ["Не путайте быстрый период с режимом сравнения.", "После ручной правки даты пресет может уже не описывать точное окно."],
            examples: ["3 месяца - оценить долгий тренд.", "15 минут - проверить свежий сбой."],
            relatedControls: ["Период анализа", "Бакет"]
        },
        bucket: {
            title: "Бакет",
            shortDescription: "Размер временного интервала, в который группируются точки графика.",
            whatItDoes: "Чем больше bucket, тем меньше точек и тем сильнее сглаживание. Авто выбирает разумный шаг под длину периода.",
            whenToUse: "Меняйте вручную, если график слишком шумный или наоборот скрывает короткие пики.",
            howToUse: ["Для коротких инцидентов выбирайте 1-5 минут.", "Для недель и месяцев оставляйте Авто или крупный bucket.", "При сравнении используйте одинаковый bucket для обоих периодов."],
            whatChangesOnChart: "Меняется детализация timeline, latency/error графиков и сравнения.",
            commonMistakes: ["Крупный bucket может скрыть короткий spike.", "Мелкий bucket на длинном периоде создает много точек и замедляет рендер."],
            examples: ["1 минута - точный разбор всплеска.", "60 минут - обзор недели."],
            relatedControls: ["Период анализа", "Метрики"]
        },
        globalContext: {
            title: "Контекст событий",
            shortDescription: "Главные фильтры, которые ограничивают все вкладки аналитики.",
            whatItDoes: "Модуль и тип события задают общий срез для Overview, Universal, Metrics и Raw.",
            whenToUse: "Выбирайте модуль или событие, когда нужно расследовать одну функциональную область, а не весь поток.",
            howToUse: ["Оставьте пусто для общей картины.", "Выберите модуль для анализа части приложения.", "Выберите тип события, если уже знаете проблемный сценарий."],
            whatChangesOnChart: "Все графики получают только события выбранного контекста.",
            commonMistakes: ["Забытый фильтр может создать впечатление, что часть событий исчезла.", "Локальные фильтры графиков не должны менять главный фильтр."],
            examples: ["SHOP + ORDER_CREATED - только создание заказов."],
            relatedControls: ["Период анализа", "Общий сценарий анализа"]
        },
        globalScenario: {
            title: "Общий сценарий анализа",
            shortDescription: "Аналитический пресет, который помогает читать все графики в одном режиме расследования.",
            whatItDoes: "Меняет аналитические подсказки и сценарный фокус графиков, не создавая записи в БД.",
            whenToUse: "Выберите сценарий, если расследование уже имеет цель: ошибки, latency, релиз до/после, нагрузка или слой.",
            howToUse: ["Выберите один сценарий.", "Смотрите рекомендуемые графики и сигналы.", "Локальные сценарии графиков можно сбросить обратно к глобальному."],
            whatChangesOnChart: "Графики показывают выбранный сценарный фокус и справку, локальные overrides сбрасываются при смене главного фильтра.",
            commonMistakes: ["Сценарий не заменяет проверку данных.", "Не делайте вывод только по одной метрике."],
            examples: ["Проблема хвоста latency - смотрите P95/P99 и stages.", "Всплеск ошибок - сверяйте Error rate, Raw и error class."],
            relatedControls: ["Режим сравнения", "Тип события"]
        },
        compareMode: {
            title: "Режим сравнения",
            shortDescription: "Включает анализ До/После для одного и того же набора фильтров.",
            whatItDoes: "Off показывает один период. Split рисует периоды раздельно. Overlay накладывает линии в одном графике.",
            whenToUse: "Используйте после релиза, фикса или изменения нагрузки, когда нужно понять, стало лучше или хуже.",
            howToUse: ["Сначала настройте текущий период.", "Выберите Split для подробного чтения двух графиков.", "Выберите Overlay для быстрого визуального сравнения формы линий."],
            whatChangesOnChart: "Появляется baseline/before payload и визуальное сравнение с текущим after payload.",
            commonMistakes: ["Разные по длине периоды дают ложную разницу.", "Сравнение без проверки Count может скрыть изменение нагрузки."],
            examples: ["Overlay: быстро увидеть, вырос ли P95 после релиза.", "Split: отдельно проверить таблицы и KPI до/после."],
            relatedControls: ["Период анализа", "Бакет"]
        },
        attributeFilter: {
            title: "Атрибут и значение",
            shortDescription: "Фильтр по системному или пользовательскому признаку события.",
            whatItDoes: "Оставляет только события с выбранным атрибутом или диапазоном значений.",
            whenToUse: "Используйте для проверки одного path, HTTP status, клиента, entity id или другого признака.",
            howToUse: ["Сначала выберите код атрибута.", "Затем выберите значение или диапазон.", "Проверьте Count: редкий атрибут может давать нестабильные проценты."],
            whatChangesOnChart: "Все агрегаты и raw-таблица сужаются до выбранного значения.",
            commonMistakes: ["Фильтр по редкому значению нельзя обобщать на весь сервис.", "Пустое значение означает отсутствие фильтра, а не пустой атрибут."],
            examples: ["HTTP_STATUS=500 - только серверные ошибки.", "HTTP_PATH=/orders - один endpoint."],
            relatedControls: ["Raw атрибут", "Тип события"]
        },
        universalMode: {
            title: "Режим Universal",
            shortDescription: "Определяет, что именно сравнивает вкладка Универсальный анализ.",
            whatItDoes: "Общая статистика смотрит весь поток. Одно событие фокусируется на одном типе. Сравнение событий сопоставляет несколько типов событий по одной метрике.",
            whenToUse: "Переключайте режим, когда меняется вопрос: общая картина, разбор одного события или сравнение нескольких событий.",
            howToUse: ["Начните с Общей статистики.", "Если выделился event type, перейдите в одно событие.", "Для нескольких событий оставьте одну метрику сравнения."],
            whatChangesOnChart: "Меняется event filter, допустимый набор метрик и подписи Universal-графиков.",
            commonMistakes: ["В режиме сравнения событий нельзя корректно читать несколько метрик одновременно.", "При возврате в Общую статистику выбранные события должны быть сброшены."],
            examples: ["Сравнить ORDER_CREATED и ORDER_PAID по P95.", "Посмотреть все события по Count/Error rate."],
            relatedControls: ["События Universal", "Метрики Universal", "Слой"]
        },
        universalEvents: {
            title: "События Universal",
            shortDescription: "Выбор event type для Universal-графиков.",
            whatItDoes: "Общая статистика не фильтрует event type. Один выбранный event дает детальный разбор. Несколько event types включают режим сравнения событий.",
            whenToUse: "Используйте после того, как KPI или timeline показали подозрительный тип события.",
            howToUse: ["Оставьте Общую статистику для всей картины.", "Выберите один event для глубокого анализа.", "Выберите несколько event types только если хотите сравнивать их между собой."],
            whatChangesOnChart: "Timeline, layers и KPI получают другой набор строк; в multi-event метрика становится single-select.",
            commonMistakes: ["Забытый выбранный event делает график похожим на общий, но данные будут только по нему.", "Нельзя сравнивать редкий event с частым без проверки Count."],
            examples: ["ORDER_CREATED - только создание заказов.", "ORDER_CREATED + ORDER_PAID - сравнение пути заказа."],
            relatedControls: ["Режим Universal", "Метрики Universal"]
        },
        universalMetrics: {
            title: "Метрики Universal",
            shortDescription: "Выбор показателей для timeline и layers во вкладке Universal.",
            whatItDoes: "Для общей статистики и одного события можно читать несколько метрик. Для сравнения нескольких событий используется одна метрика.",
            whenToUse: "Count показывает объем, AVG/P95 - задержки, Error rate - долю ошибок.",
            howToUse: ["Для одного набора событий включайте 2-4 метрики.", "Для multi-event выберите одну метрику, чтобы линии были сопоставимы.", "Начинайте с Count, затем проверяйте P95 и Error rate."],
            whatChangesOnChart: "Меняются datasets на timeline и stage/layer графиках.",
            commonMistakes: ["Сравнение разных метрик на одной шкале может выглядеть убедительно, но быть неверным.", "Высокий Error rate при маленьком Count может быть шумом."],
            examples: ["P95 - поиск хвоста latency.", "Error rate - поиск деградации качества."],
            relatedControls: ["События Universal", "Слой"]
        },
        universalLayer: {
            title: "Слой Universal",
            shortDescription: "Ограничивает анализ конкретным этапом обработки.",
            whatItDoes: "Фильтрует или группирует данные по слоям вроде CONTROLLER, SERVICE, DATABASE.",
            whenToUse: "Используйте, когда нужно понять, где именно возникает задержка или ошибка.",
            howToUse: ["Сначала смотрите все слои.", "Если один слой выделяется, выберите его.", "Проверьте тот же слой в Stage Metrics и Raw."],
            whatChangesOnChart: "Timeline и stage-график показывают выбранный слой или распределение по слоям.",
            commonMistakes: ["Выбранный слой не доказывает первопричину без raw/trace.", "Проблема в одном слое может быть следствием другого слоя."],
            examples: ["DATABASE - проверить SQL count и длительность.", "CONTROLLER - проверить HTTP status/path."],
            relatedControls: ["Метрики этапов", "Raw события"]
        },
        universalStageMetrics: {
            title: "Метрики слоёв Universal",
            shortDescription: "Определяет, какие показатели рисовать на графике слоёв.",
            whatItDoes: "AVG/P95 показывают задержки этапов, Error rate показывает долю ошибочных событий в слое.",
            whenToUse: "Используйте, когда график слоёв нужен не только как распределение, но и как диагностика latency/error.",
            howToUse: ["Для производительности выберите AVG и P95.", "Для качества включите Error rate.", "Сверяйте с Count, чтобы не принять редкий выброс за системную проблему."],
            whatChangesOnChart: "Меняется набор линий или столбцов на Universal layers.",
            commonMistakes: ["P95 на малом количестве событий может быть нестабилен.", "Error rate без Count легко переоценить."],
            examples: ["P95 по DATABASE - поиск медленных SQL.", "Error rate по SERVICE - проверка бизнес-ошибок."],
            relatedControls: ["Слой Universal", "Метрики Universal"]
        },
        stageType: {
            title: "Этап",
            shortDescription: "Фильтр по этапу обработки события.",
            whatItDoes: "Оставляет метрики только выбранного stage type или показывает все этапы.",
            whenToUse: "Используйте, когда Overview/Universal указали на проблемный слой.",
            howToUse: ["Оставьте Все этапы для первичного обзора.", "Выберите конкретный этап для детального расследования.", "Сверяйте числовые и текстовые метрики одного этапа."],
            whatChangesOnChart: "Числовые и текстовые метрики этапов пересчитываются под выбранный stage.",
            commonMistakes: ["Не сравнивайте разные этапы как одинаковые операции.", "Один медленный этап может быть следствием входного объема."],
            examples: ["DATABASE - SQL-запросы и размер ответа.", "CONTROLLER - HTTP path/status."],
            relatedControls: ["Текстовая метрика", "Режим сравнения"]
        },
        stageTextMetric: {
            title: "Текстовая метрика",
            shortDescription: "Выбирает, какие top values показывать для этапов.",
            whatItDoes: "Показывает распределение значений вроде URL страницы, HTTP method, HTTP status, error code или пользовательского атрибута.",
            whenToUse: "Используйте, чтобы найти повторяющийся path, status, error code или другое значение, связанное с проблемой.",
            howToUse: ["Выберите одну текстовую метрику.", "Смотрите top values и Count.", "Откройте Raw с тем же значением для проверки конкретных событий."],
            whatChangesOnChart: "Меняется top-N график и таблица текстовых значений.",
            commonMistakes: ["Top value не всегда является причиной, он может быть просто самым частым.", "Редкое значение с высоким процентом ошибок требует проверки raw-событий."],
            examples: ["HTTP_STATUS - найти 500/404.", "HTTP_PATH - найти проблемный endpoint."],
            relatedControls: ["Этап", "Raw атрибут"]
        },
        rawStatus: {
            title: "Статус Raw событий",
            shortDescription: "Фильтрует таблицу сырых событий по ошибкам и успешным событиям.",
            whatItDoes: "Позволяет быстро оставить все события, только ошибки или только успешные записи.",
            whenToUse: "Используйте после того, как агрегаты показали рост error rate.",
            howToUse: ["Выберите Только ошибки для расследования сбоя.", "Сверьте HTTP status, error class и trace.", "Верните Все, если нужно оценить масштаб на фоне успешных событий."],
            whatChangesOnChart: "Меняется только raw-таблица и ее пагинация.",
            commonMistakes: ["Только ошибки не показывает denominator для error rate.", "Один trace не описывает всю проблему."],
            examples: ["Только ошибки + HTTP_STATUS=500.", "Все события для проверки доли ошибок."],
            relatedControls: ["Класс ошибки", "Тип события Raw"]
        },
        rawEvent: {
            title: "Тип события Raw",
            shortDescription: "Показывает сырые записи только выбранного event type.",
            whatItDoes: "Сужает raw-таблицу до конкретного действия или системного события.",
            whenToUse: "Используйте, когда агрегаты уже выделили подозрительный event type.",
            howToUse: ["Выберите event type.", "Проверьте несколько строк, а не одну.", "Сравните path/status/stages внутри выбранного события."],
            whatChangesOnChart: "Raw-таблица перезагружается с eventTypeCode в query.",
            commonMistakes: ["Фильтр Raw не меняет автоматически главный фильтр.", "Не делайте вывод по первой строке таблицы."],
            examples: ["ORDER_CREATED - изучить конкретные запросы создания заказа."],
            relatedControls: ["Период Raw", "Статус Raw событий"]
        },
        rawAdvanced: {
            title: "Дополнительные Raw фильтры",
            shortDescription: "Точные условия для поиска конкретных событий.",
            whatItDoes: "Фильтрует raw-события по метрикам, длительности, атрибутам, path, status и сортировке.",
            whenToUse: "Используйте после первичного narrowing, когда нужно найти конкретные trace или примеры проблемы.",
            howToUse: ["Задавайте один-два фильтра за раз.", "Проверяйте, что таблица не стала пустой из-за слишком узких условий.", "Для latency используйте минимальную длительность."],
            whatChangesOnChart: "Меняется только raw query, агрегаты на других вкладках не перестраиваются.",
            commonMistakes: ["Слишком много условий легко скрывают проблему.", "Сортировка по длительности не равна фильтру по ошибкам."],
            examples: ["duration >= 1000 ms - медленные события.", "attribute HTTP_PATH=/orders - один endpoint."],
            relatedControls: ["Raw статус", "Raw тип события"]
        },
        expandedPeriod: {
            title: "Параметры увеличенного графика",
            shortDescription: "Локальные настройки только для раскрытого графика.",
            whatItDoes: "Позволяет изменить период, bucket, compare mode или метрику увеличенного графика без изменения главного фильтра.",
            whenToUse: "Используйте для детального чтения одного графика, когда весь дашборд перестраивать не нужно.",
            howToUse: ["Выберите пресет или ручной период.", "При необходимости включите split/overlay.", "Сброс возвращает настройки к верхнему фильтру."],
            whatChangesOnChart: "Перерисовывается только expanded-график и связанные inline compare области.",
            commonMistakes: ["Локальный период expanded не означает, что остальные графики смотрят тот же range.", "При сравнении проверяйте одинаковую длину before/after."],
            examples: ["Увеличить latency и посмотреть только час пика.", "Overlay для одного KPI без перестройки всего Overview."],
            relatedControls: ["Период анализа", "Режим сравнения"]
        }
    };

    const ANALYTICS_PARAMETER_HELP_BINDINGS = [
        {selector: "#analytics-from, #analytics-to", helpCode: "period"},
        {selector: "#analytics-quick-preset-select, #analytics-range-n, #analytics-range-unit, #analytics-range-apply", helpCode: "quickPeriod"},
        {selector: "#analytics-bucket", helpCode: "bucket"},
        {selector: "#analytics-module-type, #analytics-event-type", helpCode: "globalContext"},
        {selector: "#analytics-analysis-scenario", helpCode: "globalScenario"},
        {selector: ".analytics-compare-mode-radios, #stage-metric-compare-mode, #stage-text-compare-mode", helpCode: "compareMode"},
        {selector: "#analytics-global-attr-code, #analytics-global-attr-value-select, #analytics-global-attr-min, #analytics-global-attr-max", helpCode: "attributeFilter"},
        {selector: "#universal-event-type-toggle, #universal-event-type-list, #universal-event-overall", helpCode: "universalEvents"},
        {selector: "#universal-stage-type", helpCode: "universalLayer"},
        {selector: "#universal-series-metric-toggle, #universal-series-metric-list", helpCode: "universalMetrics"},
        {selector: "#universal-stage-metric-toggle, #universal-stage-metric-list", helpCode: "universalStageMetrics", placement: "before"},
        {selector: "[data-universal-mode], [data-universal-analysis-mode]", helpCode: "universalMode"},
        {selector: "#stage-metric-stage-type, #stage-text-stage-type", helpCode: "stageType"},
        {selector: "#stage-metric-quick-range, #stage-text-quick-range", helpCode: "quickPeriod"},
        {selector: "#stage-metric-from-a, #stage-metric-to-a, #stage-text-from-a, #stage-text-to-a", helpCode: "period"},
        {selector: "#stage-text-metric-type", helpCode: "stageTextMetric"},
        {selector: "#events-from, #events-to", helpCode: "period"},
        {selector: "#events-quick-range", helpCode: "quickPeriod"},
        {selector: "#events-is-error, #events-error-class", helpCode: "rawStatus"},
        {selector: "#events-event-type", helpCode: "rawEvent"},
        {selector: "#events-metric-type, #events-metric-min, #events-metric-max, #events-min-duration, #events-attribute-code, #events-attribute-value, #events-sort-by, #events-sort-dir, #events-advanced-toggle", helpCode: "rawAdvanced"},
        {selector: "[data-expanded-preset], [data-expanded-bucket], [data-expanded-compare-mode], [data-expanded-latency-metric], [data-expanded-stage-latency-event-metric], [data-range]", helpCode: "expandedPeriod", placement: "before"}
    ];

    const refs = {};

    document.addEventListener("DOMContentLoaded", () => {
        ensureGlobalLoader();
        initRefs();
        ensureParameterHelpButtons();
        bindEvents();
        initDashboardViewMode();
        initDefaultCompareRange();
        void initDashboard();
    });

    function api(path) {
        if (!path) {
            return API_BASE;
        }
        if (path.startsWith("/")) {
            return `${API_BASE}${path}`;
        }
        return `${API_BASE}/${path}`;
    }

    function initRefs() {
        refs.mainForm = document.getElementById("analytics-main-form");
        refs.moduleType = document.getElementById("analytics-module-type");
        refs.eventType = document.getElementById("analytics-event-type");
        refs.analyticsRequestPath = document.getElementById("analytics-request-path");
        refs.from = document.getElementById("analytics-from");
        refs.to = document.getElementById("analytics-to");
        refs.bucket = document.getElementById("analytics-bucket");
        refs.quickRangeCount = document.getElementById("analytics-range-n");
        refs.quickRangeUnit = document.getElementById("analytics-range-unit");
        refs.quickRangeApply = document.getElementById("analytics-range-apply");
        refs.quickRangePresetSelect = document.getElementById("analytics-quick-preset-select");
        refs.globalCompareModeOff = document.getElementById("analytics-global-compare-mode-off");
        refs.globalCompareModeSplit = document.getElementById("analytics-global-compare-mode-split");
        refs.globalCompareModeOverlay = document.getElementById("analytics-global-compare-mode-overlay");
        refs.analysisScenario = document.getElementById("analytics-analysis-scenario");
        refs.analysisScenarioHelp = document.getElementById("analytics-analysis-scenario-help");
        refs.globalComparePreset = document.getElementById("analytics-global-compare-preset");
        refs.globalBeforeSummary = document.getElementById("analytics-global-before-summary");
        refs.globalCompareGhost = document.getElementById("analytics-global-compare-ghost");
        refs.globalBeforeRow = document.getElementById("analytics-global-before-row");
        refs.globalBeforeFrom = document.getElementById("analytics-global-before-from");
        refs.globalBeforeTo = document.getElementById("analytics-global-before-to");
        refs.globalAttrCode = document.getElementById("analytics-global-attr-code");
        refs.globalAttrValueSelect = document.getElementById("analytics-global-attr-value-select");
        refs.globalAttrValueInput = document.getElementById("analytics-global-attr-value-input");
        refs.globalAttrValueRow = document.getElementById("analytics-global-attr-value-row");
        refs.globalAttrRangeRow = document.getElementById("analytics-global-attr-range-row");
        refs.globalAttrMin = document.getElementById("analytics-global-attr-min");
        refs.globalAttrMax = document.getElementById("analytics-global-attr-max");
        refs.globalAttrMinRange = document.getElementById("analytics-global-attr-min-range");
        refs.globalAttrMaxRange = document.getElementById("analytics-global-attr-max-range");
        refs.globalMetricCode = refs.globalAttrCode;
        refs.globalMetricValueSelect = refs.globalAttrValueSelect;
        refs.globalMetricValueInput = refs.globalAttrValueInput;
        refs.globalMetricValueRow = refs.globalAttrValueRow;
        refs.globalMetricRangeRow = refs.globalAttrRangeRow;
        refs.globalMetricMin = refs.globalAttrMin;
        refs.globalMetricMax = refs.globalAttrMax;
        refs.globalMetricMinRange = refs.globalAttrMinRange;
        refs.globalMetricMaxRange = refs.globalAttrMaxRange;
        refs.analyticsTabOverview = document.getElementById("analytics-tab-overview");
        refs.analyticsTabUniversal = document.getElementById("analytics-tab-universal");
        refs.analyticsTabMetrics = document.getElementById("analytics-tab-metrics");
        refs.analyticsTabRaw = document.getElementById("analytics-tab-raw");
        refs.analyticsTabCompare = document.getElementById("analytics-tab-compare");
        refs.analyticsTabButtons = document.querySelectorAll("[data-analytics-tab]");
        refs.analyticsTopTabButtons = document.querySelectorAll("[data-analytics-top-tab]");
        refs.analyticsOverviewSections = document.querySelectorAll("[data-analytics-view='overview']");
        refs.analyticsUniversalSections = document.querySelectorAll("[data-analytics-view='universal']");
        refs.analyticsMetricsSections = document.querySelectorAll("[data-analytics-view='metrics']");
        refs.analyticsRawSections = document.querySelectorAll("[data-analytics-view='raw']");
        refs.analyticsCompareSections = document.querySelectorAll("[data-analytics-view='compare']");
        refs.universalForm = document.getElementById("analytics-universal-form");
        refs.universalEventTypeToggle = document.getElementById("universal-event-type-toggle");
        refs.universalEventTypePopup = document.getElementById("universal-event-type-popup");
        refs.universalEventTypeList = document.getElementById("universal-event-type-list");
        refs.universalEventOverall = document.getElementById("universal-event-overall");
        refs.universalStageType = document.getElementById("universal-stage-type");
        refs.universalAttrCode = document.getElementById("universal-attr-code");
        refs.universalAttrValue = document.getElementById("universal-attr-value");
        refs.universalScenario = document.getElementById("universal-scenario");
        refs.universalFrom = document.getElementById("universal-from");
        refs.universalTo = document.getElementById("universal-to");
        refs.universalBeforeFrom = document.getElementById("universal-before-from");
        refs.universalBeforeTo = document.getElementById("universal-before-to");
        refs.universalBeforeWrap = document.getElementById("universal-before-wrap");
        refs.universalBeforeWrapTo = document.getElementById("universal-before-wrap-to");
        refs.universalBeforeSummary = document.getElementById("universal-before-summary");
        refs.universalBucket = document.getElementById("universal-bucket");
        refs.universalQuickPreset = document.getElementById("universal-quick-preset");
        refs.universalSeriesMetricToggle = document.getElementById("universal-series-metric-toggle");
        refs.universalSeriesMetricPopup = document.getElementById("universal-series-metric-popup");
        refs.universalSeriesMetricList = document.getElementById("universal-series-metric-list");
        refs.universalStageMetricToggle = document.getElementById("universal-stage-metric-toggle");
        refs.universalStageMetricPopup = document.getElementById("universal-stage-metric-popup");
        refs.universalStageMetricList = document.getElementById("universal-stage-metric-list");
        refs.universalTimelineZoomX = document.getElementById("universal-timeline-zoom-x");
        refs.universalTimelineZoomY = document.getElementById("universal-timeline-zoom-y");
        refs.universalStagesZoomX = document.getElementById("universal-stages-zoom-x");
        refs.universalStagesZoomY = document.getElementById("universal-stages-zoom-y");
        refs.universalEventKpiZoomX = document.getElementById("universal-event-kpi-zoom-x");
        refs.universalEventKpiZoomY = document.getElementById("universal-event-kpi-zoom-y");
        refs.universalCompareEnabled = document.getElementById("universal-compare-enabled");
        refs.universalCompareGhost = document.getElementById("universal-compare-ghost");
        refs.universalCompareWrap = document.querySelector(".analytics-universal-compare-wrap");
        refs.universalSeriesToggles = document.querySelectorAll("[data-universal-series]");
        refs.universalGrid = document.getElementById("analytics-universal-grid");
        refs.universalTimelineCard = document.getElementById("analytics-universal-timeline-card");
        refs.universalStagesCard = document.getElementById("analytics-universal-stages-card");
        refs.universalPanel = refs.universalForm?.closest("section.analytics-panel") || null;

        refs.kpiTotalEvents = document.getElementById("kpi-total-events");
        refs.kpiAvgMs = document.getElementById("kpi-avg-ms");
        refs.kpiP95Ms = document.getElementById("kpi-p95-ms");
        refs.kpiP99Ms = document.getElementById("kpi-p99-ms");
        refs.kpiErrorRate = document.getElementById("kpi-error-rate");
        refs.kpiErrors = document.getElementById("kpi-errors");


        refs.stageTableBody = document.querySelector("#analytics-stage-table tbody");
        refs.stageMetricForm = document.getElementById("analytics-stage-metrics-form");
        refs.stageMetricStageType = document.getElementById("stage-metric-stage-type");
        refs.stageMetricQuickRange = document.getElementById("stage-metric-quick-range");
        refs.stageMetricFromA = document.getElementById("stage-metric-from-a");
        refs.stageMetricToA = document.getElementById("stage-metric-to-a");
        refs.stageMetricCompareMode = document.getElementById("stage-metric-compare-mode");
        refs.stageMetricCompareEnabled = document.getElementById("stage-metric-compare-enabled");
        refs.stageMetricFromB = document.getElementById("stage-metric-from-b");
        refs.stageMetricToB = document.getElementById("stage-metric-to-b");
        refs.stageMetricCompareControlsWrap = document.getElementById("stage-metric-compare-controls-wrap");
        refs.stageMetricCompareSummary = document.getElementById("stage-metric-compare-summary");
        refs.stageMetricCompareCol = document.getElementById("analytics-stage-metric-compare-col");
        refs.stageMetricTextCompareCol = document.getElementById("analytics-stage-metric-text-compare-col");
        refs.stageMetricReset = document.getElementById("stage-metric-reset");
        refs.stageMetricTableBody = document.querySelector("#analytics-stage-metric-table tbody");
        refs.stageMetricTextTableBody = document.querySelector("#analytics-stage-metric-text-table tbody");
        refs.stageTextForm = document.getElementById("analytics-stage-text-form");
        refs.stageTextStageType = document.getElementById("stage-text-stage-type");
        refs.stageTextMetricType = document.getElementById("stage-text-metric-type");
        refs.stageTextQuickRange = document.getElementById("stage-text-quick-range");
        refs.stageTextCompareMode = document.getElementById("stage-text-compare-mode");
        refs.stageTextCompareEnabled = document.getElementById("stage-text-compare-enabled");
        refs.stageTextFromA = document.getElementById("stage-text-from-a");
        refs.stageTextToA = document.getElementById("stage-text-to-a");
        refs.stageTextFromB = document.getElementById("stage-text-from-b");
        refs.stageTextToB = document.getElementById("stage-text-to-b");
        refs.stageTextCompareControlsWrap = document.getElementById("stage-text-compare-controls-wrap");
        refs.stageTextCompareSummary = document.getElementById("stage-text-compare-summary");
        refs.stageTextReset = document.getElementById("stage-text-reset");
        refs.stageMetricPanel = refs.stageMetricForm?.closest("section.analytics-panel") || null;

        refs.eventsForm = document.getElementById("analytics-events-form");
        refs.eventsIsError = document.getElementById("events-is-error");
        refs.eventsErrorClassWrap = document.getElementById("events-error-class-wrap");
        refs.eventsErrorClass = document.getElementById("events-error-class");
        refs.eventsEventType = document.getElementById("events-event-type");
        refs.eventsFrom = document.getElementById("events-from");
        refs.eventsTo = document.getElementById("events-to");
        refs.eventsQuickRange = document.getElementById("events-quick-range");
        refs.eventsMetricType = document.getElementById("events-metric-type");
        refs.eventsMetricMin = document.getElementById("events-metric-min");
        refs.eventsMetricMax = document.getElementById("events-metric-max");
        refs.eventsMinDuration = document.getElementById("events-min-duration");
        refs.eventsRequestPath = document.getElementById("events-request-path");
        refs.eventsAttributeCode = document.getElementById("events-attribute-code");
        refs.eventsAttributeValue = document.getElementById("events-attribute-value");
        refs.eventsAdvancedWrap = document.getElementById("events-advanced-wrap");
        refs.eventsAdvancedToggle = document.getElementById("events-advanced-toggle");
        refs.eventsSortBy = document.getElementById("events-sort-by");
        refs.eventsSortDir = document.getElementById("events-sort-dir");
        refs.eventsTableBody = document.querySelector("#analytics-events-table tbody");
        refs.eventsLoadMore = document.getElementById("events-load-more");
        refs.eventsPanel = refs.eventsForm?.closest("section.analytics-panel") || null;

        refs.compareForm = document.getElementById("analytics-compare-form");
        refs.compareBaselineFrom = document.getElementById("compare-baseline-from");
        refs.compareBaselineTo = document.getElementById("compare-baseline-to");
        refs.compareTargetFrom = document.getElementById("compare-target-from");
        refs.compareTargetTo = document.getElementById("compare-target-to");
        refs.compareQuickRange = document.getElementById("compare-quick-range");
        refs.compareCards = document.getElementById("compare-kpi-cards");

        refs.eventModalEl = document.getElementById("analytics-event-modal");
        refs.eventModalBody = document.getElementById("analytics-event-modal-body");
        refs.helpModalEl = document.getElementById("analytics-help-modal");
        refs.helpModalTitle = document.getElementById("analytics-help-modal-title");
        refs.helpModalBody = document.getElementById("analytics-help-modal-body");
        refs.analyticsPage = document.querySelector(".analytics-page");
        refs.filtersFab = document.querySelector(".analytics-filter-fab");
        refs.filtersPanel = document.querySelector(".analytics-hero-floating");
        refs.floatingReset = document.getElementById("analytics-floating-reset");
    }

    function bindEvents() {
        const runMainFiltersOnce = async () => {
            const nextMainRangeKey = buildMainRangeKey();
            const mainRangeChanged = !!state.lastMainRangeKey && state.lastMainRangeKey !== nextMainRangeKey;
            state.lastMainRangeKey = nextMainRangeKey;
            if (mainRangeChanged && state.globalCompareEnabled) {
                state.globalCompareBeforeCustom = false;
            }
            clearAllChartLocalOverrides();
            resetInlineComparePresetsFromTopFilter();
            state.expandedRangesBySource = {};
            state.expandedBucketBySource = {};
            syncStageMetricQuickRangeFromMain();
            syncStageMetricRangesFromMain(true);
            syncStageTextQuickRangeFromMain();
            syncStageTextRangesFromMain(true);
            syncEventsRangeFromMain(true);
            syncUniversalRangeFromMain(true);
            await refreshScopedOptionsSafe();
            applyGlobalMetricToEventsFilter();
            state.eventsPage = 0;
            await reloadAll();
            if (state.globalCompareEnabled) {
                await applyGlobalCompareToAllCharts();
            }
            await syncExpandedGraphFiltersFromTop();
        };
        const submitMainFilters = async () => {
            if (state.mainFiltersSubmitting) {
                state.mainFiltersSubmitPending = true;
                return;
            }
            state.mainFiltersSubmitting = true;
            try {
                do {
                    state.mainFiltersSubmitPending = false;
                    await runMainFiltersOnce();
                } while (state.mainFiltersSubmitPending);
            } finally {
                state.mainFiltersSubmitting = false;
            }
        };
        const submitStageMetricFilters = async () => {
            await withStageMetricLoaders("all", () => loadStageMetrics());
        };
        const submitStageMetricTextFilters = async () => {
            await withStageMetricLoaders("text", () => loadStageMetricTextBlock());
        };
        const submitEventsFilters = async () => {
            state.eventsPage = 0;
            await loadEvents(true);
        };
        const submitUniversalFilters = async (options = {}) => {
            const action = options.action || "";
            await withUniversalChartLoaders(
                () => action === "mode_to_overall"
                    ? reloadUniversalOverallFromScratch({action})
                    : loadUniversal({
                        action,
                        allowStaleMainPayload: action === "compare_to_off"
                    }),
                {action}
            );
        };
        const submitCompareFilters = async () => {
            await loadCompare();
        };
        const debouncedMainFilters = debounce(() => {
            void submitMainFilters();
        }, 420);
        const debouncedStageMetricFilters = debounce(() => {
            void submitStageMetricFilters();
        }, 420);
        const debouncedStageMetricTextFilters = debounce(() => {
            void submitStageMetricTextFilters();
        }, 300);
        const debouncedEventsFilters = debounce(() => {
            void submitEventsFilters();
        }, 420);

        refs.helpModalBody?.addEventListener("click", (event) => {
            const scenarioButton = event.target?.closest?.("[data-help-scenario-chart][data-help-scenario-id]");
            if (!scenarioButton) {
                return;
            }
            event.preventDefault();
            openChartScenarioHelpModal(
                scenarioButton.getAttribute("data-help-scenario-chart") || "",
                scenarioButton.getAttribute("data-help-scenario-id") || ""
            );
        });
        document.addEventListener("pointerdown", handleParameterHelpPointerDown, true);
        document.addEventListener("click", handleParameterHelpClick, true);

        refs.analyticsTabButtons?.forEach((button) => {
            button.addEventListener("click", () => {
                const tab = (button.getAttribute("data-analytics-tab") || "overview").trim();
                setDashboardViewTab(tab, true);
            });
        });
        refs.analyticsTopTabButtons?.forEach((button) => {
            button.addEventListener("click", (event) => {
                const tab = (button.getAttribute("data-analytics-top-tab") || "overview").trim();
                event.preventDefault();
                setDashboardViewTab(tab, true);
            });
        });
        refs.filtersFab?.addEventListener("click", (event) => {
            event.preventDefault();
        });
        refs.floatingReset?.addEventListener("click", async () => {
            if (refs.moduleType) refs.moduleType.value = "";
            if (refs.eventType) refs.eventType.value = "";
            if (refs.analyticsRequestPath) refs.analyticsRequestPath.value = "";
            if (refs.bucket) refs.bucket.value = "";
            if (refs.globalMetricCode) refs.globalMetricCode.value = "";
            if (refs.globalMetricValueSelect) refs.globalMetricValueSelect.value = "";
            if (refs.globalMetricValueInput) refs.globalMetricValueInput.value = "";
            if (refs.globalMetricMin) refs.globalMetricMin.value = "";
            if (refs.globalMetricMax) refs.globalMetricMax.value = "";
            if (refs.analysisScenario) refs.analysisScenario.value = "";
            state.globalScenarioCode = "";
            state.globalAnalysisScenario = "";
            syncGlobalScenarioPicker();
            setGlobalCompareMode("off");
            state.globalCompareBeforeCustom = false;
            clearAllChartLocalOverrides();
            if (refs.quickRangePresetSelect) {
                refs.quickRangePresetSelect.value = "24h";
                await applyQuickRangePreset("24h");
                await applyGlobalCompareToAllCharts();
                return;
            }
            if (refs.mainForm) {
                await submitMainFilters();
                await applyGlobalCompareToAllCharts();
            }
        });

        refs.mainForm?.addEventListener("submit", async (event) => {
            event.preventDefault();
            await submitMainFilters();
        });

        refs.quickRangeApply?.addEventListener("click", async () => {
            await applyQuickRangeFromControls();
        });
        refs.quickRangePresetSelect?.addEventListener("change", async () => {
            const quickRange = refs.quickRangePresetSelect.value || "";
            if (!quickRange) {
                syncQuickRangeSelectFromRange(refs.quickRangePresetSelect, refs.from?.value || "", refs.to?.value || "");
                return;
            }
            await applyQuickRangePreset(quickRange);
        });
        [refs.globalCompareModeOff, refs.globalCompareModeSplit, refs.globalCompareModeOverlay]
            .filter(Boolean)
            .forEach((radio) => {
                radio.addEventListener("change", async () => {
                    if (!radio.checked) {
                        return;
                    }
                    const previousMode = resolveGlobalInlineCompareMode();
                    const nextMode = radio.value || "off";
                    const compareToOff = previousMode !== "off" && nextMode === "off";
                    if (!compareToOff) {
                        showUniversalChartLoaders();
                    }
                    showStageMetricLoaders("all");
                    setGlobalCompareMode(nextMode);
                    state.globalCompareBeforeCustom = false;
                    clearAllChartLocalOverrides();
                    await applyGlobalCompareToAllCharts({
                        action: compareToOff ? "compare_to_off" : "global_compare_change"
                    });
                });
            });
        refs.globalComparePreset?.addEventListener("change", async () => {
            showUniversalChartLoaders();
            showStageMetricLoaders("all");
            state.globalComparePreset = "";
            refs.globalComparePreset.value = "";
            state.globalCompareBeforeCustom = false;
            clearAllChartLocalOverrides();
            await applyGlobalCompareToAllCharts();
        });
        [refs.globalBeforeFrom, refs.globalBeforeTo].forEach((control) => {
            control?.addEventListener("change", async () => {
                showUniversalChartLoaders();
                showStageMetricLoaders("all");
                state.globalCompareBeforeCustom = false;
                clearAllChartLocalOverrides();
                await applyGlobalCompareToAllCharts();
            });
        });
        refs.globalMetricCode?.addEventListener("change", async () => {
            if (refs.globalMetricValueInput) {
                refs.globalMetricValueInput.value = "";
            }
            if (refs.globalMetricValueSelect) {
                refs.globalMetricValueSelect.value = "";
            }
            if (refs.globalMetricMin) {
                refs.globalMetricMin.value = "";
            }
            if (refs.globalMetricMax) {
                refs.globalMetricMax.value = "";
            }
            // Keep selected attribute code; reset only its value/range controls.
            await refreshGlobalMetricBlock(false);
            applyGlobalMetricToEventsFilter();
            await submitMainFilters();
        });
        refs.globalMetricValueSelect?.addEventListener("change", async () => {
            if (refs.globalMetricValueInput) {
                refs.globalMetricValueInput.value = refs.globalMetricValueSelect?.value || "";
            }
            applyGlobalMetricToEventsFilter();
            await submitMainFilters();
        });
        refs.globalMetricValueInput?.addEventListener("input", debounce(() => {
            applyGlobalMetricToEventsFilter();
            void submitMainFilters();
        }, 320));
        [refs.globalMetricMin, refs.globalMetricMax].forEach((control) => {
            control?.addEventListener("change", async () => {
                syncGlobalMetricNumericRangeFromInputs();
                applyGlobalMetricToEventsFilter();
                await submitMainFilters();
            });
            control?.addEventListener("input", debounce(() => {
                syncGlobalMetricNumericRangeFromInputs();
                applyGlobalMetricToEventsFilter();
                void submitMainFilters();
            }, 320));
        });
        refs.globalMetricMinRange?.addEventListener("input", () => {
            syncGlobalMetricNumericRangeFromSliders("min");
            applyGlobalMetricToEventsFilter();
            void submitMainFilters();
        });
        refs.globalMetricMaxRange?.addEventListener("input", () => {
            syncGlobalMetricNumericRangeFromSliders("max");
            applyGlobalMetricToEventsFilter();
            void submitMainFilters();
        });

        refs.stageMetricForm?.addEventListener("submit", async (event) => {
            event.preventDefault();
            await submitStageMetricFilters();
        });

        refs.eventsForm?.addEventListener("submit", async (event) => {
            event.preventDefault();
            await submitEventsFilters();
        });
        refs.eventsIsError?.addEventListener("change", () => {
            updateEventsErrorClassVisibility();
        });
        refs.eventsAdvancedToggle?.addEventListener("click", () => {
            const isHidden = refs.eventsAdvancedWrap?.classList.contains("d-none");
            refs.eventsAdvancedWrap?.classList.toggle("d-none", !isHidden);
        });
        refs.eventsQuickRange?.addEventListener("change", async () => {
            const preset = (refs.eventsQuickRange.value || "").trim();
            if (!preset) {
                return;
            }
            await applyEventsQuickRangePreset(preset);
        });

        refs.eventsLoadMore?.addEventListener("click", async () => {
            if (!state.eventsHasMore) {
                return;
            }
            state.eventsPage += 1;
            await loadEvents(false);
        });

        refs.eventsTableBody?.addEventListener("click", async (event) => {
            const button = event.target.closest("[data-event-uid], [data-event-id]");
            if (!button) {
                return;
            }
            const eventId = (button.getAttribute("data-event-id") || "").trim();
            const uid = (button.getAttribute("data-event-uid") || "").trim();
            try {
                if (uid && uid.toLowerCase() !== "null" && uid.toLowerCase() !== "undefined") {
                    await openEventDetails(uid);
                    return;
                }
                if (eventId && eventId.toLowerCase() !== "null" && eventId.toLowerCase() !== "undefined") {
                    await openEventDetailsById(eventId);
                    return;
                }
                showEventModalError("У этого события отсутствуют идентификаторы, открыть детали невозможно.");
            } catch (error) {
                console.error("Event details failed", error);
                const detail = error instanceof Error ? error.message : "неизвестная ошибка";
                showEventModalError(`Не удалось загрузить детали события: ${detail}`);
            }
        });

        refs.eventModalBody?.addEventListener("click", async (event) => {
            const rawToggleButton = event.target.closest(".analytics-log-raw-toggle");
            if (rawToggleButton) {
                event.preventDefault();
                toggleRawLogRow(rawToggleButton);
                return;
            }
            const messageToggleButton = event.target.closest(".analytics-log-message-toggle");
            if (messageToggleButton) {
                event.preventDefault();
                toggleMessageCell(messageToggleButton);
                return;
            }
            const copyRawButton = event.target.closest(".analytics-log-copy-raw");
            if (copyRawButton) {
                event.preventDefault();
                await copyRawLogRow(copyRawButton);
            }
        });

        refs.compareForm?.addEventListener("submit", async (event) => {
            event.preventDefault();
            await submitCompareFilters();
        });
        refs.compareQuickRange?.addEventListener("change", async () => {
            await applyCompareQuickRangePreset();
            await submitCompareFilters();
        });

        const syncMainQuickRangeFromInputs = () => {
            syncQuickRangeSelectFromRange(refs.quickRangePresetSelect, refs.from?.value || "", refs.to?.value || "");
        };
        refs.from?.addEventListener("change", initDefaultCompareRange);
        refs.to?.addEventListener("change", initDefaultCompareRange);
        refs.from?.addEventListener("input", initDefaultCompareRange);
        refs.to?.addEventListener("input", initDefaultCompareRange);
        refs.from?.addEventListener("change", syncMainQuickRangeFromInputs);
        refs.to?.addEventListener("change", syncMainQuickRangeFromInputs);
        refs.from?.addEventListener("input", syncMainQuickRangeFromInputs);
        refs.to?.addEventListener("input", syncMainQuickRangeFromInputs);
        refs.from?.addEventListener("change", () => syncStageMetricRangesFromMain(true));
        refs.to?.addEventListener("change", () => syncStageMetricRangesFromMain(true));
        refs.from?.addEventListener("input", () => syncStageMetricRangesFromMain(true));
        refs.to?.addEventListener("input", () => syncStageMetricRangesFromMain(true));

        refs.moduleType?.addEventListener("change", async () => {
            await loadDictionaries();
            await submitMainFilters();
        });

        [refs.eventType, refs.bucket, refs.from, refs.to, refs.analyticsRequestPath].forEach((control) => {
            control?.addEventListener("change", () => {
                void submitMainFilters();
            });
        });
        refs.analyticsRequestPath?.addEventListener("input", debouncedMainFilters);

        [refs.stageMetricStageType].forEach((control) => {
            control?.addEventListener("change", () => {
                setStageMetricPerfAction("metric_change");
                showStageMetricLoaders("all");
                void submitStageMetricFilters();
            });
        });
        [refs.stageMetricFromA, refs.stageMetricToA, refs.stageMetricFromB, refs.stageMetricToB].forEach((control) => {
            control?.addEventListener("change", () => {
                syncStageMetricQuickRangeFromInputs();
                setStageMetricPerfAction("period_change");
                showStageMetricLoaders("all");
                void submitStageMetricFilters();
            });
            control?.addEventListener("input", () => {
                syncStageMetricQuickRangeFromInputs();
                setStageMetricPerfAction("period_change");
                showStageMetricLoaders("all");
                debouncedStageMetricFilters();
            });
        });
        refs.stageMetricCompareMode?.addEventListener("change", async () => {
            const previousMode = readStageMetricCompareMode();
            const nextMode = normalizeCompareMode(refs.stageMetricCompareMode.value || "off");
            setStageMetricPerfAction(resolveStageMetricCompareAction(previousMode, nextMode));
            showStageMetricLoaders("all");
            setStageMetricCompareMode(refs.stageMetricCompareMode.value || "off", {syncText: !refs.stageTextCompareMode});
            await submitStageMetricFilters();
        });
        refs.stageMetricCompareEnabled?.addEventListener("change", async () => {
            const previousMode = readStageMetricCompareMode();
            const nextMode = refs.stageMetricCompareEnabled.checked ? "split" : "off";
            setStageMetricPerfAction(resolveStageMetricCompareAction(previousMode, nextMode));
            showStageMetricLoaders("all");
            setStageMetricCompareMode(refs.stageMetricCompareEnabled.checked ? "split" : "off", {syncText: !refs.stageTextCompareMode});
            updateStageMetricCompareUi();
            await submitStageMetricFilters();
        });
        refs.stageMetricQuickRange?.addEventListener("change", async () => {
            setStageMetricPerfAction("period_change");
            showStageMetricLoaders("all");
            await applyStageMetricQuickRange();
            await submitStageMetricFilters();
        });
        refs.stageMetricReset?.addEventListener("click", async () => {
            setStageMetricPerfAction("period_change");
            showStageMetricLoaders("all");
            syncStageMetricQuickRangeFromMain();
            syncStageMetricRangesFromMain(true);
            await submitStageMetricFilters();
        });

        const handleStageMetricTableChange = (event) => {
            const checkbox = event.target.closest(".analytics-metric-toggle");
            if (!checkbox) {
                return;
            }
            const code = (checkbox.getAttribute("data-metric-code") || "").trim();
            if (!code) {
                return;
            }
            const targetList = state.stageMetricSelectedCodes;
            if (checkbox.checked) {
                if (!targetList.includes(code)) {
                    state.stageMetricSelectedCodes = [...targetList, code];
                }
            } else {
                state.stageMetricSelectedCodes = targetList.filter((item) => item !== code);
            }
            showStageMetricLoaders("numeric");
            setStageMetricPerfAction("metric_change");
            void withStageMetricLoaders("numeric", () => loadStageMetricComparisonSeries());
        };
        refs.stageMetricTableBody?.addEventListener("change", handleStageMetricTableChange);
        refs.stageMetricTableBody?.addEventListener("click", (event) => {
            const helpButton = event.target.closest(".analytics-metric-help-badge");
            if (!helpButton) {
                return;
            }
            event.preventDefault();
            const metricCode = (helpButton.getAttribute("data-metric-code") || "").trim();
            if (!metricCode) {
                return;
            }
            openMetricHelpModal(metricCode);
        });
        refs.stageMetricTextTableBody?.addEventListener("click", (event) => {
            const helpButton = event.target.closest(".analytics-metric-help-badge");
            if (!helpButton) {
                return;
            }
            event.preventDefault();
            const metricCode = (helpButton.getAttribute("data-metric-code") || "").trim();
            if (!metricCode) {
                return;
            }
            openMetricHelpModal(metricCode);
        });
        refs.stageTextStageType?.addEventListener("change", async () => {
            setStageMetricPerfAction("metric_change");
            showStageMetricLoaders("text");
            await withStageMetricLoaders("text", () => loadStageMetricTextBlock());
        });
        refs.stageTextMetricType?.addEventListener("change", async () => {
            setStageMetricPerfAction("metric_change");
            showStageMetricLoaders("text");
            const selected = (refs.stageTextMetricType.value || "").trim();
            state.stageMetricTextSelectedCodes = selected ? [selected] : [];
            await submitStageMetricTextFilters();
        });
        refs.stageTextCompareMode?.addEventListener("change", async () => {
            const previousMode = readStageTextCompareMode();
            const nextMode = normalizeCompareMode(refs.stageTextCompareMode.value || "off");
            setStageMetricPerfAction(resolveStageMetricCompareAction(previousMode, nextMode));
            showStageMetricLoaders("text");
            setStageTextCompareMode(refs.stageTextCompareMode.value || "off");
            await submitStageMetricTextFilters();
        });
        refs.stageTextCompareEnabled?.addEventListener("change", async () => {
            const previousMode = readStageTextCompareMode();
            const nextMode = refs.stageTextCompareEnabled.checked ? "split" : "off";
            setStageMetricPerfAction(resolveStageMetricCompareAction(previousMode, nextMode));
            showStageMetricLoaders("text");
            setStageTextCompareMode(refs.stageTextCompareEnabled.checked ? "split" : "off");
            updateStageMetricTextCompareUi();
            await submitStageMetricTextFilters();
        });
        refs.stageTextQuickRange?.addEventListener("change", async () => {
            setStageMetricPerfAction("period_change");
            showStageMetricLoaders("text");
            await applyStageTextQuickRange();
            await submitStageMetricTextFilters();
        });
        [refs.stageTextFromA, refs.stageTextToA, refs.stageTextFromB, refs.stageTextToB].forEach((control) => {
            control?.addEventListener("change", async () => {
                syncStageTextQuickRangeFromInputs();
                showStageMetricLoaders("text");
                await submitStageMetricTextFilters();
            });
            control?.addEventListener("input", () => {
                syncStageTextQuickRangeFromInputs();
                showStageMetricLoaders("text");
                debouncedStageMetricTextFilters();
            });
        });
        refs.stageTextReset?.addEventListener("click", async () => {
            showStageMetricLoaders("text");
            syncStageTextQuickRangeFromMain();
            syncStageTextRangesFromMain(true);
            await submitStageMetricTextFilters();
        });

        [refs.eventsIsError, refs.eventsErrorClass, refs.eventsEventType, refs.eventsMetricType, refs.eventsAttributeCode, refs.eventsSortBy, refs.eventsSortDir]
            .forEach((control) => {
                control?.addEventListener("change", () => {
                    void submitEventsFilters();
                });
            });

        [refs.eventsMetricMin, refs.eventsMetricMax, refs.eventsMinDuration, refs.eventsRequestPath, refs.eventsAttributeValue]
            .forEach((control) => {
                control?.addEventListener("change", () => {
                    void submitEventsFilters();
                });
                control?.addEventListener("input", debouncedEventsFilters);
            });
        const debouncedEventsRangeFilters = debounce(() => {
            void submitEventsFilters();
        }, 260);
        [refs.eventsFrom, refs.eventsTo].forEach((control) => {
            control?.addEventListener("change", () => {
                syncEventsQuickRangeFromInputs();
                debouncedEventsRangeFilters();
            });
            control?.addEventListener("input", () => {
                syncEventsQuickRangeFromInputs();
                debouncedEventsRangeFilters();
            });
        });

        [refs.compareBaselineFrom, refs.compareBaselineTo, refs.compareTargetFrom, refs.compareTargetTo].forEach((control) => {
            control?.addEventListener("change", () => {
                syncStageMetricRangesFromMain(true);
                syncStageTextRangesFromMain(true);
                void submitCompareFilters();
            });
        });
        [refs.universalStageType, refs.universalAttrCode, refs.universalCompareEnabled, refs.universalCompareGhost].forEach((control) => {
            control?.addEventListener("change", () => {
                showUniversalChartLoaders();
                const compareToOff = control === refs.universalCompareEnabled
                    && !refs.universalCompareEnabled?.checked;
                if (control === refs.universalCompareEnabled) {
                    updateUniversalCompareUi();
                    if (!UNIVERSAL_COMPARE_FOLLOWS_GLOBAL) {
                        setUniversalCompareEnabled(!!refs.universalCompareEnabled?.checked);
                    } else {
                        syncUniversalCompareFromGlobalFilter();
                    }
                }
                if (UNIVERSAL_COMPARE_FOLLOWS_GLOBAL
                    && (control === refs.universalCompareEnabled || control === refs.universalCompareGhost)) {
                    syncUniversalCompareFromGlobalFilter();
                }
                if (control === refs.universalStageType) {
                    syncUniversalFilterModeUi();
                }
                void submitUniversalFilters({
                    action: compareToOff ? "compare_to_off" : ""
                });
            });
        });
        const debouncedUniversalRangeFilters = debounce(() => {
            void submitUniversalFilters();
        }, 260);
        const debouncedUniversalAttrFilters = debounce(() => {
            void submitUniversalFilters();
        }, 300);
        [refs.universalFrom, refs.universalTo, refs.universalBeforeFrom, refs.universalBeforeTo, refs.universalBucket].forEach((control) => {
            control?.addEventListener("change", () => {
                showUniversalChartLoaders();
                state.universalAllTime = false;
                if (control === refs.universalFrom || control === refs.universalTo) {
                    syncUniversalBeforeRangeFromAfter();
                    syncUniversalQuickRangeFromInputs();
                }
                debouncedUniversalRangeFilters();
            });
            control?.addEventListener("input", () => {
                showUniversalChartLoaders();
                state.universalAllTime = false;
                if (control === refs.universalFrom || control === refs.universalTo) {
                    syncUniversalBeforeRangeFromAfter();
                    syncUniversalQuickRangeFromInputs();
                }
                debouncedUniversalRangeFilters();
            });
        });
        refs.universalQuickPreset?.addEventListener("change", async () => {
            showUniversalChartLoaders();
            await applyUniversalQuickRangePreset(refs.universalQuickPreset?.value || "");
            void submitUniversalFilters();
        });
        refs.universalEventTypeToggle?.addEventListener("click", (event) => {
            event.preventDefault();
            syncUniversalFilterModeUi();
            if (resolveUniversalEventMetricMode() === "overall") {
                refs.universalEventTypePopup?.classList.add("d-none");
                refs.universalEventTypeToggle?.setAttribute("aria-expanded", "false");
                return;
            }
            const isOpen = !refs.universalEventTypePopup?.classList.contains("d-none");
            refs.universalEventTypePopup?.classList.toggle("d-none", isOpen);
            refs.universalEventTypeToggle?.setAttribute("aria-expanded", isOpen ? "false" : "true");
        });
        refs.universalEventOverall?.addEventListener("change", () => {
            showUniversalChartLoaders();
            const previousMode = resolveUniversalEventMetricMode();
            if (refs.universalEventOverall?.checked && refs.universalEventTypeList) {
                state.universalAnalysisMode = "overall";
                Array.from(refs.universalEventTypeList.options || []).forEach((option) => {
                    option.selected = false;
                });
            } else {
                state.universalAnalysisMode = "single-event";
            }
            enforceUniversalEventMetricDependency("events");
            syncUniversalFilterModeUi();
            const nextMode = resolveUniversalEventMetricMode();
            void submitUniversalFilters({
                action: nextMode === "overall" && previousMode !== "overall"
                    ? "mode_to_overall"
                    : "analysis_mode_change"
            });
        });
        refs.universalEventTypeList?.addEventListener("change", () => {
            showUniversalChartLoaders();
            const selectedCount = selectedUniversalEventCodes().size;
            if (state.universalAnalysisMode === "overall" && selectedCount > 0) {
                state.universalAnalysisMode = selectedCount > 1 ? "multi-event" : "single-event";
            }
            if (refs.universalEventOverall) {
                refs.universalEventOverall.checked = resolveUniversalEventMetricMode() === "overall";
            }
            enforceUniversalEventMetricDependency("events");
            syncUniversalFilterModeUi();
            void submitUniversalFilters();
        });
        refs.universalScenario?.addEventListener("change", () => {
            showUniversalChartLoaders();
            applyUniversalScenarios();
            void submitUniversalFilters();
        });
        refs.universalSeriesMetricToggle?.addEventListener("click", (event) => {
            event.preventDefault();
            syncUniversalFilterModeUi();
            const isOpen = !refs.universalSeriesMetricPopup?.classList.contains("d-none");
            refs.universalSeriesMetricPopup?.classList.toggle("d-none", isOpen);
            refs.universalSeriesMetricToggle?.setAttribute("aria-expanded", isOpen ? "false" : "true");
        });
        refs.universalSeriesMetricList?.addEventListener("change", () => {
            showUniversalChartLoaders();
            enforceUniversalSeriesRules();
            syncUniversalStageMetricsFromSeries();
            enforceUniversalEventMetricDependency("metrics");
            syncUniversalFilterModeUi();
            void submitUniversalFilters();
        });
        refs.universalStageMetricToggle?.addEventListener("click", (event) => {
            event.preventDefault();
            const isOpen = !refs.universalStageMetricPopup?.classList.contains("d-none");
            refs.universalStageMetricPopup?.classList.toggle("d-none", isOpen);
            refs.universalStageMetricToggle?.setAttribute("aria-expanded", isOpen ? "false" : "true");
        });
        refs.universalStageMetricList?.addEventListener("change", () => {
            showUniversalChartLoaders();
            enforceUniversalStageMetricRules();
            updateUniversalStageMetricToggleLabel();
            void submitUniversalFilters();
        });
        document.addEventListener("click", (event) => {
            const host = event.target.closest(".analytics-universal-metrics-inline");
            if (!host) {
                refs.universalSeriesMetricPopup?.classList.add("d-none");
                refs.universalSeriesMetricToggle?.setAttribute("aria-expanded", "false");
            }
            const stageHost = event.target.closest(".analytics-universal-stage-metrics-inline");
            if (!stageHost) {
                refs.universalStageMetricPopup?.classList.add("d-none");
                refs.universalStageMetricToggle?.setAttribute("aria-expanded", "false");
            }
            const eventHost = event.target.closest(".analytics-universal-events-inline");
            if (!eventHost) {
                refs.universalEventTypePopup?.classList.add("d-none");
                refs.universalEventTypeToggle?.setAttribute("aria-expanded", "false");
            }
        });
        [refs.universalSeriesMetricPopup].forEach(() => {
            enforceUniversalSeriesRules();
            syncUniversalStageMetricsFromSeries();
            syncUniversalFilterModeUi();
            updateUniversalStageMetricToggleLabel();
        });
        refs.universalSeriesToggles?.forEach((control) => {
            control.addEventListener("change", () => {
                showUniversalChartLoaders();
                const code = (control.getAttribute("data-universal-series") || "").trim();
                if (code === "stages") {
                    refs.universalGrid?.classList.toggle("analytics-universal-grid-single", !control.checked);
                    refs.universalStagesCard?.classList.toggle("d-none", !control.checked);
                    refs.universalTimelineCard?.classList.toggle("analytics-universal-timeline-wide", !control.checked);
                }
                enforceUniversalSeriesRules();
                void submitUniversalFilters();
            });
        });
        [refs.universalTimelineZoomX, refs.universalTimelineZoomY, refs.universalStagesZoomX, refs.universalStagesZoomY, refs.universalEventKpiZoomX, refs.universalEventKpiZoomY]
            .forEach((control) => {
                control?.addEventListener("input", () => {
                    applyUniversalChartZoom("chart-universal-timeline", refs.universalTimelineZoomX?.value, refs.universalTimelineZoomY?.value);
                    applyUniversalChartZoom("chart-universal-stages", refs.universalStagesZoomX?.value, refs.universalStagesZoomY?.value);
                    applyUniversalChartZoom("chart-universal-event-kpi", refs.universalEventKpiZoomX?.value, refs.universalEventKpiZoomY?.value);
                });
            });
        refs.universalAttrValue?.addEventListener("input", () => {
            showUniversalChartLoaders();
            debouncedUniversalAttrFilters();
        });
    }

    async function initDashboard() {
        try {
            clearDashboardDataStatus();
            ensureKpiMiniWrap(document.getElementById("chart-event-kpi"));
            initChartExpandUi();
            initHelpUi();
            await ensureAllTimeRangeLoaded();
            syncStageMetricRangesFromMain(true);
            syncStageTextQuickRangeFromMain();
            syncStageTextRangesFromMain(true);
            syncEventsRangeFromMain(true);
            syncUniversalRangeFromMain(true);
            updateEventsErrorClassVisibility();
            updateStageMetricCompareUi();
            updateStageMetricTextCompareUi();
            applyUniversalScenarios();
            updateUniversalCompareUi();
            setGlobalCompareMode(resolveGlobalCompareModeFromUi());
            if (!UNIVERSAL_COMPARE_FOLLOWS_GLOBAL) {
                setUniversalCompareEnabled(!!refs.universalCompareEnabled?.checked);
            } else {
                syncUniversalCompareFromGlobalFilter();
            }
            syncGlobalCompareControlsVisibility();
            try {
                await loadDictionaries();
            } catch (error) {
                console.error("Dictionaries bootstrap failed, continue with fallback", error);
            }
            try {
                await refreshEventTypeOptionsByScope();
            } catch (error) {
                console.error("Event-type options bootstrap failed, continue", error);
            }
            await reloadAll();
            await loadCompare();
            applyUniversalChartZoom("chart-universal-timeline", refs.universalTimelineZoomX?.value, refs.universalTimelineZoomY?.value);
            applyUniversalChartZoom("chart-universal-stages", refs.universalStagesZoomX?.value, refs.universalStagesZoomY?.value);
            applyUniversalChartZoom("chart-universal-event-kpi", refs.universalEventKpiZoomX?.value, refs.universalEventKpiZoomY?.value);
        } catch (error) {
            showDashboardDataStatus(error instanceof Error ? error.message : "Не удалось загрузить аналитические данные.", true);
            console.error("Analytics bootstrap failed", error);
        }
    }

    async function ensureAllTimeRangeLoaded() {
        if (state.allTimeRange?.from) {
            return state.allTimeRange;
        }
        try {
            const payload = await fetchJson(api("/range-start"));
            const fromIso = payload?.from || null;
            const fromDate = fromIso ? new Date(fromIso) : null;
            const fromLocal = fromDate && !Number.isNaN(fromDate.getTime())
                ? toDateTimeLocalString(fromDate)
                : ALL_TIME_START_LOCAL;
            state.allTimeRange = {
                from: fromLocal,
                to: toDateTimeLocalString(new Date())
            };
        } catch (_error) {
            state.allTimeRange = {
                from: ALL_TIME_START_LOCAL,
                to: toDateTimeLocalString(new Date())
            };
        }
        return state.allTimeRange;
    }

    function ensureUniversalChartZoomHost(canvasId) {
        const canvas = document.getElementById(canvasId);
        const wrap = canvas?.closest(".analytics-chart-wrap");
        if (!canvas || !wrap) {
            return null;
        }
        wrap.classList.add("analytics-universal-zoomable");
        let host = wrap.querySelector(".analytics-universal-zoom-host");
        if (!host) {
            host = document.createElement("div");
            host.className = "analytics-universal-zoom-host";
            canvas.parentNode.insertBefore(host, canvas);
            host.appendChild(canvas);
        }
        if (!state.universalZoomBaseByCanvas[canvasId]) {
            const baseWidth = Math.max(1, wrap.clientWidth || canvas.clientWidth || 800);
            const baseHeight = Math.max(1, wrap.clientHeight || canvas.clientHeight || 300);
            state.universalZoomBaseByCanvas[canvasId] = {
                width: baseWidth,
                height: baseHeight
            };
        }
        canvas.style.width = "100%";
        canvas.style.height = "100%";
        return host;
    }

    function applyUniversalChartZoom(canvasId, xValue, yValue) {
        const x = Math.max(100, Number(xValue) || 100);
        const y = Math.max(100, Number(yValue) || 100);
        const targetCanvasIds = [canvasId];
        const compareCanvasId = state.inlineCompareCanvasBySource?.[canvasId];
        if (compareCanvasId) {
            targetCanvasIds.push(compareCanvasId);
        }
        targetCanvasIds.forEach((targetId) => {
            const host = ensureUniversalChartZoomHost(targetId);
            if (!host) {
                return;
            }
            const chart = state.charts?.[targetId] || null;
            const base = state.universalZoomBaseByCanvas[targetId] || {width: 800, height: 300};
            const wrap = host.parentElement;

            if (targetId === "chart-universal-event-kpi" || targetId === "chart-universal-event-kpi-compare-inline") {
                const labelsCount = Array.isArray(chart?.data?.labels) ? chart.data.labels.length : 0;
                const viewportWidth = Math.max(1, wrap?.clientWidth || host.clientWidth || base.width || 800);
                const targetWidth = resolveKpiChartWidth({
                    columnsCount: labelsCount,
                    viewportWidth,
                    mode: targetId === "chart-universal-event-kpi-compare-inline" ? "expanded-compare" : "expanded-single",
                    xScale: x / 100
                });
                host.style.width = `${targetWidth}px`;
                host.style.minWidth = `${targetWidth}px`;
                host.style.maxWidth = `${targetWidth}px`;
                if (x === 100 && wrap) {
                    wrap.scrollLeft = 0;
                }
            } else {
                if (x === 100) {
                    host.style.width = "100%";
                    host.style.minWidth = "100%";
                    host.style.maxWidth = "100%";
                    if (wrap) {
                        wrap.scrollLeft = 0;
                    }
                } else {
                    const scaledWidth = Math.round(base.width * (x / 100));
                    host.style.width = `${scaledWidth}px`;
                    host.style.minWidth = `${scaledWidth}px`;
                    host.style.maxWidth = `${scaledWidth}px`;
                }
            }

            if (y === 100) {
                host.style.height = "100%";
                host.style.minHeight = "100%";
                host.style.maxHeight = "100%";
                if (wrap) {
                    wrap.scrollTop = 0;
                }
            } else {
                const scaledHeight = Math.round(base.height * (y / 100));
                host.style.height = `${scaledHeight}px`;
                host.style.minHeight = `${scaledHeight}px`;
                host.style.maxHeight = `${scaledHeight}px`;
            }
            if (chart) {
                chart.resize();
                chart.update("none");
            }
        });
    }

    async function refreshScopedOptionsSafe() {
        try {
            await refreshEventTypeOptionsByScope();
        } catch (error) {
            console.error("Scoped event-type refresh failed", error);
        }
        try {
            await refreshGlobalMetricBlock();
        } catch (error) {
            console.error("Scoped attribute refresh failed", error);
        }
    }

    function bindUniversalCompareScrollSync(canvasId) {
        if (!UNIVERSAL_COMPARE_CHART_IDS.has(canvasId)) {
            return;
        }
        const pair = refs.analyticsPage?.querySelector(`.analytics-chart-compare-pair[data-chart-id='${canvasId}']`);
        if (!pair || pair.dataset.universalScrollSyncBound === "1") {
            return;
        }
        const wraps = Array.from(pair.querySelectorAll(".analytics-chart-wrap.analytics-universal-zoomable"));
        if (wraps.length < 2) {
            return;
        }
        const leftWrap = wraps[0];
        const rightWrap = wraps[1];
        let syncing = false;
        const syncTo = (source, target) => {
            if (syncing) {
                return;
            }
            syncing = true;
            target.scrollLeft = source.scrollLeft;
            target.scrollTop = source.scrollTop;
            syncing = false;
        };
        leftWrap.addEventListener("scroll", () => syncTo(leftWrap, rightWrap));
        rightWrap.addEventListener("scroll", () => syncTo(rightWrap, leftWrap));
        pair.dataset.universalScrollSyncBound = "1";
    }

    function bindInlineMiniCompareScrollSync(canvasId) {
        const pair = refs.analyticsPage?.querySelector(`.analytics-chart-compare-pair[data-chart-id='${canvasId}']`);
        if (!pair || pair.dataset.miniScrollSyncBound === "1") {
            return;
        }
        const wraps = Array.from(pair.querySelectorAll(".analytics-chart-wrap"));
        if (wraps.length < 2) {
            return;
        }
        const leftWrap = wraps[0];
        const rightWrap = wraps[1];
        let leftScrollHost = leftWrap;
        let rightScrollHost = rightWrap;
        const isEventKpiCompare = canvasId === "chart-event-kpi";
        if (isEventKpiCompare) {
            leftScrollHost = resolveKpiMiniScrollHost(leftWrap);
            rightScrollHost = resolveKpiMiniScrollHost(rightWrap);
            leftScrollHost.classList.add("analytics-kpi-compare-scroll");
            rightScrollHost.classList.add("analytics-kpi-compare-scroll");
        }
        let syncing = false;
        const syncTo = (source, target) => {
            if (syncing) {
                return;
            }
            syncing = true;
            if (isEventKpiCompare) {
                syncScrollLeftByRatio(source, target);
                syncScrollTopByRatio(source, target);
            } else {
                target.scrollLeft = source.scrollLeft;
                target.scrollTop = source.scrollTop;
            }
            syncing = false;
        };
        leftScrollHost.addEventListener("scroll", () => syncTo(leftScrollHost, rightScrollHost));
        rightScrollHost.addEventListener("scroll", () => syncTo(rightScrollHost, leftScrollHost));
        pair.dataset.miniScrollSyncBound = "1";
    }

    function resolveKpiCompareSourceCanvasId(canvasId) {
        if (canvasId === "chart-event-kpi" || canvasId === "chart-event-kpi-compare-inline") {
            return "chart-event-kpi";
        }
        return "";
    }

    function resolveCompareSourceCanvasId(canvasId) {
        if (!canvasId) {
            return "";
        }
        const kpiSource = resolveKpiCompareSourceCanvasId(canvasId);
        if (kpiSource) {
            return kpiSource;
        }
        if (state.inlineCompareEnabled && Object.prototype.hasOwnProperty.call(state.inlineCompareEnabled, canvasId)) {
            return canvasId;
        }
        const mappedSource = Object.entries(state.inlineCompareCanvasBySource || {})
            .find((entry) => entry[1] === canvasId)?.[0];
        if (mappedSource) {
            return mappedSource;
        }
        return canvasId;
    }

    function resolveChartCompareMode(canvasId) {
        const sourceCanvasId = resolveCompareSourceCanvasId(canvasId);
        if (!sourceCanvasId || !INLINE_COMPARE_CHART_IDS.has(sourceCanvasId)) {
            return "off";
        }
        return resolveInlineCompareMode(sourceCanvasId);
    }

    function isChartSplitCompare(canvasId) {
        return resolveChartCompareMode(canvasId) === "split";
    }

    function isChartOverlayCompare(canvasId) {
        return resolveChartCompareMode(canvasId) === "overlay";
    }

    function isChartCompareEnabled(canvasId) {
        const sourceCanvasId = resolveCompareSourceCanvasId(canvasId);
        if (!sourceCanvasId) {
            return false;
        }
        if (INLINE_COMPARE_CHART_IDS.has(sourceCanvasId)) {
            return resolveInlineCompareMode(sourceCanvasId) !== "off";
        }
        const inlineEnabled = !!state.inlineCompareEnabled?.[sourceCanvasId];
        const localEnabled = !!state.localCompareEnabled?.[sourceCanvasId];
        const globalEnabled = resolveGlobalInlineCompareMode() !== "off";
        const globalApplicable = INLINE_COMPARE_CHART_IDS.has(sourceCanvasId);
        const expandedCompareEnabled = state.expandedChart?.sourceCanvasId === sourceCanvasId
            && !!state.inlineCompareEnabled?.[sourceCanvasId];
        return inlineEnabled || localEnabled || expandedCompareEnabled || (globalEnabled && globalApplicable);
    }

    function applyInitialKpiCompareScrollOffset(scrollBody, ratio = 0.06) {
        if (!scrollBody || scrollBody.dataset.initialKpiScrollApplied === "1") {
            return;
        }
        const max = Math.max(0, scrollBody.scrollWidth - scrollBody.clientWidth);
        if (max <= 0) {
            return;
        }
        const safeRatio = Math.max(0, Math.min(0.1, Number(ratio) || 0));
        scrollBody.scrollLeft = Math.min(max, Math.round(max * safeRatio));
        scrollBody.dataset.initialKpiScrollApplied = "1";
    }

    function applyInitialKpiCompareScrollOffsets(sourceCanvasId, ratio = 0.06) {
        const pair = refs.analyticsPage?.querySelector(
            `.analytics-chart-compare-pair[data-chart-id='${sourceCanvasId}']`
        );
        if (!pair) {
            return;
        }
        const wraps = Array.from(pair.querySelectorAll(".analytics-chart-wrap"));
        if (wraps.length < 2) {
            return;
        }
        wraps.forEach((wrap) => {
            const host = resolveKpiMiniScrollHost(wrap);
            if (!host) {
                return;
            }
            delete host.dataset.initialKpiScrollApplied;
            applyInitialKpiCompareScrollOffset(host, ratio);
        });
    }

    function queueInitialKpiCompareScrollOffsets(canvasId) {
        const sourceCanvasId = resolveKpiCompareSourceCanvasId(canvasId);
        if (!sourceCanvasId) {
            return;
        }
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                applyInitialKpiCompareScrollOffsets(sourceCanvasId, 0.06);
            });
        });
    }

    function getUniversalZoomRefsByCanvasId(canvasId) {
        if (canvasId === "chart-universal-timeline") {
            return {x: refs.universalTimelineZoomX, y: refs.universalTimelineZoomY};
        }
        if (canvasId === "chart-universal-stages") {
            return {x: refs.universalStagesZoomX, y: refs.universalStagesZoomY};
        }
        if (canvasId === "chart-universal-event-kpi") {
            return {x: refs.universalEventKpiZoomX, y: refs.universalEventKpiZoomY};
        }
        return {x: null, y: null};
    }

    function syncExpandedUniversalZoomFromMini(canvasId) {
        if (state.expandedChart.sourceCanvasId !== canvasId || !state.expandedChart.containerEl) {
            return;
        }
        const {x, y} = getUniversalZoomRefsByCanvasId(canvasId);
        if (!x || !y) {
            return;
        }
        const rangeX = state.expandedChart.containerEl.querySelector(".analytics-expanded-zoom-range-x");
        const rangeY = state.expandedChart.containerEl.querySelector(".analytics-expanded-zoom-range-y");
        if (rangeX && rangeX.value !== String(x.value || "100")) {
            rangeX.value = String(x.value || "100");
            rangeX.dispatchEvent(new Event("input"));
        }
        if (rangeY && rangeY.value !== String(y.value || "100")) {
            rangeY.value = String(y.value || "100");
            rangeY.dispatchEvent(new Event("input"));
        }
    }

    function initDashboardViewMode() {
        const params = new URLSearchParams(window.location.search);
        const tab = (params.get("tab") || "overview").toLowerCase();
        setDashboardViewTab(tab, false);
    }

    function setDashboardViewTab(tab, shouldPushState) {
        const normalizedTab = String(tab || "").trim().toLowerCase();
        const normalized = ["overview", "universal", "raw", "metrics", "compare"].includes(normalizedTab)
            ? normalizedTab
            : "overview";

        refs.analyticsOverviewSections?.forEach((section) => {
            section.hidden = normalized !== "overview";
        });
        refs.analyticsUniversalSections?.forEach((section) => {
            section.hidden = normalized !== "universal";
        });
        refs.analyticsMetricsSections?.forEach((section) => {
            section.hidden = normalized !== "metrics";
        });
        refs.analyticsRawSections?.forEach((section) => {
            section.hidden = normalized !== "raw";
        });
        refs.analyticsCompareSections?.forEach((section) => {
            section.hidden = normalized !== "compare";
        });

        const expandedCanvasId = state.expandedChart.sourceCanvasId || "";
        if (expandedCanvasId && getChartOwningTab(expandedCanvasId) !== normalized) {
            collapseExpandedChart();
        }

        refs.analyticsTabOverview?.classList.toggle("active", normalized === "overview");
        refs.analyticsTabUniversal?.classList.toggle("active", normalized === "universal");
        refs.analyticsTabMetrics?.classList.toggle("active", normalized === "metrics");
        refs.analyticsTabRaw?.classList.toggle("active", normalized === "raw");
        refs.analyticsTabCompare?.classList.toggle("active", normalized === "compare");
        refs.analyticsTopTabButtons?.forEach((button) => {
            const tab = (button.getAttribute("data-analytics-top-tab") || "overview").trim();
            button.classList.toggle("active", tab.toLowerCase() === normalized);
        });

        if (!shouldPushState) {
            return;
        }
        const url = new URL(window.location.href);
        if (normalized === "overview") {
            url.searchParams.delete("tab");
        } else {
            url.searchParams.set("tab", normalized);
        }
        window.history.replaceState({}, "", url.toString());
    }

    async function reloadAll() {
        const mainReloadRequestId = nextMainReloadRequestId();
        setGlobalScreenLoading(true);
        try {
            clearDashboardDataStatus();
            syncStageMetricRangesFromMain(true);
            syncStageTextQuickRangeFromMain();
            syncStageTextRangesFromMain(true);
            syncEventsRangeFromMain(true);
            // Critical path for perceived responsiveness: show main charts first.
            const criticalResults = await Promise.allSettled([
                loadOverview(mainReloadRequestId),
                loadStages(mainReloadRequestId)
            ]);
            criticalResults.forEach((result, index) => {
                if (result.status === "rejected") {
                    const label = index === 0 ? "loadOverview" : "loadStages";
                    console.error(`${label} failed`, result.reason);
                }
            });
            const firstRejected = criticalResults.find((result) => result.status === "rejected");
            if (firstRejected && firstRejected.reason) {
                const message = firstRejected.reason instanceof Error
                    ? firstRejected.reason.message
                    : "Не удалось загрузить данные графиков.";
                showDashboardDataStatus(message, true);
            }
        } finally {
            setGlobalScreenLoading(false);
        }

        // Heavy secondary blocks are refreshed in background and must not block the screen loader.
        // In global compare mode these blocks are refreshed by applyGlobalCompareToAllCharts()
        // to avoid double reload and visible re-draw flicker.
        if (!state.globalCompareEnabled) {
            void withUniversalChartLoaders(() => loadUniversal(mainReloadRequestId))
                .catch((error) => console.error("Universal background refresh failed", error));
            void withStageMetricLoaders("all", () => loadStageMetrics())
                .catch((error) => console.error("Stage metrics background refresh failed", error));
            void runPanelBackgroundRefresh(refs.eventsPanel, () => loadEvents(true), "Events background refresh failed");
        }
    }

    async function runPanelBackgroundRefresh(panelEl, task, errorLogPrefix) {
        setPanelLoading(panelEl, true);
        try {
            await task();
        } catch (error) {
            console.error(errorLogPrefix, error);
        } finally {
            setPanelLoading(panelEl, false);
        }
    }

    async function syncExpandedGraphFiltersFromTop() {
        const canvasId = state.expandedChart.sourceCanvasId || "";
        const container = state.expandedChart.containerEl;
        if (!canvasId || !container) {
            return;
        }
        const controls = container.querySelector(".analytics-expanded-graph-controls");
        if (!controls) {
            return;
        }
        state.expandedChart.customRangeActive = false;
        const ranges = state.globalCompareEnabled ? resolveGlobalBeforeRange() : expandedRangesFromTopFilter(canvasId);
        const beforeFrom = controls.querySelector("[data-range='before-from']");
        const beforeTo = controls.querySelector("[data-range='before-to']");
        const afterFrom = controls.querySelector("[data-range='after-from']");
        const afterTo = controls.querySelector("[data-range='after-to']");
        const beforeFromCompare = controls.querySelector("[data-range='before-from-compare']");
        const beforeToCompare = controls.querySelector("[data-range='before-to-compare']");
        const afterFromCompare = controls.querySelector("[data-range='after-from-compare']");
        const afterToCompare = controls.querySelector("[data-range='after-to-compare']");
        if (beforeFrom) beforeFrom.value = ranges.beforeFrom;
        if (beforeTo) beforeTo.value = ranges.beforeTo;
        if (afterFrom) afterFrom.value = ranges.afterFrom;
        if (afterTo) afterTo.value = ranges.afterTo;
        if (beforeFromCompare) beforeFromCompare.value = ranges.beforeFrom;
        if (beforeToCompare) beforeToCompare.value = ranges.beforeTo;
        if (afterFromCompare) afterFromCompare.value = ranges.afterFrom;
        if (afterToCompare) afterToCompare.value = ranges.afterTo;
        state.expandedRangesBySource[canvasId] = {...ranges};
        if (canvasId !== "chart-event-kpi") {
            await applyStoredExpandedRangesToCharts(canvasId);
        }
        renderExpandedChartClone(canvasId);
    }

    function expandedRangesFromTopFilter(canvasId) {
        if (state.globalCompareEnabled) {
            return resolveGlobalBeforeRange();
        }
        const safeAfter = resolveSafeAfterRangeFromTop();
        return normalizeCompareRangesByAfter(safeAfter.afterFrom, safeAfter.afterTo, "", "");
    }

    function expandedRangesFromPresetNow(presetCode) {
        if (String(presetCode || "").trim().toLowerCase() === "all") {
            const allRange = getAllTimeLocalRange();
            return {
                beforeFrom: allRange.from,
                beforeTo: allRange.to,
                afterFrom: allRange.from,
                afterTo: allRange.to
            };
        }
        const afterRange = buildQuickRangeFromDate(new Date(), presetCode)
            || buildQuickRangeFromDate(new Date(), "1h");
        const afterFromMs = afterRange.fromDate.getTime();
        const afterToMs = afterRange.toDate.getTime();
        const durationMs = Math.max(60_000, afterToMs - afterFromMs);
        const beforeToMs = afterFromMs;
        const beforeFromMs = beforeToMs - durationMs;
        return {
            beforeFrom: toDateTimeLocalString(new Date(beforeFromMs)),
            beforeTo: toDateTimeLocalString(new Date(beforeToMs)),
            afterFrom: toDateTimeLocalString(afterRange.fromDate),
            afterTo: toDateTimeLocalString(afterRange.toDate)
        };
    }

    function resolveExpandedRangesForMode(canvasId, isCompareEnabled) {
        const stored = state.expandedRangesBySource[canvasId];
        if (!stored || !stored.afterFrom || !stored.afterTo) {
            if (isCompareEnabled && state.globalCompareEnabled) {
                return resolveGlobalBeforeRange();
            }
            return expandedRangesFromTopFilter(canvasId);
        }
        if (!isCompareEnabled) {
            return {...stored};
        }
        const afterFromDate = new Date(stored.afterFrom);
        const afterToDate = new Date(stored.afterTo);
        if (Number.isNaN(afterFromDate.getTime()) || Number.isNaN(afterToDate.getTime()) || afterFromDate >= afterToDate) {
            return expandedRangesFromTopFilter(canvasId);
        }
        const durationMs = Math.max(60_000, afterToDate.getTime() - afterFromDate.getTime());
        const beforeTo = afterFromDate;
        const beforeFrom = new Date(beforeTo.getTime() - durationMs);
        return {
            beforeFrom: toDateTimeLocalString(beforeFrom),
            beforeTo: toDateTimeLocalString(beforeTo),
            afterFrom: stored.afterFrom,
            afterTo: stored.afterTo
        };
    }

    function normalizeStoredRangeForCompare(canvasId) {
        const stored = state.expandedRangesBySource[canvasId];
        if (!stored || !stored.afterFrom || !stored.afterTo) {
            if (state.globalCompareEnabled) {
                state.expandedRangesBySource[canvasId] = {...resolveGlobalBeforeRange()};
            }
            return;
        }
        const normalized = normalizeCompareRangesByAfter(
            stored.afterFrom,
            stored.afterTo,
            stored.beforeFrom,
            stored.beforeTo
        );
        state.expandedRangesBySource[canvasId] = {...normalized};
    }

    function captureExpandedRangesFromUi(canvasId) {
        const container = state.expandedChart.containerEl;
        if (!container || state.expandedChart.sourceCanvasId !== canvasId) {
            return;
        }
        const controls = container.querySelector(".analytics-expanded-graph-controls");
        if (!controls) {
            return;
        }
        const compareEnabled = canvasId === "chart-event-kpi"
            ? resolveExpandedCompareMode(canvasId) !== "off"
            : !!state.inlineCompareEnabled[canvasId];
        const ranges = compareEnabled
            ? {
                beforeFrom: controls.querySelector("[data-range='before-from-compare']")?.value || controls.querySelector("[data-range='before-from']")?.value || "",
                beforeTo: controls.querySelector("[data-range='before-to-compare']")?.value || controls.querySelector("[data-range='before-to']")?.value || "",
                afterFrom: controls.querySelector("[data-range='after-from-compare']")?.value || controls.querySelector("[data-range='after-from']")?.value || "",
                afterTo: controls.querySelector("[data-range='after-to-compare']")?.value || controls.querySelector("[data-range='after-to']")?.value || ""
            }
            : {
                beforeFrom: controls.querySelector("[data-range='before-from']")?.value || "",
                beforeTo: controls.querySelector("[data-range='before-to']")?.value || "",
                afterFrom: controls.querySelector("[data-range='after-from']")?.value || "",
                afterTo: controls.querySelector("[data-range='after-to']")?.value || ""
            };
        if (!ranges.afterFrom || !ranges.afterTo) {
            return;
        }
        state.expandedRangesBySource[canvasId] = normalizeCompareRangesByAfter(
            ranges.afterFrom,
            ranges.afterTo,
            ranges.beforeFrom,
            ranges.beforeTo
        );
    }

    async function loadDictionaries() {
        const selectedModule = refs.moduleType?.value?.trim() || "";
        const selectedMainEventType = refs.eventType?.value?.trim() || "";
        const selectedEventsEventType = refs.eventsEventType?.value?.trim() || "";
        const params = new URLSearchParams();
        if (selectedModule) {
            params.set("moduleCode", selectedModule);
        }
        const suffix = params.toString();
        const data = await fetchJson(suffix ? `${api("/dictionaries")}?${suffix}` : api("/dictionaries"));
        state.dictionaries = data;
        fillSelect(refs.moduleType, data.modules, "Все модули", true, selectedModule);
        fillSelect(refs.eventType, data.eventTypes, "Все события", true, selectedMainEventType);
        fillSelect(refs.eventsEventType, data.eventTypes, "Все события", true, selectedEventsEventType);
        fillSelect(refs.eventsMetricType, data.stageMetricTypes, "Любая метрика", true, undefined, (option) => localizeMetricDisplayName(option?.code, option?.name || option?.code));
        fillSelect(refs.stageMetricStageType, data.stageTypes, "Все этапы", true);
        fillSelect(refs.stageTextStageType, data.stageTypes, "Все этапы", true);
        fillSelect(refs.universalStageType, data.stageTypes, "Все этапы", true);
        fillSelect(refs.universalAttrCode, data.eventAttributeTypes, "Без фильтра", true);
        fillSelect(refs.eventsAttributeCode, data.eventAttributeTypes, "Без фильтра", true);
        fillSelect(refs.globalMetricCode, data.eventAttributeTypes, "Не выбран", true, refs.globalMetricCode?.value || "");
        fillUniversalEventSelector(data.eventTypes || []);
        await refreshGlobalMetricBlock();
    }

    function firstLabels(items, labelResolver, limit = 5) {
        return (Array.isArray(items) ? items : [])
            .slice(0, limit)
            .map((item) => {
                try {
                    return String(labelResolver(item) || "").trim();
                } catch (_error) {
                    return "";
                }
            })
            .filter(Boolean);
    }

    function chartDatasetLabels(canvasId) {
        const chart = state.charts?.[canvasId];
        return Array.isArray(chart?.data?.datasets)
            ? chart.data.datasets.map((dataset) => String(dataset?.label || "").trim()).filter(Boolean)
            : [];
    }

    function chartLabelsPreview(canvasId, limit = 5) {
        const chart = state.charts?.[canvasId];
        return Array.isArray(chart?.data?.labels)
            ? chart.data.labels.slice(0, limit).map((label) => String(label || "").trim()).filter(Boolean)
            : [];
    }

    function buildUniversalModeDebugSummary({
        action,
        paramsKey,
        perfStats,
        universal,
        labels,
        datasets,
        rows,
        currentEventRows,
        timelineCanvasId,
        stagesCanvasId,
        eventKpiCanvasId
    }) {
        const params = new URLSearchParams(paramsKey || "");
        const eventParamKeys = Array.from(params.keys()).filter((key) => /event/i.test(key));
        const eventBreakdown = Array.isArray(universal?.eventBreakdown) ? universal.eventBreakdown : [];
        const stageRows = Array.isArray(rows) ? rows : [];
        const eventRows = Array.isArray(currentEventRows) ? currentEventRows : [];
        return {
            action,
            analysisMode: resolveUniversalEventMetricMode(),
            selectedEventCodes: Array.from(selectedUniversalEventCodes()),
            queryString: paramsKey,
            containsEventParams: eventParamKeys.some((key) => key !== "analysisMode"),
            eventParamKeys,
            cacheHits: perfStats?.cacheHits || [],
            cacheMisses: perfStats?.cacheMisses || [],
            payloadSources: perfStats?.payloadSources || [],
            payload: {
                totalCount: Number(universal?.totals?.count || universal?.totals?.totalCount || 0),
                timelinePoints: Array.isArray(universal?.series) ? universal.series.length : 0,
                timelineFirstLabels: Array.isArray(labels) ? labels.slice(0, 5) : [],
                eventBreakdownCount: eventBreakdown.length,
                eventTopLabels: firstLabels(eventBreakdown, (row) => row.eventTypeName || row.eventTypeCode),
                stageRowsCount: stageRows.length,
                stageTopLabels: firstLabels(stageRows, (row) => row.stageTypeName || row.stageTypeCode)
            },
            preparedDatasets: {
                timeline: (Array.isArray(datasets) ? datasets : [])
                    .map((dataset) => String(dataset?.label || "").trim())
                    .filter(Boolean)
            },
            renderedCharts: {
                timeline: {
                    exists: !!state.charts?.[timelineCanvasId],
                    labels: chartLabelsPreview(timelineCanvasId),
                    datasets: chartDatasetLabels(timelineCanvasId)
                },
                stages: {
                    exists: !!state.charts?.[stagesCanvasId],
                    labels: chartLabelsPreview(stagesCanvasId),
                    datasets: chartDatasetLabels(stagesCanvasId)
                },
                kpi: {
                    exists: !!state.charts?.[eventKpiCanvasId],
                    labelsCount: Array.isArray(state.charts?.[eventKpiCanvasId]?.data?.labels)
                        ? state.charts[eventKpiCanvasId].data.labels.length
                        : 0,
                    labels: chartLabelsPreview(eventKpiCanvasId),
                    rowsTopLabels: firstLabels(eventRows, (row) => row.label || row.eventTypeName || row.eventTypeCode)
                }
            }
        };
    }

    function forceRebuildUniversalChartsForModeToOverall({action, paramsKey, compareCanvasIds = []} = {}) {
        if (action !== "mode_to_overall") {
            return;
        }
        const canvasIds = [
            "chart-universal-timeline",
            "chart-universal-stages",
            "chart-universal-event-kpi",
            ...compareCanvasIds
        ].filter(Boolean);
        const uniqueCanvasIds = Array.from(new Set(canvasIds));
        const before = uniqueCanvasIds.map((canvasId) => ({
            canvasId,
            hadChart: !!state.charts?.[canvasId],
            hadConfig: !!state.chartConfigs?.[canvasId],
            datasets: chartDatasetLabels(canvasId),
            labels: chartLabelsPreview(canvasId)
        }));
        uniqueCanvasIds.forEach((canvasId) => {
            const existingChart = state.charts?.[canvasId];
            if (existingChart) {
                existingChart.destroy();
                delete state.charts[canvasId];
            }
            delete state.chartConfigs[canvasId];
            delete state.chartScenarioBaseConfigs[canvasId];
            delete state.kpiFullChartConfigs[canvasId];
            delete state.kpiMiniTopStatsByCanvas[canvasId];
        });
        console.info("[UNIVERSAL_MODE_DEBUG] mode_to_overall forced rebuild", {
            action,
            queryString: paramsKey,
            destroyed: before
        });
    }

    function clearUniversalSelectedEventDerivedState() {
        state.universalEventScopeCacheKey = "";
        state.universalEventScopeCachePayload = null;
        state.universalEventScopeCachePromiseKey = "";
        state.universalEventScopeCachePromise = null;
        state.eventKpiMiniRowsSnapshot = null;
    }

    function getUniversalRenderPayloadForCurrentMode(payload, {action = "", paramsKey = "", analysisMode = ""} = {}) {
        const mode = validUniversalAnalysisMode(analysisMode)
            ? analysisMode
            : resolveUniversalEventMetricMode();
        const selectedCodes = mode === "overall"
            ? []
            : Array.from(selectedUniversalEventCodes());
        if (mode === "overall") {
            if (action === "mode_to_overall") {
                clearUniversalSelectedEventDerivedState();
            }
            return {
                payload: payload || {},
                analysisMode: "overall",
                selectedEventCodes: [],
                timelineRowsSource: "mainPayload/allEventsPayload",
                layersRowsSource: "mainPayload/allEventsPayload",
                kpiRowsSource: "mainPayload/allEventsPayload",
                queryString: paramsKey
            };
        }
        return {
            payload: payload || {},
            analysisMode: mode,
            selectedEventCodes: selectedCodes,
            timelineRowsSource: selectedCodes.length > 1 ? "mainPayload/eventSeries" : "mainPayload",
            layersRowsSource: selectedCodes.length > 1 ? "mainPayload/eventStageBreakdown" : "mainPayload",
            kpiRowsSource: selectedCodes.length ? "mainPayload/eventBreakdownFilteredBySelection" : "mainPayload",
            queryString: paramsKey
        };
    }

    function logUniversalRenderInput({action, renderContext, payload, labels = [], datasets = [], rows = [], currentEventRows = []} = {}) {
        if (action !== "mode_to_overall") {
            return;
        }
        const params = new URLSearchParams(renderContext?.queryString || "");
        const eventParamKeys = Array.from(params.keys()).filter((key) => /event/i.test(key) && key !== "analysisMode");
        const eventBreakdown = Array.isArray(payload?.eventBreakdown) ? payload.eventBreakdown : [];
        console.info("[UNIVERSAL_MODE_DEBUG] render input", {
            action,
            analysisMode: renderContext?.analysisMode || resolveUniversalEventMetricMode(),
            queryString: renderContext?.queryString || "",
            eventParamKeys,
            containsEventParams: eventParamKeys.length > 0,
            selectedUniversalEventCodes: Array.from(selectedUniversalEventCodes()),
            renderSelectedEventCodes: renderContext?.selectedEventCodes || [],
            rowsSource: {
                timeline: renderContext?.timelineRowsSource || "",
                layers: renderContext?.layersRowsSource || "",
                kpi: renderContext?.kpiRowsSource || ""
            },
            payload: {
                eventBreakdownCount: eventBreakdown.length,
                eventBreakdownTopLabels: firstLabels(eventBreakdown, (row) => row.eventTypeName || row.eventTypeCode),
                seriesPoints: Array.isArray(payload?.series) ? payload.series.length : 0,
                stageRowsCount: Array.isArray(payload?.stages) ? payload.stages.length : 0
            },
            timeline: {
                labels: Array.isArray(labels) ? labels.slice(0, 5) : [],
                datasetLabels: (Array.isArray(datasets) ? datasets : [])
                    .map((dataset) => String(dataset?.label || "").trim())
                    .filter(Boolean)
            },
            layers: {
                labels: firstLabels(rows, (row) => row.stageTypeName || row.stageTypeCode)
            },
            kpi: {
                labelsCount: Array.isArray(currentEventRows) ? currentEventRows.length : 0,
                labels: firstLabels(currentEventRows, (row) => row.label || row.key)
            }
        });
    }

    function fillUniversalEventSelectorFromOverallPayload(payload) {
        if (!refs.universalEventTypeList) {
            return;
        }
        const byCode = new Map();
        const addEvent = (row) => {
            const code = String(row?.eventTypeCode || "").trim();
            if (!code || byCode.has(code)) {
                return;
            }
            byCode.set(code, {
                code,
                name: String(row?.eventTypeName || code).trim() || code
            });
        };
        (Array.isArray(payload?.eventBreakdown) ? payload.eventBreakdown : []).forEach(addEvent);
        (Array.isArray(payload?.eventSeries) ? payload.eventSeries : []).forEach(addEvent);
        refs.universalEventTypeList.innerHTML = Array.from(byCode.values())
            .sort((a, b) => a.name.localeCompare(b.name, "ru"))
            .map((item) => `<option value="${escapeHtml(item.code)}">${escapeHtml(item.name)}</option>`)
            .join("");
        Array.from(refs.universalEventTypeList.options || []).forEach((option) => {
            option.selected = false;
        });
    }

    function universalOverallParams(useBaseline, options = {}) {
        const params = new URLSearchParams();
        const ranges = resolveUniversalCompareRanges();
        const fromValue = useBaseline ? ranges.beforeFrom : ranges.afterFrom;
        const toValue = useBaseline ? ranges.beforeTo : ranges.afterTo;
        setIfPresent(params, "from", toIso(fromValue));
        setIfPresent(params, "to", toIso(toValue));
        const moduleCode = refs.moduleType?.value?.trim();
        if (moduleCode) {
            params.set("moduleCode", moduleCode);
        }
        params.set("analysisMode", "overall");
        const attrCode = (refs.universalAttrCode?.value || "").trim();
        if (attrCode) {
            params.set("attributeCode", attrCode);
        }
        const attrValue = (refs.universalAttrValue?.value || "").trim();
        if (attrValue) {
            params.set("attributeValue", attrValue);
        }
        const stageType = (refs.universalStageType?.value || "").trim();
        if (stageType) {
            params.set("stageTypeCode", stageType);
        }
        const requestPath = refs.analyticsRequestPath?.value?.trim();
        if (requestPath) {
            params.set("requestPath", requestPath);
        }
        const bucket = (refs.universalBucket?.value || refs.bucket?.value || "").trim();
        if (bucket) {
            params.set("bucketMinutes", bucket);
        }
        if (options.includeEventStageBreakdown === false) {
            params.set("includeEventStageBreakdown", "false");
        }
        return params;
    }

    function universalQueryContainsEventParams(queryString) {
        const params = new URLSearchParams(queryString || "");
        return Array.from(params.keys()).some((key) => /event/i.test(key) && key !== "analysisMode");
    }

    function rawSelectedUniversalEventCodes() {
        if (!refs.universalEventTypeList) {
            return [];
        }
        return Array.from(refs.universalEventTypeList.selectedOptions || [])
            .map((option) => String(option.value || "").trim())
            .filter(Boolean);
    }

    async function handleUniversalOverallModeClick(event) {
        event?.preventDefault?.();
        event?.stopPropagation?.();
        const previousMode = resolveUniversalEventMetricMode();
        const selectedBeforeReset = rawSelectedUniversalEventCodes();
        state.universalAnalysisMode = "overall";
        resetUniversalEventSelectionForOverall();
        clearUniversalSelectedEventDerivedState();
        enforceUniversalMetricSelectionForMode();
        syncUniversalFilterModeUi();
        const selectedAfterReset = rawSelectedUniversalEventCodes();
        const params = universalOverallParams(false, {includeEventStageBreakdown: false});
        const queryString = params.toString();
        console.info("[UNIVERSAL_OVERALL_CLICK] clicked", {
            previousMode,
            nextMode: "overall",
            selectedEventsBeforeReset: selectedBeforeReset,
            selectedEventsAfterReset: selectedAfterReset,
            finalQueryString: queryString,
            containsEventParams: universalQueryContainsEventParams(queryString)
        });
        await withUniversalChartLoaders(
            () => reloadUniversalOverallFromScratch({action: "overall_click"}),
            {action: "overall_click"}
        );
    }

    async function reloadUniversalOverallFromScratch(options = {}) {
        const started = performance.now();
        const action = options.action || "mode_to_overall";
        const universalRequestId = Number(state.universalRequestId || 0) + 1;
        state.universalRequestId = universalRequestId;
        state.universalAnalysisMode = "overall";
        resetUniversalEventSelectionForOverall();
        clearUniversalSelectedEventDerivedState();
        enforceUniversalMetricSelectionForMode();
        syncUniversalFilterModeUi();

        const universalGlobalMode = resolveGlobalInlineCompareMode();
        const universalCompareEnabled = UNIVERSAL_COMPARE_FOLLOWS_GLOBAL
            ? universalGlobalMode !== "off"
            : !!refs.universalCompareEnabled?.checked;
        const universalGhostEnabled = UNIVERSAL_COMPARE_FOLLOWS_GLOBAL
            ? universalGlobalMode === "overlay"
            : !!refs.universalCompareGhost?.checked;
        const universalSplitEnabled = universalCompareEnabled && !universalGhostEnabled;
        const timelineCompareCanvasId = state.inlineCompareCanvasBySource["chart-universal-timeline"] || "chart-universal-timeline-compare-inline";
        const stagesCompareCanvasId = state.inlineCompareCanvasBySource["chart-universal-stages"] || "chart-universal-stages-compare-inline";
        const eventKpiCompareCanvasId = state.inlineCompareCanvasBySource["chart-universal-event-kpi"] || "chart-universal-event-kpi-compare-inline";
        const includeEventStageBreakdown = false;
        const params = universalOverallParams(false, {includeEventStageBreakdown});
        const paramsKey = params.toString();
        const baselineParams = universalCompareEnabled
            ? universalOverallParams(true, {includeEventStageBreakdown})
            : null;
        const baselineParamsKey = baselineParams?.toString() || "";
        const universalUrl = `${api("/universal")}?${paramsKey}`;
        const baselineUrl = baselineParamsKey ? `${api("/universal")}?${baselineParamsKey}` : "";
        forceRebuildUniversalChartsForModeToOverall({
            action,
            paramsKey,
            compareCanvasIds: [
                timelineCompareCanvasId,
                stagesCompareCanvasId,
                eventKpiCompareCanvasId
            ]
        });

        const [universal, baseline] = await Promise.all([
            fetchJson(universalUrl, {perfLabel: "universal-overall-isolated-main"}),
            baselineUrl
                ? fetchJson(baselineUrl, {perfLabel: "universal-overall-isolated-baseline"})
                : Promise.resolve(null)
        ]);
        if (isStaleUniversalRequest(universalRequestId)) {
            return;
        }

        fillUniversalEventSelectorFromOverallPayload(universal);
        fillUniversalAttributeSelectorByPeriod(universal);
        syncUniversalFilterModeUi();

        const labels = (universal?.series || []).map((point) => formatTime(point.time));
        const selectedMetrics = selectedUniversalTimelineMetrics();
        const datasets = [];
        if (selectedMetrics.has("count")) {
            datasets.push({label: "Count", data: (universal?.series || []).map((point) => point.count || 0), borderColor: colors.primary, tension: 0.25, pointRadius: 1.1});
        }
        if (selectedMetrics.has("avg")) {
            datasets.push({label: "AVG", data: (universal?.series || []).map((point) => point.avgMs || 0), borderColor: colors.teal, tension: 0.25, pointRadius: 1.1});
        }
        if (selectedMetrics.has("p95")) {
            datasets.push({label: "P95", data: (universal?.series || []).map((point) => point.p95Ms || 0), borderColor: colors.amber, tension: 0.25, pointRadius: 1.1});
        }
        if (selectedMetrics.has("error")) {
            datasets.push({label: "Error rate, %", data: (universal?.series || []).map((point) => toPercentNumber(point.errorRate)), borderColor: colors.red, tension: 0.25, pointRadius: 1.1});
        }
        if (baseline && universalGhostEnabled) {
            if (selectedMetrics.has("count")) {
                datasets.push({label: "Count (Before)", data: (baseline.series || []).map((point) => point.count || 0), borderColor: "rgba(109,40,217,0.45)", borderDash: [6, 4], tension: 0.25, pointRadius: 0.8});
            }
            if (selectedMetrics.has("avg")) {
                datasets.push({label: "AVG (Before)", data: (baseline.series || []).map((point) => point.avgMs || 0), borderColor: "rgba(15,118,110,0.5)", borderDash: [6, 4], tension: 0.25, pointRadius: 0.8});
            }
            if (selectedMetrics.has("p95")) {
                datasets.push({label: "P95 (Before)", data: (baseline.series || []).map((point) => point.p95Ms || 0), borderColor: "rgba(180,83,9,0.5)", borderDash: [6, 4], tension: 0.25, pointRadius: 0.8});
            }
            if (selectedMetrics.has("error")) {
                datasets.push({label: "Error rate, % (Before)", data: (baseline.series || []).map((point) => toPercentNumber(point.errorRate)), borderColor: "rgba(185,28,28,0.5)", borderDash: [6, 4], tension: 0.25, pointRadius: 0.8});
            }
        }
        upsertChart("chart-universal-timeline", {
            type: "line",
            data: {labels, datasets},
            options: baseChartOptions("Value")
        });
        if (universalSplitEnabled && baseline) {
            const beforeLabels = (baseline.series || []).map((point) => formatTime(point.time));
            const beforeDatasets = [];
            if (selectedMetrics.has("count")) beforeDatasets.push({label: "Count", data: (baseline.series || []).map((point) => point.count || 0), borderColor: colors.primary, tension: 0.25, pointRadius: 1.1});
            if (selectedMetrics.has("avg")) beforeDatasets.push({label: "AVG", data: (baseline.series || []).map((point) => point.avgMs || 0), borderColor: colors.teal, tension: 0.25, pointRadius: 1.1});
            if (selectedMetrics.has("p95")) beforeDatasets.push({label: "P95", data: (baseline.series || []).map((point) => point.p95Ms || 0), borderColor: colors.amber, tension: 0.25, pointRadius: 1.1});
            if (selectedMetrics.has("error")) beforeDatasets.push({label: "Error rate, %", data: (baseline.series || []).map((point) => toPercentNumber(point.errorRate)), borderColor: colors.red, tension: 0.25, pointRadius: 1.1});
            upsertChart(timelineCompareCanvasId, {
                type: "line",
                data: {labels: beforeLabels, datasets: beforeDatasets},
                options: baseChartOptions("Value")
            });
        } else {
            destroyChart(timelineCompareCanvasId);
        }

        const selectedStage = (refs.universalStageType?.value || "").trim();
        const rows = (universal?.stages || []).filter((row) => !selectedStage || row.stageTypeCode === selectedStage);
        const showStages = isUniversalSeriesEnabled("stages");
        const shouldRenderStages = showStages && rows.length > 0;
        refs.universalGrid?.classList.toggle("analytics-universal-grid-single", !shouldRenderStages);
        refs.universalStagesCard?.classList.toggle("d-none", !shouldRenderStages);
        refs.universalTimelineCard?.classList.toggle("analytics-universal-timeline-wide", !shouldRenderStages);
        if (!shouldRenderStages) {
            destroyChart("chart-universal-stages");
            destroyChart(stagesCompareCanvasId);
        } else {
            const stageSelectedMetrics = selectedUniversalStageMetrics();
            const baselineStageByCode = new Map((baseline?.stages || []).map((row) => [row.stageTypeCode, row]));
            const stageDatasets = stageSelectedMetrics.map((metricCode) => {
                if (metricCode === "avg") {
                    return {label: "AVG, ms", data: rows.map((row) => row.avgMs || 0), backgroundColor: "rgba(15,118,110,0.72)", borderRadius: 8, yAxisID: "y"};
                }
                if (metricCode === "p95") {
                    return {label: "P95, ms", data: rows.map((row) => row.p95Ms || 0), backgroundColor: "rgba(124,58,237,0.72)", borderRadius: 8, yAxisID: "y"};
                }
                return {label: "Error rate, %", data: rows.map((row) => toPercentNumber(row.errorRate)), backgroundColor: "rgba(185,28,28,0.72)", borderRadius: 8, yAxisID: "y1"};
            });
            if (baseline && universalGhostEnabled) {
                stageSelectedMetrics.forEach((metricCode) => {
                    if (metricCode === "avg") {
                        stageDatasets.push({label: "AVG, ms (Р”Рѕ)", data: rows.map((row) => baselineStageByCode.get(row.stageTypeCode)?.avgMs || 0), backgroundColor: "rgba(15,118,110,0.35)", borderColor: "rgba(15,118,110,0.72)", borderWidth: 1, borderRadius: 8, yAxisID: "y"});
                    } else if (metricCode === "p95") {
                        stageDatasets.push({label: "P95, ms (Р”Рѕ)", data: rows.map((row) => baselineStageByCode.get(row.stageTypeCode)?.p95Ms || 0), backgroundColor: "rgba(124,58,237,0.35)", borderColor: "rgba(124,58,237,0.72)", borderWidth: 1, borderRadius: 8, yAxisID: "y"});
                    } else {
                        stageDatasets.push({label: "Error rate, % (Before)", data: rows.map((row) => toPercentNumber(baselineStageByCode.get(row.stageTypeCode)?.errorRate)), backgroundColor: "rgba(185,28,28,0.35)", borderColor: "rgba(185,28,28,0.72)", borderWidth: 1, borderRadius: 8, yAxisID: "y1"});
                    }
                });
            }
            upsertChart("chart-universal-stages", {
                type: "bar",
                data: {
                    labels: rows.map((row) => row.stageTypeName || row.stageTypeCode),
                    datasets: stageDatasets
                },
                options: {
                    ...barChartOptions("ms"),
                    scales: {
                        ...(barChartOptions("ms").scales || {}),
                        y: {...((barChartOptions("ms").scales || {}).y || {}), position: "left"},
                        y1: {position: "right", grid: {drawOnChartArea: false}, ticks: {color: "#b91c1c"}}
                    }
                }
            });
            if (universalSplitEnabled && baseline) {
                const baselineRows = (baseline.stages || []).filter((row) => !selectedStage || row.stageTypeCode === selectedStage);
                const baselineByCode = new Map(baselineRows.map((row) => [row.stageTypeCode, row]));
                const beforeDatasets = stageSelectedMetrics.map((metricCode) => {
                    if (metricCode === "avg") {
                        return {label: "AVG, ms", data: rows.map((row) => baselineByCode.get(row.stageTypeCode)?.avgMs || 0), backgroundColor: "rgba(15,118,110,0.72)", borderRadius: 8, yAxisID: "y"};
                    }
                    if (metricCode === "p95") {
                        return {label: "P95, ms", data: rows.map((row) => baselineByCode.get(row.stageTypeCode)?.p95Ms || 0), backgroundColor: "rgba(124,58,237,0.72)", borderRadius: 8, yAxisID: "y"};
                    }
                    return {label: "Error rate, %", data: rows.map((row) => toPercentNumber(baselineByCode.get(row.stageTypeCode)?.errorRate)), backgroundColor: "rgba(185,28,28,0.72)", borderRadius: 8, yAxisID: "y1"};
                });
                upsertChart(stagesCompareCanvasId, {
                    type: "bar",
                    data: {
                        labels: rows.map((row) => row.stageTypeName || row.stageTypeCode),
                        datasets: beforeDatasets
                    },
                    options: barChartOptions("ms")
                });
            } else {
                destroyChart(stagesCompareCanvasId);
            }
        }

        const currentEventRows = buildEventKpiRows(universal?.eventBreakdown || []);
        const baselineEventRows = baseline ? buildEventKpiRows(baseline.eventBreakdown || []) : [];
        if (!currentEventRows.length && !baselineEventRows.length) {
            destroyChart("chart-universal-event-kpi");
            destroyChart(eventKpiCompareCanvasId);
        } else {
            const currentKpiConfig = baseline && universalGhostEnabled
                ? buildEventKpiOverlayChartConfig(currentEventRows, baselineEventRows, {preserveCurrentOrder: true})
                : buildEventKpiSingleChartConfig(currentEventRows);
            upsertChart("chart-universal-event-kpi", currentKpiConfig);
            if (universalSplitEnabled && baseline) {
                upsertChart(eventKpiCompareCanvasId, buildEventKpiSingleChartConfig(baselineEventRows));
            } else {
                destroyChart(eventKpiCompareCanvasId);
            }
        }
        applyUniversalChartZoom("chart-universal-event-kpi", refs.universalEventKpiZoomX?.value, refs.universalEventKpiZoomY?.value);
        await waitForChartPaint(2);
        const totalMs = Math.round(performance.now() - started);
        console.info("[UNIVERSAL_OVERALL_ISOLATED]", {
            action,
            renderPath: "isolatedOverall",
            totalMs,
            queryString: paramsKey,
            containsEventParams: universalQueryContainsEventParams(paramsKey),
            baselineQueryString: baselineParamsKey,
            baselineContainsEventParams: universalQueryContainsEventParams(baselineParamsKey),
            payloadSource: "fetch",
            selectedEventCodes: [],
            eventBreakdown: {
                count: Array.isArray(universal?.eventBreakdown) ? universal.eventBreakdown.length : 0,
                topLabels: firstLabels(universal?.eventBreakdown || [], (row) => row.eventTypeName || row.eventTypeCode)
            },
            stages: {
                count: rows.length,
                topLabels: firstLabels(rows, (row) => row.stageTypeName || row.stageTypeCode)
            },
            renderedChartIds: [
                "chart-universal-timeline",
                shouldRenderStages ? "chart-universal-stages" : "",
                currentEventRows.length ? "chart-universal-event-kpi" : ""
            ].filter(Boolean),
            timelineDatasets: chartDatasetLabels("chart-universal-timeline"),
            layerLabels: chartLabelsPreview("chart-universal-stages"),
            kpiLabelsCount: Array.isArray(state.charts?.["chart-universal-event-kpi"]?.data?.labels)
                ? state.charts["chart-universal-event-kpi"].data.labels.length
                : 0,
            compareMode: universalGhostEnabled ? "overlay" : (universalSplitEnabled ? "split" : "off")
        });
    }

    async function loadUniversal(loadOptions) {
        const totalStarted = performance.now();
        const options = (loadOptions && typeof loadOptions === "object")
            ? loadOptions
            : {mainReloadRequestId: loadOptions};
        const mainReloadRequestId = options.mainReloadRequestId;
        const action = options.action || "";
        const allowStaleMainPayload = !!options.allowStaleMainPayload;
        const perfStats = {
            action,
            requestCount: 0,
            queries: [],
            cacheHits: [],
            cacheMisses: [],
            payloadSources: []
        };
        const universalRequestId = Number(state.universalRequestId || 0) + 1;
        state.universalRequestId = universalRequestId;
        const universalGlobalMode = resolveGlobalInlineCompareMode();
        const universalAnalysisMode = resolveUniversalEventMetricMode();
        const universalCompareEnabled = UNIVERSAL_COMPARE_FOLLOWS_GLOBAL
            ? universalGlobalMode !== "off"
            : !!refs.universalCompareEnabled?.checked;
        const universalGhostEnabled = UNIVERSAL_COMPARE_FOLLOWS_GLOBAL
            ? universalGlobalMode === "overlay"
            : !!refs.universalCompareGhost?.checked;
        const universalSplitEnabled = universalCompareEnabled && !universalGhostEnabled;
        const timelineCompareCanvasId = state.inlineCompareCanvasBySource["chart-universal-timeline"] || "chart-universal-timeline-compare-inline";
        const stagesCompareCanvasId = state.inlineCompareCanvasBySource["chart-universal-stages"] || "chart-universal-stages-compare-inline";
        const eventKpiCompareCanvasId = state.inlineCompareCanvasBySource["chart-universal-event-kpi"] || "chart-universal-event-kpi-compare-inline";
        if (!universalSplitEnabled) {
            destroyChart(timelineCompareCanvasId);
            destroyChart(stagesCompareCanvasId);
            destroyChart(eventKpiCompareCanvasId);
        }
        const includeEventStageBreakdown = isUniversalSeriesEnabled("stages")
            && !refs.universalEventOverall?.checked
            && selectedUniversalEventCodes().size > 1;
        const params = universalParams(false, {includeEventStageBreakdown});
        const eventsScopeParams = universalParams(false, {includeEventFilter: false, includeEventStageBreakdown: false});
        const paramsKey = params.toString();
        const eventsScopeKey = eventsScopeParams.toString();
        const universalUrl = `${api("/universal")}?${paramsKey}`;
        const eventScopeUrl = `${api("/universal")}?${eventsScopeKey}`;
        const eventFilterActive = paramsKey !== eventsScopeKey;
        let eventScopeDeferred = false;
        let universal;
        let universalEventsScope;
        let baseline = null;
        const baselineParams = universalCompareEnabled
            ? universalParams(true, {includeEventStageBreakdown})
            : null;
        const baselineParamsKey = baselineParams?.toString() || "";
        const baselineRequest = universalCompareEnabled
            ? loadUniversalPayload(
                `baseline:${baselineParamsKey}`,
                `${api("/universal")}?${baselineParamsKey}`,
                "universal-baseline",
                {action, perfStats}
            )
            : Promise.resolve(null);
        const fetchStarted = performance.now();
        if (eventFilterActive) {
            const eventScopePromise = loadUniversalEventScope(eventsScopeKey, eventScopeUrl, {action, perfStats})
                .catch((error) => {
                    console.error("Universal event scope refresh failed", error);
                    return null;
                });
            [universal, baseline] = await Promise.all([
                loadUniversalPayload(`main:${paramsKey}`, universalUrl, "universal-main", {
                    action,
                    allowStale: allowStaleMainPayload,
                    perfStats
                }),
                baselineRequest
            ]);
            const cachedEventScope = state.universalEventScopeCacheKey === eventsScopeKey
                ? state.universalEventScopeCachePayload
                : null;
            universalEventsScope = cachedEventScope || universal;
            eventScopeDeferred = !cachedEventScope;
            if (eventScopeDeferred) {
                void eventScopePromise.then((eventScopePayload) => {
                    if (!eventScopePayload
                        || isStaleUniversalRequest(universalRequestId)
                        || isStaleMainReloadRequest(mainReloadRequestId)) {
                        return;
                    }
                    fillUniversalEventSelectorByPeriod(eventScopePayload);
                });
            }
        } else {
            [universal, baseline] = await Promise.all([
                loadUniversalPayload(`main:${paramsKey}`, universalUrl, "universal-main", {
                    action,
                    allowStale: allowStaleMainPayload,
                    perfStats
                }),
                baselineRequest
            ]);
            rememberUniversalEventScope(eventsScopeKey, universal);
            universalEventsScope = universal;
        }
        const fetchTotalMs = Math.round(performance.now() - fetchStarted);
        if (isStaleUniversalRequest(universalRequestId) || isStaleMainReloadRequest(mainReloadRequestId)) {
            return;
        }
        const currentExpectedParamsKey = universalParams(false, {includeEventStageBreakdown}).toString();
        if (currentExpectedParamsKey !== paramsKey) {
            console.info("[UNIVERSAL_MODE_DEBUG] stale render skipped", {
                action,
                requestParamsKey: paramsKey,
                currentExpectedParamsKey,
                requestAnalysisMode: universalAnalysisMode,
                currentAnalysisMode: resolveUniversalEventMetricMode()
            });
            return;
        }
        forceRebuildUniversalChartsForModeToOverall({
            action,
            paramsKey,
            compareCanvasIds: [
                timelineCompareCanvasId,
                stagesCompareCanvasId,
                eventKpiCompareCanvasId
            ]
        });
        const renderContext = getUniversalRenderPayloadForCurrentMode(universal, {
            action,
            paramsKey,
            analysisMode: universalAnalysisMode
        });
        universal = renderContext.payload;
        if (renderContext.analysisMode === "overall") {
            universalEventsScope = universal;
        }
        const buildStarted = performance.now();
        fillUniversalEventSelectorByPeriod(universalEventsScope);
        fillUniversalAttributeSelectorByPeriod(universal);
        const labels = (universal.series || []).map((point) => formatTime(point.time));
        const countSeries = (universal.series || []).map((point) => point.count || 0);
        const avgSeries = (universal.series || []).map((point) => point.avgMs || 0);
        const p95Series = (universal.series || []).map((point) => point.p95Ms || 0);
        const errSeries = (universal.series || []).map((point) => toPercentNumber(point.errorRate));

        const selectedMetrics = selectedUniversalTimelineMetrics();
        const showCount = selectedMetrics.has("count");
        const showAvg = selectedMetrics.has("avg");
        const showP95 = selectedMetrics.has("p95");
        const showError = selectedMetrics.has("error");
        const showStages = isUniversalSeriesEnabled("stages");
        const selectedEventCodes = Array.isArray(renderContext.selectedEventCodes)
            ? renderContext.selectedEventCodes
            : [];
        const hasPerEventMode = renderContext.analysisMode === "multi-event" && selectedEventCodes.length > 1;

        const renderStarted = performance.now();
        const datasets = [];
        if (hasPerEventMode) {
            const palette = ["#6d28d9", "#0f766e", "#b45309", "#b91c1c", "#1d4ed8", "#be185d", "#475569", "#0369a1", "#4d7c0f", "#7c2d12"];
            const selectedMetricCode = showCount ? "count" : (showAvg ? "avg" : (showP95 ? "p95" : "error"));
            const metricLabel = selectedMetricCode === "count"
                ? "Count"
                : selectedMetricCode === "avg"
                    ? "AVG"
                    : selectedMetricCode === "p95"
                        ? "P95"
                        : "Error rate, %";
            const seriesByCode = new Map((universal.eventSeries || []).map((item) => [String(item.eventTypeCode || "").trim(), item]));
            selectedEventCodes.forEach((eventCode, index) => {
                const payload = seriesByCode.get(eventCode);
                if (!payload) {
                    return;
                }
                const points = Array.isArray(payload.series) ? payload.series : [];
                const data = points.map((point) => {
                    if (selectedMetricCode === "count") return point.count || 0;
                    if (selectedMetricCode === "avg") return point.avgMs || 0;
                    if (selectedMetricCode === "p95") return point.p95Ms || 0;
                    return toPercentNumber(point.errorRate);
                });
                datasets.push({
                    label: `${payload.eventTypeName || payload.eventTypeCode} · ${metricLabel}`,
                    data,
                    borderColor: palette[index % palette.length],
                    tension: 0.25,
                    pointRadius: 1.1
                });
            });
        } else {
            if (showCount) {
                datasets.push({label: "Count", data: countSeries, borderColor: colors.primary, tension: 0.25, pointRadius: 1.1});
            }
            if (showAvg) {
                datasets.push({label: "AVG", data: avgSeries, borderColor: colors.teal, tension: 0.25, pointRadius: 1.1});
            }
            if (showP95) {
                datasets.push({label: "P95", data: p95Series, borderColor: colors.amber, tension: 0.25, pointRadius: 1.1});
            }
            if (showError) {
                datasets.push({label: "Error rate, %", data: errSeries, borderColor: colors.red, tension: 0.25, pointRadius: 1.1});
            }
        }

        if (isStaleUniversalRequest(universalRequestId) || isStaleMainReloadRequest(mainReloadRequestId)) {
            return;
        }
        if (baseline && universalGhostEnabled) {
            const beforeCount = (baseline.series || []).map((point) => point.count || 0);
            const beforeAvg = (baseline.series || []).map((point) => point.avgMs || 0);
            const beforeP95 = (baseline.series || []).map((point) => point.p95Ms || 0);
            const beforeErr = (baseline.series || []).map((point) => toPercentNumber(point.errorRate));
            const beforeSeriesByEventCode = new Map((baseline.eventSeries || []).map((item) => [String(item.eventTypeCode || "").trim(), item]));

            if (hasPerEventMode) {
                const selectedMetricCode = showCount ? "count" : (showAvg ? "avg" : (showP95 ? "p95" : "error"));
                const metricLabel = selectedMetricCode === "count"
                    ? "Count"
                    : selectedMetricCode === "avg"
                        ? "AVG"
                        : selectedMetricCode === "p95"
                            ? "P95"
                            : "Error rate, %";
                const palette = ["#6d28d9", "#0f766e", "#b45309", "#b91c1c", "#1d4ed8", "#be185d", "#475569", "#0369a1", "#4d7c0f", "#7c2d12"];
                selectedEventCodes.forEach((eventCode, index) => {
                    const payload = beforeSeriesByEventCode.get(eventCode);
                    if (!payload) {
                        return;
                    }
                    const points = Array.isArray(payload.series) ? payload.series : [];
                    const data = points.map((point) => {
                        if (selectedMetricCode === "count") return point.count || 0;
                        if (selectedMetricCode === "avg") return point.avgMs || 0;
                        if (selectedMetricCode === "p95") return point.p95Ms || 0;
                        return toPercentNumber(point.errorRate);
                    });
                    datasets.push({
                        label: `${payload.eventTypeName || payload.eventTypeCode} · ${metricLabel} (До)`,
                        data,
                        borderColor: palette[index % palette.length],
                        borderDash: [6, 4],
                        tension: 0.25,
                        pointRadius: 0.8
                    });
                });
            } else {
                if (showCount) {
                    datasets.push({
                        label: "Count (До)",
                        data: beforeCount,
                        borderColor: "rgba(109,40,217,0.45)",
                        borderDash: [6, 4],
                        tension: 0.25,
                        pointRadius: 0.8
                    });
                }
                if (showAvg) {
                    datasets.push({
                        label: "AVG (До)",
                        data: beforeAvg,
                        borderColor: "rgba(15,118,110,0.5)",
                        borderDash: [6, 4],
                        tension: 0.25,
                        pointRadius: 0.8
                    });
                }
                if (showP95) {
                    datasets.push({
                        label: "P95 (До)",
                        data: beforeP95,
                        borderColor: "rgba(180,83,9,0.5)",
                        borderDash: [6, 4],
                        tension: 0.25,
                        pointRadius: 0.8
                    });
                }
                if (showError) {
                    datasets.push({
                        label: "Error rate, % (До)",
                        data: beforeErr,
                        borderColor: "rgba(185,28,28,0.5)",
                        borderDash: [6, 4],
                        tension: 0.25,
                        pointRadius: 0.8
                    });
                }
            }
        }
        const selectedStage = (refs.universalStageType?.value || "").trim();
        const rows = (universal.stages || []).filter((row) => !selectedStage || row.stageTypeCode === selectedStage);
        const eventNameByCode = new Map(
            Array.from(refs.universalEventTypeList?.options || [])
                .map((option) => [String(option.value || "").trim(), String(option.textContent || option.value || "").trim()])
                .filter(([code]) => code.length > 0)
        );
        const selectedEventCategories = renderContext.analysisMode !== "overall" && selectedEventCodes.length > 0
            ? selectedEventCodes.map((code) => String(code || "").trim()).filter(Boolean)
            : [];
        const currentEventRows = buildUniversalEventKpiRows(
            universal.eventBreakdown || [],
            selectedEventCategories,
            eventNameByCode
        );
        const baselineEventRows = baseline
            ? buildUniversalEventKpiRows(
                baseline.eventBreakdown || [],
                selectedEventCategories,
                eventNameByCode
            )
            : [];
        logUniversalRenderInput({
            action,
            renderContext,
            payload: universal,
            labels,
            datasets,
            rows,
            currentEventRows
        });
        upsertChart("chart-universal-timeline", {
            type: "line",
            data: {labels, datasets},
            options: baseChartOptions("Значение")
        });
        if (universalSplitEnabled && baseline) {
            const beforeLabels = (baseline.series || []).map((point) => formatTime(point.time));
            const beforeDatasets = [];
            if (hasPerEventMode) {
                const palette = ["#6d28d9", "#0f766e", "#b45309", "#b91c1c", "#1d4ed8", "#be185d", "#475569", "#0369a1", "#4d7c0f", "#7c2d12"];
                const selectedMetricCode = showCount ? "count" : (showAvg ? "avg" : (showP95 ? "p95" : "error"));
                const metricLabel = selectedMetricCode === "count" ? "Count" : selectedMetricCode === "avg" ? "AVG" : selectedMetricCode === "p95" ? "P95" : "Error rate, %";
                const seriesByCode = new Map((baseline.eventSeries || []).map((item) => [String(item.eventTypeCode || "").trim(), item]));
                selectedEventCodes.forEach((eventCode, index) => {
                    const payload = seriesByCode.get(eventCode);
                    if (!payload) return;
                    const data = (payload.series || []).map((point) => selectedMetricCode === "count"
                        ? (point.count || 0)
                        : selectedMetricCode === "avg"
                            ? (point.avgMs || 0)
                            : selectedMetricCode === "p95"
                                ? (point.p95Ms || 0)
                                : toPercentNumber(point.errorRate));
                    beforeDatasets.push({
                        label: `${payload.eventTypeName || payload.eventTypeCode} · ${metricLabel}`,
                        data,
                        borderColor: palette[index % palette.length],
                        tension: 0.25,
                        pointRadius: 1.1
                    });
                });
            } else {
                if (showCount) beforeDatasets.push({label: "Count", data: (baseline.series || []).map((point) => point.count || 0), borderColor: colors.primary, tension: 0.25, pointRadius: 1.1});
                if (showAvg) beforeDatasets.push({label: "AVG", data: (baseline.series || []).map((point) => point.avgMs || 0), borderColor: colors.teal, tension: 0.25, pointRadius: 1.1});
                if (showP95) beforeDatasets.push({label: "P95", data: (baseline.series || []).map((point) => point.p95Ms || 0), borderColor: colors.amber, tension: 0.25, pointRadius: 1.1});
                if (showError) beforeDatasets.push({label: "Error rate, %", data: (baseline.series || []).map((point) => toPercentNumber(point.errorRate)), borderColor: colors.red, tension: 0.25, pointRadius: 1.1});
            }
            upsertChart(timelineCompareCanvasId, {
                type: "line",
                data: {labels: beforeLabels, datasets: beforeDatasets},
                options: baseChartOptions("Значение")
            });
        } else {
            destroyChart(timelineCompareCanvasId);
        }

        const shouldRenderStages = showStages && rows.length > 0;
        refs.universalGrid?.classList.toggle("analytics-universal-grid-single", !shouldRenderStages);
        refs.universalStagesCard?.classList.toggle("d-none", !shouldRenderStages);
        refs.universalTimelineCard?.classList.toggle("analytics-universal-timeline-wide", !shouldRenderStages);
        if (!shouldRenderStages) {
            destroyChart("chart-universal-stages");
            destroyChart(stagesCompareCanvasId);
        } else {
            const stageSelectedMetrics = selectedUniversalStageMetrics();
            const primaryStageMetricCode = stageSelectedMetrics.includes("p95")
                ? "p95"
                : (stageSelectedMetrics[0] || "p95");
            const stageMetricLabel = primaryStageMetricCode === "avg"
                ? "AVG, ms"
                : primaryStageMetricCode === "p95"
                    ? "P95, ms"
                    : "Error rate, %";
            const perEventStageMode = hasPerEventMode;
            const baselineStageByCode = new Map((baseline?.stages || []).map((row) => [row.stageTypeCode, row]));
            const beforeStageMetricValue = (stageRow, metricCode) => {
                if (!stageRow) {
                    return 0;
                }
                if (metricCode === "avg") return stageRow.avgMs || 0;
                if (metricCode === "p95") return stageRow.p95Ms || 0;
                return toPercentNumber(stageRow.errorRate);
            };
            if (perEventStageMode) {
                const stageLabelByCode = new Map(rows.map((row) => [row.stageTypeCode, row.stageTypeName || row.stageTypeCode]));
                const stageCodes = Array.from(stageLabelByCode.keys());
                const eventStagesMap = new Map((universal.eventStageBreakdown || []).map((item) => [String(item.eventTypeCode || "").trim(), item]));
                const baselineEventStagesMap = new Map((baseline?.eventStageBreakdown || []).map((item) => [String(item.eventTypeCode || "").trim(), item]));
                const palette = ["#6d28d9", "#0f766e", "#b45309", "#b91c1c", "#1d4ed8", "#be185d", "#475569", "#0369a1", "#4d7c0f", "#7c2d12"];
                const stageDatasets = selectedEventCodes.map((eventCode, index) => {
                    const payload = eventStagesMap.get(eventCode);
                    const byStageCode = new Map((payload?.stages || []).map((stageItem) => [stageItem.stageTypeCode, stageItem]));
                    const values = stageCodes.map((code) => {
                        const stageItem = byStageCode.get(code);
                        if (!stageItem) {
                            return 0;
                        }
                        if (primaryStageMetricCode === "avg") return stageItem.avgMs || 0;
                        if (primaryStageMetricCode === "p95") return stageItem.p95Ms || 0;
                        return toPercentNumber(stageItem.errorRate);
                    });
                    return {
                        label: payload?.eventTypeName || payload?.eventTypeCode || eventCode,
                        data: values,
                        backgroundColor: palette[index % palette.length],
                        borderRadius: 8
                    };
                });
                if (baseline && universalGhostEnabled) {
                    selectedEventCodes.forEach((eventCode, index) => {
                        const payload = baselineEventStagesMap.get(eventCode);
                        if (!payload) {
                            return;
                        }
                        const byStageCode = new Map((payload.stages || []).map((stageItem) => [stageItem.stageTypeCode, stageItem]));
                        const values = stageCodes.map((code) => {
                            const stageItem = byStageCode.get(code);
                            if (!stageItem) {
                                return 0;
                            }
                            if (primaryStageMetricCode === "avg") return stageItem.avgMs || 0;
                            if (primaryStageMetricCode === "p95") return stageItem.p95Ms || 0;
                            return toPercentNumber(stageItem.errorRate);
                        });
                        stageDatasets.push({
                            label: `${payload.eventTypeName || payload.eventTypeCode || eventCode} (До)`,
                            data: values,
                            backgroundColor: withAlphaColor(palette[index % palette.length], 0.35),
                            borderColor: palette[index % palette.length],
                            borderWidth: 1,
                            borderRadius: 8
                        });
                    });
                }
                upsertChart("chart-universal-stages", {
                    type: "bar",
                    data: {
                        labels: stageCodes.map((code) => stageLabelByCode.get(code) || code),
                        datasets: stageDatasets
                    },
                    options: barChartOptions(stageMetricLabel)
                });
                if (universalSplitEnabled && baseline) {
                    const baselineEventStagesMap = new Map((baseline.eventStageBreakdown || []).map((item) => [String(item.eventTypeCode || "").trim(), item]));
                    const palette = ["#6d28d9", "#0f766e", "#b45309", "#b91c1c", "#1d4ed8", "#be185d", "#475569", "#0369a1", "#4d7c0f", "#7c2d12"];
                    const beforeDatasets = selectedEventCodes.map((eventCode, index) => {
                        const payload = baselineEventStagesMap.get(eventCode);
                        const byStageCode = new Map((payload?.stages || []).map((stageItem) => [stageItem.stageTypeCode, stageItem]));
                        const values = stageCodes.map((code) => {
                            const stageItem = byStageCode.get(code);
                            if (!stageItem) return 0;
                            if (primaryStageMetricCode === "avg") return stageItem.avgMs || 0;
                            if (primaryStageMetricCode === "p95") return stageItem.p95Ms || 0;
                            return toPercentNumber(stageItem.errorRate);
                        });
                        return {
                            label: payload?.eventTypeName || payload?.eventTypeCode || eventCode,
                            data: values,
                            backgroundColor: palette[index % palette.length],
                            borderRadius: 8
                        };
                    });
                    upsertChart(stagesCompareCanvasId, {
                        type: "bar",
                        data: {
                            labels: stageCodes.map((code) => stageLabelByCode.get(code) || code),
                            datasets: beforeDatasets
                        },
                        options: barChartOptions(stageMetricLabel)
                    });
                } else {
                    destroyChart(stagesCompareCanvasId);
                }
            } else {
                const stageDatasets = stageSelectedMetrics.map((metricCode) => {
                if (metricCode === "avg") {
                    return {label: "AVG, ms", data: rows.map((row) => row.avgMs || 0), backgroundColor: "rgba(15,118,110,0.72)", borderRadius: 8, yAxisID: "y"};
                }
                if (metricCode === "p95") {
                    return {label: "P95, ms", data: rows.map((row) => row.p95Ms || 0), backgroundColor: "rgba(124,58,237,0.72)", borderRadius: 8, yAxisID: "y"};
                }
                return {label: "Error rate, %", data: rows.map((row) => toPercentNumber(row.errorRate)), backgroundColor: "rgba(185,28,28,0.72)", borderRadius: 8, yAxisID: "y1"};
            });
            if (baseline && universalGhostEnabled) {
                stageSelectedMetrics.forEach((metricCode) => {
                    if (metricCode === "avg") {
                        stageDatasets.push({
                            label: "AVG, ms (До)",
                            data: rows.map((row) => beforeStageMetricValue(baselineStageByCode.get(row.stageTypeCode), "avg")),
                            backgroundColor: "rgba(15,118,110,0.35)",
                            borderColor: "rgba(15,118,110,0.72)",
                            borderWidth: 1,
                            borderRadius: 8,
                            yAxisID: "y"
                        });
                        return;
                    }
                    if (metricCode === "p95") {
                        stageDatasets.push({
                            label: "P95, ms (До)",
                            data: rows.map((row) => beforeStageMetricValue(baselineStageByCode.get(row.stageTypeCode), "p95")),
                            backgroundColor: "rgba(124,58,237,0.35)",
                            borderColor: "rgba(124,58,237,0.72)",
                            borderWidth: 1,
                            borderRadius: 8,
                            yAxisID: "y"
                        });
                        return;
                    }
                    stageDatasets.push({
                        label: "Error rate, % (До)",
                        data: rows.map((row) => beforeStageMetricValue(baselineStageByCode.get(row.stageTypeCode), "error")),
                        backgroundColor: "rgba(185,28,28,0.35)",
                        borderColor: "rgba(185,28,28,0.72)",
                        borderWidth: 1,
                        borderRadius: 8,
                        yAxisID: "y1"
                    });
                });
            }
            upsertChart("chart-universal-stages", {
                type: "bar",
                data: {
                    labels: rows.map((row) => row.stageTypeName || row.stageTypeCode),
                    datasets: stageDatasets
                },
                options: {
                    ...barChartOptions("ms"),
                    scales: {
                        ...(barChartOptions("ms").scales || {}),
                        y: {
                            ...((barChartOptions("ms").scales || {}).y || {}),
                            position: "left"
                        },
                        y1: {
                            position: "right",
                            grid: {drawOnChartArea: false},
                            ticks: {color: "#b91c1c"}
                        }
                    }
                }
            });
            if (universalSplitEnabled && baseline) {
                const baselineRows = (baseline.stages || []).filter((row) => !selectedStage || row.stageTypeCode === selectedStage);
                const baselineByCode = new Map(baselineRows.map((row) => [row.stageTypeCode, row]));
                const beforeDatasets = stageSelectedMetrics.map((metricCode) => {
                    if (metricCode === "avg") {
                        return {label: "AVG, ms", data: rows.map((row) => baselineByCode.get(row.stageTypeCode)?.avgMs || 0), backgroundColor: "rgba(15,118,110,0.72)", borderRadius: 8, yAxisID: "y"};
                    }
                    if (metricCode === "p95") {
                        return {label: "P95, ms", data: rows.map((row) => baselineByCode.get(row.stageTypeCode)?.p95Ms || 0), backgroundColor: "rgba(124,58,237,0.72)", borderRadius: 8, yAxisID: "y"};
                    }
                    return {label: "Error rate, %", data: rows.map((row) => toPercentNumber(baselineByCode.get(row.stageTypeCode)?.errorRate)), backgroundColor: "rgba(185,28,28,0.72)", borderRadius: 8, yAxisID: "y1"};
                });
                upsertChart(stagesCompareCanvasId, {
                    type: "bar",
                    data: {
                        labels: rows.map((row) => row.stageTypeName || row.stageTypeCode),
                        datasets: beforeDatasets
                    },
                    options: {
                        ...barChartOptions("ms"),
                        scales: {
                            ...(barChartOptions("ms").scales || {}),
                            y: {
                                ...((barChartOptions("ms").scales || {}).y || {}),
                                position: "left"
                            },
                            y1: {
                                position: "right",
                                grid: {drawOnChartArea: false},
                                ticks: {color: "#b91c1c"}
                            }
                        }
                    }
                });
            } else {
                destroyChart(stagesCompareCanvasId);
            }
            }
        }

        if (!currentEventRows.length && !baselineEventRows.length) {
            destroyChart("chart-universal-event-kpi");
            destroyChart(eventKpiCompareCanvasId);
            await waitForChartPaint(2);
            return;
        }
        const currentKpiConfig = baseline && universalGhostEnabled
            ? buildEventKpiOverlayChartConfig(currentEventRows, baselineEventRows, {preserveCurrentOrder: true})
            : buildEventKpiSingleChartConfig(currentEventRows);
        upsertChart("chart-universal-event-kpi", currentKpiConfig);
        if (universalSplitEnabled && baseline) {
            upsertChart(eventKpiCompareCanvasId, buildEventKpiSingleChartConfig(baselineEventRows));
        } else {
            destroyChart(eventKpiCompareCanvasId);
        }
        applyUniversalChartZoom("chart-universal-event-kpi", refs.universalEventKpiZoomX?.value, refs.universalEventKpiZoomY?.value);
        await waitForChartPaint(2);
        const modeToOverallDebug = action === "mode_to_overall"
            ? buildUniversalModeDebugSummary({
                action,
                paramsKey,
                perfStats,
                universal,
                labels,
                datasets,
                rows,
                currentEventRows,
                timelineCanvasId: "chart-universal-timeline",
                stagesCanvasId: "chart-universal-stages",
                eventKpiCanvasId: "chart-universal-event-kpi"
            })
            : null;
        if (modeToOverallDebug) {
            console.info("[UNIVERSAL_MODE_DEBUG] mode_to_overall render", modeToOverallDebug);
        }
        const renderMs = Math.round(performance.now() - renderStarted);
        const totalMs = Math.round(performance.now() - totalStarted);
        console.info("[UNIVERSAL_PERF] frontend loadUniversal", {
            action,
            totalMs,
            fetchTotalMs,
            buildMs: Math.round(renderStarted - buildStarted),
            renderMs,
            requestCount: perfStats.requestCount,
            queries: perfStats.queries,
            cacheHits: perfStats.cacheHits,
            cacheMisses: perfStats.cacheMisses,
            payloadSources: perfStats.payloadSources,
            queryString: paramsKey,
            cacheKey: `main:${paramsKey}`,
            eventFilterActive,
            analysisMode: universalAnalysisMode,
            selectedEventCodes,
            compareEnabled: universalCompareEnabled,
            compareMode: universalGhostEnabled ? "overlay" : (universalSplitEnabled ? "split" : "off"),
            includeEventStageBreakdown,
            eventScopeDeferred,
            renderedCharts: {
                timeline: !!state.charts["chart-universal-timeline"],
                stages: !!state.charts["chart-universal-stages"],
                kpi: !!state.charts["chart-universal-event-kpi"]
            },
            series: Array.isArray(universal?.series) ? universal.series.length : 0,
            stages: Array.isArray(universal?.stages) ? universal.stages.length : 0,
            events: Array.isArray(universal?.eventBreakdown) ? universal.eventBreakdown.length : 0,
            eventSeries: Array.isArray(universal?.eventSeries) ? universal.eventSeries.length : 0,
            eventStageBreakdown: Array.isArray(universal?.eventStageBreakdown) ? universal.eventStageBreakdown.length : 0
        });
    }

    async function loadUniversalPayload(cacheKey, url, perfLabel, options = {}) {
        const key = String(cacheKey || "").trim();
        const now = Date.now();
        const ttlMs = 15_000;
        const action = options.action || "";
        const perfStats = options.perfStats;
        const cached = key ? state.universalPayloadCacheByKey.get(key) : null;
        const cachedAgeMs = cached ? now - Number(cached.storedAt || 0) : 0;
        if (cached && (cachedAgeMs <= ttlMs || options.allowStale)) {
            const stale = cachedAgeMs > ttlMs;
            if (perfStats) {
                perfStats.payloadSources.push({
                    label: perfLabel,
                    key,
                    source: "cache",
                    ageMs: cachedAgeMs,
                    stale
                });
            }
            if (perfStats) {
                perfStats.cacheHits.push({
                    label: perfLabel,
                    key,
                    ageMs: cachedAgeMs,
                    stale
                });
            }
            console.info("[UNIVERSAL_PERF] frontend cache", {
                action,
                label: perfLabel,
                hit: true,
                ageMs: cachedAgeMs,
                stale,
                key
            });
            return cached.payload;
        }
        const pending = key ? state.universalPayloadPromiseByKey.get(key) : null;
        if (pending) {
            if (perfStats) {
                perfStats.payloadSources.push({
                    label: perfLabel,
                    key,
                    source: "inflight"
                });
            }
            if (perfStats) {
                perfStats.cacheHits.push({
                    label: perfLabel,
                    key,
                    pending: true
                });
            }
            console.info("[UNIVERSAL_PERF] frontend cache", {
                action,
                label: perfLabel,
                hit: true,
                pending: true,
                key
            });
            return pending;
        }
        if (perfStats) {
            perfStats.requestCount += 1;
            perfStats.queries.push({label: perfLabel, url});
            perfStats.cacheMisses.push({label: perfLabel, key});
            perfStats.payloadSources.push({
                label: perfLabel,
                key,
                source: "fetch"
            });
        }
        const promise = fetchJson(url, {perfLabel})
            .then((payload) => {
                if (key) {
                    state.universalPayloadCacheByKey.set(key, {
                        storedAt: Date.now(),
                        payload
                    });
                    trimUniversalPayloadCache();
                }
                return payload;
            })
            .finally(() => {
                if (key) {
                    state.universalPayloadPromiseByKey.delete(key);
                }
            });
        if (key) {
            state.universalPayloadPromiseByKey.set(key, promise);
        }
        return promise;
    }

    function trimUniversalPayloadCache() {
        const maxEntries = 8;
        while (state.universalPayloadCacheByKey.size > maxEntries) {
            const oldestKey = state.universalPayloadCacheByKey.keys().next().value;
            if (!oldestKey) {
                break;
            }
            state.universalPayloadCacheByKey.delete(oldestKey);
        }
    }

    async function loadUniversalEventScope(scopeKey, scopeUrl, options = {}) {
        const action = options.action || "";
        const perfStats = options.perfStats;
        if (state.universalEventScopeCacheKey === scopeKey && state.universalEventScopeCachePayload) {
            if (perfStats) {
                perfStats.cacheHits.push({
                    label: "universal-event-scope",
                    key: scopeKey
                });
            }
            console.info("[UNIVERSAL_PERF] frontend cache", {
                action,
                label: "universal-event-scope",
                hit: true,
                key: scopeKey
            });
            return state.universalEventScopeCachePayload;
        }
        if (state.universalEventScopeCachePromiseKey === scopeKey && state.universalEventScopeCachePromise) {
            if (perfStats) {
                perfStats.cacheHits.push({
                    label: "universal-event-scope",
                    key: scopeKey,
                    pending: true
                });
            }
            console.info("[UNIVERSAL_PERF] frontend cache", {
                action,
                label: "universal-event-scope",
                hit: true,
                pending: true,
                key: scopeKey
            });
            return state.universalEventScopeCachePromise;
        }
        if (perfStats) {
            perfStats.requestCount += 1;
            perfStats.queries.push({label: "universal-event-scope", url: scopeUrl});
            perfStats.cacheMisses.push({label: "universal-event-scope", key: scopeKey});
        }
        const promise = fetchJson(scopeUrl, {perfLabel: "universal-event-scope"})
            .then((payload) => {
                rememberUniversalEventScope(scopeKey, payload);
                return payload;
            })
            .finally(() => {
                if (state.universalEventScopeCachePromiseKey === scopeKey) {
                    state.universalEventScopeCachePromiseKey = "";
                    state.universalEventScopeCachePromise = null;
                }
            });
        state.universalEventScopeCachePromiseKey = scopeKey;
        state.universalEventScopeCachePromise = promise;
        return promise;
    }

    function rememberUniversalEventScope(scopeKey, payload) {
        state.universalEventScopeCacheKey = scopeKey || "";
        state.universalEventScopeCachePayload = payload || null;
    }

    function isUniversalSeriesEnabled(code) {
        const target = Array.from(refs.universalSeriesToggles || []).find((item) => item.getAttribute("data-universal-series") === code);
        return !!target?.checked;
    }

    function setUniversalSeriesEnabled(code, enabled) {
        const target = Array.from(refs.universalSeriesToggles || []).find((item) => item.getAttribute("data-universal-series") === code);
        if (target) {
            target.checked = !!enabled;
        }
    }

    function applyUniversalScenarios() {
        const selectedScenario = (refs.universalScenario?.value || "").trim();
        if (!selectedScenario) {
            return;
        }
        if (selectedScenario === "tail_issue") {
            setSelectedUniversalMetrics(["p95", "error"]);
        } else if (selectedScenario === "layer_bottleneck") {
            setSelectedUniversalMetrics(["p95", "avg"]);
        } else if (selectedScenario === "error_without_load") {
            setSelectedUniversalMetrics(["error", "count"]);
        } else if (selectedScenario === "release_compare") {
            setSelectedUniversalMetrics(["count", "p95", "error"]);
        } else {
            setSelectedUniversalMetrics(["count", "avg", "p95", "error"]);
        }
        if (selectedScenario === "layer_bottleneck" && !isUniversalSeriesEnabled("stages")) {
            setUniversalSeriesEnabled("stages", true);
        }
        if (selectedScenario === "release_compare" && refs.universalCompareEnabled) {
            refs.universalCompareEnabled.checked = true;
        }
        enforceUniversalSeriesRules();
        syncUniversalStageMetricsFromSeries();
        enforceUniversalStageMetricRules();
        enforceUniversalEventMetricDependency("events");
        syncUniversalFilterModeUi();
        updateUniversalStageMetricToggleLabel();
    }

    function enforceUniversalSeriesRules() {
        const selected = selectedUniversalMetrics();
        if (selected.size > 0) {
            return;
        }
        setSelectedUniversalMetrics(["count"]);
    }

    function selectedUniversalMetrics() {
        const select = refs.universalSeriesMetricList;
        if (!select) {
            return new Set(["count"]);
        }
        const selected = new Set(
            Array.from(select.selectedOptions || [])
                .map((item) => String(item.value || "").trim())
                .filter((value) => value.length > 0)
        );
        if (!selected.size) {
            selected.add("count");
        }
        return selected;
    }

    function isUniversalConcreteStageSelected() {
        return String(refs.universalStageType?.value || "").trim().length > 0;
    }

    function selectedUniversalTimelineMetrics() {
        const selected = selectedUniversalMetrics();
        if (!isUniversalConcreteStageSelected()) {
            return selected;
        }
        const filtered = new Set(Array.from(selected).filter((code) => code !== "count"));
        if (filtered.size) {
            return filtered;
        }
        return new Set(["p95"]);
    }

    function setSelectedUniversalMetrics(metricCodes) {
        const select = refs.universalSeriesMetricList;
        if (!select) {
            return;
        }
        const desired = new Set((metricCodes || []).map((code) => String(code || "").trim()).filter((code) => code.length > 0));
        Array.from(select.options || []).forEach((option) => {
            const code = String(option.value || "").trim();
            option.selected = desired.has(code);
        });
        updateUniversalMetricToggleLabel();
        renderUniversalMetricVisualList();
        // Always propagate main metrics -> stage metrics.
        syncUniversalStageMetricsFromSeries();
        enforceUniversalStageMetricRules();
        updateUniversalStageMetricToggleLabel();
    }

    function selectedUniversalStageMetrics() {
        const select = refs.universalStageMetricList;
        if (!select) {
            return ["p95"];
        }
        const selected = Array.from(select.selectedOptions || [])
            .map((item) => String(item.value || "").trim())
            .filter((value) => ["avg", "p95", "error"].includes(value));
        return selected.length ? selected : ["p95"];
    }

    function setSelectedUniversalStageMetrics(metricCodes) {
        const select = refs.universalStageMetricList;
        if (!select) {
            return;
        }
        const desired = new Set((metricCodes || [])
            .map((code) => String(code || "").trim())
            .filter((code) => ["avg", "p95", "error"].includes(code)));
        Array.from(select.options || []).forEach((option) => {
            option.selected = desired.has(String(option.value || "").trim());
        });
        enforceUniversalStageMetricRules();
        updateUniversalStageMetricToggleLabel();
    }

    function syncUniversalStageMetricsFromSeries() {
        const stageAllowed = Array.from(selectedUniversalMetrics()).filter((code) => code !== "count");
        if (!stageAllowed.length) {
            setSelectedUniversalStageMetrics(["p95"]);
            return;
        }
        setSelectedUniversalStageMetrics(stageAllowed);
    }

    function enforceUniversalStageMetricRules() {
        const current = selectedUniversalStageMetrics();
        if (current.length > 0) {
            return;
        }
        setSelectedUniversalStageMetrics(["p95"]);
    }

    function updateUniversalStageMetricToggleLabel() {
        if (!refs.universalStageMetricToggle) {
            return;
        }
        const selected = selectedUniversalStageMetrics();
        const labelsMap = {
            avg: "AVG",
            p95: "P95",
            error: "Error rate"
        };
        const names = selected.map((code) => labelsMap[code] || code).join(", ");
        refs.universalStageMetricToggle.textContent = names ? `Метрики: ${names}` : "Метрики слоёв";
    }

    function updateUniversalMetricToggleLabel() {
        if (!refs.universalSeriesMetricToggle) {
            return;
        }
        const mode = resolveUniversalEventMetricMode();
        const stageFiltered = isUniversalConcreteStageSelected();
        const selected = Array.from(selectedUniversalTimelineMetrics());
        if (!selected.length) {
            refs.universalSeriesMetricToggle.textContent = "Метрики";
            refs.universalSeriesMetricToggle.title = "Метрики таймлайна";
            return;
        }
        if (mode === "multi-event") {
            const one = selected[0] || chooseUniversalComparisonMetric(selected);
            refs.universalSeriesMetricToggle.textContent = `Метрика сравнения: ${metricDisplayName(one)}`;
            refs.universalSeriesMetricToggle.title = stageFiltered
                ? "Выбран конкретный слой — Count скрыт в timeline; доступна одна метрика сравнения"
                : "Выбрано несколько событий — доступна одна метрика сравнения";
            return;
        }
        refs.universalSeriesMetricToggle.textContent = `Метрики: ${selected.length}`;
        refs.universalSeriesMetricToggle.title = stageFiltered
            ? "Выбран конкретный слой — Count скрыт в timeline"
            : "Для одного набора событий можно выбрать несколько метрик";
    }

    function fillUniversalEventSelector(eventTypes) {
        if (!refs.universalEventTypeList) {
            return;
        }
        const previous = selectedUniversalEventCodes();
        refs.universalEventTypeList.innerHTML = (eventTypes || []).map((item) => {
            const code = String(item?.code || "").trim();
            const checked = previous.has(code);
            return `<option value="${escapeHtml(code)}" ${checked ? "selected" : ""}>${escapeHtml(item?.name || code)}</option>`;
        }).join("");
        syncUniversalFilterModeUi();
    }

    function fillUniversalEventSelectorByPeriod(universalPayload) {
        if (!refs.universalEventTypeList) {
            return;
        }
        const previousSelected = selectedUniversalEventCodes();
        const fromBreakdown = Array.isArray(universalPayload?.eventBreakdown)
            ? universalPayload.eventBreakdown
            : [];
        const fromSeries = Array.isArray(universalPayload?.eventSeries)
            ? universalPayload.eventSeries
            : [];

        const byCode = new Map();
        fromBreakdown.forEach((row) => {
            const code = String(row?.eventTypeCode || "").trim();
            if (!code) {
                return;
            }
            byCode.set(code, {
                code,
                name: String(row?.eventTypeName || code).trim() || code
            });
        });
        fromSeries.forEach((row) => {
            const code = String(row?.eventTypeCode || "").trim();
            if (!code || byCode.has(code)) {
                return;
            }
            byCode.set(code, {
                code,
                name: String(row?.eventTypeName || code).trim() || code
            });
        });

        const available = Array.from(byCode.values())
            .sort((a, b) => a.name.localeCompare(b.name, "ru"));

        refs.universalEventTypeList.innerHTML = available.map((item) => {
            const selected = previousSelected.has(item.code);
            return `<option value="${escapeHtml(item.code)}" ${selected ? "selected" : ""}>${escapeHtml(item.name)}</option>`;
        }).join("");

        if (refs.universalEventOverall?.checked) {
            Array.from(refs.universalEventTypeList.options || []).forEach((option) => {
                option.selected = false;
            });
        }
        syncUniversalFilterModeUi();
    }

    function fillUniversalAttributeSelectorByPeriod(universalPayload) {
        if (!refs.universalAttrCode) {
            return;
        }
        const previous = String(refs.universalAttrCode.value || "").trim();
        const available = Array.isArray(universalPayload?.availableAttributeTypes)
            ? universalPayload.availableAttributeTypes
            : [];
        const options = [{code: "", name: "Без фильтра"}, ...available
            .map((item) => ({
                code: String(item?.code || "").trim(),
                name: String(item?.name || item?.code || "").trim()
            }))
            .filter((item) => item.code.length > 0)];
        refs.universalAttrCode.innerHTML = options
            .map((item) => `<option value="${escapeHtml(item.code)}">${escapeHtml(item.name)}</option>`)
            .join("");
        const hasPrevious = options.some((item) => item.code === previous);
        refs.universalAttrCode.value = hasPrevious ? previous : "";
    }

    function selectedUniversalEventCodes() {
        if (state.universalAnalysisMode === "overall" || refs.universalEventOverall?.checked) {
            return new Set();
        }
        return new Set(
            Array.from(refs.universalEventTypeList?.selectedOptions || [])
                .map((item) => String(item.value || "").trim())
                .filter((value) => value.length > 0)
        );
    }

    function validUniversalAnalysisMode(mode) {
        return ["overall", "single-event", "multi-event"].includes(mode);
    }

    function resolveUniversalEventMetricMode() {
        if (validUniversalAnalysisMode(state.universalAnalysisMode)) {
            return state.universalAnalysisMode;
        }
        const selected = Array.from(selectedUniversalEventCodes());
        if (refs.universalEventOverall?.checked || selected.length === 0) {
            return "overall";
        }
        return selected.length === 1 ? "single-event" : "multi-event";
    }

    function setSelectedUniversalEventCodes(codes, options = {}) {
        if (!refs.universalEventTypeList) {
            return;
        }
        const overallWhenEmpty = options.overallWhenEmpty !== false;
        const desired = new Set((codes || []).map((code) => String(code || "").trim()).filter(Boolean));
        Array.from(refs.universalEventTypeList.options || []).forEach((option) => {
            option.selected = desired.has(String(option.value || "").trim());
        });
        if (refs.universalEventOverall) {
            refs.universalEventOverall.checked = overallWhenEmpty && desired.size === 0;
        }
    }

    function resetUniversalEventSelectionForOverall() {
        if (refs.universalEventOverall) {
            refs.universalEventOverall.checked = true;
        }
        if (refs.universalEventTypeList) {
            Array.from(refs.universalEventTypeList.options || []).forEach((option) => {
                option.selected = false;
            });
            refs.universalEventTypeList.value = "";
        }
        refs.universalEventTypePopup?.classList.add("d-none");
        refs.universalEventTypeToggle?.setAttribute("aria-expanded", "false");
    }

    function applyUniversalAnalysisModeSelection(mode = resolveUniversalEventMetricMode()) {
        const selected = Array.from(selectedUniversalEventCodes());
        if (mode === "overall") {
            resetUniversalEventSelectionForOverall();
            return;
        }
        if (refs.universalEventOverall) {
            refs.universalEventOverall.checked = false;
        }
        if (mode === "single-event") {
            const one = selected.length === 1 ? selected[0] : "";
            setSelectedUniversalEventCodes(one ? [one] : [], {overallWhenEmpty: false});
            return;
        }
        setSelectedUniversalEventCodes(selected, {overallWhenEmpty: false});
    }

    function setUniversalAnalysisMode(mode, options = {}) {
        if (!validUniversalAnalysisMode(mode)) {
            return;
        }
        const previousMode = resolveUniversalEventMetricMode();
        const previousEvents = Array.from(selectedUniversalEventCodes());
        state.universalAnalysisMode = mode;
        applyUniversalAnalysisModeSelection(mode);
        enforceUniversalMetricSelectionForMode();
        syncUniversalFilterModeUi();
        if (options.submit) {
            const action = mode === "overall" && previousMode !== "overall"
                ? "mode_to_overall"
                : "analysis_mode_change";
            if (action === "mode_to_overall") {
                console.info("[UNIVERSAL_PERF] frontend mode_to_overall", {
                    previousMode,
                    nextMode: mode,
                    selectedBeforeReset: previousEvents,
                    selectedAfterReset: Array.from(selectedUniversalEventCodes())
                });
            }
            void submitUniversalFilters({action})
                .catch((error) => console.error("Universal analysis mode change failed", error));
        }
    }

    function metricDisplayName(code) {
        const value = String(code || "").trim();
        const labels = {
            count: "Count",
            avg: "AVG",
            p95: "P95",
            error: "Error rate"
        };
        const optionText = Array.from(refs.universalSeriesMetricList?.options || [])
            .find((option) => String(option.value || "").trim() === value)
            ?.textContent
            ?.trim();
        return optionText || labels[value] || value.toUpperCase();
    }

    function universalEventDisplayName(code) {
        const value = String(code || "").trim();
        return Array.from(refs.universalEventTypeList?.options || [])
            .find((option) => String(option.value || "").trim() === value)
            ?.textContent
            ?.trim() || value;
    }

    function chooseUniversalComparisonMetric(selectedMetrics = Array.from(selectedUniversalMetrics())) {
        const selected = selectedMetrics.map((code) => String(code || "").trim()).filter(Boolean);
        if (selected.includes("p95")) {
            return "p95";
        }
        if (selected.length) {
            return selected[0];
        }
        const hasP95 = Array.from(refs.universalSeriesMetricList?.options || [])
            .some((option) => String(option.value || "").trim() === "p95");
        return hasP95 ? "p95" : "count";
    }

    function removeUniversalInlineHints() {
        refs.universalForm?.querySelector("[data-universal-mode-hint]")?.remove();
        refs.universalEventTypePopup?.querySelector("[data-universal-event-mode-hint]")?.remove();
        refs.universalSeriesMetricPopup?.querySelector("[data-universal-metric-mode-hint]")?.remove();
    }

    function ensureUniversalAnalysisModeControl() {
        const form = refs.universalForm;
        const row = form?.querySelector(".analytics-universal-main-row");
        if (!form || !row) {
            return null;
        }
        let control = form.querySelector("[data-universal-analysis-mode-control]");
        if (!control) {
            control = document.createElement("div");
            control.className = "analytics-universal-mode-tabs";
            control.setAttribute("data-universal-analysis-mode-control", "main");
            control.setAttribute("role", "tablist");
            control.setAttribute("aria-label", "Режим анализа");
            control.innerHTML = `
                <button type="button" class="analytics-universal-mode-tab" data-universal-analysis-mode="overall" role="tab">
                    <span class="analytics-universal-mode-tab-title">Общая статистика</span>
                    <span class="analytics-universal-mode-tab-sub">Все события</span>
                </button>
                <button type="button" class="analytics-universal-mode-tab" data-universal-analysis-mode="single-event" role="tab">
                    <span class="analytics-universal-mode-tab-title">Одно событие</span>
                    <span class="analytics-universal-mode-tab-sub">Несколько метрик</span>
                </button>
                <button type="button" class="analytics-universal-mode-tab" data-universal-analysis-mode="multi-event" role="tab">
                    <span class="analytics-universal-mode-tab-title">Сравнение событий</span>
                    <span class="analytics-universal-mode-tab-sub">Одна метрика</span>
                </button>
            `;
            control.addEventListener("click", (event) => {
                const button = event.target?.closest?.("[data-universal-analysis-mode]");
                if (!button) {
                    return;
                }
                event.preventDefault();
                const mode = button.getAttribute("data-universal-analysis-mode") || "overall";
                if (mode === "overall") {
                    void handleUniversalOverallModeClick(event)
                        .catch((error) => console.error("Universal overall click reload failed", error));
                    return;
                }
                setUniversalAnalysisMode(mode, {submit: true});
            });
            row.insertAdjacentElement("beforebegin", control);
        }
        return control;
    }

    function renderUniversalAnalysisModeControl() {
        const control = ensureUniversalAnalysisModeControl();
        if (!control) {
            return;
        }
        const mode = resolveUniversalEventMetricMode();
        control.querySelectorAll("[data-universal-analysis-mode]").forEach((button) => {
            const active = button.getAttribute("data-universal-analysis-mode") === mode;
            button.classList.toggle("is-active", active);
            button.setAttribute("aria-selected", active ? "true" : "false");
            button.setAttribute("tabindex", active ? "0" : "-1");
        });
        control.setAttribute("data-universal-event-mode", mode);
    }

    function ensureUniversalEventVisualList() {
        if (!refs.universalEventTypePopup || !refs.universalEventTypeList) {
            return null;
        }
        let list = refs.universalEventTypePopup.querySelector("[data-universal-event-visual-list]");
        if (!list) {
            refs.universalEventTypeList.classList.add("analytics-native-select-hidden");
            refs.universalEventTypeList.setAttribute("tabindex", "-1");
            refs.universalEventTypeList.setAttribute("aria-hidden", "true");
            list = document.createElement("div");
            list.className = "analytics-universal-option-list analytics-universal-event-option-list";
            list.setAttribute("data-universal-event-visual-list", "events");
            refs.universalEventTypeList.insertAdjacentElement("afterend", list);
            list.addEventListener("click", (event) => {
                const button = event.target?.closest?.("[data-universal-event-option]");
                if (!button) {
                    return;
                }
                event.preventDefault();
                handleUniversalEventVisualChoice(button.getAttribute("data-universal-event-option") || "");
            });
        }
        return list;
    }

    function renderUniversalEventVisualList() {
        const list = ensureUniversalEventVisualList();
        if (!list || !refs.universalEventTypeList) {
            return;
        }
        const mode = resolveUniversalEventMetricMode();
        const selected = selectedUniversalEventCodes();
        refs.universalEventTypePopup?.setAttribute("data-universal-event-mode", mode);
        refs.universalEventOverall?.closest(".analytics-universal-events-popup-head")?.classList.add("d-none");
        if (mode === "overall") {
            list.innerHTML = `
                <div class="analytics-universal-overall-state">
                    <div class="analytics-universal-overall-state-title">Все события</div>
                    <div class="analytics-universal-overall-state-sub">Конкретные события выбираются в режимах "Одно событие" и "Сравнение событий".</div>
                </div>
            `;
            return;
        }
        list.innerHTML = Array.from(refs.universalEventTypeList.options || []).map((option) => {
            const code = String(option.value || "").trim();
            const checked = selected.has(code);
            const control = mode === "single-event"
                ? `<span class="analytics-universal-option-radio" aria-hidden="true">${checked ? "✓" : ""}</span>`
                : `<span class="analytics-universal-option-checkbox" aria-hidden="true">${checked ? "✓" : ""}</span>`;
            return `
                <button type="button"
                        class="analytics-universal-option ${checked ? "is-selected" : ""}"
                        data-universal-event-option="${escapeHtml(code)}"
                        aria-pressed="${checked ? "true" : "false"}"
                        title="${escapeHtml(option.textContent || code)}">
                    ${control}
                    <span class="analytics-universal-option-label">${escapeHtml(option.textContent || code)}</span>
                </button>
            `;
        }).join("");
    }

    function handleUniversalEventVisualChoice(code) {
        const eventCode = String(code || "").trim();
        if (!eventCode) {
            return;
        }
        const mode = resolveUniversalEventMetricMode();
        if (mode === "overall") {
            return;
        }
        if (mode === "single-event") {
            setSelectedUniversalEventCodes([eventCode]);
        } else {
            const selected = selectedUniversalEventCodes();
            if (selected.has(eventCode) && selected.size > 1) {
                selected.delete(eventCode);
            } else {
                selected.add(eventCode);
            }
            setSelectedUniversalEventCodes(Array.from(selected));
        }
        refs.universalEventTypeList?.dispatchEvent(new Event("change", {bubbles: true}));
    }

    function ensureUniversalMetricVisualList() {
        if (!refs.universalSeriesMetricPopup || !refs.universalSeriesMetricList) {
            return null;
        }
        let list = refs.universalSeriesMetricPopup.querySelector("[data-universal-metric-visual-list]");
        if (!list) {
            refs.universalSeriesMetricList.classList.add("analytics-native-select-hidden");
            refs.universalSeriesMetricList.setAttribute("tabindex", "-1");
            refs.universalSeriesMetricList.setAttribute("aria-hidden", "true");

            list = document.createElement("div");
            list.className = "analytics-universal-option-list";
            list.setAttribute("data-universal-metric-visual-list", "metrics");
            refs.universalSeriesMetricList.insertAdjacentElement("afterend", list);
            list.addEventListener("click", (event) => {
                const button = event.target?.closest?.("[data-universal-metric-option]");
                if (!button) {
                    return;
                }
                event.preventDefault();
                const code = button.getAttribute("data-universal-metric-option") || "";
                handleUniversalMetricVisualChoice(code);
            });
        }
        return list;
    }

    function renderUniversalMetricVisualList() {
        const list = ensureUniversalMetricVisualList();
        if (!list || !refs.universalSeriesMetricList) {
            return;
        }
        const mode = resolveUniversalEventMetricMode();
        const effectiveSelected = selectedUniversalTimelineMetrics();
        const stageFiltered = isUniversalConcreteStageSelected();
        refs.universalSeriesMetricPopup?.setAttribute("data-universal-event-mode", mode);
        list.innerHTML = Array.from(refs.universalSeriesMetricList.options || []).map((option) => {
            const code = String(option.value || "").trim();
            const disabled = stageFiltered && code === "count";
            const checked = effectiveSelected.has(code) && !disabled;
            const control = mode === "multi-event"
                ? `<span class="analytics-universal-option-radio" aria-hidden="true">${checked ? "✓" : ""}</span>`
                : `<span class="analytics-universal-option-checkbox" aria-hidden="true">${checked ? "✓" : ""}</span>`;
            return `
                <button type="button"
                        class="analytics-universal-option ${checked ? "is-selected" : ""} ${disabled ? "is-disabled" : ""}"
                        data-universal-metric-option="${escapeHtml(code)}"
                        ${disabled ? "disabled" : ""}
                        aria-pressed="${checked ? "true" : "false"}"
                        title="${escapeHtml(disabled ? "Count доступен только для всех этапов" : (option.textContent || code))}">
                    ${control}
                    <span class="analytics-universal-option-label">${escapeHtml(option.textContent || code)}</span>
                </button>
            `;
        }).join("");
    }

    function handleUniversalMetricVisualChoice(code) {
        const metricCode = String(code || "").trim();
        if (!metricCode) {
            return;
        }
        if (isUniversalConcreteStageSelected() && metricCode === "count") {
            return;
        }
        const mode = resolveUniversalEventMetricMode();
        if (mode === "multi-event") {
            setSelectedUniversalMetrics([metricCode]);
        } else {
            const selected = selectedUniversalMetrics();
            if (selected.has(metricCode) && selected.size > 1) {
                selected.delete(metricCode);
            } else {
                selected.add(metricCode);
            }
            setSelectedUniversalMetrics(Array.from(selected));
        }
        refs.universalSeriesMetricList?.dispatchEvent(new Event("change", {bubbles: true}));
    }

    function enforceUniversalMetricSelectionForMode() {
        const mode = resolveUniversalEventMetricMode();
        const selected = Array.from(selectedUniversalMetrics());
        if (mode === "multi-event") {
            if (selected.length > 1) {
                state.universalMetricSelectionBeforeSingle = selected;
                state.universalMetricSingleForced = true;
                setSelectedUniversalMetrics([chooseUniversalComparisonMetric(selected)]);
            }
            return;
        }
        if (state.universalMetricSingleForced
            && Array.isArray(state.universalMetricSelectionBeforeSingle)
            && state.universalMetricSelectionBeforeSingle.length > 1
            && selected.length <= 1) {
            setSelectedUniversalMetrics(state.universalMetricSelectionBeforeSingle);
        }
        state.universalMetricSingleForced = false;
        state.universalMetricSelectionBeforeSingle = [];
    }

    function syncUniversalFilterModeUi() {
        const mode = resolveUniversalEventMetricMode();
        applyUniversalAnalysisModeSelection(mode);
        enforceUniversalMetricSelectionForMode();
        renderUniversalAnalysisModeControl();
        updateUniversalEventToggleLabel();
        updateUniversalMetricToggleLabel();
        renderUniversalEventVisualList();
        renderUniversalMetricVisualList();
        removeUniversalInlineHints();
        refs.universalEventTypePopup?.setAttribute("data-universal-event-mode", mode);
        refs.universalSeriesMetricToggle?.setAttribute("data-universal-event-mode", mode);
        refs.universalEventTypeToggle?.setAttribute("data-universal-event-mode", mode);
        const eventReadonly = mode === "overall";
        refs.universalEventTypeToggle?.classList.toggle("is-readonly", eventReadonly);
        refs.universalEventTypeToggle?.setAttribute("aria-disabled", eventReadonly ? "true" : "false");
        refs.universalEventTypeToggle?.setAttribute("tabindex", eventReadonly ? "-1" : "0");
        if (eventReadonly) {
            refs.universalEventTypePopup?.classList.add("d-none");
            refs.universalEventTypeToggle?.setAttribute("aria-expanded", "false");
        }
    }

    function updateUniversalEventToggleLabel() {
        if (!refs.universalEventTypeToggle) {
            return;
        }
        const mode = resolveUniversalEventMetricMode();
        if (mode === "overall") {
            refs.universalEventTypeToggle.textContent = "События: все";
            refs.universalEventTypeToggle.title = "Общая статистика по всем событиям";
            return;
        }
        const selected = Array.from(selectedUniversalEventCodes());
        if (mode === "single-event") {
            if (!selected.length) {
                refs.universalEventTypeToggle.textContent = "Событие не выбрано";
                refs.universalEventTypeToggle.title = "Выберите событие для анализа";
                return;
            }
            const name = universalEventDisplayName(selected[0]);
            refs.universalEventTypeToggle.textContent = `Событие: ${name}`;
            refs.universalEventTypeToggle.title = `Событие: ${name}`;
            return;
        }
        refs.universalEventTypeToggle.textContent = `Сравниваемые события: ${selected.length}`;
        refs.universalEventTypeToggle.title = `Сравниваемые события: ${selected.length}`;
    }

    function enforceUniversalEventMetricDependency(source) {
        const mode = resolveUniversalEventMetricMode();
        if (mode !== "multi-event") {
            enforceUniversalMetricSelectionForMode();
            return;
        }
        const selectedMetrics = Array.from(selectedUniversalMetrics());
        const multipleMetricsSelected = selectedMetrics.length > 1;

        if ((source === "events" || source === "metrics") && multipleMetricsSelected) {
            if (!state.universalMetricSelectionBeforeSingle.length) {
                state.universalMetricSelectionBeforeSingle = selectedMetrics;
            }
            state.universalMetricSingleForced = true;
            setSelectedUniversalMetrics([chooseUniversalComparisonMetric(selectedMetrics)]);
        }
    }

    function universalParams(useBaseline, options = {}) {
        const includeEventFilter = options.includeEventFilter !== false;
        const params = new URLSearchParams();
        const ranges = resolveUniversalCompareRanges();
        const fromValue = useBaseline ? ranges.beforeFrom : ranges.afterFrom;
        const toValue = useBaseline ? ranges.beforeTo : ranges.afterTo;
        setIfPresent(params, "from", toIso(fromValue));
        setIfPresent(params, "to", toIso(toValue));
        const moduleCode = refs.moduleType?.value?.trim();
        if (moduleCode) {
            params.set("moduleCode", moduleCode);
        }
        const analysisMode = resolveUniversalEventMetricMode();
        params.set("analysisMode", analysisMode);
        const selectedEventCodes = selectedUniversalEventCodes();
        const eventFilterAllowed = includeEventFilter && analysisMode !== "overall";
        if (eventFilterAllowed && selectedEventCodes.size > 0) {
            selectedEventCodes.forEach((code) => {
                params.append("eventTypeCode", code);
            });
        }
        const attrCode = (refs.universalAttrCode?.value || "").trim();
        if (attrCode) {
            params.set("attributeCode", attrCode);
        }
        const attrValue = (refs.universalAttrValue?.value || "").trim();
        if (attrValue) {
            params.set("attributeValue", attrValue);
        }
        const stageType = (refs.universalStageType?.value || "").trim();
        if (stageType) {
            params.set("stageTypeCode", stageType);
        }
        const requestPath = refs.analyticsRequestPath?.value?.trim();
        if (requestPath) {
            params.set("requestPath", requestPath);
        }
        const bucket = (refs.universalBucket?.value || refs.bucket?.value || "").trim();
        if (bucket) {
            params.set("bucketMinutes", bucket);
        }
        if (options.includeEventStageBreakdown === false) {
            params.set("includeEventStageBreakdown", "false");
        }
        return params;
    }

    function resolveUniversalCompareRanges() {
        if (UNIVERSAL_COMPARE_FOLLOWS_GLOBAL) {
            return resolveGlobalBeforeRange();
        }
        const afterFromRaw = (refs.universalFrom?.value || refs.from?.value || "").trim();
        const afterToRaw = (refs.universalTo?.value || refs.to?.value || "").trim();
        const afterFromDate = afterFromRaw ? new Date(afterFromRaw) : null;
        const afterToDate = afterToRaw ? new Date(afterToRaw) : null;
        const hasAfterRange = afterFromDate
            && afterToDate
            && !Number.isNaN(afterFromDate.getTime())
            && !Number.isNaN(afterToDate.getTime())
            && afterFromDate.getTime() < afterToDate.getTime();
        if (!hasAfterRange) {
            const now = new Date();
            const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);
            return {
                beforeFrom: toDateTimeLocalString(new Date(oneHourAgo.getTime() - 60 * 60 * 1000)),
                beforeTo: toDateTimeLocalString(oneHourAgo),
                afterFrom: toDateTimeLocalString(oneHourAgo),
                afterTo: toDateTimeLocalString(now)
            };
        }
        const durationMs = Math.max(60_000, afterToDate.getTime() - afterFromDate.getTime());
        const computedBeforeToDate = afterFromDate;
        const computedBeforeFromDate = new Date(computedBeforeToDate.getTime() - durationMs);
        return {
            beforeFrom: toDateTimeLocalString(computedBeforeFromDate),
            beforeTo: toDateTimeLocalString(computedBeforeToDate),
            afterFrom: toDateTimeLocalString(afterFromDate),
            afterTo: toDateTimeLocalString(afterToDate)
        };
    }

    async function loadOverview(mainReloadRequestId) {
        const eventKpiMiniLoadId = Number(state.eventKpiMiniLoadId || 0) + 1;
        state.eventKpiMiniLoadId = eventKpiMiniLoadId;
        beginEventKpiMiniRenderPass(eventKpiMiniLoadId);
        try {
        const params = mainParams();
        const needInlineCompareEventsCount = !!state.inlineCompareEnabled["chart-events-count"];
        const needInlineCompareLatency = !!state.inlineCompareEnabled["chart-latency"];
        const needInlineCompareError = !!state.inlineCompareEnabled["chart-error-rate"];
        const miniKpiCompareMode = resolveMiniKpiCompareMode("chart-event-kpi");
        const needInlineCompareEventKpi = miniKpiCompareMode !== "off";
        const requests = [fetchJson(`${api("/overview")}?${params.toString()}`)];
        const baselineRequests = new Map();
        const targetRequests = new Map();
        const compareKeysByCanvas = new Map();
        const ensureOverviewPairRequests = (canvasId) => {
            const ranges = canvasId === "chart-event-kpi"
                ? resolveMiniKpiCompareRequestRanges()
                : resolveInlineCompareRequestRanges(canvasId);
            const rangeKey = serializeCompareRangeKey(ranges);
            const beforeKey = `overview-before:${rangeKey}`;
            const beforeParams = buildScopedParamsByLocalRange(ranges.beforeFrom, ranges.beforeTo);
            if (!baselineRequests.has(beforeKey)) {
                baselineRequests.set(
                    beforeKey,
                    fetchJson(`${api("/overview")}?${beforeParams.toString()}`)
                );
            }
            const afterKey = `overview-after:${rangeKey}`;
            const afterParams = buildScopedParamsByLocalRange(ranges.afterFrom, ranges.afterTo);
            if (!targetRequests.has(afterKey)) {
                targetRequests.set(
                    afterKey,
                    fetchJson(`${api("/overview")}?${afterParams.toString()}`)
                );
            }
            compareKeysByCanvas.set(canvasId, {beforeKey, afterKey});
        };
        if (needInlineCompareEventsCount) {
            ensureOverviewPairRequests("chart-events-count");
        }
        if (needInlineCompareLatency) {
            ensureOverviewPairRequests("chart-latency");
        }
        if (needInlineCompareError) {
            ensureOverviewPairRequests("chart-error-rate");
        }
        if (needInlineCompareEventKpi) {
            ensureOverviewPairRequests("chart-event-kpi");
        }
        const [data] = await Promise.all(requests);
        const baselineDataByKey = new Map();
        const targetDataByKey = new Map();
        if (baselineRequests.size > 0) {
            const entries = Array.from(baselineRequests.entries());
            const values = await Promise.all(entries.map((entry) => entry[1]));
            entries.forEach((entry, index) => {
                baselineDataByKey.set(entry[0], values[index]);
            });
        }
        if (targetRequests.size > 0) {
            const entries = Array.from(targetRequests.entries());
            const values = await Promise.all(entries.map((entry) => entry[1]));
            entries.forEach((entry, index) => {
                targetDataByKey.set(entry[0], values[index]);
            });
        }
        if (isStaleMainReloadRequest(mainReloadRequestId)) {
            completeEventKpiMiniRenderPass(eventKpiMiniLoadId);
            return;
        }

        refs.kpiTotalEvents.textContent = formatInt(data?.totals?.count || 0);
        refs.kpiAvgMs.textContent = formatMs(data?.totals?.avgMs);
        refs.kpiP95Ms.textContent = formatMs(data?.totals?.p95Ms);
        refs.kpiP99Ms.textContent = formatMs(data?.totals?.p99Ms);
        refs.kpiErrorRate.textContent = formatPercent(data?.totals?.errorRate);
        refs.kpiErrors.textContent = formatInt(data?.totals?.errorCount || 0);

        const eventsCountKeys = compareKeysByCanvas.get("chart-events-count");
        const latencyKeys = compareKeysByCanvas.get("chart-latency");
        const errorKeys = compareKeysByCanvas.get("chart-error-rate");
        const eventKpiKeys = compareKeysByCanvas.get("chart-event-kpi");
        const eventsCountData = needInlineCompareEventsCount ? (targetDataByKey.get(eventsCountKeys?.afterKey || "") || data) : data;
        const latencyData = needInlineCompareLatency ? (targetDataByKey.get(latencyKeys?.afterKey || "") || data) : data;
        const errorData = needInlineCompareError ? (targetDataByKey.get(errorKeys?.afterKey || "") || data) : data;
        const eventKpiData = needInlineCompareEventKpi ? (targetDataByKey.get(eventKpiKeys?.afterKey || "") || data) : data;

        const eventTimeLabels = (eventsCountData.series || []).map((point) => formatTime(point.time));
        const eventCountSeries = (eventsCountData.series || []).map((point) => point.count || 0);
        const sampledEvents = downsampleSeries(
            eventTimeLabels,
            [eventCountSeries],
            MAX_CHART_POINTS
        );
        const latencyLabels = (latencyData.series || []).map((point) => formatTime(point.time));
        const latencyAvgSeries = (latencyData.series || []).map((point) => point.avgMs || 0);
        const latencyP95Series = (latencyData.series || []).map((point) => point.p95Ms || 0);
        const latencyP99Series = (latencyData.series || []).map((point) => point.p99Ms || 0);
        const sampledLatency = downsampleSeries(
            latencyLabels,
            [latencyAvgSeries, latencyP95Series, latencyP99Series],
            MAX_CHART_POINTS
        );
        const errorLabels = (errorData.series || []).map((point) => formatTime(point.time));
        const errorSeries = (errorData.series || []).map((point) => toPercentNumber(point.errorRate));
        const sampledError = downsampleSeries(
            errorLabels,
            [errorSeries],
            MAX_CHART_POINTS
        );

        upsertChart("chart-events-count", {
            type: "line",
            data: {
                labels: sampledEvents.labels,
                datasets: [{
                    label: "Количество",
                    data: sampledEvents.datasets[0] || [],
                    borderColor: colors.primary,
                    backgroundColor: "rgba(109, 40, 217, 0.15)",
                    fill: true,
                    tension: 0.28,
                    pointRadius: 1.5
                }]
            },
            options: baseChartOptions("Количество")
        });
        if (needInlineCompareEventsCount) {
            const baselineData = baselineDataByKey.get(eventsCountKeys?.beforeKey || "");
            const baselineLabels = (baselineData?.series || []).map((point) => formatTime(point.time));
            const baselineCountSeries = (baselineData?.series || []).map((point) => point.count || 0);
            const sampledBaseline = downsampleSeries(baselineLabels, [baselineCountSeries], MAX_CHART_POINTS);
            const compareCanvasId = state.inlineCompareCanvasBySource["chart-events-count"] || "chart-events-count-compare-inline";
            upsertChart(compareCanvasId, {
                type: "line",
                data: {
                    labels: sampledBaseline.labels,
                    datasets: [{
                        label: "Количество (до)",
                        data: sampledBaseline.datasets[0] || [],
                        borderColor: colors.primary,
                        backgroundColor: "rgba(109, 40, 217, 0.12)",
                        fill: true,
                        tension: 0.28,
                        pointRadius: 1.4
                    }]
                },
                options: baseChartOptions("Количество")
            });
        }

        upsertChart("chart-latency", {
            type: "line",
            data: {
                labels: sampledLatency.labels,
                datasets: [
                    {
                        label: "AVG",
                        data: sampledLatency.datasets[0] || [],
                        borderColor: colors.teal,
                        backgroundColor: "rgba(15,118,110,0.14)",
                        tension: 0.25,
                        pointRadius: 1.2
                    },
                    {
                        label: "P95",
                        data: sampledLatency.datasets[1] || [],
                        borderColor: colors.accent,
                        backgroundColor: "rgba(124,58,237,0.16)",
                        tension: 0.25,
                        pointRadius: 1.2
                    },
                    {
                        label: "P99",
                        data: sampledLatency.datasets[2] || [],
                        borderColor: colors.amber,
                        backgroundColor: "rgba(180,83,9,0.16)",
                        tension: 0.25,
                        pointRadius: 1.2
                    }
                ]
            },
            options: baseChartOptions("ms")
        });
        if (needInlineCompareLatency) {
            const baselineData = baselineDataByKey.get(latencyKeys?.beforeKey || "");
            const baselineLabels = (baselineData?.series || []).map((point) => formatTime(point.time));
            const baselineAvg = (baselineData?.series || []).map((point) => point.avgMs || 0);
            const baselineP95 = (baselineData?.series || []).map((point) => point.p95Ms || 0);
            const baselineP99 = (baselineData?.series || []).map((point) => point.p99Ms || 0);
            const sampledBaseline = downsampleSeries(
                baselineLabels,
                [baselineAvg, baselineP95, baselineP99],
                MAX_CHART_POINTS
            );
            const compareCanvasId = state.inlineCompareCanvasBySource["chart-latency"] || "chart-latency-compare-inline";
            upsertChart(compareCanvasId, {
                type: "line",
                data: {
                    labels: sampledBaseline.labels,
                    datasets: [
                        {label: "AVG (До)", data: sampledBaseline.datasets[0] || [], borderColor: colors.teal, backgroundColor: "rgba(15,118,110,0.14)", tension: 0.25, pointRadius: 1.2},
                        {label: "P95 (До)", data: sampledBaseline.datasets[1] || [], borderColor: colors.accent, backgroundColor: "rgba(124,58,237,0.16)", tension: 0.25, pointRadius: 1.2},
                        {label: "P99 (До)", data: sampledBaseline.datasets[2] || [], borderColor: colors.amber, backgroundColor: "rgba(180,83,9,0.16)", tension: 0.25, pointRadius: 1.2}
                    ]
                },
                options: baseChartOptions("ms")
            });
        }

        upsertChart("chart-error-rate", {
            type: "line",
            data: {
                labels: sampledError.labels,
                datasets: [{
                    label: "Error rate, %",
                    data: sampledError.datasets[0] || [],
                    borderColor: colors.red,
                    backgroundColor: "rgba(185,28,28,0.16)",
                    fill: true,
                    tension: 0.25,
                    pointRadius: 1.2
                }]
            },
            options: barChartOptions("%")
        });
        if (needInlineCompareError) {
            const baselineData = baselineDataByKey.get(errorKeys?.beforeKey || "");
            const baselineLabels = (baselineData?.series || []).map((point) => formatTime(point.time));
            const baselineErr = (baselineData?.series || []).map((point) => toPercentNumber(point.errorRate));
            const sampledBaselineErr = downsampleSeries(baselineLabels, [baselineErr], MAX_CHART_POINTS);
            const compareCanvasId = state.inlineCompareCanvasBySource["chart-error-rate"] || "chart-error-rate-compare-inline";
            upsertChart(compareCanvasId, {
                type: "line",
                data: {
                    labels: sampledBaselineErr.labels,
                    datasets: [{
                        label: "Error rate (До), %",
                        data: sampledBaselineErr.datasets[0] || [],
                        borderColor: colors.red,
                        backgroundColor: "rgba(185,28,28,0.14)",
                        fill: true,
                        tension: 0.25,
                        pointRadius: 1.2
                    }]
                },
                options: barChartOptions("%")
            });
        }

        if (eventKpiMiniLoadId !== Number(state.eventKpiMiniLoadId || 0)) {
            return;
        }

        const baselineData = needInlineCompareEventKpi
            ? baselineDataByKey.get(eventKpiKeys?.beforeKey || "")
            : null;
        const baselineRows = buildEventKpiRows(baselineData?.eventBreakdown || []);
        const currentRows = buildEventKpiRows(eventKpiData.eventBreakdown || []);
        const eventKpiRanges = needInlineCompareEventKpi
            ? resolveMiniKpiCompareRequestRanges()
            : normalizeCompareRangesByAfter(refs.from?.value || "", refs.to?.value || "", "", "");
        state.eventKpiMiniRowsSnapshot = {
            rawMode: resolveEventKpiCompareModeRaw(),
            effectiveMode: miniKpiCompareMode,
            ranges: {...eventKpiRanges},
            currentRows,
            beforeRows: baselineRows
        };
        renderMiniEventKpiFromOverview({
            mode: miniKpiCompareMode,
            currentRows,
            baselineRows
        });
        if (!state.eventKpiFirstLoadCompleted) {
            await waitForChartPaint(1);
        }
        completeEventKpiMiniRenderPass(eventKpiMiniLoadId);
        } catch (error) {
            completeEventKpiMiniRenderPass(eventKpiMiniLoadId);
            throw error;
        }
    }

    function renderMiniEventKpiFromOverview({mode, currentRows, baselineRows}) {
        const effectiveMode = mode === "off" ? "off" : "overlay";
        const safeCurrentRows = Array.isArray(currentRows) ? currentRows : [];
        const safeBaselineRows = Array.isArray(baselineRows) ? baselineRows : [];
        clearEventKpiMiniCompareState();
        if (effectiveMode !== "off") {
            const overlayRows = buildEventKpiCompareOverlayRows(safeBaselineRows, safeCurrentRows);
            state.kpiRuntimeMetaBySource["chart-event-kpi"] = {
                labelsBefore: safeBaselineRows.length,
                labelsAfter: safeCurrentRows.length,
                labelsUnion: overlayRows.length
            };
            const eventKpiOverlayConfig = buildEventKpiOverlayChartConfig(safeCurrentRows, safeBaselineRows);
            upsertChart("chart-event-kpi", eventKpiOverlayConfig);
        } else {
            state.kpiRuntimeMetaBySource["chart-event-kpi"] = {
                labelsBefore: 0,
                labelsAfter: safeCurrentRows.length,
                labelsUnion: safeCurrentRows.length
            };
            const eventKpiSingleConfig = buildEventKpiSingleChartConfig(safeCurrentRows);
            upsertChart("chart-event-kpi", eventKpiSingleConfig);
        }
    }

    async function loadStages(mainReloadRequestId) {
        const params = mainParams();
        const needInlineCompareStageLatency = !!state.inlineCompareEnabled["chart-stage-latency"];
        const needInlineCompareStageErrors = !!state.inlineCompareEnabled["chart-stage-errors"];
        const baselineRequests = new Map();
        const targetRequests = new Map();
        const compareKeysByCanvas = new Map();
        const ensureStagesPairRequests = (canvasId) => {
            const ranges = resolveInlineCompareRequestRanges(canvasId);
            const rangeKey = serializeCompareRangeKey(ranges);
            const beforeKey = `stages-before:${rangeKey}`;
            if (!baselineRequests.has(beforeKey)) {
                baselineRequests.set(
                    beforeKey,
                    fetchJson(`${api("/stages")}?${buildScopedParamsByLocalRange(ranges.beforeFrom, ranges.beforeTo).toString()}`)
                );
            }
            const afterKey = `stages-after:${rangeKey}`;
            if (!targetRequests.has(afterKey)) {
                targetRequests.set(
                    afterKey,
                    fetchJson(`${api("/stages")}?${buildScopedParamsByLocalRange(ranges.afterFrom, ranges.afterTo).toString()}`)
                );
            }
            compareKeysByCanvas.set(canvasId, {beforeKey, afterKey});
        };
        if (needInlineCompareStageLatency) {
            ensureStagesPairRequests("chart-stage-latency");
        }
        if (needInlineCompareStageErrors) {
            ensureStagesPairRequests("chart-stage-errors");
        }
        const data = await fetchJson(`${api("/stages")}?${params.toString()}`);
        const baselineDataByKey = new Map();
        const targetDataByKey = new Map();
        if (baselineRequests.size > 0) {
            const entries = Array.from(baselineRequests.entries());
            const values = await Promise.all(entries.map((entry) => entry[1]));
            entries.forEach((entry, index) => {
                baselineDataByKey.set(entry[0], values[index]);
            });
        }
        if (targetRequests.size > 0) {
            const entries = Array.from(targetRequests.entries());
            const values = await Promise.all(entries.map((entry) => entry[1]));
            entries.forEach((entry, index) => {
                targetDataByKey.set(entry[0], values[index]);
            });
        }
        if (isStaleMainReloadRequest(mainReloadRequestId)) {
            return;
        }

        const stageLatencyKeys = compareKeysByCanvas.get("chart-stage-latency");
        const stageErrorsKeys = compareKeysByCanvas.get("chart-stage-errors");
        const stageLatencyData = needInlineCompareStageLatency ? (targetDataByKey.get(stageLatencyKeys?.afterKey || "") || data) : data;
        const stageErrorsData = needInlineCompareStageErrors ? (targetDataByKey.get(stageErrorsKeys?.afterKey || "") || data) : data;
        const rows = data.stages || [];
        refs.stageTableBody.innerHTML = rows.map((row) => `
            <tr>
                <td>${escapeHtml(row.stageTypeName || row.stageTypeCode || "-")}</td>
                <td class="text-end">${formatInt(row.count || 0)}</td>
                <td class="text-end">${formatMs(row.avgMs)}</td>
                <td class="text-end">${formatMs(row.p95Ms)}</td>
                <td class="text-end">${formatMs(row.p99Ms)}</td>
                <td class="text-end">${formatPercent(row.errorRate)}</td>
            </tr>
        `).join("");

        const stageLatencyRows = stageLatencyData.stages || [];
        const stageErrorsRows = stageErrorsData.stages || [];
        const stageLabels = stageLatencyRows.map((row) => row.stageTypeName || row.stageTypeCode);
        const stageP95 = stageLatencyRows.map((row) => row.p95Ms || 0);
        const stageAvg = stageLatencyRows.map((row) => row.avgMs || 0);
        const stageErrors = stageErrorsRows.map((row) => toPercentNumber(row.errorRate));

        upsertChart("chart-stage-latency", {
            type: "bar",
            data: {
                labels: stageLabels,
                datasets: [
                    {
                        label: "AVG, ms",
                        data: stageAvg,
                        backgroundColor: "rgba(15,118,110,0.72)",
                        borderRadius: 8
                    },
                    {
                        label: "P95, ms",
                        data: stageP95,
                        backgroundColor: "rgba(124,58,237,0.72)",
                        borderRadius: 8
                    }
                ]
            },
            options: barChartOptions("ms")
        });
        if (needInlineCompareStageLatency) {
            const baselineData = baselineDataByKey.get(stageLatencyKeys?.beforeKey || "");
            const baselineRows = baselineData?.stages || [];
            const compareCanvasId = state.inlineCompareCanvasBySource["chart-stage-latency"] || "chart-stage-latency-compare-inline";
            upsertChart(compareCanvasId, {
                type: "bar",
                data: {
                    labels: baselineRows.map((row) => row.stageTypeName || row.stageTypeCode),
                    datasets: [
                        {
                            label: "AVG (До), ms",
                            data: baselineRows.map((row) => row.avgMs || 0),
                            backgroundColor: "rgba(15,118,110,0.52)",
                            borderRadius: 8
                        },
                        {
                            label: "P95 (До), ms",
                            data: baselineRows.map((row) => row.p95Ms || 0),
                            backgroundColor: "rgba(124,58,237,0.52)",
                            borderRadius: 8
                        }
                    ]
                },
                options: barChartOptions("ms")
            });
        }

        upsertChart("chart-stage-errors", {
            type: "bar",
            data: {
                labels: stageLabels,
                datasets: [{
                    label: "Error rate, %",
                    data: stageErrors,
                    backgroundColor: "rgba(185,28,28,0.8)",
                    borderRadius: 8
                }]
            },
            options: barChartOptions("%")
        });
        if (needInlineCompareStageErrors) {
            const baselineData = baselineDataByKey.get(stageErrorsKeys?.beforeKey || "");
            const baselineRows = baselineData?.stages || [];
            const compareCanvasId = state.inlineCompareCanvasBySource["chart-stage-errors"] || "chart-stage-errors-compare-inline";
            upsertChart(compareCanvasId, {
                type: "bar",
                data: {
                    labels: baselineRows.map((row) => row.stageTypeName || row.stageTypeCode),
                    datasets: [{
                        label: "Error rate (До), %",
                        data: baselineRows.map((row) => toPercentNumber(row.errorRate)),
                        backgroundColor: "rgba(185,28,28,0.55)",
                        borderRadius: 8
                    }]
                },
                options: barChartOptions("%")
            });
        }
    }

    async function loadStageMetrics() {
        beginStageMetricPerf(resolveStageMetricPerfAction());
        const requestId = (state.stageMetricsRequestId || 0) + 1;
        state.stageMetricsRequestId = requestId;
        const previousController = state.stageMetricsAbortController;
        if (previousController) {
            previousController.abort();
        }
        const controller = new AbortController();
        state.stageMetricsAbortController = controller;
        const currentFilterKey = stageMetricFilterKey();
        const currentPrimaryFilterKey = stageMetricPrimaryFilterKey();
        if (state.stageMetricFilterKey !== currentFilterKey) {
            if (state.stageMetricPrimaryFilterKey !== currentPrimaryFilterKey) {
                clearStageMetricSeriesCache();
                clearStageMetricPayloadCache();
            }
            state.stageMetricFilterKey = currentFilterKey;
            state.stageMetricPrimaryFilterKey = currentPrimaryFilterKey;
        }
        const params = stageMetricParams("primary");
        params.set("includeTopValues", "false");
        params.set("includeSeries", "false");
        const stageType = refs.stageMetricStageType.value?.trim();
        if (stageType) {
            params.set("stageTypeCode", stageType);
        }

        let data;
        try {
            data = await fetchStageMetricPayload(params, {signal: controller.signal, purpose: "summaries"});
        } catch (error) {
            if (isAbortError(error)) {
                finishStageMetricPerf({aborted: true});
                return;
            }
            finishStageMetricPerf({error: error instanceof Error ? error.message : String(error || "unknown")});
            throw error;
        }
        if (isStaleStageMetricsRequest(requestId)) {
            finishStageMetricPerf({stale: true});
            return;
        }
        const buildStarted = nowMs();
        const summaries = mergeStageMetricSummariesWithDictionary(data.summaries || []);
        state.metricHelpByCode = {};
        const numericSummaries = summaries.filter((summary) => summary.numeric);
        const numericCodes = new Set(numericSummaries.map((summary) => summary.metricTypeCode));

        let selectedCodes = state.stageMetricSelectedCodes.filter((code) => numericCodes.has(code));
        if (!selectedCodes.length && numericSummaries.length > 0) {
            selectedCodes = [numericSummaries[0].metricTypeCode];
        }
        state.stageMetricSelectedCodes = selectedCodes;

        const renderMetricRow = (summary, kind) => {
            const metricCode = summary.metricTypeCode || "";
            state.metricHelpByCode[metricCode] = {
                code: metricCode,
                name: localizeMetricDisplayName(metricCode, summary.metricTypeName || summary.metricTypeCode || metricCode),
                description: summary.metricTypeDescription || "",
                guide: summary.metricTypeReadingGuide || ""
            };
            const topValuesHtml = renderTopValuesAccordion(summary.topValues || []);
            const metricHelp = (summary.metricTypeDescription || summary.metricTypeReadingGuide)
                ? `<button type="button" class="analytics-metric-help-badge" data-metric-code="${escapeHtml(metricCode)}" aria-label="Открыть пояснение метрики" title="Открыть пояснение">?</button>`
                : "";
            const isChecked = state.stageMetricSelectedCodes.includes(metricCode);
            const checkboxHtml = `
                <label class="analytics-metric-select" title="Показать на графике">
                    <input
                        type="checkbox"
                        class="form-check-input analytics-metric-toggle"
                        data-metric-code="${escapeHtml(metricCode)}"
                        ${isChecked ? "checked" : ""}
                    >
                    <span>${escapeHtml(localizeMetricDisplayName(metricCode, summary.metricTypeName || summary.metricTypeCode))}</span>
                </label>
            `;
            if (kind === "text") {
                return `
                    <tr>
                        <td>
                            <span class="analytics-metric-name-cell">
                                ${escapeHtml(localizeMetricDisplayName(metricCode, summary.metricTypeName || summary.metricTypeCode))}
                                ${metricHelp}
                            </span>
                        </td>
                        <td class="text-end">${formatInt(summary.sampleCount || 0)}</td>
                        <td class="small text-muted analytics-top-values-cell">${topValuesHtml}</td>
                    </tr>
                `;
            }
            return `
                <tr>
                    <td>
                        <span class="analytics-metric-name-cell">
                            ${checkboxHtml}
                            ${metricHelp}
                        </span>
                    </td>
                    <td class="text-end">${formatInt(summary.sampleCount || 0)}</td>
                    <td class="text-end">${summary.numeric ? formatMetric(summary.avgValue, summary.unit) : "-"}</td>
                    <td class="text-end">${summary.numeric ? formatMetric(summary.p95Value, summary.unit) : "-"}</td>
                    <td class="text-end">${summary.numeric ? formatMetric(summary.maxValue, summary.unit) : "-"}</td>
                    <td class="small text-muted analytics-top-values-cell">${topValuesHtml}</td>
                </tr>
            `;
        };

        if (refs.stageMetricTableBody) {
            refs.stageMetricTableBody.innerHTML = numericSummaries.map((summary) => renderMetricRow(summary, "numeric")).join("");
        }
        fillSelect(refs.stageTextStageType, state.dictionaries.stageTypes, "Все этапы", true, refs.stageTextStageType?.value || "");
        currentStageMetricPerf().buildMs += nowMs() - buildStarted;

        try {
            await Promise.all([
                loadStageMetricComparisonSeries(requestId),
                loadStageMetricTextBlock(summaries, requestId)
            ]);
        } catch (error) {
            finishStageMetricPerf({error: error instanceof Error ? error.message : String(error || "unknown")});
            throw error;
        }
        if (isStaleStageMetricsRequest(requestId)) {
            finishStageMetricPerf({stale: true});
            return;
        }
        await rebuildOpenedStageMetricExpanded(state.expandedChart.sourceCanvasId);
        finishStageMetricPerf({
            numericSelected: state.stageMetricSelectedCodes.length,
            textSelected: state.stageMetricTextSelectedCodes.length,
            compareMode: readStageMetricCompareMode()
        });

    }

    async function loadStageMetricComparisonSeries(requestId) {
        const renderStarted = nowMs();
        requestId = ensureStageMetricsRequestId(requestId);
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        const selectedCodes = Array.from(new Set(
            state.stageMetricSelectedCodes
                .map((code) => (code || "").trim())
                .filter((code) => code.length > 0)
        ));
        state.stageMetricSelectedCodes = selectedCodes;
        if (!selectedCodes.length) {
            upsertChart("chart-stage-metric-series", {type: "line", data: {labels: [], datasets: []}, options: baseChartOptions("Значение")});
            destroyChart("chart-stage-metric-series-compare");
            destroyChart("chart-stage-metric-top-values");
            destroyChart("chart-stage-metric-top-values-compare");
        } else {
            if (isStageMetricOverlayCompare()) {
                await renderStageMetricOverlayLineChart({
                    lineCanvasId: "chart-stage-metric-series",
                    selectedCodes,
                    requestId
                });
                destroyChart("chart-stage-metric-series-compare");
                destroyChart("chart-stage-metric-top-values-compare");
            } else {
                if (isStageMetricSplitCompare()) {
                    await Promise.all([
                        renderStageMetricRangeCharts({
                            rangeMode: "primary",
                            lineCanvasId: "chart-stage-metric-series",
                            barCanvasId: null,
                            selectedCodes,
                            requestId
                        }),
                        renderStageMetricRangeCharts({
                            rangeMode: "compare",
                            lineCanvasId: "chart-stage-metric-series-compare",
                            barCanvasId: null,
                            selectedCodes,
                            requestId
                        })
                    ]);
                } else {
                    await renderStageMetricRangeCharts({
                        rangeMode: "primary",
                        lineCanvasId: "chart-stage-metric-series",
                        barCanvasId: null,
                        selectedCodes,
                        requestId
                    });
                    destroyChart("chart-stage-metric-series-compare");
                    destroyChart("chart-stage-metric-top-values-compare");
                }
            }
            destroyChart("chart-stage-metric-top-values");
            destroyChart("chart-stage-metric-top-values-compare");
        }

        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        await rebuildOpenedStageMetricExpanded("chart-stage-metric-series");
        await waitForChartPaint(2);
        const elapsed = nowMs() - renderStarted;
        currentStageMetricPerf().numericRenderMs += elapsed;
        currentStageMetricPerf().renderMs += elapsed;
    }

    async function loadStageMetricTextBlock(preloadedSummaries, requestId) {
        requestId = ensureStageMetricsRequestId(requestId);
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        if (!refs.stageMetricTextTableBody) {
            return;
        }
        let summaries = Array.isArray(preloadedSummaries) ? preloadedSummaries : null;
        if (!summaries) {
            const params = stageTextParams("primary");
            params.set("includeTopValues", "false");
            params.set("includeSeries", "false");
            const data = await fetchStageMetricPayload(params, {purpose: "text-summaries"});
            summaries = data.summaries || [];
        }
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        summaries = mergeStageMetricSummariesWithDictionary(summaries);
        const textSummaries = (summaries || []).filter((summary) => !summary.numeric);
        const previous = (refs.stageTextMetricType?.value || "").trim();
        const availableCodes = new Set(textSummaries.map((summary) => summary.metricTypeCode));
        let selected = previous;
        if (!selected || !availableCodes.has(selected)) {
            selected = textSummaries[0]?.metricTypeCode || "";
        }
        state.stageMetricTextSelectedCodes = selected ? [selected] : [];
        refs.stageMetricTextTableBody.innerHTML = textSummaries.map((summary) => {
            const metricCode = summary.metricTypeCode || "";
            const metricHelp = (summary.metricTypeDescription || summary.metricTypeReadingGuide)
                ? `<button type="button" class="analytics-metric-help-badge" data-metric-code="${escapeHtml(metricCode)}" aria-label="Открыть пояснение метрики" title="Открыть пояснение">?</button>`
                : "";
            const topValuesHtml = renderTopValuesAccordion(summary.topValues || []);
            return `
                <tr>
                    <td>
                        <span class="analytics-metric-name-cell">
                            ${escapeHtml(localizeMetricDisplayName(metricCode, summary.metricTypeName || summary.metricTypeCode))}
                            ${metricHelp}
                        </span>
                    </td>
                    <td class="text-end">${formatInt(summary.sampleCount || 0)}</td>
                    <td class="small text-muted analytics-top-values-cell">${topValuesHtml}</td>
                </tr>
            `;
        }).join("");
        fillSelect(
            refs.stageTextMetricType,
            textSummaries.map((summary) => ({
                code: summary.metricTypeCode,
                name: localizeMetricDisplayName(summary.metricTypeCode, summary.metricTypeName || summary.metricTypeCode)
            })),
            "Выберите метрику",
            false,
            selected
        );
        await loadStageMetricTextCharts(requestId);
    }

    async function loadStageMetricTextCharts(requestId) {
        const renderStarted = nowMs();
        requestId = ensureStageMetricsRequestId(requestId);
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        if (!refs.stageMetricTextTableBody) {
            destroyChart("chart-stage-metric-text");
            destroyChart("chart-stage-metric-text-compare");
            await rebuildOpenedStageMetricExpanded("chart-stage-metric-text");
            await waitForChartPaint(2);
            const elapsed = nowMs() - renderStarted;
            currentStageMetricPerf().textRenderMs += elapsed;
            currentStageMetricPerf().renderMs += elapsed;
            return;
        }
        const selectedCodes = Array.from(new Set(
            state.stageMetricTextSelectedCodes
                .map((code) => (code || "").trim())
                .filter((code) => code.length > 0)
        ));
        state.stageMetricTextSelectedCodes = selectedCodes;
        if (!selectedCodes.length) {
            destroyChart("chart-stage-metric-text");
            destroyChart("chart-stage-metric-text-compare");
            await rebuildOpenedStageMetricExpanded("chart-stage-metric-text");
            await waitForChartPaint(2);
            const elapsed = nowMs() - renderStarted;
            currentStageMetricPerf().textRenderMs += elapsed;
            currentStageMetricPerf().renderMs += elapsed;
            return;
        }
        if (isStageMetricTextOverlayCompare()) {
            await renderStageMetricTextOverlayChart("chart-stage-metric-text", selectedCodes, requestId);
            destroyChart("chart-stage-metric-text-compare");
            await rebuildOpenedStageMetricExpanded("chart-stage-metric-text");
            await waitForChartPaint(2);
            const elapsed = nowMs() - renderStarted;
            currentStageMetricPerf().textRenderMs += elapsed;
            currentStageMetricPerf().renderMs += elapsed;
            return;
        }
        if (isStageMetricTextSplitCompare()) {
            await Promise.all([
                renderStageMetricTextChartByRange("compare", "chart-stage-metric-text", selectedCodes, requestId),
                renderStageMetricTextChartByRange("primary", "chart-stage-metric-text-compare", selectedCodes, requestId)
            ]);
            await rebuildOpenedStageMetricExpanded("chart-stage-metric-text");
            await waitForChartPaint(2);
            const elapsed = nowMs() - renderStarted;
            currentStageMetricPerf().textRenderMs += elapsed;
            currentStageMetricPerf().renderMs += elapsed;
            return;
        }
        await renderStageMetricTextChartByRange("primary", "chart-stage-metric-text", selectedCodes, requestId);
        if (!isStageMetricTextSplitCompare()) {
            destroyChart("chart-stage-metric-text-compare");
            await rebuildOpenedStageMetricExpanded("chart-stage-metric-text");
            await waitForChartPaint(2);
            const elapsed = nowMs() - renderStarted;
            currentStageMetricPerf().textRenderMs += elapsed;
            currentStageMetricPerf().renderMs += elapsed;
            return;
        }
    }

    async function renderStageMetricTextChartByRange(rangeMode, canvasId, selectedCodes, requestId) {
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        const results = await Promise.allSettled(selectedCodes.map((code) => fetchStageMetricSeries(code, rangeMode, true)));
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        const payloads = results
            .filter((result) => result.status === "fulfilled")
            .map((result) => result.value)
            .filter((payload) => payload && payload.metricCode);
        if (!payloads.length) {
            renderEmptyStageMetricTextChart(canvasId, selectedCodes[0] || "Текстовая метрика");
            return;
        }
        const payload = payloads[0];
        const topValues = Array.isArray(payload.topValues)
            ? payload.topValues.filter((item) => item && item.value != null && Number(item.count || 0) > 0)
            : [];
        if (!topValues.length) {
            renderEmptyStageMetricTextChart(canvasId, payload.metricName || selectedCodes[0] || "Текстовая метрика");
            return;
        }
        const labels = topValues.map((item) => String(item.value));
        const counts = topValues.map((item) => Number(item.count || 0));
        upsertChart(canvasId, {
            type: "bar",
            data: {
                labels,
                datasets: [{
                    label: `${payload.metricName}: топ значений`,
                    data: counts,
                    backgroundColor: "rgba(30, 136, 229, 0.72)",
                    borderColor: "#1e40af",
                    borderWidth: 1,
                    borderRadius: 7
                }]
            },
            options: barChartOptions("Количество")
        });
    }

    async function renderStageMetricTextOverlayChart(canvasId, selectedCodes, requestId) {
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        const metricCode = selectedCodes[0];
        if (!metricCode) {
            destroyChart(canvasId);
            return;
        }
        const [primaryResult, compareResult] = await Promise.allSettled([
            fetchStageMetricSeries(metricCode, "primary", true),
            fetchStageMetricSeries(metricCode, "compare", true)
        ]);
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        const primary = primaryResult.status === "fulfilled" ? primaryResult.value : null;
        const compare = compareResult.status === "fulfilled" ? compareResult.value : null;
        const primaryValues = Array.isArray(primary?.topValues)
            ? primary.topValues.filter((item) => item && item.value != null && Number(item.count || 0) > 0)
            : [];
        const compareValues = Array.isArray(compare?.topValues)
            ? compare.topValues.filter((item) => item && item.value != null && Number(item.count || 0) > 0)
            : [];
        const labels = Array.from(new Set([
            ...primaryValues.map((item) => String(item.value)),
            ...compareValues.map((item) => String(item.value))
        ]));
        if (!labels.length) {
            destroyChart(canvasId);
            return;
        }
        const primaryByValue = new Map(primaryValues.map((item) => [String(item.value), Number(item.count || 0)]));
        const compareByValue = new Map(compareValues.map((item) => [String(item.value), Number(item.count || 0)]));
        const metricName = primary?.metricName || compare?.metricName || metricCode;
        upsertChart(canvasId, {
            type: "bar",
            data: {
                labels,
                datasets: [
                    {
                        label: `${metricName}: топ значений`,
                        data: labels.map((label) => primaryByValue.get(label) || 0),
                        backgroundColor: "rgba(30, 136, 229, 0.72)",
                        borderColor: "#1e40af",
                        borderWidth: 1,
                        borderRadius: 7
                    },
                    {
                        label: `${metricName}: топ значений (До)`,
                        data: labels.map((label) => compareByValue.get(label) || 0),
                        backgroundColor: "rgba(30, 136, 229, 0.28)",
                        borderColor: "rgba(30, 64, 175, 0.6)",
                        borderWidth: 1,
                        borderRadius: 7
                    }
                ]
            },
            options: barChartOptions("Количество")
        });
    }

    async function renderStageMetricRangeCharts({rangeMode, lineCanvasId, barCanvasId, selectedCodes, requestId}) {
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        const results = await Promise.allSettled(selectedCodes.map((code) => fetchStageMetricSeries(code, rangeMode)));
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        const seriesPayloadMap = new Map();
        results
            .filter((result) => result.status === "fulfilled")
            .map((result) => result.value)
            .filter((payload) => payload && payload.metricCode && Array.isArray(payload.series))
            .forEach((payload) => {
                if (!seriesPayloadMap.has(payload.metricCode)) {
                    seriesPayloadMap.set(payload.metricCode, payload);
                }
            });
        const seriesPayloads = Array.from(seriesPayloadMap.values());
        renderStageMetricLinePayloads(lineCanvasId, seriesPayloads);
        if (!seriesPayloads.length) {
            destroyChart(barCanvasId);
            return;
        }
        if (!barCanvasId) {
            return;
        }
        const units = new Set(seriesPayloads.map((payload) => payload.unit).filter((unit) => unit));
        const unitKeys = new Set(seriesPayloads.map((payload) => normalizeMetricUnitKey(payload.unit)));
        const hasMixedUnits = unitKeys.size > 1;
        const yTitle = units.size === 1 ? localizeUnit(Array.from(units)[0]) : "Значение";
        const palette = ["#6d28d9", "#0f766e", "#b45309", "#b91c1c", "#1d4ed8", "#0f766e", "#be185d", "#475569"];

        if (seriesPayloads.length >= 2) {
            const compareLabels = [];
            const compareValues = [];
            const compareColors = [];
            let compareDatasetLabel = "P95 (среднее по периоду)";
            let compareAxisTitle = yTitle || "Значение";
            seriesPayloads.forEach((payload, index) => {
                const localizedUnit = localizeUnit(payload.unit);
                compareLabels.push(localizedUnit ? `${payload.metricName} (${localizedUnit})` : payload.metricName);
                const numericValues = (payload.series || []).map((point) => point?.p95Ms).filter((value) => Number.isFinite(Number(value)));
                const avgP95 = numericValues.length ? numericValues.reduce((sum, value) => sum + Number(value), 0) / numericValues.length : 0;
                if (!hasMixedUnits) {
                    compareValues.push(Number(avgP95.toFixed(4)));
                } else {
                    const avgValues = (payload.series || []).map((point) => point?.avgMs).filter((value) => Number.isFinite(Number(value)));
                    const avgAvg = avgValues.length ? avgValues.reduce((sum, value) => sum + Number(value), 0) / avgValues.length : 0;
                    const ratio = avgAvg > 0 ? (avgP95 / avgAvg) : 0;
                    compareValues.push(Number(ratio.toFixed(4)));
                }
                compareColors.push(palette[index % palette.length]);
            });
            if (hasMixedUnits) {
                compareDatasetLabel = "P95 / AVG (раз, смешанные единицы)";
                compareAxisTitle = "раз";
            }
            upsertChart(barCanvasId, {
                type: "bar",
                data: {
                    labels: compareLabels,
                    datasets: [{label: compareDatasetLabel, data: compareValues, backgroundColor: compareColors, borderRadius: 7}]
                },
                options: barChartOptions(compareAxisTitle)
            });
            return;
        }

        const topValues = seriesPayloads[0]?.topValues || [];
        const informativeTopValues = Array.isArray(topValues) ? topValues.filter((item) => Number(item?.count || 0) > 1) : [];
        if (!informativeTopValues.length) {
            destroyChart(barCanvasId);
            return;
        }
        upsertChart(barCanvasId, {
            type: "bar",
            data: {
                labels: informativeTopValues.map((item) => item.value),
                datasets: [{
                    label: "Количество",
                    data: informativeTopValues.map((item) => item.count || 0),
                    backgroundColor: "rgba(109,40,217,0.8)",
                    borderRadius: 7
                }]
            },
            options: {
                ...baseChartOptions(""),
                scales: {
                    x: {grid: {color: "rgba(148,163,184,0.12)"}, ticks: {color: "#64748b", maxRotation: 45, minRotation: 20}},
                    y: {grid: {color: "rgba(148,163,184,0.14)"}, ticks: {color: "#64748b"}}
                }
            }
        });
    }

    function renderStageMetricLinePayloads(lineCanvasId, seriesPayloads, comparePayloads = []) {
        const primaryPayloads = Array.isArray(seriesPayloads) ? seriesPayloads : [];
        const beforePayloads = Array.isArray(comparePayloads) ? comparePayloads : [];
        const allPayloads = [...primaryPayloads, ...beforePayloads];
        if (!allPayloads.length) {
            upsertChart(lineCanvasId, {type: "line", data: {labels: [], datasets: []}, options: baseChartOptions("Значение")});
            return;
        }

        const allTimes = new Set();
        for (const payload of allPayloads) {
            for (const point of payload.series || []) {
                if (point?.time) {
                    allTimes.add(point.time);
                }
            }
        }
        const sortedTimes = Array.from(allTimes).sort();
        const labels = sortedTimes.map((time) => formatTime(time));
        const units = new Set(allPayloads.map((payload) => payload.unit).filter((unit) => unit));
        const unitKeys = new Set(allPayloads.map((payload) => normalizeMetricUnitKey(payload.unit)));
        const hasMixedUnits = unitKeys.size > 1;
        const yTitle = units.size === 1 ? localizeUnit(Array.from(units)[0]) : "Значение";
        const palette = ["#6d28d9", "#0f766e", "#b45309", "#b91c1c", "#1d4ed8", "#0f766e", "#be185d", "#475569"];

        const datasetMeta = [];
        const rawSeries = [];
        const appendPayloads = (payloads, isBefore) => {
            payloads.forEach((payload, index) => {
                const byTime = new Map((payload.series || []).map((point) => [point.time, point]));
                rawSeries.push(sortedTimes.map((time) => {
                    const point = byTime.get(time);
                    return point ? (point.avgMs ?? null) : null;
                }));
                datasetMeta.push({payload, index, isBefore});
            });
        };
        appendPayloads(primaryPayloads, false);
        appendPayloads(beforePayloads, true);
        const chartSeries = hasMixedUnits ? rawSeries.map((series) => normalizeSeriesToPercent(series)) : rawSeries;
        const sampled = downsampleSeries(labels, chartSeries, MAX_CHART_POINTS);
        const datasets = datasetMeta.map((meta, datasetIndex) => {
            const color = palette[meta.index % palette.length];
            const localizedUnit = localizeUnit(meta.payload.unit);
            const baseLabel = localizedUnit ? `${meta.payload.metricName} (${localizedUnit})` : meta.payload.metricName;
            return {
                label: meta.isBefore ? `${baseLabel} (До)` : baseLabel,
                data: sampled.datasets[datasetIndex] || [],
                borderColor: meta.isBefore ? withAlphaColor(color, 0.58) : color,
                backgroundColor: "transparent",
                borderDash: meta.isBefore ? [6, 4] : undefined,
                tension: 0.25,
                pointRadius: meta.isBefore ? 1 : 1.4,
                spanGaps: true
            };
        });
        upsertChart(lineCanvasId, {
            type: "line",
            data: {labels: sampled.labels, datasets},
            options: baseChartOptions(hasMixedUnits ? "Нормализовано, % от max" : (yTitle || "Значение"))
        });
    }

    async function renderStageMetricOverlayLineChart({lineCanvasId, selectedCodes, requestId}) {
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        const [primaryResults, compareResults] = await Promise.all([
            Promise.allSettled(selectedCodes.map((code) => fetchStageMetricSeries(code, "primary"))),
            Promise.allSettled(selectedCodes.map((code) => fetchStageMetricSeries(code, "compare")))
        ]);
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        const toPayloads = (results) => {
            const seriesPayloadMap = new Map();
            results
                .filter((result) => result.status === "fulfilled")
                .map((result) => result.value)
                .filter((payload) => payload && payload.metricCode && Array.isArray(payload.series))
                .forEach((payload) => {
                    if (!seriesPayloadMap.has(payload.metricCode)) {
                        seriesPayloadMap.set(payload.metricCode, payload);
                    }
                });
            return Array.from(seriesPayloadMap.values());
        };
        const primaryPayloads = toPayloads(primaryResults);
        const comparePayloads = projectStageMetricComparePayloadsToPrimaryRange(toPayloads(compareResults));
        renderStageMetricLinePayloads(lineCanvasId, primaryPayloads, comparePayloads);
    }

    function projectStageMetricComparePayloadsToPrimaryRange(payloads) {
        const ranges = normalizeCompareRangesByAfter(
            refs.stageMetricFromA?.value || refs.from?.value || "",
            refs.stageMetricToA?.value || refs.to?.value || "",
            "",
            ""
        );
        const beforeFrom = parseLocalDateTimeMs(ranges.beforeFrom);
        const afterFrom = parseLocalDateTimeMs(ranges.afterFrom);
        const afterTo = parseLocalDateTimeMs(ranges.afterTo);
        if (!Number.isFinite(beforeFrom) || !Number.isFinite(afterFrom)) {
            return payloads;
        }
        return (payloads || []).map((payload) => ({
            ...payload,
            series: (payload.series || [])
                .map((point) => {
                    const beforeTime = Date.parse(point?.time || "");
                    if (!Number.isFinite(beforeTime)) {
                        return null;
                    }
                    const projectedTime = afterFrom + (beforeTime - beforeFrom);
                    if (Number.isFinite(afterTo) && projectedTime > afterTo) {
                        return null;
                    }
                    return {
                        ...point,
                        time: new Date(projectedTime).toISOString()
                    };
                })
                .filter(Boolean)
        }));
    }

    function parseLocalDateTimeMs(value) {
        const raw = String(value || "").trim();
        if (!raw) {
            return Number.NaN;
        }
        const timestamp = new Date(raw).getTime();
        return Number.isNaN(timestamp) ? Number.NaN : timestamp;
    }

    function stageMetricRequestPath(params) {
        const query = params instanceof URLSearchParams ? params.toString() : String(params || "");
        return `${api("/stage-metrics")}?${query}`;
    }

    async function fetchStageMetricPayload(params, options = {}) {
        const query = params instanceof URLSearchParams ? params.toString() : String(params || "");
        const key = query;
        const perf = currentStageMetricPerf();
        const cached = readStageMetricPayloadCache(key);
        if (cached) {
            perf.cacheHits += 1;
            if (!perf.queryStrings.includes(query)) {
                perf.queryStrings.push(query);
            }
            console.info("[STAGE_METRICS_PERF] frontend cache", {
                purpose: options.purpose || "",
                hit: true,
                query
            });
            return cached;
        }

        const promiseMap = state.stageMetricPayloadPromiseByKey instanceof Map
            ? state.stageMetricPayloadPromiseByKey
            : new Map();
        state.stageMetricPayloadPromiseByKey = promiseMap;
        if (promiseMap.has(key)) {
            perf.inflightHits += 1;
            if (!perf.queryStrings.includes(query)) {
                perf.queryStrings.push(query);
            }
            console.info("[STAGE_METRICS_PERF] frontend cache", {
                purpose: options.purpose || "",
                inflight: true,
                query
            });
            return promiseMap.get(key);
        }

        perf.cacheMisses += 1;
        perf.requestCount += 1;
        if (!perf.queryStrings.includes(query)) {
            perf.queryStrings.push(query);
        }
        const url = stageMetricRequestPath(params);
        const requestStarted = nowMs();
        const payloadPromise = (async () => {
            const response = await fetch(url, {
                headers: {
                    "Accept": "application/json",
                    "X-Requested-With": "XMLHttpRequest"
                },
                credentials: "same-origin",
                signal: options.signal
            });
            const waitMs = nowMs() - requestStarted;
            const contentType = (response.headers.get("content-type") || "").toLowerCase();
            const expectsJson = contentType.includes("application/json");
            if (!response.ok || !expectsJson) {
                return fetchJson(url, {signal: options.signal});
            }
            const jsonStarted = nowMs();
            const payload = await response.json();
            const jsonMs = nowMs() - jsonStarted;
            perf.fetchTotalMs += nowMs() - requestStarted;
            perf.jsonMs += jsonMs;
            console.info("[STAGE_METRICS_PERF] frontend fetch", {
                purpose: options.purpose || "",
                totalMs: Math.round(nowMs() - requestStarted),
                waitMs: Math.round(waitMs),
                jsonMs: Math.round(jsonMs),
                status: response.status,
                bytes: Number(response.headers.get("content-length") || 0) || null,
                query
            });
            writeStageMetricPayloadCache(key, payload);
            return payload;
        })();

        promiseMap.set(key, payloadPromise);
        try {
            return await payloadPromise;
        } finally {
            promiseMap.delete(key);
        }
    }

    async function fetchStageMetricSeries(metricCode, rangeMode, useTextParams) {
        const params = useTextParams ? stageTextParams(rangeMode) : stageMetricParams(rangeMode);
        if (!useTextParams) {
            const stageType = refs.stageMetricStageType.value?.trim();
            if (stageType) {
                params.set("stageTypeCode", stageType);
            }
        }
        params.set("metricTypeCode", metricCode);
        params.set("includeSummaries", "false");
        if (!useTextParams) {
            params.set("includeTopValues", "false");
        }
        const cacheKey = `${useTextParams ? "text" : "chart"}|${rangeMode || "primary"}|${metricCode || ""}|${params.toString()}`;
        const cached = readStageMetricSeriesCache(cacheKey);
        if (cached) {
            currentStageMetricPerf().cacheHits += 1;
            return cached;
        }
        const data = await fetchStageMetricPayload(params, {purpose: useTextParams ? "text-series" : "numeric-series"});
        const payload = {
            metricCode,
            metricName: localizeMetricDisplayName(metricCode, data.selectedMetricTypeName || metricCode),
            unit: data.selectedUnit || "",
            series: data.numericSeries || [],
            topValues: data.selectedTopValues || [],
            sampleCount: Number(data.selectedSampleCount || data.summary?.sampleCount || 0)
        };
        writeStageMetricSeriesCache(cacheKey, payload);
        return payload;
    }

    function stageMetricFilterKey() {
        const params = new URLSearchParams(stageMetricPrimaryFilterKey());
        if (!isStageMetricCompareEnabled()) {
            return params.toString();
        }
        const paramsCompare = stageMetricParams("compare");
        const stageType = refs.stageMetricStageType.value?.trim();
        if (stageType) {
            paramsCompare.set("stageTypeCode", stageType);
        }
        paramsCompare.delete("metricTypeCode");
        return `${params.toString()}|${paramsCompare.toString()}`;
    }

    function stageMetricPrimaryFilterKey() {
        const params = stageMetricParams("primary");
        const stageType = refs.stageMetricStageType.value?.trim();
        if (stageType) {
            params.set("stageTypeCode", stageType);
        }
        params.delete("metricTypeCode");
        return params.toString();
    }

    function normalizeCompareMode(modeRaw) {
        const mode = String(modeRaw || "").trim().toLowerCase();
        return isValidInlineCompareMode(mode) ? mode : "off";
    }

    function syncCompareModeSelectOptionLabels(selectEl, modeRaw) {
        if (!selectEl) {
            return;
        }
        const selectedMode = normalizeCompareMode(modeRaw);
        Array.from(selectEl.options || []).forEach((option) => {
            const value = normalizeCompareMode(option.value);
            option.textContent = `${value === selectedMode ? "вњ“ " : ""}${compareModeLabel(value)}`;
        });
        selectEl.value = selectedMode;
    }

    function readStageMetricCompareMode() {
        const selectMode = refs.stageMetricCompareMode?.value;
        if (isValidInlineCompareMode(selectMode)) {
            return normalizeCompareMode(selectMode);
        }
        if (refs.stageMetricCompareEnabled) {
            return refs.stageMetricCompareEnabled.checked ? "split" : "off";
        }
        return normalizeCompareMode(state.stageMetricCompareMode || resolveGlobalInlineCompareMode());
    }

    function resolveStageMetricCompareAction(previousModeRaw, nextModeRaw) {
        const previousMode = normalizeCompareMode(previousModeRaw);
        const nextMode = normalizeCompareMode(nextModeRaw);
        if (previousMode !== "off" && nextMode === "off") {
            return "compare_to_off";
        }
        if (previousMode === "overlay" && nextMode === "split") {
            return "compare_overlay_to_split";
        }
        if (previousMode === "split" && nextMode === "overlay") {
            return "compare_split_to_overlay";
        }
        if (previousMode === "off" && nextMode !== "off") {
            return "compare_on";
        }
        return "compare_change";
    }

    function setStageMetricCompareMode(modeRaw, options = {}) {
        const mode = normalizeCompareMode(modeRaw);
        const previous = normalizeCompareMode(state.stageMetricCompareMode || readStageMetricCompareMode());
        state.stageMetricCompareMode = mode;
        if (refs.stageMetricCompareMode) {
            syncCompareModeSelectOptionLabels(refs.stageMetricCompareMode, mode);
        }
        if (refs.stageMetricCompareEnabled) {
            refs.stageMetricCompareEnabled.checked = mode !== "off";
        }
        if (options.syncText === true && !refs.stageTextCompareMode && !refs.stageTextCompareEnabled) {
            state.stageTextCompareMode = mode;
        }
        updateStageMetricCompareUi();
        return previous !== mode;
    }

    function readStageTextCompareMode() {
        const selectMode = refs.stageTextCompareMode?.value;
        if (isValidInlineCompareMode(selectMode)) {
            return normalizeCompareMode(selectMode);
        }
        if (refs.stageTextCompareEnabled) {
            return refs.stageTextCompareEnabled.checked ? "split" : "off";
        }
        return normalizeCompareMode(state.stageTextCompareMode || readStageMetricCompareMode());
    }

    function setStageTextCompareMode(modeRaw) {
        const mode = normalizeCompareMode(modeRaw);
        const previous = normalizeCompareMode(state.stageTextCompareMode || readStageTextCompareMode());
        state.stageTextCompareMode = mode;
        if (refs.stageTextCompareMode) {
            syncCompareModeSelectOptionLabels(refs.stageTextCompareMode, mode);
        }
        if (refs.stageTextCompareEnabled) {
            refs.stageTextCompareEnabled.checked = mode !== "off";
        }
        updateStageMetricTextCompareUi();
        return previous !== mode;
    }

    function applyGlobalCompareModeToStageMetrics(modeRaw) {
        const mode = normalizeCompareMode(modeRaw);
        const metricChanged = setStageMetricCompareMode(mode, {syncText: false});
        const textChanged = setStageTextCompareMode(mode);
        syncStageMetricRangesFromMain(true);
        syncStageTextRangesFromMain(true);
        return metricChanged || textChanged;
    }

    function isStageMetricCompareEnabled() {
        return readStageMetricCompareMode() !== "off";
    }

    function isStageMetricSplitCompare() {
        return readStageMetricCompareMode() === "split";
    }

    function isStageMetricOverlayCompare() {
        return readStageMetricCompareMode() === "overlay";
    }

    function updateStageMetricCompareUi() {
        const enabled = isStageMetricCompareEnabled();
        const split = isStageMetricSplitCompare();
        syncCompareModeSelectOptionLabels(refs.stageMetricCompareMode, readStageMetricCompareMode());
        refs.stageMetricForm?.classList.toggle("is-compare-enabled", enabled);
        refs.stageMetricForm?.classList.toggle("is-compare-disabled", !enabled);
        refs.stageMetricCompareControlsWrap?.classList.add("d-none");
        refs.stageMetricCompareCol?.classList.toggle("d-none", !split);
        const topGrid = document.getElementById("analytics-stage-metric-charts");
        topGrid?.classList.toggle("is-single", !split);
        const textGrid = document.getElementById("analytics-stage-metric-text-charts");
        textGrid?.classList.toggle("is-single", !isStageMetricTextSplitCompare());
        refs.stageMetricTextCompareCol?.classList.toggle("d-none", !isStageMetricTextSplitCompare());
        const compareLabels = refs.stageMetricForm?.querySelectorAll("[data-stage-metric-compare-label]") || [];
        compareLabels.forEach((label) => {
            label.classList.toggle("d-none", !split);
        });
        syncComparePeriodSummary(
            refs.stageMetricCompareSummary,
            readStageMetricCompareMode(),
            normalizeCompareRangesByAfter(refs.stageMetricFromA?.value || refs.from?.value || "", refs.stageMetricToA?.value || refs.to?.value || "", "", ""),
            {includeAfter: split}
        );
        updateStageMetricQuickRangeAvailability();
    }

    function isStageMetricTextCompareEnabled() {
        return readStageTextCompareMode() !== "off";
    }

    function isStageMetricTextSplitCompare() {
        return readStageTextCompareMode() === "split";
    }

    function isStageMetricTextOverlayCompare() {
        return readStageTextCompareMode() === "overlay";
    }

    function updateStageMetricTextCompareUi() {
        const enabled = isStageMetricTextCompareEnabled();
        const split = isStageMetricTextSplitCompare();
        syncCompareModeSelectOptionLabels(refs.stageTextCompareMode, readStageTextCompareMode());
        refs.stageTextCompareControlsWrap?.classList.add("d-none");
        refs.stageMetricTextCompareCol?.classList.toggle("d-none", !split);
        const textGrid = document.getElementById("analytics-stage-metric-text-charts");
        textGrid?.classList.toggle("is-single", !split);
        refs.stageTextForm?.classList.toggle("is-compare-enabled", enabled);
        refs.stageTextForm?.classList.toggle("is-compare-disabled", !enabled);
        syncComparePeriodSummary(
            refs.stageTextCompareSummary,
            readStageTextCompareMode(),
            normalizeCompareRangesByAfter(refs.stageTextFromA?.value || refs.stageMetricFromA?.value || refs.from?.value || "", refs.stageTextToA?.value || refs.stageMetricToA?.value || refs.to?.value || "", "", ""),
            {includeAfter: split}
        );
    }

    function stageTextParams(rangeMode) {
        const params = new URLSearchParams();
        const mode = rangeMode === "compare" ? "compare" : "primary";
        const globalRanges = state.globalCompareEnabled ? resolveGlobalBeforeRange() : null;
        const localAfterFrom = refs.stageTextFromA?.value || refs.stageMetricFromA?.value || globalRanges?.afterFrom || refs.from?.value || "";
        const localAfterTo = refs.stageTextToA?.value || refs.stageMetricToA?.value || globalRanges?.afterTo || refs.to?.value || "";
        const ranges = normalizeCompareRangesByAfter(localAfterFrom, localAfterTo, "", "");
        const fromValue = mode === "compare" ? ranges.beforeFrom : ranges.afterFrom;
        const toValue = mode === "compare" ? ranges.beforeTo : ranges.afterTo;
        setIfPresent(params, "from", toIso((fromValue || "").trim()));
        setIfPresent(params, "to", toIso((toValue || "").trim()));
        const moduleCode = refs.moduleType?.value?.trim();
        if (moduleCode) {
            params.set("moduleCode", moduleCode);
        }
        const eventType = refs.eventType.value?.trim();
        if (eventType) {
            params.set("eventTypeCode", eventType);
        }
        const globalPath = refs.analyticsRequestPath?.value?.trim();
        if (globalPath) {
            params.set("requestPath", globalPath);
        }
        const bucket = refs.bucket.value?.trim();
        if (bucket) {
            params.set("bucketMinutes", bucket);
        }
        const stageType = refs.stageTextStageType?.value?.trim();
        if (stageType) {
            params.set("stageTypeCode", stageType);
        }
        return params;
    }

    function syncStageTextRangesFromMain(force) {
        const globalRanges = state.globalCompareEnabled ? resolveGlobalBeforeRange() : null;
        syncStageTextRangeField("fromA", refs.stageTextFromA, globalRanges?.afterFrom || refs.from?.value || "", force);
        syncStageTextRangeField("toA", refs.stageTextToA, globalRanges?.afterTo || refs.to?.value || "", force);
        updateStageMetricTextCompareUi();
    }

    function syncStageTextRangeField(key, input, targetValue, force) {
        if (!input) {
            return;
        }
        const current = input.value || "";
        const previous = state.stageTextRangeSynced[key] || "";
        if (force || !current || current === previous) {
            input.value = targetValue || "";
            state.stageTextRangeSynced[key] = input.value || "";
        }
    }

    function syncStageTextQuickRangeFromMain() {
        if (!refs.stageTextQuickRange || !refs.quickRangePresetSelect) {
            return;
        }
        syncQuickRangeSelectFromRange(
            refs.stageTextQuickRange,
            refs.from?.value || "",
            refs.to?.value || ""
        );
    }

    function syncStageTextQuickRangeFromInputs() {
        syncQuickRangeSelectFromRange(refs.stageTextQuickRange, refs.stageTextFromA?.value || "", refs.stageTextToA?.value || "");
    }

    async function applyStageTextQuickRange() {
        const preset = (refs.stageTextQuickRange?.value || "").trim();
        if (!preset) {
            return;
        }
        if (preset === "all") {
            await ensureAllTimeRangeLoaded();
            const allRange = getAllTimeLocalRange();
            if (refs.stageTextFromA) refs.stageTextFromA.value = allRange.from;
            if (refs.stageTextToA) refs.stageTextToA.value = allRange.to;
            updateStageMetricTextCompareUi();
            return;
        }
        const range = buildQuickRangeFromDate(new Date(), preset);
        if (!range) {
            if (refs.stageTextFromA) refs.stageTextFromA.value = "";
            if (refs.stageTextToA) refs.stageTextToA.value = "";
            updateStageMetricTextCompareUi();
            return;
        }
        if (refs.stageTextFromA) refs.stageTextFromA.value = toDateTimeLocalString(range.fromDate);
        if (refs.stageTextToA) refs.stageTextToA.value = toDateTimeLocalString(range.toDate);
        updateStageMetricTextCompareUi();
    }

    function syncStageMetricQuickRangeFromMain() {
        const select = refs.stageMetricQuickRange;
        if (!select) {
            return;
        }
        syncQuickRangeSelectFromRange(select, refs.from?.value || "", refs.to?.value || "");
        updateStageMetricQuickRangeAvailability();
    }

    function syncStageMetricQuickRangeFromInputs() {
        syncQuickRangeSelectFromRange(refs.stageMetricQuickRange, refs.stageMetricFromA?.value || "", refs.stageMetricToA?.value || "");
        updateStageMetricQuickRangeAvailability();
    }

    function buildQuickRangeFromDate(baseDate, presetCode) {
        const normalized = String(presetCode || "").trim().toLowerCase();
        const match = normalized.match(/^(\d+)(m|h|d|w|mo|y)$/);
        const toDate = baseDate instanceof Date ? new Date(baseDate.getTime()) : new Date(baseDate || "");
        if (!match || Number.isNaN(toDate.getTime())) {
            return null;
        }
        const count = Number(match[1]);
        const unitCode = match[2];
        if (!Number.isFinite(count) || count <= 0) {
            return null;
        }
        const fromDate = new Date(toDate.getTime());
        if (unitCode === "m") {
            fromDate.setMinutes(fromDate.getMinutes() - count);
        } else if (unitCode === "h") {
            fromDate.setHours(fromDate.getHours() - count);
        } else if (unitCode === "d") {
            fromDate.setDate(fromDate.getDate() - count);
        } else if (unitCode === "w") {
            fromDate.setDate(fromDate.getDate() - count * 7);
        } else if (unitCode === "mo") {
            fromDate.setMonth(fromDate.getMonth() - count);
        } else if (unitCode === "y") {
            fromDate.setFullYear(fromDate.getFullYear() - count);
        }
        return {fromDate, toDate};
    }

    function isWithinQuickRangeTolerance(actualMs, expectedMs) {
        return Math.abs(actualMs - expectedMs) <= QUICK_RANGE_MATCH_TOLERANCE_MS;
    }

    function ensureQuickRangeCustomOption(select) {
        if (!select || select.tagName !== "SELECT" || Array.from(select.options || []).some((option) => option.value === "")) {
            return;
        }
        const option = document.createElement("option");
        option.value = "";
        option.textContent = QUICK_RANGE_CUSTOM_LABEL;
        select.insertBefore(option, select.firstChild);
    }

    function inferQuickRangeCodeFromValues(fromValue, toValue) {
        const fromRaw = String(fromValue || "").trim();
        const toRaw = String(toValue || "").trim();
        if (!fromRaw && !toRaw) {
            return "all";
        }
        const fromDate = fromRaw ? new Date(fromRaw) : null;
        const toDate = toRaw ? new Date(toRaw) : null;
        if (!fromDate || !toDate || Number.isNaN(fromDate.getTime()) || Number.isNaN(toDate.getTime()) || fromDate >= toDate) {
            return "";
        }
        const allRange = getAllTimeLocalRange();
        const allFrom = allRange.from || ALL_TIME_START_LOCAL;
        const allFromDate = new Date(allFrom);
        const matchesAllFrom = fromRaw === allFrom
            || (!Number.isNaN(allFromDate.getTime()) && isWithinQuickRangeTolerance(fromDate.getTime(), allFromDate.getTime()));
        if (matchesAllFrom && toDate.getTime() <= Date.now() + QUICK_RANGE_MATCH_TOLERANCE_MS) {
            return "all";
        }
        for (const option of INLINE_COMPARE_PRESET_OPTIONS) {
            const code = option.value;
            if (!code || code === "all") {
                continue;
            }
            const expected = buildQuickRangeFromDate(toDate, code);
            if (!expected) {
                continue;
            }
            if (isWithinQuickRangeTolerance(fromDate.getTime(), expected.fromDate.getTime())) {
                return code;
            }
        }
        return "";
    }

    function syncQuickRangeSelectFromRange(select, fromValue, toValue) {
        if (!select) {
            return "";
        }
        ensureQuickRangeCustomOption(select);
        const code = inferQuickRangeCodeFromValues(fromValue, toValue);
        const hasOptions = select.tagName === "SELECT";
        if (code && (!hasOptions || Array.from(select.options || []).some((option) => option.value === code))) {
            select.value = code;
            return code;
        }
        select.value = "";
        return "";
    }

    function buildQuickRangeOptionsHtml(selectedValue = "") {
        const selected = String(selectedValue || "").trim().toLowerCase();
        return [
            `<option value="" ${selected ? "" : "selected"}>${QUICK_RANGE_CUSTOM_LABEL}</option>`,
            ...INLINE_COMPARE_PRESET_OPTIONS.map((item) => {
                const isSelected = item.value === selected;
                return `<option value="${escapeHtml(item.value)}" ${isSelected ? "selected" : ""}>${escapeHtml(item.label)}</option>`;
            })
        ].join("");
    }

    function syncStageMetricRangesFromMain(force) {
        const quickRange = refs.stageMetricQuickRange?.value?.trim() || "";
        const globalRanges = state.globalCompareEnabled ? resolveGlobalBeforeRange() : null;
        let targetAFrom = globalRanges?.afterFrom || refs.from?.value || "";
        let targetATo = globalRanges?.afterTo || refs.to?.value || "";
        // When syncing from the global sidebar (force=true), always mirror the global period.
        // Local quick range must apply only on explicit local change in the stage-metrics block.
        if (!force && quickRange && quickRange !== "all") {
            const range = buildQuickRangeFromDate(new Date(), quickRange);
            if (range) {
                targetAFrom = toDateTimeLocalString(range.fromDate);
                targetATo = toDateTimeLocalString(range.toDate);
            }
        }
        syncStageMetricRangeField(refs.stageMetricFromA, "fromA", targetAFrom, force);
        syncStageMetricRangeField(refs.stageMetricToA, "toA", targetATo, force);
        updateStageMetricCompareUi();
    }

    async function applyStageMetricQuickRange() {
        const quickRange = refs.stageMetricQuickRange?.value?.trim() || "";
        if (quickRange === "all") {
            await ensureAllTimeRangeLoaded();
            const allRange = getAllTimeLocalRange();
            if (refs.stageMetricFromA) refs.stageMetricFromA.value = allRange.from;
            if (refs.stageMetricToA) refs.stageMetricToA.value = allRange.to;
            updateStageMetricCompareUi();
            return;
        }
        const range = buildQuickRangeFromDate(new Date(), quickRange);
        if (!range) {
            return;
        }
        if (refs.stageMetricFromA) refs.stageMetricFromA.value = toDateTimeLocalString(range.fromDate);
        if (refs.stageMetricToA) refs.stageMetricToA.value = toDateTimeLocalString(range.toDate);
        updateStageMetricCompareUi();
    }

    function parseQuickRangeMs(value) {
        const raw = String(value || "").trim().toLowerCase();
        const match = raw.match(/^(\d+)(m|h|d|w|mo|y)$/);
        if (!match) {
            return 0;
        }
        const count = Number(match[1]);
        const unit = match[2];
        if (!Number.isFinite(count) || count <= 0) {
            return 0;
        }
        if (unit === "m") {
            return count * 60_000;
        }
        if (unit === "h") {
            return count * 3_600_000;
        }
        if (unit === "d") {
            return count * 86_400_000;
        }
        if (unit === "w") {
            return count * 7 * 86_400_000;
        }
        if (unit === "mo") {
            return count * 30 * 86_400_000;
        }
        if (unit === "y") {
            return count * 365 * 86_400_000;
        }
        return 0;
    }

    function quickRangeCodeToDurationMs(value) {
        return parseQuickRangeMs(value);
    }

    function resolveSafeAfterRangeFromTop() {
        const afterFromRaw = (refs.from?.value || "").trim();
        const afterToRaw = (refs.to?.value || "").trim();
        const afterFromDate = afterFromRaw ? new Date(afterFromRaw) : null;
        const afterToDate = afterToRaw ? new Date(afterToRaw) : null;
        if (afterFromDate
            && afterToDate
            && !Number.isNaN(afterFromDate.getTime())
            && !Number.isNaN(afterToDate.getTime())
            && afterFromDate.getTime() < afterToDate.getTime()) {
            return {
                afterFrom: toDateTimeLocalString(afterFromDate),
                afterTo: toDateTimeLocalString(afterToDate)
            };
        }
        const now = new Date();
        const hourAgo = new Date(now.getTime() - 60 * 60 * 1000);
        return {
            afterFrom: toDateTimeLocalString(hourAgo),
            afterTo: toDateTimeLocalString(now)
        };
    }

    function buildContiguousBeforeRange(afterFromDate, afterToDate) {
        const durationMs = Math.max(60_000, afterToDate.getTime() - afterFromDate.getTime());
        const beforeToDate = new Date(afterFromDate.getTime());
        const beforeFromDate = new Date(beforeToDate.getTime() - durationMs);
        return {
            beforeFrom: toDateTimeLocalString(beforeFromDate),
            beforeTo: toDateTimeLocalString(beforeToDate)
        };
    }

    function isAllTimeMirrorCompareRange(beforeFromRaw, beforeToRaw, afterFromRaw, afterToRaw) {
        const allFrom = getAllTimeLocalRange().from;
        return !!afterFromRaw
            && !!afterToRaw
            && beforeFromRaw === afterFromRaw
            && beforeToRaw === afterToRaw
            && afterFromRaw === allFrom;
    }

    function normalizeCompareRangesByAfter(afterFromRaw, afterToRaw, beforeFromRaw, beforeToRaw) {
        const safeAfter = {
            afterFrom: String(afterFromRaw || "").trim(),
            afterTo: String(afterToRaw || "").trim()
        };
        const afterFromDate = safeAfter.afterFrom ? new Date(safeAfter.afterFrom) : null;
        const afterToDate = safeAfter.afterTo ? new Date(safeAfter.afterTo) : null;
        const hasAfter = afterFromDate
            && afterToDate
            && !Number.isNaN(afterFromDate.getTime())
            && !Number.isNaN(afterToDate.getTime())
            && afterFromDate.getTime() < afterToDate.getTime();
        if (!hasAfter) {
            const fallback = resolveSafeAfterRangeFromTop();
            const fallbackFromDate = new Date(fallback.afterFrom);
            const fallbackToDate = new Date(fallback.afterTo);
            const contiguousFallback = buildContiguousBeforeRange(fallbackFromDate, fallbackToDate);
            return {
                beforeFrom: contiguousFallback.beforeFrom,
                beforeTo: contiguousFallback.beforeTo,
                afterFrom: fallback.afterFrom,
                afterTo: fallback.afterTo
            };
        }

        const contiguous = buildContiguousBeforeRange(afterFromDate, afterToDate);
        return {
            beforeFrom: contiguous.beforeFrom,
            beforeTo: contiguous.beforeTo,
            afterFrom: safeAfter.afterFrom,
            afterTo: safeAfter.afterTo
        };
    }

    function buildScopedParamsByLocalRange(fromLocal, toLocal, includeEventType = true) {
        const params = mainParams(includeEventType);
        const fromIso = toIso(fromLocal);
        const toIsoValue = toIso(toLocal);
        if (fromIso) {
            params.set("from", fromIso);
        } else {
            params.delete("from");
        }
        if (toIsoValue) {
            params.set("to", toIsoValue);
        } else {
            params.delete("to");
        }
        return params;
    }

    function serializeCompareRangeKey(ranges) {
        return [
            ranges?.beforeFrom || "",
            ranges?.beforeTo || "",
            ranges?.afterFrom || "",
            ranges?.afterTo || ""
        ].join("|");
    }

    function resolveInlineCompareRequestRanges(canvasId) {
        const hasLocalOverride = !!state.inlineComparePresetOverriddenBySource[canvasId];
        const stored = state.expandedRangesBySource[canvasId];
        if (stored && stored.afterFrom && stored.afterTo) {
            const normalizedStored = normalizeCompareRangesByAfter(stored.afterFrom, stored.afterTo, stored.beforeFrom, stored.beforeTo);
            state.expandedRangesBySource[canvasId] = {...normalizedStored};
            return normalizedStored;
        }
        if (state.globalCompareEnabled && !hasLocalOverride) {
            return resolveGlobalBeforeRange();
        }
        if (state.globalCompareEnabled) {
            return resolveGlobalBeforeRange();
        }
        const topRanges = expandedRangesFromTopFilter(canvasId);
        return normalizeCompareRangesByAfter(topRanges.afterFrom, topRanges.afterTo, topRanges.beforeFrom, topRanges.beforeTo);
    }

    function resolveMiniKpiCompareRequestRanges() {
        const safeAfter = resolveSafeAfterRangeFromTop();
        return normalizeCompareRangesByAfter(safeAfter.afterFrom, safeAfter.afterTo, "", "");
    }

    function updateStageMetricQuickRangeAvailability() {
        const select = refs.stageMetricQuickRange;
        if (!select) {
            return;
        }
        const allOption = Array.from(select.options).find((option) => option.value === "all");
        if (!allOption) {
            return;
        }
        allOption.disabled = false;
    }

    function buildStageMetricQuickRangeWindows(anchorMs, durationMs, compareEnabled) {
        const afterToMs = anchorMs;
        const afterFromMs = afterToMs - durationMs;
        if (!compareEnabled) {
            return {
                aFromMs: afterFromMs,
                aToMs: afterToMs,
                bFromMs: afterFromMs,
                bToMs: afterToMs
            };
        }
        const beforeToMs = afterFromMs;
        const beforeFromMs = beforeToMs - durationMs;
        return {
            aFromMs: beforeFromMs,
            aToMs: beforeToMs,
            bFromMs: afterFromMs,
            bToMs: afterToMs
        };
    }

    function syncStageMetricRangeField(input, key, targetValue, force) {
        if (!input) {
            return;
        }
        const current = input.value || "";
        const prevSynced = state.stageMetricRangeSynced?.[key] || "";
        if (force || !current || current === prevSynced) {
            input.value = targetValue || "";
            state.stageMetricRangeSynced[key] = targetValue || "";
        }
    }

    function openMetricHelpModal(metricCode) {
        if (!refs.helpModalEl || !refs.helpModalTitle || !refs.helpModalBody) {
            return;
        }
        const info = state.metricHelpByCode?.[metricCode];
        if (!info) {
            return;
        }
        const title = String(info.name || metricCode).trim() || metricCode;
        refs.helpModalTitle.textContent = title;
        refs.helpModalBody.innerHTML = buildMetricHelpHtml(metricCode, title, info.description, info.guide);
        const modal = bootstrap.Modal.getOrCreateInstance(refs.helpModalEl);
        modal.show();
    }

    async function loadEvents(reset) {
        if (reset) {
            state.eventsPage = 0;
        }
        const params = rangeParams();
        const eventsFromIso = toIso(refs.eventsFrom?.value);
        const eventsToIso = toIso(refs.eventsTo?.value);
        if (eventsFromIso) {
            params.set("from", eventsFromIso);
        }
        if (eventsToIso) {
            params.set("to", eventsToIso);
        }
        params.set("page", String(state.eventsPage));
        params.set("size", String(state.eventsSize));

        const rawEventType = refs.eventsEventType?.value?.trim();
        const mainEventType = refs.eventType?.value?.trim();
        if (rawEventType) {
            params.set("eventTypeCode", rawEventType);
        } else if (mainEventType) {
            params.set("eventTypeCode", mainEventType);
        }

        if (refs.eventsIsError.value !== "") {
            params.set("isError", refs.eventsIsError.value);
        }
        if (refs.eventsErrorClass?.value?.trim()) {
            params.set("errorClass", refs.eventsErrorClass.value.trim());
        }
        if (refs.eventsMinDuration.value?.trim()) {
            const minDuration = Number(refs.eventsMinDuration.value.trim());
            if (Number.isFinite(minDuration) && minDuration > 0) {
                params.set("minDurationMs", String(Math.floor(minDuration)));
            }
        }
        if (refs.eventsRequestPath?.value?.trim()) {
            params.set("requestPath", refs.eventsRequestPath.value.trim());
        }
        if (refs.eventsAttributeCode.value?.trim()) {
            params.set("attributeCode", refs.eventsAttributeCode.value.trim());
        }
        if (refs.eventsAttributeValue.value?.trim()) {
            params.set("attributeValue", refs.eventsAttributeValue.value.trim());
        }
        if (refs.eventsMetricType.value?.trim()) {
            params.set("metricTypeCode", refs.eventsMetricType.value.trim());
        }
        if (refs.eventsMetricMin.value?.trim()) {
            const metricMin = Number(refs.eventsMetricMin.value.trim());
            if (Number.isFinite(metricMin)) {
                params.set("metricMinValue", String(metricMin));
            }
        }
        if (refs.eventsMetricMax.value?.trim()) {
            const metricMax = Number(refs.eventsMetricMax.value.trim());
            if (Number.isFinite(metricMax)) {
                params.set("metricMaxValue", String(metricMax));
            }
        }
        if (refs.eventsSortBy.value?.trim()) {
            params.set("sortBy", refs.eventsSortBy.value.trim());
        }
        if (refs.eventsSortDir.value?.trim()) {
            params.set("sortDir", refs.eventsSortDir.value.trim());
        }

        try {
            const data = await fetchJson(`${api("/events")}?${params.toString()}`);
            state.eventsHasMore = !!data.hasMore;
            refs.eventsLoadMore.classList.toggle("d-none", !state.eventsHasMore);

            const rowsHtml = (data.items || []).map((item) => {
                const attrText = compactAttributes(item.attributes || {});
                const statusClass = item.isError ? "analytics-badge-error" : "analytics-badge-success";
                const statusText = item.isError ? "Ошибка" : "OK";
                const errorClass = String(item.errorClass || "NONE").toUpperCase();
                const errorClassLabel = errorClass === "NONE" ? "—" : errorClass;
                return `
                    <tr>
                        <td class="small text-muted">${formatDateTime(item.startedAt)}</td>
                        <td>
                            <div class="fw-semibold">${escapeHtml(item.eventTypeName || item.eventTypeCode || "-")}</div>
                            <div class="small text-muted">Модуль: ${escapeHtml(item.moduleName || item.moduleCode || "-")}</div>
                            <div class="small text-muted">${attrText}</div>
                        </td>
                        <td class="text-end fw-semibold">${formatInt(item.durationMs || 0)}</td>
                        <td class="text-end">${item.statusCode == null ? "-" : formatInt(item.statusCode)}</td>
                        <td class="small">${escapeHtml(item.requestPath || "-")}</td>
                        <td class="small text-muted">${escapeHtml(item.traceId || "-")}</td>
                        <td><span class="analytics-status-badge ${errorClassBadgeClass(errorClass)}">${escapeHtml(errorClassLabel)}</span></td>
                        <td><span class="analytics-status-badge ${statusClass}">${statusText}</span></td>
                        <td class="text-end">
                            <button class="btn btn-sm btn-outline-dark" type="button" data-event-uid="${item.eventUid || ""}" data-event-id="${item.eventId || ""}">Детали</button>
                        </td>
                    </tr>
                `;
            }).join("");

            if (reset) {
                refs.eventsTableBody.innerHTML = rowsHtml || eventsInfoRow("По выбранным фильтрам событий не найдено.", false);
            } else if (rowsHtml) {
                refs.eventsTableBody.insertAdjacentHTML("beforeend", rowsHtml);
            }
        } catch (error) {
            console.error("Events load failed", error);
            state.eventsHasMore = false;
            refs.eventsLoadMore.classList.add("d-none");
            const detail = error instanceof Error ? error.message : "неизвестная ошибка";
            refs.eventsTableBody.innerHTML = eventsInfoRow(`Не удалось загрузить события: ${detail}`, true);
        }
    }

    async function openEventDetails(eventUid) {
        const data = await fetchJson(`${api("/events")}/${encodeURIComponent(eventUid)}`);
        renderEventDetailsModal(data);
    }

    async function openEventDetailsById(eventId) {
        const data = await fetchJson(`${api("/events/by-id")}/${encodeURIComponent(eventId)}`);
        renderEventDetailsModal(data);
    }

    function renderEventDetailsModal(data) {
        const stages = buildLinearTimelineStages(data, data.stages || []);
        const traceLogs = data.traceLogs || [];
        const traceLogStatus = data.traceLogStatus || {};
        const uidSafe = String(data.eventUid || "event").replace(/[^a-zA-Z0-9_-]/g, "");
        const displayDurationMs = resolveEventDurationForDisplay(data, stages);

        const attrsHtml = (data.attributes || []).map((attr) => {
            const code = String(attr.attributeTypeCode || "").trim().toUpperCase();
            const hint = SYSTEM_ATTRIBUTE_HELP[code] || "";
            const helpBadge = hint
                ? `<span class="analytics-metric-help-badge" data-tooltip="${escapeHtml(hint)}" tabindex="0">?</span>`
                : "";
            return `
                <tr>
                    <td>
                        <span class="analytics-metric-name-cell">
                            ${escapeHtml(attr.attributeTypeName || attr.attributeTypeCode)}
                            ${helpBadge}
                        </span>
                    </td>
                    <td>${escapeHtml(attr.value || attr.valueJson || "-")}</td>
                </tr>
            `;
        }).join("");

        const stageSummaryMap = new Map();
        for (const stage of stages) {
            const key = stage.stageTypeCode || "UNKNOWN";
            const name = stage.stageTypeName || stage.stageTypeCode || "Неизвестно";
            const duration = Number(stage.durationMs || 0);
            const current = stageSummaryMap.get(key) || {
                key,
                name,
                count: 0,
                totalDuration: 0,
                maxDuration: 0,
                errorCount: 0
            };
            current.count += 1;
            current.totalDuration += duration;
            if (duration > current.maxDuration) {
                current.maxDuration = duration;
            }
            if (stage.isError) {
                current.errorCount += 1;
            }
            stageSummaryMap.set(key, current);
        }

        const stageSummaryHtml = Array.from(stageSummaryMap.values())
            .sort((a, b) => b.totalDuration - a.totalDuration)
            .map((item) => `
                <tr>
                    <td>${escapeHtml(item.name)}</td>
                    <td class="text-end">${formatInt(item.count)}</td>
                    <td class="text-end">${formatInt(item.totalDuration)} ms</td>
                    <td class="text-end">${formatInt(item.maxDuration)} ms</td>
                    <td class="text-end ${item.errorCount > 0 ? "text-danger" : "text-muted"}">${formatInt(item.errorCount)}</td>
                </tr>
            `).join("");

        const stageGroups = buildStageGroups(stages);
        const stagesHtml = stageGroups.map((group, groupIndex) => {
            if (!group || !group.items || group.items.length === 0) {
                return "";
            }
            if (group.items.length === 1) {
                const single = group.items[0];
                return renderStageCard(single.stage, single.index, uidSafe, false);
            }
            return renderStageGroupCard(group, groupIndex, uidSafe);
        }).join("");

        const hasErrorMessage = Boolean(data.isError || (data.errorMessage && data.errorMessage.trim() && data.errorMessage.trim() !== "Нет ошибок"));
        const detailsGridClass = hasErrorMessage
            ? "analytics-grid analytics-event-details-grid"
            : "analytics-grid analytics-event-details-grid is-single";
        const overviewTabId = `analytics-event-overview-${uidSafe}`;
        const logsTabId = `analytics-event-logs-${uidSafe}`;
        const errorSectionHtml = hasErrorMessage ? `
                <section class="glass-card p-3">
                    <div class="fw-semibold mb-2">Ошибка</div>
                    <div class="small ${data.isError ? "text-danger" : "text-muted"}">
                        ${escapeHtml(data.errorMessage || "Ошибка без текста")}
                    </div>
                </section>
            ` : "";

        refs.eventModalBody.innerHTML = `
            <div class="analytics-event-header mb-3">
                <div class="small text-muted">${formatDateTime(data.startedAt)} - ${formatDateTime(data.endedAt)}</div>
                <div class="h6 mb-1">${escapeHtml(data.eventTypeName || data.eventTypeCode || "-")}</div>
                <div class="small text-muted">Модуль: ${escapeHtml(data.moduleName || data.moduleCode || "-")}</div>
                <div class="small text-muted">UID: ${escapeHtml(data.eventUid)}</div>
                <div class="small text-muted">Path: ${escapeHtml(data.requestPath || "-")} · Trace: ${escapeHtml(data.traceId || "-")}</div>
                <div class="small text-muted">HTTP ${data.statusCode == null ? "-" : escapeHtml(String(data.statusCode))} · ${formatInt(displayDurationMs)} ms</div>
                <div class="small text-muted">Класс ошибки: ${escapeHtml(data.errorClass || "NONE")}</div>
            </div>
            <ul class="nav nav-tabs analytics-event-tabs" role="tablist">
                <li class="nav-item" role="presentation">
                    <button class="nav-link active" data-bs-toggle="tab" data-bs-target="#${overviewTabId}" type="button" role="tab">Обзор</button>
                </li>
                <li class="nav-item" role="presentation">
                    <button class="nav-link" data-bs-toggle="tab" data-bs-target="#${logsTabId}" type="button" role="tab">Логи (trace)</button>
                </li>
            </ul>
            <div class="tab-content pt-3">
                <div class="tab-pane fade show active" id="${overviewTabId}" role="tabpanel">
                    <div class="${detailsGridClass}">
                        <section class="glass-card p-3">
                            <div class="fw-semibold mb-2">Атрибуты</div>
                            <div class="table-responsive">
                                <table class="table table-sm align-middle analytics-table analytics-event-attrs-table mb-0">
                                    <thead>
                                    <tr><th>Код</th><th>Значение</th></tr>
                                    </thead>
                                    <tbody>${attrsHtml || "<tr><td colspan='2' class='text-muted'>Нет данных</td></tr>"}</tbody>
                                </table>
                            </div>
                        </section>
                        ${errorSectionHtml}
                    </div>
                    <section class="mt-3">
                        <div class="fw-semibold mb-2">Этапы по вызову</div>
                        <div class="table-responsive mb-3">
                            <table class="table table-sm align-middle analytics-table mb-0">
                                <thead>
                                <tr>
                                    <th>Этап</th>
                                    <th class="text-end">Метрика</th>
                                    <th class="text-end">Σ длительность</th>
                                    <th class="text-end">Max</th>
                                    <th class="text-end">Ошибки</th>
                                </tr>
                                </thead>
                                <tbody>${stageSummaryHtml || "<tr><td colspan='5' class='text-muted'>Нет данных</td></tr>"}</tbody>
                            </table>
                        </div>
                        <div class="fw-semibold mb-1">Сгруппированные вызовы</div>
                        <div class="small text-muted mb-2">
                            Это агрегированные последовательные вызовы: один источник, одна операция, backend-путь и один класс ошибки.
                        </div>
                        ${stagesHtml || "<div class='text-muted'>Этапы не найдены</div>"}
                    </section>
                </div>
                <div class="tab-pane fade" id="${logsTabId}" role="tabpanel">
                    <div class="small text-muted mb-2">
                        Нормализованные логи по Trace: <b>${escapeHtml(data.traceId || "-")}</b>. Логи полезны для детального разбора ошибки на уровне этапов.
                    </div>
                    ${renderTraceLogStatus(traceLogStatus)}
                    ${renderNormalizedLogsTable(traceLogs, "По этому trace логи не найдены.", true)}
                </div>
            </div>
        `;

        const modal = bootstrap.Modal.getOrCreateInstance(refs.eventModalEl);
        modal.show();
    }

    function renderTraceLogStatus(status) {
        const code = String(status?.status || "").trim().toUpperCase();
        if (!code || code === "CURRENT_FOUND") {
            return "";
        }
        const fileName = status?.fileName || "";
        const moduleCode = status?.moduleCode || "";
        const lineCount = Number(status?.lineCount || 0);
        const warnCount = Number(status?.warnCount || 0);
        const errorCount = Number(status?.errorCount || 0);
        const summary = status?.summary || "";
        const message = status?.message || "";
        const badgeClass = code === "ARCHIVE_AVAILABLE"
            ? "text-bg-info"
            : (code === "ARCHIVE_INDEX_ONLY" ? "text-bg-warning" : "text-bg-secondary");
        const title = code === "ARCHIVE_AVAILABLE"
            ? "Логи найдены в архиве"
            : (code === "ARCHIVE_INDEX_ONLY" ? "Доступна только сводка индекса" : "Логи не найдены");
        const excerpts = Array.isArray(status?.excerpts) ? status.excerpts : [];
        const excerptsHtml = excerpts.length ? `
            <div class="mt-2">
                <div class="fw-semibold small mb-1">Сохранённые важные фрагменты</div>
                <div class="analytics-log-excerpt-list">
                    ${excerpts.map((item) => `
                        <div class="analytics-log-excerpt-item">
                            <div class="small text-muted">${escapeHtml(formatDateTime(item.timestamp))} · ${escapeHtml(item.level || "-")} · ${escapeHtml(item.source || "-")} · line ${escapeHtml(item.lineNumber || "-")}</div>
                            <div class="small">${escapeHtml(item.messageShort || item.excerpt || "-")}</div>
                        </div>
                    `).join("")}
                </div>
            </div>
        ` : "";
        const reasons = code === "NOT_FOUND" ? `
            <ul class="small mb-0 mt-2">
                <li>проверьте, что trace-логирование включено;</li>
                <li>проверьте retention текущих логов и архивов;</li>
                <li>запустите индексацию логов в конфигурации аналитики, если архив появился недавно.</li>
            </ul>
        ` : "";
        return `
            <div class="alert alert-light border analytics-trace-log-status mb-3">
                <div class="d-flex align-items-center justify-content-between gap-2">
                    <div class="fw-semibold">${escapeHtml(title)}</div>
                    <span class="badge ${badgeClass}">${escapeHtml(code)}</span>
                </div>
                <div class="small text-muted mt-1">${escapeHtml(message)}</div>
                ${fileName ? `
                    <div class="small mt-2">
                        <b>Файл:</b> ${escapeHtml(fileName)}
                        ${moduleCode ? ` · <b>Модуль:</b> ${escapeHtml(moduleCode)}` : ""}
                        ${lineCount ? ` · <b>Строк trace:</b> ${escapeHtml(formatInt(lineCount))}` : ""}
                        ${warnCount ? ` · <b>WARN:</b> ${escapeHtml(formatInt(warnCount))}` : ""}
                        ${errorCount ? ` · <b>ERROR:</b> ${escapeHtml(formatInt(errorCount))}` : ""}
                    </div>
                ` : ""}
                ${summary ? `<div class="small mt-1"><b>Сводка:</b> ${escapeHtml(summary)}</div>` : ""}
                ${excerptsHtml}
                ${reasons}
            </div>
        `;
    }

    function buildStageGroups(stages) {
        const groups = [];
        const indexByKey = new Map();
        for (let i = 0; i < (stages || []).length; i++) {
            const stage = stages[i];
            if (!stage) {
                continue;
            }
            const key = stageGroupingKey(stage);
            let groupIndex = indexByKey.get(key);
            if (groupIndex == null) {
                groupIndex = groups.length;
                indexByKey.set(key, groupIndex);
                groups.push({
                    key,
                    stageTypeCode: stage.stageTypeCode || "UNKNOWN",
                    stageTypeName: stage.stageTypeName || stage.stageTypeCode || "Неизвестно",
                    source: stagePrimarySource(stage),
                    operation: stagePrimaryOperation(stage),
                    isError: Boolean(stage.isError),
                    items: []
                });
            }
            groups[groupIndex].items.push({stage, index: i});
        }
        return groups;
    }

    function stageGroupingKey(stage) {
        const typeCode = normalizeStageCode(stage?.stageTypeCode);
        const source = normalizeTextForGrouping(stagePrimarySource(stage));
        const operation = normalizeTextForGrouping(stagePrimaryOperation(stage));
        const errorPart = Boolean(stage?.isError) ? "ERR" : "OK";
        return `${typeCode}|${source}|${operation}|${errorPart}`;
    }

    function normalizeTextForGrouping(value) {
        if (value == null) {
            return "";
        }
        return String(value).trim().toLowerCase();
    }

    function stagePrimarySource(stage) {
        const logs = stage?.logs || [];
        for (const entry of logs) {
            const source = entry?.source == null ? "" : String(entry.source).trim();
            if (source && source !== "-") {
                return source;
            }
        }
        return stage?.stageTypeName || stage?.stageTypeCode || "—";
    }

    function stagePrimaryOperation(stage) {
        const logs = stage?.logs || [];
        for (const entry of logs) {
            const operation = entry?.operation == null ? "" : String(entry.operation).trim();
            if (operation && operation !== "-") {
                return operation;
            }
        }
        return "—";
    }

    function renderStageGroupCard(group, groupIndex, uidSafe) {
        const items = group?.items || [];
        if (items.length === 0) {
            return "";
        }
        const collapseId = `analytics-stage-group-${uidSafe}-${groupIndex}`;
        const firstStage = items[0].stage;
        const lastStage = items[items.length - 1].stage;
        const totalDuration = items.reduce((acc, item) => acc + Number(item.stage?.durationMs || 0), 0);
        const maxDuration = items.reduce((acc, item) => Math.max(acc, Number(item.stage?.durationMs || 0)), 0);
        const avgDuration = items.length > 0 ? Math.round(totalDuration / items.length) : 0;
        const hasErrors = items.some((item) => Boolean(item.stage?.isError));
        const nestedStagesHtml = items.map((item) => renderStageCard(item.stage, item.index, `${uidSafe}-grp${groupIndex}`, true)).join("");
        return `
            <article class="analytics-event-stage mb-3">
                <div class="d-flex justify-content-between gap-2 align-items-start">
                    <div>
                        <div class="fw-semibold">${escapeHtml(group.stageTypeName)} · ${escapeHtml(group.source || "—")}</div>
                        <div class="small text-muted">Вызовы: ${formatInt(items.length)} · ${escapeHtml(group.operation || "—")}</div>
                        <div class="small text-muted">${formatDateTime(firstStage?.startedAt)} - ${formatDateTime(lastStage?.endedAt)}</div>
                    </div>
                    <div class="text-end">
                        <div class="fw-semibold">ОЈ ${formatInt(totalDuration)} ms</div>
                        <div class="small text-muted">avg ${formatInt(avgDuration)} · max ${formatInt(maxDuration)}</div>
                        <span class="analytics-status-badge ${hasErrors ? "analytics-badge-error" : "analytics-badge-success"}">
                            ${hasErrors ? "Есть ошибки" : "OK"}
                        </span>
                    </div>
                </div>
                <div class="mt-2">
                    <button class="btn btn-sm btn-outline-secondary analytics-stage-log-toggle" type="button"
                            data-bs-toggle="collapse" data-bs-target="#${collapseId}"
                            aria-expanded="false" aria-controls="${collapseId}">
                        Показать лог (${formatInt(items.length)})
                    </button>
                    <div class="collapse mt-2" id="${collapseId}">
                        ${nestedStagesHtml}
                    </div>
                </div>
            </article>
        `;
    }

    function renderStageCard(stage, index, uidSafe, nested) {
        const stageLogs = stage?.logs || [];
        const collapseId = `analytics-stage-log-${uidSafe}-${Number(stage?.stageOrder || 0)}-${index}`;
        const stageLogSectionHtml = stageLogs.length > 0
            ? `
                <div class="mt-2">
                    <button class="btn btn-sm btn-outline-secondary analytics-stage-log-toggle" type="button"
                            data-bs-toggle="collapse" data-bs-target="#${collapseId}"
                            aria-expanded="false" aria-controls="${collapseId}">
                        Лог (${formatInt(stageLogs.length)})
                    </button>
                    <div class="collapse mt-2" id="${collapseId}">
                        <div class="analytics-stage-log-panel">
                            ${renderNormalizedLogsTable(stageLogs, "Для этого вызова логов не найдено.", true)}
                        </div>
                    </div>
                </div>
            `
            : "";
        const nestedClass = nested ? "ms-2 mt-2" : "";
        return `
            <article class="analytics-event-stage mb-3 ${nestedClass}">
                <div class="d-flex justify-content-between gap-2 align-items-start">
                    <div>
                        <div class="fw-semibold">${escapeHtml(stage?.stageTypeName || stage?.stageTypeCode)}</div>
                        <div class="small text-muted">#${formatInt(stage?.stageOrder || 0)} · ${formatDateTime(stage?.startedAt)} - ${formatDateTime(stage?.endedAt)}</div>
                    </div>
                    <div class="text-end">
                        <div class="fw-semibold">${formatInt(stage?.durationMs || 0)} ms</div>
                        <span class="analytics-status-badge ${stage?.isError ? "analytics-badge-error" : "analytics-badge-success"}">
                            ${stage?.isError ? "Ошибка" : "OK"}
                        </span>
                    </div>
                </div>
                <div class="table-responsive mt-2">
                    <table class="table table-sm align-middle analytics-table mb-0">
                        <thead>
                        <tr>
                            <th>Метрика</th>
                            <th class="text-end">Значение</th>
                            <th>Unit</th>
                        </tr>
                        </thead>
                        <tbody>
                        ${(stage?.metrics || []).map((metric) => `
                            <tr>
                                <td>${escapeHtml(localizeMetricDisplayName(metric.metricTypeCode, metric.metricTypeName || metric.metricTypeCode))}</td>
                                <td class="text-end">${metric.metricValueNum != null ? escapeHtml(String(metric.metricValueNum)) : escapeHtml(metric.metricValueText || "-")}</td>
                                <td>${escapeHtml(metric.unit || "-")}</td>
                            </tr>
                        `).join("")}
                        </tbody>
                    </table>
                </div>
                ${stageLogSectionHtml}
            </article>
        `;
    }

    function resolveEventDurationForDisplay(eventData, stages) {
        const byStages = durationByStages(stages);
        if (byStages != null) {
            return byStages;
        }
        const raw = Number(eventData?.durationMs || 0);
        if (!Number.isFinite(raw) || raw < 0) {
            return 0;
        }
        return Math.round(raw);
    }

    function durationByStages(stages) {
        if (!Array.isArray(stages) || stages.length === 0) {
            return null;
        }
        let minStartedAt = null;
        let maxEndedAt = null;
        for (const stage of stages) {
            const startedAtMs = toEpochMs(stage?.startedAt);
            const endedAtMs = toEpochMs(stage?.endedAt);
            if (startedAtMs != null) {
                minStartedAt = minStartedAt == null ? startedAtMs : Math.min(minStartedAt, startedAtMs);
            }
            if (endedAtMs != null) {
                maxEndedAt = maxEndedAt == null ? endedAtMs : Math.max(maxEndedAt, endedAtMs);
            }
        }
        if (minStartedAt == null || maxEndedAt == null || maxEndedAt < minStartedAt) {
            return null;
        }
        return Math.max(0, Math.round(maxEndedAt - minStartedAt));
    }

    function buildLinearTimelineStages(eventData, rawStages) {
        if (!Array.isArray(rawStages) || rawStages.length === 0) {
            return [];
        }
        const sorted = rawStages.slice().sort(compareStageTimelineOrder);
        const frontendStage = pickFrontendStage(sorted);
        if (!frontendStage) {
            return sorted;
        }

        const ttfbMs = readStageMetricNum(frontendStage, "FRONTEND_TTFB_MS");
        const domLoadedMs = readStageMetricNum(frontendStage, "FRONTEND_DOM_CONTENT_LOADED_MS");
        const navType = readStageMetricText(frontendStage, "FRONTEND_NAV_TYPE");
        if (ttfbMs == null && domLoadedMs == null && !navType) {
            return sorted;
        }

        const backendStages = sorted.filter((stage) => normalizeStageCode(stage.stageTypeCode) !== "FRONTEND");
        let frontendStartMs = toEpochMs(eventData?.startedAt);

        const frontendLogEndMs = toEpochMs(frontendStage.logEndedAt) ?? toEpochMs(frontendStage.endedAt);
        if (frontendStartMs == null && ttfbMs != null && frontendLogEndMs != null) {
            frontendStartMs = frontendLogEndMs - ttfbMs;
        }
        if (frontendStartMs == null) {
            const firstKnownStageMs = minEpochMs(sorted.map((stage) => toEpochMs(stage.startedAt)));
            if (firstKnownStageMs != null && ttfbMs != null) {
                frontendStartMs = firstKnownStageMs - ttfbMs;
            } else {
                frontendStartMs = firstKnownStageMs;
            }
        }
        if (frontendStartMs == null) {
            return sorted;
        }

        let serverWaitMs = ttfbMs;
        if (serverWaitMs == null) {
            const firstBackendMs = minEpochMs(backendStages.map((stage) => toEpochMs(stage.startedAt)));
            if (firstBackendMs != null) {
                serverWaitMs = Math.max(0, firstBackendMs - frontendStartMs);
            }
        }
        serverWaitMs = clampDuration(serverWaitMs);

        let frontendRenderMs = null;
        if (domLoadedMs != null && serverWaitMs != null) {
            frontendRenderMs = Math.max(0, domLoadedMs - serverWaitMs);
        }
        frontendRenderMs = clampDuration(frontendRenderMs);

        const serverWaitStartMs = frontendStartMs;
        const serverWaitEndMs = frontendStartMs + (serverWaitMs || 0);
        const frontendRenderEndMs = serverWaitEndMs + (frontendRenderMs || 0);

        const syntheticStartStage = {
            stageTypeCode: "FRONTEND_START",
            stageTypeName: "Фронтенд: старт навигации",
            stageOrder: -1000,
            startedAt: toIsoTime(frontendStartMs),
            endedAt: toIsoTime(frontendStartMs),
            logStartedAt: null,
            logEndedAt: null,
            durationMs: 0,
            isError: false,
            errorMessage: null,
            metrics: [],
            logs: []
        };

        const serverWaitMetrics = [];
        if (serverWaitMs != null) {
            serverWaitMetrics.push(makeMetricNum("FRONTEND_TTFB_MS", "TTFB (frontend)", serverWaitMs, "ms"));
        }
        const pageUrl = readStageMetricText(frontendStage, "FRONTEND_PAGE_URL");
        if (pageUrl) {
            serverWaitMetrics.push(makeMetricText("FRONTEND_PAGE_URL", "Page URL", pageUrl));
        }
        if (navType) {
            serverWaitMetrics.push(makeMetricText("FRONTEND_NAV_TYPE", "Navigation Type", navType));
        }

        const syntheticServerWaitStage = {
            stageTypeCode: "SERVER_WAIT",
            stageTypeName: "Ожидание ответа сервера",
            stageOrder: -900,
            startedAt: toIsoTime(serverWaitStartMs),
            endedAt: toIsoTime(serverWaitEndMs),
            logStartedAt: null,
            logEndedAt: null,
            durationMs: serverWaitMs ?? 0,
            isError: Boolean(frontendStage.isError),
            errorMessage: frontendStage.errorMessage || null,
            metrics: serverWaitMetrics,
            logs: []
        };

        const syntheticFrontendRenderStage = {
            stageTypeCode: "FRONTEND_RENDER",
            stageTypeName: "Фронтенд: рендер после ответа",
            stageOrder: 10_000,
            startedAt: toIsoTime(serverWaitEndMs),
            endedAt: toIsoTime(frontendRenderEndMs),
            logStartedAt: null,
            logEndedAt: null,
            durationMs: frontendRenderMs ?? 0,
            isError: Boolean(frontendStage.isError),
            errorMessage: frontendStage.errorMessage || null,
            metrics: frontendStage.metrics || [],
            logs: []
        };

        return [
            syntheticStartStage,
            syntheticServerWaitStage,
            ...backendStages,
            syntheticFrontendRenderStage
        ];
    }

    function compareStageTimelineOrder(left, right) {
        const leftTs = toEpochMs(left?.startedAt);
        const rightTs = toEpochMs(right?.startedAt);
        if (leftTs != null && rightTs != null && leftTs !== rightTs) {
            return leftTs - rightTs;
        }
        if (leftTs != null && rightTs == null) {
            return -1;
        }
        if (leftTs == null && rightTs != null) {
            return 1;
        }
        const leftOrder = Number(left?.stageOrder);
        const rightOrder = Number(right?.stageOrder);
        const safeLeftOrder = Number.isFinite(leftOrder) ? leftOrder : Number.MAX_SAFE_INTEGER;
        const safeRightOrder = Number.isFinite(rightOrder) ? rightOrder : Number.MAX_SAFE_INTEGER;
        if (safeLeftOrder !== safeRightOrder) {
            return safeLeftOrder - safeRightOrder;
        }
        return 0;
    }

    function pickFrontendStage(stages) {
        const frontendStages = (stages || []).filter((stage) => normalizeStageCode(stage?.stageTypeCode) === "FRONTEND");
        if (!frontendStages.length) {
            return null;
        }
        const withTtfb = frontendStages.find((stage) => readStageMetricNum(stage, "FRONTEND_TTFB_MS") != null);
        if (withTtfb) {
            return withTtfb;
        }
        return frontendStages[frontendStages.length - 1];
    }

    function normalizeStageCode(code) {
        if (!code) {
            return "";
        }
        return String(code).trim().toUpperCase();
    }

    function readStageMetricNum(stage, metricCode) {
        const metric = findStageMetric(stage, metricCode);
        if (!metric) {
            return null;
        }
        const raw = metric.metricValueNum != null ? metric.metricValueNum : metric.metricValueText;
        const value = Number(raw);
        return Number.isFinite(value) ? value : null;
    }

    function readStageMetricText(stage, metricCode) {
        const metric = findStageMetric(stage, metricCode);
        if (!metric) {
            return null;
        }
        const text = metric.metricValueText == null ? null : String(metric.metricValueText).trim();
        return text || null;
    }

    function findStageMetric(stage, metricCode) {
        const normalizedCode = normalizeStageCode(metricCode);
        return (stage?.metrics || []).find((metric) => normalizeStageCode(metric?.metricTypeCode) === normalizedCode) || null;
    }

    function toEpochMs(value) {
        if (!value) {
            return null;
        }
        const date = new Date(value);
        const ms = date.getTime();
        return Number.isFinite(ms) ? ms : null;
    }

    function toIsoTime(epochMs) {
        if (!Number.isFinite(epochMs)) {
            return null;
        }
        return new Date(epochMs).toISOString();
    }

    function minEpochMs(values) {
        const normalized = (values || []).filter((value) => Number.isFinite(value));
        if (!normalized.length) {
            return null;
        }
        return Math.min(...normalized);
    }

    function clampDuration(value) {
        if (value == null) {
            return null;
        }
        if (!Number.isFinite(value)) {
            return 0;
        }
        return Math.max(0, Math.round(value));
    }

    function makeMetricNum(code, name, value, unit) {
        return {
            metricTypeCode: code,
            metricTypeName: name,
            metricValueNum: Number(value),
            metricValueText: null,
            unit: unit || null
        };
    }

    function makeMetricText(code, name, value) {
        return {
            metricTypeCode: code,
            metricTypeName: name,
            metricValueNum: null,
            metricValueText: value,
            unit: null
        };
    }

    async function loadCompare() {
        const params = new URLSearchParams();
        const moduleCode = refs.moduleType?.value?.trim();
        if (moduleCode) {
            params.set("moduleCode", moduleCode);
        }
        const eventType = refs.eventType.value?.trim();
        if (eventType) {
            params.set("eventTypeCode", eventType);
        }
        const requestPath = refs.analyticsRequestPath?.value?.trim();
        if (requestPath) {
            params.set("requestPath", requestPath);
        }

        setIfPresent(params, "baselineFrom", toIso(refs.compareBaselineFrom.value));
        setIfPresent(params, "baselineTo", toIso(refs.compareBaselineTo.value));
        setIfPresent(params, "targetFrom", toIso(refs.compareTargetFrom.value));
        setIfPresent(params, "targetTo", toIso(refs.compareTargetTo.value));

        const data = await fetchJson(`${api("/compare")}?${params.toString()}`);
        refs.compareCards.innerHTML = renderCompareCards(data);

        const delta = data.delta || {};
        upsertChart("chart-compare-delta", {
            type: "bar",
            data: {
                labels: ["Count %", "AVG %", "P95 %", "ErrorRate %"],
                datasets: [{
                    label: "Изменение, %",
                    data: [
                        toNumber(delta.countPct),
                        toNumber(delta.avgMsPct),
                        toNumber(delta.p95MsPct),
                        toNumber(delta.errorRatePct)
                    ],
                    backgroundColor: [
                        "rgba(109,40,217,0.8)",
                        "rgba(15,118,110,0.8)",
                        "rgba(180,83,9,0.8)",
                        "rgba(185,28,28,0.8)"
                    ],
                    borderRadius: 8
                }]
            },
            options: barChartOptions("%")
        });
    }

    function renderCompareCards(data) {
        const baseline = data.baseline || {};
        const target = data.target || {};
        const delta = data.delta || {};
        const cards = [
            {
                title: "Count",
                base: formatInt(baseline.count || 0),
                target: formatInt(target.count || 0),
                delta: formatDelta(delta.countPct)
            },
            {
                title: "AVG, ms",
                base: formatMs(baseline.avgMs),
                target: formatMs(target.avgMs),
                delta: formatDelta(delta.avgMsPct)
            },
            {
                title: "P95, ms",
                base: formatMs(baseline.p95Ms),
                target: formatMs(target.p95Ms),
                delta: formatDelta(delta.p95MsPct)
            },
            {
                title: "Error rate",
                base: formatPercent(baseline.errorRate),
                target: formatPercent(target.errorRate),
                delta: formatDelta(delta.errorRatePct)
            }
        ];
        return cards.map((card) => `
            <article class="analytics-compare-card">
                <div class="analytics-compare-title">${card.title}</div>
                <div class="analytics-compare-lines">
                    <div>База: <b>${card.base}</b></div>
                    <div>Текущий: <b>${card.target}</b></div>
                </div>
                <div class="analytics-compare-delta ${card.delta.className}">${card.delta.text}</div>
            </article>
        `).join("");
    }

    function mainParams(includeEventType = true) {
        const params = new URLSearchParams();
        setIfPresent(params, "from", toIso(refs.from.value));
        setIfPresent(params, "to", toIso(refs.to.value));
        const moduleCode = refs.moduleType?.value?.trim();
        if (moduleCode) {
            params.set("moduleCode", moduleCode);
        }
        const eventType = refs.eventType.value?.trim();
        if (includeEventType && eventType) {
            params.set("eventTypeCode", eventType);
        }
        const requestPath = refs.analyticsRequestPath?.value?.trim();
        if (requestPath) {
            params.set("requestPath", requestPath);
        }
        const bucket = refs.bucket.value?.trim();
        if (bucket) {
            params.set("bucketMinutes", bucket);
        }
        appendGlobalMetricFilterParams(params);
        return params;
    }

    async function refreshEventTypeOptionsByScope() {
        if (!refs.eventType && !refs.eventsEventType) {
            return;
        }
        const selectedMain = (refs.eventType?.value || "").trim();
        const selectedEvents = (refs.eventsEventType?.value || "").trim();
        const payload = await fetchFilterOptionsPayload("", false);
        const available = Array.isArray(payload?.eventTypes)
            ? payload.eventTypes
                .map((item) => ({
                    code: String(item?.code || "").trim(),
                    name: String(item?.name || item?.code || "").trim()
                }))
                .filter((item) => item.code.length > 0)
                .sort((a, b) => a.name.localeCompare(b.name, "ru"))
            : [];
        const nextMain = available.some((item) => item.code === selectedMain) ? selectedMain : "";
        const nextEvents = available.some((item) => item.code === selectedEvents) ? selectedEvents : "";
        fillSelect(refs.eventType, available, "Все события", true, nextMain);
        fillSelect(refs.eventsEventType, available, "Все события", true, nextEvents);
    }

    async function fetchFilterOptionsPayload(attributeCode, includeEventType = true) {
        const params = new URLSearchParams();
        setIfPresent(params, "from", toIso(refs.from?.value || ""));
        setIfPresent(params, "to", toIso(refs.to?.value || ""));
        const moduleCode = refs.moduleType?.value?.trim();
        if (moduleCode) {
            params.set("moduleCode", moduleCode);
        }
        const eventTypeCode = refs.eventType?.value?.trim();
        if (includeEventType && eventTypeCode) {
            params.set("eventTypeCode", eventTypeCode);
        }
        const requestPath = refs.analyticsRequestPath?.value?.trim();
        if (requestPath) {
            params.set("requestPath", requestPath);
        }
        const attrCode = String(attributeCode || "").trim();
        if (attrCode) {
            params.set("attributeCode", attrCode);
        }
        return fetchJson(`${api("/filter-options")}?${params.toString()}`);
    }

    function stageMetricParams(rangeMode) {
        const params = new URLSearchParams();
        const mode = rangeMode === "compare" ? "compare" : "primary";
        const globalRanges = state.globalCompareEnabled ? resolveGlobalBeforeRange() : null;
        const localAfterFrom = refs.stageMetricFromA?.value || globalRanges?.afterFrom || refs.from?.value || "";
        const localAfterTo = refs.stageMetricToA?.value || globalRanges?.afterTo || refs.to?.value || "";
        const ranges = normalizeCompareRangesByAfter(localAfterFrom, localAfterTo, "", "");
        const fromValue = mode === "compare" ? ranges.beforeFrom : ranges.afterFrom;
        const toValue = mode === "compare" ? ranges.beforeTo : ranges.afterTo;
        setIfPresent(params, "from", toIso((fromValue || "").trim()));
        setIfPresent(params, "to", toIso((toValue || "").trim()));
        const moduleCode = refs.moduleType?.value?.trim();
        if (moduleCode) {
            params.set("moduleCode", moduleCode);
        }
        const eventType = refs.eventType.value?.trim();
        if (eventType) {
            params.set("eventTypeCode", eventType);
        }
        const globalPath = refs.analyticsRequestPath?.value?.trim();
        const effectivePath = globalPath;
        if (effectivePath) {
            params.set("requestPath", effectivePath);
        }
        const bucket = refs.bucket.value?.trim();
        if (bucket) {
            params.set("bucketMinutes", bucket);
        }
        appendGlobalMetricFilterParams(params);
        return params;
    }

    function compareTargetParamsFromBaseline() {
        const params = new URLSearchParams();
        setIfPresent(params, "from", toIso(refs.compareBaselineFrom?.value));
        setIfPresent(params, "to", toIso(refs.compareBaselineTo?.value));
        const moduleCode = refs.moduleType?.value?.trim();
        if (moduleCode) {
            params.set("moduleCode", moduleCode);
        }
        const eventType = refs.eventType?.value?.trim();
        if (eventType) {
            params.set("eventTypeCode", eventType);
        }
        const requestPath = refs.analyticsRequestPath?.value?.trim();
        if (requestPath) {
            params.set("requestPath", requestPath);
        }
        const bucket = refs.bucket?.value?.trim();
        if (bucket) {
            params.set("bucketMinutes", bucket);
        }
        appendGlobalMetricFilterParams(params);
        return params;
    }

    function rangeParams() {
        const params = new URLSearchParams();
        setIfPresent(params, "from", toIso(refs.from.value));
        setIfPresent(params, "to", toIso(refs.to.value));
        const moduleCode = refs.moduleType?.value?.trim();
        if (moduleCode) {
            params.set("moduleCode", moduleCode);
        }
        appendGlobalMetricFilterParams(params);
        return params;
    }

    function appendGlobalMetricFilterParams(params) {
        if (!params) {
            return;
        }
        const attrCode = (refs.globalMetricCode?.value || "").trim();
        if (!attrCode) {
            return;
        }
        params.set("filterAttributeCode", attrCode);
        const meta = state.globalMetricMetaByCode[attrCode] || {};
        if (meta.numeric) {
            const min = (refs.globalMetricMin?.value || "").trim();
            const max = (refs.globalMetricMax?.value || "").trim();
            if (min !== "" && Number.isFinite(Number(min))) {
                params.set("filterAttributeMinValue", String(Number(min)));
            }
            if (max !== "" && Number.isFinite(Number(max))) {
                params.set("filterAttributeMaxValue", String(Number(max)));
            }
            return;
        }
        const value = (refs.globalMetricValueInput?.value || refs.globalMetricValueSelect?.value || "").trim();
        if (value) {
            params.set("filterAttributeValue", value);
        }
    }

    function syncEventsRangeFromMain(force) {
        syncEventsRangeField("from", refs.eventsFrom, refs.from?.value || "", force);
        syncEventsRangeField("to", refs.eventsTo, refs.to?.value || "", force);
        syncEventsQuickRangeFromInputs();
    }

    function syncEventsQuickRangeFromInputs() {
        syncQuickRangeSelectFromRange(refs.eventsQuickRange, refs.eventsFrom?.value || "", refs.eventsTo?.value || "");
    }

    function syncEventsRangeField(key, input, targetValue, force) {
        if (!input) {
            return;
        }
        const current = input.value || "";
        const prevSynced = state.eventsRangeSynced[key] || "";
        if (force || !current || current === prevSynced) {
            input.value = targetValue || "";
            state.eventsRangeSynced[key] = targetValue || "";
            return;
        }
        state.eventsRangeSynced[key] = current;
    }

    function syncUniversalRangeFromMain(force) {
        if (!force) {
            return;
        }
        if (refs.universalFrom) {
            refs.universalFrom.value = refs.from?.value || "";
        }
        if (refs.universalTo) {
            refs.universalTo.value = refs.to?.value || "";
        }
        if (refs.universalBucket) {
            refs.universalBucket.value = refs.bucket?.value || "";
        }
        syncUniversalQuickRangeFromInputs();
        state.universalAllTime = (refs.universalQuickPreset?.value || "").trim().toLowerCase() === "all";
        syncUniversalBeforeRangeFromAfter();
        updateUniversalCompareUi();
    }

    function syncUniversalQuickRangeFromInputs() {
        syncQuickRangeSelectFromRange(refs.universalQuickPreset, refs.universalFrom?.value || "", refs.universalTo?.value || "");
    }

    function updateUniversalCompareUi() {
        const mode = UNIVERSAL_COMPARE_FOLLOWS_GLOBAL
            ? resolveGlobalInlineCompareMode()
            : (!!refs.universalCompareEnabled?.checked ? "split" : "off");
        const enabled = mode !== "off";
        refs.universalBeforeWrap?.classList.add("d-none");
        refs.universalBeforeWrapTo?.classList.add("d-none");
        if (enabled) {
            syncUniversalBeforeRangeFromAfter();
        }
        syncComparePeriodSummary(refs.universalBeforeSummary, mode, resolveUniversalCompareRanges());
    }

    function syncUniversalCompareFromGlobalFilter() {
        if (!UNIVERSAL_COMPARE_FOLLOWS_GLOBAL) {
            return;
        }
        const mode = resolveGlobalInlineCompareMode();
        const enabled = mode !== "off";
        if (refs.universalCompareWrap) {
            refs.universalCompareWrap.classList.add("d-none");
        }
        if (refs.universalCompareEnabled) {
            refs.universalCompareEnabled.checked = enabled;
            refs.universalCompareEnabled.disabled = true;
        }
        if (refs.universalCompareGhost) {
            refs.universalCompareGhost.checked = mode === "overlay";
            refs.universalCompareGhost.disabled = true;
        }
        if (refs.universalBeforeFrom) {
            refs.universalBeforeFrom.disabled = true;
        }
        if (refs.universalBeforeTo) {
            refs.universalBeforeTo.disabled = true;
        }
        if (enabled) {
            const ranges = resolveGlobalBeforeRange();
            if (refs.universalBeforeFrom) refs.universalBeforeFrom.value = ranges.beforeFrom || "";
            if (refs.universalBeforeTo) refs.universalBeforeTo.value = ranges.beforeTo || "";
        }
        updateUniversalCompareUi();
        setUniversalCompareMode(mode);
    }

    function setUniversalCompareMode(modeRaw) {
        const mode = normalizeCompareMode(modeRaw);
        const splitActive = mode === "split";
        UNIVERSAL_COMPARE_CHART_IDS.forEach((canvasId) => {
            const currentlyEnabled = !!state.inlineCompareEnabled[canvasId];
            state.inlineCompareModeBySource[canvasId] = mode;
            state.inlineCompareModeOverriddenBySource[canvasId] = false;
            state.inlineCompareGhostBySource[canvasId] = mode === "overlay";
            if (splitActive && !currentlyEnabled) {
                enableInlineCompareLayout(canvasId);
                state.inlineCompareEnabled[canvasId] = true;
                bindUniversalCompareScrollSync(canvasId);
                return;
            }
            if (!splitActive && currentlyEnabled) {
                disableInlineCompareLayout(canvasId);
                state.inlineCompareEnabled[canvasId] = false;
            }
        });
        updateCompareButtonsState();
        const expandedId = state.expandedChart.sourceCanvasId || "";
        if (UNIVERSAL_COMPARE_CHART_IDS.has(expandedId)) {
            collapseExpandedChart();
            toggleExpandedChart(expandedId);
        }
    }

    function setUniversalCompareEnabled(enabled) {
        setUniversalCompareMode(enabled ? "split" : "off");
    }

    function syncUniversalBeforeRangeFromAfter() {
        const afterFromRaw = (refs.universalFrom?.value || "").trim();
        const afterToRaw = (refs.universalTo?.value || "").trim();
        if (!afterFromRaw || !afterToRaw) {
            return;
        }
        const afterFromDate = new Date(afterFromRaw);
        const afterToDate = new Date(afterToRaw);
        if (Number.isNaN(afterFromDate.getTime()) || Number.isNaN(afterToDate.getTime()) || afterFromDate >= afterToDate) {
            return;
        }
        const durationMs = Math.max(60_000, afterToDate.getTime() - afterFromDate.getTime());
        const beforeToDate = new Date(afterFromDate.getTime());
        const beforeFromDate = new Date(beforeToDate.getTime() - durationMs);
        if (refs.universalBeforeFrom) {
            refs.universalBeforeFrom.value = toDateTimeLocalString(beforeFromDate);
        }
        if (refs.universalBeforeTo) {
            refs.universalBeforeTo.value = toDateTimeLocalString(beforeToDate);
        }
    }

    async function applyUniversalQuickRangePreset(presetValue) {
        const normalized = String(presetValue || "").trim().toLowerCase();
        if (!normalized) {
            return;
        }
        if (normalized === "all") {
            await ensureAllTimeRangeLoaded();
            const allRange = getAllTimeLocalRange();
            if (refs.universalFrom) refs.universalFrom.value = allRange.from;
            if (refs.universalTo) refs.universalTo.value = allRange.to;
            state.universalAllTime = true;
            syncUniversalBeforeRangeFromAfter();
            return;
        }
        state.universalAllTime = false;
        const range = buildQuickRangeFromDate(new Date(), normalized);
        if (!range) {
            return;
        }
        if (refs.universalFrom) refs.universalFrom.value = toDateTimeLocalString(range.fromDate);
        if (refs.universalTo) refs.universalTo.value = toDateTimeLocalString(range.toDate);
        syncUniversalBeforeRangeFromAfter();
    }

    async function applyEventsQuickRangePreset(presetValue) {
        const normalized = String(presetValue || "").trim().toLowerCase();
        if (!normalized) {
            return;
        }
        if (normalized === "all") {
            await ensureAllTimeRangeLoaded();
            const allRange = getAllTimeLocalRange();
            refs.eventsFrom.value = allRange.from;
            refs.eventsTo.value = allRange.to;
            state.eventsPage = 0;
            await loadEvents(true);
            return;
        }
        const range = buildQuickRangeFromDate(new Date(), normalized);
        if (!range) {
            return;
        }
        refs.eventsFrom.value = toDateTimeLocalString(range.fromDate);
        refs.eventsTo.value = toDateTimeLocalString(range.toDate);
        state.eventsPage = 0;
        await loadEvents(true);
    }

    function updateEventsErrorClassVisibility() {
        const show = (refs.eventsIsError?.value || "") === "true";
        refs.eventsErrorClassWrap?.classList.toggle("d-none", !show);
        if (!show && refs.eventsErrorClass) {
            refs.eventsErrorClass.value = "";
        }
    }

    async function applyQuickRangeFromControls() {
        const count = Number(refs.quickRangeCount?.value || 0);
        const normalizedCount = Number.isFinite(count) && count > 0 ? Math.floor(count) : 1;
        const unit = refs.quickRangeUnit?.value || "hours";
        await applyRelativeRange(normalizedCount, unit);
    }

    async function applyQuickRangePreset(presetValue) {
        const normalized = String(presetValue || "").trim().toLowerCase();
        if (!normalized) {
            return;
        }
        if (normalized === "all") {
            await applyAllTimeRange();
            return;
        }

        const match = normalized.match(/^(\d+)(m|h|d|w|mo|y)$/);
        if (!match) {
            return;
        }

        const count = Number(match[1]);
        const unitCode = match[2];
        const unitMap = {
            m: "minutes",
            h: "hours",
            d: "days",
            w: "weeks",
            mo: "months",
            y: "years"
        };
        const unit = unitMap[unitCode];
        if (!unit || !Number.isFinite(count) || count <= 0) {
            return;
        }

        await applyRelativeRange(count, unit);
    }

    async function applyRelativeRange(count, unit) {
        const safeCount = Number.isFinite(count) && count > 0 ? Math.floor(count) : 1;
        const safeUnit = String(unit || "hours").toLowerCase();
        const toDate = new Date();
        const fromDate = new Date(toDate.getTime());

        if (safeUnit === "minutes") {
            fromDate.setMinutes(fromDate.getMinutes() - safeCount);
        } else if (safeUnit === "hours") {
            fromDate.setHours(fromDate.getHours() - safeCount);
        } else if (safeUnit === "days") {
            fromDate.setDate(fromDate.getDate() - safeCount);
        } else if (safeUnit === "weeks") {
            fromDate.setDate(fromDate.getDate() - safeCount * 7);
        } else if (safeUnit === "months") {
            fromDate.setMonth(fromDate.getMonth() - safeCount);
        } else if (safeUnit === "years") {
            fromDate.setFullYear(fromDate.getFullYear() - safeCount);
        } else {
            fromDate.setHours(fromDate.getHours() - safeCount);
        }

        refs.from.value = toDateTimeLocalString(fromDate);
        refs.to.value = toDateTimeLocalString(toDate);
        initDefaultCompareRange();
        state.lastMainRangeKey = buildMainRangeKey();
        if (state.globalCompareEnabled) {
            state.globalCompareBeforeCustom = false;
        }
        clearAllChartLocalOverrides();
        resetInlineComparePresetsFromTopFilter();
        syncStageMetricQuickRangeFromMain();
        syncStageMetricRangesFromMain(true);
        syncStageTextQuickRangeFromMain();
        syncStageTextRangesFromMain(true);
        syncUniversalRangeFromMain(true);

        // Keep sidebar dictionaries in sync with the new top period,
        // but do not block chart reload on dictionary errors/timeouts.
        await refreshScopedOptionsSafe();
        applyGlobalMetricToEventsFilter();

        state.eventsPage = 0;
        await reloadAll();
        if (state.globalCompareEnabled) {
            await applyGlobalCompareToAllCharts();
        }
    }

    async function applyAllTimeRange() {
        await ensureAllTimeRangeLoaded();
        const allRange = getAllTimeLocalRange();
        refs.from.value = allRange.from;
        refs.to.value = allRange.to;
        refs.compareBaselineFrom.value = allRange.from;
        refs.compareBaselineTo.value = allRange.to;
        refs.compareTargetFrom.value = allRange.from;
        refs.compareTargetTo.value = allRange.to;
        state.lastMainRangeKey = buildMainRangeKey();
        if (state.globalCompareEnabled) {
            state.globalCompareBeforeCustom = false;
        }
        clearAllChartLocalOverrides();
        resetInlineComparePresetsFromTopFilter();
        syncStageMetricQuickRangeFromMain();
        syncStageMetricRangesFromMain(true);
        syncStageTextQuickRangeFromMain();
        syncStageTextRangesFromMain(true);
        syncUniversalRangeFromMain(true);

        // Keep sidebar dictionaries in sync with the new top period,
        // but do not block chart reload on dictionary errors/timeouts.
        await refreshScopedOptionsSafe();
        applyGlobalMetricToEventsFilter();

        state.eventsPage = 0;
        await reloadAll();
        if (state.globalCompareEnabled) {
            await applyGlobalCompareToAllCharts();
        }
    }

    function fillSelect(select, options, emptyLabel, includeEmpty, selectedValue, labelResolver) {
        if (!select) {
            return;
        }
        const values = options || [];
        const parts = [];
        if (includeEmpty) {
            parts.push(`<option value="">${escapeHtml(emptyLabel || "Все")}</option>`);
        }
        for (const option of values) {
            const resolvedLabel = typeof labelResolver === "function"
                ? labelResolver(option)
                : (option?.name || option?.code);
            parts.push(`<option value="${escapeHtml(option.code)}">${escapeHtml(resolvedLabel || option.code)}</option>`);
        }
        select.innerHTML = parts.join("");
        if (selectedValue != null) {
            select.value = selectedValue;
        }
    }

    function localizeMetricDisplayName(metricCode, fallbackName) {
        const code = String(metricCode || "").trim().toUpperCase();
        const map = {
            DB_QUERY_COUNT: "Количество SQL-запросов",
            RESPONSE_SIZE_BYTES: "Полный размер ответа (байт)",
            ITEM_COUNT: "Количество элементов",
            PAGE_URL: "URL страницы",
            PAYLOAD_SIZE_BYTES: "Размер полезной нагрузки (байт)",
            NAVIGATION_TYPE: "Тип навигации",
            FRONTEND_NAV_TYPE: "Тип навигации",
            DOM_CONTENT_LOADED_MS: "DOM загружен (DOMContentLoaded, мс)",
            FRONTEND_DOM_CONTENT_LOADED_MS: "DOM загружен (DOMContentLoaded, мс)",
            FRONTEND_TTFB_MS: "Время до первого байта (TTFB, мс)",
            LCP: "Отрисовка самого крупного элемента (LCP, мс)",
            LCP_MS: "Отрисовка самого крупного элемента (LCP, мс)",
            DOM_INTERACTIVE_MS: "Готовность DOM к взаимодействию (DOM Interactive, мс)",
            LOAD_EVENT_MS: "Завершение загрузки страницы (Load Event, мс)",
            TRANSFER_SIZE_BYTES: "Размер передачи (байт)",
            INP: "Задержка отклика интерфейса (INP, мс)",
            INP_MS: "Задержка отклика интерфейса (INP, мс)",
            ERROR_CODE: "Код ошибки",
            API_DURATION_MS: "Длительность API (мс)",
            API_METHOD: "HTTP-метод",
            API_URL: "Адрес API-запроса",
            HTTP_STATUS: "HTTP-статус"
        };
        if (map[code]) {
            return map[code];
        }
        return fallbackName || metricCode || "";
    }

    function mergeStageMetricSummariesWithDictionary(summariesRaw) {
        const byCode = new Map();
        (Array.isArray(summariesRaw) ? summariesRaw : []).forEach((summary) => {
            const code = String(summary?.metricTypeCode || "").trim();
            if (!code || byCode.has(code)) {
                return;
            }
            byCode.set(code, {...summary});
        });
        (state.dictionaries?.stageMetricTypes || []).forEach((option) => {
            const code = String(option?.code || "").trim();
            if (!code || byCode.has(code)) {
                return;
            }
            byCode.set(code, {
                metricTypeCode: code,
                metricTypeName: option?.name || code,
                metricTypeDescription: "",
                metricTypeReadingGuide: "",
                numeric: !isKnownTextStageMetricCode(code),
                sampleCount: 0,
                avgValue: 0,
                p95Value: 0,
                minValue: 0,
                maxValue: 0,
                unit: "",
                topValues: []
            });
        });
        return Array.from(byCode.values()).sort((left, right) => {
            const leftName = localizeMetricDisplayName(left.metricTypeCode, left.metricTypeName || left.metricTypeCode);
            const rightName = localizeMetricDisplayName(right.metricTypeCode, right.metricTypeName || right.metricTypeCode);
            return String(leftName).localeCompare(String(rightName), "ru");
        });
    }

    function isKnownTextStageMetricCode(metricCode) {
        const code = String(metricCode || "").trim().toUpperCase();
        if (!code) {
            return false;
        }
        const exactTextCodes = new Set([
            "API_METHOD",
            "API_URL",
            "ERROR_CODE",
            "FRONTEND_HTTP_METHOD",
            "FRONTEND_HTTP_STATUS",
            "FRONTEND_NAV_TYPE",
            "FRONTEND_PAGE_URL",
            "HTTP_METHOD",
            "HTTP_STATUS",
            "NAVIGATION_TYPE",
            "PAGE_URL",
            "REQUEST_PATH",
            "TRACE_ID",
            "USER_AGENT"
        ]);
        if (exactTextCodes.has(code)) {
            return true;
        }
        return code.includes("URL")
            || code.includes("METHOD")
            || code.includes("STATUS")
            || code.includes("TRACE")
            || code.includes("NAVIGATION")
            || code.includes("ERROR_CODE")
            || code.includes("USER_AGENT");
    }

    function initDefaultCompareRange() {
        if (!refs.from.value || !refs.to.value) {
            return;
        }
        refs.compareTargetFrom.value = refs.from.value;
        refs.compareTargetTo.value = refs.to.value;

        const fromDate = new Date(refs.from.value);
        const toDate = new Date(refs.to.value);
        if (Number.isNaN(fromDate.getTime()) || Number.isNaN(toDate.getTime())) {
            return;
        }
        const durationMs = Math.max(60_000, toDate.getTime() - fromDate.getTime());
        const baselineTo = new Date(fromDate.getTime());
        const baselineFrom = new Date(fromDate.getTime() - durationMs);
        refs.compareBaselineFrom.value = toDateTimeLocalString(baselineFrom);
        refs.compareBaselineTo.value = toDateTimeLocalString(baselineTo);
        syncCompareQuickRangeByCurrentWindow();
    }

    async function applyCompareQuickRangePreset() {
        const preset = (refs.compareQuickRange?.value || "").trim();
        if (preset === "all") {
            await ensureAllTimeRangeLoaded();
            const allRange = getAllTimeLocalRange();
            refs.compareTargetFrom.value = allRange.from;
            refs.compareTargetTo.value = allRange.to;
            refs.compareBaselineFrom.value = allRange.from;
            refs.compareBaselineTo.value = allRange.to;
            return;
        }
        const targetRange = buildQuickRangeFromDate(new Date(), preset);
        if (!targetRange) {
            return;
        }
        const targetFromMs = targetRange.fromDate.getTime();
        const targetToMs = targetRange.toDate.getTime();
        const durationMs = Math.max(60_000, targetToMs - targetFromMs);
        const baselineToMs = targetFromMs;
        const baselineFromMs = baselineToMs - durationMs;
        refs.compareTargetFrom.value = toDateTimeLocalString(new Date(targetFromMs));
        refs.compareTargetTo.value = toDateTimeLocalString(new Date(targetToMs));
        refs.compareBaselineFrom.value = toDateTimeLocalString(new Date(baselineFromMs));
        refs.compareBaselineTo.value = toDateTimeLocalString(new Date(baselineToMs));
    }

    function syncCompareQuickRangeByCurrentWindow() {
        if (!refs.compareQuickRange) {
            return;
        }
        const targetFrom = refs.compareTargetFrom?.value ? new Date(refs.compareTargetFrom.value) : null;
        const targetTo = refs.compareTargetTo?.value ? new Date(refs.compareTargetTo.value) : null;
        if (!targetFrom || !targetTo || Number.isNaN(targetFrom.getTime()) || Number.isNaN(targetTo.getTime())) {
            return;
        }
        syncQuickRangeSelectFromRange(refs.compareQuickRange, toDateTimeLocalString(targetFrom), toDateTimeLocalString(targetTo));
    }

    function upsertChart(canvasId, config) {
        const canvas = document.getElementById(canvasId);
        if (!canvas) {
            if (canvasId === "chart-event-kpi-compare-inline") {
                state.kpiFullChartConfigs[canvasId] = cloneChartConfig(config);
                state.chartConfigs[canvasId] = cloneChartConfig(config);
            }
            return;
        }
        state.chartScenarioBaseConfigs[canvasId] = cloneChartConfig(config);
        let configToRender = applyScenarioToChartConfig(canvasId, cloneChartConfig(config));
        if (canvasId === "chart-event-kpi" || canvasId === "chart-event-kpi-compare-inline") {
            ensureKpiMiniWrap(canvas);
            if (canvasId === "chart-event-kpi") {
                normalizeEventKpiMiniLayoutState();
            }
            const wrap = canvas.closest(".analytics-chart-wrap");
            if (wrap && !wrap.classList.contains("analytics-chart-wrap-expanded")) {
                if (canvasId === "chart-event-kpi-compare-inline") {
                    storeKpiCompareConfigForExpanded(canvasId, config);
                    destroyRenderedChartOnly(canvasId);
                    return;
                }
                const miniKpiCompareMode = resolveMiniKpiCompareMode("chart-event-kpi");
                const compareEnabledResolved = miniKpiCompareMode !== "off";
                const isCompare = compareEnabledResolved;
                state.kpiFullChartConfigs[canvasId] = cloneChartConfig(config);
                const totalCount = Array.isArray(config?.data?.labels) ? config.data.labels.length : 0;
                state.kpiMiniTopStatsByCanvas[canvasId] = {
                    totalCount,
                    shownCount: totalCount,
                    truncated: false
                };
                refreshEventKpiMiniTopHint();
                const labels = Array.isArray(configToRender?.data?.labels) ? configToRender.data.labels : [];
                const mode = isCompare ? "mini-compare" : "mini-single";
                applyEventKpiMiniRenderLayout(wrap, mode);
                applyKpiDynamicWidth(wrap, labels.length, mode, 1);
                applyKpiDynamicHeight(wrap, labels, mode);
            }
        }
        state.chartConfigs[canvasId] = cloneChartConfig(configToRender);
        const existingChart = state.charts[canvasId];
        if (existingChart) {
            const requestedType = configToRender.type || existingChart.config.type;
            const currentType = existingChart.config.type;
            if (requestedType !== currentType) {
                existingChart.destroy();
                state.charts[canvasId] = new Chart(canvas.getContext("2d"), configToRender);
                setChartExpandedTicks(state.charts[canvasId], false);
                queueInitialKpiCompareScrollOffsets(canvasId);
                return;
            }
            existingChart.data = configToRender.data;
            existingChart.options = configToRender.options;
            setChartExpandedTicks(existingChart, false);
            existingChart.update("none");
            queueInitialKpiCompareScrollOffsets(canvasId);
            if (isExpandedChartRelated(canvasId)) {
                renderExpandedChartClone(state.expandedChart.sourceCanvasId);
            }
            return;
        }
        state.charts[canvasId] = new Chart(canvas.getContext("2d"), configToRender);
        setChartExpandedTicks(state.charts[canvasId], false);
        queueInitialKpiCompareScrollOffsets(canvasId);
        if (isExpandedChartRelated(canvasId)) {
            renderExpandedChartClone(state.expandedChart.sourceCanvasId);
        }
    }

    function isExpandedChartRelated(canvasId) {
        const sourceCanvasId = state.expandedChart.sourceCanvasId || "";
        if (!sourceCanvasId || !canvasId) {
            return false;
        }
        if (canvasId === sourceCanvasId) {
            return true;
        }
        const compareCanvasId = state.inlineCompareCanvasBySource[sourceCanvasId] || "";
        return compareCanvasId && compareCanvasId === canvasId;
    }

    function ensureKpiMiniWrap(canvas) {
        const wrap = canvas?.closest(".analytics-chart-wrap");
        if (!wrap) {
            return;
        }
        if (wrap.classList.contains("analytics-chart-wrap-expanded")) {
            return;
        }
        wrap.classList.add("analytics-kpi-mini-inner");
        let parent = wrap.parentElement;
        if (!parent) {
            return;
        }
        if (parent.classList.contains("analytics-kpi-mini-body")) {
            relocateKpiActionsToOuter(wrap, parent.parentElement);
            return;
        }
        if (parent.classList.contains("analytics-kpi-mini-outer")) {
            const body = document.createElement("div");
            body.className = "analytics-kpi-mini-body";
            parent.insertBefore(body, wrap);
            body.appendChild(wrap);
            relocateKpiActionsToOuter(wrap, parent);
            return;
        }
        const outer = document.createElement("div");
        outer.className = "analytics-kpi-mini-outer";
        const body = document.createElement("div");
        body.className = "analytics-kpi-mini-body";
        parent.insertBefore(outer, wrap);
        outer.appendChild(body);
        body.appendChild(wrap);
        relocateKpiActionsToOuter(wrap, outer);
    }

    function relocateKpiActionsToOuter(wrap, outer) {
        if (!wrap || !outer) {
            return;
        }
        const inlineActions = wrap.querySelector(":scope > .analytics-chart-actions");
        if (!inlineActions) {
            return;
        }
        const hostActions = outer.querySelector(":scope > .analytics-chart-actions");
        if (hostActions && hostActions !== inlineActions) {
            while (inlineActions.firstChild) {
                hostActions.appendChild(inlineActions.firstChild);
            }
            inlineActions.remove();
            return;
        }
        outer.appendChild(inlineActions);
    }

    function resolveKpiMiniScrollHost(wrap) {
        if (!wrap) {
            return wrap;
        }
        return wrap.closest(".analytics-kpi-mini-body") || wrap;
    }

    function isOverviewEventKpiWrap(wrap) {
        const canvas = wrap?.querySelector(":scope > canvas");
        return !!canvas && canvas.id === "chart-event-kpi";
    }

    function resolveChartActionsHost(wrap) {
        if (isOverviewEventKpiWrap(wrap)) {
            return wrap.closest(".analytics-kpi-mini-outer") || wrap;
        }
        const canvas = wrap?.querySelector(":scope > canvas");
        const sourceCanvasId = resolveStageMetricPrimaryCanvasId(canvas?.id || "");
        if (isStageMetricPrimaryCanvas(sourceCanvasId)) {
            const sourceCanvas = document.getElementById(sourceCanvasId);
            return sourceCanvas?.closest(".analytics-stage-metric-chart-card")
                || sourceCanvas?.closest(".analytics-stage-metric-block")
                || wrap;
        }
        return wrap;
    }

    function syncScrollLeftByRatio(source, target) {
        if (!source || !target) {
            return;
        }
        const sourceRange = Math.max(0, source.scrollWidth - source.clientWidth);
        const targetRange = Math.max(0, target.scrollWidth - target.clientWidth);
        if (sourceRange <= 0 || targetRange <= 0) {
            return;
        }
        const ratio = Math.max(0, Math.min(1, source.scrollLeft / sourceRange));
        target.scrollLeft = Math.round(ratio * targetRange);
    }

    function syncScrollTopByRatio(source, target) {
        if (!source || !target) {
            return;
        }
        const sourceRange = Math.max(0, source.scrollHeight - source.clientHeight);
        const targetRange = Math.max(0, target.scrollHeight - target.clientHeight);
        if (sourceRange <= 0 || targetRange <= 0) {
            return;
        }
        const ratio = Math.max(0, Math.min(1, source.scrollTop / sourceRange));
        target.scrollTop = Math.round(ratio * targetRange);
    }

    function kpiLabelStats(labels) {
        const normalized = Array.isArray(labels) ? labels.map((item) => String(item || "")) : [];
        const maxLabelLen = normalized.reduce((acc, value) => Math.max(acc, value.length), 0);
        return {
            count: normalized.length,
            maxLabelLen
        };
    }

    function calcKpiContentHeight(labels, basePlotHeight) {
        const stats = kpiLabelStats(labels);
        const denseOrLong = stats.count >= 16 || stats.maxLabelLen >= 18;
        const xLabelReserve = denseOrLong ? 130 : 88;
        return Math.max(basePlotHeight + xLabelReserve, 280);
    }

    function shouldUseHorizontalScroll(columnsCount, mode) {
        const count = Math.max(0, Number(columnsCount) || 0);
        if (mode === "mini-single") {
            return count > 25;
        }
        if (mode === "mini-compare") {
            return count > 15;
        }
        if (mode === "expanded-compare") {
            return count > 40;
        }
        if (mode === "expanded-single") {
            return count > 60;
        }
        return false;
    }

    function resolveKpiChartWidth({columnsCount, viewportWidth, mode, xScale = 1}) {
        const safeViewportWidth = Math.max(1, Number(viewportWidth) || 0);
        const needsScroll = shouldUseHorizontalScroll(columnsCount, mode);
        const safeScale = Math.max(1, Number(xScale) || 1);
        if (!needsScroll && safeScale <= 1) {
            return safeViewportWidth;
        }
        if (!needsScroll) {
            return Math.ceil(safeViewportWidth * safeScale);
        }
        const minColumnWidthByMode = {
            "mini-single": 35,
            "mini-compare": 21,
            "expanded-compare": 56,
            "expanded-single": 52
        };
        const baseWidth = minColumnWidthByMode[mode] || 56;
        const columnWidth = baseWidth * safeScale;
        const padding = 80;
        const requiredWidth = Math.ceil((Math.max(0, Number(columnsCount) || 0) * columnWidth) + padding);
        return Math.max(safeViewportWidth, requiredWidth);
    }

    function applyKpiDynamicWidth(targetEl, labelsCount, mode, xScale = 1) {
        if (!targetEl) {
            return;
        }
        const scrollHost = resolveKpiMiniScrollHost(targetEl);
        const viewportWidth = Math.max(
            0,
            scrollHost?.clientWidth || targetEl.parentElement?.clientWidth || targetEl.clientWidth || 0
        );
        if (viewportWidth <= 1) {
            return;
        }
        const targetWidth = resolveKpiChartWidth({
            columnsCount: labelsCount,
            viewportWidth,
            mode,
            xScale
        });
        targetEl.style.width = `${targetWidth}px`;
        targetEl.style.minWidth = `${targetWidth}px`;
    }

    function applyKpiDynamicHeight(targetEl, labels, mode = "mini-single") {
        if (!targetEl) {
            return;
        }
        let height = Math.min(620, calcKpiContentHeight(labels, 250));
        if (mode === "mini-compare") {
            height = Math.max(height, 400);
        }
        targetEl.style.height = `${height}px`;
        targetEl.style.minHeight = `${height}px`;
    }

    function destroyChart(canvasId) {
        const existingChart = state.charts[canvasId];
        if (existingChart) {
            existingChart.destroy();
            delete state.charts[canvasId];
        }
        delete state.chartConfigs[canvasId];
        if (state.expandedChart.sourceCanvasId === canvasId) {
            collapseExpandedChart();
        }
    }

    function destroyRenderedChartOnly(canvasId) {
        const existingChart = state.charts[canvasId];
        if (existingChart) {
            existingChart.destroy();
            delete state.charts[canvasId];
        }
    }

    function storeKpiCompareConfigForExpanded(canvasId, config) {
        if (canvasId !== "chart-event-kpi-compare-inline") {
            return;
        }
        const cloned = cloneChartConfig(config);
        state.kpiFullChartConfigs[canvasId] = cloned;
        state.chartConfigs[canvasId] = cloneChartConfig(config);
    }

    function keepKpiCompareConfigWithoutMiniSplit(compareCanvasId, config) {
        storeKpiCompareConfigForExpanded(compareCanvasId, config);
        destroyRenderedChartOnly(compareCanvasId);
    }

    function applyEventKpiMiniRenderLayout(wrap, mode) {
        if (!wrap) {
            return;
        }
        const normalizedMode = mode === "mini-compare" ? "mini-compare" : "mini-single";
        const body = wrap.closest(".analytics-kpi-mini-body");
        const outer = wrap.closest(".analytics-kpi-mini-outer");
        [wrap, body, outer].forEach((element) => {
            if (!element) {
                return;
            }
            element.classList.remove("analytics-kpi-mini-mode-single", "analytics-kpi-mini-mode-compare");
            element.classList.add(normalizedMode === "mini-compare"
                ? "analytics-kpi-mini-mode-compare"
                : "analytics-kpi-mini-mode-single");
            element.dataset.kpiMiniMode = normalizedMode;
        });
        const scrollHost = resolveKpiMiniScrollHost(wrap);
        if (scrollHost) {
            scrollHost.scrollLeft = 0;
            scrollHost.scrollTop = 0;
        }
    }

    function normalizeEventKpiMiniLayoutState() {
        const canvasId = "chart-event-kpi";
        const sourceCanvas = document.getElementById(canvasId);
        const stalePair = refs.analyticsPage?.querySelector(`.analytics-chart-compare-pair[data-chart-id='${canvasId}']`);
        const sourceWrapFromPair = stalePair?.querySelector(".analytics-chart-wrap:not(.analytics-chart-wrap-compare)");
        const restoreParent = stalePair?.parentElement || null;
        if (sourceWrapFromPair && restoreParent) {
            restoreParent.insertBefore(sourceWrapFromPair, stalePair);
        }
        if (restoreParent?.classList?.contains("analytics-kpi-compare-body-host")) {
            restoreParent.classList.remove("analytics-kpi-compare-body-host");
        }
        if (stalePair) {
            stalePair.remove();
        }

        if (sourceCanvas) {
            ensureKpiMiniWrap(sourceCanvas);
        }
        const sourceWrap = sourceCanvas?.closest(".analytics-chart-wrap");
        const outer = sourceWrap?.closest(".analytics-kpi-mini-outer");
        const body = sourceWrap?.closest(".analytics-kpi-mini-body");
        const cleanupRoot = outer || refs.analyticsPage || document;
        cleanupRoot.querySelectorAll?.(".analytics-kpi-mini-body.analytics-kpi-compare-scroll")
            ?.forEach((element) => {
                element.classList.remove("analytics-kpi-compare-scroll");
                delete element.dataset.initialKpiScrollApplied;
                element.scrollLeft = 0;
                element.scrollTop = 0;
            });
        cleanupRoot.querySelectorAll?.(".analytics-kpi-mini-body.analytics-kpi-compare-body-host")
            ?.forEach((element) => element.classList.remove("analytics-kpi-compare-body-host"));
        if (body) {
            body.classList.remove("analytics-kpi-compare-scroll", "analytics-kpi-compare-body-host");
            delete body.dataset.initialKpiScrollApplied;
            body.scrollLeft = 0;
            body.scrollTop = 0;
        }
        const scrollHost = resolveKpiMiniScrollHost(sourceWrap);
        if (scrollHost) {
            scrollHost.classList?.remove?.("analytics-kpi-compare-scroll", "analytics-kpi-compare-body-host");
            delete scrollHost.dataset.initialKpiScrollApplied;
            scrollHost.scrollLeft = 0;
            scrollHost.scrollTop = 0;
        }
        delete state.inlineCompareCanvasBySource[canvasId];
        state.inlineCompareEnabled[canvasId] = false;
    }

    function clearEventKpiMiniCompareState() {
        const canvasId = "chart-event-kpi";
        const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || "chart-event-kpi-compare-inline";
        disableInlineCompareLayout(canvasId);
        destroyRenderedChartOnly(compareCanvasId);
        delete state.chartConfigs[compareCanvasId];
        delete state.kpiFullChartConfigs[compareCanvasId];
        delete state.inlineCompareCanvasBySource[canvasId];
        state.inlineCompareEnabled[canvasId] = false;
        normalizeEventKpiMiniLayoutState();
    }

    function toggleStageMetricTopChart(isVisible) {
        const chartsGrid = document.getElementById("analytics-stage-metric-charts");
        const topCol = document.getElementById("analytics-stage-metric-top-col");
        if (!chartsGrid || !topCol) {
            return;
        }
        chartsGrid.classList.toggle("is-single", !isVisible);
        topCol.classList.toggle("is-hidden", !isVisible);
    }

    function baseChartOptions(yTitle) {
        const parseMaybeNumber = (value) => {
            if (value == null) {
                return null;
            }
            if (typeof value === "number") {
                return Number.isFinite(value) ? value : null;
            }
            if (typeof value === "object") {
                const yValue = parseMaybeNumber(value?.y);
                if (Number.isFinite(yValue)) {
                    return yValue;
                }
            }
            const rawText = String(value);
            const match = rawText.match(/-?\d+(?:[.,]\d+)?/);
            const text = (match ? match[0] : rawText).replace(/\s+/g, "").replace(",", ".");
            const parsed = Number(text);
            return Number.isFinite(parsed) ? parsed : null;
        };
        const resolveTooltipNumeric = (tooltipItem) => {
            const datasetValue = tooltipItem?.dataset?.data?.[tooltipItem?.dataIndex];
            const chartDatasetValue = tooltipItem?.chart?.data?.datasets?.[tooltipItem?.datasetIndex]?.data?.[tooltipItem?.dataIndex];
            const candidates = [
                tooltipItem?.parsed?.y,
                tooltipItem?.raw,
                datasetValue,
                chartDatasetValue,
                tooltipItem?.formattedValue
            ];
            return candidates
                .map((value) => parseMaybeNumber(value))
                .find((value) => Number.isFinite(value));
        };
        return {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            normalized: true,
            interaction: { intersect: false, mode: "index" },
            plugins: {
                legend: { labels: { color: "#334155" } },
                tooltip: {
                    mode: "index",
                    intersect: false,
                    filter: (tooltipItem) => {
                        const numeric = resolveTooltipNumeric(tooltipItem);
                        if (!Number.isFinite(numeric)) {
                            return false;
                        }
                        return Math.abs(numeric) > 0.0001;
                    },
                    callbacks: {
                        label: (tooltipItem) => {
                            const numeric = resolveTooltipNumeric(tooltipItem);
                            if (!Number.isFinite(numeric) || Math.abs(numeric) <= 0.0001) {
                                return null;
                            }
                            const label = tooltipItem?.dataset?.label || "";
                            const valueText = tooltipItem?.formattedValue ?? String(numeric);
                            return label ? `${label}: ${valueText}` : valueText;
                        }
                    }
                },
                decimation: {
                    enabled: true,
                    algorithm: "lttb",
                    samples: 120
                }
            },
            scales: {
                x: {
                    grid: { color: "rgba(148,163,184,0.12)" },
                    ticks: { color: "#64748b", autoSkip: true, maxTicksLimit: 8 }
                },
                y: {
                    grid: { color: "rgba(148,163,184,0.14)" },
                    ticks: { color: "#64748b" },
                    title: yTitle ? { display: true, text: yTitle, color: "#64748b" } : undefined
                }
            }
        };
    }

    function barChartOptions(yTitle) {
        const options = baseChartOptions(yTitle);
        options.interaction = {intersect: true, mode: "nearest"};
        options.plugins = options.plugins || {};
        options.plugins.tooltip = options.plugins.tooltip || {};
        options.plugins.tooltip.mode = "nearest";
        options.plugins.tooltip.intersect = true;
        return options;
    }

    async function fetchJson(url, options = {}) {
        const signal = options?.signal;
        const perfLabel = String(options?.perfLabel || "").trim();
        const perfStarted = typeof performance !== "undefined" ? performance.now() : Date.now();
        const response = await fetch(url, {
            headers: {
                "Accept": "application/json",
                "X-Requested-With": "XMLHttpRequest"
            },
            credentials: "same-origin",
            signal
        });
        const headersMs = Math.round((typeof performance !== "undefined" ? performance.now() : Date.now()) - perfStarted);
        const contentType = (response.headers.get("content-type") || "").toLowerCase();
        const expectsJson = contentType.includes("application/json");
        const redirectedToAdminLogin = response.redirected
            && String(response.url || "").includes("/analytics-admin/login");
        if (!response.ok) {
            const text = await response.text();
            let detail = text;
            if (expectsJson) {
                try {
                    const payload = JSON.parse(text);
                    detail = payload?.message || payload?.error || text;
                } catch (_ignored) {
                    detail = text;
                }
            } else if (redirectedToAdminLogin || (response.redirected && response.url.includes("/login"))) {
                detail = "Сессия истекла. Выполните вход снова.";
            } else {
                detail = text;
                detail = detail.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
            }
            if (!expectsJson) {
                detail = detail || "Сервер вернул HTML вместо JSON.";
            }
            if (redirectedToAdminLogin || /action="\/analytics-admin\/login"/i.test(text || "")) {
                redirectToAnalyticsLogin();
            }
            const compactDetail = detail.length > 260 ? `${detail.slice(0, 260)}...` : detail;
            throw new Error(`HTTP ${response.status}: ${compactDetail}`);
        }
        if (!expectsJson) {
            const body = await response.text();
            if (redirectedToAdminLogin
                || (response.redirected && response.url.includes("/login"))
                || /action="\/analytics-admin\/login"/i.test(body || "")) {
                redirectToAnalyticsLogin();
                throw new Error("Сессия истекла. Выполните вход снова.");
            }
            const compactBody = body
                .replace(/<[^>]+>/g, " ")
                .replace(/\s+/g, " ")
                .trim()
                .slice(0, 260);
            throw new Error(`Сервер вернул не JSON (${response.status}). ${compactBody || "Пустой ответ"}`);
        }
        const jsonStarted = typeof performance !== "undefined" ? performance.now() : Date.now();
        const payload = await response.json();
        const jsonMs = Math.round((typeof performance !== "undefined" ? performance.now() : Date.now()) - jsonStarted);
        if (perfLabel) {
            console.info("[UNIVERSAL_PERF] frontend fetchJson", {
                label: perfLabel,
                totalMs: Math.round((typeof performance !== "undefined" ? performance.now() : Date.now()) - perfStarted),
                waitMs: headersMs,
                jsonMs,
                status: response.status,
                bytes: Number(response.headers.get("content-length") || 0) || null,
                url: String(url || "").replace(window.location.origin, "")
            });
        }
        return payload;
    }

    function redirectToAnalyticsLogin() {
        if (state.authRedirectInProgress) {
            return;
        }
        state.authRedirectInProgress = true;
        const current = window.location.pathname + (window.location.search || "");
        const next = encodeURIComponent(current);
        window.location.assign(`/analytics-admin/login?expired=1&next=${next}`);
    }

    function clearDashboardDataStatus() {
        const host = refs.analyticsPage || document.querySelector(".analytics-page");
        if (!host) {
            return;
        }
        const existing = host.querySelector("#analytics-data-status");
        if (existing) {
            existing.remove();
        }
    }

    function showDashboardDataStatus(message, isError) {
        const host = refs.analyticsPage || document.querySelector(".analytics-page");
        if (!host) {
            return;
        }
        clearDashboardDataStatus();
        const status = document.createElement("div");
        status.id = "analytics-data-status";
        status.className = `alert ${isError ? "alert-danger" : "alert-warning"} mb-3`;
        status.setAttribute("role", "alert");
        status.textContent = String(message || "Не удалось загрузить данные.").trim();
        host.insertBefore(status, host.firstChild);
    }

    function isAbortError(error) {
        return !!(error && (error.name === "AbortError" || /aborted/i.test(String(error.message || ""))));
    }

    function isStaleStageMetricsRequest(requestId) {
        return Number.isFinite(Number(requestId))
            && Number(requestId) !== Number(state.stageMetricsRequestId || 0);
    }

    function ensureStageMetricsRequestId(requestId) {
        if (Number.isFinite(Number(requestId))) {
            return Number(requestId);
        }
        const nextRequestId = Number(state.stageMetricsRequestId || 0) + 1;
        state.stageMetricsRequestId = nextRequestId;
        return nextRequestId;
    }

    function isStaleUniversalRequest(requestId) {
        return Number.isFinite(Number(requestId))
            && Number(requestId) !== Number(state.universalRequestId || 0);
    }

    function nextMainReloadRequestId() {
        state.mainReloadRequestId = Number(state.mainReloadRequestId || 0) + 1;
        return state.mainReloadRequestId;
    }

    function isStaleMainReloadRequest(requestId) {
        return Number.isFinite(Number(requestId))
            && Number(requestId) !== Number(state.mainReloadRequestId || 0);
    }

    function readStageMetricSeriesCache(key) {
        if (!key) {
            return null;
        }
        const cache = state.stageMetricSeriesCache;
        if (!(cache instanceof Map)) {
            state.stageMetricSeriesCache = new Map();
            return null;
        }
        if (!cache.has(key)) {
            return null;
        }
        const value = cache.get(key);
        cache.delete(key);
        cache.set(key, value);
        return value;
    }

    function writeStageMetricSeriesCache(key, value) {
        if (!key) {
            return;
        }
        const cache = state.stageMetricSeriesCache instanceof Map
            ? state.stageMetricSeriesCache
            : new Map();
        state.stageMetricSeriesCache = cache;
        if (cache.has(key)) {
            cache.delete(key);
        }
        cache.set(key, value);
        if (cache.size <= STAGE_METRIC_SERIES_CACHE_LIMIT) {
            return;
        }
        const staleKey = cache.keys().next().value;
        if (staleKey != null) {
            cache.delete(staleKey);
        }
    }

    function clearStageMetricSeriesCache() {
        if (state.stageMetricSeriesCache instanceof Map) {
            state.stageMetricSeriesCache.clear();
            return;
        }
        state.stageMetricSeriesCache = new Map();
    }

    function nowMs() {
        return typeof performance !== "undefined" ? performance.now() : Date.now();
    }

    function currentStageMetricPerf() {
        if (!state.stageMetricPerfCurrent) {
            state.stageMetricPerfCurrent = {
                action: "unknown",
                startedAt: nowMs(),
                requestCount: 0,
                cacheHits: 0,
                cacheMisses: 0,
                inflightHits: 0,
                queryStrings: [],
                fetchTotalMs: 0,
                jsonMs: 0,
                buildMs: 0,
                renderMs: 0,
                numericRenderMs: 0,
                textRenderMs: 0,
                loaderStartedAt: null
            };
        }
        return state.stageMetricPerfCurrent;
    }

    function beginStageMetricPerf(action) {
        state.stageMetricPerfCurrent = {
            action: action || "unknown",
            startedAt: nowMs(),
            requestCount: 0,
            cacheHits: 0,
            cacheMisses: 0,
            inflightHits: 0,
            queryStrings: [],
            fetchTotalMs: 0,
            jsonMs: 0,
            buildMs: 0,
            renderMs: 0,
            numericRenderMs: 0,
            textRenderMs: 0,
            loaderStartedAt: nowMs()
        };
        return state.stageMetricPerfCurrent;
    }

    function finishStageMetricPerf(extra = {}) {
        const perf = state.stageMetricPerfCurrent;
        if (!perf) {
            return;
        }
        const totalMs = Math.round(nowMs() - perf.startedAt);
        const loaderHoldMs = perf.loaderStartedAt == null ? null : Math.round(nowMs() - perf.loaderStartedAt);
        console.info("[STAGE_METRICS_PERF] frontend load", {
            action: perf.action,
            requestCount: perf.requestCount,
            queryStrings: perf.queryStrings,
            cacheHits: perf.cacheHits,
            cacheMisses: perf.cacheMisses,
            inflightHits: perf.inflightHits,
            fetchTotalMs: Math.round(perf.fetchTotalMs),
            jsonMs: Math.round(perf.jsonMs),
            buildMs: Math.round(perf.buildMs),
            renderMs: Math.round(perf.renderMs),
            numericRenderMs: Math.round(perf.numericRenderMs),
            textRenderMs: Math.round(perf.textRenderMs),
            loaderHoldMs,
            totalMs,
            ...extra
        });
        state.stageMetricPerfCurrent = null;
    }

    function setStageMetricPerfAction(action) {
        state.stageMetricPendingPerfAction = action || "";
    }

    function resolveStageMetricPerfAction() {
        const action = state.stageMetricPendingPerfAction || "";
        state.stageMetricPendingPerfAction = "";
        if (action) {
            return action;
        }
        return state.stageMetricFilterKey ? "reload" : "initial_load";
    }

    function readStageMetricPayloadCache(key) {
        if (!key) {
            return null;
        }
        const cache = state.stageMetricPayloadCache instanceof Map
            ? state.stageMetricPayloadCache
            : new Map();
        state.stageMetricPayloadCache = cache;
        if (!cache.has(key)) {
            return null;
        }
        const value = cache.get(key);
        cache.delete(key);
        cache.set(key, value);
        return value;
    }

    function writeStageMetricPayloadCache(key, value) {
        if (!key) {
            return;
        }
        const cache = state.stageMetricPayloadCache instanceof Map
            ? state.stageMetricPayloadCache
            : new Map();
        state.stageMetricPayloadCache = cache;
        if (cache.has(key)) {
            cache.delete(key);
        }
        cache.set(key, value);
        while (cache.size > STAGE_METRIC_SERIES_CACHE_LIMIT) {
            const staleKey = cache.keys().next().value;
            if (staleKey == null) {
                break;
            }
            cache.delete(staleKey);
        }
    }

    function clearStageMetricPayloadCache() {
        if (state.stageMetricPayloadCache instanceof Map) {
            state.stageMetricPayloadCache.clear();
        } else {
            state.stageMetricPayloadCache = new Map();
        }
        if (state.stageMetricPayloadPromiseByKey instanceof Map) {
            state.stageMetricPayloadPromiseByKey.clear();
        } else {
            state.stageMetricPayloadPromiseByKey = new Map();
        }
    }

    function setPanelLoading(panelEl, isLoading) {
        if (!panelEl) {
            return;
        }
        const depthMap = state.panelLoadingDepthByElement;
        const prevDepth = depthMap.get(panelEl) || 0;
        const nextDepth = Math.max(0, prevDepth + (isLoading ? 1 : -1));
        depthMap.set(panelEl, nextDepth);

        let loader = panelEl.querySelector(".analytics-panel-loader");
        if (!loader) {
            loader = document.createElement("div");
            loader.className = "analytics-panel-loader";
            loader.innerHTML = `
            <div class="analytics-panel-loader-box">
                <div class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></div>
                <span>Применяем фильтр...</span>
            </div>
        `;
            panelEl.appendChild(loader);
        }
        loader.classList.toggle("is-visible", nextDepth > 0);
    }

    function clearPanelLoading(panelEl) {
        if (!panelEl) {
            return;
        }
        state.panelLoadingDepthByElement.set(panelEl, 0);
        panelEl.querySelector(".analytics-panel-loader")?.classList.remove("is-visible");
    }

    function calculateLocalBounds(hostEl, boundsEls) {
        const hostRect = hostEl?.getBoundingClientRect();
        if (!hostRect || hostRect.width <= 0 || hostRect.height <= 0) {
            return null;
        }
        const rects = uniqueElements(boundsEls)
            .map((item) => item?.getBoundingClientRect())
            .filter((rect) => rect && rect.width > 0 && rect.height > 0);
        if (!rects.length) {
            return {top: 0, left: 0, width: hostRect.width, height: hostRect.height};
        }
        const top = Math.min(...rects.map((rect) => rect.top));
        const left = Math.min(...rects.map((rect) => rect.left));
        const right = Math.max(...rects.map((rect) => rect.right));
        const bottom = Math.max(...rects.map((rect) => rect.bottom));
        return {
            top: Math.max(0, top - hostRect.top),
            left: Math.max(0, left - hostRect.left),
            width: Math.max(0, Math.min(hostRect.right, right) - Math.max(hostRect.left, left)),
            height: Math.max(0, Math.min(hostRect.bottom, bottom) - Math.max(hostRect.top, top))
        };
    }

    function updateSectionLocalLoadingBounds(hostEl, boundsEls) {
        const loader = Array.from(hostEl?.children || [])
            .find((child) => child.classList?.contains("analytics-section-local-loader"));
        if (!hostEl || !loader) {
            return;
        }
        const bounds = calculateLocalBounds(hostEl, boundsEls);
        if (!bounds || bounds.width <= 0 || bounds.height <= 0) {
            loader.style.inset = "0";
            loader.style.width = "";
            loader.style.height = "";
            return;
        }
        loader.style.inset = "auto";
        loader.style.top = `${Math.round(bounds.top)}px`;
        loader.style.left = `${Math.round(bounds.left)}px`;
        loader.style.width = `${Math.round(bounds.width)}px`;
        loader.style.height = `${Math.round(bounds.height)}px`;
    }

    function setSectionLocalLoading(hostEl, boundsEls, isLoading, message = "Применяем фильтр...") {
        if (!hostEl) {
            return;
        }
        const depthMap = state.sectionLocalLoadingDepthByElement;
        const prevDepth = depthMap.get(hostEl) || 0;
        const nextDepth = Math.max(0, prevDepth + (isLoading ? 1 : -1));
        depthMap.set(hostEl, nextDepth);

        let loader = Array.from(hostEl.children || [])
            .find((child) => child.classList?.contains("analytics-section-local-loader"));
        if (!loader) {
            loader = document.createElement("div");
            loader.className = "analytics-section-local-loader";
            loader.innerHTML = `
            <div class="analytics-panel-loader-box">
                <div class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></div>
                <span></span>
            </div>
        `;
            hostEl.appendChild(loader);
        }
        const messageEl = loader.querySelector("span");
        if (messageEl) {
            messageEl.textContent = message;
        }
        updateSectionLocalLoadingBounds(hostEl, boundsEls);
        loader.classList.toggle("is-visible", nextDepth > 0);
    }

    function nextFrame() {
        return new Promise((resolve) => requestAnimationFrame(() => resolve()));
    }

    async function waitForChartPaint(frames = 2) {
        const safeFrames = Math.max(1, Number(frames) || 1);
        for (let index = 0; index < safeFrames; index += 1) {
            await nextFrame();
        }
    }

    function beginEventKpiMiniRenderPass(loadId) {
        state.eventKpiMiniRenderPromise = new Promise((resolve) => {
            state.eventKpiMiniRenderResolve = resolve;
        });
        state.eventKpiMiniRenderLoadId = loadId;
    }

    function completeEventKpiMiniRenderPass(loadId) {
        if (Number(state.eventKpiMiniRenderLoadId || 0) !== Number(loadId || 0)) {
            return;
        }
        const resolve = state.eventKpiMiniRenderResolve;
        state.eventKpiMiniRenderPromise = null;
        state.eventKpiMiniRenderResolve = null;
        state.eventKpiMiniRenderLoadId = 0;
        state.eventKpiFirstLoadCompleted = true;
        if (typeof resolve === "function") {
            resolve();
        }
    }

    async function waitForEventKpiMiniRenderIfPending() {
        const pending = state.eventKpiMiniRenderPromise;
        if (pending && typeof pending.then === "function") {
            await pending;
            return true;
        }
        return false;
    }

    function uniqueElements(elements) {
        return Array.from(new Set((elements || []).filter(Boolean)));
    }

    function showScopedCardLoaders(scopeKey, hosts) {
        if (!scopeKey) {
            return 0;
        }
        const resolvedHosts = uniqueElements(hosts);
        const token = ((state.cardLoaderTokens?.[scopeKey] || 0) + 1);
        state.cardLoaderTokens = state.cardLoaderTokens || {};
        state.cardLoaderTokens[scopeKey] = token;
        if (!state.cardLoaderScopes) {
            state.cardLoaderScopes = {};
        }
        if (!state.cardLoaderHostsByScope) {
            state.cardLoaderHostsByScope = {};
        }
        const wasActive = !!state.cardLoaderScopes[scopeKey];
        const previousHosts = uniqueElements(state.cardLoaderHostsByScope[scopeKey] || []);
        state.cardLoaderScopes[scopeKey] = true;
        state.cardLoaderHostsByScope[scopeKey] = uniqueElements([...previousHosts, ...resolvedHosts]);
        if (!wasActive) {
            resolvedHosts.forEach((host) => setPanelLoading(host, true));
        } else {
            resolvedHosts
                .filter((host) => !previousHosts.includes(host))
                .forEach((host) => setPanelLoading(host, true));
        }
        return token;
    }

    function hideScopedCardLoaders(scopeKey, token) {
        if (!scopeKey) {
            return;
        }
        if (token && state.cardLoaderTokens?.[scopeKey] && state.cardLoaderTokens[scopeKey] !== token) {
            return;
        }
        if (!state.cardLoaderScopes) {
            state.cardLoaderScopes = {};
        }
        state.cardLoaderScopes[scopeKey] = false;
        const hosts = state.cardLoaderHostsByScope?.[scopeKey] || [];
        uniqueElements(hosts).forEach((host) => setPanelLoading(host, false));
        if (state.cardLoaderHostsByScope) {
            delete state.cardLoaderHostsByScope[scopeKey];
        }
    }

    function showScopedSectionLoader(scopeKey, hosts) {
        if (!scopeKey) {
            return 0;
        }
        const resolvedHosts = uniqueElements(hosts);
        if (!resolvedHosts.length) {
            return 0;
        }
        const token = ((state.sectionLoaderTokens?.[scopeKey] || 0) + 1);
        state.sectionLoaderTokens = state.sectionLoaderTokens || {};
        state.sectionLoaderTokens[scopeKey] = token;
        if (!state.sectionLoaderScopes) {
            state.sectionLoaderScopes = {};
        }
        if (!state.sectionLoaderHostsByScope) {
            state.sectionLoaderHostsByScope = {};
        }
        if (!state.sectionLoaderBoundsByScope) {
            state.sectionLoaderBoundsByScope = {};
        }
        const wasActive = !!state.sectionLoaderScopes[scopeKey];
        const previousHosts = uniqueElements(state.sectionLoaderHostsByScope[scopeKey] || []);
        state.sectionLoaderScopes[scopeKey] = true;
        state.sectionLoaderHostsByScope[scopeKey] = uniqueElements([...previousHosts, ...resolvedHosts]);
        state.sectionLoaderBoundsByScope[scopeKey] = new Map(
            state.sectionLoaderHostsByScope[scopeKey].map((host) => [host, [host]])
        );
        if (!wasActive) {
            resolvedHosts.forEach((host) => setSectionLocalLoading(host, [host], true));
        } else {
            resolvedHosts.forEach((host) => {
                if (!previousHosts.includes(host)) {
                    setSectionLocalLoading(host, [host], true);
                    return;
                }
                updateSectionLocalLoadingBounds(host, [host]);
            });
        }
        return token;
    }

    function hideScopedSectionLoader(scopeKey, token) {
        if (!scopeKey) {
            return;
        }
        if (token && state.sectionLoaderTokens?.[scopeKey] && state.sectionLoaderTokens[scopeKey] !== token) {
            return;
        }
        if (!state.sectionLoaderScopes) {
            state.sectionLoaderScopes = {};
        }
        state.sectionLoaderScopes[scopeKey] = false;
        const hosts = uniqueElements(state.sectionLoaderHostsByScope?.[scopeKey] || []);
        const boundsByHost = state.sectionLoaderBoundsByScope?.[scopeKey];
        hosts.forEach((host) => {
            const boundsEls = boundsByHost instanceof Map ? (boundsByHost.get(host) || [host]) : [host];
            setSectionLocalLoading(host, boundsEls, false);
        });
        if (state.sectionLoaderHostsByScope) {
            delete state.sectionLoaderHostsByScope[scopeKey];
        }
        if (state.sectionLoaderBoundsByScope) {
            delete state.sectionLoaderBoundsByScope[scopeKey];
        }
    }

    function getUniversalChartLoaderHosts() {
        return uniqueElements([
            refs.universalTimelineCard,
            refs.universalStagesCard,
            document.getElementById("analytics-universal-event-kpi-card")
        ]);
    }

    function showUniversalChartLoaders() {
        clearPanelLoading(refs.universalPanel);
        return showScopedSectionLoader("universal", getUniversalChartLoaderHosts());
    }

    function hideUniversalChartLoaders(token) {
        hideScopedSectionLoader("universal", token);
    }

    async function withUniversalChartLoaders(task, options = {}) {
        const action = options.action || "";
        const loaderStarted = performance.now();
        const token = showUniversalChartLoaders();
        await nextFrame();
        try {
            const result = await task();
            await waitForChartPaint(2);
            return result;
        } finally {
            hideUniversalChartLoaders(token);
            if (action) {
                console.info("[UNIVERSAL_PERF] frontend loader", {
                    action,
                    loaderHoldMs: Math.round(performance.now() - loaderStarted)
                });
            }
        }
    }

    function getStageMetricNumericLoaderHosts() {
        const chart = document.getElementById("chart-stage-metric-series");
        const host = chart?.closest(".analytics-stage-metric-chart-card")
            || chart?.closest(".analytics-stage-metric-block")
            || chart?.closest(".analytics-chart-wrap");
        return uniqueElements([host]);
    }

    function getStageMetricTextLoaderHosts() {
        const chart = document.getElementById("chart-stage-metric-text");
        const host = chart?.closest(".analytics-stage-metric-chart-card")
            || chart?.closest(".analytics-stage-metric-block")
            || chart?.closest(".analytics-chart-wrap");
        return uniqueElements([host]);
    }

    function resolveStageMetricLoaderHosts(scope) {
        const expandedHost = getOpenedExpandedMetricsHost();
        if (scope === "numeric") {
            return uniqueElements([
                ...getStageMetricNumericLoaderHosts(),
                expandedHost
            ]);
        }
        if (scope === "text") {
            return uniqueElements([
                ...getStageMetricTextLoaderHosts(),
                expandedHost
            ]);
        }
        return uniqueElements([
            ...getStageMetricNumericLoaderHosts(),
            ...getStageMetricTextLoaderHosts(),
            expandedHost
        ]);
    }

    function showStageMetricLoaders(scope = "all") {
        clearPanelLoading(refs.stageMetricPanel);
        return showScopedSectionLoader("stage-metrics", resolveStageMetricLoaderHosts(scope || "all"));
    }

    function hideStageMetricLoaders(scope = "all", token) {
        hideScopedSectionLoader("stage-metrics", token);
    }

    async function withStageMetricLoaders(scope, task) {
        const normalizedScope = scope || "all";
        const ownsPerf = normalizedScope !== "all" && !state.stageMetricPerfCurrent;
        if (ownsPerf) {
            beginStageMetricPerf(resolveStageMetricPerfAction());
        }
        const token = showStageMetricLoaders(normalizedScope);
        await nextFrame();
        try {
            const result = await task();
            await waitForChartPaint(2);
            return result;
        } finally {
            hideStageMetricLoaders(normalizedScope, token);
            if (ownsPerf && state.stageMetricPerfCurrent) {
                finishStageMetricPerf({scope: normalizedScope});
            }
        }
    }

    function setExpandedChartLoading(canvasId, isLoading) {
        const container = state.expandedChart.containerEl;
        if (!container || state.expandedChart.sourceCanvasId !== canvasId) {
            return;
        }
        let loader = container.querySelector(".analytics-expanded-loader");
        if (!loader) {
            loader = document.createElement("div");
            loader.className = "analytics-expanded-loader";
            loader.innerHTML = `
                <div class="analytics-panel-loader-box">
                    <div class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></div>
                    <span>Применяем фильтр...</span>
                </div>
            `;
            container.appendChild(loader);
        }
        loader.classList.toggle("is-visible", !!isLoading);
    }

    function setChartActionLoading(canvasId, isLoading) {
        const panelEl = findChartPanel(canvasId);
        setPanelLoading(panelEl, isLoading);
        setExpandedChartLoading(canvasId, isLoading);
    }

    function ensureGlobalLoader() {
        if (document.getElementById("analytics-global-loader")) {
            return;
        }
        const loader = document.createElement("div");
        loader.id = "analytics-global-loader";
        loader.className = "analytics-global-loader";
        loader.innerHTML = `
            <div class="analytics-global-loader-box">
                <div class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></div>
                <span>Применяем фильтр...</span>
            </div>
        `;
        document.body.appendChild(loader);
    }

    function setGlobalScreenLoading(isLoading) {
        state.globalLoadingDepth = Math.max(0, (state.globalLoadingDepth || 0) + (isLoading ? 1 : -1));
        const loader = document.getElementById("analytics-global-loader");
        if (!loader) {
            return;
        }
        loader.classList.toggle("is-visible", state.globalLoadingDepth > 0);
    }

    function setIfPresent(params, key, value) {
        if (value != null && value !== "") {
            params.set(key, value);
        }
    }

    function buildMainRangeKey() {
        return `${String(refs.from?.value || "").trim()}|${String(refs.to?.value || "").trim()}`;
    }

    function toIso(localValue) {
        if (!localValue) {
            return null;
        }
        const date = new Date(localValue);
        if (Number.isNaN(date.getTime())) {
            return null;
        }
        return date.toISOString();
    }

    function toDateTimeLocalString(date) {
        if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
            return "";
        }
        const pad = (value) => String(value).padStart(2, "0");
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function getAllTimeLocalRange() {
        if (state.allTimeRange?.from) {
            return {
                from: state.allTimeRange.from,
                to: toDateTimeLocalString(new Date())
            };
        }
        return {
            from: ALL_TIME_START_LOCAL,
            to: toDateTimeLocalString(new Date())
        };
    }

    function formatTime(value) {
        if (!value) {
            return "";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "";
        }
        return formatDateTimeAxisPattern(date);
    }

    function formatDateTime(value) {
        if (!value) {
            return "-";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "-";
        }
        return formatDateTimePattern(date);
    }

    function formatLogDateTime(value) {
        if (!value) {
            return "-";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "-";
        }
        return formatLogDateTimePattern(date);
    }

    function formatDateTimePattern(date) {
        const pad2 = (value) => String(value).padStart(2, "0");
        return `${pad2(date.getDate())}.${pad2(date.getMonth() + 1)}.${date.getFullYear()}, `
            + `${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`;
    }

    function formatComparePeriodTime(value) {
        const date = value instanceof Date ? value : new Date(value || "");
        if (Number.isNaN(date.getTime())) {
            return "-";
        }
        const pad2 = (item) => String(item).padStart(2, "0");
        return `${pad2(date.getDate())}.${pad2(date.getMonth() + 1)}.${date.getFullYear()} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
    }

    function formatComparePeriodRange(fromValue, toValue) {
        return `${formatComparePeriodTime(fromValue)} — ${formatComparePeriodTime(toValue)}`;
    }

    function syncComparePeriodSummary(summaryEl, mode, ranges, options = {}) {
        if (!summaryEl) {
            return;
        }
        const enabled = normalizeCompareMode(mode) !== "off";
        summaryEl.classList.toggle("d-none", !enabled);
        if (!enabled) {
            summaryEl.textContent = "";
            return;
        }
        const prefix = options.overlayText && normalizeCompareMode(mode) === "overlay"
            ? options.overlayText
            : (options.prefix || "Период «До»");
        const beforeLabel = formatComparePeriodRange(ranges?.beforeFrom, ranges?.beforeTo);
        summaryEl.textContent = `${prefix}: ${beforeLabel}`;
    }

    function formatLogDateTimePattern(date) {
        const pad2 = (value) => String(value).padStart(2, "0");
        const pad3 = (value) => String(value).padStart(3, "0");
        return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} `
            + `${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}.${pad3(date.getMilliseconds())}`;
    }

    function formatDateTimeAxisPattern(date) {
        const pad2 = (value) => String(value).padStart(2, "0");
        const year2 = String(date.getFullYear()).slice(-2);
        return `${pad2(date.getDate())}.${pad2(date.getMonth() + 1)}.${year2} `
            + `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
    }

    function isValidInlineCompareMode(mode) {
        return ["off", "split", "overlay"].includes(String(mode || "").trim().toLowerCase());
    }

    function resolveGlobalCompareModeFromUi() {
        if (refs.globalCompareModeOverlay?.checked) {
            return "overlay";
        }
        if (refs.globalCompareModeSplit?.checked) {
            return "split";
        }
        return "off";
    }

    function setGlobalCompareMode(modeRaw) {
        const mode = isValidInlineCompareMode(modeRaw) ? String(modeRaw).trim().toLowerCase() : "off";
        state.globalCompareMode = mode;
        state.globalCompareEnabled = mode !== "off";
        if (refs.globalCompareModeOff) {
            refs.globalCompareModeOff.checked = mode === "off";
        }
        if (refs.globalCompareModeSplit) {
            refs.globalCompareModeSplit.checked = mode === "split";
        }
        if (refs.globalCompareModeOverlay) {
            refs.globalCompareModeOverlay.checked = mode === "overlay";
        }
    }

    function resolveGlobalInlineCompareMode() {
        const mode = String(state.globalCompareMode || "").trim().toLowerCase();
        if (isValidInlineCompareMode(mode)) {
            return mode;
        }
        if (!state.globalCompareEnabled) {
            return "off";
        }
        return "split";
    }

    function resolveInlineCompareMode(canvasId) {
        const existing = String(state.inlineCompareModeBySource?.[canvasId] || "").trim().toLowerCase();
        const isOverridden = !!state.inlineCompareModeOverriddenBySource?.[canvasId];
        if (isOverridden && isValidInlineCompareMode(existing)) {
            return existing;
        }
        const inherited = resolveGlobalInlineCompareMode();
        state.inlineCompareModeBySource[canvasId] = inherited;
        state.inlineCompareModeOverriddenBySource[canvasId] = false;
        return inherited;
    }

    function resolveEventKpiCompareModeRaw() {
        return resolveInlineCompareMode("chart-event-kpi");
    }

    function setEventKpiCompareModeRaw(modeRaw, override = true) {
        const mode = isValidInlineCompareMode(modeRaw) ? String(modeRaw).trim().toLowerCase() : resolveGlobalInlineCompareMode();
        state.inlineCompareModeBySource["chart-event-kpi"] = mode;
        state.inlineCompareModeOverriddenBySource["chart-event-kpi"] = override === true;
        state.inlineCompareGhostBySource["chart-event-kpi"] = mode === "overlay";
        state.miniKpiCompareModeOverriddenBySource["chart-event-kpi"] = false;
        delete state.miniKpiCompareModeBySource["chart-event-kpi"];
        state.expandedCompareModeOverriddenBySource["chart-event-kpi"] = false;
        delete state.expandedCompareModeBySource["chart-event-kpi"];
        clearEventKpiMiniCompareState();
        return mode;
    }

    function resolveMiniKpiCompareMode(canvasId) {
        const sourceCanvasId = resolveKpiCompareSourceCanvasId(canvasId) || canvasId;
        const mode = sourceCanvasId === "chart-event-kpi"
            ? resolveEventKpiCompareModeRaw()
            : resolveInlineCompareMode(sourceCanvasId);
        if (sourceCanvasId === "chart-event-kpi" && mode === "split") {
            return "overlay";
        }
        return mode;
    }

    function resolveMiniKpiDisplayCompareMode(canvasId) {
        const sourceCanvasId = resolveKpiCompareSourceCanvasId(canvasId) || canvasId;
        if (sourceCanvasId === "chart-event-kpi") {
            return resolveEventKpiCompareModeRaw();
        }
        return resolveInlineCompareMode(sourceCanvasId);
    }

    function resolveExpandedCompareMode(canvasId) {
        if (canvasId === "chart-event-kpi") {
            return resolveEventKpiCompareModeRaw();
        }
        return resolveInlineCompareMode(canvasId);
    }

    function isMiniKpiSplitModeOptionDisabled(canvasId, mode) {
        const sourceCanvasId = resolveKpiCompareSourceCanvasId(canvasId) || canvasId;
        return sourceCanvasId === "chart-event-kpi" && String(mode || "").trim().toLowerCase() === "split";
    }

    function miniKpiSplitDisabledTitle() {
        return "В mini-версии KPI доступно только наложение. Раздельный режим доступен в увеличенном графике";
    }

    function compareModeLabel(mode) {
        switch (String(mode || "").trim().toLowerCase()) {
            case "split":
                return "Раздельно";
            case "overlay":
                return "Наложением";
            default:
                return "Выключено";
        }
    }

    function compareModeIcon(mode) {
        switch (String(mode || "").trim().toLowerCase()) {
            case "split":
                return "bi-layout-split";
            case "overlay":
                return "bi-layers";
            default:
                return "bi-slash-circle";
        }
    }

    function syncInlineCompareModeSelectValues(canvasId) {
        const value = resolveKpiCompareSourceCanvasId(canvasId)
            ? resolveMiniKpiDisplayCompareMode(canvasId)
            : resolveInlineCompareMode(canvasId);
        const miniSelect = refs.analyticsPage?.querySelector(`[data-inline-compare-mode='${canvasId}']`);
        if (miniSelect) {
            miniSelect.value = value;
            Array.from(miniSelect.options || []).forEach((option) => {
                option.disabled = isMiniKpiSplitModeOptionDisabled(canvasId, option.value);
                if (option.disabled) {
                    option.title = miniKpiSplitDisabledTitle();
                } else {
                    option.removeAttribute("title");
                }
            });
        }
        const miniTrigger = refs.analyticsPage?.querySelector(`[data-inline-compare-mode-trigger='${canvasId}']`);
        if (miniTrigger) {
            miniTrigger.title = `Режим сравнения: ${compareModeLabel(value)}`;
            miniTrigger.setAttribute("aria-label", `Режим сравнения: ${compareModeLabel(value)}`);
            miniTrigger.innerHTML = `<i class="bi ${compareModeIcon(value)}"></i>`;
        }
        refs.analyticsPage?.querySelectorAll(`[data-inline-compare-mode-option='${canvasId}']`)
            ?.forEach((item) => {
                const mode = item.getAttribute("data-mode") || "off";
                const disabled = isMiniKpiSplitModeOptionDisabled(canvasId, mode);
                const selected = mode === value;
                item.classList.toggle("active", selected);
                item.classList.toggle("disabled", disabled);
                item.disabled = disabled;
                item.setAttribute("aria-selected", selected ? "true" : "false");
                item.setAttribute("aria-disabled", disabled ? "true" : "false");
                if (disabled) {
                    item.title = miniKpiSplitDisabledTitle();
                } else {
                    item.removeAttribute("title");
                }
                const label = compareModeLabel(mode);
                item.textContent = `${selected ? "вњ“ " : ""}${label}`;
            });
        if (state.expandedChart.sourceCanvasId === canvasId && state.expandedChart.containerEl) {
            const expanded = state.expandedChart.containerEl.querySelector("[data-expanded-compare-mode]");
            if (expanded) {
                const expandedValue = resolveExpandedCompareMode(canvasId);
                expanded.innerHTML = INLINE_COMPARE_MODE_OPTIONS
                    .map((item) => `<option value="${item.value}" ${item.value === expandedValue ? "selected" : ""}>${item.value === expandedValue ? "вњ“ " : ""}${item.label}</option>`)
                    .join("");
                expanded.value = expandedValue;
            }
        }
    }

    function syncInlineCompareModeResetVisibility(canvasId) {
        refs.analyticsPage?.querySelectorAll(`[data-inline-compare-reset='${canvasId}']`)
            ?.forEach((button) => {
                button.classList.toggle("d-none", !hasChartLocalOverride(canvasId));
            });
        if (state.expandedChart.sourceCanvasId === canvasId && state.expandedChart.containerEl) {
            const expandedReset = state.expandedChart.containerEl.querySelector("[data-expanded-reset]");
            expandedReset?.classList.toggle("d-none", !hasChartLocalOverride(canvasId));
        }
    }

    function chartRangesEqual(left, right) {
        return String(left?.beforeFrom || "") === String(right?.beforeFrom || "")
            && String(left?.beforeTo || "") === String(right?.beforeTo || "")
            && String(left?.afterFrom || "") === String(right?.afterFrom || "")
            && String(left?.afterTo || "") === String(right?.afterTo || "");
    }

    function hasChartLocalRangeOverride(canvasId) {
        const localRanges = state.expandedRangesBySource?.[canvasId];
        if (!localRanges?.afterFrom || !localRanges?.afterTo) {
            return false;
        }
        const inherited = expandedRangesFromTopFilter(canvasId);
        return !chartRangesEqual(
            normalizeCompareRangesByAfter(localRanges.afterFrom, localRanges.afterTo, "", ""),
            inherited
        );
    }

    function hasChartLocalOverride(canvasId) {
        if (!canvasId) {
            return false;
        }
        const scenarioSourceId = resolveChartScenarioSourceCanvasId(canvasId);
        if (!!state.scenarioOverriddenBySource?.[scenarioSourceId]) {
            return true;
        }
        const localMode = String(state.inlineCompareModeBySource?.[canvasId] || "").trim().toLowerCase();
        if (!!state.inlineCompareModeOverriddenBySource?.[canvasId]
            && isValidInlineCompareMode(localMode)
            && localMode !== resolveGlobalInlineCompareMode()) {
            return true;
        }
        const miniMode = String(state.miniKpiCompareModeBySource?.[canvasId] || "").trim().toLowerCase();
        if (canvasId === "chart-event-kpi"
            && !!state.miniKpiCompareModeOverriddenBySource?.[canvasId]
            && isValidInlineCompareMode(miniMode)
            && miniMode !== resolveGlobalInlineCompareMode()) {
            return true;
        }
        const expandedMode = String(state.expandedCompareModeBySource?.[canvasId] || "").trim().toLowerCase();
        if (canvasId === "chart-event-kpi"
            && !!state.expandedCompareModeOverriddenBySource?.[canvasId]
            && isValidInlineCompareMode(expandedMode)
            && expandedMode !== resolveGlobalInlineCompareMode()) {
            return true;
        }
        const localPreset = String(state.inlineComparePresetBySource?.[canvasId] || "").trim().toLowerCase();
        if (!!state.inlineComparePresetOverriddenBySource?.[canvasId]
            && localPreset
            && localPreset !== resolveTopInlineComparePresetOrDefault()) {
            return true;
        }
        const localBucket = String(state.expandedBucketBySource?.[canvasId] || "").trim();
        if (localBucket && localBucket !== String(refs.bucket?.value || "").trim()) {
            return true;
        }
        if (hasChartLocalRangeOverride(canvasId)) {
            return true;
        }
        return false;
    }

    function syncAllChartResetButtons() {
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => syncInlineCompareModeResetVisibility(canvasId));
    }

    async function resetChartLocalOverride(canvasId, options = {}) {
        if (!canvasId) {
            return;
        }
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        const wasExpanded = state.expandedChart.sourceCanvasId === canvasId;
        resetScenarioForSource(sourceCanvasId);
        rerenderScenarioChart(sourceCanvasId);
        refreshAllChartScenarioPickers();
        syncChartScenarioSummary(sourceCanvasId);
        if (!INLINE_COMPARE_CHART_IDS.has(canvasId)) {
            return;
        }
        state.inlineCompareModeOverriddenBySource[canvasId] = false;
        state.inlineCompareModeBySource[canvasId] = resolveGlobalInlineCompareMode();
        state.miniKpiCompareModeOverriddenBySource[canvasId] = false;
        delete state.miniKpiCompareModeBySource[canvasId];
        state.expandedCompareModeOverriddenBySource[canvasId] = false;
        delete state.expandedCompareModeBySource[canvasId];
        state.inlineComparePresetOverriddenBySource[canvasId] = false;
        state.inlineComparePresetBySource[canvasId] = resolveTopInlineComparePresetOrDefault();
        delete state.expandedRangesBySource[canvasId];
        delete state.expandedBucketBySource[canvasId];
        const mode = resolveInlineCompareMode(canvasId);
        const shouldSplit = mode === "split";
        if (canvasId === "chart-event-kpi") {
            clearEventKpiMiniCompareState();
        } else if (!!state.inlineCompareEnabled[canvasId] !== shouldSplit) {
            if (shouldSplit) {
                normalizeStoredRangeForCompare(canvasId);
                enableInlineCompareLayout(canvasId);
            } else {
                disableInlineCompareLayout(canvasId);
            }
            state.inlineCompareEnabled[canvasId] = shouldSplit;
        }
        state.inlineCompareGhostBySource[canvasId] = mode === "overlay";
        syncInlineCompareModeSelectValues(canvasId);
        syncInlineCompareModeResetVisibility(canvasId);
        await applyInlineComparePresetToChart(canvasId);
        await applyStoredExpandedRangesToCharts(canvasId);
        if (wasExpanded) {
            const rebuilt = rebuildExpandedChartForCurrentMode(canvasId);
            if (options.syncControls !== false) {
                await syncExpandedGraphFiltersFromTop();
            } else if (!rebuilt) {
                renderExpandedChartClone(canvasId);
            }
        }
        if (options.syncControls !== false) {
            syncInlineCompareModeResetVisibility(canvasId);
        }
    }

    function clearAllChartLocalOverrides() {
        resetAllScenariosToGlobal();
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            state.inlineCompareModeOverriddenBySource[canvasId] = false;
            state.inlineCompareModeBySource[canvasId] = resolveGlobalInlineCompareMode();
            state.miniKpiCompareModeOverriddenBySource[canvasId] = false;
            delete state.miniKpiCompareModeBySource[canvasId];
            state.expandedCompareModeOverriddenBySource[canvasId] = false;
            delete state.expandedCompareModeBySource[canvasId];
            state.inlineComparePresetOverriddenBySource[canvasId] = false;
            state.inlineComparePresetBySource[canvasId] = resolveTopInlineComparePresetOrDefault();
            delete state.expandedRangesBySource[canvasId];
            delete state.expandedBucketBySource[canvasId];
        });
        syncAllChartResetButtons();
    }

    function initChartExpandUi() {
        if (!refs.analyticsPage) {
            return;
        }
        const canvases = refs.analyticsPage.querySelectorAll("canvas[id^='chart-']");
        canvases.forEach((canvas) => {
            if (isStageMetricCompareCanvas(canvas.id)) {
                return;
            }
            const wrap = canvas.closest(".analytics-chart-wrap");
            if (!wrap) {
                return;
            }
            const actions = ensureChartActionsBar(wrap);
            if (INLINE_COMPARE_CHART_IDS.has(canvas.id)) {
                ensureInlineCompareModeControl(actions, canvas.id);
                ensureInlineComparePresetControl(actions, canvas.id);
            }
            ensureChartScenarioPicker(actions, canvas.id);
            if (actions.querySelector(`[data-chart-expand='${canvas.id}']`)) {
                return;
            }
            if (NO_EXPAND_CHART_IDS.has(canvas.id)) {
                return;
            }
            const button = document.createElement("button");
            button.type = "button";
            button.className = "btn btn-outline-dark analytics-chart-icon-btn analytics-chart-expand-btn";
            button.setAttribute("data-chart-expand", canvas.id);
            button.title = "Увеличить график";
            button.setAttribute("aria-label", "Увеличить график");
            button.innerHTML = '<i class="bi bi-search"></i>';
            button.addEventListener("click", () => {
                toggleExpandedChart(canvas.id, button);
            });
            actions.appendChild(button);
        });
    }

    async function toggleInlineCompareChart(canvasId, button) {
        if (canvasId === "chart-event-kpi") {
            const nextMode = resolveMiniKpiCompareMode(canvasId) === "off" ? "overlay" : "off";
            await applyMiniKpiCompareMode(nextMode, {
                source: "mini-compare-toggle",
                explicitUserChoice: true
            });
            button?.blur();
            return;
        }
        captureExpandedRangesFromUi(canvasId);
        setChartActionLoading(canvasId, true);
        const wasExpanded = state.expandedChart.sourceCanvasId === canvasId;
        const currentlyEnabled = !!state.inlineCompareEnabled[canvasId];
        if (currentlyEnabled) {
            disableInlineCompareLayout(canvasId);
            state.inlineCompareEnabled[canvasId] = false;
        } else {
            normalizeStoredRangeForCompare(canvasId);
            resolveInlineComparePreset(canvasId);
            enableInlineCompareLayout(canvasId);
            state.inlineCompareEnabled[canvasId] = true;
        }
        if (wasExpanded) {
            collapseExpandedChart();
            toggleExpandedChart(canvasId);
            // Expanded container is recreated above, so re-attach visible loader for this long-running update path.
            setExpandedChartLoading(canvasId, true);
        }
        updateCompareButtonsState();
        try {
            await applyInlineComparePresetToChart(canvasId);
            await applyStoredExpandedRangesToCharts(canvasId);
            if (state.expandedChart.sourceCanvasId === canvasId) {
                renderExpandedChartClone(canvasId);
            }
        } catch (error) {
            console.error("Inline compare reload failed", error);
        } finally {
            setChartActionLoading(canvasId, false);
        }
        if (button) {
            button.blur();
        }
    }

    async function applyInlineCompareMode(canvasId, modeRaw, options = {}) {
        if (!INLINE_COMPARE_CHART_IDS.has(canvasId)) {
            return;
        }
        const mode = isValidInlineCompareMode(modeRaw) ? String(modeRaw).trim().toLowerCase() : resolveGlobalInlineCompareMode();
        const override = options.override === true;
        if (canvasId === "chart-event-kpi") {
            captureExpandedRangesFromUi(canvasId);
            setEventKpiCompareModeRaw(mode, override);
            setChartActionLoading(canvasId, true);
            try {
                await loadOverview();
                syncInlineCompareModeSelectValues(canvasId);
                syncInlineCompareModeResetVisibility(canvasId);
                if (state.expandedChart.sourceCanvasId === canvasId) {
                    rebuildExpandedChartForCurrentMode(canvasId);
                }
            } catch (error) {
                console.error("Inline compare mode apply failed", error);
            } finally {
                setChartActionLoading(canvasId, false);
            }
            return;
        }
        state.inlineCompareModeBySource[canvasId] = mode;
        state.inlineCompareModeOverriddenBySource[canvasId] = override;
        captureExpandedRangesFromUi(canvasId);
        setChartActionLoading(canvasId, true);
        const wasExpanded = state.expandedChart.sourceCanvasId === canvasId;
        const shouldEnable = mode === "split";
        const currentlyEnabled = !!state.inlineCompareEnabled[canvasId];
        if (currentlyEnabled !== shouldEnable) {
            if (shouldEnable) {
                normalizeStoredRangeForCompare(canvasId);
                resolveInlineComparePreset(canvasId);
                enableInlineCompareLayout(canvasId);
            } else {
                disableInlineCompareLayout(canvasId);
            }
            state.inlineCompareEnabled[canvasId] = shouldEnable;
        }
        state.inlineCompareGhostBySource[canvasId] = mode === "overlay";
        if (wasExpanded) {
            collapseExpandedChart();
            toggleExpandedChart(canvasId);
            setExpandedChartLoading(canvasId, true);
        }
        syncInlineCompareModeSelectValues(canvasId);
        syncInlineCompareModeResetVisibility(canvasId);
        updateCompareButtonsState();
        try {
            await applyInlineComparePresetToChart(canvasId);
            await applyStoredExpandedRangesToCharts(canvasId);
            if (state.expandedChart.sourceCanvasId === canvasId) {
                renderExpandedChartClone(canvasId);
            }
        } catch (error) {
            console.error("Inline compare mode apply failed", error);
        } finally {
            setChartActionLoading(canvasId, false);
        }
    }

    async function applyMiniKpiCompareMode(modeRaw, options = {}) {
        const canvasId = "chart-event-kpi";
        const mode = isValidInlineCompareMode(modeRaw) ? String(modeRaw).trim().toLowerCase() : resolveGlobalInlineCompareMode();
        const explicitUserChoice = options.explicitUserChoice === true;
        if (mode === "split") {
            return;
        }
        if (mode === "off" && !explicitUserChoice) {
            return;
        }
        setEventKpiCompareModeRaw(mode, true);
        setChartActionLoading(canvasId, true);
        try {
            await loadOverview();
            syncInlineCompareModeSelectValues(canvasId);
            syncInlineCompareModeResetVisibility(canvasId);
            if (state.expandedChart.sourceCanvasId === canvasId) {
                rebuildExpandedChartForCurrentMode(canvasId);
            }
        } catch (error) {
            console.error("Mini KPI compare mode apply failed", error);
        } finally {
            setChartActionLoading(canvasId, false);
        }
    }

    function enableInlineCompareLayout(canvasId) {
        const sourceCanvas = document.getElementById(canvasId);
        const sourceWrap = sourceCanvas?.closest(".analytics-chart-wrap");
        if (!sourceCanvas || !sourceWrap || !sourceWrap.parentElement) {
            return;
        }
        const isEventKpiCompare = canvasId === "chart-event-kpi";
        const existingPair = refs.analyticsPage?.querySelector(`.analytics-chart-compare-pair[data-chart-id='${canvasId}']`);
        if (isEventKpiCompare) {
            if (existingPair) {
                const sourceWrapFromPair = existingPair.querySelector(".analytics-chart-wrap:not(.analytics-chart-wrap-compare)");
                const restoreParent = existingPair.parentElement;
                if (sourceWrapFromPair && restoreParent) {
                    restoreParent.insertBefore(sourceWrapFromPair, existingPair);
                    restoreParent.classList.remove("analytics-kpi-compare-body-host");
                }
                existingPair.remove();
            }
            state.inlineCompareCanvasBySource[canvasId] = `${canvasId}-compare-inline`;
            return;
        }
        if (existingPair) {
            return;
        }
        const parent = sourceWrap.parentElement;
        const pair = document.createElement("div");
        pair.className = "analytics-chart-compare-pair";
        pair.setAttribute("data-chart-id", canvasId);
        if (isEventKpiCompare) {
            pair.classList.add("analytics-kpi-compare-pair");
        }

        const compareWrap = document.createElement("div");
        compareWrap.className = sourceWrap.className;
        compareWrap.classList.add("analytics-chart-wrap-compare");
        const compareCanvas = document.createElement("canvas");
        const compareCanvasId = `${canvasId}-compare-inline`;
        compareCanvas.id = compareCanvasId;
        compareWrap.appendChild(compareCanvas);

        if (isEventKpiCompare) {
            parent.classList.add("analytics-kpi-compare-body-host");
            parent.insertBefore(pair, sourceWrap);
            const compareBody = document.createElement("div");
            compareBody.className = "analytics-kpi-mini-body analytics-kpi-compare-scroll";
            const comparePanel = document.createElement("div");
            comparePanel.className = "analytics-kpi-compare-panel";
            const compareLabel = document.createElement("div");
            compareLabel.className = "analytics-kpi-compare-panel-label";
            compareLabel.textContent = "\u0414\u043e";
            const sourceBody = document.createElement("div");
            sourceBody.className = "analytics-kpi-mini-body analytics-kpi-compare-scroll";
            const sourcePanel = document.createElement("div");
            sourcePanel.className = "analytics-kpi-compare-panel";
            const sourceLabel = document.createElement("div");
            sourceLabel.className = "analytics-kpi-compare-panel-label";
            sourceLabel.textContent = "\u041f\u043e\u0441\u043b\u0435";
            compareBody.appendChild(compareWrap);
            comparePanel.appendChild(compareLabel);
            comparePanel.appendChild(compareBody);
            sourceBody.appendChild(sourceWrap);
            sourcePanel.appendChild(sourceLabel);
            sourcePanel.appendChild(sourceBody);
            pair.appendChild(comparePanel);
            pair.appendChild(sourcePanel);
        } else {
            parent.insertBefore(pair, sourceWrap);
            pair.appendChild(compareWrap);
            pair.appendChild(sourceWrap);
        }
        state.inlineCompareCanvasBySource[canvasId] = compareCanvasId;
        bindInlineMiniCompareScrollSync(canvasId);
        bindUniversalCompareScrollSync(canvasId);
    }

    function disableInlineCompareLayout(canvasId) {
        const pair = refs.analyticsPage?.querySelector(`.analytics-chart-compare-pair[data-chart-id='${canvasId}']`);
        const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
        if (pair) {
            const sourceWrap = pair.querySelector(".analytics-chart-wrap:not(.analytics-chart-wrap-compare)");
            const restoreParent = pair.parentElement;
            if (sourceWrap && restoreParent) {
                restoreParent.insertBefore(sourceWrap, pair);
            }
            if (canvasId === "chart-event-kpi") {
                restoreParent?.classList?.remove("analytics-kpi-compare-body-host");
            }
            pair.remove();
        }
        destroyChart(compareCanvasId);
        delete state.inlineCompareCanvasBySource[canvasId];
        if (canvasId === "chart-event-kpi") {
            refreshEventKpiMiniTopHint();
        }
    }

    function toggleExpandedChart(canvasId, button) {
        if (state.expandedChart.sourceCanvasId === canvasId) {
            collapseExpandedChart();
            return;
        }
        collapseExpandedChart();

        const canvas = document.getElementById(canvasId);
        const wrap = canvas?.closest(".analytics-chart-wrap");
        if (!canvas || !wrap) {
            return;
        }
        wrap.classList.add("is-expanded-source");
        const container = document.createElement("div");
        container.className = "analytics-chart-wrap analytics-chart-wrap-expanded analytics-chart-wrap-inline analytics-expanded-block mt-2";
        container.setAttribute("data-expanded-for", canvasId);
        const closeButton = document.createElement("button");
        closeButton.type = "button";
        closeButton.className = "btn btn-dark analytics-chart-icon-btn analytics-expanded-close-btn";
        closeButton.setAttribute("title", "Закрыть разворот");
        closeButton.setAttribute("aria-label", "Закрыть разворот");
        closeButton.innerHTML = '<i class="bi bi-x-lg"></i>';
        closeButton.addEventListener("click", () => {
            collapseExpandedChart();
        });
        container.appendChild(closeButton);
        const metricCompareCanvasId = getStageMetricExpandedCompareCanvasId(canvasId);
        const isKpiExpandedSplit = canvasId === "chart-event-kpi" && resolveExpandedCompareMode(canvasId) === "split";
        const compareCanvasId = state.inlineCompareCanvasBySource[canvasId]
            || (isKpiExpandedSplit ? "chart-event-kpi-compare-inline" : "")
            || metricCompareCanvasId
            || "";
        const expandComparePair = isKpiExpandedSplit
            || (!!state.inlineCompareEnabled[canvasId] && !!compareCanvasId)
            || !!metricCompareCanvasId;
        if (expandComparePair) {
            container.classList.add("analytics-expanded-compare-host");
            const pair = document.createElement("div");
            pair.className = "analytics-chart-compare-pair analytics-expanded-compare-pair";

            const leftScroll = document.createElement("div");
            leftScroll.className = "analytics-expanded-scroll";
            leftScroll.setAttribute("data-expanded-scroll", "left");
            const leftZoomHost = document.createElement("div");
            leftZoomHost.className = "analytics-expanded-zoom-host";
            const leftWrap = document.createElement("div");
            leftWrap.className = "analytics-chart-wrap analytics-chart-wrap-inline analytics-chart-wrap-expanded";
            if (canvasId === "chart-event-kpi") {
                leftWrap.classList.add("analytics-chart-wrap-kpi-label-scroll");
            }
            const leftCanvas = document.createElement("canvas");
            leftCanvas.id = `chart-expanded-${canvasId}-compare`;
            leftCanvas.setAttribute("data-expanded-compare-for", canvasId);
            leftWrap.appendChild(leftCanvas);
            leftZoomHost.appendChild(leftWrap);
            if (metricCompareCanvasId) {
                const leftLabel = document.createElement("div");
                leftLabel.className = "small text-muted mb-1";
                leftLabel.textContent = "До";
                leftScroll.appendChild(leftLabel);
            }
            leftScroll.appendChild(leftZoomHost);

            const rightScroll = document.createElement("div");
            rightScroll.className = "analytics-expanded-scroll";
            rightScroll.setAttribute("data-expanded-scroll", "right");
            const rightZoomHost = document.createElement("div");
            rightZoomHost.className = "analytics-expanded-zoom-host";
            const rightWrap = document.createElement("div");
            rightWrap.className = "analytics-chart-wrap analytics-chart-wrap-inline analytics-chart-wrap-expanded";
            if (canvasId === "chart-event-kpi") {
                rightWrap.classList.add("analytics-chart-wrap-kpi-label-scroll");
            }
            const rightCanvas = document.createElement("canvas");
            rightCanvas.id = `chart-expanded-${canvasId}`;
            rightWrap.appendChild(rightCanvas);
            rightZoomHost.appendChild(rightWrap);
            if (metricCompareCanvasId) {
                const rightLabel = document.createElement("div");
                rightLabel.className = "small text-muted mb-1";
                rightLabel.textContent = "После";
                rightScroll.appendChild(rightLabel);
            }
            rightScroll.appendChild(rightZoomHost);

            pair.appendChild(leftScroll);
            pair.appendChild(rightScroll);
            if (canvasId === "chart-event-kpi") {
                pair.classList.add("analytics-expanded-kpi-compare-pair");
            }
            container.appendChild(pair);
        } else {
            const singleScroll = document.createElement("div");
            singleScroll.className = "analytics-expanded-scroll";
            singleScroll.setAttribute("data-expanded-scroll", "single");
            const singleZoomHost = document.createElement("div");
            singleZoomHost.className = "analytics-expanded-zoom-host";
            const expandedCanvas = document.createElement("canvas");
            expandedCanvas.id = `chart-expanded-${canvasId}`;
            const singleWrap = document.createElement("div");
            singleWrap.className = "analytics-chart-wrap analytics-chart-wrap-inline analytics-chart-wrap-expanded";
            if (canvasId === "chart-event-kpi") {
                singleWrap.classList.add("analytics-chart-wrap-kpi-label-scroll");
            }
            singleWrap.appendChild(expandedCanvas);
            singleZoomHost.appendChild(singleWrap);
            singleScroll.appendChild(singleZoomHost);
            container.appendChild(singleScroll);
        }

        let anchor = wrap.closest(".glass-card.analytics-panel") || wrap.closest(".analytics-grid > *") || wrap;
        const isUniversalChart = UNIVERSAL_COMPARE_CHART_IDS.has(canvasId);
        if (isUniversalChart) {
            anchor = wrap.closest(".glass-card") || wrap;
            if ((canvasId === "chart-universal-timeline" || canvasId === "chart-universal-stages") && refs.universalGrid) {
                anchor = refs.universalGrid;
            }
        }
        if (canvasId === "chart-stage-latency" || canvasId === "chart-stage-errors") {
            const stageGridRow = wrap.closest(".analytics-grid.analytics-grid-2");
            if (stageGridRow) {
                anchor = stageGridRow;
            }
        }
        const parent = anchor.parentElement;
        if (insertMetricExpandedChart(canvasId, wrap, container)) {
            // Inserted before metric tables by the metrics-specific layout.
        } else if (isUniversalChart && parent) {
            anchor.insertAdjacentElement("afterend", container);
        } else if (parent && getComputedStyle(parent).display === "grid") {
            container.classList.add("analytics-expand-host");
            const siblings = Array.from(parent.children).filter((el) => !el.classList.contains("analytics-expanded-block"));
            const index = siblings.indexOf(anchor);
            const columns = getGridColumnCount(parent);
            const rowEndIndex = index >= 0
                ? Math.min(siblings.length - 1, Math.floor(index / columns) * columns + (columns - 1))
                : siblings.length - 1;
            const rowEndEl = siblings[rowEndIndex] || anchor;
            rowEndEl.insertAdjacentElement("afterend", container);
        } else {
            anchor.insertAdjacentElement("afterend", container);
        }

        state.expandedChart.sourceCanvasId = canvasId;
        state.expandedChart.containerEl = container;
        state.expandedChart.customRangeActive = false;
        if (!UNIVERSAL_COMPARE_CHART_IDS.has(canvasId) && !METRIC_EXPANDED_CONTROLLESS_CHART_IDS.has(canvasId)) {
            setupExpandedGraphControls(container, canvasId);
        }
        setupExpandedZoomControls(container);
        updateExpandButtonsState();
        renderExpandedChartClone(canvasId);
        if (button) {
            button.blur();
        }
    }

    function insertMetricExpandedChart(canvasId, wrap, container) {
        if (!isStageMetricPrimaryCanvas(canvasId)) {
            return false;
        }
        const block = wrap.closest(".analytics-stage-metric-chart-card, .analytics-stage-metric-block");
        if (!block) {
            return false;
        }
        const adminMain = block.closest(".analytics-stage-metric-block-main");
        const adminTablesRow = adminMain?.querySelector(":scope > .analytics-stage-metric-tables-row");
        if (adminMain && adminTablesRow) {
            adminMain.insertBefore(container, adminTablesRow);
            return true;
        }
        const tableWrap = block.querySelector(":scope > .analytics-stage-metric-table-wrap, :scope > .table-responsive.analytics-stage-metric-table-wrap");
        if (tableWrap) {
            block.insertBefore(container, tableWrap);
            return true;
        }
        block.appendChild(container);
        return true;
    }

    function collapseExpandedChart() {
        const sourceCanvasId = state.expandedChart.sourceCanvasId;
        if (state.expandedChart.instance) {
            state.expandedChart.instance.destroy();
            state.expandedChart.instance = null;
        }
        if (state.expandedChart.compareInstance) {
            state.expandedChart.compareInstance.destroy();
            state.expandedChart.compareInstance = null;
        }
        if (state.expandedChart.containerEl) {
            const sourceWrap = state.expandedChart.containerEl.previousElementSibling;
            if (sourceWrap?.classList.contains("analytics-chart-wrap")) {
                sourceWrap.classList.remove("is-expanded-source");
            }
            state.expandedChart.containerEl.remove();
        }
        refs.analyticsPage?.querySelectorAll(".analytics-chart-wrap.is-expanded-source")
            ?.forEach((wrap) => wrap.classList.remove("is-expanded-source"));
        if (sourceCanvasId && state.charts[sourceCanvasId]) {
            state.charts[sourceCanvasId].update("none");
        }
        state.expandedChart.sourceCanvasId = "";
        state.expandedChart.containerEl = null;
        state.expandedChart.customRangeActive = false;
        updateExpandButtonsState();
    }

    function rebuildExpandedChartForCurrentMode(canvasId) {
        if (!canvasId || state.expandedChart.sourceCanvasId !== canvasId || !state.expandedChart.containerEl) {
            return false;
        }
        collapseExpandedChart();
        toggleExpandedChart(canvasId);
        return state.expandedChart.sourceCanvasId === canvasId;
    }

    function updateExpandButtonsState() {
        const buttons = refs.analyticsPage?.querySelectorAll("[data-chart-expand]") || [];
        buttons.forEach((button) => {
            const canvasId = button.getAttribute("data-chart-expand") || "";
            const expanded = canvasId === state.expandedChart.sourceCanvasId;
            button.innerHTML = expanded
                ? '<i class="bi bi-x-lg"></i>'
                : '<i class="bi bi-search"></i>';
            button.title = expanded ? "Свернуть график" : "Увеличить график";
            button.setAttribute("aria-label", expanded ? "Свернуть график" : "Увеличить график");
            button.classList.toggle("btn-dark", expanded);
            button.classList.toggle("btn-outline-dark", !expanded);
        });
        updateCompareButtonsState();
    }

    function updateCompareButtonsState() {
        const allCanvases = refs.analyticsPage?.querySelectorAll("canvas[id^='chart-']") || [];
        allCanvases.forEach((canvas) => {
            const wrap = canvas.closest(".analytics-chart-wrap");
            if (!wrap) {
                return;
            }
            const actions = ensureChartActionsBar(wrap);
            ensureChartScenarioPicker(actions, canvas.id);
        });

        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            const canvas = document.getElementById(canvasId);
            const wrap = canvas?.closest(".analytics-chart-wrap");
            const actions = wrap ? ensureChartActionsBar(wrap) : null;
            if (actions) {
                ensureInlineCompareModeControl(actions, canvasId);
                ensureInlineComparePresetControl(actions, canvasId);
                ensureChartScenarioPicker(actions, canvasId);
            }
            syncInlineCompareModeSelectValues(canvasId);
            syncInlineCompareModeResetVisibility(canvasId);
        });
    }

    function resolveChartScenarioOptions(canvasId) {
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        return CHART_SCENARIOS_BY_CANVAS[sourceCanvasId] || [];
    }

    function resolveChartScenarioSourceCanvasId(canvasId) {
        const resolved = resolveCompareSourceCanvasId(canvasId);
        if (CHART_SCENARIOS_BY_CANVAS[resolved]) {
            return resolved;
        }
        return canvasId;
    }

    function closeAllChartScenarioPickers() {
        refs.analyticsPage?.querySelectorAll(".analytics-chart-scenario-picker.is-expanded")
            ?.forEach((picker) => {
                picker.classList.remove("is-expanded");
                picker.querySelector(".analytics-chart-scenario-popup")?.classList.add("d-none");
            });
    }

    function closeGlobalScenarioPicker() {
        const picker = refs.analysisScenario?.parentElement?.querySelector(".analytics-global-scenario-picker");
        picker?.classList.remove("is-expanded");
        picker?.querySelector(".analytics-global-scenario-popup")?.classList.add("d-none");
    }

    function scenarioCodeOf(scenario) {
        return String(scenario?.code || scenario?.id || "").trim().toLowerCase();
    }

    function allScenarioSourceCanvasIds() {
        return Array.from(new Set(Object.keys(CHART_SCENARIOS_BY_CANVAS || {})
            .map((canvasId) => resolveChartScenarioSourceCanvasId(canvasId))
            .filter((canvasId) => !!canvasId && canvasId !== "global")));
    }

    function globalScenarioMapping(scenarioId = state.globalScenarioCode) {
        const id = String(scenarioId || "").trim();
        const mapping = {
            traffic_spike: {
                "chart-events-count": "traffic_spike",
                "chart-event-kpi": "event_load_growth",
                "chart-universal-timeline": "universal_event_analysis"
            },
            tail_latency: {
                "chart-latency": "p95_growth",
                "chart-event-kpi": "event_p95_degradation",
                "chart-stage-latency": "layer_bottleneck",
                "chart-stage-metric-series": "numeric_metric_degradation"
            },
            error_burst: {
                "chart-error-rate": "error_growth",
                "chart-event-kpi": "event_error_growth",
                "chart-stage-errors": "stage_error_growth"
            },
            release_compare: {
                "chart-compare-delta": "release_delta",
                "chart-latency": "p95_growth",
                "chart-error-rate": "error_growth"
            },
            layer_bottleneck: {
                "chart-stage-latency": "layer_bottleneck",
                "chart-stage-metric-series": "numeric_metric_degradation",
                "chart-universal-stages": "universal_layer_bottleneck"
            },
            error_without_load: {
                "chart-error-rate": "error_growth",
                "chart-event-kpi": "event_error_growth",
                "analytics-events-table": "error_search"
            },
            errors_without_load: {
                "chart-error-rate": "error_growth",
                "chart-event-kpi": "event_error_growth",
                "analytics-events-table": "error_search"
            }
        };
        return mapping[id] || {};
    }

    function resolveEffectiveScenarioCode(canvasId) {
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        if (Object.prototype.hasOwnProperty.call(state.scenarioOverriddenBySource, sourceCanvasId)
            && state.scenarioOverriddenBySource[sourceCanvasId]) {
            return String(state.scenarioBySource[sourceCanvasId] || "").trim().toLowerCase();
        }
        const inherited = String(globalScenarioMapping()[sourceCanvasId] || "").trim().toLowerCase();
        state.scenarioBySource[sourceCanvasId] = inherited;
        state.chartScenarioBySource[sourceCanvasId] = inherited;
        return inherited;
    }

    function resolveChartScenario(canvasId) {
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        const code = resolveEffectiveScenarioCode(sourceCanvasId);
        if (!code) {
            return null;
        }
        return (CHART_SCENARIOS_BY_CANVAS[sourceCanvasId] || [])
            .find((item) => scenarioCodeOf(item) === code) || null;
    }

    function setScenarioForSource(sourceCanvasId, scenarioCode, overridden) {
        const code = String(scenarioCode || "").trim().toLowerCase();
        state.scenarioBySource[sourceCanvasId] = code;
        state.chartScenarioBySource[sourceCanvasId] = code;
        state.scenarioOverriddenBySource[sourceCanvasId] = !!overridden;
    }

    function resetScenarioForSource(sourceCanvasId) {
        delete state.scenarioOverriddenBySource[sourceCanvasId];
        setScenarioForSource(sourceCanvasId, globalScenarioMapping()[sourceCanvasId] || "", false);
    }

    function resetAllScenariosToGlobal() {
        allScenarioSourceCanvasIds().forEach((sourceCanvasId) => {
            resetScenarioForSource(sourceCanvasId);
        });
        refreshAllChartScenarioPickers();
        syncAllScenarioSummaries();
    }

    function scenarioFocusKeywords(scenarioCode, scenario) {
        const text = [
            scenarioCode,
            scenario?.label,
            scenario?.shortDescription,
            scenario?.description,
            scenario?.fullDescription,
            scenario?.details
        ].join(" ").toLowerCase();
        const keywords = new Set();
        if (/error|ошиб|status|5xx|4xx/.test(text)) {
            ["error", "ошиб", "%", "rate", "status", "код"].forEach((item) => keywords.add(item));
        }
        if (/latency|p95|p99|duration|задерж|хвост|медлен/.test(text)) {
            ["p95", "p99", "latency", "duration", "ms", "мс", "задерж", "длит"].forEach((item) => keywords.add(item));
        }
        if (/count|load|traffic|нагруз|колич|volume|поток/.test(text)) {
            ["count", "колич", "событ", "volume", "нагруз"].forEach((item) => keywords.add(item));
        }
        if (/database|db|sql|баз/.test(text)) {
            ["database", "db", "sql", "баз"].forEach((item) => keywords.add(item));
        }
        if (/service|бизнес/.test(text)) {
            ["service", "бизнес"].forEach((item) => keywords.add(item));
        }
        if (/controller|frontend|network|path|url/.test(text)) {
            ["controller", "frontend", "network", "path", "url", "контрол"].forEach((item) => keywords.add(item));
        }
        return Array.from(keywords);
    }

    function applyScenarioToChartConfig(canvasId, config) {
        const scenario = resolveChartScenario(canvasId);
        if (!scenario || !config) {
            return config;
        }
        const code = scenarioCodeOf(scenario);
        config.options = config.options || {};
        config.options.plugins = config.options.plugins || {};
        config.options.plugins.subtitle = {
            ...(config.options.plugins.subtitle || {}),
            display: true,
            text: scenario.shortDescription || scenario.description || scenario.label || code,
            color: "#1f2937",
            font: {size: 11, weight: "600"},
            padding: {bottom: 6}
        };
        const datasets = Array.isArray(config.data?.datasets) ? config.data.datasets : [];
        const keywords = scenarioFocusKeywords(code, scenario);
        let highlighted = 0;
        datasets.forEach((dataset, index) => {
            const label = String(dataset.label || "").toLowerCase();
            const isMatch = keywords.length === 0
                ? index === 0
                : keywords.some((keyword) => label.includes(keyword));
            if (isMatch) {
                highlighted += 1;
                dataset.borderWidth = Math.max(Number(dataset.borderWidth || 2), 3);
                dataset.pointRadius = Math.max(Number(dataset.pointRadius || 0), 2);
                dataset.order = 0;
            } else if (datasets.length > 1) {
                dataset.borderWidth = Math.max(1, Number(dataset.borderWidth || 2) - 1);
                dataset.pointRadius = Math.min(Number(dataset.pointRadius || 1), 1);
                dataset.order = 5;
                if (typeof dataset.borderColor === "string") {
                    dataset.borderColor = withAlphaColor(dataset.borderColor, 0.38);
                }
                if (typeof dataset.backgroundColor === "string") {
                    dataset.backgroundColor = withAlphaColor(dataset.backgroundColor, 0.18);
                }
            }
        });
        if (!highlighted && datasets[0]) {
            datasets[0].borderWidth = Math.max(Number(datasets[0].borderWidth || 2), 3);
            datasets[0].pointRadius = Math.max(Number(datasets[0].pointRadius || 0), 2);
        }
        config.options.scenarioCode = code;
        return config;
    }

    function rerenderScenarioChart(canvasId) {
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        const baseConfig = state.chartScenarioBaseConfigs[sourceCanvasId] || state.chartConfigs[sourceCanvasId];
        if (baseConfig && document.getElementById(sourceCanvasId)) {
            upsertChart(sourceCanvasId, cloneChartConfig(baseConfig));
        }
        const compareCanvasId = state.inlineCompareCanvasBySource[sourceCanvasId] || getStageMetricExpandedCompareCanvasId(sourceCanvasId) || "";
        const compareBaseConfig = compareCanvasId ? (state.chartScenarioBaseConfigs[compareCanvasId] || state.chartConfigs[compareCanvasId]) : null;
        if (compareCanvasId && compareBaseConfig && document.getElementById(compareCanvasId)) {
            upsertChart(compareCanvasId, cloneChartConfig(compareBaseConfig));
        }
        if (state.expandedChart.sourceCanvasId === sourceCanvasId) {
            renderExpandedChartClone(sourceCanvasId);
        }
        syncChartScenarioSummary(sourceCanvasId);
    }

    function scenarioSummaryText(canvasId) {
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        const scenario = resolveChartScenario(sourceCanvasId);
        if (!scenario) {
            return "";
        }
        const inherited = !state.scenarioOverriddenBySource[sourceCanvasId];
        const prefix = inherited && state.globalScenarioCode ? "Глобальный сценарий" : "Сценарий";
        return `${prefix}: ${scenario.label || scenarioCodeOf(scenario)}. ${scenario.shortDescription || scenario.description || scenario.details || ""}`.trim();
    }

    function ensureScenarioSummaryEl(host, canvasId, expanded) {
        if (!host) {
            return null;
        }
        const attr = expanded ? "data-expanded-scenario-summary" : "data-chart-scenario-summary";
        let summary = host.querySelector(`[${attr}='${canvasId}']`);
        if (!summary) {
            summary = document.createElement("div");
            summary.className = expanded ? "analytics-chart-scenario-summary analytics-chart-scenario-summary-expanded" : "analytics-chart-scenario-summary";
            summary.setAttribute(attr, canvasId);
            host.appendChild(summary);
        }
        return summary;
    }

    function syncChartScenarioSummary(canvasId) {
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        const text = scenarioSummaryText(sourceCanvasId);
        const sourceCanvas = document.getElementById(sourceCanvasId);
        const wrap = sourceCanvas?.closest(".analytics-chart-wrap");
        const miniHost = wrap?.parentElement || wrap;
        const miniSummary = ensureScenarioSummaryEl(miniHost, sourceCanvasId, false);
        if (miniSummary) {
            miniSummary.textContent = text;
            miniSummary.classList.toggle("d-none", !text);
        }
        if (state.expandedChart.sourceCanvasId === sourceCanvasId && state.expandedChart.containerEl) {
            const expandedSummary = ensureScenarioSummaryEl(state.expandedChart.containerEl, sourceCanvasId, true);
            if (expandedSummary) {
                expandedSummary.textContent = text;
                expandedSummary.classList.toggle("d-none", !text);
            }
        }
    }

    function syncAllScenarioSummaries() {
        allScenarioSourceCanvasIds().forEach((canvasId) => syncChartScenarioSummary(canvasId));
    }

    function setScenarioChartsLoading(isLoading) {
        allScenarioSourceCanvasIds().forEach((canvasId) => setChartActionLoading(canvasId, isLoading));
    }

    function upgradeGlobalScenarioSelect() {
        if (!refs.analysisScenario || state.globalScenarioUpgraded) {
            return;
        }
        const options = ANALYTICS_SCENARIO_REGISTRY.global || [];
        if (!options.length) {
            return;
        }
        state.globalScenarioUpgraded = true;
        refs.analysisScenario.classList.add("d-none");
        refs.analysisScenario.innerHTML = [
            `<option value="">Без общего сценария</option>`,
            ...options.map((item) => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.label)}</option>`)
        ].join("");

        const picker = document.createElement("div");
        picker.className = "analytics-global-scenario-picker";
        picker.innerHTML = `
            <button type="button"
                    class="btn btn-outline-dark analytics-global-scenario-toggle"
                    data-global-scenario-toggle
                    aria-expanded="false">
                <span data-global-scenario-label>Без общего сценария</span>
            </button>
            <div class="analytics-global-scenario-popup d-none">
                <button type="button" class="analytics-global-scenario-item" data-global-scenario-option="">
                    <span class="analytics-global-scenario-item-title">Без общего сценария</span>
                    <span class="analytics-global-scenario-item-sub">Не выделять аналитический сценарий поверх графиков.</span>
                </button>
                ${options.map((item) => `
                    <div class="analytics-global-scenario-row">
                        <button type="button" class="analytics-global-scenario-item" data-global-scenario-option="${escapeHtml(item.id)}">
                            <span class="analytics-global-scenario-item-title">${escapeHtml(item.label)}</span>
                            <span class="analytics-global-scenario-item-sub">${escapeHtml(item.description || "")}</span>
                        </button>
                        <button type="button"
                                class="btn btn-outline-secondary analytics-chart-scenario-help-btn"
                                data-global-scenario-help="${escapeHtml(item.id)}"
                                title="Подсказка по сценарию"
                                aria-label="Подсказка по сценарию">?</button>
                    </div>
                `).join("")}
            </div>
        `;
        refs.analysisScenario.insertAdjacentElement("afterend", picker);

        const toggle = picker.querySelector("[data-global-scenario-toggle]");
        const popup = picker.querySelector(".analytics-global-scenario-popup");
        toggle?.addEventListener("click", (event) => {
            event.preventDefault();
            event.stopPropagation();
            const shouldOpen = !picker.classList.contains("is-expanded");
            closeAllChartScenarioPickers();
            picker.classList.toggle("is-expanded", shouldOpen);
            popup?.classList.toggle("d-none", !shouldOpen);
            toggle.setAttribute("aria-expanded", shouldOpen ? "true" : "false");
        });
        picker.querySelectorAll("[data-global-scenario-option]").forEach((button) => {
            button.addEventListener("click", async (event) => {
                event.preventDefault();
                event.stopPropagation();
                const scenarioId = button.getAttribute("data-global-scenario-option") || "";
                refs.analysisScenario.value = scenarioId;
                await applyGlobalAnalysisScenario(scenarioId);
                syncGlobalScenarioPicker();
                closeGlobalScenarioPicker();
            });
        });
        picker.querySelectorAll("[data-global-scenario-help]").forEach((button) => {
            button.addEventListener("click", (event) => {
                event.preventDefault();
                event.stopPropagation();
                openGlobalScenarioHelpModal(button.getAttribute("data-global-scenario-help") || "");
            });
        });
        refs.analysisScenarioHelp?.addEventListener("click", () => {
            openGlobalScenarioHelpModal(refs.analysisScenario.value || options[0]?.id || "");
        });
        document.addEventListener("click", (event) => {
            if (!event.target?.closest?.(".analytics-global-scenario-picker")) {
                closeGlobalScenarioPicker();
            }
        });
        syncGlobalScenarioPicker();
    }

    function syncGlobalScenarioPicker() {
        const picker = refs.analysisScenario?.parentElement?.querySelector(".analytics-global-scenario-picker");
        const label = picker?.querySelector("[data-global-scenario-label]");
        const selectedId = refs.analysisScenario?.value || "";
        const scenario = (ANALYTICS_SCENARIO_REGISTRY.global || []).find((item) => item.id === selectedId);
        if (label) {
            label.textContent = scenario?.label || "Без общего сценария";
        }
        picker?.querySelectorAll("[data-global-scenario-option]").forEach((button) => {
            button.classList.toggle("active", (button.getAttribute("data-global-scenario-option") || "") === selectedId);
        });
    }

    function renderEmptyStageMetricTextChart(canvasId, metricName) {
        upsertChart(canvasId, {
            type: "bar",
            data: {
                labels: ["Нет данных"],
                datasets: [{
                    label: `${metricName || "Текстовая метрика"}: топ значений`,
                    data: [0],
                    backgroundColor: "rgba(148, 163, 184, 0.28)",
                    borderColor: "rgba(100, 116, 139, 0.45)",
                    borderWidth: 1,
                    borderRadius: 7
                }]
            },
            options: barChartOptions("Количество")
        });
    }

    async function applyGlobalAnalysisScenario(scenarioId) {
        const id = String(scenarioId || "").trim();
        state.globalAnalysisScenario = id;
        state.globalScenarioCode = id;
        if (refs.analysisScenario) {
            refs.analysisScenario.value = id;
        }
        setScenarioChartsLoading(true);
        await nextFrame();
        try {
            resetAllScenariosToGlobal();
            allScenarioSourceCanvasIds().forEach((canvasId) => rerenderScenarioChart(canvasId));
            await waitForChartPaint(2);
        } finally {
            setScenarioChartsLoading(false);
        }
    }

    function refreshAllChartScenarioPickers() {
        refs.analyticsPage?.querySelectorAll("[data-chart-scenario-picker]")?.forEach((picker) => {
            const canvasId = picker.getAttribute("data-chart-scenario-picker") || "";
            const actions = picker.closest(".analytics-chart-actions");
            if (canvasId && actions) {
                ensureChartScenarioPicker(actions, canvasId);
            }
        });
    }

    function openGlobalScenarioHelpModal(scenarioId) {
        if (!refs.helpModalEl || !refs.helpModalTitle || !refs.helpModalBody) {
            return;
        }
        const scenarios = ANALYTICS_SCENARIO_REGISTRY.global || [];
        const scenario = scenarios.find((item) => item.id === scenarioId) || scenarios[0];
        refs.helpModalTitle.textContent = scenario?.label || "Общий сценарий анализа";
        refs.helpModalBody.innerHTML = renderGlobalScenarioHelpHtml(scenario);
        const modal = bootstrap.Modal.getOrCreateInstance(refs.helpModalEl);
        modal.show();
    }

    function renderGlobalScenarioHelpHtml(scenario) {
        if (!scenario) {
            return `<div class="analytics-help-block">Выберите сценарий, чтобы увидеть подсказку.</div>`;
        }
        const enriched = enrichScenarioHelpForModal(scenario);
        return `
            <div class="analytics-help-block">
                ${renderHelpSection("Когда использовать", `<p>${escapeHtml(enriched.whenToUse)}</p>`)}
                ${renderHelpSection("Что помогает найти", `<p>${escapeHtml(enriched.whatItFinds)}</p>`)}
                ${renderHelpSection("Маршрут анализа", renderHelpList(enriched.route))}
                ${renderHelpSection("Когда вывод может быть ошибочным", `<p>${escapeHtml(enriched.falseAlarm)}</p>`)}
            </div>
        `;
    }

    function selectedChartScenarioCodes(canvasId) {
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        const code = resolveEffectiveScenarioCode(sourceCanvasId);
        return code ? [code] : [];
    }

    async function applyChartScenario(canvasId, scenarioId, checked) {
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        const code = String(scenarioId || "").trim().toLowerCase();
        if (code === "__global__") {
            resetScenarioForSource(sourceCanvasId);
        } else if (!code && !checked) {
            setScenarioForSource(sourceCanvasId, "", true);
        } else if (checked) {
            setScenarioForSource(sourceCanvasId, code, true);
        } else if (state.scenarioBySource[sourceCanvasId] === code) {
            setScenarioForSource(sourceCanvasId, "", true);
        }
        setChartActionLoading(sourceCanvasId, true);
        await nextFrame();
        try {
            rerenderScenarioChart(sourceCanvasId);
            refreshAllChartScenarioPickers();
            syncInlineCompareModeResetVisibility(sourceCanvasId);
            if (checked && shouldScenarioPreferCompareOverlay(code) && INLINE_COMPARE_CHART_IDS.has(sourceCanvasId)) {
                await applyInlineCompareMode(sourceCanvasId, "overlay", {override: true});
                await applyInlineComparePresetToChart(sourceCanvasId);
                await applyStoredExpandedRangesToCharts(sourceCanvasId);
                if (state.expandedChart.sourceCanvasId === sourceCanvasId) {
                    renderExpandedChartClone(sourceCanvasId);
                }
            }
            await waitForChartPaint(2);
        } catch (error) {
            console.error("Chart scenario apply failed", {canvasId, scenarioId: code, error});
        } finally {
            setChartActionLoading(sourceCanvasId, false);
        }
    }

    function shouldScenarioPreferCompareOverlay(code) {
        const value = String(code || "").toLowerCase();
        return value.includes("compare")
            || value.includes("release")
            || value.includes("delta")
            || value.includes("before_after");
    }

    function openChartScenarioHelpModal(canvasId, scenarioId) {
        if (!refs.helpModalEl || !refs.helpModalTitle || !refs.helpModalBody) {
            return;
        }
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        const scenario = (CHART_SCENARIOS_BY_CANVAS[sourceCanvasId] || [])
            .find((item) => scenarioCodeOf(item) === String(scenarioId || "").trim().toLowerCase());
        if (!scenario) {
            return;
        }
        const target = document.getElementById(sourceCanvasId) || document.getElementById(canvasId);
        const chartTitle = target?.closest(".analytics-panel, .glass-card")
            ?.querySelector(".analytics-panel-title, .small.text-muted")
            ?.textContent
            ?.trim();
        refs.helpModalTitle.textContent = scenario.label || "Сценарий графика";
        const enriched = enrichScenarioHelpForModal(scenario);
        refs.helpModalBody.innerHTML = `
            <div class="analytics-help-block">
                ${chartTitle ? `<div class="analytics-help-modal-subtitle">${escapeHtml(chartTitle)}</div>` : ""}
                ${renderHelpSection("Когда использовать", `<p>${escapeHtml(enriched.whenToUse)}</p>`)}
                ${renderHelpSection("Что помогает найти", `<p>${escapeHtml(enriched.whatItFinds)}</p>`)}
                ${renderHelpSection("Маршрут анализа", renderHelpList(enriched.route))}
                ${renderHelpSection("Когда вывод может быть ошибочным", `<p>${escapeHtml(enriched.falseAlarm)}</p>`)}
            </div>
        `;
        const modal = bootstrap.Modal.getOrCreateInstance(refs.helpModalEl);
        modal.show();
    }

    function enrichScenarioHelpForModal(scenario) {
        const label = scenario?.label || "Сценарий";
        const description = scenario?.description || "";
        const details = scenario?.details || "";
        const checklist = Array.isArray(scenario?.checklist) && scenario.checklist.length
            ? scenario.checklist
            : [
                "Сначала проверьте Count, чтобы понять размер выборки.",
                "Затем сравните P95 и Error rate в том же периоде.",
                "Откройте соседние графики, чтобы понять, проблема локальная или общая.",
                "Подтвердите вывод Raw-событиями и Trace ID."
            ];
        return {
            whenToUse: description || `Используйте сценарий «${label}», когда на графике виден подозрительный рост, провал или отличие До/После.`,
            whatItFinds: details || "Сценарий помогает превратить график в проверяемую гипотезу: что изменилось, где искать причину и какие данные нужны для подтверждения.",
            route: checklist.map((item) => String(item || "").trim()).filter(Boolean),
            falseAlarm: scenario?.falseAlarm || "Не делайте вывод сразу, если выборка маленькая, периоды До/После отличаются по нагрузке или включены разные фильтры. Сначала проверьте Count и повторяемость в нескольких bucket."
        };
    }

    function ensureChartScenarioPicker(actions, canvasId) {
        if (!actions || !canvasId) {
            return;
        }
        if (!actions.querySelector(`[data-chart-scenario-picker='${canvasId}']`)) {
            const options = resolveChartScenarioOptions(canvasId);
            if (!options.length) {
                return;
            }
            const picker = document.createElement("div");
            picker.className = "analytics-chart-scenario-picker";
            picker.setAttribute("data-chart-scenario-picker", canvasId);
            picker.innerHTML = `
                <button type="button"
                        class="btn btn-outline-dark analytics-chart-icon-btn analytics-chart-scenario-toggle"
                        data-chart-scenario-toggle="${canvasId}"
                        title="Сценарии графика"
                        aria-label="Сценарии графика">
                    <i class="bi bi-lightning-charge"></i>
                </button>
                <div class="analytics-chart-scenario-popup d-none" data-chart-scenario-popup="${canvasId}">
                    <div class="analytics-chart-scenario-item">
                        <label class="analytics-chart-scenario-label form-check mb-0">
                            <input class="form-check-input"
                                   type="radio"
                                   name="analytics-chart-scenario-${escapeHtml(canvasId)}"
                                   data-chart-scenario-option="${canvasId}"
                                   value="__global__">
                            <span class="form-check-label">По глобальному сценарию</span>
                        </label>
                        <div class="analytics-chart-scenario-item-sub">Сбросить локальный выбор и наследовать главный фильтр.</div>
                    </div>
                    <div class="analytics-chart-scenario-item">
                        <label class="analytics-chart-scenario-label form-check mb-0">
                            <input class="form-check-input"
                                   type="radio"
                                   name="analytics-chart-scenario-${escapeHtml(canvasId)}"
                                   data-chart-scenario-option="${canvasId}"
                                   value="">
                            <span class="form-check-label">Без сценария</span>
                        </label>
                    </div>
                    ${options.map((item) => `
                        <div class="analytics-chart-scenario-item">
                            <div class="analytics-chart-scenario-item-head">
                                <label class="analytics-chart-scenario-label form-check mb-0">
                                    <input class="form-check-input"
                                           type="radio"
                                           name="analytics-chart-scenario-${escapeHtml(canvasId)}"
                                           data-chart-scenario-option="${canvasId}"
                                           value="${escapeHtml(scenarioCodeOf(item))}">
                                    <span class="form-check-label">${escapeHtml(item.label)}</span>
                                </label>
                                <button type="button"
                                        class="btn btn-outline-secondary analytics-chart-scenario-help-btn"
                                        data-chart-scenario-help="${canvasId}"
                                        data-chart-scenario-help-id="${escapeHtml(scenarioCodeOf(item))}"
                                        title="Подсказка по сценарию"
                                        aria-label="Подсказка по сценарию">?</button>
                            </div>
                            <div class="analytics-chart-scenario-item-sub">${escapeHtml(item.description || "")}</div>
                        </div>
                    `).join("")}
                </div>
            `;
            actions.appendChild(picker);

            const toggle = picker.querySelector(`[data-chart-scenario-toggle='${canvasId}']`);
            const popup = picker.querySelector(`[data-chart-scenario-popup='${canvasId}']`);
            toggle?.addEventListener("click", (event) => {
                event.preventDefault();
                event.stopPropagation();
                const shouldOpen = !picker.classList.contains("is-expanded");
                closeAllChartScenarioPickers();
                picker.classList.toggle("is-expanded", shouldOpen);
                popup?.classList.toggle("d-none", !shouldOpen);
            });

            picker.querySelectorAll(`[data-chart-scenario-option='${canvasId}']`)
                .forEach((input) => {
                    input.addEventListener("change", async () => {
                        if (!input.checked) {
                            return;
                        }
                        await applyChartScenario(canvasId, input.value || "", !!input.value);
                        ensureChartScenarioPicker(actions, canvasId);
                        closeAllChartScenarioPickers();
                    });
                });

            picker.querySelectorAll(`[data-chart-scenario-help='${canvasId}']`)
                .forEach((button) => button.addEventListener("click", (event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    openChartScenarioHelpModal(canvasId, button.getAttribute("data-chart-scenario-help-id") || "");
                }));
        }

        const selected = new Set(selectedChartScenarioCodes(canvasId));
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        const hasScenarioOverride = !!state.scenarioOverriddenBySource[sourceCanvasId];
        actions.querySelectorAll(
            `[data-chart-scenario-picker='${canvasId}'] [data-chart-scenario-option='${canvasId}']`
        ).forEach((input) => {
            const value = String(input.value || "").trim().toLowerCase();
            if (value === "__global__") {
                input.checked = !hasScenarioOverride;
            } else {
                input.checked = value ? selected.has(value) : (hasScenarioOverride && selected.size === 0);
            }
        });
        const toggle = actions.querySelector(`[data-chart-scenario-toggle='${canvasId}']`);
        if (toggle) {
            const count = selected.size;
            toggle.classList.toggle("active", count > 0);
            const selectedScenario = resolveChartScenarioOptions(canvasId)
                .find((item) => selected.has(scenarioCodeOf(item)));
            const sourceLabel = selectedScenario && !hasScenarioOverride ? "глобальный" : "локальный";
            toggle.title = selectedScenario ? `Сценарий графика (${sourceLabel}): ${selectedScenario.label}` : "Сценарии графика";
            toggle.setAttribute("aria-label", toggle.title);
        }
        syncChartScenarioSummary(canvasId);

        if (!state.chartScenarioOutsideBound) {
            document.addEventListener("click", (event) => {
                const inside = event.target?.closest?.(".analytics-chart-scenario-picker");
                if (!inside) {
                    closeAllChartScenarioPickers();
                    refs.analyticsPage?.querySelectorAll(".analytics-chart-scenario-popup")
                        ?.forEach((popup) => popup.classList.add("d-none"));
                }
            });
            state.chartScenarioOutsideBound = true;
        }
    }

    function isInlineGhostEnabled(canvasId) {
        const stored = state.inlineCompareGhostBySource?.[canvasId];
        if (typeof stored === "boolean") {
            return stored;
        }
        state.inlineCompareGhostBySource[canvasId] = true;
        return true;
    }

    function buildGhostDataset(baseDataset, canvasId) {
        const ghost = deepClonePreservingFunctions(baseDataset);
        ghost.label = `${baseDataset?.label || "Период"} (До)`;
        const baseColor = String(baseDataset?.borderColor || baseDataset?.backgroundColor || colors.slate);
        const asBarCanvas = canvasId === "chart-event-kpi"
            || canvasId === "chart-stage-latency"
            || canvasId === "chart-stage-errors"
            || canvasId === "chart-universal-stages"
            || canvasId === "chart-universal-event-kpi";
        const asBar = baseDataset?.type === "bar" || asBarCanvas;
        if (asBar) {
            ghost.type = "bar";
            ghost.backgroundColor = withAlphaColor(baseColor, 0.28);
            ghost.borderColor = withAlphaColor(baseColor, 0.6);
            ghost.borderWidth = 1;
        } else {
            ghost.type = "line";
            ghost.borderColor = withAlphaColor(baseColor, 0.55);
            ghost.backgroundColor = withAlphaColor(baseColor, 0.12);
            ghost.borderDash = [6, 4];
            ghost.pointRadius = Math.min(1, Number(baseDataset?.pointRadius || 1));
        }
        return ghost;
    }

    function ensureInlineCompareModeControl(actions, canvasId) {
        let select = actions.querySelector(`[data-inline-compare-mode='${canvasId}']`);
        if (!select) {
            select = document.createElement("select");
            select.className = "form-select form-select-sm analytics-inline-compare-mode d-none";
            select.setAttribute("data-inline-compare-mode", canvasId);
            select.innerHTML = INLINE_COMPARE_MODE_OPTIONS
                .map((item) => `<option value="${item.value}">${item.label}</option>`)
                .join("");
            actions.appendChild(select);
        }

        let dropdown = actions.querySelector(`[data-inline-compare-mode-dropdown='${canvasId}']`);
        if (!dropdown) {
            dropdown = document.createElement("div");
            dropdown.className = "dropdown";
            dropdown.setAttribute("data-inline-compare-mode-dropdown", canvasId);
            dropdown.innerHTML = `
                <button type="button"
                        class="btn btn-outline-dark analytics-chart-icon-btn dropdown-toggle"
                        data-inline-compare-mode-trigger="${canvasId}"
                        data-bs-toggle="dropdown"
                        aria-expanded="false"
                        title="Режим сравнения">
                    <i class="bi bi-slash-circle"></i>
                </button>
                <div class="dropdown-menu dropdown-menu-end">
                    ${INLINE_COMPARE_MODE_OPTIONS.map((item) => `
                        <button type="button"
                                class="dropdown-item"
                                data-inline-compare-mode-option="${canvasId}"
                                data-mode="${item.value}">${item.label}</button>
                    `).join("")}
                </div>
            `;
            actions.appendChild(dropdown);
            dropdown.querySelectorAll(`[data-inline-compare-mode-option='${canvasId}']`)
                .forEach((item) => {
                    item.addEventListener("click", async (event) => {
                        const mode = item.getAttribute("data-mode") || "off";
                        if (isMiniKpiSplitModeOptionDisabled(canvasId, mode)) {
                            event.preventDefault();
                            event.stopPropagation();
                            return;
                        }
                        if (resolveKpiCompareSourceCanvasId(canvasId) === "chart-event-kpi") {
                            await applyMiniKpiCompareMode(mode, {
                                source: "mini-dropdown-option",
                                explicitUserChoice: true
                            });
                            ensureInlineCompareModeControl(actions, canvasId);
                            return;
                        }
                        await applyInlineCompareMode(canvasId, mode, {override: true});
                        ensureInlineCompareModeControl(actions, canvasId);
                    });
                });
        }

        select.value = resolveInlineCompareMode(canvasId);
        syncInlineCompareModeSelectValues(canvasId);
        syncInlineCompareModeResetVisibility(canvasId);
    }

    function ensureInlineComparePresetControl(actions, canvasId) {
        const existing = actions.querySelector(`[data-inline-compare-preset='${canvasId}']`);
        const existingReset = actions.querySelector(`[data-inline-compare-reset='${canvasId}']`);
        existing?.remove();
        let resetButton = existingReset;
        if (!resetButton) {
            resetButton = document.createElement("button");
            resetButton.type = "button";
            resetButton.className = "btn btn-outline-dark analytics-chart-icon-btn analytics-inline-compare-reset-btn";
            resetButton.setAttribute("data-inline-compare-reset", canvasId);
            resetButton.setAttribute("title", "Сбросить к верхнему фильтру");
            resetButton.setAttribute("aria-label", "Сбросить к верхнему фильтру");
            resetButton.innerHTML = '<i class="bi bi-arrow-counterclockwise"></i>';
            resetButton.addEventListener("click", async () => {
                setChartActionLoading(canvasId, true);
                try {
                    await resetChartLocalOverride(canvasId);
                } finally {
                    setChartActionLoading(canvasId, false);
                }
                ensureInlineComparePresetControl(actions, canvasId);
            });
            actions.appendChild(resetButton);
        }
        resetButton.classList.toggle("d-none", !hasChartLocalOverride(canvasId));
    }

    function resolveInlineComparePreset(canvasId) {
        const existing = (state.inlineComparePresetBySource[canvasId] || "").trim();
        const isOverridden = !!state.inlineComparePresetOverriddenBySource[canvasId];
        if (isOverridden && existing && INLINE_COMPARE_PRESET_OPTIONS.some((item) => item.value === existing)) {
            return existing;
        }
        const inherited = resolveTopInlineComparePresetOrDefault();
        state.inlineComparePresetBySource[canvasId] = inherited;
        state.inlineComparePresetOverriddenBySource[canvasId] = false;
        return inherited;
    }

    function syncPresetSelectValues(canvasId) {
        const value = resolveInlineComparePreset(canvasId);
        const mini = refs.analyticsPage?.querySelector(`[data-inline-compare-preset='${canvasId}']`);
        if (mini) {
            mini.value = value;
        }
        if (state.expandedChart.sourceCanvasId === canvasId && state.expandedChart.containerEl) {
            const expanded = state.expandedChart.containerEl.querySelector("[data-expanded-preset]");
            if (expanded) {
                const stored = state.expandedRangesBySource?.[canvasId];
                if (stored?.afterFrom && stored?.afterTo) {
                    syncQuickRangeSelectFromRange(expanded, stored.afterFrom, stored.afterTo);
                } else {
                    const inferred = syncQuickRangeSelectFromRange(expanded, refs.from?.value || "", refs.to?.value || "");
                    if (!inferred && value && state.inlineComparePresetOverriddenBySource?.[canvasId]) {
                        expanded.value = value;
                    }
                }
            }
        }
    }

    function resolveExpandedBucket(canvasId) {
        const local = (state.expandedBucketBySource[canvasId] || "").trim();
        if (local) {
            return local;
        }
        return (refs.bucket?.value || "").trim();
    }

    function inlineCompareParams(_presetCode, canvasId = "") {
        const ranges = resolveInlineCompareRequestRanges(canvasId);
        return buildScopedParamsByLocalRange(ranges.beforeFrom, ranges.beforeTo);
    }

    function inlineCompareAfterParams(_presetCode, canvasId = "") {
        const ranges = resolveInlineCompareRequestRanges(canvasId);
        return buildScopedParamsByLocalRange(ranges.afterFrom, ranges.afterTo);
    }

    async function reloadInlineCompareChartSources() {
        await Promise.all([
            loadOverview(),
            loadStages()
        ]);
    }

    async function applyInlineComparePresetToChart(canvasId) {
        if (!INLINE_COMPARE_CHART_IDS.has(canvasId)) {
            return;
        }
        if (canvasId === "chart-event-kpi") {
            clearEventKpiMiniCompareState();
            await loadOverview();
            return;
        }
        const compareMode = canvasId === "chart-event-kpi"
            ? resolveMiniKpiCompareMode(canvasId)
            : resolveInlineCompareMode(canvasId);
        const isSplitMode = compareMode === "split";
        const isOverlayMode = compareMode === "overlay";
        const compareEnabled = isSplitMode || isOverlayMode;
        const bucketOverride = resolveExpandedBucket(canvasId);
        const chartOptions = getExpandedEventRenderOptions(canvasId);
        const hasLocalRange = hasChartLocalRangeOverride(canvasId);
        if (state.globalCompareEnabled && !state.inlineComparePresetOverriddenBySource[canvasId] && !hasLocalRange) {
            const ranges = resolveGlobalBeforeRange();
            if (canvasId !== "chart-event-kpi") {
                state.expandedRangesBySource[canvasId] = {...ranges};
            }
            const afterLabel = compareEnabled ? "После" : "Период";
            const afterConfig = await buildChartConfigByRange(canvasId, ranges.afterFrom, ranges.afterTo, afterLabel, bucketOverride, chartOptions);
            upsertChart(canvasId, afterConfig);
            if (compareEnabled) {
                const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
                const beforeConfig = await buildChartConfigByRange(canvasId, ranges.beforeFrom, ranges.beforeTo, "До", bucketOverride, chartOptions);
                if (isSplitMode) {
                    upsertChart(compareCanvasId, beforeConfig);
                } else if (canvasId === "chart-event-kpi") {
                    keepKpiCompareConfigWithoutMiniSplit(compareCanvasId, beforeConfig);
                } else {
                    destroyChart(compareCanvasId);
                }
                if (isOverlayMode && isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
                    const ghostDatasets = beforeConfig.data.datasets.map((item) => buildGhostDataset(item, canvasId));
                    afterConfig.data = afterConfig.data || {};
                    afterConfig.data.datasets = [...(afterConfig.data.datasets || []), ...ghostDatasets];
                    upsertChart(canvasId, afterConfig);
                }
            }
            return;
        }
        if (!state.inlineComparePresetOverriddenBySource[canvasId] && !hasLocalRange) {
            const topConfig = await buildChartConfigByRange(
                canvasId,
                refs.from?.value || "",
                refs.to?.value || "",
                "После",
                bucketOverride,
                chartOptions
            );
            upsertChart(canvasId, topConfig);
            if (compareEnabled) {
                const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
                const topFrom = refs.from?.value ? new Date(refs.from.value) : null;
                const topTo = refs.to?.value ? new Date(refs.to.value) : null;
                const hasTopRange = topFrom && topTo && !Number.isNaN(topFrom.getTime()) && !Number.isNaN(topTo.getTime());
                if (hasTopRange) {
                    const durationMs = Math.max(60_000, topTo.getTime() - topFrom.getTime());
                    const beforeFrom = toDateTimeLocalString(new Date(topFrom.getTime() - durationMs));
                    const beforeTo = toDateTimeLocalString(new Date(topFrom.getTime()));
                    const beforeConfig = await buildChartConfigByRange(canvasId, beforeFrom, beforeTo, "До", bucketOverride, chartOptions);
                    if (isSplitMode) {
                        upsertChart(compareCanvasId, beforeConfig);
                    } else if (canvasId === "chart-event-kpi") {
                        keepKpiCompareConfigWithoutMiniSplit(compareCanvasId, beforeConfig);
                    } else {
                        destroyChart(compareCanvasId);
                    }
                    if (isOverlayMode && isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
                        const ghostDatasets = beforeConfig.data.datasets.map((item) => buildGhostDataset(item, canvasId));
                        topConfig.data = topConfig.data || {};
                        topConfig.data.datasets = [...(topConfig.data.datasets || []), ...ghostDatasets];
                        upsertChart(canvasId, topConfig);
                    }
                } else {
                    destroyChart(compareCanvasId);
                }
            }
            return;
        }
        const ranges = state.expandedRangesBySource[canvasId] || expandedRangesFromPresetNow(resolveInlineComparePreset(canvasId));
        const afterConfig = await buildChartConfigByRange(canvasId, ranges.afterFrom, ranges.afterTo, compareEnabled ? "После" : "Период", bucketOverride, chartOptions);
        upsertChart(canvasId, afterConfig);
        if (compareEnabled) {
            const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
            const beforeConfig = await buildChartConfigByRange(canvasId, ranges.beforeFrom, ranges.beforeTo, "До", bucketOverride, chartOptions);
            if (isSplitMode) {
                upsertChart(compareCanvasId, beforeConfig);
            } else if (canvasId === "chart-event-kpi") {
                keepKpiCompareConfigWithoutMiniSplit(compareCanvasId, beforeConfig);
            } else {
                destroyChart(compareCanvasId);
            }
            if (isOverlayMode && isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
                const ghostDatasets = beforeConfig.data.datasets.map((item) => buildGhostDataset(item, canvasId));
                afterConfig.data = afterConfig.data || {};
                afterConfig.data.datasets = [...(afterConfig.data.datasets || []), ...ghostDatasets];
                upsertChart(canvasId, afterConfig);
            }
        }
    }

    async function applyStoredExpandedRangesToCharts(canvasId) {
        if (canvasId === "chart-event-kpi") {
            clearEventKpiMiniCompareState();
            return;
        }
        const stored = state.expandedRangesBySource[canvasId];
        if (!stored || !stored.afterFrom || !stored.afterTo) {
            return;
        }
        const compareMode = canvasId === "chart-event-kpi"
            ? resolveMiniKpiCompareMode(canvasId)
            : resolveInlineCompareMode(canvasId);
        const isSplitMode = compareMode === "split";
        const isOverlayMode = compareMode === "overlay";
        const compareEnabled = isSplitMode || isOverlayMode;
        const afterFromDate = new Date(stored.afterFrom);
        const afterToDate = new Date(stored.afterTo);
        if (Number.isNaN(afterFromDate.getTime()) || Number.isNaN(afterToDate.getTime()) || afterFromDate >= afterToDate) {
            return;
        }
        const bucketOverride = resolveExpandedBucket(canvasId);
        const chartOptions = getExpandedEventRenderOptions(canvasId);
        const afterConfig = await buildChartConfigByRange(canvasId, stored.afterFrom, stored.afterTo, compareEnabled ? "После" : "Период", bucketOverride, chartOptions);
        if (!compareEnabled) {
            upsertChart(canvasId, afterConfig);
            return;
        }
        const beforeFromDate = new Date(stored.beforeFrom || "");
        const beforeToDate = new Date(stored.beforeTo || "");
        if (Number.isNaN(beforeFromDate.getTime()) || Number.isNaN(beforeToDate.getTime()) || beforeFromDate >= beforeToDate) {
            upsertChart(canvasId, afterConfig);
            return;
        }
        const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
        const beforeConfig = await buildChartConfigByRange(canvasId, stored.beforeFrom, stored.beforeTo, "До", bucketOverride, chartOptions);
        if (isSplitMode) {
            upsertChart(compareCanvasId, beforeConfig);
        } else if (canvasId === "chart-event-kpi") {
            keepKpiCompareConfigWithoutMiniSplit(compareCanvasId, beforeConfig);
        } else {
            destroyChart(compareCanvasId);
        }
        if (isOverlayMode && isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
            const ghostDatasets = beforeConfig.data.datasets.map((item) => buildGhostDataset(item, canvasId));
            afterConfig.data = afterConfig.data || {};
            afterConfig.data.datasets = [...(afterConfig.data.datasets || []), ...ghostDatasets];
        }
        upsertChart(canvasId, afterConfig);
    }

    function resolveTopInlineComparePresetOrDefault() {
        const topPreset = (refs.quickRangePresetSelect?.value || "").trim().toLowerCase();
        if (topPreset
            && INLINE_COMPARE_PRESET_OPTIONS.some((item) => item.value === topPreset)) {
            return topPreset;
        }
        return "24h";
    }

    function isValidGlobalBeforeRange() {
        const from = refs.globalBeforeFrom?.value ? new Date(refs.globalBeforeFrom.value) : null;
        const to = refs.globalBeforeTo?.value ? new Date(refs.globalBeforeTo.value) : null;
        if (!from || !to || Number.isNaN(from.getTime()) || Number.isNaN(to.getTime())) {
            return false;
        }
        return from.getTime() < to.getTime();
    }

    function resolveGlobalBeforeRange() {
        const safeAfter = resolveSafeAfterRangeFromTop();
        const normalized = normalizeCompareRangesByAfter(
            safeAfter.afterFrom,
            safeAfter.afterTo,
            "",
            ""
        );
        return {
            beforeFrom: normalized.beforeFrom,
            beforeTo: normalized.beforeTo,
            afterFrom: normalized.afterFrom,
            afterTo: normalized.afterTo
        };
    }

    function syncGlobalCompareControlsVisibility() {
        const mode = resolveGlobalInlineCompareMode();
        const enabled = mode !== "off";
        setGlobalCompareMode(mode);
        syncUniversalCompareFromGlobalFilter();
        refs.globalBeforeRow?.classList.add("d-none");
        if (refs.globalComparePreset && !state.globalCompareBeforeCustom) {
            refs.globalComparePreset.value = "";
        }
        if (refs.globalComparePreset) {
            refs.globalComparePreset.disabled = true;
        }
        if (enabled) {
            const ranges = resolveGlobalBeforeRange();
            if (refs.globalBeforeFrom) refs.globalBeforeFrom.value = ranges.beforeFrom || "";
            if (refs.globalBeforeTo) refs.globalBeforeTo.value = ranges.beforeTo || "";
            syncComparePeriodSummary(refs.globalBeforeSummary, mode, ranges);
        } else {
            syncComparePeriodSummary(refs.globalBeforeSummary, mode, null);
        }
    }

    function isGlobalBeforeRangeCoveredByData(ranges) {
        const beforeFrom = new Date(ranges?.beforeFrom || "");
        const beforeTo = new Date(ranges?.beforeTo || "");
        const dataFrom = new Date(state.allTimeRange?.from || "");
        if ([beforeFrom, beforeTo, dataFrom].some((date) => Number.isNaN(date.getTime()))) {
            return true;
        }
        if (beforeFrom >= beforeTo) {
            return false;
        }
        return beforeTo.getTime() > dataFrom.getTime();
    }

    function showNoCompareDataWarningOnce(ranges) {
        const warningKey = `${ranges?.beforeFrom || ""}|${ranges?.beforeTo || ""}|${state.allTimeRange?.from || ""}`;
        if (state.globalCompareNoDataWarningKey === warningKey) {
            return;
        }
        state.globalCompareNoDataWarningKey = warningKey;
        if (!refs.helpModalEl || !refs.helpModalTitle || !refs.helpModalBody) {
            return;
        }
        refs.helpModalTitle.textContent = "Недостаточно данных";
        const beforeFrom = formatDateTime(ranges?.beforeFrom || "");
        const beforeTo = formatDateTime(ranges?.beforeTo || "");
        const dataFrom = formatDateTime(state.allTimeRange?.from || "");
        refs.helpModalBody.innerHTML = `
            <p class="mb-2">Для периода <b>до</b> нет данных, которые попадают в окно.</p>
            <ul class="mb-0 ps-3">
                <li>Период до: <b>${escapeHtml(beforeFrom)}</b> — <b>${escapeHtml(beforeTo)}</b></li>
                <li>Данные в базе начинаются с: <b>${escapeHtml(dataFrom)}</b></li>
            </ul>
        `;
        const modal = bootstrap.Modal.getOrCreateInstance(refs.helpModalEl);
        modal.show();
    }

    function buildGlobalMetricScopeSignature(attributeCode) {
        const params = new URLSearchParams();
        setIfPresent(params, "from", toIso(refs.from?.value || ""));
        setIfPresent(params, "to", toIso(refs.to?.value || ""));
        const moduleCode = refs.moduleType?.value?.trim();
        if (moduleCode) {
            params.set("moduleCode", moduleCode);
        }
        const eventTypeCode = refs.eventType?.value?.trim();
        if (eventTypeCode) {
            params.set("eventTypeCode", eventTypeCode);
        }
        const requestPath = refs.analyticsRequestPath?.value?.trim();
        if (requestPath) {
            params.set("requestPath", requestPath);
        }
        const attrCode = String(attributeCode || "").trim();
        if (attrCode) {
            params.set("attributeCode", attrCode);
        }
        return params.toString();
    }

    async function refreshGlobalMetricBlock(resetValue) {
        const requestId = (state.globalMetricRefreshRequestId || 0) + 1;
        state.globalMetricRefreshRequestId = requestId;
        state.globalMetricMetaByCode = {};
        const selectedBefore = (refs.globalMetricCode?.value || "").trim();
        const selectedEventsAttrBefore = (refs.eventsAttributeCode?.value || "").trim();
        const scopeSignatureBefore = buildGlobalMetricScopeSignature(selectedBefore);
        const scopeChanged = scopeSignatureBefore !== (state.globalMetricScopeSignature || "");
        const shouldResetValues = !!resetValue || scopeChanged;
        const scopePayload = await fetchFilterOptionsPayload("");
        if (requestId !== state.globalMetricRefreshRequestId) {
            return;
        }
        const availableCodes = Array.isArray(scopePayload?.attributeTypes)
            ? scopePayload.attributeTypes
                .map((item) => String(item?.code || "").trim())
                .filter((code) => code.length > 0)
                .sort((a, b) => a.localeCompare(b, "ru"))
            : [];
        const attrNameByCode = new Map(
            (state.dictionaries?.eventAttributeTypes || [])
                .map((item) => [String(item?.code || "").trim(), String(item?.name || item?.code || "").trim()])
        );
        if (refs.globalMetricCode) {
            refs.globalMetricCode.innerHTML = [
                "<option value=''>Не выбран</option>",
                ...availableCodes.map((code) => `<option value="${escapeHtml(code)}">${escapeHtml(attrNameByCode.get(code) || code)}</option>`)
            ].join("");
            refs.globalMetricCode.value = availableCodes.includes(selectedBefore)
                ? selectedBefore
                : "";
        }
        if (refs.eventsAttributeCode) {
            refs.eventsAttributeCode.innerHTML = [
                "<option value=''>Без фильтра</option>",
                ...availableCodes.map((code) => `<option value="${escapeHtml(code)}">${escapeHtml(attrNameByCode.get(code) || code)}</option>`)
            ].join("");
            refs.eventsAttributeCode.value = availableCodes.includes(selectedEventsAttrBefore)
                ? selectedEventsAttrBefore
                : "";
            if (!refs.eventsAttributeCode.value && refs.eventsAttributeValue) {
                refs.eventsAttributeValue.value = "";
            }
        }
        const attrCode = (refs.globalMetricCode?.value || "").trim();
        state.globalMetricScopeSignature = buildGlobalMetricScopeSignature(attrCode);
        if (!attrCode) {
            if (refs.globalMetricValueSelect) {
                refs.globalMetricValueSelect.innerHTML = "<option value=''>Нет доступных значений</option>";
            }
            if (refs.globalMetricValueInput) {
                refs.globalMetricValueInput.value = "";
            }
            if (refs.globalMetricMin) {
                refs.globalMetricMin.value = "";
            }
            if (refs.globalMetricMax) {
                refs.globalMetricMax.value = "";
            }
            refs.globalMetricValueRow?.classList.remove("d-none");
            refs.globalMetricRangeRow?.classList.add("d-none");
            return;
        }

        const selectedAtFetchStart = attrCode;
        const valuesPayload = await fetchFilterOptionsPayload(selectedAtFetchStart);
        if (requestId !== state.globalMetricRefreshRequestId) {
            return;
        }
        const selectedNow = (refs.globalMetricCode?.value || "").trim();
        if (selectedNow !== selectedAtFetchStart) {
            return;
        }

        const rawValues = Array.isArray(valuesPayload?.attributeValues)
            ? valuesPayload.attributeValues
                .map((item) => String(item?.code || item?.name || "").trim())
                .filter((value) => value.length > 0)
            : [];
        const parsedNumbers = rawValues
            .map((value) => Number(value.replace(",", ".")))
            .filter((value) => Number.isFinite(value));
        const numeric = rawValues.length > 0 && parsedNumbers.length === rawValues.length;
        const safeMin = numeric ? Number(Math.min(...parsedNumbers).toFixed(4)) : 0;
        const safeMax = numeric ? Number(Math.max(...parsedNumbers).toFixed(4)) : 0;
        state.globalMetricMetaByCode[attrCode] = {numeric, min: safeMin, max: safeMax};
        if (numeric && parsedNumbers.length > 0) {
            refs.globalMetricValueRow?.classList.add("d-none");
            refs.globalMetricRangeRow?.classList.remove("d-none");
            if (refs.globalMetricMin && (shouldResetValues || !refs.globalMetricMin.value)) {
                refs.globalMetricMin.value = String(safeMin);
            }
            if (refs.globalMetricMax && (shouldResetValues || !refs.globalMetricMax.value)) {
                refs.globalMetricMax.value = safeMax > 0 ? String(safeMax) : "";
            }
            if (refs.globalMetricValueInput) {
                refs.globalMetricValueInput.value = "";
            }
            if (refs.globalMetricValueSelect) {
                refs.globalMetricValueSelect.value = "";
            }
            setupGlobalMetricNumericSliders(safeMin, safeMax);
            syncGlobalMetricNumericRangeFromInputs();
        } else {
            const uniqueValues = Array.from(new Set(rawValues)).sort((a, b) => a.localeCompare(b, "ru"));
            refs.globalMetricValueRow?.classList.remove("d-none");
            refs.globalMetricRangeRow?.classList.add("d-none");
            const current = refs.globalMetricValueSelect?.value || "";
            if (refs.globalMetricValueSelect) {
                refs.globalMetricValueSelect.innerHTML = [
                    "<option value=''>Не выбрано</option>",
                    ...uniqueValues.map((value) => `<option value="${escapeHtml(value)}">${escapeHtml(value)}</option>`)
                ].filter(Boolean).join("");
                const nextSelected = !shouldResetValues && uniqueValues.includes(current)
                    ? current
                    : "";
                refs.globalMetricValueSelect.value = nextSelected;
                if (refs.globalMetricValueInput) {
                    refs.globalMetricValueInput.value = nextSelected;
                }
            }
            if (refs.globalMetricMin) {
                refs.globalMetricMin.value = "";
            }
            if (refs.globalMetricMax) {
                refs.globalMetricMax.value = "";
            }
        }
    }

    function setupGlobalMetricNumericSliders(safeMin, safeMax) {
        const min = Number.isFinite(safeMin) ? safeMin : 0;
        const max = safeMax > min ? safeMax : (min + 1);
        const step = max > 100 ? 1 : 0.01;
        [refs.globalMetricMinRange, refs.globalMetricMaxRange].forEach((slider) => {
            if (!slider) {
                return;
            }
            slider.min = String(min);
            slider.max = String(max);
            slider.step = String(step);
        });
    }

    function syncGlobalMetricNumericRangeFromInputs() {
        if (!refs.globalMetricMin || !refs.globalMetricMax) {
            return;
        }
        let min = Number(refs.globalMetricMin.value || 0);
        let max = Number(refs.globalMetricMax.value || 0);
        if (!Number.isFinite(min)) min = 0;
        if (!Number.isFinite(max)) max = 0;
        if (min > max) {
            const tmp = min;
            min = max;
            max = tmp;
        }
        refs.globalMetricMin.value = String(min);
        refs.globalMetricMax.value = String(max);
        if (refs.globalMetricMinRange) refs.globalMetricMinRange.value = String(min);
        if (refs.globalMetricMaxRange) refs.globalMetricMaxRange.value = String(max);
    }

    function syncGlobalMetricNumericRangeFromSliders(changedSide) {
        if (!refs.globalMetricMinRange || !refs.globalMetricMaxRange) {
            return;
        }
        let min = Number(refs.globalMetricMinRange.value || 0);
        let max = Number(refs.globalMetricMaxRange.value || 0);
        if (changedSide === "min" && min > max) {
            max = min;
            refs.globalMetricMaxRange.value = String(max);
        }
        if (changedSide === "max" && max < min) {
            min = max;
            refs.globalMetricMinRange.value = String(min);
        }
        if (refs.globalMetricMin) refs.globalMetricMin.value = String(min);
        if (refs.globalMetricMax) refs.globalMetricMax.value = String(max);
    }

    function applyGlobalMetricToEventsFilter() {
        const attrCode = (refs.globalMetricCode?.value || "").trim();
        const meta = state.globalMetricMetaByCode[attrCode] || {};
        if (!attrCode) {
            if (refs.eventsAttributeCode) refs.eventsAttributeCode.value = "";
            if (refs.eventsAttributeValue) refs.eventsAttributeValue.value = "";
            return;
        }
        if (refs.eventsAttributeCode) {
            refs.eventsAttributeCode.value = attrCode;
        }
        if (meta.numeric) {
            if (refs.eventsAttributeValue) {
                const min = (refs.globalMetricMin?.value || "").trim();
                const max = (refs.globalMetricMax?.value || "").trim();
                refs.eventsAttributeValue.value = (min || max) ? `${min}..${max}` : "";
            }
        } else {
            const selectedValue = (refs.globalMetricValueSelect?.value || "").trim();
            const manualValue = (refs.globalMetricValueInput?.value || "").trim();
            const picked = (selectedValue || manualValue).trim();
            if (refs.eventsAttributeValue) {
                refs.eventsAttributeValue.value = picked;
            }
        }
    }

    async function applyGlobalCompareGhostOnly() {
        const ghostChecked = resolveGlobalInlineCompareMode() === "overlay";
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            state.inlineCompareModeOverriddenBySource[canvasId] = false;
            state.inlineCompareGhostBySource[canvasId] = ghostChecked;
            state.inlineCompareModeBySource[canvasId] = resolveGlobalInlineCompareMode();
            state.miniKpiCompareModeOverriddenBySource[canvasId] = false;
            delete state.miniKpiCompareModeBySource[canvasId];
            state.expandedCompareModeOverriddenBySource[canvasId] = false;
            delete state.expandedCompareModeBySource[canvasId];
        });
        if (refs.universalCompareGhost) {
            refs.universalCompareGhost.checked = ghostChecked;
        }
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            syncInlineCompareModeSelectValues(canvasId);
            syncInlineCompareModeResetVisibility(canvasId);
        });
        if (state.globalCompareEnabled) {
            await Promise.all(Array.from(INLINE_COMPARE_CHART_IDS).map(async (canvasId) => {
                if (canvasId === "chart-event-kpi") {
                    return;
                }
                if (resolveInlineCompareMode(canvasId) === "overlay" || state.inlineCompareEnabled[canvasId]) {
                    await applyInlineComparePresetToChart(canvasId);
                }
            }));
            await loadOverview();
            if (refs.universalCompareGhost) {
                await withUniversalChartLoaders(() => loadUniversal());
            }
        }
    }

    async function setInlineCompareEnabledForAll(enabled, options = {}) {
        const skipOverridden = options.skipOverridden === true;
        const ids = Array.from(INLINE_COMPARE_CHART_IDS);
        for (const canvasId of ids) {
            if (skipOverridden && state.inlineCompareModeOverriddenBySource?.[canvasId]) {
                continue;
            }
            if (!!state.inlineCompareEnabled[canvasId] === enabled) {
                continue;
            }
            await toggleInlineCompareChart(canvasId, null);
        }
    }

    function setEventKpiInlineCompareLayoutState() {
        clearEventKpiMiniCompareState();
    }

    function clearGlobalCompareStateForInheritedCharts() {
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            state.inlineCompareModeBySource[canvasId] = "off";
            state.inlineCompareGhostBySource[canvasId] = false;
            state.inlineCompareModeOverriddenBySource[canvasId] = false;
            state.inlineComparePresetOverriddenBySource[canvasId] = false;
            state.miniKpiCompareModeOverriddenBySource[canvasId] = false;
            delete state.miniKpiCompareModeBySource[canvasId];
            state.expandedCompareModeOverriddenBySource[canvasId] = false;
            delete state.expandedCompareModeBySource[canvasId];
            if (canvasId === "chart-event-kpi") {
                clearEventKpiMiniCompareState();
            } else if (state.inlineCompareEnabled[canvasId]) {
                disableInlineCompareLayout(canvasId);
                state.inlineCompareEnabled[canvasId] = false;
            }
            delete state.expandedRangesBySource[canvasId];
            delete state.expandedBucketBySource[canvasId];
        });
    }

    async function applyGlobalBeforeRangeToAllCharts() {
        const ranges = resolveGlobalBeforeRange();
        if (refs.globalBeforeFrom) refs.globalBeforeFrom.value = ranges.beforeFrom || "";
        if (refs.globalBeforeTo) refs.globalBeforeTo.value = ranges.beforeTo || "";
        await ensureAllTimeRangeLoaded();
        if (!isGlobalBeforeRangeCoveredByData(ranges)) {
            showNoCompareDataWarningOnce(ranges);
            setGlobalCompareMode("off");
            syncGlobalCompareControlsVisibility();
            await applyGlobalCompareToAllCharts();
            return;
        }
        const loaderCanvasIds = Array.from(INLINE_COMPARE_CHART_IDS);
        let overviewLoadersHidden = false;
        const hideOverviewLoaders = () => {
            if (overviewLoadersHidden) {
                return;
            }
            overviewLoadersHidden = true;
            loaderCanvasIds.forEach((canvasId) => setChartActionLoading(canvasId, false));
        };
        loaderCanvasIds.forEach((canvasId) => setChartActionLoading(canvasId, true));
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            state.inlineComparePresetOverriddenBySource[canvasId] = false;
            state.inlineCompareModeOverriddenBySource[canvasId] = false;
            state.inlineCompareModeBySource[canvasId] = resolveGlobalInlineCompareMode();
            state.miniKpiCompareModeOverriddenBySource[canvasId] = false;
            delete state.miniKpiCompareModeBySource[canvasId];
            state.expandedCompareModeOverriddenBySource[canvasId] = false;
            delete state.expandedCompareModeBySource[canvasId];
            state.expandedRangesBySource[canvasId] = {
                beforeFrom: ranges.beforeFrom,
                beforeTo: ranges.beforeTo,
                afterFrom: ranges.afterFrom,
                afterTo: ranges.afterTo
            };
        });
        try {
            await Promise.all(Array.from(INLINE_COMPARE_CHART_IDS).map(async (canvasId) => {
                if (canvasId === "chart-event-kpi") {
                    return;
                }
                if (resolveInlineCompareMode(canvasId) === "overlay" || state.inlineCompareEnabled[canvasId]) {
                    await applyStoredExpandedRangesToCharts(canvasId);
                    await applyInlineComparePresetToChart(canvasId);
                }
            }));
            await loadOverview();
            await waitForChartPaint(1);
            hideOverviewLoaders();
            if (refs.stageMetricForm) {
                applyGlobalCompareModeToStageMetrics(resolveGlobalInlineCompareMode());
                await withStageMetricLoaders("all", () => loadStageMetrics());
            }
            if (refs.universalCompareEnabled) {
                refs.universalCompareEnabled.checked = true;
                if (refs.universalBeforeFrom) refs.universalBeforeFrom.value = ranges.beforeFrom;
                if (refs.universalBeforeTo) refs.universalBeforeTo.value = ranges.beforeTo;
                await withUniversalChartLoaders(() => loadUniversal());
            }
        } finally {
            hideOverviewLoaders();
        }
    }

    async function applyGlobalCompareToAllCharts(options = {}) {
        const action = options.action || "";
        const globalMode = resolveGlobalInlineCompareMode();
        setGlobalCompareMode(globalMode);
        syncGlobalCompareControlsVisibility();
        if (globalMode === "off") {
            const offStarted = performance.now();
            state.globalCompareNoDataWarningKey = "";
            clearGlobalCompareStateForInheritedCharts();
            const universalOffTask = (refs.universalCompareEnabled?.checked || UNIVERSAL_COMPARE_FOLLOWS_GLOBAL)
                ? (async () => {
                    if (refs.universalCompareEnabled) {
                        refs.universalCompareEnabled.checked = false;
                    }
                    await withUniversalChartLoaders(
                        () => loadUniversal({
                            action,
                            allowStaleMainPayload: action === "compare_to_off"
                        }),
                        {action}
                    );
                })()
                : Promise.resolve();
            await universalOffTask;
            console.info("[UNIVERSAL_PERF] frontend compare_to_off path", {
                action,
                universalDoneMs: Math.round(performance.now() - offStarted)
            });
            await reloadInlineCompareChartSources();
            INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
                syncInlineCompareModeSelectValues(canvasId);
                syncInlineCompareModeResetVisibility(canvasId);
            });
            if (state.expandedChart.sourceCanvasId && INLINE_COMPARE_CHART_IDS.has(state.expandedChart.sourceCanvasId)) {
                rebuildExpandedChartForCurrentMode(state.expandedChart.sourceCanvasId);
            }
            if (refs.stageMetricForm) {
                applyGlobalCompareModeToStageMetrics("off");
                await withStageMetricLoaders("all", () => loadStageMetrics());
            }
            return;
        }

        await ensureAllTimeRangeLoaded();
        const ranges = resolveGlobalBeforeRange();
        if (!isGlobalBeforeRangeCoveredByData(ranges)) {
            showNoCompareDataWarningOnce(ranges);
            setGlobalCompareMode("off");
            syncGlobalCompareControlsVisibility();
            await applyGlobalCompareToAllCharts();
            return;
        }

        const loaderCanvasIds = Array.from(INLINE_COMPARE_CHART_IDS);
        let overviewLoadersHidden = false;
        const hideOverviewLoaders = () => {
            if (overviewLoadersHidden) {
                return;
            }
            overviewLoadersHidden = true;
            loaderCanvasIds.forEach((canvasId) => setChartActionLoading(canvasId, false));
        };
        loaderCanvasIds.forEach((canvasId) => setChartActionLoading(canvasId, true));
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            state.inlineComparePresetOverriddenBySource[canvasId] = false;
            state.inlineComparePresetBySource[canvasId] = resolveTopInlineComparePresetOrDefault();
            state.inlineCompareModeOverriddenBySource[canvasId] = false;
            state.inlineCompareGhostBySource[canvasId] = globalMode === "overlay";
            state.inlineCompareModeBySource[canvasId] = globalMode;
            state.miniKpiCompareModeOverriddenBySource[canvasId] = false;
            delete state.miniKpiCompareModeBySource[canvasId];
            state.expandedCompareModeOverriddenBySource[canvasId] = false;
            delete state.expandedCompareModeBySource[canvasId];
            state.expandedRangesBySource[canvasId] = {
                beforeFrom: ranges.beforeFrom,
                beforeTo: ranges.beforeTo,
                afterFrom: ranges.afterFrom,
                afterTo: ranges.afterTo
            };
        });
        try {
            const ids = Array.from(INLINE_COMPARE_CHART_IDS);
            for (const canvasId of ids) {
                const shouldSplit = resolveInlineCompareMode(canvasId) === "split";
                if (canvasId === "chart-event-kpi") {
                    setEventKpiInlineCompareLayoutState();
                    continue;
                }
                if (!!state.inlineCompareEnabled[canvasId] === shouldSplit) {
                    continue;
                }
                await toggleInlineCompareChart(canvasId, null);
            }
            // Rebuild overview/stages once for all compare charts to avoid staggered chart-by-chart updates.
            await reloadInlineCompareChartSources();
            if (globalMode === "overlay") {
                await Promise.all(ids
                    .filter((canvasId) => canvasId !== "chart-event-kpi")
                    .map((canvasId) => applyInlineComparePresetToChart(canvasId)));
            }
            INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
                syncInlineCompareModeSelectValues(canvasId);
                syncInlineCompareModeResetVisibility(canvasId);
            });
            if (state.expandedChart.sourceCanvasId && INLINE_COMPARE_CHART_IDS.has(state.expandedChart.sourceCanvasId)) {
                rebuildExpandedChartForCurrentMode(state.expandedChart.sourceCanvasId);
            }
            await waitForChartPaint(1);
            hideOverviewLoaders();

            const secondaryTasks = [];

            if (refs.stageMetricForm) {
                applyGlobalCompareModeToStageMetrics(globalMode);
                secondaryTasks.push(withStageMetricLoaders("all", () => loadStageMetrics()));
            }
            if (refs.universalCompareEnabled) {
                refs.universalCompareEnabled.checked = true;
                if (refs.universalCompareGhost) {
                    refs.universalCompareGhost.checked = globalMode === "overlay";
                }
                secondaryTasks.push(withUniversalChartLoaders(() => loadUniversal()));
            }

            if (secondaryTasks.length) {
                await Promise.all(secondaryTasks);
            }
        } finally {
            hideOverviewLoaders();
        }
    }

    function resetInlineComparePresetsFromTopFilter() {
        const inheritedPreset = resolveTopInlineComparePresetOrDefault();
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            state.inlineComparePresetBySource[canvasId] = inheritedPreset;
            state.inlineComparePresetOverriddenBySource[canvasId] = false;
        });
        const controls = refs.analyticsPage?.querySelectorAll("[data-inline-compare-preset]") || [];
        controls.forEach((control) => {
            control.value = inheritedPreset;
        });
    }

    function findChartPanel(canvasId) {
        const canvas = document.getElementById(canvasId);
        return canvas?.closest(".analytics-panel") || null;
    }

    function cloneChartConfig(config) {
        if (typeof structuredClone === "function") {
            try {
                return structuredClone(config);
            } catch (_error) {
                // Chart configs may contain functions (e.g., tooltip callbacks) that are not structured-cloneable.
            }
        }
        return deepClonePreservingFunctions(config);
    }

    function deepClonePreservingFunctions(value, seen = new WeakMap()) {
        if (value == null) {
            return value;
        }
        const valueType = typeof value;
        if (valueType === "function" || valueType !== "object") {
            return value;
        }
        if (value instanceof Date) {
            return new Date(value.getTime());
        }
        if (Array.isArray(value)) {
            if (seen.has(value)) {
                return seen.get(value);
            }
            const arrClone = [];
            seen.set(value, arrClone);
            for (const item of value) {
                arrClone.push(deepClonePreservingFunctions(item, seen));
            }
            return arrClone;
        }
        if (seen.has(value)) {
            return seen.get(value);
        }
        const objClone = {};
        seen.set(value, objClone);
        Object.keys(value).forEach((key) => {
            objClone[key] = deepClonePreservingFunctions(value[key], seen);
        });
        return objClone;
    }

    function setChartExpandedTicks(chart, expanded) {
        if (!chart?.options?.scales?.x) {
            return;
        }
        const isEventKpi = chart?.canvas?.id === "chart-event-kpi"
            || chart?.canvas?.id === "chart-event-kpi-compare-inline"
            || chart?.canvas?.id === "chart-universal-event-kpi"
            || chart?.canvas?.id === "chart-universal-event-kpi-compare-inline";
        if (isEventKpi) {
            if (!chart.options.scales.x.ticks) {
                chart.options.scales.x.ticks = {};
            }
            chart.options.scales.x.ticks.autoSkip = false;
            chart.options.scales.x.ticks.maxTicksLimit = 1000;
            return;
        }
        if (!chart.options.scales.x.ticks) {
            chart.options.scales.x.ticks = {};
        }
        chart.options.scales.x.ticks.autoSkip = true;
        chart.options.scales.x.ticks.maxTicksLimit = expanded ? 20 : 8;
    }

    function getGridColumnCount(gridEl) {
        const template = getComputedStyle(gridEl).gridTemplateColumns || "";
        const columns = template
            .split(" ")
            .map((part) => part.trim())
            .filter((part) => part && part !== "/")
            .length;
        return Math.max(1, columns || 1);
    }

    function resolveStageMetricPrimaryCanvasId(canvasId) {
        const id = String(canvasId || "").trim();
        return STAGE_METRIC_SOURCE_CANVAS_BY_COMPARE[id] || id;
    }

    function isStageMetricCompareCanvas(canvasId) {
        return !!STAGE_METRIC_SOURCE_CANVAS_BY_COMPARE[String(canvasId || "").trim()];
    }

    function isStageMetricPrimaryCanvas(canvasId) {
        return STAGE_METRIC_PRIMARY_CANVAS_IDS.has(String(canvasId || "").trim());
    }

    function getStageMetricExpandedCompareCanvasId(canvasId) {
        const sourceCanvasId = resolveStageMetricPrimaryCanvasId(canvasId);
        if (sourceCanvasId === "chart-stage-metric-series" && isStageMetricSplitCompare()) {
            return STAGE_METRIC_COMPARE_CANVAS_BY_SOURCE[sourceCanvasId] || "";
        }
        if (sourceCanvasId === "chart-stage-metric-text" && isStageMetricTextSplitCompare()) {
            return STAGE_METRIC_COMPARE_CANVAS_BY_SOURCE[sourceCanvasId] || "";
        }
        return "";
    }

    function getChartOwningTab(canvasId) {
        const id = String(canvasId || "").trim();
        if (isStageMetricPrimaryCanvas(id) || isStageMetricCompareCanvas(id)) {
            return "metrics";
        }
        if (UNIVERSAL_COMPARE_CHART_IDS.has(id)) {
            return "universal";
        }
        return "overview";
    }

    function getOpenedExpandedMetricsHost() {
        const sourceCanvasId = state.expandedChart.sourceCanvasId || "";
        if (!isStageMetricPrimaryCanvas(sourceCanvasId) && !isStageMetricCompareCanvas(sourceCanvasId)) {
            return null;
        }
        return state.expandedChart.containerEl || null;
    }

    function attachOpenedExpandedMetricsHostToActiveLoader() {
        const host = getOpenedExpandedMetricsHost();
        if (!host || !state.sectionLoaderScopes?.["stage-metrics"]) {
            return;
        }
        const scopeKey = "stage-metrics";
        const previousHosts = uniqueElements(state.sectionLoaderHostsByScope?.[scopeKey] || []);
        if (previousHosts.includes(host)) {
            return;
        }
        if (!state.sectionLoaderHostsByScope) {
            state.sectionLoaderHostsByScope = {};
        }
        state.sectionLoaderHostsByScope[scopeKey] = uniqueElements([...previousHosts, host]);
        const boundsByHost = state.sectionLoaderBoundsByScope?.[scopeKey];
        if (boundsByHost instanceof Map) {
            boundsByHost.set(host, [host]);
        }
        setSectionLocalLoading(host, [host], true);
    }

    function isOpenedExpandedStageMetricSource(canvasId) {
        const opened = resolveStageMetricPrimaryCanvasId(state.expandedChart.sourceCanvasId || "");
        const requested = resolveStageMetricPrimaryCanvasId(canvasId || "");
        return !!opened && opened === requested && !!state.expandedChart.containerEl;
    }

    async function rebuildOpenedStageMetricExpanded(canvasId) {
        const sourceCanvasId = resolveStageMetricPrimaryCanvasId(canvasId || state.expandedChart.sourceCanvasId || "");
        if (!isOpenedExpandedStageMetricSource(sourceCanvasId)) {
            return;
        }
        const sourceCanvas = document.getElementById(sourceCanvasId);
        if (!sourceCanvas) {
            collapseExpandedChart();
            return;
        }
        collapseExpandedChart();
        toggleExpandedChart(sourceCanvasId);
        attachOpenedExpandedMetricsHostToActiveLoader();
        await waitForChartPaint(2);
    }

    async function renderExpandedEventKpiAfterMiniLifecycle(canvasId) {
        const hadPendingMiniRender = !!state.eventKpiMiniRenderPromise && !state.eventKpiFirstLoadCompleted;
        if (hadPendingMiniRender) {
            setExpandedChartLoading(canvasId, true);
        }
        try {
            if (hadPendingMiniRender) {
                await waitForEventKpiMiniRenderIfPending();
            }
            if (state.expandedChart.sourceCanvasId !== canvasId || !state.expandedChart.containerEl) {
                return;
            }
            const ranges = resolveExpandedRangesForMode(canvasId, resolveExpandedCompareMode(canvasId) !== "off");
            await renderExpandedChartByRanges(canvasId, ranges, getExpandedEventRenderOptions(canvasId));
            if (hadPendingMiniRender) {
                await waitForChartPaint(1);
            }
        } finally {
            if (hadPendingMiniRender) {
                setExpandedChartLoading(canvasId, false);
            }
        }
    }

    function renderExpandedChartClone(canvasId) {
        if (!canvasId) {
            return;
        }
        if (state.expandedChart.customRangeActive) {
            return;
        }
        if (canvasId === "chart-event-kpi") {
            void renderExpandedEventKpiAfterMiniLifecycle(canvasId);
            return;
        }
        const sourceConfig = canvasId === "chart-event-kpi"
            ? (state.kpiFullChartConfigs[canvasId] || state.chartConfigs[canvasId])
            : state.chartConfigs[canvasId];
        const container = state.expandedChart.containerEl;
        if (!sourceConfig || !container) {
            return;
        }
        const expandedCanvas = container.querySelector(`#chart-expanded-${canvasId}`);
        if (!expandedCanvas) {
            return;
        }
        const config = cloneChartConfig(sourceConfig);
        config.options = config.options || {};
        config.options.responsive = true;
        config.options.maintainAspectRatio = false;
        if (config.options.scales?.x?.ticks) {
            if (canvasId === "chart-event-kpi") {
                config.options.scales.x.ticks.autoSkip = false;
                config.options.scales.x.ticks.maxTicksLimit = 1000;
            } else {
                config.options.scales.x.ticks.autoSkip = true;
                config.options.scales.x.ticks.maxTicksLimit = 20;
            }
        }
        if (config.options.plugins?.decimation) {
            config.options.plugins.decimation.samples = 260;
        }
        if (state.expandedChart.instance) {
            state.expandedChart.instance.destroy();
        }
        state.expandedChart.instance = new Chart(expandedCanvas.getContext("2d"), config);

        if (state.expandedChart.compareInstance) {
            state.expandedChart.compareInstance.destroy();
            state.expandedChart.compareInstance = null;
        }
        const compareSourceCanvasId = state.inlineCompareCanvasBySource[canvasId] || getStageMetricExpandedCompareCanvasId(canvasId) || "";
        const compareSourceConfig = compareSourceCanvasId
            ? (
                canvasId === "chart-event-kpi"
                    ? (state.kpiFullChartConfigs[compareSourceCanvasId] || state.chartConfigs[compareSourceCanvasId])
                    : state.chartConfigs[compareSourceCanvasId]
            )
            : null;
        const compareExpandedCanvas = container.querySelector(`[data-expanded-compare-for='${canvasId}']`);
        if (!compareSourceConfig || !compareExpandedCanvas) {
            return;
        }
        const compareConfig = cloneChartConfig(compareSourceConfig);
        compareConfig.options = compareConfig.options || {};
        compareConfig.options.responsive = true;
        compareConfig.options.maintainAspectRatio = false;
        if (compareConfig.options.scales?.x?.ticks) {
            if (canvasId === "chart-event-kpi") {
                compareConfig.options.scales.x.ticks.autoSkip = false;
                compareConfig.options.scales.x.ticks.maxTicksLimit = 1000;
            } else {
                compareConfig.options.scales.x.ticks.autoSkip = true;
                compareConfig.options.scales.x.ticks.maxTicksLimit = 20;
            }
        }
        if (compareConfig.options.plugins?.decimation) {
            compareConfig.options.plugins.decimation.samples = 260;
        }
        state.expandedChart.compareInstance = new Chart(compareExpandedCanvas.getContext("2d"), compareConfig);
    }

    function setupExpandedZoomControls(container) {
        if (!container || container.querySelector(".analytics-expanded-zoom-range-x")) {
            return;
        }
        const xGroup = document.createElement("div");
        xGroup.className = "analytics-expanded-zoom-group analytics-expanded-zoom-group-x";
        xGroup.innerHTML = `
            <label class="analytics-expanded-zoom-label">X</label>
            <input type="range" class="form-range analytics-expanded-zoom-range-x" min="100" max="400" step="10" value="100" aria-label="Масштаб по X">
            <span class="analytics-expanded-zoom-value" data-zoom-axis="x">100%</span>
        `;
        const toolbarLeft = container.querySelector("[data-expanded-toolbar-left]");
        if (toolbarLeft) {
            toolbarLeft.insertBefore(xGroup, toolbarLeft.firstChild);
        } else {
            container.insertBefore(xGroup, container.firstChild);
        }

        const yControls = document.createElement("div");
        yControls.className = "analytics-expanded-zoom-group analytics-expanded-zoom-group-y";
        yControls.innerHTML = `
            <label class="analytics-expanded-zoom-label">Y</label>
            <input type="range" class="form-range analytics-expanded-zoom-range-y" min="100" max="250" step="10" value="100" aria-label="Масштаб по Y">
            <span class="analytics-expanded-zoom-value" data-zoom-axis="y">100%</span>
        `;
        container.appendChild(yControls);

        const rangeX = xGroup.querySelector(".analytics-expanded-zoom-range-x");
        const rangeY = yControls.querySelector(".analytics-expanded-zoom-range-y");
        const valueX = xGroup.querySelector("[data-zoom-axis='x']");
        const valueY = yControls.querySelector("[data-zoom-axis='y']");
        const zoomHosts = Array.from(container.querySelectorAll(".analytics-expanded-zoom-host"));
        const scrolls = Array.from(container.querySelectorAll(".analytics-expanded-scroll"));
        const expandedWraps = Array.from(container.querySelectorAll(".analytics-chart-wrap-expanded"));
        const isCompareExpanded = expandedWraps.length >= 2;
        const sourceCanvasId = state.expandedChart.sourceCanvasId || "";
        const compareSourceCanvasId = state.inlineCompareCanvasBySource[sourceCanvasId] || "";
        const isKpiExpanded = sourceCanvasId === "chart-event-kpi";
        const isKpiCompareExpanded = isKpiExpanded && isCompareExpanded;
        const isUniversalExpanded = UNIVERSAL_COMPARE_CHART_IDS.has(sourceCanvasId);
        const baseViewportHeight = isKpiExpanded ? 569 : 489;
        const baseContentHeight = baseViewportHeight;
        const baseExpandedKpiPlotHeight = 300;
        if (rangeY && !isCompareExpanded) {
            rangeY.max = "180";
        }
        expandedWraps.forEach((wrap) => {
            wrap.style.setProperty("aspect-ratio", "auto", "important");
        });
        scrolls.forEach((scroll) => {
            scroll.style.setProperty("height", `${baseViewportHeight}px`);
            scroll.style.setProperty("min-height", `${baseViewportHeight}px`);
            scroll.style.setProperty("max-height", `${baseViewportHeight}px`);
        });

        const getExpandedKpiLabelsByHost = (host) => {
            if (!host) {
                return [];
            }
            const canvasEl = host.querySelector("canvas");
            if (!canvasEl) {
                return [];
            }
            const isCompareCanvas = canvasEl.id === `chart-expanded-${sourceCanvasId}-compare`;
            const chartInstance = isCompareCanvas
                ? state.expandedChart.compareInstance
                : state.expandedChart.instance;
            if (Array.isArray(chartInstance?.data?.labels)) {
                return chartInstance.data.labels;
            }
            const fallbackCanvasId = isCompareCanvas ? compareSourceCanvasId : sourceCanvasId;
            const fallbackConfig = state.chartConfigs[fallbackCanvasId];
            return Array.isArray(fallbackConfig?.data?.labels) ? fallbackConfig.data.labels : [];
        };

        const getExpandedChartInstanceByHost = (host) => {
            const canvasEl = host?.querySelector("canvas");
            if (!canvasEl) {
                return null;
            }
            const compareCanvasId = `chart-expanded-${sourceCanvasId}-compare`;
            const primaryCanvasId = `chart-expanded-${sourceCanvasId}`;
            if (canvasEl.id === compareCanvasId) {
                return state.expandedChart.compareInstance || null;
            }
            if (canvasEl.id === primaryCanvasId) {
                return state.expandedChart.instance || null;
            }
            return null;
        };

        const applyZoomX = (zoomValue) => {
            const widthPercent = Math.max(100, Number(zoomValue) || 100);
            if (isKpiExpanded) {
                const mode = isKpiCompareExpanded ? "expanded-compare" : "expanded-single";
                const xScale = widthPercent / 100;
                zoomHosts.forEach((host, index) => {
                    const scrollBody = scrolls[index] || host.parentElement;
                    const viewportWidth = Math.max(1, scrollBody?.clientWidth || host.clientWidth || 0);
                    const labels = getExpandedKpiLabelsByHost(host);
                    const targetWidth = resolveKpiChartWidth({
                        columnsCount: labels.length,
                        viewportWidth,
                        mode,
                        xScale
                    });
                    host.style.width = `${targetWidth}px`;
                    host.style.minWidth = `${targetWidth}px`;
                });
            } else {
                zoomHosts.forEach((host) => {
                    host.style.width = `${widthPercent}%`;
                    host.style.minWidth = `${widthPercent}%`;
                });
            }
            zoomHosts.forEach((host) => {
                const chartInstance = getExpandedChartInstanceByHost(host);
                if (!chartInstance) {
                    return;
                }
                chartInstance.resize();
                chartInstance.update("none");
            });
            if (valueX) {
                valueX.textContent = `${widthPercent}%`;
            }
        };
        const applyZoomY = (zoomValue) => {
            const yPercent = Math.max(100, Number(zoomValue) || 100);
            const ratio = yPercent / 100;
            expandedWraps.forEach((wrap, index) => {
                const scrollBody = scrolls[index] || wrap.parentElement;
                const viewportHeight = Math.max(baseViewportHeight, scrollBody?.clientHeight || baseViewportHeight);
                const labels = isKpiExpanded ? getExpandedKpiLabelsByHost(zoomHosts[index]) : [];
                const logicalBaseHeight = isKpiExpanded
                    ? calcKpiContentHeight(labels, baseExpandedKpiPlotHeight)
                    : baseContentHeight;
                const scaledHeight = Math.max(viewportHeight, Math.round(logicalBaseHeight * ratio));
                wrap.style.setProperty("height", `${scaledHeight}px`, "important");
                wrap.style.setProperty("min-height", `${scaledHeight}px`, "important");
                wrap.style.setProperty("max-height", `${scaledHeight}px`, "important");
                wrap.style.setProperty("aspect-ratio", "auto", "important");
            });
            if (valueY) {
                valueY.textContent = `${yPercent}%`;
            }
        };

        rangeX?.addEventListener("input", () => {
            applyZoomX(rangeX.value);
            if (isUniversalExpanded) {
                const {x} = getUniversalZoomRefsByCanvasId(sourceCanvasId);
                if (x) {
                    x.value = String(rangeX.value || "100");
                }
            }
        });
        rangeY?.addEventListener("input", () => {
            applyZoomY(rangeY.value);
            if (isUniversalExpanded) {
                const {y} = getUniversalZoomRefsByCanvasId(sourceCanvasId);
                if (y) {
                    y.value = String(rangeY.value || "100");
                }
            }
        });
        if (isUniversalExpanded) {
            const miniZoom = getUniversalZoomRefsByCanvasId(sourceCanvasId);
            if (rangeX && miniZoom.x) {
                rangeX.value = String(miniZoom.x.value || "100");
            }
            if (rangeY && miniZoom.y) {
                rangeY.value = String(miniZoom.y.value || "100");
            }
        }
        applyZoomX(rangeX?.value || 100);
        applyZoomY(rangeY?.value || 100);
        window.requestAnimationFrame(() => applyZoomX(rangeX?.value || 100));
        window.requestAnimationFrame(() => applyZoomY(rangeY?.value || 100));

        if (scrolls.length >= 2) {
            let syncing = false;
            const syncTo = (source, target) => {
                if (syncing) {
                    return;
                }
                syncing = true;
                if (isKpiCompareExpanded) {
                    syncScrollLeftByRatio(source, target);
                    syncScrollTopByRatio(source, target);
                } else {
                    target.scrollLeft = source.scrollLeft;
                    target.scrollTop = source.scrollTop;
                }
                syncing = false;
            };
            scrolls[0].addEventListener("scroll", () => syncTo(scrolls[0], scrolls[1]));
            scrolls[1].addEventListener("scroll", () => syncTo(scrolls[1], scrolls[0]));
        }
    }

    function getExpandedEventFilterState(canvasId) {
        if (!state.expandedEventFilterBySource) {
            state.expandedEventFilterBySource = {};
        }
        if (!state.expandedEventFilterBySource[canvasId]) {
            state.expandedEventFilterBySource[canvasId] = {
                includeOverall: true,
                codes: []
            };
        }
        return state.expandedEventFilterBySource[canvasId];
    }

    function getExpandedEventOptions(canvasId) {
        const stored = state.expandedEventOptionsBySource?.[canvasId];
        if (Array.isArray(stored) && stored.length) {
            return stored;
        }
        return (state.dictionaries?.eventTypes || [])
            .map((item) => ({
                code: item.code,
                name: item.name || item.code
            }))
            .sort((left, right) => String(left.name || left.code || "").localeCompare(String(right.name || right.code || ""), "ru", {sensitivity: "base"}));
    }

    function getExpandedEventRenderOptions(canvasId) {
        if (!EXPANDED_EVENT_FILTER_CHART_IDS.has(canvasId)) {
            return {};
        }
        const eventFilterState = getExpandedEventFilterState(canvasId);
        const options = {
            includeOverall: !!eventFilterState.includeOverall,
            eventCodes: Array.isArray(eventFilterState.codes) ? [...eventFilterState.codes] : []
        };
        if (canvasId === "chart-latency") {
            options.latencyMetric = getExpandedLatencyMetricMode(canvasId);
        }
        if (canvasId === "chart-stage-latency") {
            options.stageLatencyEventMetric = getExpandedStageLatencyEventMetricMode(canvasId);
        }
        return options;
    }

    function getExpandedLatencyMetricMode(canvasId) {
        if (!state.expandedLatencyMetricBySource) {
            state.expandedLatencyMetricBySource = {};
        }
        const current = String(state.expandedLatencyMetricBySource[canvasId] || "").trim().toLowerCase();
        if (["avg", "p95", "p99"].includes(current)) {
            return current;
        }
        state.expandedLatencyMetricBySource[canvasId] = "p95";
        return "p95";
    }

    function getExpandedStageLatencyEventMetricMode(canvasId) {
        if (!state.expandedStageLatencyEventMetricBySource) {
            state.expandedStageLatencyEventMetricBySource = {};
        }
        const current = String(state.expandedStageLatencyEventMetricBySource[canvasId] || "").trim().toLowerCase();
        if (["avg", "p95"].includes(current)) {
            return current;
        }
        state.expandedStageLatencyEventMetricBySource[canvasId] = "p95";
        return "p95";
    }

    async function fetchAvailableEventTypesForRanges(ranges, bucketOverride, includeCompareRange) {
        const buildParams = (fromLocal, toLocal) => {
            const params = mainParams();
            const fromIso = toIso(fromLocal);
            const toIsoValue = toIso(toLocal);
            if (fromIso) {
                params.set("from", fromIso);
            } else {
                params.delete("from");
            }
            if (toIsoValue) {
                params.set("to", toIsoValue);
            } else {
                params.delete("to");
            }
            if (bucketOverride != null) {
                const normalizedBucket = String(bucketOverride || "").trim();
                if (normalizedBucket) {
                    params.set("bucketMinutes", normalizedBucket);
                } else {
                    params.delete("bucketMinutes");
                }
            }
            return params;
        };
        const requests = [
            fetchJson(`${api("/overview")}?${buildParams(ranges.afterFrom, ranges.afterTo).toString()}`)
        ];
        if (includeCompareRange) {
            requests.push(fetchJson(`${api("/overview")}?${buildParams(ranges.beforeFrom, ranges.beforeTo).toString()}`));
        }
        const payloads = await Promise.all(requests);
        const byCode = new Map();
        payloads.forEach((payload) => {
            (payload?.eventBreakdown || []).forEach((row) => {
                const code = String(row?.eventTypeCode || "").trim();
                if (!code) {
                    return;
                }
                const name = String(row?.eventTypeName || code).trim();
                if (!byCode.has(code)) {
                    byCode.set(code, {code, name});
                }
            });
        });
        return Array.from(byCode.values())
            .sort((left, right) => String(left.name || left.code || "").localeCompare(String(right.name || right.code || ""), "ru", {sensitivity: "base"}));
    }

    function readMultiSelectValues(selectEl) {
        if (!selectEl) {
            return [];
        }
        return Array.from(selectEl.selectedOptions || [])
            .map((option) => (option.value || "").trim())
            .filter((value) => value.length > 0);
    }

    function syncMultiSelectValues(selectEl, values) {
        if (!selectEl) {
            return;
        }
        const selected = new Set(values || []);
        Array.from(selectEl.options || []).forEach((option) => {
            option.selected = selected.has(option.value);
        });
    }

    function eventHash(value) {
        const text = String(value || "");
        let hash = 0;
        for (let index = 0; index < text.length; index += 1) {
            hash = ((hash << 5) - hash) + text.charCodeAt(index);
            hash |= 0;
        }
        return Math.abs(hash);
    }

    function buildDistinctEventColors(eventCodes) {
        const codes = Array.isArray(eventCodes) ? eventCodes : [];
        const usedHues = new Set();
        const colorByCode = new Map();
        codes.forEach((code, index) => {
            const baseHue = eventHash(code) % 360;
            let hue = baseHue;
            let guard = 0;
            while (usedHues.has(hue) && guard < 360) {
                hue = (hue + 17) % 360;
                guard += 1;
            }
            usedHues.add(hue);
            const saturation = 68 + (index % 3) * 6;
            const lightness = 40 + (index % 4) * 5;
            colorByCode.set(code, `hsl(${hue} ${saturation}% ${lightness}%)`);
        });
        return colorByCode;
    }

    function withAlphaColor(color, alpha) {
        const safeAlpha = Math.max(0, Math.min(1, Number(alpha)));
        const text = String(color || "").trim();
        const hslaMatch = text.match(/^hsla\(\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)%\s*,\s*(\d+(?:\.\d+)?)%\s*,\s*([01]?(?:\.\d+)?)\s*\)$/i);
        if (hslaMatch) {
            return `hsla(${hslaMatch[1]}, ${hslaMatch[2]}%, ${hslaMatch[3]}%, ${safeAlpha})`;
        }
        const hslMatch = text.match(/^hsl\(\s*(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)%\s+(\d+(?:\.\d+)?)%\s*\)$/i);
        if (hslMatch) {
            return `hsla(${hslMatch[1]}, ${hslMatch[2]}%, ${hslMatch[3]}%, ${safeAlpha})`;
        }
        const rgbaMatch = text.match(/^rgba\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*([01]?(?:\.\d+)?)\s*\)$/i);
        if (rgbaMatch) {
            return `rgba(${rgbaMatch[1]}, ${rgbaMatch[2]}, ${rgbaMatch[3]}, ${safeAlpha})`;
        }
        const rgbMatch = text.match(/^rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$/i);
        if (rgbMatch) {
            return `rgba(${rgbMatch[1]}, ${rgbMatch[2]}, ${rgbMatch[3]}, ${safeAlpha})`;
        }
        const hexMatch = text.match(/^#([0-9a-f]{6})$/i);
        if (hexMatch) {
            const intColor = parseInt(hexMatch[1], 16);
            const r = (intColor >> 16) & 255;
            const g = (intColor >> 8) & 255;
            const b = intColor & 255;
            return `rgba(${r}, ${g}, ${b}, ${safeAlpha})`;
        }
        return text || "rgba(15,23,42,0.72)";
    }

    function stageLabelOrderRank(label) {
        const text = String(label || "").trim().toUpperCase();
        if (text.includes("FRONT")) return 1;
        if (text.includes("CONTROLLER")) return 2;
        if (text.includes("DATABASE")) return 3;
        if (text.includes("SERVICE")) return 4;
        if (text.includes("REPOSITORY")) return 5;
        return 100;
    }

    function setupExpandedGraphControls(container, canvasId) {
        if (!container || container.querySelector(".analytics-expanded-graph-controls")) {
            return;
        }
        const controls = document.createElement("div");
        controls.className = "analytics-expanded-graph-controls";
        const preset = resolveInlineComparePreset(canvasId);
        const topPreset = resolveTopInlineComparePresetOrDefault();
        const bucketValue = resolveExpandedBucket(canvasId);
        const supportsExpandedEventFilter = EXPANDED_EVENT_FILTER_CHART_IDS.has(canvasId);
        const singleEventSelectMode = EXPANDED_SINGLE_EVENT_FILTER_CHART_IDS.has(canvasId);
        const supportsLatencyMetricSelect = canvasId === "chart-latency";
        const supportsStageLatencyEventMetricSelect = canvasId === "chart-stage-latency";
        const eventFilterState = getExpandedEventFilterState(canvasId);
        const selectedLatencyMetric = getExpandedLatencyMetricMode(canvasId);
        const selectedStageLatencyEventMetric = getExpandedStageLatencyEventMetricMode(canvasId);
        const availableEventOptions = getExpandedEventOptions(canvasId);
        const eventOptionsHtml = availableEventOptions
            .map((item) => `<option value="${escapeHtml(item.code)}" ${eventFilterState.codes.includes(item.code) ? "selected" : ""}>${escapeHtml(item.name || item.code)}</option>`)
            .join("");
        const eventFilterHtml = supportsExpandedEventFilter ? `
                    <div class="analytics-expanded-events-inline">
                        <button type="button" class="btn btn-outline-dark btn-sm analytics-expanded-events-toggle" data-event-popup-toggle>
                            События
                        </button>
                        ${singleEventSelectMode ? `
                        <label class="form-check mb-0 analytics-expanded-events-overall-inline">
                            <input class="form-check-input" type="checkbox" data-event-overall-toggle ${eventFilterState.includeOverall ? "checked" : ""}>
                            <span class="form-check-label">Общая статистика</span>
                        </label>
                        ` : ""}
                        <div class="analytics-expanded-events-popup d-none" data-event-popup>
                            ${singleEventSelectMode
        ? `<select class="form-select form-select-sm" data-event-codes size="8">
                                ${eventOptionsHtml}
                            </select>`
        : `<select class="form-select form-select-sm" data-event-codes multiple size="8">
                                <option value="__all__">Все события</option>
                                <option value="__overall__" ${eventFilterState.includeOverall ? "selected" : ""}>Общая статистика</option>
                                ${eventOptionsHtml}
                            </select>`}
                        </div>
                    </div>
            ` : "";
        const bucketOptionsHtml = Array.from(refs.bucket?.options || [])
            .map((option) => `<option value="${escapeHtml(option.value || "")}" ${(option.value || "") === bucketValue ? "selected" : ""}>${escapeHtml(option.textContent || "")}</option>`)
            .join("");
        const selectedEventCount = Array.isArray(eventFilterState.codes) ? eventFilterState.codes.length : 0;
        const showLatencyMetricSelect = supportsLatencyMetricSelect && selectedEventCount > 0;
        const showStageLatencyEventMetricSelect = supportsStageLatencyEventMetricSelect && selectedEventCount > 1;
        const compareModeResolved = resolveExpandedCompareMode(canvasId);
        const isCompareEnabled = compareModeResolved !== "off";
        const defaults = resolveExpandedRangesForMode(canvasId, isCompareEnabled);
        const quickPresetValue = inferQuickRangeCodeFromValues(defaults.afterFrom, defaults.afterTo);
        controls.innerHTML = `
            <div class="analytics-expanded-graph-row analytics-expanded-graph-row-top">
                <div class="analytics-expanded-toolbar-left" data-expanded-toolbar-left>
                    ${eventFilterHtml}
                    <div class="analytics-expanded-range-group" data-expanded-quick-group>
                        <span class="analytics-expanded-range-label">Пресет</span>
                        <select class="form-select form-select-sm analytics-expanded-quick-preset" data-expanded-preset aria-label="Быстрый период графика">
                            ${buildQuickRangeOptionsHtml(quickPresetValue)}
                        </select>
                    </div>
                    <div class="analytics-expanded-range-group ${isCompareEnabled ? "d-none" : ""}" data-after-group>
                        <span class="analytics-expanded-range-label">Период</span>
                        <input type="datetime-local" class="form-control form-control-sm" data-range="after-from" value="${escapeHtml(defaults.afterFrom)}">
                        <input type="datetime-local" class="form-control form-control-sm" data-range="after-to" value="${escapeHtml(defaults.afterTo)}">
                    </div>
                </div>
                <div class="analytics-expanded-actions" data-expanded-actions>
                    ${supportsLatencyMetricSelect ? `
                    <select class="form-select form-select-sm analytics-expanded-latency-metric ${showLatencyMetricSelect ? "" : "d-none"}" data-expanded-latency-metric ${selectedEventCount <= 1 ? "disabled" : ""}>
                        <option value="avg" ${selectedLatencyMetric === "avg" ? "selected" : ""}>AVG</option>
                        <option value="p95" ${selectedLatencyMetric === "p95" ? "selected" : ""}>P95</option>
                        <option value="p99" ${selectedLatencyMetric === "p99" ? "selected" : ""}>P99</option>
                    </select>
                    ` : ""}
                    ${supportsStageLatencyEventMetricSelect ? `
                    <select class="form-select form-select-sm analytics-expanded-stage-latency-event-metric ${showStageLatencyEventMetricSelect ? "" : "d-none"}" data-expanded-stage-latency-event-metric>
                        <option value="avg" ${selectedStageLatencyEventMetric === "avg" ? "selected" : ""}>AVG (события)</option>
                        <option value="p95" ${selectedStageLatencyEventMetric === "p95" ? "selected" : ""}>P95 (события)</option>
                    </select>
                    ` : ""}
                    <select class="form-select form-select-sm analytics-inline-bucket" data-expanded-bucket>
                        ${bucketOptionsHtml}
                    </select>
                    <select class="form-select form-select-sm analytics-inline-compare-mode" data-expanded-compare-mode>
                        ${INLINE_COMPARE_MODE_OPTIONS.map((item) => `<option value="${item.value}" ${item.value === compareModeResolved ? "selected" : ""}>${item.value === compareModeResolved ? "вњ“ " : ""}${item.label}</option>`).join("")}
                    </select>
                    <button type="button" class="btn btn-outline-dark analytics-chart-icon-btn d-none" data-expanded-reset title="Сбросить к верхнему фильтру" aria-label="Сбросить к верхнему фильтру">
                        <i class="bi bi-arrow-counterclockwise"></i>
                    </button>
                </div>
            </div>
            <div class="analytics-expanded-graph-row analytics-expanded-graph-row-ranges ${isCompareEnabled ? "" : "d-none"}" data-expanded-ranges-row>
                <div class="small text-muted" data-expanded-before-summary></div>
                <div class="analytics-expanded-range-group" data-after-compare-group>
                    <span class="analytics-expanded-range-label">После</span>
                    <input type="datetime-local" class="form-control form-control-sm" data-range="after-from-compare" value="${escapeHtml(defaults.afterFrom)}">
                    <input type="datetime-local" class="form-control form-control-sm" data-range="after-to-compare" value="${escapeHtml(defaults.afterTo)}">
                </div>
            </div>
        `;
        container.insertBefore(controls, container.firstChild);
        ensureParameterHelpButtons(controls);

        const presetEl = controls.querySelector("[data-expanded-preset]");
        const bucketEl = controls.querySelector("[data-expanded-bucket]");
        const resetEl = controls.querySelector("[data-expanded-reset]");
        const compareModeEl = controls.querySelector("[data-expanded-compare-mode]");
        const actionsEl = controls.querySelector(".analytics-expanded-actions");
        ensureChartScenarioPicker(actionsEl, canvasId);
        const toolbarLeft = controls.querySelector("[data-expanded-toolbar-left]");
        const rangesRow = controls.querySelector("[data-expanded-ranges-row]");
        const beforeSummaryEl = controls.querySelector("[data-expanded-before-summary]");
        const afterSingleGroup = controls.querySelector("[data-after-group]");
        const popupToggleEl = controls.querySelector("[data-event-popup-toggle]");
        const popupEl = controls.querySelector("[data-event-popup]");
        const beforeFromEl = controls.querySelector("[data-range='before-from']");
        const beforeToEl = controls.querySelector("[data-range='before-to']");
        const afterFromEl = controls.querySelector("[data-range='after-from']");
        const afterToEl = controls.querySelector("[data-range='after-to']");
        const beforeFromCompareEl = controls.querySelector("[data-range='before-from-compare']");
        const beforeToCompareEl = controls.querySelector("[data-range='before-to-compare']");
        const afterFromCompareEl = controls.querySelector("[data-range='after-from-compare']");
        const afterToCompareEl = controls.querySelector("[data-range='after-to-compare']");
        const eventCodesEl = controls.querySelector("[data-event-codes]");
        const eventOverallToggleEl = controls.querySelector("[data-event-overall-toggle]");
        const latencyMetricEl = controls.querySelector("[data-expanded-latency-metric]");
        const stageLatencyEventMetricEl = controls.querySelector("[data-expanded-stage-latency-event-metric]");
        let applyTimerId = null;
        const isCompareMode = () => resolveExpandedCompareMode(canvasId) !== "off";
        const readRangesFromUi = () => {
            if (isCompareMode()) {
                const afterFrom = afterFromCompareEl?.value || afterFromEl?.value || "";
                const afterTo = afterToCompareEl?.value || afterToEl?.value || "";
                return normalizeCompareRangesByAfter(afterFrom, afterTo, "", "");
            }
            const afterFrom = afterFromEl?.value || "";
            const afterTo = afterToEl?.value || "";
            return normalizeCompareRangesByAfter(afterFrom, afterTo, "", "");
        };
        const updateExpandedBeforeSummary = (ranges = readRangesFromUi()) => {
            const mode = resolveExpandedCompareMode(canvasId);
            syncComparePeriodSummary(
                beforeSummaryEl,
                mode,
                ranges,
                mode === "overlay"
                    ? {overlayText: "Наложено с периодом «До»"}
                    : {prefix: "До", includeAfter: true}
            );
        };
        const writeRangesToUi = (ranges) => {
            const normalized = normalizeCompareRangesByAfter(ranges?.afterFrom, ranges?.afterTo, "", "");
            if (beforeFromEl) beforeFromEl.value = normalized.beforeFrom || "";
            if (beforeToEl) beforeToEl.value = normalized.beforeTo || "";
            if (afterFromEl) afterFromEl.value = normalized.afterFrom || "";
            if (afterToEl) afterToEl.value = normalized.afterTo || "";
            if (beforeFromCompareEl) beforeFromCompareEl.value = normalized.beforeFrom || "";
            if (beforeToCompareEl) beforeToCompareEl.value = normalized.beforeTo || "";
            if (afterFromCompareEl) afterFromCompareEl.value = normalized.afterFrom || "";
            if (afterToCompareEl) afterToCompareEl.value = normalized.afterTo || "";
            syncQuickRangeSelectFromRange(presetEl, normalized.afterFrom, normalized.afterTo);
            updateExpandedBeforeSummary(normalized);
        };
        const refreshExpandedEventOptions = async () => {
            if (!supportsExpandedEventFilter || !eventCodesEl) {
                return;
            }
            try {
                const ranges = readRangesFromUi();
                const bucketOverride = resolveExpandedBucket(canvasId);
                const options = await fetchAvailableEventTypesForRanges(
                    ranges,
                    bucketOverride,
                    isCompareMode()
                );
                state.expandedEventOptionsBySource[canvasId] = options;
                const optionsHtml = options
                    .map((item) => `<option value="${escapeHtml(item.code)}">${escapeHtml(item.name || item.code)}</option>`)
                    .join("");
                eventFilterState.codes = eventFilterState.codes.filter((code) => options.some((item) => item.code === code));
                const selectedAvailableCount = eventFilterState.codes.length;
                const allSelected = options.length > 0 && selectedAvailableCount === options.length;
                if (singleEventSelectMode) {
                    eventCodesEl.innerHTML = optionsHtml;
                    if (eventOverallToggleEl) {
                        eventOverallToggleEl.checked = !!eventFilterState.includeOverall;
                    }
                    const selectedValue = eventFilterState.codes[0] || "";
                    if (selectedValue) {
                        eventCodesEl.value = selectedValue;
                    } else if (eventCodesEl.options.length > 0) {
                        eventCodesEl.selectedIndex = -1;
                    }
                } else {
                    eventCodesEl.innerHTML = `<option value="__all__"${allSelected ? " selected" : ""}>Все события</option><option value="__overall__"${eventFilterState.includeOverall ? " selected" : ""}>Общая статистика</option>${optionsHtml}`;
                    const selectedNormalized = [
                        ...(allSelected ? ["__all__"] : []),
                        ...(eventFilterState.includeOverall ? ["__overall__"] : []),
                        ...eventFilterState.codes
                    ];
                    syncMultiSelectValues(eventCodesEl, selectedNormalized);
                }
            } catch (error) {
                console.error("Expanded events options load failed", error);
            }
        };
        const rerenderExpandedEventFilterIfNeeded = async () => {
            if (!supportsExpandedEventFilter || eventFilterState.includeOverall || eventFilterState.codes.length === 0) {
                return;
            }
            const ranges = readRangesFromUi();
            if (!isValidExpandedRanges(ranges)) {
                return;
            }
            await renderExpandedChartByRanges(canvasId, ranges, {
                includeOverall: false,
                eventCodes: eventFilterState.codes,
                latencyMetric: getExpandedLatencyMetricMode(canvasId),
                stageLatencyEventMetric: getExpandedStageLatencyEventMetricMode(canvasId)
            });
            state.expandedChart.customRangeActive = true;
        };

        const syncResetVisibility = () => {
            const top = resolveTopInlineComparePresetOrDefault();
            const current = (presetEl?.value || "").trim();
            const presetChanged = presetEl ? current !== top : false;
            const topBucket = (refs.bucket?.value || "").trim();
            const currentBucket = (bucketEl?.value || "").trim();
            const bucketChanged = currentBucket !== topBucket;
            const defaults = expandedRangesFromTopFilter(canvasId);
            const currentRanges = readRangesFromUi();
            const dateChanged = (currentRanges.beforeFrom || "") !== (defaults.beforeFrom || "")
                || (currentRanges.beforeTo || "") !== (defaults.beforeTo || "")
                || (currentRanges.afterFrom || "") !== (defaults.afterFrom || "")
                || (currentRanges.afterTo || "") !== (defaults.afterTo || "");
            const eventChanged = supportsExpandedEventFilter
                && (!eventFilterState.includeOverall || eventFilterState.codes.length > 0);
            const hasOverride = hasChartLocalOverride(canvasId) || presetChanged || dateChanged || bucketChanged || eventChanged;
            resetEl?.classList.toggle("d-none", !hasOverride);
            refs.analyticsPage?.querySelectorAll(`[data-inline-compare-reset='${canvasId}']`)
                ?.forEach((button) => {
                    button.classList.toggle("d-none", !hasChartLocalOverride(canvasId));
                });
        };
        const syncLatencyMetricVisibility = () => {
            if (!latencyMetricEl) {
                return;
            }
            const count = Array.isArray(eventFilterState.codes) ? eventFilterState.codes.length : 0;
            latencyMetricEl.classList.toggle("d-none", count <= 0);
            latencyMetricEl.disabled = count <= 1;
        };
        const syncStageLatencyMetricVisibility = () => {
            if (!stageLatencyEventMetricEl) {
                return;
            }
            const count = Array.isArray(eventFilterState.codes) ? eventFilterState.codes.length : 0;
            stageLatencyEventMetricEl.classList.toggle("d-none", count <= 1);
        };
        const syncDateMode = () => {
            const enabled = resolveExpandedCompareMode(canvasId) !== "off";
            controls.classList.toggle("is-compare", enabled);
            rangesRow?.classList.toggle("d-none", !enabled);
            afterSingleGroup?.classList.toggle("d-none", enabled);
            updateExpandedBeforeSummary();
        };
        syncResetVisibility();
        syncLatencyMetricVisibility();
        syncStageLatencyMetricVisibility();
        syncDateMode();
        syncInlineCompareModeSelectValues(canvasId);
        syncInlineCompareModeResetVisibility(canvasId);

        const closeBtn = container.querySelector(".analytics-expanded-close-btn");
        if (closeBtn && actionsEl && !actionsEl.contains(closeBtn)) {
            closeBtn.classList.remove("btn-dark");
            closeBtn.classList.add("btn-outline-dark");
            actionsEl.appendChild(closeBtn);
        }
        if (supportsExpandedEventFilter) {
            const applyExpandedEventFilter = async () => {
                if (singleEventSelectMode) {
                    const selectedValue = String(eventCodesEl?.value || "").trim();
                    eventFilterState.includeOverall = !!eventOverallToggleEl?.checked;
                    eventFilterState.codes = selectedValue ? [selectedValue] : [];
                } else {
                    const rawSelected = readMultiSelectValues(eventCodesEl);
                    const hasAll = rawSelected.includes("__all__");
                    const hasOverall = rawSelected.includes("__overall__");
                    const allCodes = getExpandedEventOptions(canvasId).map((item) => item.code);
                    eventFilterState.includeOverall = hasOverall;
                    eventFilterState.codes = hasAll
                        ? allCodes
                        : rawSelected.filter((code) => code !== "__overall__" && code !== "__all__");
                    if (eventCodesEl) {
                        const selectedNormalized = [
                            ...(hasAll ? ["__all__"] : []),
                            ...(eventFilterState.includeOverall ? ["__overall__"] : []),
                            ...eventFilterState.codes
                        ];
                        syncMultiSelectValues(eventCodesEl, selectedNormalized);
                    }
                }
                syncLatencyMetricVisibility();
                syncStageLatencyMetricVisibility();
                const ranges = readRangesFromUi();
                if (!isValidExpandedRanges(ranges)) {
                    return;
                }
                syncResetVisibility();
                setChartActionLoading(canvasId, true);
                try {
                    await renderExpandedChartByRanges(canvasId, ranges, {
                        includeOverall: eventFilterState.includeOverall,
                        eventCodes: eventFilterState.codes,
                        latencyMetric: getExpandedLatencyMetricMode(canvasId),
                        stageLatencyEventMetric: getExpandedStageLatencyEventMetricMode(canvasId)
                    });
                    state.expandedChart.customRangeActive = true;
                } finally {
                    setChartActionLoading(canvasId, false);
                }
            };
            eventCodesEl?.addEventListener("change", () => {
                void applyExpandedEventFilter();
            });
            eventOverallToggleEl?.addEventListener("change", () => {
                void applyExpandedEventFilter();
            });
            popupToggleEl?.addEventListener("click", (event) => {
                event.preventDefault();
                popupEl?.classList.toggle("d-none");
            });
            container.addEventListener("click", (event) => {
                if (!popupEl || popupEl.classList.contains("d-none")) {
                    return;
                }
                const insidePopup = event.target.closest("[data-event-popup]");
                const insideToggle = event.target.closest("[data-event-popup-toggle]");
                if (!insidePopup && !insideToggle) {
                    popupEl.classList.add("d-none");
                }
            });
            void refreshExpandedEventOptions();
        }

        latencyMetricEl?.addEventListener("change", async () => {
            state.expandedLatencyMetricBySource[canvasId] = (latencyMetricEl.value || "p95").trim().toLowerCase();
            const ranges = readRangesFromUi();
            if (!isValidExpandedRanges(ranges)) {
                return;
            }
            setChartActionLoading(canvasId, true);
            try {
                await renderExpandedChartByRanges(canvasId, ranges, {
                    includeOverall: eventFilterState.includeOverall,
                    eventCodes: eventFilterState.codes,
                    latencyMetric: getExpandedLatencyMetricMode(canvasId),
                    stageLatencyEventMetric: getExpandedStageLatencyEventMetricMode(canvasId)
                });
                if (canvasId !== "chart-event-kpi") {
                    await applyStoredExpandedRangesToCharts(canvasId);
                }
                state.expandedChart.customRangeActive = true;
            } finally {
                setChartActionLoading(canvasId, false);
            }
        });

        stageLatencyEventMetricEl?.addEventListener("change", async () => {
            state.expandedStageLatencyEventMetricBySource[canvasId] = (stageLatencyEventMetricEl.value || "p95").trim().toLowerCase();
            const ranges = readRangesFromUi();
            if (!isValidExpandedRanges(ranges)) {
                return;
            }
            setChartActionLoading(canvasId, true);
            try {
                await renderExpandedChartByRanges(canvasId, ranges, {
                    includeOverall: eventFilterState.includeOverall,
                    eventCodes: eventFilterState.codes,
                    stageLatencyEventMetric: getExpandedStageLatencyEventMetricMode(canvasId)
                });
                if (canvasId !== "chart-event-kpi") {
                    await applyStoredExpandedRangesToCharts(canvasId);
                }
                state.expandedChart.customRangeActive = true;
            } finally {
                setChartActionLoading(canvasId, false);
            }
        });

        presetEl?.addEventListener("change", async () => {
            setChartActionLoading(canvasId, true);
            const next = (presetEl.value || "").trim();
            if (!next) {
                syncQuickRangeSelectFromRange(presetEl, readRangesFromUi().afterFrom, readRangesFromUi().afterTo);
                setChartActionLoading(canvasId, false);
                return;
            }
            if (next) {
                state.inlineComparePresetBySource[canvasId] = next;
                state.inlineComparePresetOverriddenBySource[canvasId] = true;
            }
            try {
                if (next === "all") {
                    await ensureAllTimeRangeLoaded();
                }
                state.expandedChart.customRangeActive = false;
                const fresh = expandedRangesFromPresetNow(next);
                writeRangesToUi(fresh);
                state.expandedRangesBySource[canvasId] = {...fresh};
                syncPresetSelectValues(canvasId);
                syncResetVisibility();
                if (canvasId !== "chart-event-kpi") {
                    await applyInlineComparePresetToChart(canvasId);
                    await applyStoredExpandedRangesToCharts(canvasId);
                }
                await refreshExpandedEventOptions();
                renderExpandedChartClone(canvasId);
                await rerenderExpandedEventFilterIfNeeded();
            } finally {
                setChartActionLoading(canvasId, false);
            }
        });

        resetEl?.addEventListener("click", async () => {
            setChartActionLoading(canvasId, true);
            try {
                if (supportsExpandedEventFilter) {
                    eventFilterState.includeOverall = true;
                    eventFilterState.codes = [];
                }
                if (latencyMetricEl) {
                    state.expandedLatencyMetricBySource[canvasId] = "p95";
                }
                if (stageLatencyEventMetricEl) {
                    state.expandedStageLatencyEventMetricBySource[canvasId] = "p95";
                }
                await resetChartLocalOverride(canvasId);
                updateCompareButtonsState();
            } finally {
                setChartActionLoading(canvasId, false);
            }
        });

        compareModeEl?.addEventListener("change", async () => {
            await applyInlineCompareMode(canvasId, compareModeEl.value, {override: true});
        });

        bucketEl?.addEventListener("change", async () => {
            setChartActionLoading(canvasId, true);
            try {
                state.expandedBucketBySource[canvasId] = (bucketEl.value || "").trim();
                state.expandedChart.customRangeActive = false;
                syncResetVisibility();
                if (canvasId !== "chart-event-kpi") {
                    await applyInlineComparePresetToChart(canvasId);
                    await applyStoredExpandedRangesToCharts(canvasId);
                }
                await refreshExpandedEventOptions();
                renderExpandedChartClone(canvasId);
                await rerenderExpandedEventFilterIfNeeded();
            } finally {
                setChartActionLoading(canvasId, false);
            }
        });

        const applyRangesFromInputs = async () => {
            setChartActionLoading(canvasId, true);
            const chartOptions = supportsExpandedEventFilter
                ? {
                    includeOverall: eventFilterState.includeOverall,
                    eventCodes: eventFilterState.codes,
                    latencyMetric: getExpandedLatencyMetricMode(canvasId),
                    stageLatencyEventMetric: getExpandedStageLatencyEventMetricMode(canvasId)
                }
                : {};
            const ranges = readRangesFromUi();
            try {
                if (resolveExpandedCompareMode(canvasId) === "split") {
                    if (!isValidExpandedRanges(ranges)) {
                        return;
                    }
                    const normalizedRanges = normalizeCompareRangesByAfter(
                        ranges.afterFrom,
                        ranges.afterTo,
                        ranges.beforeFrom,
                        ranges.beforeTo
                    );
                    writeRangesToUi(normalizedRanges);
                    const bucketOverride = resolveExpandedBucket(canvasId);
                    const beforeConfig = await buildChartConfigByRange(canvasId, normalizedRanges.beforeFrom, normalizedRanges.beforeTo, "До", bucketOverride, chartOptions);
                    const afterConfig = await buildChartConfigByRange(canvasId, normalizedRanges.afterFrom, normalizedRanges.afterTo, "После", bucketOverride, chartOptions);
                    const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
                    if (canvasId !== "chart-event-kpi") {
                        upsertChart(compareCanvasId, beforeConfig);
                        upsertChart(canvasId, afterConfig);
                    }
                    state.expandedRangesBySource[canvasId] = {...normalizedRanges};
                    await refreshExpandedEventOptions();
                    await renderExpandedChartByRanges(canvasId, normalizedRanges, chartOptions);
                    state.expandedChart.customRangeActive = true;
                    syncResetVisibility();
                    return;
                }
                const from = ranges.afterFrom;
                const to = ranges.afterTo;
                const fromDate = new Date(from || "");
                const toDate = new Date(to || "");
                if (Number.isNaN(fromDate.getTime()) || Number.isNaN(toDate.getTime()) || fromDate >= toDate) {
                    return;
                }
                const compareMode = resolveExpandedCompareMode(canvasId);
                if (compareMode === "overlay") {
                    const normalizedRanges = normalizeCompareRangesByAfter(from, to, "", "");
                    writeRangesToUi(normalizedRanges);
                    state.expandedRangesBySource[canvasId] = {...normalizedRanges};
                    await refreshExpandedEventOptions();
                    await renderExpandedChartByRanges(canvasId, normalizedRanges, chartOptions);
                    state.expandedChart.customRangeActive = true;
                    syncResetVisibility();
                    return;
                }
                const config = await buildChartConfigByRange(canvasId, from, to, "Период", resolveExpandedBucket(canvasId), chartOptions);
                const expandedCanvas = container.querySelector(`#chart-expanded-${canvasId}`);
                if (!expandedCanvas) {
                    return;
                }
                if (state.expandedChart.instance) {
                    state.expandedChart.instance.destroy();
                }
                state.expandedChart.instance = new Chart(expandedCanvas.getContext("2d"), config);
                if (canvasId !== "chart-event-kpi") {
                    upsertChart(canvasId, config);
                }
                state.expandedRangesBySource[canvasId] = {...ranges};
                await refreshExpandedEventOptions();
                state.expandedChart.customRangeActive = true;
                syncResetVisibility();
            } finally {
                setChartActionLoading(canvasId, false);
            }
        };
        const scheduleApplyRanges = () => {
            syncQuickRangeSelectFromRange(presetEl, readRangesFromUi().afterFrom, readRangesFromUi().afterTo);
            syncResetVisibility();
            if (applyTimerId != null) {
                clearTimeout(applyTimerId);
            }
            applyTimerId = setTimeout(() => {
                applyTimerId = null;
                void applyRangesFromInputs();
            }, 180);
        };
        [beforeFromEl, beforeToEl, afterFromEl, afterToEl, beforeFromCompareEl, beforeToCompareEl, afterFromCompareEl, afterToCompareEl].forEach((input) => {
            input?.addEventListener("change", scheduleApplyRanges);
        });
        beforeToCompareEl?.addEventListener("change", () => {
            if (afterFromCompareEl) {
                afterFromCompareEl.value = beforeToCompareEl.value || "";
            }
            scheduleApplyRanges();
        });
        afterFromCompareEl?.addEventListener("change", () => {
            if (beforeToCompareEl) {
                beforeToCompareEl.value = afterFromCompareEl.value || "";
            }
            scheduleApplyRanges();
        });
    }

    function setupExpandedCompareRangeControls(container, canvasId) {
        const controls = document.createElement("div");
        controls.className = "analytics-expanded-range-controls";
        const defaults = defaultExpandedCompareRanges(canvasId);
        controls.innerHTML = `
            <div class="analytics-expanded-range-group">
                <span class="analytics-expanded-range-label">До</span>
                <input type="datetime-local" class="form-control form-control-sm" data-range="before-from" value="${escapeHtml(defaults.beforeFrom)}">
                <input type="datetime-local" class="form-control form-control-sm" data-range="before-to" value="${escapeHtml(defaults.beforeTo)}">
            </div>
            <div class="analytics-expanded-range-group">
                <span class="analytics-expanded-range-label">После</span>
                <input type="datetime-local" class="form-control form-control-sm" data-range="after-from" value="${escapeHtml(defaults.afterFrom)}">
                <input type="datetime-local" class="form-control form-control-sm" data-range="after-to" value="${escapeHtml(defaults.afterTo)}">
            </div>
            <button type="button" class="btn btn-sm btn-dark" data-action="apply">Применить</button>
            <button type="button" class="btn btn-sm btn-outline-secondary" data-action="reset">Сбросить</button>
        `;
        container.insertAdjacentElement("afterbegin", controls);

        const readRanges = () => ({
            beforeFrom: controls.querySelector("[data-range='before-from']")?.value || "",
            beforeTo: controls.querySelector("[data-range='before-to']")?.value || "",
            afterFrom: controls.querySelector("[data-range='after-from']")?.value || "",
            afterTo: controls.querySelector("[data-range='after-to']")?.value || ""
        });
        const applyDefaults = () => {
            const fresh = defaultExpandedCompareRanges(canvasId);
            const beforeFrom = controls.querySelector("[data-range='before-from']");
            const beforeTo = controls.querySelector("[data-range='before-to']");
            const afterFrom = controls.querySelector("[data-range='after-from']");
            const afterTo = controls.querySelector("[data-range='after-to']");
            if (beforeFrom) beforeFrom.value = fresh.beforeFrom;
            if (beforeTo) beforeTo.value = fresh.beforeTo;
            if (afterFrom) afterFrom.value = fresh.afterFrom;
            if (afterTo) afterTo.value = fresh.afterTo;
            return fresh;
        };

        controls.querySelector("[data-action='apply']")?.addEventListener("click", async () => {
            const ranges = readRanges();
            if (!isValidExpandedRanges(ranges)) {
                return;
            }
            try {
                await renderExpandedChartByRanges(canvasId, ranges);
                state.expandedChart.customRangeActive = true;
            } catch (error) {
                console.error("Expanded compare apply failed", error);
            }
        });
        controls.querySelector("[data-action='reset']")?.addEventListener("click", () => {
            applyDefaults();
            state.expandedChart.customRangeActive = false;
            renderExpandedChartClone(canvasId);
        });
    }

    function isValidExpandedRanges(ranges) {
        const beforeFrom = new Date(ranges.beforeFrom || "");
        const beforeTo = new Date(ranges.beforeTo || "");
        const afterFrom = new Date(ranges.afterFrom || "");
        const afterTo = new Date(ranges.afterTo || "");
        if ([beforeFrom, beforeTo, afterFrom, afterTo].some((date) => Number.isNaN(date.getTime()))) {
            return false;
        }
        return beforeFrom.getTime() < beforeTo.getTime() && afterFrom.getTime() < afterTo.getTime();
    }

    function defaultExpandedCompareRanges(canvasId) {
        const preset = resolveInlineComparePreset(canvasId);
        const topFrom = refs.from?.value ? new Date(refs.from.value) : null;
        const topTo = refs.to?.value ? new Date(refs.to.value) : null;
        const hasTopRange = topFrom && topTo && !Number.isNaN(topFrom.getTime()) && !Number.isNaN(topTo.getTime());
        if (hasTopRange) {
            const topPreset = inferQuickRangeCodeFromValues(refs.from?.value || "", refs.to?.value || "");
            if (topPreset && topPreset === String(preset || "").trim().toLowerCase()) {
                const durationMs = Math.max(60_000, topTo.getTime() - topFrom.getTime());
                const beforeFromMs = topFrom.getTime() - durationMs;
                const beforeToMs = topFrom.getTime();
                return {
                    beforeFrom: toDateTimeLocalString(new Date(beforeFromMs)),
                    beforeTo: toDateTimeLocalString(new Date(beforeToMs)),
                    afterFrom: toDateTimeLocalString(new Date(topFrom.getTime())),
                    afterTo: toDateTimeLocalString(new Date(topTo.getTime()))
                };
            }
        }
        const afterRange = buildQuickRangeFromDate(hasTopRange ? topTo : new Date(), preset)
            || buildQuickRangeFromDate(hasTopRange ? topTo : new Date(), "1h");
        const afterFromMs = afterRange.fromDate.getTime();
        const afterToMs = afterRange.toDate.getTime();
        const durationMs = Math.max(60_000, afterToMs - afterFromMs);
        const beforeToMs = afterFromMs;
        const beforeFromMs = beforeToMs - durationMs;
        return {
            beforeFrom: toDateTimeLocalString(new Date(beforeFromMs)),
            beforeTo: toDateTimeLocalString(new Date(beforeToMs)),
            afterFrom: toDateTimeLocalString(afterRange.fromDate),
            afterTo: toDateTimeLocalString(afterRange.toDate)
        };
    }

    function rangesHaveSameAfter(left, right) {
        return String(left?.afterFrom || "") === String(right?.afterFrom || "")
            && String(left?.afterTo || "") === String(right?.afterTo || "");
    }

    function rangesHaveSameBefore(left, right) {
        return String(left?.beforeFrom || "") === String(right?.beforeFrom || "")
            && String(left?.beforeTo || "") === String(right?.beforeTo || "");
    }

    function getEventKpiMiniRowsSnapshotForRanges(ranges, compareMode, options = {}) {
        if (Array.isArray(options.eventCodes) && options.eventCodes.length > 0) {
            return null;
        }
        if (options.includeOverall === false) {
            return null;
        }
        const snapshot = state.eventKpiMiniRowsSnapshot;
        if (!snapshot?.ranges || !Array.isArray(snapshot.currentRows)) {
            return null;
        }
        const normalizedRequested = normalizeCompareRangesByAfter(
            ranges?.afterFrom,
            ranges?.afterTo,
            ranges?.beforeFrom,
            ranges?.beforeTo
        );
        const normalizedSnapshot = normalizeCompareRangesByAfter(
            snapshot.ranges.afterFrom,
            snapshot.ranges.afterTo,
            snapshot.ranges.beforeFrom,
            snapshot.ranges.beforeTo
        );
        if (!rangesHaveSameAfter(normalizedRequested, normalizedSnapshot)) {
            return null;
        }
        if (compareMode !== "off" && !rangesHaveSameBefore(normalizedRequested, normalizedSnapshot)) {
            return null;
        }
        if (compareMode !== "off" && String(snapshot.rawMode || "off") === "off") {
            return null;
        }
        return snapshot;
    }

    async function renderExpandedChartByRanges(canvasId, ranges, options = {}) {
        const container = state.expandedChart.containerEl;
        if (!container) {
            return;
        }
        const compareMode = resolveExpandedCompareMode(canvasId);
        const isOverlayMode = compareMode === "overlay";
        const primaryCanvas = container.querySelector(`#chart-expanded-${canvasId}`);
        const compareCanvas = container.querySelector(`[data-expanded-compare-for='${canvasId}']`);
        if (!primaryCanvas) {
            return;
        }
        if (canvasId === "chart-event-kpi") {
            if (state.expandedChart.compareInstance) {
                state.expandedChart.compareInstance.destroy();
                state.expandedChart.compareInstance = null;
            }
            if (state.expandedChart.instance) {
                state.expandedChart.instance.destroy();
            }
            const snapshot = getEventKpiMiniRowsSnapshotForRanges(ranges, compareMode, options);
            if (snapshot) {
                const afterRows = Array.isArray(snapshot.currentRows) ? snapshot.currentRows : [];
                const beforeRows = Array.isArray(snapshot.beforeRows) ? snapshot.beforeRows : [];
                if (compareMode === "split" && compareCanvas) {
                    const beforeConfig = buildEventKpiSingleChartConfig(beforeRows, options);
                    const afterConfig = buildEventKpiSingleChartConfig(afterRows, options);
                    state.expandedChart.compareInstance = new Chart(compareCanvas.getContext("2d"), beforeConfig);
                    state.expandedChart.instance = new Chart(primaryCanvas.getContext("2d"), afterConfig);
                    return;
                }
                if (isOverlayMode) {
                    const overlayConfig = buildEventKpiOverlayChartConfig(afterRows, beforeRows, {
                        ...options,
                        preserveCurrentOrder: true
                    });
                    state.expandedChart.instance = new Chart(primaryCanvas.getContext("2d"), overlayConfig);
                    return;
                }
                const afterConfig = buildEventKpiSingleChartConfig(afterRows, options);
                state.expandedChart.instance = new Chart(primaryCanvas.getContext("2d"), afterConfig);
                return;
            }
            if (compareMode === "split" && compareCanvas) {
                const [beforeRows, afterRows] = await Promise.all([
                    loadExpandedEventKpiRowsForRange(ranges.beforeFrom, ranges.beforeTo, options),
                    loadExpandedEventKpiRowsForRange(ranges.afterFrom, ranges.afterTo, options)
                ]);
                const beforeConfig = buildEventKpiSingleChartConfig(beforeRows, options);
                const afterConfig = buildEventKpiSingleChartConfig(afterRows, options);
                state.expandedChart.compareInstance = new Chart(compareCanvas.getContext("2d"), beforeConfig);
                state.expandedChart.instance = new Chart(primaryCanvas.getContext("2d"), afterConfig);
                return;
            }
            if (isOverlayMode) {
                const [beforeRows, afterRows] = await Promise.all([
                    loadExpandedEventKpiRowsForRange(ranges.beforeFrom, ranges.beforeTo, options),
                    loadExpandedEventKpiRowsForRange(ranges.afterFrom, ranges.afterTo, options)
                ]);
                const overlayConfig = buildEventKpiOverlayChartConfig(afterRows, beforeRows, {
                    ...options,
                    preserveCurrentOrder: true
                });
                state.expandedChart.instance = new Chart(primaryCanvas.getContext("2d"), overlayConfig);
                return;
            }
            const afterRows = await loadExpandedEventKpiRowsForRange(ranges.afterFrom, ranges.afterTo, options);
            const afterConfig = buildEventKpiSingleChartConfig(afterRows, options);
            state.expandedChart.instance = new Chart(primaryCanvas.getContext("2d"), afterConfig);
            return;
        }
        const afterConfig = await buildChartConfigByRange(canvasId, ranges.afterFrom, ranges.afterTo, compareCanvas ? "После" : "Период", undefined, options);
        if (!compareCanvas) {
            if (state.expandedChart.compareInstance) {
                state.expandedChart.compareInstance.destroy();
                state.expandedChart.compareInstance = null;
            }
            if (isOverlayMode) {
                const normalizedRanges = normalizeCompareRangesByAfter(
                    ranges.afterFrom,
                    ranges.afterTo,
                    ranges.beforeFrom,
                    ranges.beforeTo
                );
                const beforeConfig = await buildChartConfigByRange(canvasId, normalizedRanges.beforeFrom, normalizedRanges.beforeTo, "До", undefined, options);
                if (isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
                    const ghostDatasets = beforeConfig.data.datasets.map((item) => buildGhostDataset(item, canvasId));
                    afterConfig.data = afterConfig.data || {};
                    afterConfig.data.datasets = [...(afterConfig.data.datasets || []), ...ghostDatasets];
                }
            }
            if (state.expandedChart.instance) {
                state.expandedChart.instance.destroy();
            }
            state.expandedChart.instance = new Chart(primaryCanvas.getContext("2d"), afterConfig);
            if (canvasId !== "chart-event-kpi") {
                upsertChart(canvasId, afterConfig);
            }
            return;
        }
        const beforeConfig = await buildChartConfigByRange(canvasId, ranges.beforeFrom, ranges.beforeTo, "До", undefined, options);
        if (isOverlayMode && isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
            const ghostDatasets = beforeConfig.data.datasets.map((item) => buildGhostDataset(item, canvasId));
            afterConfig.data = afterConfig.data || {};
            afterConfig.data.datasets = [...(afterConfig.data.datasets || []), ...ghostDatasets];
        }
        if (state.expandedChart.instance) {
            state.expandedChart.instance.destroy();
        }
        if (state.expandedChart.compareInstance) {
            state.expandedChart.compareInstance.destroy();
        }
        state.expandedChart.compareInstance = new Chart(compareCanvas.getContext("2d"), beforeConfig);
        state.expandedChart.instance = new Chart(primaryCanvas.getContext("2d"), afterConfig);
    }

    async function loadExpandedEventKpiRowsForRange(fromLocal, toLocal, options = {}) {
        return await fetchEventKpiRowsByRange(fromLocal, toLocal, undefined, options);
    }

    async function fetchEventKpiRowsByRange(fromLocal, toLocal, bucketOverride, options = {}) {
        const params = mainParams();
        if (bucketOverride != null) {
            const normalizedBucket = String(bucketOverride || "").trim();
            if (normalizedBucket) {
                params.set("bucketMinutes", normalizedBucket);
            } else {
                params.delete("bucketMinutes");
            }
        }
        const fromIso = toIso(fromLocal);
        const toIsoValue = toIso(toLocal);
        if (fromIso) {
            params.set("from", fromIso);
        } else {
            params.delete("from");
        }
        if (toIsoValue) {
            params.set("to", toIsoValue);
        } else {
            params.delete("to");
        }
        const data = await fetchJson(`${api("/overview")}?${params.toString()}`);
        return buildEventKpiRows(data?.eventBreakdown || [], options.categories);
    }

    async function buildChartConfigByRange(canvasId, fromLocal, toLocal, sideLabel, bucketOverride, options = {}) {
        const lowerId = (canvasId || "").toLowerCase();
        const fromIso = toIso(fromLocal);
        const toIsoValue = toIso(toLocal);
        const params = mainParams();
        if (bucketOverride != null) {
            const normalizedBucket = String(bucketOverride || "").trim();
            if (normalizedBucket) {
                params.set("bucketMinutes", normalizedBucket);
            } else {
                params.delete("bucketMinutes");
            }
        }
        if (fromIso) {
            params.set("from", fromIso);
        } else {
            params.delete("from");
        }
        if (toIsoValue) {
            params.set("to", toIsoValue);
        } else {
            params.delete("to");
        }
        const labelSuffix = "";
        const withSuffix = (label) => label;
        if (lowerId.startsWith("chart-stage-")) {
            const stageEventCodes = Array.isArray(options.eventCodes)
                ? options.eventCodes.map((code) => String(code || "").trim()).filter((code) => code)
                : [];
            const stageLatencyMetric = String(options.stageLatencyEventMetric || "p95").toLowerCase() === "avg" ? "avg" : "p95";
            const overallParams = new URLSearchParams(params.toString());
            overallParams.delete("eventTypeCode");
            const needOverall = !!options.includeOverall || stageEventCodes.length === 0;
            const overallPromise = needOverall
                ? fetchJson(`${api("/stages")}?${overallParams.toString()}`)
                : Promise.resolve(null);
            const eventPromises = stageEventCodes.map(async (eventCode) => {
                const eventParams = new URLSearchParams(params.toString());
                eventParams.set("eventTypeCode", eventCode);
                const payload = await fetchJson(`${api("/stages")}?${eventParams.toString()}`);
                return {eventCode, payload};
            });
            const [overallData, ...eventPayloads] = await Promise.all([overallPromise, ...eventPromises]);
            const overallRows = overallData?.stages || [];
            const eventRowsByCode = new Map(eventPayloads.map((item) => [item.eventCode, item.payload?.stages || []]));
            const labelSet = new Set();
            overallRows.forEach((row) => labelSet.add(row.stageTypeName || row.stageTypeCode));
            eventRowsByCode.forEach((rows) => rows.forEach((row) => labelSet.add(row.stageTypeName || row.stageTypeCode)));
            const labels = Array.from(labelSet).sort((left, right) => {
                const rankDiff = stageLabelOrderRank(left) - stageLabelOrderRank(right);
                if (rankDiff !== 0) {
                    return rankDiff;
                }
                return String(left || "").localeCompare(String(right || ""), "ru", {sensitivity: "base"});
            });
            const byLabel = (sourceRows, valueKey) => {
                const map = new Map((sourceRows || []).map((row) => [row.stageTypeName || row.stageTypeCode, row]));
                return labels.map((label) => {
                    const row = map.get(label);
                    return row ? (Number(row[valueKey]) || 0) : 0;
                });
            };
            if (canvasId === "chart-stage-latency") {
                const datasets = [];
                const eventColorByCode = buildDistinctEventColors(stageEventCodes);
                if (stageEventCodes.length === 0) {
                    datasets.push(
                        {label: "AVG, ms", data: byLabel(overallRows, "avgMs"), backgroundColor: "rgba(15,118,110,0.72)", borderRadius: 8},
                        {label: "P95, ms", data: byLabel(overallRows, "p95Ms"), backgroundColor: "rgba(124,58,237,0.72)", borderRadius: 8}
                    );
                } else if (stageEventCodes.length === 1) {
                    const eventCode = stageEventCodes[0];
                    const eventRows = eventRowsByCode.get(eventCode) || [];
                    const eventName = (state.dictionaries?.eventTypes || []).find((item) => item.code === eventCode)?.name || eventCode;
                    if (options.includeOverall) {
                        datasets.push(
                            {label: "AVG (общая), ms", data: byLabel(overallRows, "avgMs"), backgroundColor: "rgba(15,118,110,0.38)", borderRadius: 8},
                            {label: "P95 (общая), ms", data: byLabel(overallRows, "p95Ms"), backgroundColor: "rgba(124,58,237,0.38)", borderRadius: 8}
                        );
                    }
                    datasets.push(
                        {label: `AVG (${eventName}), ms`, data: byLabel(eventRows, "avgMs"), backgroundColor: "rgba(15,118,110,0.75)", borderRadius: 8},
                        {label: `P95 (${eventName}), ms`, data: byLabel(eventRows, "p95Ms"), backgroundColor: "rgba(124,58,237,0.75)", borderRadius: 8}
                    );
                } else {
                    const valueKey = stageLatencyMetric === "avg"
                        ? "avgMs"
                        : (stageLatencyMetric === "p99" ? "p99Ms" : "p95Ms");
                    if (options.includeOverall) {
                        datasets.push({
                            label: `${stageLatencyMetric === "avg" ? "AVG" : (stageLatencyMetric === "p99" ? "P99" : "P95")} (общая), ms`,
                            data: byLabel(overallRows, valueKey),
                            backgroundColor: "rgba(51,65,85,0.42)",
                            borderRadius: 8
                        });
                    }
                    stageEventCodes.forEach((eventCode) => {
                        const eventRows = eventRowsByCode.get(eventCode) || [];
                        const eventName = (state.dictionaries?.eventTypes || []).find((item) => item.code === eventCode)?.name || eventCode;
                        const color = eventColorByCode.get(eventCode) || "#6d28d9";
                        datasets.push({
                            label: `${eventName}, ${stageLatencyMetric.toUpperCase()} ms`,
                            data: byLabel(eventRows, valueKey),
                            backgroundColor: withAlphaColor(color, 0.78),
                            borderRadius: 8
                        });
                    });
                }
                return {
                    type: "bar",
                    data: {
                        labels,
                        datasets
                    },
                    options: barChartOptions("ms")
                };
            }
            const errorDatasets = [];
            const eventColorByCode = buildDistinctEventColors(stageEventCodes);
            if (stageEventCodes.length === 0) {
                errorDatasets.push({
                    label: withSuffix("Error rate") + ", %",
                    data: labels.map((label) => {
                        const row = new Map(overallRows.map((item) => [item.stageTypeName || item.stageTypeCode, item])).get(label);
                        return toPercentNumber(row?.errorRate);
                    }),
                    backgroundColor: "rgba(185,28,28,0.62)",
                    borderRadius: 8
                });
            } else {
                if (options.includeOverall) {
                    errorDatasets.push({
                        label: "Error rate (общая), %",
                        data: labels.map((label) => {
                            const row = new Map(overallRows.map((item) => [item.stageTypeName || item.stageTypeCode, item])).get(label);
                            return toPercentNumber(row?.errorRate);
                        }),
                        backgroundColor: "rgba(51,65,85,0.42)",
                        borderRadius: 8
                    });
                }
                stageEventCodes.forEach((eventCode) => {
                    const eventRows = eventRowsByCode.get(eventCode) || [];
                    const eventName = (state.dictionaries?.eventTypes || []).find((item) => item.code === eventCode)?.name || eventCode;
                    const color = eventColorByCode.get(eventCode) || "#b91c1c";
                    errorDatasets.push({
                        label: `${eventName}, Error rate %`,
                        data: labels.map((label) => {
                            const row = new Map(eventRows.map((item) => [item.stageTypeName || item.stageTypeCode, item])).get(label);
                            return toPercentNumber(row?.errorRate);
                        }),
                        backgroundColor: withAlphaColor(color, 0.78),
                        borderRadius: 8
                    });
                });
            }
            return {
                type: "bar",
                data: {
                    labels,
                    datasets: errorDatasets
                },
                options: barChartOptions("%")
            };
        }
        const data = await fetchJson(`${api("/overview")}?${params.toString()}`);
        const labels = (data.series || []).map((point) => formatTime(point.time));
        const countSeries = (data.series || []).map((point) => point.count || 0);
        const avgSeries = (data.series || []).map((point) => point.avgMs || 0);
        const p95Series = (data.series || []).map((point) => point.p95Ms || 0);
        const p99Series = (data.series || []).map((point) => point.p99Ms || 0);
        const errSeries = (data.series || []).map((point) => toPercentNumber(point.errorRate));
        if (canvasId === "chart-events-count") {
            if (Array.isArray(options.eventCodes) && options.eventCodes.length > 0) {
                return await buildExpandedEventSeriesChartConfig(params, options.eventCodes, "flow", labelSuffix, {
                    includeOverall: !!options.includeOverall,
                    overallSeries: countSeries
                });
            }
            const sampled = downsampleSeries(labels, [countSeries], MAX_CHART_POINTS);
            return {
                type: "line",
                data: {
                    labels: sampled.labels,
                    datasets: [{
                        label: withSuffix("Количество"),
                        data: sampled.datasets[0] || [],
                        borderColor: colors.primary,
                        backgroundColor: "rgba(109, 40, 217, 0.15)",
                        fill: true,
                        tension: 0.28,
                        pointRadius: 1.5
                    }]
                },
                options: baseChartOptions("Количество")
            };
        }
        if (canvasId === "chart-latency") {
            if (Array.isArray(options.eventCodes) && options.eventCodes.length > 0) {
                if (options.eventCodes.length === 1) {
                    const singleCode = options.eventCodes[0];
                    const eventParams = new URLSearchParams(params.toString());
                    eventParams.set("eventTypeCode", singleCode);
                    const payload = await fetchJson(`${api("/overview")}?${eventParams.toString()}`);
                    const singleLabels = (payload?.series || []).map((point) => formatTime(point.time));
                    const singleAvg = (payload?.series || []).map((point) => point.avgMs || 0);
                    const singleP95 = (payload?.series || []).map((point) => point.p95Ms || 0);
                    const singleP99 = (payload?.series || []).map((point) => point.p99Ms || 0);
                    const sampledSingle = downsampleSeries(singleLabels, [singleAvg, singleP95, singleP99], MAX_CHART_POINTS);
                    const singleName = (state.dictionaries?.eventTypes || []).find((item) => item.code === singleCode)?.name || singleCode;
                    return {
                        type: "line",
                        data: {
                            labels: sampledSingle.labels,
                            datasets: [
                                {label: `${singleName}: AVG`, data: sampledSingle.datasets[0] || [], borderColor: colors.teal, backgroundColor: "rgba(15,118,110,0.14)", tension: 0.25, pointRadius: 1.2},
                                {label: `${singleName}: P95`, data: sampledSingle.datasets[1] || [], borderColor: colors.accent, backgroundColor: "rgba(124,58,237,0.16)", tension: 0.25, pointRadius: 1.2},
                                {label: `${singleName}: P99`, data: sampledSingle.datasets[2] || [], borderColor: colors.amber, backgroundColor: "rgba(180,83,9,0.16)", tension: 0.25, pointRadius: 1.2}
                            ]
                        },
                        options: baseChartOptions("ms")
                    };
                }
                return await buildExpandedEventSeriesChartConfig(params, options.eventCodes, "latency", labelSuffix, {
                    includeOverall: !!options.includeOverall,
                    overallSeries: p95Series,
                    latencyMetric: options.latencyMetric
                });
            }
            const sampled = downsampleSeries(labels, [avgSeries, p95Series, p99Series], MAX_CHART_POINTS);
            return {
                type: "line",
                data: {
                    labels: sampled.labels,
                    datasets: [
                        {label: withSuffix("AVG"), data: sampled.datasets[0] || [], borderColor: colors.teal, backgroundColor: "rgba(15,118,110,0.14)", tension: 0.25, pointRadius: 1.2},
                        {label: withSuffix("P95"), data: sampled.datasets[1] || [], borderColor: colors.accent, backgroundColor: "rgba(124,58,237,0.16)", tension: 0.25, pointRadius: 1.2},
                        {label: withSuffix("P99"), data: sampled.datasets[2] || [], borderColor: colors.amber, backgroundColor: "rgba(180,83,9,0.16)", tension: 0.25, pointRadius: 1.2}
                    ]
                },
                options: baseChartOptions("ms")
            };
        }
        if (canvasId === "chart-error-rate") {
            if (Array.isArray(options.eventCodes) && options.eventCodes.length > 0) {
                return await buildExpandedEventSeriesChartConfig(params, options.eventCodes, "error", labelSuffix, {
                    includeOverall: !!options.includeOverall,
                    overallSeries: errSeries
                });
            }
            const sampled = downsampleSeries(labels, [errSeries], MAX_CHART_POINTS);
            return {
                type: "line",
                data: {
                    labels: sampled.labels,
                    datasets: [{
                        label: withSuffix("Error rate") + ", %",
                        data: sampled.datasets[0] || [],
                        borderColor: colors.red,
                        backgroundColor: "rgba(185,28,28,0.16)",
                        fill: true,
                        tension: 0.25,
                        pointRadius: 1.2
                    }]
                },
                options: baseChartOptions("%")
            };
        }
        return buildEventKpiSingleChartConfig(buildEventKpiRows(data.eventBreakdown || []), {
            labelSuffix
        });
    }

    async function buildExpandedEventSeriesChartConfig(baseParams, eventCodes, mode, labelSuffix, options = {}) {
        const selectedCodes = Array.from(new Set((eventCodes || []).map((code) => String(code || "").trim()).filter((code) => code)));
        const isErrorMode = mode === "error";
        if (!selectedCodes.length) {
            return {
                type: "line",
                data: {labels: [], datasets: []},
                options: baseChartOptions(mode === "error" ? "%" : (mode === "latency" ? "ms" : "Количество"))
            };
        }
        const responses = await Promise.all(selectedCodes.map(async (code) => {
            const params = new URLSearchParams(baseParams.toString());
            params.set("eventTypeCode", code);
            const payload = await fetchJson(`${api("/overview")}?${params.toString()}`);
            return {code, payload};
        }));
        const labelsRaw = (responses[0]?.payload?.series || []).map((point) => formatTime(point.time));
        const colorByCode = buildDistinctEventColors(selectedCodes);
        const datasetsRaw = responses.map(({code, payload}, index) => {
            const points = payload?.series || [];
            const values = points.map((point) => {
                if (mode === "error") {
                    return toPercentNumber(point.errorRate);
                }
                if (mode === "latency") {
                    const metricMode = String(options.latencyMetric || "p95").toLowerCase();
                    if (metricMode === "avg") {
                        return point.avgMs || 0;
                    }
                    if (metricMode === "p99") {
                        return point.p99Ms || 0;
                    }
                    return point.p95Ms || 0;
                }
                return point.count || 0;
            });
            const dictionaryName = (state.dictionaries?.eventTypes || []).find((item) => item.code === code)?.name || code;
            const color = colorByCode.get(code) || `hsl(${(index * 43) % 360} 72% 45%)`;
            return {
                label: labelSuffix ? `${dictionaryName} ${labelSuffix}` : dictionaryName,
                data: values,
                borderColor: color,
                backgroundColor: isErrorMode ? withAlphaColor(color, 0.16) : "transparent",
                tension: 0.24,
                pointRadius: 1.2,
                spanGaps: true,
                fill: isErrorMode
            };
        });
        if (options.includeOverall && Array.isArray(options.overallSeries) && options.overallSeries.length > 0) {
            datasetsRaw.unshift({
                label: labelSuffix ? `Общая статистика ${labelSuffix}` : "Общая статистика",
                data: options.overallSeries,
                borderColor: "#111827",
                backgroundColor: isErrorMode ? "rgba(17, 24, 39, 0.12)" : "rgba(17, 24, 39, 0.12)",
                fill: true,
                tension: 0.24,
                pointRadius: 1.1,
                spanGaps: true,
                borderDash: [5, 3]
            });
        }
        const sampled = downsampleSeries(labelsRaw, datasetsRaw.map((item) => item.data), MAX_CHART_POINTS);
        const datasets = datasetsRaw.map((item, index) => ({...item, data: sampled.datasets[index] || []}));
        return {
            type: "line",
            data: {
                labels: sampled.labels,
                datasets
            },
            options: baseChartOptions(mode === "error" ? "%" : (mode === "latency" ? "P95, ms" : "Количество"))
        };
    }

    function initHelpUi() {
        upgradeGlobalScenarioSelect();
        attachPanelHelpButtons();
        attachKpiHoverHints();
    }

    function attachPanelHelpButtons() {
        const panelTargets = Array.from(CHART_HELP_TARGETS);

        panelTargets.forEach((targetId) => {
            const target = document.getElementById(targetId);
            if (!target) {
                return;
            }
            const panel = target.closest(".analytics-panel");
            const head = panel?.querySelector(".analytics-panel-head");
            if (!panel || !head || head.querySelector(`[data-help-target='${targetId}']`)) {
                return;
            }
            const text = HELP_TEXTS[targetId] || ANALYTICS_CHART_HELP_REGISTRY[targetId]?.shortDescription;
            if (!text) {
                return;
            }
            const button = document.createElement("button");
            button.type = "button";
            button.className = "btn btn-outline-secondary analytics-chart-icon-btn analytics-help-btn";
            button.setAttribute("data-help-target", targetId);
            button.title = "Как читать этот блок";
            button.setAttribute("aria-label", "Как читать этот блок");
            button.textContent = "?";

            button.addEventListener("click", () => {
                openHelpModal(targetId, target);
            });

            const chartWrap = target.tagName.toLowerCase() === "canvas"
                ? target.closest(".analytics-chart-wrap")
                : null;

            if (chartWrap) {
                const actions = ensureChartActionsBar(chartWrap);
                if (actions.querySelector(`[data-help-target='${targetId}']`)) {
                    return;
                }
                actions.appendChild(button);
            } else {
                head.appendChild(button);
            }
        });
    }

    function ensureChartActionsBar(wrap) {
        const host = resolveChartActionsHost(wrap);
        let actions = host.querySelector(":scope > .analytics-chart-actions");
        if (!actions) {
            actions = document.createElement("div");
            actions.className = "analytics-chart-actions";
            host.appendChild(actions);
        }
        return actions;
    }

    function attachKpiHoverHints() {
        const kpiIds = [
            "kpi-total-events",
            "kpi-avg-ms",
            "kpi-p95-ms",
            "kpi-p99-ms",
            "kpi-error-rate",
            "kpi-errors"
        ];
        kpiIds.forEach((id) => {
            const valueEl = document.getElementById(id);
            const card = valueEl?.closest(".analytics-kpi-card");
            if (!valueEl || !card || card.querySelector(`[data-kpi-help='${id}']`)) {
                return;
            }
            const hint = HELP_TEXTS[id];
            if (!hint) {
                return;
            }
            const label = card.querySelector(".analytics-kpi-label");
            if (!label) {
                return;
            }
            const badge = document.createElement("span");
            badge.className = "analytics-kpi-help-badge";
            badge.setAttribute("data-kpi-help", id);
            badge.setAttribute("data-tooltip", hint);
            badge.tabIndex = 0;
            badge.textContent = "?";
            label.appendChild(badge);
        });
    }

    function handleParameterHelpPointerDown(event) {
        const button = event.target?.closest?.("[data-parameter-help]");
        if (!button) {
            return;
        }
        event.stopPropagation();
        if (typeof event.stopImmediatePropagation === "function") {
            event.stopImmediatePropagation();
        }
    }

    function handleParameterHelpClick(event) {
        const button = event.target?.closest?.("[data-parameter-help]");
        if (!button) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        if (typeof event.stopImmediatePropagation === "function") {
            event.stopImmediatePropagation();
        }
        openParameterHelpModal(button.getAttribute("data-parameter-help") || "");
    }

    function ensureParameterHelpButtons(root = document) {
        const scope = root || document;
        ANALYTICS_PARAMETER_HELP_BINDINGS.forEach((binding) => {
            scope.querySelectorAll(binding.selector)?.forEach((target) => {
                attachParameterHelpButton(target, binding.helpCode, binding);
            });
        });
    }

    function attachParameterHelpButton(target, helpCode, binding = {}) {
        if (!target || !helpCode || !ANALYTICS_PARAMETER_HELP_REGISTRY[helpCode]) {
            return;
        }
        const host = target.closest(
            ".analytics-filter-field, .analytics-universal-field, .analytics-stage-metric-field, .analytics-events-field, .analytics-expanded-range-group, .analytics-expanded-actions, .analytics-quick-range-main, .analytics-stage-metric-compare-toggle-wrap"
        ) || target.parentElement;
        if (!host) {
            return;
        }
        if (host.querySelector(`.analytics-parameter-help-btn[data-parameter-help="${cssEscape(helpCode)}"]`)) {
            return;
        }
        const label = findParameterHelpLabel(host, target);
        const button = createParameterHelpButton(helpCode);
        if (label) {
            label.classList.add("analytics-label-with-help");
            label.appendChild(button);
            return;
        }
        if (binding.placement === "before" && target.parentElement) {
            button.classList.add("analytics-parameter-help-btn-inline");
            target.insertAdjacentElement("beforebegin", button);
            return;
        }
        button.classList.add("analytics-parameter-help-btn-inline");
        host.insertBefore(button, host.firstChild);
    }

    function findParameterHelpLabel(host, target) {
        if (!host) {
            return null;
        }
        const label = host.querySelector("label.form-label, .analytics-expanded-range-label, .analytics-quick-range-label");
        if (label && label.textContent.trim()) {
            return label;
        }
        if (target?.id) {
            const explicit = document.querySelector(`label[for="${cssEscape(target.id)}"]`);
            if (explicit && explicit.textContent.trim()) {
                return explicit;
            }
        }
        return null;
    }

    function createParameterHelpButton(helpCode) {
        const help = ANALYTICS_PARAMETER_HELP_REGISTRY[helpCode] || {};
        const button = document.createElement("button");
        button.type = "button";
        button.className = "analytics-parameter-help-btn";
        button.setAttribute("data-parameter-help", helpCode);
        button.setAttribute("aria-label", `Справка: ${help.title || helpCode}`);
        button.title = help.title || "Справка по параметру";
        button.textContent = "?";
        return button;
    }

    function openParameterHelpModal(helpCode) {
        if (!refs.helpModalEl || !refs.helpModalTitle || !refs.helpModalBody) {
            return;
        }
        const help = ANALYTICS_PARAMETER_HELP_REGISTRY[helpCode] || {
            title: "Справка по параметру",
            shortDescription: "Этот параметр меняет срез данных или способ чтения графика.",
            whatItDoes: "Проверьте выбранный период, фильтры и режим сравнения перед выводами."
        };
        refs.helpModalTitle.textContent = help.title || "Справка по параметру";
        refs.helpModalBody.innerHTML = buildParameterHelpHtml(help);
        const modal = bootstrap.Modal.getOrCreateInstance(refs.helpModalEl);
        modal.show();
    }

    function buildParameterHelpHtml(help) {
        return `
            <div class="analytics-help-block analytics-parameter-help-block">
                ${help.shortDescription ? `<div class="analytics-help-modal-subtitle">${escapeHtml(help.shortDescription)}</div>` : ""}
                ${renderHelpSection("Что делает", `<p>${escapeHtml(help.whatItDoes || help.shortDescription || "")}</p>`)}
                ${help.whenToUse ? renderHelpSection("Когда использовать", `<p>${escapeHtml(help.whenToUse)}</p>`) : ""}
                ${Array.isArray(help.howToUse) && help.howToUse.length ? renderHelpSection("Как настроить", renderHelpList(help.howToUse)) : ""}
                ${help.whatChangesOnChart ? renderHelpSection("Что изменится на графике", `<p>${escapeHtml(help.whatChangesOnChart)}</p>`) : ""}
                ${Array.isArray(help.commonMistakes) && help.commonMistakes.length ? renderHelpSection("Частые ошибки", renderHelpList(help.commonMistakes)) : ""}
                ${Array.isArray(help.examples) && help.examples.length ? renderHelpSection("Примеры", renderHelpList(help.examples)) : ""}
                ${Array.isArray(help.relatedControls) && help.relatedControls.length ? renderHelpSection("Связанные параметры", renderHelpList(help.relatedControls)) : ""}
            </div>
        `;
    }

    function cssEscape(value) {
        if (window.CSS && typeof window.CSS.escape === "function") {
            return window.CSS.escape(String(value || ""));
        }
        return String(value || "").replace(/["\\]/g, "\\$&");
    }

    function openHelpModal(targetId, targetEl) {
        if (!refs.helpModalEl || !refs.helpModalTitle || !refs.helpModalBody) {
            return;
        }
        const panel = targetEl.closest(".analytics-panel");
        const blockTitle = targetEl.closest(".analytics-stage-metric-chart-card, .analytics-stage-metric-block")
            ?.querySelector(".analytics-stage-metric-block-title")
            ?.textContent
            ?.trim();
        const title = blockTitle
            || panel?.querySelector(".analytics-panel-title")?.textContent?.trim()
            || targetId
            || "Подсказка";
        const sub = panel?.querySelector(".analytics-panel-sub")?.textContent?.trim() || "";
        const help = ANALYTICS_CHART_HELP_REGISTRY[targetId] || null;
        refs.helpModalTitle.textContent = help?.title || title;
        refs.helpModalBody.innerHTML = buildChartHelpHtml(targetId, title, sub);
        const modal = bootstrap.Modal.getOrCreateInstance(refs.helpModalEl);
        modal.show();
    }

    function buildChartHelpHtml(targetId, title, sub) {
        const help = ANALYTICS_CHART_HELP_REGISTRY[targetId] || null;
        const fallbackSummary = sub || HELP_TEXTS[targetId] || "Выберите период и фильтры, затем сравните динамику по ключевым метрикам.";
        const scenarios = resolveChartScenarioOptions(targetId);
        if (!help) {
            return `
                <div class="analytics-help-block">
                    ${renderHelpSection(title || "Подсказка", `<p>${escapeHtml(fallbackSummary)}</p>`)}
                    ${scenarios.length ? renderScenarioHelpCards(targetId, scenarios) : ""}
                </div>
            `;
        }
        return `
                <div class="analytics-help-block">
                    ${renderHelpSection("Что показывает", `<p>${escapeHtml(help.whatItShows || help.shortDescription || fallbackSummary)}</p>`)}
                    ${help.howToRead ? renderHelpSection("Как читать", `<p>${escapeHtml(help.howToRead)}</p>`) : ""}
                ${Array.isArray(help.metrics) && help.metrics.length ? renderHelpSection("Метрики", renderMetricHelpList(help.metrics)) : ""}
                ${getHelpPatterns(help).length ? renderHelpSection("Типовые паттерны", renderTrendCards(getHelpPatterns(help))) : ""}
                ${getHelpActions(help).length ? renderHelpSection("Что проверить", renderHelpList(getHelpActions(help))) : ""}
                ${Array.isArray(help.analysisMistakes) && help.analysisMistakes.length ? renderHelpSection("Частые ошибки чтения", renderHelpList(help.analysisMistakes)) : ""}
                ${Array.isArray(help.relatedCharts) && help.relatedCharts.length ? renderHelpSection("Смотреть рядом", renderHelpList(help.relatedCharts)) : ""}
                ${scenarios.length ? renderScenarioHelpCards(targetId, scenarios) : ""}
            </div>
        `;
    }

    function getHelpPatterns(help) {
        return Array.isArray(help?.patterns) && help.patterns.length
            ? help.patterns
            : (Array.isArray(help?.trendPatterns) ? help.trendPatterns : []);
    }

    function getHelpActions(help) {
        return Array.isArray(help?.actions) && help.actions.length
            ? help.actions
            : (Array.isArray(help?.problemSignals) ? help.problemSignals : []);
    }

    function renderHelpSection(title, html) {
        if (!html) {
            return "";
        }
        return `
            <section class="analytics-help-modal-section">
                <h6 class="analytics-help-section-title">${escapeHtml(title)}</h6>
                <div class="analytics-help-section-body">${html}</div>
            </section>
        `;
    }

    function renderHelpList(items) {
        return `
            <ul class="analytics-help-list">
                ${(items || []).map((item) => `<li>${escapeHtml(item)}</li>`).join("")}
            </ul>
        `;
    }

    function renderMetricHelpList(metrics) {
        return `
            <div class="analytics-help-metric-list">
                ${(metrics || []).map((metric) => `
                    <div class="analytics-help-metric-item">
                        <span class="analytics-help-metric-name">${escapeHtml(metric.name || "")}</span>
                        <span class="analytics-help-metric-description">${escapeHtml(metric.description || "")}</span>
                    </div>
                `).join("")}
            </div>
        `;
    }

    function renderTrendCards(patterns) {
        return `
            <div class="analytics-help-trend-grid">
                ${patterns.map((pattern) => `
                    <div class="analytics-help-trend-card">
                        ${renderTrendIcon(pattern.icon)}
                        <div>
                            <div class="analytics-help-trend-title">${escapeHtml(pattern.title || "Паттерн")}</div>
                            <div class="analytics-help-trend-text">${escapeHtml(pattern.text || pattern.description || "")}</div>
                            ${pattern.howToCheck ? `<div class="analytics-help-trend-text"><b>Как проверить:</b> ${escapeHtml(pattern.howToCheck)}</div>` : ""}
                            ${pattern.falseAlarm ? `<div class="analytics-help-trend-text"><b>Ложная тревога:</b> ${escapeHtml(pattern.falseAlarm)}</div>` : ""}
                        </div>
                    </div>
                `).join("")}
            </div>
        `;
    }

    function renderScenarioHelpCards(chartId, scenarios) {
        return renderHelpSection("Сценарии", `
            <div class="analytics-help-scenario-list">
                ${scenarios.map((scenario) => `
                    <button type="button"
                            class="analytics-help-scenario-card"
                            data-help-scenario-chart="${escapeHtml(chartId)}"
                            data-help-scenario-id="${escapeHtml(scenario.id)}">
                        <span class="analytics-help-scenario-title">${escapeHtml(scenario.label || scenario.id)}</span>
                        <span class="analytics-help-scenario-text">${escapeHtml(scenario.description || scenario.details || "")}</span>
                    </button>
                `).join("")}
            </div>
        `);
    }

    function renderTrendIcon(iconId) {
        const path = ANALYTICS_TREND_ICON_REGISTRY[iconId] || ANALYTICS_TREND_ICON_REGISTRY.trend_up;
        return `
            <svg class="analytics-help-trend-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path d="${escapeHtml(path)}"></path>
            </svg>
        `;
    }

    function formatMetric(value, unit) {
        const formatted = toNumber(value).toFixed(2);
        const localizedUnit = localizeUnit(unit);
        return localizedUnit ? `${formatted} ${localizedUnit}` : formatted;
    }

    function formatMs(value) {
        return `${toNumber(value).toFixed(2)}`;
    }

    function formatPercent(value) {
        return `${(toNumber(value) * 100).toFixed(2)}%`;
    }

    function toPercentNumber(value) {
        return Number((toNumber(value) * 100).toFixed(4));
    }

    function toNumber(value) {
        const num = Number(value);
        return Number.isFinite(num) ? num : 0;
    }

    function formatInt(value) {
        return Math.round(toNumber(value)).toLocaleString("ru-RU");
    }

    function formatDelta(value) {
        const num = toNumber(value);
        const sign = num > 0 ? "+" : "";
        let className = "is-neutral";
        if (num > 0.0001) {
            className = "is-up";
        } else if (num < -0.0001) {
            className = "is-down";
        }
        return {
            text: `${sign}${num.toFixed(2)}%`,
            className
        };
    }

    function compactAttributes(attributes) {
        const entries = Object.entries(attributes || {});
        if (!entries.length) {
            return "-";
        }
        return entries.slice(0, 2).map(([key, value]) => `${key}: ${value}`).join(" · ");
    }

    function renderTopValuesAccordion(values) {
        const rows = Array.isArray(values) ? values.filter((item) => item && item.value != null) : [];
        if (!rows.length) {
            return "-";
        }

        if (rows.length === 1) {
            const row = rows[0];
            return `${escapeHtml(row.value)} (${formatInt(row.count || 0)})`;
        }

        const preview = rows.slice(0, 2)
            .map((item) => `${escapeHtml(item.value)} (${formatInt(item.count || 0)})`)
            .join(" · ");
        const moreCount = Math.max(rows.length - 2, 0);
        const previewText = moreCount > 0 ? `${preview} +${moreCount}` : preview;
        const listHtml = rows.map((item) => `
            <div class="analytics-top-values-item">
                <span class="analytics-top-values-value">${escapeHtml(item.value)}</span>
                <span class="analytics-top-values-count">${formatInt(item.count || 0)}</span>
            </div>
        `).join("");

        return `
            <details class="analytics-top-values-accordion">
                <summary class="analytics-top-values-summary">
                    <span class="analytics-top-values-preview">${previewText}</span>
                    <span class="analytics-top-values-size">${formatInt(rows.length)}</span>
                </summary>
                <div class="analytics-top-values-list">${listHtml}</div>
            </details>
        `;
    }

    function eventsInfoRow(message, isError) {
        const className = isError ? "text-danger" : "text-muted";
        return `<tr><td colspan="9" class="text-center ${className} py-3">${escapeHtml(message)}</td></tr>`;
    }

    function renderNormalizedLogsTable(logs, emptyMessage, includeRaw) {
        const rows = Array.isArray(logs) ? logs : [];
        if (!rows.length) {
            return `<div class="text-muted small">${escapeHtml(emptyMessage || "Логи не найдены.")}</div>`;
        }
        const compactCell = (value, className, lines) => {
            const text = value == null || value === "" ? "-" : String(value);
            const title = text.replace(/\s+/g, " ").trim();
            return `<div class="analytics-log-cell ${className} analytics-clamp-${lines}" title="${escapeHtml(title)}">${escapeHtml(text)}</div>`;
        };
        const bodyHtml = rows.map((entry, index) => {
            const status = (entry.status || "").toUpperCase();
            const statusBadge = `
                <span class="analytics-status-badge ${logStatusBadgeClass(status)}">
                    ${escapeHtml(status || "INFO")}
                </span>
            `;
            const duration = entry.durationMs == null ? "-" : `${formatInt(entry.durationMs)} ms`;
            const operation = entry.operation || "-";
            const raw = entry.rawMessage || entry.message || "-";
            const message = entry.message || raw;
            const messageNormalized = String(message).replace(/\s+/g, " ").trim();
            const hasHiddenMessageTail = messageNormalized.length > 180;
            const rowId = String(index);
            const rawToggleCell = includeRaw
                ? `
                    <td class="analytics-log-col-toggle text-center">
                        <button type="button"
                                class="analytics-log-raw-toggle"
                                data-raw-row-id="${rowId}"
                                aria-expanded="false"
                                title="Показать исходную строку"
                                aria-label="Показать исходную строку">
                            <i class="bi bi-chevron-right"></i>
                        </button>
                    </td>
                `
                : "";
            const rawExpandedRow = includeRaw
                ? `
                    <tr class="analytics-log-raw-row" data-raw-row-id="${rowId}" hidden>
                        <td colspan="8" class="analytics-log-raw-cell">
                            <div class="analytics-log-raw-panel">
                                <div class="analytics-log-raw-head">
                                    <span>Исходная строка лога</span>
                                    <button type="button"
                                            class="btn btn-outline-secondary btn-sm analytics-log-copy-raw"
                                            title="Копировать исходную строку"
                                            aria-label="Копировать исходную строку">
                                        <i class="bi bi-clipboard"></i>
                                    </button>
                                </div>
                                <pre>${escapeHtml(raw)}</pre>
                            </div>
                        </td>
                    </tr>
                `
                : "";
            return `
                <tr class="analytics-log-row">
                    ${rawToggleCell}
                    <td class="small text-muted text-nowrap analytics-log-col-time">${formatLogDateTime(entry.timestamp)}</td>
                    <td class="analytics-log-col-status">${statusBadge}</td>
                    <td class="analytics-log-col-layer">${compactCell(entry.layer || "-", "analytics-log-layer", 1)}</td>
                    <td class="analytics-log-col-source">${compactCell(entry.source || "-", "analytics-log-source", 1)}</td>
                    <td class="analytics-log-col-operation">${compactCell(operation, "analytics-log-operation", 2)}</td>
                    <td class="text-end text-nowrap analytics-log-col-duration">${duration}</td>
                    <td class="analytics-log-col-message">
                        <div class="analytics-log-message-wrap">
                            <div class="analytics-log-message-main">
                                ${compactCell(message, "analytics-log-message", 2)}
                                ${hasHiddenMessageTail ? `
                                    <button type="button"
                                            class="analytics-log-message-toggle"
                                            aria-expanded="false"
                                            title="Нормализовать сообщения"
                                            aria-label="Нормализовать сообщения">
                                        <i class="bi bi-chevron-down"></i>
                                    </button>
                                ` : ""}
                            </div>
                            ${hasHiddenMessageTail ? `<pre class="analytics-log-message-full" hidden>${escapeHtml(message)}</pre>` : ""}
                        </div>
                    </td>
                </tr>
                ${rawExpandedRow}
            `;
        }).join("");

        return `
            <div class="table-responsive analytics-event-log-table-wrap">
                <table class="table table-sm align-middle analytics-table analytics-event-log-table mb-0">
                    <thead>
                    <tr>
                        ${includeRaw ? "<th class=\"analytics-log-col-toggle\"></th>" : ""}
                        <th class="analytics-log-col-time">Время</th>
                        <th class="analytics-log-col-status">Статус</th>
                        <th class="analytics-log-col-layer">Слой</th>
                        <th class="analytics-log-col-source">Источник</th>
                        <th class="analytics-log-col-operation">Операция</th>
                        <th class="text-end analytics-log-col-duration">Длит.</th>
                        <th class="analytics-log-col-message">Сообщение</th>
                    </tr>
                    </thead>
                    <tbody>${bodyHtml}</tbody>
                </table>
            </div>
        `;
    }

    function toggleRawLogRow(toggleButton) {
        const rowId = (toggleButton.getAttribute("data-raw-row-id") || "").trim();
        const table = toggleButton.closest("table");
        if (!rowId || !table) {
            return;
        }
        const targetRow = table.querySelector(`.analytics-log-raw-row[data-raw-row-id="${rowId}"]`);
        if (!targetRow) {
            return;
        }
        const shouldOpen = toggleButton.getAttribute("aria-expanded") !== "true";

        table.querySelectorAll(".analytics-log-raw-row").forEach((row) => {
            row.hidden = true;
        });
        table.querySelectorAll(".analytics-log-raw-toggle").forEach((button) => {
            button.setAttribute("aria-expanded", "false");
        });
        table.querySelectorAll(".analytics-log-row").forEach((row) => {
            row.classList.remove("is-raw-open");
        });

        if (!shouldOpen) {
            return;
        }

        targetRow.hidden = false;
        toggleButton.setAttribute("aria-expanded", "true");
        toggleButton.closest(".analytics-log-row")?.classList.add("is-raw-open");
    }

    async function copyRawLogRow(copyButton) {
        const panel = copyButton.closest(".analytics-log-raw-panel");
        const pre = panel?.querySelector("pre");
        if (!pre) {
            return;
        }
        const text = pre.textContent || "";
        try {
            if (navigator.clipboard?.writeText) {
                await navigator.clipboard.writeText(text);
            } else {
                const textarea = document.createElement("textarea");
                textarea.value = text;
                textarea.setAttribute("readonly", "readonly");
                textarea.style.position = "absolute";
                textarea.style.left = "-9999px";
                document.body.appendChild(textarea);
                textarea.select();
                document.execCommand("copy");
                document.body.removeChild(textarea);
            }
            const icon = copyButton.querySelector("i");
            const originalClass = icon?.className || "";
            if (icon) {
                icon.className = "bi bi-check2";
            }
            copyButton.disabled = true;
            setTimeout(() => {
                if (icon) {
                    icon.className = originalClass;
                }
                copyButton.disabled = false;
            }, 1200);
        } catch (error) {
            console.error("Raw copy failed", error);
        }
    }

    function toggleMessageCell(button) {
        const wrap = button.closest(".analytics-log-message-wrap");
        if (!wrap) {
            return;
        }
        const fullBlock = wrap.querySelector(".analytics-log-message-full");
        if (!fullBlock) {
            return;
        }
        const isExpanded = button.getAttribute("aria-expanded") === "true";
        if (isExpanded) {
            button.setAttribute("aria-expanded", "false");
            fullBlock.hidden = true;
            return;
        }
        button.setAttribute("aria-expanded", "true");
        fullBlock.hidden = false;
    }

    function logStatusBadgeClass(status) {
        switch ((status || "").toUpperCase()) {
            case "OK":
                return "analytics-badge-success";
            case "ERROR":
                return "analytics-badge-error";
            case "WARN":
                return "analytics-badge-warning";
            case "START":
            case "DETAIL":
            case "DEBUG":
                return "analytics-badge-info";
            default:
                return "analytics-badge-muted";
        }
    }

    function errorClassBadgeClass(errorClass) {
        switch ((errorClass || "").toUpperCase()) {
            case "VALIDATION":
                return "analytics-badge-warning";
            case "BUSINESS":
                return "analytics-badge-info";
            case "SYSTEM":
                return "analytics-badge-error";
            default:
                return "analytics-badge-muted";
        }
    }

    function showEventModalError(message) {
        refs.eventModalBody.innerHTML = `
            <div class="alert alert-danger mb-0">
                ${escapeHtml(message)}
            </div>
        `;
        if (typeof bootstrap !== "undefined" && bootstrap.Modal) {
            const modal = bootstrap.Modal.getOrCreateInstance(refs.eventModalEl);
            modal.show();
        }
    }

    function localizeUnit(unit) {
        if (!unit) {
            return "";
        }
        const normalized = String(unit).trim().toLowerCase();
        return {
            count: "шт",
            bytes: "байт",
            ms: "мс"
        }[normalized] || unit;
    }

    function normalizeMetricUnitKey(unit) {
        const normalized = String(unit || "").trim().toLowerCase();
        return normalized || "__unitless__";
    }

    function normalizeSeriesToPercent(series) {
        const values = Array.isArray(series) ? series : [];
        const numericValues = values
            .map((value) => Number(value))
            .filter((value, index) => values[index] != null && Number.isFinite(value));
        const maxValue = numericValues.length ? Math.max(...numericValues.map((value) => Math.abs(value))) : 0;
        if (!Number.isFinite(maxValue) || maxValue <= 0) {
            return values.map((value) => (value == null ? null : 0));
        }
        return values.map((value) => {
            if (value == null) {
                return null;
            }
            const numeric = Number(value);
            if (!Number.isFinite(numeric)) {
                return null;
            }
            return Number(((Math.abs(numeric) / maxValue) * 100).toFixed(4));
        });
    }

    function downsampleSeries(labels, datasetSeries, maxPoints) {
        const safeLabels = Array.isArray(labels) ? labels : [];
        const safeSeries = Array.isArray(datasetSeries)
            ? datasetSeries.map((series) => (Array.isArray(series) ? series : []))
            : [];

        if (safeLabels.length <= maxPoints || maxPoints <= 0) {
            return {
                labels: safeLabels,
                datasets: safeSeries
            };
        }

        const step = Math.ceil(safeLabels.length / maxPoints);
        const indexes = [];
        for (let index = 0; index < safeLabels.length; index += step) {
            indexes.push(index);
        }

        const lastIndex = safeLabels.length - 1;
        if (indexes[indexes.length - 1] !== lastIndex) {
            indexes.push(lastIndex);
        }

        return {
            labels: indexes.map((index) => safeLabels[index]),
            datasets: safeSeries.map((series) => indexes.map((index) => toNumber(series[index])))
        };
    }

    function buildUniversalEventKpiRows(eventBreakdown, categories = [], eventNameByCode = new Map()) {
        const normalizedCategories = Array.isArray(categories)
            ? categories.map((code) => String(code || "").trim()).filter(Boolean)
            : [];
        const rows = buildEventKpiRows(eventBreakdown, normalizedCategories);
        if (!normalizedCategories.length) {
            return rows;
        }
        return rows.map((row) => {
            const label = row.label && row.label !== row.key
                ? row.label
                : (eventNameByCode.get(row.key) || row.label || row.key);
            return {
                ...row,
                label
            };
        }).sort(compareEventKpiRowsByCurrent);
    }

    function buildEventKpiRows(eventBreakdown, categories) {
        const rows = Array.isArray(eventBreakdown) ? eventBreakdown : [];
        const source = rows.map((row) => ({
            key: String(row?.eventTypeCode || row?.eventTypeName || "").trim(),
            label: String(row?.eventTypeName || row?.eventTypeCode || "-").trim() || "-",
            count: Number(row?.count || 0),
            p95: Number(row?.p95Ms || 0),
            err: toPercentNumber(row?.errorRate)
        })).filter((row) => row.key);
        const byKey = new Map();
        source.forEach((row) => {
            const existing = byKey.get(row.key);
            const count = toNumberSafe(row.count);
            const errorCount = count * (toNumberSafe(row.err) / 100);
            if (!existing) {
                byKey.set(row.key, {
                    ...row,
                    count,
                    p95: toNumberSafe(row.p95),
                    err: toNumberSafe(row.err),
                    errorCount
                });
                return;
            }
            existing.count += count;
            existing.p95 = Math.max(toNumberSafe(existing.p95), toNumberSafe(row.p95));
            existing.errorCount += errorCount;
            existing.err = existing.count > 0
                ? Number(((existing.errorCount / existing.count) * 100).toFixed(4))
                : Math.max(toNumberSafe(existing.err), toNumberSafe(row.err));
            if (String(row.label || "").localeCompare(String(existing.label || ""), "ru") < 0) {
                existing.label = row.label;
            }
        });
        const keys = Array.isArray(categories) && categories.length
            ? Array.from(new Set(categories))
            : Array.from(byKey.keys());
        return keys
            .map((key) => {
                const row = byKey.get(key);
                if (!row) {
                    return {key, label: key, count: 0, p95: 0, err: 0};
                }
                const {errorCount: _errorCount, ...cleanRow} = row;
                return cleanRow;
            })
            .sort(compareEventKpiRowsByCurrent);
    }

    function compareEventKpiRowsByCurrent(left, right) {
        const countDelta = toNumberSafe(right?.count) - toNumberSafe(left?.count);
        if (countDelta !== 0) {
            return countDelta;
        }
        const p95Delta = toNumberSafe(right?.p95) - toNumberSafe(left?.p95);
        if (p95Delta !== 0) {
            return p95Delta;
        }
        const errDelta = toNumberSafe(right?.err) - toNumberSafe(left?.err);
        if (errDelta !== 0) {
            return errDelta;
        }
        const labelDelta = String(left?.label || "").localeCompare(String(right?.label || ""), "ru");
        if (labelDelta !== 0) {
            return labelDelta;
        }
        return String(left?.key || "").localeCompare(String(right?.key || ""), "ru");
    }

    function compareEventKpiOverlayRowsByBefore(left, right) {
        const countDelta = toNumberSafe(right?.countBefore) - toNumberSafe(left?.countBefore);
        if (countDelta !== 0) {
            return countDelta;
        }
        const p95Delta = toNumberSafe(right?.p95Before) - toNumberSafe(left?.p95Before);
        if (p95Delta !== 0) {
            return p95Delta;
        }
        const errDelta = toNumberSafe(right?.errBefore) - toNumberSafe(left?.errBefore);
        if (errDelta !== 0) {
            return errDelta;
        }
        const labelDelta = String(left?.label || "").localeCompare(String(right?.label || ""), "ru");
        if (labelDelta !== 0) {
            return labelDelta;
        }
        return String(left?.key || "").localeCompare(String(right?.key || ""), "ru");
    }

    function buildEventKpiCompareOverlayRows(beforeRowsRaw, afterRowsRaw, options = {}) {
        const beforeRows = Array.isArray(beforeRowsRaw) ? beforeRowsRaw : [];
        const afterRows = Array.isArray(afterRowsRaw) ? afterRowsRaw : [];
        const beforeByKey = new Map(beforeRows.map((row) => [row.key, row]));
        const afterByKey = new Map(afterRows.map((row) => [row.key, row]));
        const preserveCurrentOrder = options.preserveCurrentOrder !== false;
        let keys = [];
        const seen = new Set();
        afterRows.forEach((row) => {
            if (row.key && !seen.has(row.key)) {
                seen.add(row.key);
                keys.push(row.key);
            }
        });
        const beforeOnlyRows = beforeRows
            .filter((row) => row.key && !seen.has(row.key))
            .map((row) => ({
                key: row.key,
                label: String(row?.label || row?.key || "-"),
                countBefore: toNumberSafe(row?.count),
                p95Before: toNumberSafe(row?.p95),
                errBefore: toNumberSafe(row?.err)
            }))
            .sort(compareEventKpiOverlayRowsByBefore);
        beforeOnlyRows.forEach((row) => {
            seen.add(row.key);
            keys.push(row.key);
        });
        if (!preserveCurrentOrder) {
            keys = [...new Set([...beforeByKey.keys(), ...afterByKey.keys()])];
        }
        const rows = Array.from(keys).map((key) => {
            const before = beforeByKey.get(key);
            const after = afterByKey.get(key);
            return {
                key,
                label: String(after?.label || before?.label || key || "-"),
                countBefore: toNumberSafe(before?.count),
                countAfter: toNumberSafe(after?.count),
                p95Before: toNumberSafe(before?.p95),
                p95After: toNumberSafe(after?.p95),
                errBefore: toNumberSafe(before?.err),
                errAfter: toNumberSafe(after?.err)
            };
        });
        if (!preserveCurrentOrder) {
            rows.sort((a, b) => {
                const left = Math.max(a.countBefore, a.countAfter);
                const right = Math.max(b.countBefore, b.countAfter);
                if (right !== left) {
                    return right - left;
                }
                const p95Delta = Math.max(b.p95Before, b.p95After) - Math.max(a.p95Before, a.p95After);
                if (p95Delta !== 0) {
                    return p95Delta;
                }
                const errDelta = Math.max(b.errBefore, b.errAfter) - Math.max(a.errBefore, a.errAfter);
                if (errDelta !== 0) {
                    return errDelta;
                }
                const labelDelta = a.label.localeCompare(b.label, "ru");
                if (labelDelta !== 0) {
                    return labelDelta;
                }
                return String(a.key || "").localeCompare(String(b.key || ""), "ru");
            });
        }
        return rows;
    }

    function buildEventKpiOverlayChartConfig(currentRows, beforeRows, options = {}) {
        const overlayRows = buildEventKpiCompareOverlayRows(beforeRows, currentRows, options);
        const suffix = options.labelSuffix ? String(options.labelSuffix) : "";
        const withSuffix = (label) => suffix ? `${label} ${suffix}` : label;
        return {
            data: {
                labels: overlayRows.map((row) => row.label),
                datasets: [
                    {
                        type: "bar",
                        label: withSuffix("\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e"),
                        data: overlayRows.map((row) => row.countAfter),
                        backgroundColor: "rgba(109,40,217,0.75)",
                        borderRadius: 8,
                        yAxisID: "y"
                    },
                    {
                        type: "bar",
                        label: withSuffix("\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e (\u0414\u043e)"),
                        data: overlayRows.map((row) => row.countBefore),
                        backgroundColor: "rgba(109,40,217,0.35)",
                        borderRadius: 8,
                        yAxisID: "y"
                    },
                    {
                        type: "line",
                        label: withSuffix("P95"),
                        data: overlayRows.map((row) => row.p95After),
                        borderColor: colors.teal,
                        backgroundColor: "rgba(15,118,110,0.2)",
                        tension: 0.25,
                        yAxisID: "y1"
                    },
                    {
                        type: "line",
                        label: withSuffix("P95 (\u0414\u043e)"),
                        data: overlayRows.map((row) => row.p95Before),
                        borderColor: "rgba(15,118,110,0.6)",
                        borderDash: [6, 4],
                        backgroundColor: "rgba(15,118,110,0.14)",
                        tension: 0.25,
                        yAxisID: "y1"
                    },
                    {
                        type: "line",
                        label: withSuffix("\u041e\u0448\u0438\u0431\u043a\u0438"),
                        data: overlayRows.map((row) => row.errAfter),
                        borderColor: colors.red,
                        backgroundColor: "rgba(185,28,28,0.2)",
                        tension: 0.25,
                        yAxisID: "y2"
                    },
                    {
                        type: "line",
                        label: withSuffix("\u041e\u0448\u0438\u0431\u043a\u0438 (\u0414\u043e)"),
                        data: overlayRows.map((row) => row.errBefore),
                        borderColor: "rgba(185,28,28,0.62)",
                        borderDash: [6, 4],
                        backgroundColor: "rgba(185,28,28,0.14)",
                        tension: 0.25,
                        yAxisID: "y2"
                    }
                ]
            },
            options: eventKpiOptions()
        };
    }

    function buildEventKpiSingleChartConfig(currentRows, options = {}) {
        const rows = Array.isArray(currentRows) ? currentRows : [];
        const suffix = options.labelSuffix ? String(options.labelSuffix) : "";
        const withSuffix = (label) => suffix ? `${label} ${suffix}` : label;
        return {
            data: {
                labels: rows.map((row) => row.label),
                datasets: [
                    {
                        type: "bar",
                        label: withSuffix("\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e"),
                        data: rows.map((row) => row.count),
                        backgroundColor: "rgba(109,40,217,0.75)",
                        borderRadius: 8,
                        yAxisID: "y"
                    },
                    {
                        type: "line",
                        label: withSuffix("P95") + ", ms",
                        data: rows.map((row) => row.p95),
                        borderColor: colors.teal,
                        backgroundColor: "rgba(15,118,110,0.2)",
                        tension: 0.25,
                        yAxisID: "y1"
                    },
                    {
                        type: "line",
                        label: withSuffix("\u0414\u043e\u043b\u044f \u043e\u0448\u0438\u0431\u043e\u043a") + ", %",
                        data: rows.map((row) => row.err),
                        borderColor: colors.red,
                        backgroundColor: "rgba(185,28,28,0.2)",
                        tension: 0.25,
                        yAxisID: "y2"
                    }
                ]
            },
            options: eventKpiOptions()
        };
    }

    function toNumberSafe(value) {
        const num = Number(value);
        return Number.isFinite(num) ? num : 0;
    }

    function prepareEventKpiMiniTopNConfig(config, limit = 10) {
        const cloned = cloneChartConfig(config || {});
        const labels = Array.isArray(cloned?.data?.labels) ? cloned.data.labels : [];
        const datasets = Array.isArray(cloned?.data?.datasets) ? cloned.data.datasets : [];
        const totalCount = labels.length;
        if (totalCount <= limit || !datasets.length) {
            return {
                config: cloned,
                totalCount,
                shownCount: totalCount,
                truncated: false
            };
        }
        const countDatasetIndex = datasets.findIndex((ds) => {
            const label = String(ds?.label || "").toLowerCase();
            return label.includes("колич") || ds?.type === "bar";
        });
        const idx = countDatasetIndex >= 0 ? countDatasetIndex : 0;
        const countData = Array.isArray(datasets[idx]?.data) ? datasets[idx].data : [];
        const sortedIndexes = labels
            .map((_, index) => index)
            .sort((a, b) => toNumberSafe(countData[b]) - toNumberSafe(countData[a]));
        const topIndexes = sortedIndexes.slice(0, limit);

        cloned.data.labels = topIndexes.map((index) => labels[index]);
        cloned.data.datasets = datasets.map((dataset) => {
            const nextDataset = {...dataset};
            const rawData = Array.isArray(dataset?.data) ? dataset.data : [];
            nextDataset.data = topIndexes.map((index) => rawData[index]);
            return nextDataset;
        });

        return {
            config: cloned,
            totalCount,
            shownCount: topIndexes.length,
            truncated: true
        };
    }

    function upsertEventKpiMiniTopHint(text) {
        const panel = findChartPanel("chart-event-kpi");
        if (!panel) {
            return;
        }
        let hint = panel.querySelector("[data-event-kpi-mini-top-hint]");
        if (!text) {
            if (hint) {
                hint.remove();
            }
            return;
        }
        if (!hint) {
            hint = document.createElement("div");
            hint.className = "small text-muted mt-2";
            hint.setAttribute("data-event-kpi-mini-top-hint", "1");
            panel.appendChild(hint);
        }
        hint.textContent = text;
    }

    function refreshEventKpiMiniTopHint() {
        upsertEventKpiMiniTopHint("");
    }

    function readEventKpiMiniHintText() {
        const panel = findChartPanel("chart-event-kpi");
        const hint = panel?.querySelector("[data-event-kpi-mini-top-hint]");
        return (hint?.textContent || "").trim();
    }

    function truncateLabel(value, maxLen = 18) {
        const text = String(value || "");
        if (text.length <= maxLen) {
            return text;
        }
        return `${text.slice(0, maxLen - 1)}…`;
    }

    function splitLabelToTwoLines(value, maxLineLen = 14) {
        const text = String(value || "").trim();
        if (!text) {
            return "";
        }
        if (text.length <= maxLineLen) {
            return text;
        }
        const words = text.split(/\s+/).filter(Boolean);
        if (words.length <= 1) {
            const first = text.slice(0, maxLineLen);
            const secondRaw = text.slice(maxLineLen, maxLineLen * 2);
            const second = secondRaw.length < (text.length - maxLineLen) ? `${secondRaw.slice(0, Math.max(0, secondRaw.length - 1))}…` : secondRaw;
            return [first, second];
        }
        let firstLine = "";
        let secondLine = "";
        for (const word of words) {
            if (!firstLine) {
                firstLine = word;
                continue;
            }
            const nextFirst = `${firstLine} ${word}`;
            if (nextFirst.length <= maxLineLen) {
                firstLine = nextFirst;
                continue;
            }
            secondLine = secondLine ? `${secondLine} ${word}` : word;
        }
        if (!secondLine) {
            const first = text.slice(0, maxLineLen);
            const secondRaw = text.slice(maxLineLen, maxLineLen * 2);
            const second = secondRaw.length < (text.length - maxLineLen) ? `${secondRaw.slice(0, Math.max(0, secondRaw.length - 1))}…` : secondRaw;
            return [first, second];
        }
        if (secondLine.length > maxLineLen) {
            secondLine = `${secondLine.slice(0, Math.max(0, maxLineLen - 1))}…`;
        }
        return [firstLine, secondLine];
    }

    function eventKpiOptions() {
        const baseOptions = baseChartOptions();
        return {
            ...baseOptions,
            layout: {
                padding: {
                    left: 4,
                    right: 10,
                    top: 0,
                    bottom: 0
                }
            },
            scales: {
                x: {
                    grid: { color: "rgba(148,163,184,0.12)" },
                    offset: true,
                    ticks: {
                        color: "#64748b",
                        autoSkip: false,
                        maxRotation: 45,
                        minRotation: 45,
                        padding: 2,
                        callback(value) {
                            return this.getLabelForValue(value);
                        }
                    }
                },
                y: {
                    position: "left",
                    grid: { color: "rgba(148,163,184,0.14)" },
                    ticks: { color: "#64748b" }
                },
                y1: {
                    position: "right",
                    grid: { drawOnChartArea: false },
                    ticks: { color: "#0f766e" }
                },
                y2: {
                    position: "right",
                    offset: true,
                    grid: { drawOnChartArea: false },
                    ticks: { color: "#b91c1c" }
                }
            },
            plugins: {
                ...baseOptions.plugins,
                tooltip: {
                    mode: "index",
                    intersect: false,
                    filter: (tooltipItem) => {
                        const datasetValue = tooltipItem?.dataset?.data?.[tooltipItem?.dataIndex];
                        const candidates = [tooltipItem?.parsed?.y, tooltipItem?.raw, datasetValue, tooltipItem?.formattedValue];
                        const numeric = candidates
                            .map((value) => {
                                if (value == null) {
                                    return null;
                                }
                                const rawText = String(value);
                                const match = rawText.match(/-?\d+(?:[.,]\d+)?/);
                                const normalized = (match ? match[0] : rawText).replace(/\s+/g, "").replace(",", ".");
                                const parsed = Number(normalized);
                                return Number.isFinite(parsed) ? parsed : null;
                            })
                            .find((value) => Number.isFinite(value));
                        return Number.isFinite(numeric) && Math.abs(numeric) > 0.0001;
                    },
                    callbacks: {
                        title: eventKpiTooltipTitle,
                        label: (tooltipItem) => {
                            const datasetValue = tooltipItem?.dataset?.data?.[tooltipItem?.dataIndex];
                            const candidates = [tooltipItem?.parsed?.y, tooltipItem?.raw, datasetValue, tooltipItem?.formattedValue];
                            const numeric = candidates
                                .map((value) => {
                                    if (value == null) {
                                        return null;
                                    }
                                    const rawText = String(value);
                                    const match = rawText.match(/-?\d+(?:[.,]\d+)?/);
                                    const normalized = (match ? match[0] : rawText).replace(/\s+/g, "").replace(",", ".");
                                    const parsed = Number(normalized);
                                    return Number.isFinite(parsed) ? parsed : null;
                                })
                                .find((value) => Number.isFinite(value));
                            if (!Number.isFinite(numeric) || Math.abs(numeric) <= 0.0001) {
                                return null;
                            }
                            const label = tooltipItem?.dataset?.label || "";
                            const valueText = tooltipItem?.formattedValue ?? String(numeric);
                            return label ? `${label}: ${valueText}` : valueText;
                        }
                    }
                }
            }
        };
    }

    function eventKpiTooltipTitle(items) {
        const first = items?.[0];
        return first?.label || "";
    }

    function debounce(callback, delayMs) {
        let timerId = null;
        return function debounced(...args) {
            if (timerId != null) {
                clearTimeout(timerId);
            }
            timerId = setTimeout(() => {
                timerId = null;
                callback(...args);
            }, delayMs);
        };
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }
})();

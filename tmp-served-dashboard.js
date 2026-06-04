(function () {
    const inferredBase = window.location.pathname.startsWith("/analytics-admin")
        ? "/analytics-admin/api"
        : "/analytics/api";
    const API_BASE = (window.analyticsApiBase || inferredBase).replace(/\/+$/, "");
    const ANALYTICS_DASHBOARD_DEBUG_VERSION = "kpi-compare-debug-2026-06-01-01";
    const SHOW_KPI_DEBUG_BADGE = false;
    const DEBUG_KPI_EXPANDED_X_ZOOM = false;
    const state = {
        charts: {},
        chartConfigs: {},
        kpiFullChartConfigs: {},
        kpiMiniTopStatsByCanvas: {},
        kpiRuntimeMetaBySource: {},
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
        globalAttrMetaByCode: {},
        globalMetricMetaByCode: {},
        globalMetricRefreshRequestId: 0,
        globalMetricScopeSignature: "",
        chartScenarioBySource: {},
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
        stageMetricsRequestId: 0,
        stageMetricsAbortController: null,
        stageMetricSeriesCache: new Map(),
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
        mainFiltersSubmitting: false,
        mainFiltersSubmitPending: false,
        universalZoomBaseByCanvas: {},
        universalAllTime: false,
        allTimeRange: null,
        expandedEventOptionsBySource: {},
        eventsPage: 0,
        eventsSize: 15,
        eventsHasMore: false
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
    const UNIVERSAL_COMPARE_CHART_IDS = new Set([
        "chart-universal-timeline",
        "chart-universal-stages",
        "chart-universal-event-kpi"
    ]);
    const UNIVERSAL_COMPARE_FOLLOWS_GLOBAL = true;
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

    const refs = {};

    document.addEventListener("DOMContentLoaded", () => {
        ensureGlobalLoader();
        initRefs();
        exposeAnalyticsDebug();
        bindEvents();
        initDashboardViewMode();
        initDefaultCompareRange();
        void initDashboard();
    });

    function exposeAnalyticsDebug() {
        if (typeof window === "undefined") {
            return;
        }
        window.__analyticsDebug = {
            version: ANALYTICS_DASHBOARD_DEBUG_VERSION,
            state,
            isChartCompareEnabled,
            resolveCompareSourceCanvasId,
            resolveKpiCompareSourceCanvasId
        };
    }

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
        refs.globalCompareEnabled = document.getElementById("analytics-global-compare-enabled");
        refs.globalComparePreset = document.getElementById("analytics-global-compare-preset");
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
        refs.stageMetricCompareEnabled = document.getElementById("stage-metric-compare-enabled");
        refs.stageMetricFromB = document.getElementById("stage-metric-from-b");
        refs.stageMetricToB = document.getElementById("stage-metric-to-b");
        refs.stageMetricCompareControlsWrap = document.getElementById("stage-metric-compare-controls-wrap");
        refs.stageMetricCompareCol = document.getElementById("analytics-stage-metric-compare-col");
        refs.stageMetricTextCompareCol = document.getElementById("analytics-stage-metric-text-compare-col");
        refs.stageMetricReset = document.getElementById("stage-metric-reset");
        refs.stageMetricTableBody = document.querySelector("#analytics-stage-metric-table tbody");
        refs.stageMetricTextTableBody = document.querySelector("#analytics-stage-metric-text-table tbody");
        refs.stageTextForm = document.getElementById("analytics-stage-text-form");
        refs.stageTextStageType = document.getElementById("stage-text-stage-type");
        refs.stageTextMetricType = document.getElementById("stage-text-metric-type");
        refs.stageTextQuickRange = document.getElementById("stage-text-quick-range");
        refs.stageTextCompareEnabled = document.getElementById("stage-text-compare-enabled");
        refs.stageTextFromA = document.getElementById("stage-text-from-a");
        refs.stageTextToA = document.getElementById("stage-text-to-a");
        refs.stageTextFromB = document.getElementById("stage-text-from-b");
        refs.stageTextToB = document.getElementById("stage-text-to-b");
        refs.stageTextCompareControlsWrap = document.getElementById("stage-text-compare-controls-wrap");
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
            setPanelLoading(refs.stageMetricPanel, true);
            try {
                await loadStageMetrics();
            } finally {
                setPanelLoading(refs.stageMetricPanel, false);
            }
        };
        const submitEventsFilters = async () => {
            state.eventsPage = 0;
            await loadEvents(true);
        };
        const submitUniversalFilters = async () => {
            setPanelLoading(refs.universalPanel, true);
            try {
                await loadUniversal();
            } finally {
                setPanelLoading(refs.universalPanel, false);
            }
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
        const debouncedEventsFilters = debounce(() => {
            void submitEventsFilters();
        }, 420);

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
            if (refs.quickRangePresetSelect) {
                refs.quickRangePresetSelect.value = "24h";
                await applyQuickRangePreset("24h");
                return;
            }
            if (refs.mainForm) {
                refs.mainForm.requestSubmit?.();
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
                    setGlobalCompareMode(radio.value || "off");
                    state.globalCompareBeforeCustom = false;
                    await applyGlobalCompareToAllCharts();
                });
            });
        refs.globalComparePreset?.addEventListener("change", async () => {
            state.globalComparePreset = (refs.globalComparePreset.value || "").trim();
            state.globalCompareBeforeCustom = false;
            if (state.globalCompareMode === "split") {
                if (refs.quickRangePresetSelect && state.globalComparePreset) {
                    refs.quickRangePresetSelect.value = state.globalComparePreset;
                }
                if (state.globalComparePreset) {
                    await applyQuickRangePreset(state.globalComparePreset);
                } else {
                    await applyGlobalCompareToAllCharts();
                }
            } else {
                syncGlobalCompareControlsVisibility();
            }
        });
        [refs.globalBeforeFrom, refs.globalBeforeTo].forEach((control) => {
            control?.addEventListener("change", async () => {
                if (state.globalCompareMode !== "split") {
                    return;
                }
                if (!isValidGlobalBeforeRange()) {
                    return;
                }
                state.globalCompareBeforeCustom = true;
                await applyGlobalBeforeRangeToAllCharts();
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

        refs.from?.addEventListener("change", initDefaultCompareRange);
        refs.to?.addEventListener("change", initDefaultCompareRange);
        refs.from?.addEventListener("input", initDefaultCompareRange);
        refs.to?.addEventListener("input", initDefaultCompareRange);
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
                void submitStageMetricFilters();
            });
        });
        [refs.stageMetricFromA, refs.stageMetricToA, refs.stageMetricFromB, refs.stageMetricToB].forEach((control) => {
            control?.addEventListener("change", () => {
                void submitStageMetricFilters();
            });
            control?.addEventListener("input", () => {
                debouncedStageMetricFilters();
            });
        });
        refs.stageMetricCompareEnabled?.addEventListener("change", async () => {
            updateStageMetricCompareUi();
            await submitStageMetricFilters();
        });
        refs.stageMetricQuickRange?.addEventListener("change", async () => {
            await applyStageMetricQuickRange();
            await submitStageMetricFilters();
        });
        refs.stageMetricReset?.addEventListener("click", async () => {
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
            setPanelLoading(refs.stageMetricPanel, true);
            void (async () => {
                try {
                    await loadStageMetricComparisonSeries();
                } finally {
                    setPanelLoading(refs.stageMetricPanel, false);
                }
            })();
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
            await loadStageMetricTextBlock();
        });
        refs.stageTextMetricType?.addEventListener("change", async () => {
            const selected = (refs.stageTextMetricType.value || "").trim();
            state.stageMetricTextSelectedCodes = selected ? [selected] : [];
            await loadStageMetricTextCharts();
        });
        refs.stageTextCompareEnabled?.addEventListener("change", async () => {
            updateStageMetricTextCompareUi();
            await loadStageMetricTextCharts();
        });
        refs.stageTextQuickRange?.addEventListener("change", async () => {
            await applyStageTextQuickRange();
            await loadStageMetricTextCharts();
        });
        [refs.stageTextFromA, refs.stageTextToA, refs.stageTextFromB, refs.stageTextToB].forEach((control) => {
            control?.addEventListener("change", async () => {
                await loadStageMetricTextCharts();
            });
            control?.addEventListener("input", debounce(() => {
                void loadStageMetricTextCharts();
            }, 300));
        });
        refs.stageTextReset?.addEventListener("click", async () => {
            syncStageTextQuickRangeFromMain();
            syncStageTextRangesFromMain(true);
            await loadStageMetricTextCharts();
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
            control?.addEventListener("change", debouncedEventsRangeFilters);
            control?.addEventListener("input", debouncedEventsRangeFilters);
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
                void submitUniversalFilters();
            });
        });
        const debouncedUniversalRangeFilters = debounce(() => {
            void submitUniversalFilters();
        }, 260);
        [refs.universalFrom, refs.universalTo, refs.universalBeforeFrom, refs.universalBeforeTo, refs.universalBucket].forEach((control) => {
            control?.addEventListener("change", () => {
                state.universalAllTime = false;
                if (control === refs.universalFrom || control === refs.universalTo) {
                    syncUniversalBeforeRangeFromAfter();
                }
                debouncedUniversalRangeFilters();
            });
            control?.addEventListener("input", () => {
                state.universalAllTime = false;
                if (control === refs.universalFrom || control === refs.universalTo) {
                    syncUniversalBeforeRangeFromAfter();
                }
                debouncedUniversalRangeFilters();
            });
        });
        refs.universalQuickPreset?.addEventListener("change", async () => {
            await applyUniversalQuickRangePreset(refs.universalQuickPreset?.value || "");
            void submitUniversalFilters();
        });
        refs.universalEventTypeToggle?.addEventListener("click", (event) => {
            event.preventDefault();
            const isOpen = !refs.universalEventTypePopup?.classList.contains("d-none");
            refs.universalEventTypePopup?.classList.toggle("d-none", isOpen);
            refs.universalEventTypeToggle?.setAttribute("aria-expanded", isOpen ? "false" : "true");
        });
        refs.universalEventOverall?.addEventListener("change", () => {
            if (refs.universalEventOverall?.checked && refs.universalEventTypeList) {
                Array.from(refs.universalEventTypeList.options || []).forEach((option) => {
                    option.selected = false;
                });
            }
            enforceUniversalEventMetricDependency("events");
            updateUniversalEventToggleLabel();
            void submitUniversalFilters();
        });
        refs.universalEventTypeList?.addEventListener("change", () => {
            const selectedCount = selectedUniversalEventCodes().size;
            if (refs.universalEventOverall) {
                refs.universalEventOverall.checked = selectedCount === 0;
            }
            enforceUniversalEventMetricDependency("events");
            updateUniversalEventToggleLabel();
            void submitUniversalFilters();
        });
        refs.universalScenario?.addEventListener("change", () => {
            applyUniversalScenarios();
            void submitUniversalFilters();
        });
        refs.universalSeriesMetricToggle?.addEventListener("click", (event) => {
            event.preventDefault();
            const isOpen = !refs.universalSeriesMetricPopup?.classList.contains("d-none");
            refs.universalSeriesMetricPopup?.classList.toggle("d-none", isOpen);
            refs.universalSeriesMetricToggle?.setAttribute("aria-expanded", isOpen ? "false" : "true");
        });
        refs.universalSeriesMetricList?.addEventListener("change", () => {
            enforceUniversalSeriesRules();
            syncUniversalStageMetricsFromSeries();
            enforceUniversalEventMetricDependency("metrics");
            updateUniversalMetricToggleLabel();
            void submitUniversalFilters();
        });
        refs.universalStageMetricToggle?.addEventListener("click", (event) => {
            event.preventDefault();
            const isOpen = !refs.universalStageMetricPopup?.classList.contains("d-none");
            refs.universalStageMetricPopup?.classList.toggle("d-none", isOpen);
            refs.universalStageMetricToggle?.setAttribute("aria-expanded", isOpen ? "false" : "true");
        });
        refs.universalStageMetricList?.addEventListener("change", () => {
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
            updateUniversalMetricToggleLabel();
            updateUniversalStageMetricToggleLabel();
        });
        refs.universalSeriesToggles?.forEach((control) => {
            control.addEventListener("change", () => {
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
        refs.universalAttrValue?.addEventListener("input", debounce(() => {
            void submitUniversalFilters();
        }, 300));
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
            void runPanelBackgroundRefresh(refs.universalPanel, () => loadUniversal(mainReloadRequestId), "Universal background refresh failed");
            void runPanelBackgroundRefresh(refs.stageMetricPanel, () => loadStageMetrics(), "Stage metrics background refresh failed");
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
        await applyStoredExpandedRangesToCharts(canvasId);
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
        const durationMs = parseQuickRangeMs(presetCode) || (60 * 60 * 1000);
        const anchorMs = Date.now();
        const afterFromMs = anchorMs - durationMs;
        const beforeToMs = afterFromMs;
        const beforeFromMs = beforeToMs - durationMs;
        return {
            beforeFrom: toDateTimeLocalString(new Date(beforeFromMs)),
            beforeTo: toDateTimeLocalString(new Date(beforeToMs)),
            afterFrom: toDateTimeLocalString(new Date(afterFromMs)),
            afterTo: toDateTimeLocalString(new Date(anchorMs))
        };
    }

    function resolveExpandedRangesForMode(canvasId, isCompareEnabled) {
        if (isCompareEnabled && state.globalCompareEnabled) {
            return resolveGlobalBeforeRange();
        }
        const stored = state.expandedRangesBySource[canvasId];
        if (!stored || !stored.afterFrom || !stored.afterTo) {
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
        if (state.globalCompareEnabled) {
            state.expandedRangesBySource[canvasId] = {...resolveGlobalBeforeRange()};
            return;
        }
        const stored = state.expandedRangesBySource[canvasId];
        if (!stored || !stored.afterFrom || !stored.afterTo) {
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
        const compareEnabled = !!state.inlineCompareEnabled[canvasId];
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

    async function loadUniversal(mainReloadRequestId) {
        const universalGlobalMode = resolveGlobalInlineCompareMode();
        const universalCompareEnabled = UNIVERSAL_COMPARE_FOLLOWS_GLOBAL
            ? universalGlobalMode !== "off"
            : !!refs.universalCompareEnabled?.checked;
        const universalGhostEnabled = UNIVERSAL_COMPARE_FOLLOWS_GLOBAL
            ? universalGlobalMode === "overlay"
            : !!refs.universalCompareGhost?.checked;
        const timelineCompareCanvasId = state.inlineCompareCanvasBySource["chart-universal-timeline"] || "chart-universal-timeline-compare-inline";
        const stagesCompareCanvasId = state.inlineCompareCanvasBySource["chart-universal-stages"] || "chart-universal-stages-compare-inline";
        const eventKpiCompareCanvasId = state.inlineCompareCanvasBySource["chart-universal-event-kpi"] || "chart-universal-event-kpi-compare-inline";
        const params = universalParams(false);
        const universal = await fetchJson(`${api("/universal")}?${params.toString()}`);
        const universalEventsScope = await fetchJson(`${api("/universal")}?${universalParams(false, {includeEventFilter: false}).toString()}`);
        if (isStaleMainReloadRequest(mainReloadRequestId)) {
            return;
        }
        fillUniversalEventSelectorByPeriod(universalEventsScope);
        fillUniversalAttributeSelectorByPeriod(universal);
        const labels = (universal.series || []).map((point) => formatTime(point.time));
        const countSeries = (universal.series || []).map((point) => point.count || 0);
        const avgSeries = (universal.series || []).map((point) => point.avgMs || 0);
        const p95Series = (universal.series || []).map((point) => point.p95Ms || 0);
        const errSeries = (universal.series || []).map((point) => toPercentNumber(point.errorRate));

        const selectedMetrics = selectedUniversalMetrics();
        const showCount = selectedMetrics.has("count");
        const showAvg = selectedMetrics.has("avg");
        const showP95 = selectedMetrics.has("p95");
        const showError = selectedMetrics.has("error");
        const showStages = isUniversalSeriesEnabled("stages");
        const selectedEventCodes = Array.from(selectedUniversalEventCodes());
        const hasPerEventMode = !refs.universalEventOverall?.checked && selectedEventCodes.length > 1;

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
                    label: `${payload.eventTypeName || payload.eventTypeCode} В· ${metricLabel}`,
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

        let baseline = null;
        if (universalCompareEnabled) {
            baseline = await fetchJson(`${api("/universal")}?${universalParams(true).toString()}`);
        }
        if (isStaleMainReloadRequest(mainReloadRequestId)) {
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
                        label: `${payload.eventTypeName || payload.eventTypeCode} В· ${metricLabel} (До)`,
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
        upsertChart("chart-universal-timeline", {
            type: "line",
            data: {labels, datasets},
            options: baseChartOptions("Значение")
        });
        if (universalCompareEnabled && baseline) {
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
                        label: `${payload.eventTypeName || payload.eventTypeCode} В· ${metricLabel}`,
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

        const selectedStage = (refs.universalStageType?.value || "").trim();
        const rows = (universal.stages || []).filter((row) => !selectedStage || row.stageTypeCode === selectedStage);
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
                if (universalCompareEnabled && baseline) {
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
            if (universalCompareEnabled && baseline) {
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

        const breakdownRows = Array.isArray(universal.eventBreakdown) ? universal.eventBreakdown : [];
        const breakdownByCode = new Map(breakdownRows.map((row) => [String(row?.eventTypeCode || "").trim(), row]));
        const baselineBreakdownRows = Array.isArray(baseline?.eventBreakdown) ? baseline.eventBreakdown : [];
        const baselineBreakdownByCode = new Map(baselineBreakdownRows.map((row) => [String(row?.eventTypeCode || "").trim(), row]));
        const eventNameByCode = new Map(
            Array.from(refs.universalEventTypeList?.options || [])
                .map((option) => [String(option.value || "").trim(), String(option.textContent || option.value || "").trim()])
                .filter(([code]) => code.length > 0)
        );
        const isOverallMode = !!refs.universalEventOverall?.checked;
        const eventRows = (!isOverallMode && selectedEventCodes.length > 0)
            ? selectedEventCodes.map((code) => {
                const normalizedCode = String(code || "").trim();
                const row = breakdownByCode.get(normalizedCode) || baselineBreakdownByCode.get(normalizedCode) || {};
                return {
                    eventTypeCode: normalizedCode,
                    eventTypeName: row.eventTypeName || eventNameByCode.get(normalizedCode) || normalizedCode,
                    count: Number(row.count || 0),
                    p95Ms: Number(row.p95Ms || 0),
                    errorRate: Number(row.errorRate || 0)
                };
            })
            : breakdownRows.slice(0, 24);
        if (!eventRows.length) {
            destroyChart("chart-universal-event-kpi");
            return;
        }
        const kpiDatasets = [
            {
                type: "bar",
                label: "Количество",
                data: eventRows.map((row) => row.count || 0),
                backgroundColor: "rgba(109,40,217,0.75)",
                borderRadius: 8,
                yAxisID: "y"
            },
            {
                type: "line",
                label: "P95, ms",
                data: eventRows.map((row) => row.p95Ms || 0),
                borderColor: colors.teal,
                backgroundColor: "rgba(15,118,110,0.2)",
                tension: 0.25,
                yAxisID: "y1",
                pointRadius: 1.2
            },
            {
                type: "line",
                label: "Доля ошибок, %",
                data: eventRows.map((row) => toPercentNumber(row.errorRate)),
                borderColor: colors.red,
                backgroundColor: "rgba(185,28,28,0.2)",
                tension: 0.25,
                yAxisID: "y2",
                pointRadius: 1.2
            }
        ];
        if (baseline && universalGhostEnabled) {
            const baselineRowsByCode = new Map((baseline.eventBreakdown || []).map((row) => [String(row.eventTypeCode || "").trim(), row]));
            kpiDatasets.push({
                type: "bar",
                label: "Количество (До)",
                data: eventRows.map((row) => baselineRowsByCode.get(String(row.eventTypeCode || "").trim())?.count || 0),
                backgroundColor: "rgba(109,40,217,0.35)",
                borderColor: "rgba(109,40,217,0.75)",
                borderWidth: 1,
                borderRadius: 8,
                yAxisID: "y"
            });
            kpiDatasets.push({
                type: "line",
                label: "P95, ms (До)",
                data: eventRows.map((row) => baselineRowsByCode.get(String(row.eventTypeCode || "").trim())?.p95Ms || 0),
                borderColor: "rgba(15,118,110,0.45)",
                backgroundColor: "rgba(15,118,110,0.12)",
                borderDash: [6, 4],
                tension: 0.25,
                yAxisID: "y1",
                pointRadius: 1
            });
            kpiDatasets.push({
                type: "line",
                label: "Доля ошибок, % (До)",
                data: eventRows.map((row) => toPercentNumber(baselineRowsByCode.get(String(row.eventTypeCode || "").trim())?.errorRate)),
                borderColor: "rgba(185,28,28,0.45)",
                backgroundColor: "rgba(185,28,28,0.12)",
                borderDash: [6, 4],
                tension: 0.25,
                yAxisID: "y2",
                pointRadius: 1
            });
        }
        upsertChart("chart-universal-event-kpi", {
            data: {
                labels: eventRows.map((row) => row.eventTypeName || row.eventTypeCode),
                datasets: kpiDatasets
            },
            options: {
                ...baseChartOptions(),
                scales: {
                    x: { grid: { color: "rgba(148,163,184,0.12)" } },
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
                }
            }
        });
        if (universalCompareEnabled && baseline) {
            const baselineRows = (!isOverallMode && selectedEventCodes.length > 0)
                ? selectedEventCodes.map((code) => {
                    const normalizedCode = String(code || "").trim();
                    const row = baselineBreakdownByCode.get(normalizedCode) || breakdownByCode.get(normalizedCode) || {};
                    return {
                        eventTypeCode: normalizedCode,
                        eventTypeName: row.eventTypeName || eventNameByCode.get(normalizedCode) || normalizedCode,
                        count: Number(row.count || 0),
                        p95Ms: Number(row.p95Ms || 0),
                        errorRate: Number(row.errorRate || 0)
                    };
                })
                : baselineBreakdownRows.slice(0, 24);
            upsertChart(eventKpiCompareCanvasId, {
                data: {
                    labels: baselineRows.map((row) => row.eventTypeName || row.eventTypeCode),
                    datasets: [
                        {
                            type: "bar",
                            label: "Количество",
                            data: baselineRows.map((row) => row.count || 0),
                            backgroundColor: "rgba(109,40,217,0.75)",
                            borderRadius: 8,
                            yAxisID: "y"
                        },
                        {
                            type: "line",
                            label: "P95, ms",
                            data: baselineRows.map((row) => row.p95Ms || 0),
                            borderColor: colors.teal,
                            backgroundColor: "rgba(15,118,110,0.2)",
                            tension: 0.25,
                            yAxisID: "y1",
                            pointRadius: 1.2
                        },
                        {
                            type: "line",
                            label: "Доля ошибок, %",
                            data: baselineRows.map((row) => toPercentNumber(row.errorRate)),
                            borderColor: colors.red,
                            backgroundColor: "rgba(185,28,28,0.2)",
                            tension: 0.25,
                            yAxisID: "y2",
                            pointRadius: 1.2
                        }
                    ]
                },
                options: {
                    ...baseChartOptions(),
                    scales: {
                        x: { grid: { color: "rgba(148,163,184,0.12)" } },
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
                    }
                }
            });
        } else {
            destroyChart(eventKpiCompareCanvasId);
        }
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
        updateUniversalMetricToggleLabel();
        updateUniversalStageMetricToggleLabel();
        updateUniversalEventToggleLabel();
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
        const selected = Array.from(selectedUniversalMetrics());
        if (!selected.length) {
            refs.universalSeriesMetricToggle.textContent = "Метрики";
            return;
        }
        if (selected.length === 1) {
            const one = selected[0];
            refs.universalSeriesMetricToggle.textContent = one === "error"
                ? "Error rate"
                : one.toUpperCase();
            return;
        }
        refs.universalSeriesMetricToggle.textContent = `Метрики: ${selected.length}`;
    }

    function fillUniversalEventSelector(eventTypes) {
        if (!refs.universalEventTypeList) {
            return;
        }
        const previous = selectedUniversalEventCodes();
        const selectedTopEventType = (refs.eventType?.value || "").trim();
        const shouldSeedFromTop = previous.size === 0 && selectedTopEventType.length > 0;
        refs.universalEventTypeList.innerHTML = (eventTypes || []).map((item) => {
            const code = String(item?.code || "").trim();
            const checked = shouldSeedFromTop
                ? code === selectedTopEventType
                : previous.has(code);
            return `<option value="${escapeHtml(code)}" ${checked ? "selected" : ""}>${escapeHtml(item?.name || code)}</option>`;
        }).join("");
        if (shouldSeedFromTop && refs.universalEventOverall) {
            refs.universalEventOverall.checked = false;
        }
        updateUniversalEventToggleLabel();
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
        updateUniversalEventToggleLabel();
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
        return new Set(
            Array.from(refs.universalEventTypeList?.selectedOptions || [])
                .map((item) => String(item.value || "").trim())
                .filter((value) => value.length > 0)
        );
    }

    function updateUniversalEventToggleLabel() {
        if (!refs.universalEventTypeToggle) {
            return;
        }
        if (refs.universalEventOverall?.checked) {
            refs.universalEventTypeToggle.textContent = "События: все";
            return;
        }
        const selected = Array.from(selectedUniversalEventCodes());
        if (!selected.length) {
            refs.universalEventTypeToggle.textContent = "События";
            return;
        }
        refs.universalEventTypeToggle.textContent = `События: ${selected.length}`;
    }

    function enforceUniversalEventMetricDependency(source) {
        if (refs.universalEventOverall?.checked) {
            return;
        }
        const selectedEvents = Array.from(selectedUniversalEventCodes());
        const selectedMetrics = Array.from(selectedUniversalMetrics());
        const multipleEventsSelected = selectedEvents.length > 1;
        const multipleMetricsSelected = selectedMetrics.length > 1;

        if (source === "events" && multipleEventsSelected && multipleMetricsSelected) {
            const options = Array.from(refs.universalEventTypeList?.options || []);
            if (refs.universalEventOverall?.checked) {
                refs.universalEventOverall.checked = false;
                if (selectedEvents.length === 0 && options.length > 0) {
                    options[0].selected = true;
                }
            }
            const eventCodes = Array.from(selectedUniversalEventCodes());
            const keepEvent = eventCodes[0] || String(options[0]?.value || "").trim();
            options.forEach((option) => {
                const code = String(option.value || "").trim();
                option.selected = code === keepEvent;
            });
            updateUniversalEventToggleLabel();
            return;
        }
        if (source === "metrics" && multipleMetricsSelected && multipleEventsSelected) {
            setSelectedUniversalMetrics([selectedMetrics[0]]);
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
        const selectedEventCodes = selectedUniversalEventCodes();
        if (includeEventFilter && selectedEventCodes.size > 0 && !refs.universalEventOverall?.checked) {
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
        const beforeFromRaw = (refs.universalBeforeFrom?.value || "").trim();
        const beforeToRaw = (refs.universalBeforeTo?.value || "").trim();
        const beforeFromDate = beforeFromRaw ? new Date(beforeFromRaw) : null;
        const beforeToDate = beforeToRaw ? new Date(beforeToRaw) : null;
        const hasBeforeRange = beforeFromDate
            && beforeToDate
            && !Number.isNaN(beforeFromDate.getTime())
            && !Number.isNaN(beforeToDate.getTime())
            && beforeFromDate.getTime() < beforeToDate.getTime();
        if (hasBeforeRange) {
            return {
                beforeFrom: toDateTimeLocalString(beforeFromDate),
                beforeTo: toDateTimeLocalString(beforeToDate),
                afterFrom: toDateTimeLocalString(afterFromDate),
                afterTo: toDateTimeLocalString(afterToDate)
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
        const params = mainParams();
        const needInlineCompareEventsCount = !!state.inlineCompareEnabled["chart-events-count"];
        const needInlineCompareLatency = !!state.inlineCompareEnabled["chart-latency"];
        const needInlineCompareError = !!state.inlineCompareEnabled["chart-error-rate"];
        const needInlineCompareEventKpi = isChartCompareEnabled("chart-event-kpi");
        const requests = [fetchJson(`${api("/overview")}?${params.toString()}`)];
        const baselineRequests = new Map();
        const targetRequests = new Map();
        const compareKeysByCanvas = new Map();
        const ensureOverviewPairRequests = (canvasId) => {
            const ranges = resolveInlineCompareRequestRanges(canvasId);
            const rangeKey = serializeCompareRangeKey(ranges);
            const beforeKey = `overview-before:${rangeKey}`;
            if (!baselineRequests.has(beforeKey)) {
                baselineRequests.set(
                    beforeKey,
                    fetchJson(`${api("/overview")}?${buildScopedParamsByLocalRange(ranges.beforeFrom, ranges.beforeTo).toString()}`)
                );
            }
            const afterKey = `overview-after:${rangeKey}`;
            if (!targetRequests.has(afterKey)) {
                targetRequests.set(
                    afterKey,
                    fetchJson(`${api("/overview")}?${buildScopedParamsByLocalRange(ranges.afterFrom, ranges.afterTo).toString()}`)
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

        const baselineData = needInlineCompareEventKpi
            ? baselineDataByKey.get(eventKpiKeys?.beforeKey || "")
            : null;
        const baselineRows = buildEventKpiRows(baselineData?.eventBreakdown || []);
        const currentRows = buildEventKpiRows(eventKpiData.eventBreakdown || []);
        const compareEnabledResolved = isChartCompareEnabled("chart-event-kpi");
        if (compareEnabledResolved) {
            const overlayRows = buildEventKpiCompareOverlayRows(baselineRows, currentRows);
            state.kpiRuntimeMetaBySource["chart-event-kpi"] = {
                labelsBefore: baselineRows.length,
                labelsAfter: currentRows.length,
                labelsUnion: overlayRows.length
            };
            console.debug("[KPI_COMPARE_STATE]", {
                canvasId: "chart-event-kpi",
                isEventKpi: true,
                globalCompareEnabled: !!state.globalCompareEnabled,
                inlineCompareEnabled: !!state.inlineCompareEnabled["chart-event-kpi"],
                localCompareEnabled: !!state.localCompareEnabled?.["chart-event-kpi"],
                expandedCompareEnabled: state.expandedChart?.sourceCanvasId === "chart-event-kpi"
                    ? !!state.inlineCompareEnabled["chart-event-kpi"]
                    : false,
                compareEnabledResolved,
                hasBeforeData: !!baselineData,
                hasAfterData: !!eventKpiData,
                labelsCount: overlayRows.length,
                beforeLabelsCount: baselineRows.length,
                afterLabelsCount: currentRows.length,
                renderMode: "mini-compare-overlay"
            });
            upsertChart("chart-event-kpi", {
                data: {
                    labels: overlayRows.map((row) => row.label),
                    datasets: [
                        {
                            type: "bar",
                            label: "\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e",
                            data: overlayRows.map((row) => row.countAfter),
                            backgroundColor: "rgba(109,40,217,0.75)",
                            borderRadius: 8,
                            yAxisID: "y"
                        },
                        {
                            type: "bar",
                            label: "\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e (\u0414\u043e)",
                            data: overlayRows.map((row) => row.countBefore),
                            backgroundColor: "rgba(109,40,217,0.35)",
                            borderRadius: 8,
                            yAxisID: "y"
                        },
                        {
                            type: "line",
                            label: "P95",
                            data: overlayRows.map((row) => row.p95After),
                            borderColor: colors.teal,
                            backgroundColor: "rgba(15,118,110,0.2)",
                            tension: 0.25,
                            yAxisID: "y1"
                        },
                        {
                            type: "line",
                            label: "P95 (\u0414\u043e)",
                            data: overlayRows.map((row) => row.p95Before),
                            borderColor: "rgba(15,118,110,0.6)",
                            borderDash: [6, 4],
                            backgroundColor: "rgba(15,118,110,0.14)",
                            tension: 0.25,
                            yAxisID: "y1"
                        },
                        {
                            type: "line",
                            label: "\u041e\u0448\u0438\u0431\u043a\u0438",
                            data: overlayRows.map((row) => row.errAfter),
                            borderColor: colors.red,
                            backgroundColor: "rgba(185,28,28,0.2)",
                            tension: 0.25,
                            yAxisID: "y2"
                        },
                        {
                            type: "line",
                            label: "\u041e\u0448\u0438\u0431\u043a\u0438 (\u0414\u043e)",
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
            });
            const compareCanvasId = state.inlineCompareCanvasBySource["chart-event-kpi"] || "chart-event-kpi-compare-inline";
            upsertChart(compareCanvasId, {
                data: {
                    labels: baselineRows.map((row) => row.label),
                    datasets: [
                        {
                            type: "bar",
                            label: "\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e (\u0414\u043e)",
                            data: baselineRows.map((row) => row.count),
                            backgroundColor: "rgba(109,40,217,0.55)",
                            borderRadius: 8,
                            yAxisID: "y"
                        },
                        {
                            type: "line",
                            label: "P95 (\u0414\u043e), ms",
                            data: baselineRows.map((row) => row.p95),
                            borderColor: colors.teal,
                            backgroundColor: "rgba(15,118,110,0.16)",
                            tension: 0.25,
                            yAxisID: "y1"
                        },
                        {
                            type: "line",
                            label: "\u0414\u043e\u043b\u044f \u043e\u0448\u0438\u0431\u043e\u043a (\u0414\u043e), %",
                            data: baselineRows.map((row) => row.err),
                            borderColor: colors.red,
                            backgroundColor: "rgba(185,28,28,0.16)",
                            tension: 0.25,
                            yAxisID: "y2"
                        }
                    ]
                },
                options: eventKpiOptions()
            });
        } else {
            state.kpiRuntimeMetaBySource["chart-event-kpi"] = {
                labelsBefore: 0,
                labelsAfter: currentRows.length,
                labelsUnion: currentRows.length
            };
            console.debug("[KPI_COMPARE_STATE]", {
                canvasId: "chart-event-kpi",
                isEventKpi: true,
                globalCompareEnabled: !!state.globalCompareEnabled,
                inlineCompareEnabled: !!state.inlineCompareEnabled["chart-event-kpi"],
                localCompareEnabled: !!state.localCompareEnabled?.["chart-event-kpi"],
                expandedCompareEnabled: state.expandedChart?.sourceCanvasId === "chart-event-kpi"
                    ? !!state.inlineCompareEnabled["chart-event-kpi"]
                    : false,
                compareEnabledResolved,
                hasBeforeData: !!baselineData,
                hasAfterData: !!eventKpiData,
                labelsCount: currentRows.length,
                beforeLabelsCount: baselineRows.length,
                afterLabelsCount: currentRows.length,
                renderMode: "mini-single-full"
            });
            const eventLabels = currentRows.map((row) => row.label);
            const eventCounts = currentRows.map((row) => row.count);
            const eventP95 = currentRows.map((row) => row.p95);
            const eventErr = currentRows.map((row) => row.err);
            upsertChart("chart-event-kpi", {
                data: {
                    labels: eventLabels,
                    datasets: [
                        {
                            type: "bar",
                            label: "\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e",
                            data: eventCounts,
                            backgroundColor: "rgba(109,40,217,0.75)",
                            borderRadius: 8,
                            yAxisID: "y"
                        },
                        {
                            type: "line",
                            label: "P95, ms",
                            data: eventP95,
                            borderColor: colors.teal,
                            backgroundColor: "rgba(15,118,110,0.2)",
                            tension: 0.25,
                            yAxisID: "y1"
                        },
                        {
                            type: "line",
                            label: "\u0414\u043e\u043b\u044f \u043e\u0448\u0438\u0431\u043e\u043a, %",
                            data: eventErr,
                            borderColor: colors.red,
                            backgroundColor: "rgba(185,28,28,0.2)",
                            tension: 0.25,
                            yAxisID: "y2"
                        }
                    ]
                },
                options: eventKpiOptions()
            });
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
        const requestId = (state.stageMetricsRequestId || 0) + 1;
        state.stageMetricsRequestId = requestId;
        const previousController = state.stageMetricsAbortController;
        if (previousController) {
            previousController.abort();
        }
        const controller = new AbortController();
        state.stageMetricsAbortController = controller;
        const currentFilterKey = stageMetricFilterKey();
        if (state.stageMetricFilterKey !== currentFilterKey) {
            state.stageMetricFilterKey = currentFilterKey;
            clearStageMetricSeriesCache();
        }
        const params = stageMetricParams("primary");
        const stageType = refs.stageMetricStageType.value?.trim();
        if (stageType) {
            params.set("stageTypeCode", stageType);
        }

        let data;
        try {
            data = await fetchJson(`${api("/stage-metrics")}?${params.toString()}`, {signal: controller.signal});
        } catch (error) {
            if (isAbortError(error)) {
                return;
            }
            throw error;
        }
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        const summaries = data.summaries || [];
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

        await loadStageMetricComparisonSeries(requestId);
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        await loadStageMetricTextBlock(summaries, requestId);

    }

    async function loadStageMetricComparisonSeries(requestId) {
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
            await renderStageMetricRangeCharts({
                rangeMode: "primary",
                lineCanvasId: "chart-stage-metric-series",
                barCanvasId: null,
                selectedCodes,
                requestId
            });
            if (!isStageMetricCompareEnabled()) {
                destroyChart("chart-stage-metric-series-compare");
                destroyChart("chart-stage-metric-top-values-compare");
            } else {
                await renderStageMetricRangeCharts({
                    rangeMode: "compare",
                    lineCanvasId: "chart-stage-metric-series-compare",
                    barCanvasId: null,
                    selectedCodes,
                    requestId
                });
            }
            destroyChart("chart-stage-metric-top-values");
            destroyChart("chart-stage-metric-top-values-compare");
        }

        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
    }

    async function loadStageMetricTextBlock(preloadedSummaries, requestId) {
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        if (!refs.stageMetricTextTableBody) {
            return;
        }
        let summaries = Array.isArray(preloadedSummaries) ? preloadedSummaries : null;
        if (!summaries) {
            const params = stageTextParams("primary");
            const data = await fetchJson(`${api("/stage-metrics")}?${params.toString()}`);
            summaries = data.summaries || [];
        }
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
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
        if (isStaleStageMetricsRequest(requestId)) {
            return;
        }
        if (!refs.stageMetricTextTableBody) {
            destroyChart("chart-stage-metric-text");
            destroyChart("chart-stage-metric-text-compare");
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
            return;
        }
        await renderStageMetricTextChartByRange("primary", "chart-stage-metric-text", selectedCodes, requestId);
        if (!isStageMetricCompareEnabled()) {
            destroyChart("chart-stage-metric-text-compare");
            return;
        }
        await renderStageMetricTextChartByRange("compare", "chart-stage-metric-text-compare", selectedCodes, requestId);
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
            destroyChart(canvasId);
            return;
        }
        const payload = payloads[0];
        const topValues = Array.isArray(payload.topValues)
            ? payload.topValues.filter((item) => item && item.value != null && Number(item.count || 0) > 0)
            : [];
        if (!topValues.length) {
            destroyChart(canvasId);
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
        if (!seriesPayloads.length) {
            upsertChart(lineCanvasId, {type: "line", data: {labels: [], datasets: []}, options: baseChartOptions("Значение")});
            destroyChart(barCanvasId);
            return;
        }

        const allTimes = new Set();
        for (const payload of seriesPayloads) {
            for (const point of payload.series) {
                if (point?.time) {
                    allTimes.add(point.time);
                }
            }
        }
        const sortedTimes = Array.from(allTimes).sort();
        const labels = sortedTimes.map((time) => formatTime(time));
        const units = new Set(seriesPayloads.map((payload) => payload.unit).filter((unit) => unit));
        const unitKeys = new Set(seriesPayloads.map((payload) => normalizeMetricUnitKey(payload.unit)));
        const hasMixedUnits = unitKeys.size > 1;
        const yTitle = units.size === 1 ? localizeUnit(Array.from(units)[0]) : "Значение";
        const palette = ["#6d28d9", "#0f766e", "#b45309", "#b91c1c", "#1d4ed8", "#0f766e", "#be185d", "#475569"];

        const avgRawSeries = [];
        for (const payload of seriesPayloads) {
            const byTime = new Map(payload.series.map((point) => [point.time, point]));
            avgRawSeries.push(sortedTimes.map((time) => {
                const point = byTime.get(time);
                return point ? (point.avgMs ?? null) : null;
            }));
        }
        const chartAvgSeries = hasMixedUnits ? avgRawSeries.map((series) => normalizeSeriesToPercent(series)) : avgRawSeries;
        const sampledAvg = downsampleSeries(labels, chartAvgSeries, MAX_CHART_POINTS);
        const avgDatasets = seriesPayloads.map((payload, index) => {
            const color = palette[index % palette.length];
            const localizedUnit = localizeUnit(payload.unit);
            return {
                label: localizedUnit ? `${payload.metricName} (${localizedUnit})` : payload.metricName,
                data: sampledAvg.datasets[index] || [],
                borderColor: color,
                backgroundColor: "transparent",
                tension: 0.25,
                pointRadius: 1.4,
                spanGaps: true
            };
        });
        upsertChart(lineCanvasId, {
            type: "line",
            data: {labels: sampledAvg.labels, datasets: avgDatasets},
            options: baseChartOptions(hasMixedUnits ? "Нормализовано, % от max" : (yTitle || "Значение"))
        });
        if (!barCanvasId) {
            return;
        }

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
        const cacheKey = `${useTextParams ? "text" : "chart"}|${rangeMode || "primary"}|${metricCode || ""}|${params.toString()}`;
        const cached = readStageMetricSeriesCache(cacheKey);
        if (cached) {
            return cached;
        }
        const data = await fetchJson(`${api("/stage-metrics")}?${params.toString()}`);
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
        const params = stageMetricParams("primary");
        const stageType = refs.stageMetricStageType.value?.trim();
        if (stageType) {
            params.set("stageTypeCode", stageType);
        }
        params.delete("metricTypeCode");
        if (!isStageMetricCompareEnabled()) {
            return params.toString();
        }
        const paramsCompare = stageMetricParams("compare");
        if (stageType) {
            paramsCompare.set("stageTypeCode", stageType);
        }
        paramsCompare.delete("metricTypeCode");
        return `${params.toString()}|${paramsCompare.toString()}`;
    }

    function isStageMetricCompareEnabled() {
        return !!refs.stageMetricCompareEnabled?.checked;
    }

    function updateStageMetricCompareUi() {
        const enabled = isStageMetricCompareEnabled();
        refs.stageMetricForm?.classList.toggle("is-compare-enabled", enabled);
        refs.stageMetricForm?.classList.toggle("is-compare-disabled", !enabled);
        refs.stageMetricCompareControlsWrap?.classList.toggle("d-none", !enabled);
        refs.stageMetricCompareCol?.classList.toggle("d-none", !enabled);
        const topGrid = document.getElementById("analytics-stage-metric-charts");
        topGrid?.classList.toggle("is-single", !enabled);
        const textGrid = document.getElementById("analytics-stage-metric-text-charts");
        textGrid?.classList.toggle("is-single", !enabled);
        refs.stageMetricTextCompareCol?.classList.toggle("d-none", !enabled);
        const compareLabels = refs.stageMetricForm?.querySelectorAll("[data-stage-metric-compare-label]") || [];
        compareLabels.forEach((label) => {
            label.classList.toggle("d-none", !enabled);
        });
        updateStageMetricQuickRangeAvailability();
    }

    function isStageMetricTextCompareEnabled() {
        if (refs.stageTextCompareEnabled) {
            return !!refs.stageTextCompareEnabled.checked;
        }
        return isStageMetricCompareEnabled();
    }

    function updateStageMetricTextCompareUi() {
        const enabled = isStageMetricTextCompareEnabled();
        refs.stageTextCompareControlsWrap?.classList.toggle("d-none", !enabled);
        refs.stageMetricTextCompareCol?.classList.toggle("d-none", !enabled);
        const textGrid = document.getElementById("analytics-stage-metric-text-charts");
        textGrid?.classList.toggle("is-single", !enabled);
        refs.stageTextForm?.classList.toggle("is-compare-enabled", enabled);
        refs.stageTextForm?.classList.toggle("is-compare-disabled", !enabled);
    }

    function stageTextParams(rangeMode) {
        const params = new URLSearchParams();
        const mode = rangeMode === "compare" ? "compare" : "primary";
        const localFrom = mode === "compare"
            ? (refs.stageMetricFromB?.value || refs.stageTextFromB?.value)
            : (refs.stageMetricFromA?.value || refs.stageTextFromA?.value);
        const localTo = mode === "compare"
            ? (refs.stageMetricToB?.value || refs.stageTextToB?.value)
            : (refs.stageMetricToA?.value || refs.stageTextToA?.value);
        const fallbackFrom = mode === "compare" ? refs.compareBaselineFrom?.value : refs.from?.value;
        const fallbackTo = mode === "compare" ? refs.compareBaselineTo?.value : refs.to?.value;
        setIfPresent(params, "from", toIso((localFrom || fallbackFrom || "").trim()));
        setIfPresent(params, "to", toIso((localTo || fallbackTo || "").trim()));
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
        syncStageTextRangeField("fromA", refs.stageTextFromA, refs.from?.value || "", force);
        syncStageTextRangeField("toA", refs.stageTextToA, refs.to?.value || "", force);
        syncStageTextRangeField("fromB", refs.stageTextFromB, refs.compareBaselineFrom?.value || "", force);
        syncStageTextRangeField("toB", refs.stageTextToB, refs.compareBaselineTo?.value || "", force);
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
        refs.stageTextQuickRange.value = refs.quickRangePresetSelect.value || "24h";
    }

    async function applyStageTextQuickRange() {
        const preset = (refs.stageTextQuickRange?.value || "").trim();
        if (!preset) {
            return;
        }
        if (preset === "all") {
            await ensureAllTimeRangeLoaded();
            const allRange = getAllTimeLocalRange();
            refs.stageTextFromA.value = allRange.from;
            refs.stageTextToA.value = allRange.to;
            refs.stageTextFromB.value = allRange.from;
            refs.stageTextToB.value = allRange.to;
            return;
        }
        const now = Date.now();
        const durationMs = quickRangeCodeToDurationMs(preset);
        if (durationMs <= 0) {
            refs.stageTextFromA.value = "";
            refs.stageTextToA.value = "";
            refs.stageTextFromB.value = "";
            refs.stageTextToB.value = "";
            return;
        }
        const afterTo = new Date(now);
        const afterFrom = new Date(now - durationMs);
        refs.stageTextFromA.value = toDateTimeLocalString(afterFrom);
        refs.stageTextToA.value = toDateTimeLocalString(afterTo);
        refs.stageTextFromB.value = toDateTimeLocalString(new Date(afterFrom.getTime() - durationMs));
        refs.stageTextToB.value = toDateTimeLocalString(afterFrom);
    }

    function syncStageMetricQuickRangeFromMain() {
        const select = refs.stageMetricQuickRange;
        if (!select) {
            return;
        }
        const topFrom = (refs.from?.value || "").trim();
        const topTo = (refs.to?.value || "").trim();
        let nextValue = "";
        if (!topFrom && !topTo) {
            nextValue = "all";
        } else {
            const fromDate = topFrom ? new Date(topFrom) : null;
            const toDate = topTo ? new Date(topTo) : null;
            if (fromDate && toDate && !Number.isNaN(fromDate.getTime()) && !Number.isNaN(toDate.getTime())) {
                const durationMs = Math.max(0, toDate.getTime() - fromDate.getTime());
                nextValue = inferQuickRangeCode(durationMs);
            }
        }
        if (nextValue && Array.from(select.options).some((option) => option.value === nextValue)) {
            if (nextValue === "all" && isStageMetricCompareEnabled()) {
                select.value = "1h";
            } else {
                select.value = nextValue;
            }
        } else {
            // Custom top range: do not force preset buckets (like 24h), keep exact main dates.
            select.value = "";
            select.selectedIndex = -1;
        }
        updateStageMetricQuickRangeAvailability();
    }

    function inferQuickRangeCode(durationMs) {
        const options = [
            ["15m", 15 * 60_000],
            ["30m", 30 * 60_000],
            ["1h", 60 * 60_000],
            ["3h", 3 * 60 * 60_000],
            ["6h", 6 * 60 * 60_000],
            ["12h", 12 * 60 * 60_000],
            ["24h", 24 * 60 * 60_000],
            ["1w", 7 * 24 * 60 * 60_000],
            ["1mo", 30 * 24 * 60 * 60_000],
            ["3mo", 90 * 24 * 60 * 60_000],
            ["6mo", 180 * 24 * 60 * 60_000],
            ["1y", 365 * 24 * 60 * 60_000]
        ];
        const toleranceMs = 2 * 60_000;
        for (const [code, ms] of options) {
            if (Math.abs(durationMs - ms) <= toleranceMs) {
                return code;
            }
        }
        return "";
    }

    function syncStageMetricRangesFromMain(force) {
        const quickRange = refs.stageMetricQuickRange?.value?.trim() || "";
        const quickRangeMs = parseQuickRangeMs(quickRange);
        let targetAFrom = refs.from?.value || "";
        let targetATo = refs.to?.value || "";
        let targetBFrom = refs.compareBaselineFrom?.value || "";
        let targetBTo = refs.compareBaselineTo?.value || "";
        // When syncing from the global sidebar (force=true), always mirror the global period.
        // Local quick range must apply only on explicit local change in the stage-metrics block.
        if (!force && quickRangeMs > 0) {
            const anchorMs = Date.now();
            const windows = buildStageMetricQuickRangeWindows(anchorMs, quickRangeMs, isStageMetricCompareEnabled());
            targetAFrom = toDateTimeLocalString(new Date(windows.aFromMs));
            targetATo = toDateTimeLocalString(new Date(windows.aToMs));
            targetBFrom = toDateTimeLocalString(new Date(windows.bFromMs));
            targetBTo = toDateTimeLocalString(new Date(windows.bToMs));
        } else if (!targetBFrom || !targetBTo) {
            const primaryFrom = targetAFrom ? new Date(targetAFrom) : null;
            const primaryTo = targetATo ? new Date(targetATo) : null;
            if (primaryFrom && primaryTo && !Number.isNaN(primaryFrom.getTime()) && !Number.isNaN(primaryTo.getTime())) {
                const duration = Math.max(60_000, primaryTo.getTime() - primaryFrom.getTime());
                targetBTo = toDateTimeLocalString(new Date(primaryFrom.getTime()));
                targetBFrom = toDateTimeLocalString(new Date(primaryFrom.getTime() - duration));
            }
        }
        syncStageMetricRangeField(refs.stageMetricFromA, "fromA", targetAFrom, force);
        syncStageMetricRangeField(refs.stageMetricToA, "toA", targetATo, force);
        syncStageMetricRangeField(refs.stageMetricFromB, "fromB", targetBFrom, force);
        syncStageMetricRangeField(refs.stageMetricToB, "toB", targetBTo, force);
    }

    async function applyStageMetricQuickRange() {
        const quickRange = refs.stageMetricQuickRange?.value?.trim() || "";
        if (quickRange === "all") {
            await ensureAllTimeRangeLoaded();
            const allRange = getAllTimeLocalRange();
            refs.stageMetricFromA.value = allRange.from;
            refs.stageMetricToA.value = allRange.to;
            refs.stageMetricFromB.value = allRange.from;
            refs.stageMetricToB.value = allRange.to;
            return;
        }
        const durationMs = parseQuickRangeMs(quickRange);
        if (!durationMs) {
            return;
        }
        const anchorMs = Date.now();
        const windows = buildStageMetricQuickRangeWindows(anchorMs, durationMs, isStageMetricCompareEnabled());
        refs.stageMetricFromA.value = toDateTimeLocalString(new Date(windows.aFromMs));
        refs.stageMetricToA.value = toDateTimeLocalString(new Date(windows.aToMs));
        refs.stageMetricFromB.value = toDateTimeLocalString(new Date(windows.bFromMs));
        refs.stageMetricToB.value = toDateTimeLocalString(new Date(windows.bToMs));
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

        const safeBeforeFrom = String(beforeFromRaw || "").trim();
        const safeBeforeTo = String(beforeToRaw || "").trim();
        if (isAllTimeMirrorCompareRange(safeBeforeFrom, safeBeforeTo, safeAfter.afterFrom, safeAfter.afterTo)) {
            return {
                beforeFrom: safeBeforeFrom,
                beforeTo: safeBeforeTo,
                afterFrom: safeAfter.afterFrom,
                afterTo: safeAfter.afterTo
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
        if (state.globalCompareEnabled && !hasLocalOverride) {
            return resolveGlobalBeforeRange();
        }
        const stored = state.expandedRangesBySource[canvasId];
        if (stored && stored.afterFrom && stored.afterTo) {
            const normalizedStored = normalizeCompareRangesByAfter(stored.afterFrom, stored.afterTo, stored.beforeFrom, stored.beforeTo);
            state.expandedRangesBySource[canvasId] = {...normalizedStored};
            return normalizedStored;
        }
        if (state.globalCompareEnabled) {
            return resolveGlobalBeforeRange();
        }
        const topRanges = expandedRangesFromTopFilter(canvasId);
        return normalizeCompareRangesByAfter(topRanges.afterFrom, topRanges.afterTo, topRanges.beforeFrom, topRanges.beforeTo);
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
        const compareEnabled = isStageMetricCompareEnabled();
        allOption.disabled = compareEnabled;
        if (compareEnabled && select.value === "all") {
            select.value = "1h";
            void applyStageMetricQuickRange();
        }
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
                    ${renderNormalizedLogsTable(traceLogs, "По этому trace логи не найдены.", true)}
                </div>
            </div>
        `;

        const modal = bootstrap.Modal.getOrCreateInstance(refs.eventModalEl);
        modal.show();
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
        const localFrom = mode === "compare" ? refs.stageMetricFromB?.value : refs.stageMetricFromA?.value;
        const localTo = mode === "compare" ? refs.stageMetricToB?.value : refs.stageMetricToA?.value;
        const fallbackFrom = mode === "compare" ? refs.compareBaselineFrom?.value : refs.from?.value;
        const fallbackTo = mode === "compare" ? refs.compareBaselineTo?.value : refs.to?.value;
        setIfPresent(params, "from", toIso((localFrom || fallbackFrom || "").trim()));
        setIfPresent(params, "to", toIso((localTo || fallbackTo || "").trim()));
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
        if (refs.eventsQuickRange && refs.quickRangePresetSelect) {
            refs.eventsQuickRange.value = refs.quickRangePresetSelect.value || "";
        }
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
        if (refs.universalQuickPreset && refs.quickRangePresetSelect) {
            refs.universalQuickPreset.value = refs.quickRangePresetSelect.value || "24h";
        }
        state.universalAllTime = (refs.universalQuickPreset?.value || "").trim().toLowerCase() === "all";
        syncUniversalBeforeRangeFromAfter();
        updateUniversalCompareUi();
    }

    function updateUniversalCompareUi() {
        const mode = UNIVERSAL_COMPARE_FOLLOWS_GLOBAL
            ? resolveGlobalInlineCompareMode()
            : (!!refs.universalCompareEnabled?.checked ? "split" : "off");
        const splitMode = mode === "split";
        refs.universalBeforeWrap?.classList.toggle("d-none", !splitMode);
        refs.universalBeforeWrapTo?.classList.toggle("d-none", !splitMode);
        if (splitMode) {
            syncUniversalBeforeRangeFromAfter();
        }
    }

    function syncUniversalCompareFromGlobalFilter() {
        if (!UNIVERSAL_COMPARE_FOLLOWS_GLOBAL) {
            return;
        }
        const mode = resolveGlobalInlineCompareMode();
        const enabled = mode !== "off";
        const splitMode = mode === "split";
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
            refs.universalBeforeFrom.disabled = !splitMode;
        }
        if (refs.universalBeforeTo) {
            refs.universalBeforeTo.disabled = !splitMode;
        }
        if (splitMode) {
            const ranges = resolveGlobalBeforeRange();
            if (refs.universalBeforeFrom) refs.universalBeforeFrom.value = ranges.beforeFrom || "";
            if (refs.universalBeforeTo) refs.universalBeforeTo.value = ranges.beforeTo || "";
        }
        updateUniversalCompareUi();
        setUniversalCompareEnabled(splitMode);
    }

    function setUniversalCompareEnabled(enabled) {
        const active = !!enabled;
        UNIVERSAL_COMPARE_CHART_IDS.forEach((canvasId) => {
            const currentlyEnabled = !!state.inlineCompareEnabled[canvasId];
            if (active && !currentlyEnabled) {
                enableInlineCompareLayout(canvasId);
                state.inlineCompareEnabled[canvasId] = true;
                bindUniversalCompareScrollSync(canvasId);
                return;
            }
            if (!active && currentlyEnabled) {
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
        const match = normalized.match(/^(\d+)(m|h|d|w|mo|y)$/);
        if (!match) {
            return;
        }
        const count = Number(match[1]);
        const unitCode = match[2];
        if (!Number.isFinite(count) || count <= 0) {
            return;
        }
        const toDate = new Date();
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
        if (refs.universalFrom) refs.universalFrom.value = toDateTimeLocalString(fromDate);
        if (refs.universalTo) refs.universalTo.value = toDateTimeLocalString(toDate);
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
        const match = normalized.match(/^(\d+)(m|h|d|w|mo|y)$/);
        if (!match) {
            return;
        }
        const count = Number(match[1]);
        const unitCode = match[2];
        const toDate = new Date();
        const fromDate = new Date(toDate.getTime());
        if (!Number.isFinite(count) || count <= 0) {
            return;
        }
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
        refs.eventsFrom.value = toDateTimeLocalString(fromDate);
        refs.eventsTo.value = toDateTimeLocalString(toDate);
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
        resetInlineComparePresetsFromTopFilter();
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
        resetInlineComparePresetsFromTopFilter();
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
        const durationMs = parseQuickRangeMs(preset);
        if (!durationMs) {
            return;
        }
        const nowMs = Date.now();
        const targetToMs = nowMs;
        const targetFromMs = targetToMs - durationMs;
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
        const duration = Math.max(0, targetTo.getTime() - targetFrom.getTime());
        const code = inferQuickRangeCode(duration);
        if (code && Array.from(refs.compareQuickRange.options).some((opt) => opt.value === code)) {
            refs.compareQuickRange.value = code;
        }
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
        let configToRender = config;
        if (canvasId === "chart-event-kpi" || canvasId === "chart-event-kpi-compare-inline") {
            ensureKpiMiniWrap(canvas);
            const wrap = canvas.closest(".analytics-chart-wrap");
            if (wrap && !wrap.classList.contains("analytics-chart-wrap-expanded")) {
                const compareEnabledResolved = isChartCompareEnabled(canvasId);
                const isCompare = canvasId === "chart-event-kpi-compare-inline"
                    || !!wrap.closest(".analytics-chart-compare-pair[data-chart-id='chart-event-kpi']")
                    || (canvasId === "chart-event-kpi"
                        && compareEnabledResolved);
                state.kpiFullChartConfigs[canvasId] = cloneChartConfig(config);
                const labelsBefore = Array.isArray(config?.data?.labels) ? config.data.labels.length : 0;
                let topNApplied = false;
                const totalCount = Array.isArray(config?.data?.labels) ? config.data.labels.length : 0;
                state.kpiMiniTopStatsByCanvas[canvasId] = {
                    totalCount,
                    shownCount: totalCount,
                    truncated: false
                };
                refreshEventKpiMiniTopHint();
                const labels = Array.isArray(configToRender?.data?.labels) ? configToRender.data.labels : [];
                const mode = isCompare ? "mini-compare" : "mini-single";
                const sourceCanvasId = resolveCompareSourceCanvasId(canvasId) || "chart-event-kpi";
                const runtimeMeta = state.kpiRuntimeMetaBySource[sourceCanvasId] || {};
                const hintText = readEventKpiMiniHintText();
                const renderModeResolved = mode === "mini-compare" ? "mini-compare-overlay" : "mini-single-full";
                console.debug("[KPI_COMPARE_STATE]", {
                    canvasId,
                    sourceCanvasId,
                    isEventKpi: true,
                    globalCompareEnabled: !!state.globalCompareEnabled,
                    inlineCompareEnabled: !!state.inlineCompareEnabled?.["chart-event-kpi"],
                    localCompareEnabled: !!state.localCompareEnabled?.["chart-event-kpi"],
                    compareEnabledResolved,
                    renderMode: renderModeResolved,
                    labelsBefore,
                    labelsAfter: Number(runtimeMeta.labelsAfter || labelsBefore),
                    labelsCount: labels.length,
                    labelsRendered: labels.length,
                    topNApplied,
                    hintText,
                    jsVersion: ANALYTICS_DASHBOARD_DEBUG_VERSION
                });
                const badgeState = {
                    compareResolved: compareEnabledResolved,
                    globalCompare: !!state.globalCompareEnabled,
                    inlineCompare: !!state.inlineCompareEnabled?.["chart-event-kpi"],
                    localCompare: !!state.localCompareEnabled?.["chart-event-kpi"],
                    renderMode: renderModeResolved,
                    topNApplied,
                    labelsBefore: Number(runtimeMeta.labelsBefore || labelsBefore),
                    labelsAfter: Number(runtimeMeta.labelsAfter || labelsBefore),
                    labelsRendered: labels.length,
                    source: sourceCanvasId,
                    jsVersion: ANALYTICS_DASHBOARD_DEBUG_VERSION
                };
                if (SHOW_KPI_DEBUG_BADGE) {
                    renderKpiDebugBadge(badgeState);
                } else {
                    clearKpiDebugBadge();
                }
                if (compareEnabledResolved && (topNApplied || /\btop-10\b/i.test(hintText))) {
                    console.error("[KPI BUG] compare mode tried to show Top-10 hint", {
                        ...badgeState,
                        hintText
                    });
                }
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
        if (!needsScroll) {
            return safeViewportWidth;
        }
        const minColumnWidthByMode = {
            "mini-single": 35,
            "mini-compare": 21,
            "expanded-compare": 56,
            "expanded-single": 52
        };
        const safeScale = Math.max(1, Number(xScale) || 1);
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
        const response = await fetch(url, {
            headers: {
                "Accept": "application/json",
                "X-Requested-With": "XMLHttpRequest"
            },
            credentials: "same-origin",
            signal
        });
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
        return response.json();
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
        const value = resolveInlineCompareMode(canvasId);
        const miniSelect = refs.analyticsPage?.querySelector(`[data-inline-compare-mode='${canvasId}']`);
        if (miniSelect) {
            miniSelect.value = value;
        }
        const miniTrigger = refs.analyticsPage?.querySelector(`[data-inline-compare-mode-trigger='${canvasId}']`);
        if (miniTrigger) {
            miniTrigger.title = `Режим сравнения: ${compareModeLabel(value)}`;
            miniTrigger.setAttribute("aria-label", `Режим сравнения: ${compareModeLabel(value)}`);
            miniTrigger.innerHTML = `<i class="bi ${compareModeIcon(value)}"></i>`;
        }
        if (state.expandedChart.sourceCanvasId === canvasId && state.expandedChart.containerEl) {
            const expanded = state.expandedChart.containerEl.querySelector("[data-expanded-compare-mode]");
            if (expanded) {
                expanded.value = value;
            }
        }
    }

    function syncInlineCompareModeResetVisibility(canvasId) {
        void canvasId;
    }

    function initChartExpandUi() {
        if (!refs.analyticsPage) {
            return;
        }
        const canvases = refs.analyticsPage.querySelectorAll("canvas[id^='chart-']");
        canvases.forEach((canvas) => {
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
            await reloadInlineCompareChartSources();
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
            await reloadInlineCompareChartSources();
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
        const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || "";
        const expandComparePair = !!state.inlineCompareEnabled[canvasId] && !!compareCanvasId;
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
        if (isUniversalChart && parent) {
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
        if (!UNIVERSAL_COMPARE_CHART_IDS.has(canvasId)) {
            setupExpandedGraphControls(container, canvasId);
        }
        setupExpandedZoomControls(container);
        updateExpandButtonsState();
        renderExpandedChartClone(canvasId);
        if (button) {
            button.blur();
        }
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
        if (sourceCanvasId && state.charts[sourceCanvasId]) {
            state.charts[sourceCanvasId].update("none");
        }
        state.expandedChart.sourceCanvasId = "";
        state.expandedChart.containerEl = null;
        state.expandedChart.customRangeActive = false;
        updateExpandButtonsState();
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

    function selectedChartScenarioCodes(canvasId) {
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        const current = state.chartScenarioBySource[sourceCanvasId];
        return Array.isArray(current) ? current : [];
    }

    async function applyChartScenario(canvasId, scenarioId, checked) {
        const sourceCanvasId = resolveChartScenarioSourceCanvasId(canvasId);
        const code = String(scenarioId || "").trim().toLowerCase();
        if (!code) {
            return;
        }
        const selected = new Set(selectedChartScenarioCodes(sourceCanvasId));
        if (checked) {
            selected.add(code);
        } else {
            selected.delete(code);
        }
        state.chartScenarioBySource[sourceCanvasId] = Array.from(selected);
        try {
            if (checked && shouldScenarioPreferCompareOverlay(code) && INLINE_COMPARE_CHART_IDS.has(sourceCanvasId)) {
                await applyInlineCompareMode(sourceCanvasId, "overlay", {override: true});
                await applyInlineComparePresetToChart(sourceCanvasId);
                await applyStoredExpandedRangesToCharts(sourceCanvasId);
                if (state.expandedChart.sourceCanvasId === sourceCanvasId) {
                    renderExpandedChartClone(sourceCanvasId);
                }
            }
        } catch (error) {
            console.error("Chart scenario apply failed", {canvasId, scenarioId: code, error});
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
            .find((item) => item.id === scenarioId);
        if (!scenario) {
            return;
        }
        const target = document.getElementById(sourceCanvasId) || document.getElementById(canvasId);
        const chartTitle = target?.closest(".analytics-panel, .glass-card")
            ?.querySelector(".analytics-panel-title, .small.text-muted")
            ?.textContent
            ?.trim();
        refs.helpModalTitle.textContent = scenario.label || "Сценарий графика";
        refs.helpModalBody.innerHTML = `
            <div class="analytics-help-block mb-3">
                ${chartTitle ? `<div class="small text-muted mb-2">${escapeHtml(chartTitle)}</div>` : ""}
                <div class="analytics-help-block-title">${escapeHtml(scenario.label || "Сценарий")}</div>
                <div class="small mb-2">${escapeHtml(scenario.description || "")}</div>
                <div class="small text-muted">${escapeHtml(scenario.details || "Используйте сценарий как локальную гипотезу для чтения этого графика.")}</div>
            </div>
        `;
        const modal = bootstrap.Modal.getOrCreateInstance(refs.helpModalEl);
        modal.show();
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
                    ${options.map((item) => `
                        <div class="analytics-chart-scenario-item">
                            <div class="analytics-chart-scenario-item-head">
                                <label class="analytics-chart-scenario-label form-check mb-0">
                                    <input class="form-check-input"
                                           type="checkbox"
                                           data-chart-scenario-option="${canvasId}"
                                           value="${item.id}">
                                    <span class="form-check-label">${escapeHtml(item.label)}</span>
                                </label>
                                <button type="button"
                                        class="btn btn-outline-secondary analytics-chart-scenario-help-btn"
                                        data-chart-scenario-help="${canvasId}"
                                        data-chart-scenario-help-id="${item.id}"
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
                            await applyChartScenario(canvasId, input.value || "", false);
                            ensureChartScenarioPicker(actions, canvasId);
                            return;
                        }
                        await applyChartScenario(canvasId, input.value || "", true);
                        ensureChartScenarioPicker(actions, canvasId);
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
        actions.querySelectorAll(
            `[data-chart-scenario-picker='${canvasId}'] [data-chart-scenario-option='${canvasId}']`
        ).forEach((input) => {
            input.checked = selected.has(String(input.value || "").trim().toLowerCase());
        });
        const toggle = actions.querySelector(`[data-chart-scenario-toggle='${canvasId}']`);
        if (toggle) {
            const count = selected.size;
            toggle.classList.toggle("active", count > 0);
            toggle.title = count > 0 ? `Сценарии графика: выбрано ${count}` : "Сценарии графика";
            toggle.setAttribute("aria-label", toggle.title);
        }

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
                    item.addEventListener("click", async () => {
                        const mode = item.getAttribute("data-mode") || "off";
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
        let select = existing;
        if (!select) {
            select = document.createElement("select");
            select.className = "form-select form-select-sm analytics-inline-compare-preset";
            select.setAttribute("data-inline-compare-preset", canvasId);
            select.setAttribute("aria-label", "Период сравнения до/после");
            select.innerHTML = INLINE_COMPARE_PRESET_OPTIONS
                .map((item) => `<option value="${item.value}">${item.label}</option>`)
                .join("");
            select.addEventListener("change", async () => {
                setChartActionLoading(canvasId, true);
                const next = (select.value || "").trim();
                if (next) {
                    state.inlineComparePresetBySource[canvasId] = next;
                    state.inlineComparePresetOverriddenBySource[canvasId] = true;
                    state.expandedRangesBySource[canvasId] = expandedRangesFromPresetNow(next);
                }
                syncPresetSelectValues(canvasId);
                ensureInlineComparePresetControl(actions, canvasId);
                try {
                    state.expandedChart.customRangeActive = false;
                    await applyInlineComparePresetToChart(canvasId);
                    await applyStoredExpandedRangesToCharts(canvasId);
                    if (state.expandedChart.sourceCanvasId === canvasId) {
                        const ranges = state.expandedRangesBySource[canvasId];
                        const container = state.expandedChart.containerEl;
                        if (ranges && container) {
                            const bf = container.querySelector("[data-range='before-from']");
                            const bt = container.querySelector("[data-range='before-to']");
                            const af = container.querySelector("[data-range='after-from']");
                            const at = container.querySelector("[data-range='after-to']");
                            if (bf) bf.value = ranges.beforeFrom || "";
                            if (bt) bt.value = ranges.beforeTo || "";
                            if (af) af.value = ranges.afterFrom || "";
                            if (at) at.value = ranges.afterTo || "";
                        }
                        renderExpandedChartClone(canvasId);
                    }
                } catch (error) {
                    console.error("Inline compare preset reload failed", error);
                } finally {
                    setChartActionLoading(canvasId, false);
                }
            });
            actions.appendChild(select);
        }
        select.value = resolveInlineComparePreset(canvasId);
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
                state.inlineComparePresetOverriddenBySource[canvasId] = false;
                state.inlineComparePresetBySource[canvasId] = resolveTopInlineComparePresetOrDefault();
                state.expandedRangesBySource[canvasId] = expandedRangesFromTopFilter(canvasId);
                select.value = resolveInlineComparePreset(canvasId);
                syncPresetSelectValues(canvasId);
                ensureInlineComparePresetControl(actions, canvasId);
                try {
                    state.expandedChart.customRangeActive = false;
                    await applyInlineComparePresetToChart(canvasId);
                    await applyStoredExpandedRangesToCharts(canvasId);
                    if (state.expandedChart.sourceCanvasId === canvasId) {
                        renderExpandedChartClone(canvasId);
                    }
                } catch (error) {
                    console.error("Inline compare reset failed", error);
                } finally {
                    setChartActionLoading(canvasId, false);
                }
            });
            actions.appendChild(resetButton);
        }
        const topPreset = resolveTopInlineComparePresetOrDefault();
        const currentPreset = (select.value || "").trim();
        const isOverridden = !!state.inlineComparePresetOverriddenBySource[canvasId];
        const shouldShowReset = isOverridden && currentPreset !== topPreset;
        resetButton.classList.toggle("d-none", !shouldShowReset);

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
                expanded.value = value;
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
        const compareMode = resolveInlineCompareMode(canvasId);
        const isSplitMode = compareMode === "split";
        const isOverlayMode = compareMode === "overlay";
        const compareEnabled = isSplitMode || isOverlayMode;
        const bucketOverride = resolveExpandedBucket(canvasId);
        const chartOptions = getExpandedEventRenderOptions(canvasId);
        if (state.globalCompareEnabled && !state.inlineComparePresetOverriddenBySource[canvasId]) {
            const ranges = resolveGlobalBeforeRange();
            state.expandedRangesBySource[canvasId] = {...ranges};
            const afterLabel = compareEnabled ? "После" : "Период";
            const afterConfig = await buildChartConfigByRange(canvasId, ranges.afterFrom, ranges.afterTo, afterLabel, bucketOverride, chartOptions);
            upsertChart(canvasId, afterConfig);
            if (compareEnabled) {
                const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
                const beforeConfig = await buildChartConfigByRange(canvasId, ranges.beforeFrom, ranges.beforeTo, "До", bucketOverride, chartOptions);
                if (isSplitMode) {
                    upsertChart(compareCanvasId, beforeConfig);
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
        if (!state.inlineComparePresetOverriddenBySource[canvasId]) {
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
        const stored = state.expandedRangesBySource[canvasId];
        if (!stored || !stored.afterFrom || !stored.afterTo) {
            return;
        }
        const compareMode = resolveInlineCompareMode(canvasId);
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
        const globalPreset = (state.globalComparePreset || "").trim().toLowerCase();
        if (state.globalCompareEnabled
            && globalPreset
            && INLINE_COMPARE_PRESET_OPTIONS.some((item) => item.value === globalPreset)) {
            return globalPreset;
        }
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
        const beforeFromRaw = state.globalCompareBeforeCustom ? (refs.globalBeforeFrom?.value || "") : "";
        const beforeToRaw = state.globalCompareBeforeCustom ? (refs.globalBeforeTo?.value || "") : "";
        const normalized = normalizeCompareRangesByAfter(
            safeAfter.afterFrom,
            safeAfter.afterTo,
            beforeFromRaw,
            beforeToRaw
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
        const splitMode = mode === "split";
        setGlobalCompareMode(mode);
        syncUniversalCompareFromGlobalFilter();
        refs.globalBeforeRow?.classList.toggle("d-none", !splitMode);
        if (refs.globalComparePreset && !state.globalCompareBeforeCustom) {
            refs.globalComparePreset.value = (state.globalComparePreset || "").trim();
        }
        if (refs.globalComparePreset) {
            refs.globalComparePreset.disabled = !splitMode;
        }
        if (enabled) {
            const ranges = resolveGlobalBeforeRange();
            if (refs.globalBeforeFrom) refs.globalBeforeFrom.value = ranges.beforeFrom || "";
            if (refs.globalBeforeTo) refs.globalBeforeTo.value = ranges.beforeTo || "";
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
            if (state.inlineCompareModeOverriddenBySource?.[canvasId]) {
                return;
            }
            state.inlineCompareGhostBySource[canvasId] = ghostChecked;
            state.inlineCompareModeBySource[canvasId] = resolveGlobalInlineCompareMode();
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
                if (state.inlineCompareEnabled[canvasId]) {
                    await applyInlineComparePresetToChart(canvasId);
                }
            }));
            if (refs.universalCompareGhost) {
                await loadUniversal();
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
        loaderCanvasIds.forEach((canvasId) => setChartActionLoading(canvasId, true));
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            state.inlineComparePresetOverriddenBySource[canvasId] = true;
            state.expandedRangesBySource[canvasId] = {
                beforeFrom: ranges.beforeFrom,
                beforeTo: ranges.beforeTo,
                afterFrom: ranges.afterFrom,
                afterTo: ranges.afterTo
            };
        });
        try {
            await Promise.all(Array.from(INLINE_COMPARE_CHART_IDS).map(async (canvasId) => {
                if (state.inlineCompareEnabled[canvasId]) {
                    await applyStoredExpandedRangesToCharts(canvasId);
                }
            }));
            if (refs.universalCompareEnabled) {
                refs.universalCompareEnabled.checked = true;
                if (refs.universalBeforeFrom) refs.universalBeforeFrom.value = ranges.beforeFrom;
                if (refs.universalBeforeTo) refs.universalBeforeTo.value = ranges.beforeTo;
                await loadUniversal();
            }
        } finally {
            loaderCanvasIds.forEach((canvasId) => setChartActionLoading(canvasId, false));
        }
    }

    async function applyGlobalCompareToAllCharts() {
        const globalMode = resolveGlobalInlineCompareMode();
        setGlobalCompareMode(globalMode);
        syncGlobalCompareControlsVisibility();
        if (globalMode === "off") {
            state.globalCompareNoDataWarningKey = "";
            INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
                if (state.inlineCompareModeOverriddenBySource?.[canvasId]) {
                    return;
                }
                state.inlineCompareModeBySource[canvasId] = "off";
                state.inlineCompareGhostBySource[canvasId] = false;
            });
            await setInlineCompareEnabledForAll(false, {skipOverridden: true});
            if (refs.stageMetricCompareEnabled?.checked) {
                refs.stageMetricCompareEnabled.checked = false;
                toggleStageMetricCompare();
                await loadStageMetrics();
            }
            if (refs.stageTextCompareEnabled?.checked) {
                refs.stageTextCompareEnabled.checked = false;
                toggleStageTextCompare();
                await loadStageMetricText();
            }
            if (refs.universalCompareEnabled?.checked || UNIVERSAL_COMPARE_FOLLOWS_GLOBAL) {
                refs.universalCompareEnabled.checked = false;
                await loadUniversal();
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

        const globalPreset = globalMode === "split"
            ? (state.globalComparePreset || "").trim().toLowerCase()
            : "";
        const loaderCanvasIds = Array.from(INLINE_COMPARE_CHART_IDS);
        loaderCanvasIds.forEach((canvasId) => setChartActionLoading(canvasId, true));
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            state.inlineComparePresetOverriddenBySource[canvasId] = false;
            if (globalPreset) {
                state.inlineComparePresetBySource[canvasId] = globalPreset;
            }
            if (!state.inlineCompareModeOverriddenBySource?.[canvasId]) {
                state.inlineCompareGhostBySource[canvasId] = globalMode === "overlay";
                state.inlineCompareModeBySource[canvasId] = globalMode;
            }
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
                if (state.inlineCompareModeOverriddenBySource?.[canvasId]) {
                    continue;
                }
                const shouldSplit = resolveInlineCompareMode(canvasId) === "split";
                if (!!state.inlineCompareEnabled[canvasId] === shouldSplit) {
                    continue;
                }
                await toggleInlineCompareChart(canvasId, null);
            }
            if (state.globalCompareBeforeCustom) {
                await applyGlobalBeforeRangeToAllCharts();
            } else {
                // Rebuild overview/stages once for all compare charts to avoid staggered chart-by-chart updates.
                await reloadInlineCompareChartSources();
            }
            INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
                syncInlineCompareModeSelectValues(canvasId);
                syncInlineCompareModeResetVisibility(canvasId);
            });

            const secondaryTasks = [];

            if (refs.stageMetricCompareEnabled && !refs.stageMetricCompareEnabled.checked) {
                refs.stageMetricCompareEnabled.checked = true;
                toggleStageMetricCompare();
                secondaryTasks.push(loadStageMetrics());
            }
            if (refs.stageTextCompareEnabled && !refs.stageTextCompareEnabled.checked) {
                refs.stageTextCompareEnabled.checked = true;
                toggleStageTextCompare();
                secondaryTasks.push(loadStageMetricText());
            }
            if (refs.universalCompareEnabled) {
                refs.universalCompareEnabled.checked = true;
                if (refs.universalCompareGhost) {
                    refs.universalCompareGhost.checked = globalMode === "overlay";
                }
                if (state.globalCompareBeforeCustom && isValidGlobalBeforeRange()) {
                    if (refs.universalBeforeFrom) refs.universalBeforeFrom.value = refs.globalBeforeFrom?.value || "";
                    if (refs.universalBeforeTo) refs.universalBeforeTo.value = refs.globalBeforeTo?.value || "";
                }
                if (!state.globalCompareBeforeCustom) {
                    secondaryTasks.push(loadUniversal());
                }
            }

            if (secondaryTasks.length) {
                await Promise.all(secondaryTasks);
            }
        } finally {
            loaderCanvasIds.forEach((canvasId) => setChartActionLoading(canvasId, false));
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
        const isEventKpi = chart?.canvas?.id === "chart-event-kpi" || chart?.canvas?.id === "chart-event-kpi-compare-inline";
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

    function renderExpandedChartClone(canvasId) {
        if (!canvasId) {
            return;
        }
        if (state.expandedChart.customRangeActive) {
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
        const compareSourceCanvasId = state.inlineCompareCanvasBySource[canvasId] || "";
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
        const baseViewportHeight = 489;
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
                    if (DEBUG_KPI_EXPANDED_X_ZOOM) {
                        console.debug("[KPI_EXPANDED_X_ZOOM]", {
                            sourceCanvasId,
                            mode,
                            columnsCount: labels.length,
                            xScale,
                            viewportWidth,
                            targetWidth,
                            hostClass: host?.className || "",
                            scrollWidth: scrollBody?.scrollWidth || 0,
                            clientWidth: scrollBody?.clientWidth || 0
                        });
                    }
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
        const isCompareEnabled = !!state.inlineCompareEnabled[canvasId];
        const compareModeResolved = resolveInlineCompareMode(canvasId);
        const defaults = resolveExpandedRangesForMode(canvasId, isCompareEnabled);
        controls.innerHTML = `
            <div class="analytics-expanded-graph-row analytics-expanded-graph-row-top">
                <div class="analytics-expanded-toolbar-left" data-expanded-toolbar-left>
                    ${eventFilterHtml}
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
                    <select class="form-select form-select-sm analytics-inline-compare-preset" data-expanded-preset>
                        ${INLINE_COMPARE_PRESET_OPTIONS.map((item) => `<option value="${item.value}" ${item.value === preset ? "selected" : ""}>${item.label}</option>`).join("")}
                    </select>
                    <select class="form-select form-select-sm analytics-inline-bucket" data-expanded-bucket>
                        ${bucketOptionsHtml}
                    </select>
                    <select class="form-select form-select-sm analytics-inline-compare-mode" data-expanded-compare-mode>
                        ${INLINE_COMPARE_MODE_OPTIONS.map((item) => `<option value="${item.value}" ${item.value === compareModeResolved ? "selected" : ""}>${item.label}</option>`).join("")}
                    </select>
                    <button type="button" class="btn btn-outline-dark analytics-chart-icon-btn ${preset !== topPreset ? "" : "d-none"}" data-expanded-reset title="Сбросить к верхнему фильтру" aria-label="Сбросить к верхнему фильтру">
                        <i class="bi bi-arrow-counterclockwise"></i>
                    </button>
                </div>
            </div>
            <div class="analytics-expanded-graph-row analytics-expanded-graph-row-ranges ${isCompareEnabled ? "" : "d-none"}" data-expanded-ranges-row>
                <div class="analytics-expanded-range-group" data-before-group>
                    <span class="analytics-expanded-range-label">До</span>
                    <input type="datetime-local" class="form-control form-control-sm" data-range="before-from-compare" value="${escapeHtml(defaults.beforeFrom)}">
                    <input type="datetime-local" class="form-control form-control-sm" data-range="before-to-compare" value="${escapeHtml(defaults.beforeTo)}">
                </div>
                <div class="analytics-expanded-range-group" data-after-compare-group>
                    <span class="analytics-expanded-range-label">После</span>
                    <input type="datetime-local" class="form-control form-control-sm" data-range="after-from-compare" value="${escapeHtml(defaults.afterFrom)}">
                    <input type="datetime-local" class="form-control form-control-sm" data-range="after-to-compare" value="${escapeHtml(defaults.afterTo)}">
                </div>
            </div>
        `;
        container.insertBefore(controls, container.firstChild);

        const presetEl = controls.querySelector("[data-expanded-preset]");
        const bucketEl = controls.querySelector("[data-expanded-bucket]");
        const resetEl = controls.querySelector("[data-expanded-reset]");
        const compareModeEl = controls.querySelector("[data-expanded-compare-mode]");
        const actionsEl = controls.querySelector(".analytics-expanded-actions");
        ensureChartScenarioPicker(actionsEl, canvasId);
        const toolbarLeft = controls.querySelector("[data-expanded-toolbar-left]");
        const rangesRow = controls.querySelector("[data-expanded-ranges-row]");
        const beforeGroup = controls.querySelector("[data-before-group]");
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
        const isCompareMode = () => !!state.inlineCompareEnabled[canvasId];
        const readRangesFromUi = () => {
            if (isCompareMode()) {
                return {
                    beforeFrom: beforeFromCompareEl?.value || "",
                    beforeTo: beforeToCompareEl?.value || "",
                    afterFrom: afterFromCompareEl?.value || "",
                    afterTo: afterToCompareEl?.value || ""
                };
            }
            const afterFrom = afterFromEl?.value || "";
            const afterTo = afterToEl?.value || "";
            const afterFromDate = new Date(afterFrom || "");
            const afterToDate = new Date(afterTo || "");
            let beforeFrom = "";
            let beforeTo = "";
            if (!Number.isNaN(afterFromDate.getTime()) && !Number.isNaN(afterToDate.getTime()) && afterFromDate < afterToDate) {
                const durationMs = Math.max(60_000, afterToDate.getTime() - afterFromDate.getTime());
                beforeTo = afterFrom;
                beforeFrom = toDateTimeLocalString(new Date(afterFromDate.getTime() - durationMs));
            }
            return {
                beforeFrom,
                beforeTo,
                afterFrom,
                afterTo
            };
        };
        const writeRangesToUi = (ranges) => {
            if (beforeFromEl) beforeFromEl.value = ranges.beforeFrom || "";
            if (beforeToEl) beforeToEl.value = ranges.beforeTo || "";
            if (afterFromEl) afterFromEl.value = ranges.afterFrom || "";
            if (afterToEl) afterToEl.value = ranges.afterTo || "";
            if (beforeFromCompareEl) beforeFromCompareEl.value = ranges.beforeFrom || "";
            if (beforeToCompareEl) beforeToCompareEl.value = ranges.beforeTo || "";
            if (afterFromCompareEl) afterFromCompareEl.value = ranges.afterFrom || "";
            if (afterToCompareEl) afterToCompareEl.value = ranges.afterTo || "";
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
            const presetChanged = current !== top;
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
            resetEl?.classList.toggle("d-none", !(presetChanged || dateChanged || bucketChanged || eventChanged));
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
            const enabled = !!state.inlineCompareEnabled[canvasId];
            controls.classList.toggle("is-compare", enabled);
            rangesRow?.classList.toggle("d-none", !enabled);
            afterSingleGroup?.classList.toggle("d-none", enabled);
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
                await applyStoredExpandedRangesToCharts(canvasId);
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
                await applyStoredExpandedRangesToCharts(canvasId);
                state.expandedChart.customRangeActive = true;
            } finally {
                setChartActionLoading(canvasId, false);
            }
        });

        presetEl?.addEventListener("change", async () => {
            setChartActionLoading(canvasId, true);
            const next = (presetEl.value || "").trim();
            if (next) {
                state.inlineComparePresetBySource[canvasId] = next;
                state.inlineComparePresetOverriddenBySource[canvasId] = true;
            }
            try {
                state.expandedChart.customRangeActive = false;
                const fresh = expandedRangesFromPresetNow(next);
                writeRangesToUi(fresh);
                state.expandedRangesBySource[canvasId] = {...fresh};
                syncPresetSelectValues(canvasId);
                syncResetVisibility();
                await applyInlineComparePresetToChart(canvasId);
                await applyStoredExpandedRangesToCharts(canvasId);
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
                state.inlineCompareModeOverriddenBySource[canvasId] = false;
                state.inlineCompareModeBySource[canvasId] = resolveGlobalInlineCompareMode();
                const resetMode = resolveInlineCompareMode(canvasId);
                const shouldSplit = resetMode === "split";
                if (!!state.inlineCompareEnabled[canvasId] !== shouldSplit) {
                    if (shouldSplit) {
                        normalizeStoredRangeForCompare(canvasId);
                        resolveInlineComparePreset(canvasId);
                        enableInlineCompareLayout(canvasId);
                    } else {
                        disableInlineCompareLayout(canvasId);
                    }
                    state.inlineCompareEnabled[canvasId] = shouldSplit;
                }
                state.inlineComparePresetOverriddenBySource[canvasId] = false;
                state.inlineComparePresetBySource[canvasId] = resolveTopInlineComparePresetOrDefault();
                state.chartScenarioBySource[resolveChartScenarioSourceCanvasId(canvasId)] = [];
                ensureChartScenarioPicker(actionsEl, canvasId);
                delete state.expandedBucketBySource[canvasId];
                state.expandedChart.customRangeActive = false;
                if (presetEl) {
                    presetEl.value = resolveInlineComparePreset(canvasId);
                }
                if (bucketEl) {
                    bucketEl.value = (refs.bucket?.value || "").trim();
                }
                if (supportsExpandedEventFilter) {
                    eventFilterState.includeOverall = true;
                    eventFilterState.codes = [];
                    if (eventCodesEl) {
                        syncMultiSelectValues(eventCodesEl, ["__overall__"]);
                    }
                }
                if (latencyMetricEl) {
                    state.expandedLatencyMetricBySource[canvasId] = "p95";
                    latencyMetricEl.value = "p95";
                    syncLatencyMetricVisibility();
                }
                syncDateMode();
                syncInlineCompareModeSelectValues(canvasId);
                updateCompareButtonsState();
                const fresh = expandedRangesFromTopFilter(canvasId);
                writeRangesToUi(fresh);
                state.expandedRangesBySource[canvasId] = {...fresh};
                syncPresetSelectValues(canvasId);
                syncResetVisibility();
                await applyInlineComparePresetToChart(canvasId);
                await applyStoredExpandedRangesToCharts(canvasId);
                await refreshExpandedEventOptions();
                renderExpandedChartClone(canvasId);
                await rerenderExpandedEventFilterIfNeeded();
                syncResetVisibility();
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
                await applyInlineComparePresetToChart(canvasId);
                await applyStoredExpandedRangesToCharts(canvasId);
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
                if (state.inlineCompareEnabled[canvasId]) {
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
                    if (isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
                        const ghostDatasets = beforeConfig.data.datasets.map((item) => buildGhostDataset(item, canvasId));
                        afterConfig.data = afterConfig.data || {};
                        afterConfig.data.datasets = [...(afterConfig.data.datasets || []), ...ghostDatasets];
                    }
                    const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
                    upsertChart(compareCanvasId, beforeConfig);
                    upsertChart(canvasId, afterConfig);
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
                const compareMode = resolveInlineCompareMode(canvasId);
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
                upsertChart(canvasId, config);
                state.expandedRangesBySource[canvasId] = {...ranges};
                await refreshExpandedEventOptions();
                state.expandedChart.customRangeActive = true;
                syncResetVisibility();
            } finally {
                setChartActionLoading(canvasId, false);
            }
        };
        const scheduleApplyRanges = () => {
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
        const durationMs = parseQuickRangeMs(preset) || (60 * 60 * 1000);
        const topFrom = refs.from?.value ? new Date(refs.from.value) : null;
        const topTo = refs.to?.value ? new Date(refs.to.value) : null;
        const hasTopRange = topFrom && topTo && !Number.isNaN(topFrom.getTime()) && !Number.isNaN(topTo.getTime());
        if (hasTopRange) {
            const topDurationMs = Math.max(0, topTo.getTime() - topFrom.getTime());
            if (Math.abs(topDurationMs - durationMs) <= 120000) {
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
        const anchorMs = hasTopRange ? topTo.getTime() : Date.now();
        const afterFromMs = anchorMs - durationMs;
        const beforeToMs = afterFromMs;
        const beforeFromMs = beforeToMs - durationMs;
        return {
            beforeFrom: toDateTimeLocalString(new Date(beforeFromMs)),
            beforeTo: toDateTimeLocalString(new Date(beforeToMs)),
            afterFrom: toDateTimeLocalString(new Date(afterFromMs)),
            afterTo: toDateTimeLocalString(new Date(anchorMs))
        };
    }

    async function renderExpandedChartByRanges(canvasId, ranges, options = {}) {
        const container = state.expandedChart.containerEl;
        if (!container) {
            return;
        }
        const compareMode = resolveInlineCompareMode(canvasId);
        const isOverlayMode = compareMode === "overlay";
        const primaryCanvas = container.querySelector(`#chart-expanded-${canvasId}`);
        const compareCanvas = container.querySelector(`[data-expanded-compare-for='${canvasId}']`);
        if (!primaryCanvas) {
            return;
        }
        const afterConfig = await buildChartConfigByRange(canvasId, ranges.afterFrom, ranges.afterTo, compareCanvas ? "После" : "Период", undefined, options);
        if (!compareCanvas) {
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
            upsertChart(canvasId, afterConfig);
            return;
        }
        const beforeConfig = await buildChartConfigByRange(canvasId, ranges.beforeFrom, ranges.beforeTo, "До", undefined, options);
        if (isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
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
        const eventLabels = (data.eventBreakdown || []).map((row) => row.eventTypeName || row.eventTypeCode);
        const eventCounts = (data.eventBreakdown || []).map((row) => row.count || 0);
        const eventP95 = (data.eventBreakdown || []).map((row) => row.p95Ms || 0);
        const eventErr = (data.eventBreakdown || []).map((row) => toPercentNumber(row.errorRate));
        return {
            data: {
                labels: eventLabels,
                datasets: [
                    {type: "bar", label: withSuffix("Количество"), data: eventCounts, backgroundColor: "rgba(109,40,217,0.75)", borderRadius: 8, yAxisID: "y"},
                    {type: "line", label: withSuffix("P95") + ", ms", data: eventP95, borderColor: colors.teal, backgroundColor: "rgba(15,118,110,0.2)", tension: 0.25, yAxisID: "y1"},
                    {type: "line", label: withSuffix("Доля ошибок") + ", %", data: eventErr, borderColor: colors.red, backgroundColor: "rgba(185,28,28,0.2)", tension: 0.25, yAxisID: "y2"}
                ]
            },
            options: eventKpiOptions()
        };
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
            const text = HELP_TEXTS[targetId];
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

    function openHelpModal(targetId, targetEl) {
        if (!refs.helpModalEl || !refs.helpModalTitle || !refs.helpModalBody) {
            return;
        }
        const panel = targetEl.closest(".analytics-panel");
        const title = panel?.querySelector(".analytics-panel-title")?.textContent?.trim()
            || targetId
            || "Подсказка";
        const sub = panel?.querySelector(".analytics-panel-sub")?.textContent?.trim() || "";
        refs.helpModalTitle.textContent = title;
        refs.helpModalBody.innerHTML = buildChartHelpHtml(targetId, title, sub);
        const modal = bootstrap.Modal.getOrCreateInstance(refs.helpModalEl);
        modal.show();
    }

        function buildChartHelpHtml(targetId, title, sub) {
        const quick = HELP_TEXTS[targetId] || "";
        const summary = sub || "Выберите период и фильтры, затем сравните динамику по ключевым метрикам.";
        const tips = [
            "Сначала фиксируйте период и событие, потом сравнивайте latency/error rate.",
            "Для спорных участков переходите в Raw-события и проверяйте trace/path.",
            "Для проверки изменений используйте режим до/после на одном наборе фильтров."
        ];

        return `
            <div class="analytics-help-block mb-3">
                <div class="analytics-help-block-title">${escapeHtml(title || "Подсказка")}</div>
                ${summary ? `<div class="small text-muted mb-2">${escapeHtml(summary)}</div>` : ""}
                ${quick ? `<div class="small mb-2">${escapeHtml(quick)}</div>` : ""}
                <ul class="small mb-0 ps-3">
                    ${tips.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}
                </ul>
            </div>
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
        return entries.slice(0, 2).map(([key, value]) => `${key}: ${value}`).join(" В· ");
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
            .join(" В· ");
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

    function buildEventKpiRows(eventBreakdown, categories) {
        const rows = Array.isArray(eventBreakdown) ? eventBreakdown : [];
        const source = rows.map((row) => ({
            key: String(row?.eventTypeCode || row?.eventTypeName || "").trim(),
            label: String(row?.eventTypeName || row?.eventTypeCode || "-").trim() || "-",
            count: Number(row?.count || 0),
            p95: Number(row?.p95Ms || 0),
            err: toPercentNumber(row?.errorRate)
        })).filter((row) => row.key);
        const keys = Array.isArray(categories) && categories.length
            ? categories
            : source.map((row) => row.key);
        const byKey = new Map(source.map((row) => [row.key, row]));
        return keys.map((key) => byKey.get(key) || {key, label: key, count: 0, p95: 0, err: 0});
    }

    function buildEventKpiCompareOverlayRows(beforeRowsRaw, afterRowsRaw) {
        const beforeRows = Array.isArray(beforeRowsRaw) ? beforeRowsRaw : [];
        const afterRows = Array.isArray(afterRowsRaw) ? afterRowsRaw : [];
        const beforeByKey = new Map(beforeRows.map((row) => [row.key, row]));
        const afterByKey = new Map(afterRows.map((row) => [row.key, row]));
        const keys = new Set([...beforeByKey.keys(), ...afterByKey.keys()]);
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
        rows.sort((a, b) => {
            const left = Math.max(a.countBefore, a.countAfter);
            const right = Math.max(b.countBefore, b.countAfter);
            if (right !== left) {
                return right - left;
            }
            return a.label.localeCompare(b.label, "ru");
        });
        return rows;
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
        const sourceStats = state.kpiMiniTopStatsByCanvas["chart-event-kpi"];
        const total = Number(sourceStats?.totalCount || 0);
        if (total > 0) {
            upsertEventKpiMiniTopHint(
                "\u041f\u043e\u043a\u0430\u0437\u0430\u043d\u044b \u0432\u0441\u0435 \u0441\u043e\u0431\u044b\u0442\u0438\u044f. \u0414\u043b\u044f \u0443\u0434\u043e\u0431\u0441\u0442\u0432\u0430 \u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430 \u0433\u043e\u0440\u0438\u0437\u043e\u043d\u0442\u0430\u043b\u044c\u043d\u0430\u044f \u043f\u0440\u043e\u043a\u0440\u0443\u0442\u043a\u0430. \u041f\u043e\u043b\u043d\u0430\u044f \u0434\u0435\u0442\u0430\u043b\u0438\u0437\u0430\u0446\u0438\u044f \u2014 \u0432 \u0443\u0432\u0435\u043b\u0438\u0447\u0435\u043d\u043d\u043e\u043c \u0440\u0435\u0436\u0438\u043c\u0435."
            );
            return;
        }
        upsertEventKpiMiniTopHint("");
    }

    function readEventKpiMiniHintText() {
        const panel = findChartPanel("chart-event-kpi");
        const hint = panel?.querySelector("[data-event-kpi-mini-top-hint]");
        return (hint?.textContent || "").trim();
    }

    function renderKpiDebugBadge(debugState) {
        const panel = findChartPanel("chart-event-kpi");
        if (!panel) {
            return;
        }
        let badge = panel.querySelector("[data-event-kpi-debug-badge]");
        if (!badge) {
            badge = document.createElement("pre");
            badge.className = "small text-muted mt-2 mb-0";
            badge.setAttribute("data-event-kpi-debug-badge", "1");
            badge.style.whiteSpace = "pre-wrap";
            badge.style.fontFamily = "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace";
            badge.style.lineHeight = "1.25";
            panel.appendChild(badge);
        }
        const payload = debugState || {};
        const lines = [
            "DEBUG KPI:",
            `compareResolved = ${payload.compareResolved}`,
            `globalCompare = ${payload.globalCompare}`,
            `inlineCompare = ${payload.inlineCompare}`,
            `localCompare = ${payload.localCompare}`,
            `renderMode = ${payload.renderMode}`,
            `topNApplied = ${payload.topNApplied}`,
            `labelsBefore = ${payload.labelsBefore}`,
            `labelsAfter = ${payload.labelsAfter}`,
            `labelsRendered = ${payload.labelsRendered}`,
            `source = ${payload.source || "chart-event-kpi"}`,
            `jsVersion = ${payload.jsVersion || ANALYTICS_DASHBOARD_DEBUG_VERSION}`
        ];
        badge.textContent = lines.join("\n");
    }

    function clearKpiDebugBadge() {
        const panel = findChartPanel("chart-event-kpi");
        const badge = panel?.querySelector("[data-event-kpi-debug-badge]");
        if (badge) {
            badge.remove();
        }
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


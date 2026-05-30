(function () {
    const inferredBase = window.location.pathname.startsWith("/analytics-admin")
        ? "/analytics-admin/api"
        : "/analytics/api";
    const API_BASE = (window.analyticsApiBase || inferredBase).replace(/\/+$/, "");
    const state = {
        charts: {},
        chartConfigs: {},
        inlineCompareEnabled: {},
        inlineCompareCanvasBySource: {},
        inlineComparePresetBySource: {},
        inlineComparePresetOverriddenBySource: {},
        inlineCompareGhostBySource: {},
        globalCompareEnabled: false,
        globalComparePreset: "",
        globalCompareBeforeCustom: false,
        globalAttrMetaByCode: {},
        globalMetricMetaByCode: {},
        globalMetricRefreshRequestId: 0,
        globalMetricScopeSignature: "",
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
        "kpi-p99-ms": "99% запросов быстрее этого значения. Показывает редкие, но самые тяжёлые случаи.",
        "kpi-error-rate": "Доля событий с ошибкой. Смотрите вместе с количеством событий и HTTP-кодами.",
        "kpi-errors": "Абсолютное число событий с ошибкой за период.",
        "chart-events-count": "График показывает интенсивность трафика по времени. Пики указывают на нагрузки или маркетинговые всплески. Сравнивайте пики с ростом latency/error rate.",
        "chart-latency": "Линии AVG/P95/P99 отражают скорость обработки. Если P95/P99 растут быстрее AVG, есть деградация хвоста распределения.",
        "chart-error-rate": "Доля ошибок по времени. Ищите всплески и сверяйте их с конкретными событиями в Raw.",
        "chart-event-kpi": "Сравнение событий между собой: объём, P95 и error rate. Помогает выявить самые рискованные типы событий.",
        "chart-stage-latency": "Время по слоям (CONTROLLER/SERVICE/DATABASE). Показывает, где тратится основная часть времени.",
        "chart-stage-errors": "Ошибки по слоям. Если растёт DATABASE, чаще всего проблема в SQL/соединениях/таймаутах.",
        "chart-stage-metric-series": "Временная динамика метрик этапа. При разных единицах график нормализуется в проценты от максимума по каждой метрике.",
        "chart-stage-metric-top-values": "Для одной метрики показывает топ значений, для нескольких — сравнение P95. При разных единицах показывает отношение P95/AVG.",
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
        REQUEST_ID: "Сквозной идентификатор запроса для связи с backend-логами и межсервисной трассировкой."
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
        refs.analyticsTabRaw = document.getElementById("analytics-tab-raw");
        refs.analyticsTabButtons = document.querySelectorAll("[data-analytics-tab]");
        refs.analyticsTopTabButtons = document.querySelectorAll("[data-analytics-top-tab]");
        refs.analyticsOverviewSections = document.querySelectorAll("[data-analytics-view='overview']");
        refs.analyticsUniversalSections = document.querySelectorAll("[data-analytics-view='universal']");
        refs.analyticsRawSections = document.querySelectorAll("[data-analytics-view='raw']");
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
                if (tab === "overview" || tab === "raw") {
                    event.preventDefault();
                    setDashboardViewTab(tab, true);
                }
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

        refs.mainForm.addEventListener("submit", async (event) => {
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
            if (state.globalCompareEnabled) {
                await applyGlobalCompareToAllCharts();
            }
        });
        refs.globalCompareEnabled?.addEventListener("change", async () => {
            state.globalCompareEnabled = !!refs.globalCompareEnabled.checked;
            state.globalCompareBeforeCustom = false;
            await applyGlobalCompareToAllCharts();
        });
        refs.globalComparePreset?.addEventListener("change", async () => {
            state.globalComparePreset = (refs.globalComparePreset.value || "").trim();
            state.globalCompareBeforeCustom = false;
            if (state.globalCompareEnabled) {
                await applyGlobalCompareToAllCharts();
            } else {
                syncGlobalCompareControlsVisibility();
            }
        });
        refs.globalCompareGhost?.addEventListener("change", async () => {
            if (!state.globalCompareEnabled) {
                return;
            }
            await applyGlobalCompareGhostOnly();
        });
        [refs.globalBeforeFrom, refs.globalBeforeTo].forEach((control) => {
            control?.addEventListener("change", async () => {
                if (!state.globalCompareEnabled) {
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

        refs.stageMetricForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            await submitStageMetricFilters();
        });

        refs.eventsForm.addEventListener("submit", async (event) => {
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

        refs.eventsLoadMore.addEventListener("click", async () => {
            if (!state.eventsHasMore) {
                return;
            }
            state.eventsPage += 1;
            await loadEvents(false);
        });

        refs.eventsTableBody.addEventListener("click", async (event) => {
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

        refs.compareForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            await submitCompareFilters();
        });
        refs.compareQuickRange?.addEventListener("change", async () => {
            await applyCompareQuickRangePreset();
            await submitCompareFilters();
        });

        refs.from.addEventListener("change", initDefaultCompareRange);
        refs.to.addEventListener("change", initDefaultCompareRange);
        refs.from.addEventListener("input", initDefaultCompareRange);
        refs.to.addEventListener("input", initDefaultCompareRange);
        refs.from.addEventListener("change", () => syncStageMetricRangesFromMain(true));
        refs.to.addEventListener("change", () => syncStageMetricRangesFromMain(true));
        refs.from.addEventListener("input", () => syncStageMetricRangesFromMain(true));
        refs.to.addEventListener("input", () => syncStageMetricRangesFromMain(true));

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
                    setUniversalCompareEnabled(!!refs.universalCompareEnabled?.checked);
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
            setUniversalCompareEnabled(!!refs.universalCompareEnabled?.checked);
            syncGlobalCompareControlsVisibility();
            await loadDictionaries();
            await refreshEventTypeOptionsByScope();
            await reloadAll();
            await loadCompare();
            applyUniversalChartZoom("chart-universal-timeline", refs.universalTimelineZoomX?.value, refs.universalTimelineZoomY?.value);
            applyUniversalChartZoom("chart-universal-stages", refs.universalStagesZoomX?.value, refs.universalStagesZoomY?.value);
            applyUniversalChartZoom("chart-universal-event-kpi", refs.universalEventKpiZoomX?.value, refs.universalEventKpiZoomY?.value);
        } catch (error) {
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
        const normalized = tab === "raw" || tab === "universal" ? tab : "overview";

        refs.analyticsOverviewSections?.forEach((section) => {
            section.hidden = normalized !== "overview";
        });
        refs.analyticsUniversalSections?.forEach((section) => {
            section.hidden = normalized !== "universal";
        });
        refs.analyticsRawSections?.forEach((section) => {
            section.hidden = normalized !== "raw";
        });

        refs.analyticsTabOverview?.classList.toggle("active", normalized === "overview");
        refs.analyticsTabUniversal?.classList.toggle("active", normalized === "universal");
        refs.analyticsTabRaw?.classList.toggle("active", normalized === "raw");
        refs.analyticsTopTabButtons?.forEach((button) => {
            const tab = (button.getAttribute("data-analytics-top-tab") || "overview").trim();
            button.classList.toggle("active", tab === normalized);
        });

        if (!shouldPushState) {
            return;
        }
        const url = new URL(window.location.href);
        if (normalized === "raw") {
            url.searchParams.set("tab", "raw");
        } else if (normalized === "universal") {
            url.searchParams.set("tab", "universal");
        } else {
            url.searchParams.delete("tab");
        }
        window.history.replaceState({}, "", url.toString());
    }

    async function reloadAll() {
        const mainReloadRequestId = nextMainReloadRequestId();
        setGlobalScreenLoading(true);
        try {
            syncStageMetricRangesFromMain(true);
            syncStageTextQuickRangeFromMain();
            syncStageTextRangesFromMain(true);
            syncEventsRangeFromMain(true);
            // Critical path for perceived responsiveness: show main charts first.
            await Promise.all([
                loadOverview(mainReloadRequestId),
                loadStages(mainReloadRequestId)
            ]);
        } finally {
            setGlobalScreenLoading(false);
        }

        // Heavy secondary blocks are refreshed in background and must not block the screen loader.
        void runPanelBackgroundRefresh(refs.universalPanel, () => loadUniversal(mainReloadRequestId), "Universal background refresh failed");
        void runPanelBackgroundRefresh(refs.stageMetricPanel, () => loadStageMetrics(), "Stage metrics background refresh failed");
        void runPanelBackgroundRefresh(refs.eventsPanel, () => loadEvents(true), "Events background refresh failed");
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
        const ranges = expandedRangesFromTopFilter(canvasId);
        const beforeFrom = controls.querySelector("[data-range='before-from']");
        const beforeTo = controls.querySelector("[data-range='before-to']");
        const afterFrom = controls.querySelector("[data-range='after-from']");
        const afterTo = controls.querySelector("[data-range='after-to']");
        if (beforeFrom) beforeFrom.value = ranges.beforeFrom;
        if (beforeTo) beforeTo.value = ranges.beforeTo;
        if (afterFrom) afterFrom.value = ranges.afterFrom;
        if (afterTo) afterTo.value = ranges.afterTo;
        state.expandedRangesBySource[canvasId] = {...ranges};
        await applyInlineComparePresetToChart(canvasId);
        renderExpandedChartClone(canvasId);
    }

    function expandedRangesFromTopFilter(canvasId) {
        const topFromValue = (refs.from?.value || "").trim();
        const topToValue = (refs.to?.value || "").trim();
        const topFrom = topFromValue ? new Date(topFromValue) : null;
        const topTo = topToValue ? new Date(topToValue) : null;
        const hasTopRange = topFrom
            && topTo
            && !Number.isNaN(topFrom.getTime())
            && !Number.isNaN(topTo.getTime())
            && topFrom.getTime() < topTo.getTime();
        if (!hasTopRange) {
            return defaultExpandedCompareRanges(canvasId);
        }
        const durationMs = Math.max(60_000, topTo.getTime() - topFrom.getTime());
        return {
            beforeFrom: toDateTimeLocalString(new Date(topFrom.getTime() - durationMs)),
            beforeTo: toDateTimeLocalString(new Date(topFrom.getTime())),
            afterFrom: toDateTimeLocalString(new Date(topFrom.getTime())),
            afterTo: toDateTimeLocalString(new Date(topTo.getTime()))
        };
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
        const stored = state.expandedRangesBySource[canvasId];
        if (!stored || !stored.afterFrom || !stored.afterTo) {
            return;
        }
        const afterFromDate = new Date(stored.afterFrom);
        const afterToDate = new Date(stored.afterTo);
        if (Number.isNaN(afterFromDate.getTime()) || Number.isNaN(afterToDate.getTime()) || afterFromDate >= afterToDate) {
            return;
        }
        const durationMs = Math.max(60_000, afterToDate.getTime() - afterFromDate.getTime());
        const beforeToDate = afterFromDate;
        const beforeFromDate = new Date(beforeToDate.getTime() - durationMs);
        state.expandedRangesBySource[canvasId] = {
            beforeFrom: toDateTimeLocalString(beforeFromDate),
            beforeTo: toDateTimeLocalString(beforeToDate),
            afterFrom: stored.afterFrom,
            afterTo: stored.afterTo
        };
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
        const ranges = {
            beforeFrom: controls.querySelector("[data-range='before-from']")?.value || "",
            beforeTo: controls.querySelector("[data-range='before-to']")?.value || "",
            afterFrom: controls.querySelector("[data-range='after-from']")?.value || "",
            afterTo: controls.querySelector("[data-range='after-to']")?.value || ""
        };
        if (!ranges.afterFrom || !ranges.afterTo) {
            return;
        }
        state.expandedRangesBySource[canvasId] = {...ranges};
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
        const universalCompareEnabled = !!refs.universalCompareEnabled?.checked;
        const universalGhostEnabled = !!refs.universalCompareGhost?.checked;
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
        refs.universalStageMetricToggle.textContent = names ? `Слои: ${names}` : "Метрики слоёв";
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
        const needInlineCompareEventKpi = !!state.inlineCompareEnabled["chart-event-kpi"];
        const requests = [fetchJson(`${api("/overview")}?${params.toString()}`)];
        const baselineRequests = new Map();
        const targetRequests = new Map();
        const ensureOverviewPairRequests = (preset) => {
            const beforeKey = `overview-before:${preset}`;
            if (!baselineRequests.has(beforeKey)) {
                baselineRequests.set(beforeKey, fetchJson(`${api("/overview")}?${inlineCompareParams(preset).toString()}`));
            }
            const afterKey = `overview-after:${preset}`;
            if (!targetRequests.has(afterKey)) {
                targetRequests.set(afterKey, fetchJson(`${api("/overview")}?${inlineCompareAfterParams(preset).toString()}`));
            }
        };
        if (needInlineCompareEventsCount) {
            const preset = resolveInlineComparePreset("chart-events-count");
            ensureOverviewPairRequests(preset);
        }
        if (needInlineCompareLatency) {
            const preset = resolveInlineComparePreset("chart-latency");
            ensureOverviewPairRequests(preset);
        }
        if (needInlineCompareError) {
            const preset = resolveInlineComparePreset("chart-error-rate");
            ensureOverviewPairRequests(preset);
        }
        if (needInlineCompareEventKpi) {
            const preset = resolveInlineComparePreset("chart-event-kpi");
            ensureOverviewPairRequests(preset);
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

        const eventsCountPreset = resolveInlineComparePreset("chart-events-count");
        const latencyPreset = resolveInlineComparePreset("chart-latency");
        const errorPreset = resolveInlineComparePreset("chart-error-rate");
        const eventKpiPreset = resolveInlineComparePreset("chart-event-kpi");
        const eventsCountData = needInlineCompareEventsCount ? (targetDataByKey.get(`overview-after:${eventsCountPreset}`) || data) : data;
        const latencyData = needInlineCompareLatency ? (targetDataByKey.get(`overview-after:${latencyPreset}`) || data) : data;
        const errorData = needInlineCompareError ? (targetDataByKey.get(`overview-after:${errorPreset}`) || data) : data;
        const eventKpiData = needInlineCompareEventKpi ? (targetDataByKey.get(`overview-after:${eventKpiPreset}`) || data) : data;

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
                    label: "Событий",
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
            const preset = resolveInlineComparePreset("chart-events-count");
            const baselineData = baselineDataByKey.get(`overview-before:${preset}`);
            const baselineLabels = (baselineData?.series || []).map((point) => formatTime(point.time));
            const baselineCountSeries = (baselineData?.series || []).map((point) => point.count || 0);
            const sampledBaseline = downsampleSeries(baselineLabels, [baselineCountSeries], MAX_CHART_POINTS);
            const compareCanvasId = state.inlineCompareCanvasBySource["chart-events-count"] || "chart-events-count-compare-inline";
            upsertChart(compareCanvasId, {
                type: "line",
                data: {
                    labels: sampledBaseline.labels,
                    datasets: [{
                        label: "Событий (До)",
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
            const preset = resolveInlineComparePreset("chart-latency");
            const baselineData = baselineDataByKey.get(`overview-before:${preset}`);
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
            const preset = resolveInlineComparePreset("chart-error-rate");
            const baselineData = baselineDataByKey.get(`overview-before:${preset}`);
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

        const baselineEventBreakdown = needInlineCompareEventKpi
            ? (baselineDataByKey.get(`overview-before:${resolveInlineComparePreset("chart-event-kpi")}`)?.eventBreakdown || [])
            : [];
        const currentRows = buildEventKpiRows(eventKpiData.eventBreakdown || []);
        const baselineRowsForUnion = buildEventKpiRows(baselineEventBreakdown || []);
        const unifiedKeys = Array.from(new Set([...currentRows, ...baselineRowsForUnion].map((row) => row.key)));
        const unifiedCurrentRows = buildEventKpiRows(eventKpiData.eventBreakdown || [], unifiedKeys);
        const eventLabels = unifiedCurrentRows.map((row) => row.label);
        const eventCounts = unifiedCurrentRows.map((row) => row.count);
        const eventP95 = unifiedCurrentRows.map((row) => row.p95);
        const eventErr = unifiedCurrentRows.map((row) => row.err);
        upsertChart("chart-event-kpi", {
            data: {
                labels: eventLabels,
                datasets: [
                    {
                        type: "bar",
                        label: "Количество",
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
                        label: "Доля ошибок, %",
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
        if (needInlineCompareEventKpi) {
            const preset = resolveInlineComparePreset("chart-event-kpi");
            const baselineData = baselineDataByKey.get(`overview-before:${preset}`);
            const baselineRows = buildEventKpiRows(baselineData?.eventBreakdown || [], unifiedKeys);
            const baselineEventLabels = baselineRows.map((row) => row.label);
            const baselineEventCounts = baselineRows.map((row) => row.count);
            const baselineEventP95 = baselineRows.map((row) => row.p95);
            const baselineEventErr = baselineRows.map((row) => row.err);
            const compareCanvasId = state.inlineCompareCanvasBySource["chart-event-kpi"] || "chart-event-kpi-compare-inline";
            upsertChart(compareCanvasId, {
                data: {
                    labels: baselineEventLabels,
                    datasets: [
                        {
                            type: "bar",
                            label: "Количество (До)",
                            data: baselineEventCounts,
                            backgroundColor: "rgba(109,40,217,0.55)",
                            borderRadius: 8,
                            yAxisID: "y"
                        },
                        {
                            type: "line",
                            label: "P95 (До), ms",
                            data: baselineEventP95,
                            borderColor: colors.teal,
                            backgroundColor: "rgba(15,118,110,0.16)",
                            tension: 0.25,
                            yAxisID: "y1"
                        },
                        {
                            type: "line",
                            label: "Доля ошибок (До), %",
                            data: baselineEventErr,
                            borderColor: colors.red,
                            backgroundColor: "rgba(185,28,28,0.16)",
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
        const ensureStagesPairRequests = (preset) => {
            const beforeKey = `stages-before:${preset}`;
            if (!baselineRequests.has(beforeKey)) {
                baselineRequests.set(beforeKey, fetchJson(`${api("/stages")}?${inlineCompareParams(preset).toString()}`));
            }
            const afterKey = `stages-after:${preset}`;
            if (!targetRequests.has(afterKey)) {
                targetRequests.set(afterKey, fetchJson(`${api("/stages")}?${inlineCompareAfterParams(preset).toString()}`));
            }
        };
        if (needInlineCompareStageLatency) {
            const preset = resolveInlineComparePreset("chart-stage-latency");
            ensureStagesPairRequests(preset);
        }
        if (needInlineCompareStageErrors) {
            const preset = resolveInlineComparePreset("chart-stage-errors");
            ensureStagesPairRequests(preset);
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

        const stageLatencyPreset = resolveInlineComparePreset("chart-stage-latency");
        const stageErrorsPreset = resolveInlineComparePreset("chart-stage-errors");
        const stageLatencyData = needInlineCompareStageLatency ? (targetDataByKey.get(`stages-after:${stageLatencyPreset}`) || data) : data;
        const stageErrorsData = needInlineCompareStageErrors ? (targetDataByKey.get(`stages-after:${stageErrorsPreset}`) || data) : data;
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
            const preset = resolveInlineComparePreset("chart-stage-latency");
            const baselineData = baselineDataByKey.get(`stages-before:${preset}`);
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
            const preset = resolveInlineComparePreset("chart-stage-errors");
            const baselineData = baselineDataByKey.get(`stages-before:${preset}`);
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
        if (!isStageMetricTextCompareEnabled()) {
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
        updateStageMetricQuickRangeAvailability();
    }

    function isStageMetricTextCompareEnabled() {
        return !!refs.stageTextCompareEnabled?.checked;
    }

    function updateStageMetricTextCompareUi() {
        const enabled = isStageMetricTextCompareEnabled();
        refs.stageTextCompareControlsWrap?.classList.toggle("d-none", !enabled);
        refs.stageMetricTextCompareCol?.classList.toggle("d-none", !enabled);
        refs.stageTextForm?.classList.toggle("is-compare-enabled", enabled);
        refs.stageTextForm?.classList.toggle("is-compare-disabled", !enabled);
    }

    function stageTextParams(rangeMode) {
        const params = new URLSearchParams();
        const mode = rangeMode === "compare" ? "compare" : "primary";
        const localFrom = mode === "compare" ? refs.stageTextFromB?.value : refs.stageTextFromA?.value;
        const localTo = mode === "compare" ? refs.stageTextToB?.value : refs.stageTextToA?.value;
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
                    <button class="nav-link" data-bs-toggle="tab" data-bs-target="#${logsTabId}" type="button" role="tab">Лог (trace)</button>
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
                        <div class="fw-semibold mb-2">Сводка по слоям</div>
                        <div class="table-responsive mb-3">
                            <table class="table table-sm align-middle analytics-table mb-0">
                                <thead>
                                <tr>
                                    <th>Слой</th>
                                    <th class="text-end">Вызовов</th>
                                    <th class="text-end">Σ длительность</th>
                                    <th class="text-end">Max</th>
                                    <th class="text-end">Ошибки</th>
                                </tr>
                                </thead>
                                <tbody>${stageSummaryHtml || "<tr><td colspan='5' class='text-muted'>Нет данных</td></tr>"}</tbody>
                            </table>
                        </div>
                        <div class="fw-semibold mb-1">Трассировка вызовов</div>
                        <div class="small text-muted mb-2">
                            Ниже показана линейная последовательность этапов: старт фронтенда, ожидание сервера, backend-вызовы и фронтенд-рендер.
                        </div>
                        ${stagesHtml || "<div class='text-muted'>Этапы не найдены</div>"}
                    </section>
                </div>
                <div class="tab-pane fade" id="${logsTabId}" role="tabpanel">
                    <div class="small text-muted mb-2">
                        Нормализованный лог по Trace: <b>${escapeHtml(data.traceId || "-")}</b>. Можно читать как структурированную расшифровку без поиска по файлам.
                    </div>
                    ${renderNormalizedLogsTable(traceLogs, "По этому trace логов не найдено.", true)}
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
                        <div class="small text-muted">Повторы: ${formatInt(items.length)} · ${escapeHtml(group.operation || "—")}</div>
                        <div class="small text-muted">${formatDateTime(firstStage?.startedAt)} - ${formatDateTime(lastStage?.endedAt)}</div>
                    </div>
                    <div class="text-end">
                        <div class="fw-semibold">Σ ${formatInt(totalDuration)} ms</div>
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
                        Показать вызовы (${formatInt(items.length)})
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
        const enabled = !!refs.universalCompareEnabled?.checked;
        refs.universalBeforeWrap?.classList.toggle("d-none", !enabled);
        refs.universalBeforeWrapTo?.classList.toggle("d-none", !enabled);
        if (enabled) {
            syncUniversalBeforeRangeFromAfter();
        }
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
            PAYLOAD_SIZE_BYTES: "Размер полезных данных ответа (байт)",
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
            return;
        }
        if (canvasId === "chart-event-kpi" || canvasId === "chart-event-kpi-compare-inline") {
            ensureKpiMiniWrap(canvas);
            const wrap = canvas.closest(".analytics-chart-wrap");
            const labelsCount = Array.isArray(config?.data?.labels) ? config.data.labels.length : 0;
            if (wrap && !wrap.classList.contains("analytics-chart-wrap-expanded")) {
                applyKpiDynamicWidth(wrap, labelsCount, 20);
            }
        }
        state.chartConfigs[canvasId] = cloneChartConfig(config);
        const existingChart = state.charts[canvasId];
        if (existingChart) {
            const requestedType = config.type || existingChart.config.type;
            const currentType = existingChart.config.type;
            if (requestedType !== currentType) {
                existingChart.destroy();
                state.charts[canvasId] = new Chart(canvas.getContext("2d"), config);
                return;
            }
            existingChart.data = config.data;
            existingChart.options = config.options;
            setChartExpandedTicks(existingChart, false);
            existingChart.update("none");
            if (isExpandedChartRelated(canvasId)) {
                renderExpandedChartClone(state.expandedChart.sourceCanvasId);
            }
            return;
        }
        state.charts[canvasId] = new Chart(canvas.getContext("2d"), config);
        setChartExpandedTicks(state.charts[canvasId], false);
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
        const parent = wrap.parentElement;
        if (!parent) {
            return;
        }
        if (parent.classList.contains("analytics-kpi-mini-outer")) {
            return;
        }
        const outer = document.createElement("div");
        outer.className = "analytics-kpi-mini-outer";
        parent.insertBefore(outer, wrap);
        outer.appendChild(wrap);
    }

    function kpiWidthPercentByLabels(labelsCount, threshold) {
        const count = Number(labelsCount) || 0;
        const safeThreshold = Number.isFinite(Number(threshold)) ? Number(threshold) : 12;
        if (count <= safeThreshold) {
            return 100;
        }
        const extra = count - safeThreshold;
        const percent = 100 + (extra * 6);
        return Math.min(360, Math.max(100, percent));
    }

    function applyKpiDynamicWidth(targetEl, labelsCount, threshold) {
        if (!targetEl) {
            return;
        }
        const percent = kpiWidthPercentByLabels(labelsCount, threshold);
        targetEl.style.width = `${percent}%`;
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
            } else if (response.redirected && response.url.includes("/login")) {
                detail = "Сессия истекла. Выполни повторный вход.";
            } else {
                detail = text;
                detail = detail.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
            }
            if (!expectsJson) {
                detail = detail || "Сервер вернул HTML вместо JSON.";
            }
            const compactDetail = detail.length > 260 ? `${detail.slice(0, 260)}...` : detail;
            throw new Error(`HTTP ${response.status}: ${compactDetail}`);
        }
        if (!expectsJson) {
            if (response.redirected && response.url.includes("/login")) {
                throw new Error("Сессия истекла. Выполни повторный вход.");
            }
            const body = await response.text();
            const compactBody = body
                .replace(/<[^>]+>/g, " ")
                .replace(/\s+/g, " ")
                .trim()
                .slice(0, 260);
            throw new Error(`Сервер вернул не JSON (${response.status}). ${compactBody || "Пустой ответ"}`);
        }
        return response.json();
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
            if (actions.querySelector(`[data-chart-expand='${canvas.id}']`)) {
                return;
            }
            if (INLINE_COMPARE_CHART_IDS.has(canvas.id) && !actions.querySelector(`[data-chart-compare-inline='${canvas.id}']`)) {
                const compareButton = document.createElement("button");
                compareButton.type = "button";
                compareButton.className = "btn btn-outline-dark analytics-chart-icon-btn analytics-chart-compare-btn";
                compareButton.setAttribute("data-chart-compare-inline", canvas.id);
                compareButton.title = "Сравнить до/после";
                compareButton.setAttribute("aria-label", "Сравнить до/после");
                compareButton.innerHTML = '<i class="bi bi-layout-split"></i>';
                compareButton.addEventListener("click", () => {
                    void toggleInlineCompareChart(canvas.id, compareButton);
                });
                actions.appendChild(compareButton);
            }
            if (INLINE_COMPARE_CHART_IDS.has(canvas.id)) {
                ensureInlineComparePresetControl(actions, canvas.id);
            }
            if (NO_EXPAND_CHART_IDS.has(canvas.id)) {
                return;
            }
            const button = document.createElement("button");
            button.type = "button";
            button.className = "btn btn-outline-dark analytics-chart-icon-btn analytics-chart-expand-btn";
            button.setAttribute("data-chart-expand", canvas.id);
            button.title = "Развернуть график";
            button.setAttribute("aria-label", "Развернуть график");
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

    function enableInlineCompareLayout(canvasId) {
        const sourceCanvas = document.getElementById(canvasId);
        const sourceWrap = sourceCanvas?.closest(".analytics-chart-wrap");
        if (!sourceCanvas || !sourceWrap || !sourceWrap.parentElement) {
            return;
        }
        const existingPair = refs.analyticsPage?.querySelector(`.analytics-chart-compare-pair[data-chart-id='${canvasId}']`);
        if (existingPair) {
            return;
        }
        const parent = sourceWrap.parentElement;
        const pair = document.createElement("div");
        pair.className = "analytics-chart-compare-pair";
        pair.setAttribute("data-chart-id", canvasId);

        const compareWrap = document.createElement("div");
        compareWrap.className = sourceWrap.className;
        compareWrap.classList.add("analytics-chart-wrap-compare");
        const compareCanvas = document.createElement("canvas");
        const compareCanvasId = `${canvasId}-compare-inline`;
        compareCanvas.id = compareCanvasId;
        compareWrap.appendChild(compareCanvas);

        parent.insertBefore(pair, sourceWrap);
        pair.appendChild(compareWrap);
        pair.appendChild(sourceWrap);
        state.inlineCompareCanvasBySource[canvasId] = compareCanvasId;
        bindUniversalCompareScrollSync(canvasId);
    }

    function disableInlineCompareLayout(canvasId) {
        const pair = refs.analyticsPage?.querySelector(`.analytics-chart-compare-pair[data-chart-id='${canvasId}']`);
        const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
        if (pair) {
            const sourceWrap = pair.querySelector(".analytics-chart-wrap:not(.analytics-chart-wrap-compare)");
            const parent = pair.parentElement;
            if (sourceWrap && parent) {
                parent.insertBefore(sourceWrap, pair);
            }
            pair.remove();
        }
        destroyChart(compareCanvasId);
        delete state.inlineCompareCanvasBySource[canvasId];
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
            button.title = expanded ? "Свернуть график" : "Развернуть график";
            button.setAttribute("aria-label", expanded ? "Свернуть график" : "Развернуть график");
            button.classList.toggle("btn-dark", expanded);
            button.classList.toggle("btn-outline-dark", !expanded);
        });
        updateCompareButtonsState();
    }

    function updateCompareButtonsState() {
        const buttons = refs.analyticsPage?.querySelectorAll("[data-chart-compare-inline]") || [];
        buttons.forEach((button) => {
            const canvasId = button.getAttribute("data-chart-compare-inline") || "";
            const enabled = !!state.inlineCompareEnabled[canvasId];
            button.classList.toggle("btn-dark", enabled);
            button.classList.toggle("btn-outline-dark", !enabled);
            button.title = enabled ? "Скрыть до/после" : "Сравнить до/после";
            button.setAttribute("aria-label", enabled ? "Скрыть до/после" : "Сравнить до/после");
            const actions = button.closest(".analytics-chart-actions");
            if (actions) {
                ensureInlineComparePresetControl(actions, canvasId);
            }
        });
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
        ghost.label = `${baseDataset?.label || "Серия"} (До)`;
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

    function inlineCompareParams(presetCode) {
        if (String(presetCode || "").trim().toLowerCase() === "all") {
            const allRange = getAllTimeLocalRange();
            const paramsAll = mainParams();
            paramsAll.set("from", new Date(allRange.from).toISOString());
            paramsAll.set("to", new Date(allRange.to).toISOString());
            return paramsAll;
        }
        const durationMs = parseQuickRangeMs(presetCode);
        const params = mainParams();
        if (!durationMs) {
            return compareTargetParamsFromBaseline();
        }
        const topFrom = refs.from?.value ? new Date(refs.from.value) : null;
        const topTo = refs.to?.value ? new Date(refs.to.value) : null;
        const anchorMs = topTo && !Number.isNaN(topTo.getTime()) ? topTo.getTime() : Date.now();
        const targetFromMs = anchorMs - durationMs;
        const baselineToMs = targetFromMs;
        const baselineFromMs = baselineToMs - durationMs;
        params.set("from", new Date(baselineFromMs).toISOString());
        params.set("to", new Date(baselineToMs).toISOString());
        if (topFrom && topTo && !Number.isNaN(topFrom.getTime()) && !Number.isNaN(topTo.getTime())) {
            const topDurationMs = Math.max(0, topTo.getTime() - topFrom.getTime());
            if (Math.abs(topDurationMs - durationMs) <= 120000) {
                params.set("from", new Date(topFrom.getTime() - durationMs).toISOString());
                params.set("to", new Date(topFrom.getTime()).toISOString());
            }
        }
        return params;
    }

    function inlineCompareAfterParams(presetCode) {
        if (String(presetCode || "").trim().toLowerCase() === "all") {
            const allRange = getAllTimeLocalRange();
            const paramsAll = mainParams();
            paramsAll.set("from", new Date(allRange.from).toISOString());
            paramsAll.set("to", new Date(allRange.to).toISOString());
            return paramsAll;
        }
        const durationMs = parseQuickRangeMs(presetCode);
        const params = mainParams();
        if (!durationMs) {
            return params;
        }
        const topFrom = refs.from?.value ? new Date(refs.from.value) : null;
        const topTo = refs.to?.value ? new Date(refs.to.value) : null;
        const anchorMs = topTo && !Number.isNaN(topTo.getTime()) ? topTo.getTime() : Date.now();
        const targetFromMs = anchorMs - durationMs;
        params.set("from", new Date(targetFromMs).toISOString());
        params.set("to", new Date(anchorMs).toISOString());
        if (topFrom && topTo && !Number.isNaN(topFrom.getTime()) && !Number.isNaN(topTo.getTime())) {
            const topDurationMs = Math.max(0, topTo.getTime() - topFrom.getTime());
            if (Math.abs(topDurationMs - durationMs) <= 120000) {
                params.set("from", new Date(topFrom.getTime()).toISOString());
                params.set("to", new Date(topTo.getTime()).toISOString());
            }
        }
        return params;
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
        const bucketOverride = resolveExpandedBucket(canvasId);
        const chartOptions = getExpandedEventRenderOptions(canvasId);
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
            if (state.inlineCompareEnabled[canvasId]) {
                const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
                const topFrom = refs.from?.value ? new Date(refs.from.value) : null;
                const topTo = refs.to?.value ? new Date(refs.to.value) : null;
                const hasTopRange = topFrom && topTo && !Number.isNaN(topFrom.getTime()) && !Number.isNaN(topTo.getTime());
                if (hasTopRange) {
                    const durationMs = Math.max(60_000, topTo.getTime() - topFrom.getTime());
                    const beforeFrom = toDateTimeLocalString(new Date(topFrom.getTime() - durationMs));
                    const beforeTo = toDateTimeLocalString(new Date(topFrom.getTime()));
                    const beforeConfig = await buildChartConfigByRange(canvasId, beforeFrom, beforeTo, "До", bucketOverride, chartOptions);
                    upsertChart(compareCanvasId, beforeConfig);
                    if (isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
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
        const afterConfig = await buildChartConfigByRange(canvasId, ranges.afterFrom, ranges.afterTo, "После", bucketOverride, chartOptions);
        upsertChart(canvasId, afterConfig);
        if (state.inlineCompareEnabled[canvasId]) {
            const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
            const beforeConfig = await buildChartConfigByRange(canvasId, ranges.beforeFrom, ranges.beforeTo, "До", bucketOverride, chartOptions);
            upsertChart(compareCanvasId, beforeConfig);
            if (isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
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
        const afterFromDate = new Date(stored.afterFrom);
        const afterToDate = new Date(stored.afterTo);
        if (Number.isNaN(afterFromDate.getTime()) || Number.isNaN(afterToDate.getTime()) || afterFromDate >= afterToDate) {
            return;
        }
        const bucketOverride = resolveExpandedBucket(canvasId);
        const chartOptions = getExpandedEventRenderOptions(canvasId);
        const afterConfig = await buildChartConfigByRange(canvasId, stored.afterFrom, stored.afterTo, state.inlineCompareEnabled[canvasId] ? "После" : "Период", bucketOverride, chartOptions);
        if (!state.inlineCompareEnabled[canvasId]) {
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
        upsertChart(compareCanvasId, beforeConfig);
        if (isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
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
        const afterFromRaw = refs.from?.value || "";
        const afterToRaw = refs.to?.value || "";
        const afterFromDate = afterFromRaw ? new Date(afterFromRaw) : null;
        const afterToDate = afterToRaw ? new Date(afterToRaw) : null;
        if (!afterFromDate || !afterToDate || Number.isNaN(afterFromDate.getTime()) || Number.isNaN(afterToDate.getTime())) {
            const now = new Date();
            const hourAgo = new Date(now.getTime() - 60 * 60 * 1000);
            return {
                beforeFrom: toDateTimeLocalString(hourAgo),
                beforeTo: toDateTimeLocalString(now),
                afterFrom: toDateTimeLocalString(now),
                afterTo: toDateTimeLocalString(new Date(now.getTime() + 60 * 60 * 1000))
            };
        }
        if (state.globalCompareBeforeCustom && isValidGlobalBeforeRange()) {
            return {
                beforeFrom: refs.globalBeforeFrom?.value || "",
                beforeTo: refs.globalBeforeTo?.value || "",
                afterFrom: afterFromRaw,
                afterTo: afterToRaw
            };
        }
        const preset = (state.globalComparePreset || "").trim().toLowerCase();
        const presetMs = parseQuickRangeMs(preset);
        const durationMs = presetMs || Math.max(60_000, afterToDate.getTime() - afterFromDate.getTime());
        const beforeTo = new Date(afterFromDate.getTime());
        const beforeFrom = new Date(beforeTo.getTime() - durationMs);
        return {
            beforeFrom: toDateTimeLocalString(beforeFrom),
            beforeTo: toDateTimeLocalString(beforeTo),
            afterFrom: afterFromRaw,
            afterTo: afterToRaw
        };
    }

    function syncGlobalCompareControlsVisibility() {
        const enabled = !!state.globalCompareEnabled;
        refs.globalBeforeRow?.classList.toggle("d-none", !enabled);
        if (refs.globalCompareEnabled) {
            refs.globalCompareEnabled.checked = enabled;
        }
        if (refs.globalComparePreset && !state.globalCompareBeforeCustom) {
            refs.globalComparePreset.value = (state.globalComparePreset || "").trim();
        }
        if (enabled) {
            const ranges = resolveGlobalBeforeRange();
            if (refs.globalBeforeFrom) refs.globalBeforeFrom.value = ranges.beforeFrom || "";
            if (refs.globalBeforeTo) refs.globalBeforeTo.value = ranges.beforeTo || "";
        }
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
        const ghostChecked = !!refs.globalCompareGhost?.checked;
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            state.inlineCompareGhostBySource[canvasId] = ghostChecked;
        });
        if (refs.universalCompareGhost) {
            refs.universalCompareGhost.checked = ghostChecked;
        }
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

    async function setInlineCompareEnabledForAll(enabled) {
        const ids = Array.from(INLINE_COMPARE_CHART_IDS);
        for (const canvasId of ids) {
            if (!!state.inlineCompareEnabled[canvasId] === enabled) {
                continue;
            }
            const button = refs.analyticsPage?.querySelector(`[data-chart-compare-inline='${canvasId}']`);
            await toggleInlineCompareChart(canvasId, button || null);
        }
    }

    async function applyGlobalBeforeRangeToAllCharts() {
        const ranges = resolveGlobalBeforeRange();
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            state.inlineComparePresetOverriddenBySource[canvasId] = true;
            state.expandedRangesBySource[canvasId] = {
                beforeFrom: ranges.beforeFrom,
                beforeTo: ranges.beforeTo,
                afterFrom: ranges.afterFrom,
                afterTo: ranges.afterTo
            };
        });
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
    }

    async function applyGlobalCompareToAllCharts() {
        syncGlobalCompareControlsVisibility();
        if (!state.globalCompareEnabled) {
            await setInlineCompareEnabledForAll(false);
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
            if (refs.universalCompareEnabled?.checked) {
                refs.universalCompareEnabled.checked = false;
                await loadUniversal();
            }
            return;
        }

        const globalPreset = (state.globalComparePreset || "").trim().toLowerCase();
        INLINE_COMPARE_CHART_IDS.forEach((canvasId) => {
            state.inlineComparePresetOverriddenBySource[canvasId] = false;
            if (globalPreset) {
                state.inlineComparePresetBySource[canvasId] = globalPreset;
            }
            state.inlineCompareGhostBySource[canvasId] = !!refs.globalCompareGhost?.checked;
        });
        await setInlineCompareEnabledForAll(true);
        if (state.globalCompareBeforeCustom) {
            await applyGlobalBeforeRangeToAllCharts();
        } else {
            await Promise.all(Array.from(INLINE_COMPARE_CHART_IDS).map(async (canvasId) => {
                if (state.inlineCompareEnabled[canvasId]) {
                    await applyInlineComparePresetToChart(canvasId);
                }
            }));
        }

        if (refs.stageMetricCompareEnabled && !refs.stageMetricCompareEnabled.checked) {
            refs.stageMetricCompareEnabled.checked = true;
            toggleStageMetricCompare();
            await loadStageMetrics();
        }
        if (refs.stageTextCompareEnabled && !refs.stageTextCompareEnabled.checked) {
            refs.stageTextCompareEnabled.checked = true;
            toggleStageTextCompare();
            await loadStageMetricText();
        }
        if (refs.universalCompareEnabled) {
            refs.universalCompareEnabled.checked = true;
            if (refs.universalCompareGhost) {
                refs.universalCompareGhost.checked = !!refs.globalCompareGhost?.checked;
            }
            if (state.globalCompareBeforeCustom && isValidGlobalBeforeRange()) {
                if (refs.universalBeforeFrom) refs.universalBeforeFrom.value = refs.globalBeforeFrom?.value || "";
                if (refs.universalBeforeTo) refs.universalBeforeTo.value = refs.globalBeforeTo?.value || "";
            }
            await loadUniversal();
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
        const sourceConfig = state.chartConfigs[canvasId];
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
        const compareSourceConfig = compareSourceCanvasId ? state.chartConfigs[compareSourceCanvasId] : null;
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
        const isKpiExpanded = sourceCanvasId === "chart-event-kpi";
        const isUniversalExpanded = UNIVERSAL_COMPARE_CHART_IDS.has(sourceCanvasId);
        const sourceLabelsCount = Array.isArray(state.chartConfigs[sourceCanvasId]?.data?.labels)
            ? state.chartConfigs[sourceCanvasId].data.labels.length
            : 0;
        const baseWidthPercent = isKpiExpanded ? kpiWidthPercentByLabels(sourceLabelsCount, 60) : 100;
        const baseViewportHeight = 489;
        const baseContentHeight = isKpiExpanded
            ? Math.round(baseViewportHeight * 1.2)
            : baseViewportHeight;
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

        const applyZoomX = (zoomValue) => {
            const widthPercent = Math.max(100, Number(zoomValue) || 100);
            const effectivePercent = Math.round((baseWidthPercent * widthPercent) / 100);
            zoomHosts.forEach((host) => {
                host.style.width = `${effectivePercent}%`;
            });
            if (valueX) {
                valueX.textContent = `${widthPercent}%`;
            }
        };
        const applyZoomY = (zoomValue) => {
            const yPercent = Math.max(100, Number(zoomValue) || 100);
            const ratio = yPercent / 100;
            const scaledHeight = Math.round(baseContentHeight * ratio);
            expandedWraps.forEach((wrap) => {
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
        window.requestAnimationFrame(() => applyZoomY(rangeY?.value || 100));

        if (scrolls.length >= 2) {
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
        const defaults = resolveExpandedRangesForMode(canvasId, isCompareEnabled);
        controls.innerHTML = `
            <div class="analytics-expanded-graph-row analytics-expanded-graph-row-top">
                <div class="analytics-expanded-toolbar-left" data-expanded-toolbar-left>
                    ${eventFilterHtml}
                    <label class="form-check form-check-inline m-0 small ${isCompareEnabled ? "" : "d-none"}" data-expanded-ghost-wrap>
                        <input class="form-check-input" type="checkbox" data-expanded-ghost ${isInlineGhostEnabled(canvasId) ? "checked" : ""}>
                        <span class="form-check-label">До поверх</span>
                    </label>
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
                    <button type="button" class="btn btn-outline-dark analytics-chart-icon-btn ${preset !== topPreset ? "" : "d-none"}" data-expanded-reset title="Сбросить к верхнему фильтру" aria-label="Сбросить к верхнему фильтру">
                        <i class="bi bi-arrow-counterclockwise"></i>
                    </button>
                    <button type="button" class="btn ${isCompareEnabled ? "btn-dark" : "btn-outline-dark"} analytics-chart-icon-btn" data-expanded-compare title="Сравнить до/после" aria-label="Сравнить до/после">
                        <i class="bi bi-layout-split"></i>
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
        const compareEl = controls.querySelector("[data-expanded-compare]");
        const actionsEl = controls.querySelector(".analytics-expanded-actions");
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
        const ghostWrapEl = controls.querySelector("[data-expanded-ghost-wrap]");
        const ghostEl = controls.querySelector("[data-expanded-ghost]");
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
            compareEl?.classList.toggle("btn-dark", enabled);
            compareEl?.classList.toggle("btn-outline-dark", !enabled);
            ghostWrapEl?.classList.toggle("d-none", !enabled);
        };
        const applyGhostForExpandedIfNeeded = async () => {
            if (!state.inlineCompareEnabled[canvasId]) {
                return;
            }
            if (!ghostEl || !ghostEl.checked) {
                return;
            }
            state.inlineCompareGhostBySource[canvasId] = true;
            await applyInlineComparePresetToChart(canvasId);
            await applyStoredExpandedRangesToCharts(canvasId);
            if (state.expandedChart.sourceCanvasId === canvasId) {
                renderExpandedChartClone(canvasId);
            }
        };
        syncResetVisibility();
        syncLatencyMetricVisibility();
        syncStageLatencyMetricVisibility();
        syncDateMode();

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
                state.inlineComparePresetOverriddenBySource[canvasId] = false;
                state.inlineComparePresetBySource[canvasId] = resolveTopInlineComparePresetOrDefault();
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

        compareEl?.addEventListener("click", async () => {
            const sourceButton = refs.analyticsPage?.querySelector(`[data-chart-compare-inline='${canvasId}']`);
            await toggleInlineCompareChart(canvasId, sourceButton || compareEl);
            syncDateMode();
        });

        ghostEl?.addEventListener("change", async () => {
            state.inlineCompareGhostBySource[canvasId] = !!ghostEl.checked;
            setChartActionLoading(canvasId, true);
            try {
                await applyInlineComparePresetToChart(canvasId);
                await applyStoredExpandedRangesToCharts(canvasId);
                if (state.expandedChart.sourceCanvasId === canvasId) {
                    renderExpandedChartClone(canvasId);
                }
            } finally {
                setChartActionLoading(canvasId, false);
            }
        });

        if (state.inlineCompareEnabled[canvasId] && ghostEl?.checked) {
            setChartActionLoading(canvasId, true);
            void applyGhostForExpandedIfNeeded()
                .catch((error) => {
                    console.error("Expanded initial ghost apply failed", error);
                })
                .finally(() => {
                    setChartActionLoading(canvasId, false);
                });
        }

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
                    const bucketOverride = resolveExpandedBucket(canvasId);
                    const beforeConfig = await buildChartConfigByRange(canvasId, ranges.beforeFrom, ranges.beforeTo, "До", bucketOverride, chartOptions);
                    const afterConfig = await buildChartConfigByRange(canvasId, ranges.afterFrom, ranges.afterTo, "После", bucketOverride, chartOptions);
                    if (isInlineGhostEnabled(canvasId) && Array.isArray(beforeConfig?.data?.datasets)) {
                        const ghostDatasets = beforeConfig.data.datasets.map((item) => buildGhostDataset(item, canvasId));
                        afterConfig.data = afterConfig.data || {};
                        afterConfig.data.datasets = [...(afterConfig.data.datasets || []), ...ghostDatasets];
                    }
                    const compareCanvasId = state.inlineCompareCanvasBySource[canvasId] || `${canvasId}-compare-inline`;
                    upsertChart(compareCanvasId, beforeConfig);
                    upsertChart(canvasId, afterConfig);
                    state.expandedRangesBySource[canvasId] = {...ranges};
                    await refreshExpandedEventOptions();
                    await renderExpandedChartByRanges(canvasId, ranges, chartOptions);
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
        const primaryCanvas = container.querySelector(`#chart-expanded-${canvasId}`);
        const compareCanvas = container.querySelector(`[data-expanded-compare-for='${canvasId}']`);
        if (!primaryCanvas) {
            return;
        }
        const afterConfig = await buildChartConfigByRange(canvasId, ranges.afterFrom, ranges.afterTo, compareCanvas ? "После" : "Период", undefined, options);
        if (!compareCanvas) {
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
                        label: withSuffix("Событий"),
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
        let actions = wrap.querySelector(":scope > .analytics-chart-actions");
        if (!actions) {
            actions = document.createElement("div");
            actions.className = "analytics-chart-actions";
            wrap.appendChild(actions);
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
        const kpiHints = {
            "chart-events-count": [
                "Начинайте с общей формы графика: ровный фон, плавный рост, резкие пики или пилообразная динамика.",
                "После этого включайте разрез по событиям и смотрите, какое событие дало основной вклад в всплеск.",
                "Если видите пик, обязательно сверяйте этот же интервал с Latency и Error rate — сам по себе рост потока не всегда проблема.",
                "Сравнивайте окно с таким же периодом прошлого дня/недели: это помогает отделить сезонность от реальной аномалии.",
                "Если поток просел почти до нуля, проверяйте не только бизнес-трафик, но и то, что трекинг событий вообще отправляется."
            ],
            "chart-latency": [
                "Опорная метрика для качества опыта — P95: она лучше AVG показывает, как «чувствует» систему заметная часть пользователей.",
                "Если выбрано одно событие, всегда читайте три линии вместе: AVG — фон, P95 — массовая боль, P99 — редкие тяжёлые кейсы.",
                "Если выбрано несколько событий, фиксируйте одну метрику (обычно P95) и сравнивайте события в одном масштабе.",
                "Когда AVG стабилен, а P95/P99 растут, это ранний сигнал деградации хвоста — проблема уже есть, просто она не у всех.",
                "Смотрите на устойчивые участки, а не на одиночные иглы: для решений важнее тренд, чем разовый всплеск."
            ],
            "chart-error-rate": [
                "Сначала ищите не единичные всплески, а длительные участки повышенной ошибки — именно они обычно бьют по бизнесу.",
                "Переключайте события и проверяйте, какой тип события держит повышенный error rate дольше остальных.",
                "Если ошибок мало по count, но доля очень высокая, это может быть редкий, но критичный пользовательский сценарий.",
                "Сравнивайте рост ошибок с потоком: совместный рост часто указывает на перегрузку, изолированный — на дефект логики.",
                "После локализации интервала сразу переходите в Raw с теми же фильтрами: там находится конкретная причина."
            ],
            "chart-event-kpi": [
                "Это карта приоритетов: где одновременно большой объём, высокий P95 и/или высокий error rate.",
                "Сначала смотрите на объём (count), потом на риск (ошибки и задержки) — так проще выбрать, что чинить первым.",
                "Высокий count + высокий P95 обычно даёт максимальный эффект от оптимизации для реальных пользователей.",
                "Низкий count + высокий error rate не стоит игнорировать: это часто редкий, но важный сценарий (например, ключевая операция).",
                "Используйте график как точку входа: выбрали проблемное событие и провалились в остальные графики с тем же фильтром."
            ],
            "chart-stage-latency": [
                "График показывает, в каком слое (Controller/Service/Database/Frontend) реально накапливается задержка.",
                "Сравнивайте один и тот же слой между событиями: это быстро показывает, где проблема общая, а где сценарная.",
                "Если слой-лидер стабильно один и тот же, у вас локализованное узкое место и понятный кандидат на оптимизацию.",
                "Если лидеры по слоям постоянно меняются, ищите плавающую причину: зависимость, сеть, очередь, внешний сервис.",
                "При множественном выборе событий фиксируйте метрику (AVG/P95), чтобы сравнение между событиями было честным."
            ],
            "chart-stage-errors": [
                "Показывает, где именно рождается ошибка: в контроллере, сервисе, базе или клиентском слое.",
                "Смотрите не только общий фон, но и конкретные события — одна и та же ошибка может жить в разных слоях по разным сценариям.",
                "Если один слой резко доминирует по ошибкам, это почти всегда лучшая точка старта для расследования.",
                "Если ошибки равномерно растут по всем слоям, вероятна системная причина, а не локальный баг.",
                "Сравнение «событие против общей статистики» помогает понять, это общий инцидент или узкий дефект конкретного флоу."
            ],
            "chart-stage-metric-series": [
                "Этот график нужен, чтобы увидеть динамику метрики по времени: до/после релиза, на пике нагрузки, в инцидент.",
                "Если единицы у метрик разные, нормализация показывает форму тренда — сравнивайте направление и переломы.",
                "Сопоставляйте динамику с Latency/Error rate: совпадение переломов часто даёт прямую гипотезу причины.",
                "Резкая ступенька после изменения почти всегда указывает на конкретный момент регрессии.",
                "Пилообразная динамика обычно говорит о периодическом фоне (batch, cron, периодическая очистка и т.п.)."
            ],
            "chart-stage-metric-top-values": [
                "Для одной метрики показывает, какие значения встречаются чаще всего и где концентрируется нагрузка.",
                "Для нескольких метрик сравнивайте их P95 между собой, чтобы быстро выбрать приоритет.",
                "Если одно значение сильно доминирует, это хороший кандидат на точечную оптимизацию.",
                "Если хвост широкий и без явного лидера, сужайте фильтры: по событию, path или интервалу времени.",
                "Используйте этот график для поиска «аномальных корзин» значений, которые вытягивают деградацию вверх."
            ],
            "chart-compare-delta": [
                "Это быстрый итог изменений «до/после» в одном месте: сразу видно, стало лучше, хуже или нейтрально.",
                "Для latency и error rate отрицательная дельта обычно означает улучшение качества.",
                "Для count интерпретация зависит от контекста: рост может быть нормой бизнеса, а может быть следствием дублей/шума.",
                "Сравнивайте только равные по длительности окна, иначе выводы по дельте легко исказить.",
                "Если общая дельта спорная, дробите анализ по событиям: часто регресс скрыт внутри одного сценария."
            ]
        };

        const interpretation = {
            "chart-events-count": [
                "Кейс: count растёт, latency и error стабильны. Вывод: система держит рост, масштабирование пока достаточное.",
                "Кейс: count растёт вместе с P95 и error. Вывод: система начинает упираться в ресурсы или в тяжелый сценарий.",
                "Кейс: count стабильный, а latency скачет. Вывод: проблема чаще в качестве обработки, а не в объёме трафика.",
                "Кейс: count падает почти до нуля. Вывод: проверьте, не отвалился ли сбор событий/трекер.",
                "Кейс: резкие короткие пики без последствий по качеству. Вывод: разовые всплески трафика, не обязательно инцидент."
            ],
            "chart-latency": [
                "Кейс: AVG ровный, P95/P99 растут. Вывод: деградация в хвостах — заметная доля пользователей уже страдает.",
                "Кейс: растут все три линии. Вывод: массовая деградация, проблема затрагивает большую часть запросов.",
                "Кейс: прыгает только P99. Вывод: редкие, но болезненные выбросы, нужны точечные trace для расследования.",
                "Кейс: latency выросла без роста потока. Вывод: корень проблемы, скорее всего, в логике/БД/интеграции.",
                "Кейс: latency ухудшилась только на одном событии. Вывод: локальная проблема конкретного пользовательского флоу."
            ],
            "chart-error-rate": [
                "Кейс: короткий единичный всплеск. Вывод: вероятен временный внешний сбой, но стоит проверить повторяемость.",
                "Кейс: длительная полка ошибок. Вывод: устойчивый дефект, нужна приоритизация как инцидента.",
                "Кейс: ошибка растёт только на одном событии. Вывод: локальная проблема в конкретном пользовательском сценарии.",
                "Кейс: ошибок мало по count, но доля очень высокая. Вывод: ломается редкий, но важный сценарий — игнорировать нельзя.",
                "Кейс: error растёт вместе с latency. Вывод: возможен каскад таймаутов и перегрузка."
            ],
            "chart-event-kpi": [
                "Кейс: высокий count и высокий P95. Вывод: это главный кандидат на оптимизацию, эффект будет заметен многим.",
                "Кейс: событий мало, но доля ошибок очень высокая. Вывод: вероятно, ломается редкий, но важный сценарий — игнорировать нельзя.",
                "Кейс: высокий count и нормальный P95, но растёт error. Вывод: проблема в корректности, а не в скорости.",
                "Кейс: высокий P95 без ошибок. Вывод: сервис работает, но медленно — нужна чистая performance-оптимизация.",
                "Кейс: высокий error без роста P95. Вывод: быстрый отказ, чаще логическая/валидационная проблема."
            ],
            "chart-stage-latency": [
                "Кейс: лидер по P95 — DATABASE. Вывод: приоритет на SQL, индексы, планы запроса, пул соединений.",
                "Кейс: лидер — SERVICE. Вывод: тяжелая бизнес-логика, внешние вызовы или лишние вычисления.",
                "Кейс: по выбранному событию слой хуже общей статистики. Вывод: узкое место именно этого сценария.",
                "Кейс: лидеры по слоям часто меняются. Вывод: причина плавающая, ищите внешнюю нестабильность.",
                "Кейс: FRONTEND слой вырос при стабильном backend. Вывод: возможно сеть/клиентская обработка."
            ],
            "chart-stage-errors": [
                "Кейс: ошибки концентрируются в CONTROLLER. Вывод: проверьте валидацию, маршрутизацию и контракты запроса.",
                "Кейс: ошибки в SERVICE. Вывод: проблема в доменной логике или интеграционных зависимостях.",
                "Кейс: ошибки в DATABASE. Вывод: SQL, блокировки, таймауты и состояние соединений.",
                "Кейс: ошибки растут во всех слоях сразу. Вывод: вероятна системная причина, а не локальный баг.",
                "Кейс: ошибки только у одного события. Вывод: дефект специализирован и быстро локализуется."
            ],
            "chart-stage-metric-series": [
                "Кейс: метрика растёт синхронно с P95. Вывод: высокая вероятность, что именно она тянет деградацию.",
                "Кейс: метрика растёт, но P95 стабильный. Вывод: запас производительности пока есть.",
                "Кейс: метрика скачет только на одном этапе. Вывод: локализованное узкое место.",
                "Кейс: ступенька после релиза. Вывод: есть четкая привязка к изменению, удобно откатывать/сравнивать.",
                "Кейс: пилообразная динамика. Вывод: влияние периодических фоновых процессов."
            ],
            "chart-stage-metric-top-values": [
                "Кейс: одно top-значение доминирует. Вывод: есть повторяющийся паттерн данных, который и надо оптимизировать.",
                "Кейс: широкий хвост top-значений. Вывод: проблема размыта, нужен более узкий фильтр.",
                "Кейс: по нескольким метрикам один лидер P95. Вывод: это главный приоритет для оптимизации.",
                "Кейс: доминирующий top сменился после изменений. Вывод: профиль нагрузки изменился, проверьте новые ветки.",
                "Кейс: редкие top начали расти. Вывод: появляются новые аномальные сценарии."
            ],
            "chart-compare-delta": [
                "Кейс: P95 и error ушли в минус, count ровный. Вывод: качественное улучшение после изменений подтверждается данными.",
                "Кейс: count вырос, P95 тоже вырос. Вывод: вероятен нагрузочный эффект без нужного масштабирования.",
                "Кейс: count упал, error вырос. Вывод: часть запросов отваливается до нормальной обработки.",
                "Кейс: дельта по AVG хорошая, но по P95/P99 нет. Вывод: улучшили фон, но тяжёлые кейсы остались.",
                "Кейс: общий результат нейтральный, но по событиям есть сильный разброс. Вывод: анализировать нужно по событиям отдельно."
            ]
        };

        const nextSteps = {
            "chart-events-count": [
                "Выберите проблемный интервал (пик/просадку), затем включите разрез по событиям и найдите главный вклад.",
                "Сузьте фильтры по path и событию, после чего откройте Raw для конкретных trace.",
                "Проверьте тот же интервал на Latency и Error rate, чтобы подтвердить бизнес-риск.",
                "Сравните с аналогичным окном прошлого периода, чтобы исключить сезонность.",
                "Зафиксируйте baseline перед правкой и используйте «до/после» для проверки эффекта."
            ],
            "chart-latency": [
                "Для одного события анализируйте AVG/P95/P99 вместе, не по отдельности.",
                "Для нескольких событий фиксируйте одну метрику (обычно P95), иначе сравнение будет шумным.",
                "После локализации переходите в Этапы выполнения, чтобы понять, какой слой даёт задержку.",
                "Далее в Метриках этапов ищите корневые причины (SQL count, payload, элементы и т.п.).",
                "Проверьте исправление на том же окне и на соседнем пиковом окне, чтобы исключить случайный эффект."
            ],
            "chart-error-rate": [
                "Включите разрез по событиям и найдите, какое событие держит основную долю ошибок.",
                "Перейдите в Raw: «Только ошибки», нужный период, сортировка по времени.",
                "Соберите топ trace/path/атрибутов для воспроизведения и быстрой локализации.",
                "Проверьте, не связан ли рост ошибки с пиковыми интервалами потока или ростом latency.",
                "После правки сравните до/после на равных окнах и убедитесь, что ошибка ушла устойчиво."
            ],
            "chart-event-kpi": [
                "Выберите 1–2 события с худшим сочетанием объёма и риска (count + P95/error).",
                "Примените выбранное событие в остальных графиках и подтвердите источник проблемы по слоям.",
                "В Raw соберите конкретные запросы и trace для разработческой диагностики.",
                "Зафиксируйте baseline (до изменений), чтобы потом честно сравнить результат.",
                "После исправления вернитесь сюда и перепроверьте, что событие ушло из зоны риска."
            ],
            "chart-stage-latency": [
                "Сравните слой-лидер в общей статистике и в проблемном событии — это покажет локальность проблемы.",
                "При множественном выборе событий фиксируйте метрику и ищите устойчиво худший слой.",
                "Перейдите в Метрики этапов и уточните техническую причину (SQL, payload, элементы).",
                "Сверьте вывод с Error по слоям: часто latency и ошибки сходятся в одном узле.",
                "Повторите сравнение после изменений на тех же условиях, чтобы подтвердить эффект."
            ],
            "chart-stage-errors": [
                "Сначала определите слой с максимальной долей ошибок — это главный кандидат на разбор.",
                "Сравните событие против общей статистики, чтобы понять, дефект общий или сценарный.",
                "Провалитесь в Raw и соберите trace-логи именно по этому слою и периоду.",
                "Проверьте, не совпадает ли рост ошибок с ростом latency на том же слое.",
                "После исправления отследите устойчивое снижение ошибки, а не только разовый удачный интервал."
            ],
            "chart-stage-metric-series": [
                "Оставляйте 1–3 ключевые метрики, чтобы не утонуть в шуме и не потерять причинные связи.",
                "Сверяйте переломы графика с релизами, фича-флагами и инцидентами по времени.",
                "Смотрите, совпадает ли рост метрики с ухудшением P95/error — это сильная гипотеза причины.",
                "Проверяйте динамику на нескольких окнах, чтобы исключить случайное совпадение.",
                "После оптимизации убедитесь, что улучшается не только метрика, но и пользовательские KPI."
            ],
            "chart-stage-metric-top-values": [
                "Выделите доминирующие значения и проверьте, какие события/экраны их создают.",
                "Сузьте фильтры до конкретного диапазона и подтвердите проблему на Raw событиях.",
                "Сопоставьте top-значения с latency/error: важны не просто частые, а именно вредные значения.",
                "Проверьте, как эти значения ведут себя в периодах до/после изменений.",
                "Если лидер не один, дробите анализ по событиям и path, иначе выводы будут размыты."
            ],
            "chart-compare-delta": [
                "Сравнивайте только равные по длительности окна и сопоставимые по нагрузке периоды.",
                "Сначала оценивайте качество (P95/error), затем объём (count) и вторичные метрики.",
                "Если результаты разнонаправленные, обязательно дробите анализ по событиям.",
                "Фиксируйте выводы и baseline в одном месте, чтобы не потерять контекст между итерациями.",
                "Повторяйте сравнение на соседнем периоде, чтобы подтвердить устойчивость улучшения."
            ]
        };
        const trendHints = {
            "chart-events-count": [
                "Ровный фон: стабильная нагрузка, без аномалий по входящему потоку.",
                "Плавный рост: естественное увеличение трафика (акция, прайм-тайм, рассылка).",
                "Ступенька вверх: резкое изменение входного потока, часто после внешнего события.",
                "Короткая игла: разовый всплеск, обычно не критичен без роста latency/error.",
                "Пила: циклические батчи, периодические фоновые задачи или повторяющиеся пользовательские волны."
            ],
            "chart-latency": [
                "Плавный рост P95/P99 при стабильном AVG: ухудшение хвостов, часть запросов стала тяжелее.",
                "Рост всех линий: системная деградация, проблема затрагивает почти всех пользователей.",
                "Редкие иглы P99: единичные тяжелые кейсы, ищите аномальные trace/path.",
                "Ступенька вверх после изменения: вероятный регресс после релиза/конфигурации.",
                "Пила: периодический внешний фактор (батчи, GC-паузы, очереди, внешние API)."
            ],
            "chart-error-rate": [
                "Низкий ровный фон: штатное состояние, случайные ошибки в пределах нормы.",
                "Короткие всплески: временные сбои (сеть, внешний сервис), проверяйте повторяемость.",
                "Длинная полка: устойчивый дефект, требует приоритизации и инцидентного разбора.",
                "Ступенька вверх: резкая смена состояния системы (релиз, фича-флаг, инфраструктура).",
                "Параллельный рост с latency: обычно перегрузка или цепочка ошибок по таймаутам."
            ],
            "chart-event-kpi": [
                {type: "bar-leader", text: "Событие с большим count и большим P95: главный кандидат на оптимизацию UX."},
                {type: "bar-spike", text: "Событие с низким count и высоким error rate: проблемный редкий сценарий, который может быть критичным."},
                {type: "bar-uniform", text: "Рост count без роста риска: масштабирование потока без деградации качества."},
                {type: "bar-compare", text: "Рост риска при стабильном count: проблема в логике/данных, а не в объеме."},
                {type: "bar-rotate", text: "Смена лидера по риску: появились новые узкие места, пересмотрите приоритеты."}
            ],
            "chart-stage-latency": [
                {type: "bar-leader", text: "Лидер по P95 стабильно один и тот же слой: локализованное узкое место."},
                {type: "bar-rotate", text: "Ротация лидеров между слоями: плавающая причина, проверьте зависимые системы."},
                {type: "bar-spike", text: "Прыжок только у DATABASE: SQL/индексы/блокировки/пул соединений."},
                {type: "bar-spike", text: "Прыжок у SERVICE при нормальной базе: тяжелая бизнес-логика или внешние вызовы."},
                {type: "bar-compare", text: "Событие хуже общей статистики в одном слое: проблема специфична для этого сценария."}
            ],
            "chart-stage-errors": [
                {type: "bar-leader", text: "Ошибки сконцентрированы в одном слое: точка отказа локализована."},
                {type: "bar-uniform", text: "Равномерный рост по слоям: системный инцидент или каскадные ошибки."},
                {type: "bar-spike", text: "Иглы в DATABASE: кратковременные проблемы БД/соединений."},
                {type: "bar-plateau", text: "Полка в SERVICE: устойчивый дефект в бизнес-ветке или интеграции."},
                {type: "bar-compare", text: "Рост только для одного события: специализированный баг сценария."}
            ],
            "chart-stage-metric-series": [
                "Плавный рост метрики: накопительный эффект нагрузки/данных.",
                "Ступенька после релиза: изменение поведения системы из-за кода/конфига.",
                "Пила: периодические фоновые процессы влияют на метрику.",
                "Метрика растет вместе с P95/error: высокая вероятность причинной связи.",
                "Метрика растет, а P95 стабилен: запас по производительности еще есть."
            ],
            "chart-stage-metric-top-values": [
                "Один доминирующий top: повторяющийся шаблон деградации.",
                "Широкое распределение top: проблема размыта, нужен дополнительный фильтр.",
                "Смена доминирующего top во времени: изменился профиль нагрузки.",
                "Стабильный высокий P95 у одной метрики: приоритет для оптимизации.",
                "Рост количества редких top: появляются новые аномальные кейсы."
            ],
            "chart-compare-delta": [
                "Устойчивая отрицательная дельта latency/error: качественное улучшение.",
                "Положительная дельта только по count: возможно рост бизнеса без регресса.",
                "Положительная дельта по latency при стабильном count: регресс в обработке.",
                "Разнонаправленные дельты: сегментируйте по событиям и path, общий итог скрывает детали.",
                "Резкая смена знака дельты: переломный момент после изменения условий."
            ]
        };
        const antiPatterns = {
            "chart-events-count": [
                "Анти-паттерн: считать любой пик проблемой. Правильно: проверять, выросли ли в тот же момент latency и error rate.",
                "Анти-паттерн: сравнивать будний день с выходным без поправки на сезонность. Правильно: брать сопоставимые периоды.",
                "Анти-паттерн: делать вывод по общему потоку без разреза по событиям. Правильно: локализовать вклад конкретного события.",
                "Анти-паттерн: игнорировать просадки потока. Правильно: проверять, не сломался ли трекинг событий или маршрут.",
                "Анти-паттерн: сравнивать окна разной длины. Правильно: использовать одинаковые интервалы."
            ],
            "chart-latency": [
                "Анти-паттерн: ориентироваться только на AVG. Правильно: основную оценку качества делать по P95/P99.",
                "Анти-паттерн: анализировать latency без учета потока. Правильно: проверять, не связан ли рост со всплеском нагрузки.",
                "Анти-паттерн: принимать одиночный пик P99 за массовую проблему. Правильно: смотреть устойчивость тренда.",
                "Анти-паттерн: сравнивать разные события в разных метриках одновременно. Правильно: фиксировать одну метрику для честного сравнения.",
                "Анти-паттерн: не проверять этапы выполнения после локализации. Правильно: искать слой-источник задержки."
            ],
            "chart-error-rate": [
                "Анти-паттерн: делать вывод по одной «игле» ошибки. Правильно: смотреть, держится ли рост хотя бы на серии соседних точек.",
                "Анти-паттерн: сравнивать периоды разной длины и с разной нагрузкой. Правильно: сравнивать равные окна и похожие часы/дни.",
                "Анти-паттерн: смотреть только общий Error rate. Правильно: включать разрез по событиям и искать источник внутри конкретного флоу.",
                "Анти-паттерн: игнорировать низкий count при высоком Error rate. Правильно: проверять, не ломается ли редкий, но критичный сценарий.",
                "Анти-паттерн: не сверять ошибку с latency и потоком. Правильно: смотреть связку из трёх графиков, чтобы не перепутать причину.",
                "Анти-паттерн: лечить симптом «в среднем стало лучше». Правильно: проверять P95/P99 и Raw-события, чтобы убедиться, что хвосты тоже исправлены."
            ],
            "chart-event-kpi": [
                "Анти-паттерн: выбирать приоритет только по count. Правильно: учитывать связку count + latency + error rate.",
                "Анти-паттерн: игнорировать low-count события с высоким error rate. Правильно: проверять критичность сценария для бизнеса.",
                "Анти-паттерн: смешивать общий фон и сценарные аномалии. Правильно: проваливаться в событие и подтверждать вывод в других графиках.",
                "Анти-паттерн: делать вывод без проверки Raw trace. Правильно: подтверждать проблему конкретными событиями.",
                "Анти-паттерн: считать нейтральный общий KPI отсутствием проблемы. Правильно: смотреть разброс по отдельным событиям."
            ],
            "chart-stage-latency": [
                "Анти-паттерн: смотреть только на абсолютный лидер слоя. Правильно: сравнивать слой в разрезе событий и периода.",
                "Анти-паттерн: делать вывод по одному окну. Правильно: проверять повторяемость на соседних окнах.",
                "Анти-паттерн: игнорировать смену лидера между событиями. Правильно: это сигнал разных корневых причин.",
                "Анти-паттерн: не связывать слой с метриками этапов. Правильно: подтверждать гипотезу через SQL/payload и др.",
                "Анти-паттерн: сравнивать разные метрики одновременно. Правильно: фиксировать AVG или P95."
            ],
            "chart-stage-errors": [
                "Анти-паттерн: считать все ошибки одинаковыми. Правильно: локализовать слой и тип события.",
                "Анти-паттерн: анализировать ошибки без времени. Правильно: смотреть всплески и длительные полки отдельно.",
                "Анти-паттерн: игнорировать общую статистику при разборе события. Правильно: сравнивать событие против фона.",
                "Анти-паттерн: делать вывод без проверки Raw/trace. Правильно: подтверждать первопричину конкретными кейсами.",
                "Анти-паттерн: не проверять связь с latency. Правильно: ошибки и задержки часто растут вместе в одном слое."
            ],
            "chart-stage-metric-series": [
                "Анти-паттерн: смотреть много метрик сразу и терять читаемость. Правильно: держать 1–3 ключевые метрики.",
                "Анти-паттерн: трактовать нормализованные графики как абсолютные значения. Правильно: читать их как форму тренда.",
                "Анти-паттерн: игнорировать релизные точки во времени. Правильно: сопоставлять переломы с изменениями.",
                "Анти-паттерн: делать вывод по совпадению в одном окне. Правильно: проверять повторяемость на нескольких периодах.",
                "Анти-паттерн: не проверять итог на пользовательских KPI. Правильно: подтверждать эффект по P95/error."
            ],
            "chart-stage-metric-top-values": [
                "Анти-паттерн: брать top-значение как единственную причину. Правильно: проверять его вклад в latency/error.",
                "Анти-паттерн: игнорировать широкий хвост. Правильно: при размытом профиле дробить анализ по событиям/path.",
                "Анти-паттерн: не сравнивать периоды до/после. Правильно: проверять, как меняется доминирующий top после правок.",
                "Анти-паттерн: делать вывод без Raw-подтверждения. Правильно: идти в trace для воспроизведения.",
                "Анти-паттерн: смешивать метрики с разными единицами без контекста. Правильно: сравнивать их отдельно по смыслу."
            ],
            "chart-compare-delta": [
                "Анти-паттерн: сравнивать окна разной длины. Правильно: только равные интервалы.",
                "Анти-паттерн: оценивать успех только по count. Правильно: приоритет — качество (P95/error), потом объем.",
                "Анти-паттерн: делать общий вывод без разреза по событиям. Правильно: общий итог может скрывать локальный регресс.",
                "Анти-паттерн: считать разовую хорошую дельту подтверждением. Правильно: проверять устойчивость на соседних окнах.",
                "Анти-паттерн: игнорировать контекст релиза/нагрузки. Правильно: интерпретировать дельту с учетом условий периода."
            ],
            "analytics-events-table": [
                "Анти-паттерн: смотреть только на первые строки таблицы. Правильно: менять сортировку и фильтры, чтобы увидеть разные срезы проблемы.",
                "Анти-паттерн: анализировать Raw без фиксации периода. Правильно: сначала зафиксировать узкое окно инцидента, потом идти в детали.",
                "Анти-паттерн: смешивать разные события и path в одном выводе. Правильно: изолировать один сценарий и проверять его отдельно.",
                "Анти-паттерн: фокусироваться только на error message. Правильно: обязательно смотреть trace, длительность и атрибуты рядом.",
                "Анти-паттерн: делать вывод по одному trace. Правильно: собрать повторяющийся паттерн из нескольких похожих кейсов."
            ]
        };

        const inferTrendType = (line) => {
            if (line && typeof line === "object" && line.type) {
                return String(line.type);
            }
            const text = String(line || "").toLowerCase();
            if (text.includes("ровн")) return "flat";
            if (text.includes("плавн") || text.includes("рост")) return "rise";
            if (text.includes("ступень")) return "step";
            if (text.includes("игл") || text.includes("всплеск")) return "spike";
            if (text.includes("пил")) return "saw";
            if (text.includes("полк")) return "plateau";
            if (text.includes("паден") || text.includes("снижен")) return "fall";
            return "generic";
        };

        const trendIconSvg = (type) => {
            const stroke = "#64748b";
            const base = '<svg width="26" height="14" viewBox="0 0 26 14" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">';
            const end = "</svg>";
            const grid = '<path d="M1 13H25" stroke="#cbd5e1" stroke-width="1"/>';
            const map = {
                flat: '<path d="M2 8 L24 8" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round"/>',
                rise: '<path d="M2 11 L8 10 L13 8 L18 6 L24 3" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                fall: '<path d="M2 3 L8 5 L13 7 L18 9 L24 11" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                step: '<path d="M2 10 L10 10 L10 7 L18 7 L18 4 L24 4" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                spike: '<path d="M2 10 L8 10 L11 3 L14 10 L24 10" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                saw: '<path d="M2 10 L6 6 L10 10 L14 6 L18 10 L22 6 L24 8" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                plateau: '<path d="M2 10 L8 10 L11 6 L20 6 L24 6" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                "bar-leader": '<rect x="3" y="7" width="4" height="5" rx="1" fill="#94a3b8"/><rect x="10" y="3" width="4" height="9" rx="1" fill="#475569"/><rect x="17" y="8" width="4" height="4" rx="1" fill="#94a3b8"/>',
                "bar-rotate": '<rect x="3" y="6" width="4" height="6" rx="1" fill="#64748b"/><rect x="10" y="8" width="4" height="4" rx="1" fill="#94a3b8"/><rect x="17" y="3" width="4" height="9" rx="1" fill="#334155"/>',
                "bar-spike": '<rect x="3" y="9" width="4" height="3" rx="1" fill="#94a3b8"/><rect x="10" y="2" width="4" height="10" rx="1" fill="#334155"/><rect x="17" y="9" width="4" height="3" rx="1" fill="#94a3b8"/>',
                "bar-uniform": '<rect x="3" y="5" width="4" height="7" rx="1" fill="#64748b"/><rect x="10" y="5" width="4" height="7" rx="1" fill="#64748b"/><rect x="17" y="5" width="4" height="7" rx="1" fill="#64748b"/>',
                "bar-plateau": '<rect x="3" y="8" width="4" height="4" rx="1" fill="#94a3b8"/><rect x="10" y="5" width="4" height="7" rx="1" fill="#475569"/><rect x="17" y="5" width="4" height="7" rx="1" fill="#475569"/>',
                "bar-compare": '<rect x="4" y="7" width="3" height="5" rx="1" fill="#94a3b8"/><rect x="9" y="4" width="3" height="8" rx="1" fill="#334155"/><rect x="16" y="6" width="3" height="6" rx="1" fill="#94a3b8"/><rect x="21" y="3" width="3" height="9" rx="1" fill="#334155"/>',
                generic: '<path d="M2 10 L8 8 L13 9 L18 5 L24 6" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>'
            };
            return `${base}${grid}${map[type] || map.generic}${end}`;
        };

        const renderBlockContent = (content, fallback, opts = {}) => {
            if (Array.isArray(content) && content.length) {
                const items = content.map((line) => {
                    const lineText = line && typeof line === "object" ? line.text : line;
                    if (opts.withTrendIcons) {
                        const icon = trendIconSvg(inferTrendType(line));
                        return `<li><span class="analytics-help-trend-icon">${icon}</span><span>${escapeHtml(lineText)}</span></li>`;
                    }
                    return `<li>${escapeHtml(lineText)}</li>`;
                }).join("");
                return `<ul class="mb-0">${items}</ul>`;
            }
            return `<div>${escapeHtml(content || fallback)}</div>`;
        };

        return `
            <div class="analytics-help-modal-intro mb-2">
                ${sub ? `<div class="small text-muted mb-1">${escapeHtml(sub)}</div>` : ""}
                <div>${escapeHtml(quick)}</div>
            </div>
            <div class="analytics-help-modal-section">
                <div class="analytics-help-modal-subtitle">Как читать график</div>
                ${renderBlockContent(kpiHints[targetId], "Смотрите динамику показателя во времени и сопоставляйте её с соседними графиками.")}
            </div>
            <div class="analytics-help-modal-section">
                <div class="analytics-help-modal-subtitle">Как интерпретировать</div>
                ${renderBlockContent(interpretation[targetId], "Ищите резкие отклонения, длительные аномалии и корреляцию с ошибками/латентностью.")}
            </div>
            <div class="analytics-help-modal-section">
                <div class="analytics-help-modal-subtitle">Тренды и их смысл</div>
                ${renderBlockContent(trendHints[targetId], "Смотрите на форму графика: рост, полка, всплески, ступеньки и периодичность.", {withTrendIcons: true})}
            </div>
            ${antiPatterns[targetId] ? `
            <div class="analytics-help-modal-section">
                <div class="analytics-help-modal-subtitle">Частые ошибки в анализе</div>
                ${renderBlockContent(antiPatterns[targetId], "Избегайте выводов по одной точке и без сравнения равных периодов.")}
            </div>` : ""}
            <div class="analytics-help-modal-section mb-0">
                <div class="analytics-help-modal-subtitle">Что делать дальше</div>
                ${renderBlockContent(nextSteps[targetId], "Переходите в Raw-события, фильтруйте проблемный интервал и анализируйте traceId.")}
            </div>
        `;
    }

    function formatMs(value) {
        return `${toNumber(value).toFixed(2)}`;
    }

    function buildMetricHelpHtml(metricCode, metricName, description, readingGuide) {
        const key = resolveMetricHelpKey(metricCode);
        const shortDescription = (description || "").trim();
        const guide = (readingGuide || "").trim();
        const byCode = metricHelpByCode(key, metricName);
        const renderList = (items) => {
            const safe = Array.isArray(items) ? items.filter(Boolean) : [];
            if (!safe.length) {
                return "";
            }
            return `<ul class="mb-0">${safe.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>`;
        };
        const metricTrendType = (line) => {
            const text = String(line || "").toLowerCase();
            if (text.includes("ровн")) return "flat";
            if (text.includes("плавн") || text.includes("рост")) return "rise";
            if (text.includes("ступень")) return "step";
            if (text.includes("игл") || text.includes("всплеск")) return "spike";
            if (text.includes("пил")) return "saw";
            if (text.includes("полк")) return "plateau";
            if (text.includes("паден") || text.includes("снижен")) return "fall";
            return "generic";
        };
        const metricTrendIconSvg = (type) => {
            const stroke = "#64748b";
            const base = '<svg width="26" height="14" viewBox="0 0 26 14" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">';
            const end = "</svg>";
            const grid = '<path d="M1 13H25" stroke="#cbd5e1" stroke-width="1"/>';
            const map = {
                flat: '<path d="M2 8 L24 8" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round"/>',
                rise: '<path d="M2 11 L8 10 L13 8 L18 6 L24 3" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                fall: '<path d="M2 3 L8 5 L13 7 L18 9 L24 11" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                step: '<path d="M2 10 L10 10 L10 7 L18 7 L18 4 L24 4" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                spike: '<path d="M2 10 L8 10 L11 3 L14 10 L24 10" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                saw: '<path d="M2 10 L6 6 L10 10 L14 6 L18 10 L22 6 L24 8" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                plateau: '<path d="M2 10 L8 10 L11 6 L20 6 L24 6" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
                generic: '<path d="M2 10 L8 8 L13 9 L18 5 L24 6" stroke="' + stroke + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>'
            };
            return `${base}${grid}${map[type] || map.generic}${end}`;
        };
        const renderTrendList = (items) => {
            const safe = Array.isArray(items) ? items.filter(Boolean) : [];
            if (!safe.length) {
                return "";
            }
            return `<ul class="mb-0">${safe.map((item) => `<li><span class="analytics-help-trend-icon">${metricTrendIconSvg(metricTrendType(item))}</span><span>${escapeHtml(item)}</span></li>`).join("")}</ul>`;
        };
        const intro = shortDescription
            || byCode.intro
            || "Метрика помогает понять состояние конкретного технического или пользовательского сценария.";
        const readBlock = byCode.read.length ? byCode.read : [
            "Сначала зафиксируйте одинаковый период и событие, иначе сравнение будет шумным.",
            "Смотрите метрику вместе с count, latency и error rate, а не отдельно."
        ];
        const scenariosBlock = byCode.scenarios.length ? byCode.scenarios : [
            "Если показатель стабилен в обычных окнах, это нормальный фон.",
            "Если показатель растет вместе с P95/error rate, метрика может быть частью причины деградации.",
            "Если есть редкие выбросы (MAX >> P95), анализируйте конкретные trace в Raw."
        ];
        const trendsBlock = byCode.trends.length ? byCode.trends : [
            "Плавный рост: накопительный эффект данных или нагрузки.",
            "Ступенька после релиза: вероятная регрессия после изменения.",
            "Пила: периодический внешний фактор (batch, cron, фоновые процессы)."
        ];
        const antiBlock = byCode.anti.length ? byCode.anti : [
            "Не делайте вывод по одной точке — смотрите устойчивый участок.",
            "Не сравнивайте окна разной длины и разной нагрузки."
        ];
        const nextBlock = byCode.next.length ? byCode.next : [
            "Сузьте анализ до конкретного события и path.",
            "Подтвердите гипотезу в Raw-событиях и trace-логах.",
            "Проверьте результат в сравнении до/после на равных окнах."
        ];

        return `
            <div class="analytics-help-modal-intro mb-2">
                <div>${escapeHtml(intro)}</div>
            </div>
            <div class="analytics-help-modal-section">
                <div class="analytics-help-modal-subtitle">Как читать метрику</div>
                ${renderList(readBlock)}
            </div>
            <div class="analytics-help-modal-section">
                <div class="analytics-help-modal-subtitle">Сценарии и выводы</div>
                ${renderList(scenariosBlock)}
            </div>
            <div class="analytics-help-modal-section">
                <div class="analytics-help-modal-subtitle">Тренды и что они означают</div>
                ${renderTrendList(trendsBlock)}
            </div>
            <div class="analytics-help-modal-section">
                <div class="analytics-help-modal-subtitle">Частые ошибки в интерпретации</div>
                ${renderList(antiBlock)}
            </div>
            <div class="analytics-help-modal-section mb-0">
                <div class="analytics-help-modal-subtitle">Что делать дальше</div>
                ${renderList(nextBlock)}
                ${guide ? `<div class="small text-muted mt-2">${escapeHtml(guide)}</div>` : ""}
            </div>
        `;
    }

    function resolveMetricHelpKey(metricCode) {
        const code = String(metricCode || "").toUpperCase();
        if (code.includes("DB_QUERY")) return "DB_QUERY_COUNT";
        if (code.includes("RESPONSE_SIZE")) return "RESPONSE_SIZE_BYTES";
        if (code.includes("ITEM_COUNT")) return "ITEM_COUNT";
        if (code.includes("PAGE_URL")) return "PAGE_URL";
        if (code.includes("PAYLOAD_SIZE")) return "PAYLOAD_SIZE_BYTES";
        if (code.includes("NAV") && code.includes("TYPE")) return "NAVIGATION_TYPE";
        if (code.includes("DOM_CONTENT_LOADED")) return "DOM_CONTENT_LOADED_MS";
        if (code.includes("TTFB")) return "FRONTEND_TTFB_MS";
        if (code === "LCP" || code.includes("LCP_")) return "LCP_MS";
        if (code.includes("DOM_INTERACTIVE")) return "DOM_INTERACTIVE_MS";
        if (code.includes("LOAD_EVENT")) return "LOAD_EVENT_MS";
        if (code.includes("TRANSFER_SIZE")) return "TRANSFER_SIZE_BYTES";
        if (code === "INP" || code.includes("INP_")) return "INP_MS";
        if (code.includes("ERROR_CODE")) return "ERROR_CODE";
        if (code.includes("API_DURATION")) return "API_DURATION_MS";
        if (code.includes("API_METHOD")) return "API_METHOD";
        if (code.includes("API_URL")) return "API_URL";
        if (code.includes("HTTP_STATUS")) return "HTTP_STATUS";
        return code || "GENERIC";
    }

    function metricHelpByCode(key, metricName) {
        const common = {
            intro: "",
            read: [],
            scenarios: [],
            trends: [],
            anti: [],
            next: []
        };
        const map = {
            DB_QUERY_COUNT: {
                intro: "Показывает, сколько SQL-запросов выполняется для одного пользовательского сценария. Рост обычно указывает на лишние обращения к базе.",
                read: [
                    "Сравнивайте метрику на одном и том же экране/endpoint и одинаковом событии.",
                    "Смотрите вместе с размером ответа, количеством элементов и latency слоя DATABASE.",
                    "Лучше анализировать окна 15–60 минут и сравнивать с похожим окном прошлого периода."
                ],
                scenarios: [
                    "AVG и P95 около 1 и стабильны: доступ к базе предсказуемый, лишних запросов не видно.",
                    "AVG и P95 растут вместе: запросов к БД стало больше, вероятны лишние чтения.",
                    "AVG стабильный, P95/MAX растут: проблема нерегулярная, часть запросов уходит в тяжелый путь.",
                    "Count низкий, но SQL резко высокий: редкий сценарий может быть неэффективным и потенциально критичным."
                ],
                trends: [
                    "Плавный рост: накопление данных или изменение выборки.",
                    "Ступенька после релиза: вероятный регресс в репозитории/fetch-плане.",
                    "Пила: периодические batch/кеш-промахи/фоновые задачи."
                ],
                anti: [
                    "Ошибка: сравнивать разные события между собой и делать вывод о SQL.",
                    "Ошибка: смотреть только среднее и игнорировать хвосты P95/P99/MAX."
                ],
                next: [
                    "Отфильтруйте проблемный path и событие в Raw, найдите повторяющиеся trace.",
                    "Проверьте запросы и fetch-план на N+1 и лишние загрузки.",
                    "После правки перепроверьте метрику в режиме до/после."
                ]
            },
            RESPONSE_SIZE_BYTES: {
                intro: "Размер ответа показывает, сколько данных система отдает пользователю или клиенту. Большие ответы часто тянут latency вверх.",
                read: [
                    "Сравнивайте размер ответа с количеством элементов и длительностью API/DB этапов.",
                    "Смотрите не только AVG, но и P95/MAX — они показывают тяжелые кейсы."
                ],
                scenarios: [
                    "Размер ответа растет вместе с latency: вероятно, проблема в объеме данных.",
                    "Размер ответа растет, latency стабильна: пока есть запас по производительности.",
                    "MAX сильно выше P95: редкие аномально большие ответы."
                ],
                trends: ["Плавный рост: расширение контента/полей.", "Ступенька: изменение контракта ответа.", "Иглы: редкие тяжелые выборки."],
                anti: ["Ошибка: считать большой ответ проблемой без роста latency/error.", "Ошибка: не учитывать, что часть роста может быть бизнес-нормой."],
                next: ["Проверить состав payload.", "Ограничить поля/пагинацию.", "Сверить до/после на одинаковом окне."]
            },
            ITEM_COUNT: {
                intro: "Количество элементов помогает понять, насколько «тяжелые» наборы данных обрабатываются в конкретном сценарии.",
                read: ["Смотрите в связке с RESPONSE_SIZE_BYTES и latency.", "Сравнивайте одинаковые события и path."],
                scenarios: ["Рост элементов и рост latency: система не держит объем.", "Рост элементов без роста latency: текущий запас достаточный."],
                trends: ["Плавный рост: сезонный/бизнесовый фактор.", "Иглы: редкие перегруженные страницы."],
                anti: ["Ошибка: делать вывод по одному пику.", "Ошибка: игнорировать фильтры по атрибутам/segment."],
                next: ["Проверить пагинацию и лимиты.", "Сузить выборки.", "Сравнить до/после изменений."]
            },
            DOM_CONTENT_LOADED_MS: metricFrontendTimingHelp("DOM Content Loaded"),
            FRONTEND_TTFB_MS: metricFrontendTimingHelp("TTFB"),
            LCP_MS: metricFrontendTimingHelp("LCP"),
            DOM_INTERACTIVE_MS: metricFrontendTimingHelp("DOM Interactive"),
            LOAD_EVENT_MS: metricFrontendTimingHelp("Load Event"),
            INP_MS: metricFrontendTimingHelp("INP"),
            TRANSFER_SIZE_BYTES: {
                intro: "Transfer Size показывает фактический сетевой объем передачи. Полезно для диагностики сетевой тяжести страницы.",
                read: ["Сравнивайте с RESPONSE_SIZE_BYTES и frontend latency метриками.", "Смотрите P95/MAX, а не только среднее."],
                scenarios: ["Рост transfer size + рост TTFB/LCP: сеть стала bottleneck.", "Transfer size стабилен, latency выросла: проблема не в объеме сети."],
                trends: ["Ступенька: изменение статических ассетов/контента.", "Пила: кэш то срабатывает, то нет."],
                anti: ["Ошибка: анализировать без учета кэша и CDN.", "Ошибка: не разделять мобильный/desktop трафик."],
                next: ["Проверить сжатие и кэширование.", "Проверить тяжелые ресурсы.", "Сверить по сегментам клиентов."]
            },
            ERROR_CODE: {
                intro: "Код ошибки показывает, какие типы сбоев доминируют в проблемном интервале.",
                read: ["Смотрите распределение кодов вместе с error rate и временем возникновения.", "Сравнивайте коды между событиями и path."],
                scenarios: ["Один код резко доминирует: есть единая первопричина.", "Кодов много и равномерно: проблема размыта или комплексная."],
                trends: ["Появился новый код после релиза: вероятный регресс.", "Долгая полка одного кода: устойчивый дефект."],
                anti: ["Ошибка: смотреть только текст ошибки без trace/path.", "Ошибка: игнорировать частоту повторения кода."],
                next: ["Отфильтровать код в Raw.", "Собрать top trace/path.", "Проверить изменения вокруг времени появления."]
            },
            API_DURATION_MS: {
                intro: "Показывает длительность API-вызова в целевом сценарии. Это прямая метрика пользовательского ожидания ответа.",
                read: ["Смотрите вместе с DB_QUERY_COUNT, RESPONSE_SIZE_BYTES и stage latency.", "Ориентируйтесь на P95 как основную рабочую метрику."],
                scenarios: ["Растет API duration и DB metrics: узкое место в backend обработке.", "API duration растет без DB роста: возможно внешний сервис/логика."],
                trends: ["Плавный рост: накопительный эффект нагрузки.", "Иглы: редкие долгие запросы."],
                anti: ["Ошибка: делать вывод по AVG и игнорировать P95/P99.", "Ошибка: сравнивать окна разной нагрузки."],
                next: ["Локализовать по событию/path.", "Проверить этапы выполнения.", "Подтвердить эффект через до/после."]
            },
            API_METHOD: {
                intro: "Метрика API Method помогает понять, какие HTTP-методы несут основную нагрузку и ошибки.",
                read: ["Сравнивайте долю GET/POST/... в проблемном интервале.", "Сопоставляйте с error rate и API duration."],
                scenarios: ["Ошибки сосредоточены в POST: вероятен дефект записи/валидации.", "Тяжелые ответы у GET: возможно перегруженные чтения."],
                trends: ["Смена доминирующего метода: изменение пользовательского поведения или API-потока."],
                anti: ["Ошибка: оценивать метод без учета endpoint.", "Ошибка: не смотреть разрез по событиям."],
                next: ["Сузить анализ до метода + path.", "Проверить контракты и валидацию.", "Сверить до/после."]
            },
            API_URL: {
                intro: "API URL показывает, какие endpoint дают основной вклад в нагрузку, задержку или ошибки.",
                read: ["Смотрите top URL в проблемный интервал.", "Сравнивайте URL с event type и error class."],
                scenarios: ["Один URL доминирует по ошибкам: локальный дефект endpoint.", "URL доминирует по latency: кандидат на оптимизацию."],
                trends: ["Новый URL в топе после релиза: изменение маршрутизации или функционала."],
                anti: ["Ошибка: смешивать URL с разной бизнес-ролью в один вывод."],
                next: ["Фильтровать Raw по URL.", "Проверить trace по endpoint.", "Оптимизировать целевой маршрут."]
            },
            HTTP_STATUS: {
                intro: "HTTP Status отражает сетевой и прикладной результат запросов. Позволяет быстро понять характер сбоев.",
                read: ["Смотрите доли 2xx/4xx/5xx вместе с error rate.", "Сравнивайте по событиям и path."],
                scenarios: ["Рост 5xx: серверная проблема.", "Рост 4xx: валидация/контракты/клиентские ошибки.", "Много 499/таймаутных статусов: обрывы/долгие ответы."],
                trends: ["Ступенька по 5xx: часто после релиза или инфраструктурного события."],
                anti: ["Ошибка: считать все 4xx одинаково критичными.", "Ошибка: не анализировать конкретные коды отдельно."],
                next: ["Разбить по конкретным статусам.", "Проверить связанные trace.", "Построить до/после по проблемным кодам."]
            }
        };
        const raw = map[key] || common;
        if (!raw.intro) {
            raw.intro = `Метрика ${metricName || key} помогает анализировать поведение системы в выбранном периоде.`;
        }
        return raw;
    }

    function metricFrontendTimingHelp(metricLabel) {
        return {
            intro: `${metricLabel} показывает скорость клиентского этапа загрузки/интеракции. Полезно для оценки реального пользовательского опыта.`,
            read: [
                "Сравнивайте метрику в одном и том же пользовательском сценарии и на равных окнах времени.",
                "Смотрите вместе с transfer size, API duration и общим latency."
            ],
            scenarios: [
                `${metricLabel} растет вместе с transfer size: вероятно тяжелый контент/сеть.`,
                `${metricLabel} растет при стабильном transfer size: проблема в клиентской обработке или backend ожидании.`,
                `Редкие высокие выбросы по P99: сложные условия на части устройств/сетей.`
            ],
            trends: [
                "Плавный рост: постепенная деградация UX.",
                "Ступенька после релиза: вероятный регресс фронтенда или контракта API.",
                "Пила: нестабильная сеть/кэш/фоновые процессы."
            ],
            anti: [
                "Ошибка: сравнивать мобильный и desktop трафик как одно целое.",
                "Ошибка: делать вывод по AVG без проверки P95/P99."
            ],
            next: [
                "Сегментировать по событиям и path.",
                "Проверить тяжелые ресурсы/ответы.",
                "Сверить до/после после оптимизации."
            ]
        };
    }

    function formatMetric(value, unit) {
        const formatted = toNumber(value).toFixed(2);
        const localizedUnit = localizeUnit(unit);
        return localizedUnit ? `${formatted} ${localizedUnit}` : formatted;
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
                                            title="Развернуть сообщение"
                                            aria-label="Развернуть сообщение">
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
        return {
            ...baseChartOptions(),
            scales: {
                x: {
                    grid: { color: "rgba(148,163,184,0.12)" },
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
                ...baseChartOptions().plugins,
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

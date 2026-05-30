(function (global) {
    "use strict";

    if (global.AnalyticsWeb) {
        return;
    }

    const DEFAULT_ENDPOINT = "/api/analytics/frontend/ingest";
    const DEFAULT_BATCH_SIZE = 20;
    const DEFAULT_FLUSH_INTERVAL_MS = 3000;
    const MAX_TEXT_LENGTH = 2000;
    const SKIP_HEADER = "X-Analytics-Frontend";

    let config = null;
    let initialized = false;
    let queue = [];
    let flushTimer = 0;
    let fetchWrapped = false;
    let xhrWrapped = false;
    let latestTraceId = "";
    let currentEventUid = "";
    let lcpValue = 0;
    let inpValue = 0;
    let clsValue = 0;
    let vitalsSent = false;

    function nowMs() {
        if (global.performance && typeof global.performance.now === "function") {
            return global.performance.now();
        }
        return Date.now();
    }

    function toNumber(value) {
        if (typeof value === "number" && Number.isFinite(value)) {
            return value;
        }
        return null;
    }

    function trimText(value) {
        if (value == null) {
            return null;
        }
        const text = String(value).trim();
        if (!text) {
            return null;
        }
        return text.length > MAX_TEXT_LENGTH ? text.slice(0, MAX_TEXT_LENGTH) : text;
    }

    function normalizeModuleCode(code) {
        const value = trimText(code);
        if (!value) {
            return null;
        }
        return value.toUpperCase().replace(/-/g, "_");
    }

    function normalizeEventUid(value) {
        const text = trimText(value);
        if (!text) {
            return "";
        }
        const normalized = text.toLowerCase();
        return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(normalized)
            ? normalized
            : "";
    }

    function readMetaEventUid() {
        const meta = global.document.querySelector("meta[name='analytics_event_uid']");
        const content = meta ? meta.getAttribute("content") : null;
        return normalizeEventUid(content);
    }

    function readMetaTraceId() {
        const meta = global.document.querySelector("meta[name='analytics_trace_id']");
        const content = meta ? meta.getAttribute("content") : null;
        return trimText(content) || "";
    }

    function resolveModuleCode(pathname) {
        if (config && typeof config.moduleResolver === "function") {
            try {
                const custom = normalizeModuleCode(config.moduleResolver(pathname));
                if (custom) {
                    return custom;
                }
            } catch (_e) {
                // keep fallback
            }
        }
        const path = trimText(pathname || global.location.pathname || "") || "";
        const lower = path.toLowerCase();
        if (lower.startsWith("/admin")) {
            return "ADMIN";
        }
        if (lower.startsWith("/analytics")) {
            return "ANALYTICS";
        }
        return "SHOP";
    }

    function shouldSkipByPath(pathname) {
        const path = (pathname || "").toLowerCase();
        const ignored = config && Array.isArray(config.ignorePathPrefixes)
            ? config.ignorePathPrefixes
            : ["/analytics", "/analytics-admin"];
        return ignored.some((prefix) => path.startsWith(String(prefix).toLowerCase()));
    }

    function csrfHeaders() {
        const tokenMeta = global.document.querySelector("meta[name='_csrf']");
        const headerMeta = global.document.querySelector("meta[name='_csrf_header']");
        const token = tokenMeta ? tokenMeta.getAttribute("content") : null;
        const headerName = headerMeta ? headerMeta.getAttribute("content") : null;
        if (!token || !headerName) {
            return {};
        }
        return {[headerName]: token};
    }

    function enqueueEvent(event) {
        if (!event || !event.code) {
            return;
        }
        queue.push(event);
        if (queue.length >= (config.batchSize || DEFAULT_BATCH_SIZE)) {
            flushQueue();
            return;
        }
        scheduleFlush();
    }

    function scheduleFlush() {
        if (flushTimer) {
            return;
        }
        flushTimer = global.setTimeout(() => {
            flushTimer = 0;
            flushQueue();
        }, config.flushIntervalMs || DEFAULT_FLUSH_INTERVAL_MS);
    }

    function flushQueue() {
        if (!queue.length) {
            return;
        }
        const payload = {events: queue.slice(0, config.batchSize || DEFAULT_BATCH_SIZE)};
        queue = queue.slice(payload.events.length);
        sendPayload(payload).finally(() => {
            if (queue.length) {
                scheduleFlush();
            }
        });
    }

    function sendPayload(payload) {
        const headers = Object.assign({
            "Content-Type": "application/json",
            "X-Requested-With": "XMLHttpRequest",
            [SKIP_HEADER]: "1"
        }, csrfHeaders());
        return global.fetch(config.endpoint || DEFAULT_ENDPOINT, {
            method: "POST",
            headers,
            credentials: "same-origin",
            keepalive: true,
            body: JSON.stringify(payload)
        }).catch(() => {
            // swallow analytics transport error
        });
    }

    function buildEvent(code, options) {
        const opts = options || {};
        const pagePath = trimText(opts.pagePath || global.location.pathname || "/");
        const parentEventUid = normalizeEventUid(opts.parentEventUid || currentEventUid);
        return {
            code,
            parentEventUid: parentEventUid || undefined,
            moduleCode: normalizeModuleCode(opts.moduleCode || resolveModuleCode(pagePath)),
            pagePath,
            requestPath: trimText(opts.requestPath || pagePath),
            httpMethod: trimText(opts.httpMethod || "GET"),
            traceId: trimText(opts.traceId || latestTraceId),
            statusCode: typeof opts.statusCode === "number" ? opts.statusCode : undefined,
            error: Boolean(opts.error),
            errorMessage: trimText(opts.errorMessage || null),
            metricsNum: opts.metricsNum || {},
            metricsText: opts.metricsText || {}
        };
    }

    function collectPageLoadMetrics() {
        const navEntries = global.performance && typeof global.performance.getEntriesByType === "function"
            ? global.performance.getEntriesByType("navigation")
            : [];
        const nav = navEntries && navEntries.length ? navEntries[0] : null;
        if (!nav) {
            return;
        }
        const metricsNum = {};
        const metricsText = {
            FRONTEND_PAGE_URL: trimText(global.location.pathname + global.location.search) || "/",
            FRONTEND_NAV_TYPE: trimText(nav.type) || "navigate"
        };
        const ttfb = toNumber(nav.responseStart);
        const domInteractive = toNumber(nav.domInteractive);
        const domContentLoaded = toNumber(nav.domContentLoadedEventEnd);
        const loadEvent = toNumber(nav.loadEventEnd);
        const transferSize = toNumber(nav.transferSize);
        if (ttfb != null) metricsNum.FRONTEND_TTFB_MS = Number(ttfb.toFixed(2));
        if (domInteractive != null) metricsNum.FRONTEND_DOM_INTERACTIVE_MS = Number(domInteractive.toFixed(2));
        if (domContentLoaded != null) metricsNum.FRONTEND_DOM_CONTENT_LOADED_MS = Number(domContentLoaded.toFixed(2));
        if (loadEvent != null) metricsNum.FRONTEND_LOAD_EVENT_MS = Number(loadEvent.toFixed(2));
        if (transferSize != null) metricsNum.FRONTEND_TRANSFER_SIZE_BYTES = Number(transferSize.toFixed(0));

        enqueueEvent(buildEvent("FRONTEND_PAGE_LOAD", {
            metricsNum,
            metricsText,
            statusCode: 200
        }));
    }

    function setupWebVitalsObservers() {
        if (!("PerformanceObserver" in global)) {
            return;
        }
        try {
            const lcpObserver = new PerformanceObserver((entryList) => {
                const entries = entryList.getEntries();
                for (const entry of entries) {
                    const value = toNumber(entry.startTime);
                    if (value != null && value > lcpValue) {
                        lcpValue = value;
                    }
                }
            });
            lcpObserver.observe({type: "largest-contentful-paint", buffered: true});
        } catch (_e) {
            // ignore browser incompatibility
        }
        try {
            const clsObserver = new PerformanceObserver((entryList) => {
                const entries = entryList.getEntries();
                for (const entry of entries) {
                    if (!entry.hadRecentInput) {
                        clsValue += Number(entry.value || 0);
                    }
                }
            });
            clsObserver.observe({type: "layout-shift", buffered: true});
        } catch (_e) {
            // ignore browser incompatibility
        }
        try {
            const inpObserver = new PerformanceObserver((entryList) => {
                const entries = entryList.getEntries();
                for (const entry of entries) {
                    const value = toNumber(entry.duration);
                    if (value != null && value > inpValue) {
                        inpValue = value;
                    }
                }
            });
            inpObserver.observe({type: "event", buffered: true, durationThreshold: 16});
        } catch (_e) {
            // ignore browser incompatibility
        }
    }

    function sendWebVitalsOnce() {
        if (vitalsSent) {
            return;
        }
        vitalsSent = true;
        const metricsNum = {};
        if (lcpValue > 0) metricsNum.FRONTEND_LCP_MS = Number(lcpValue.toFixed(2));
        if (inpValue > 0) metricsNum.FRONTEND_INP_MS = Number(inpValue.toFixed(2));
        if (clsValue > 0) metricsNum.FRONTEND_CLS_SCORE = Number(clsValue.toFixed(4));
        if (!Object.keys(metricsNum).length) {
            return;
        }
        enqueueEvent(buildEvent("FRONTEND_WEB_VITALS", {
            metricsNum,
            metricsText: {
                FRONTEND_PAGE_URL: trimText(global.location.pathname + global.location.search) || "/"
            },
            statusCode: 200
        }));
        flushQueue();
    }

    function trackJsError(kind, message, stack) {
        enqueueEvent(buildEvent("FRONTEND_JS_ERROR", {
            statusCode: 500,
            error: true,
            errorMessage: trimText(message) || "JavaScript error",
            metricsText: {
                FRONTEND_ERROR_MESSAGE: trimText(message),
                FRONTEND_NETWORK_ERROR: trimText(kind),
                FRONTEND_CUSTOM_ATTRS_JSON: trimText(stack)
            }
        }));
    }

    function shouldSkipApiTrack(url, init) {
        const text = (url || "").toString().toLowerCase();
        if (!text) {
            return true;
        }
        if (text.includes("/api/analytics/frontend/ingest")) {
            return true;
        }
        const headers = init && init.headers;
        if (headers && typeof headers === "object") {
            if (headers[SKIP_HEADER] || headers[SKIP_HEADER.toLowerCase()]) {
                return true;
            }
            if (typeof headers.get === "function" && headers.get(SKIP_HEADER)) {
                return true;
            }
        }
        return false;
    }

    function resolveRequestMethod(init, fallback) {
        if (init && typeof init.method === "string" && init.method.trim()) {
            return init.method.trim().toUpperCase();
        }
        return (fallback || "GET").toUpperCase();
    }

    function wrapFetch() {
        if (fetchWrapped || typeof global.fetch !== "function") {
            return;
        }
        fetchWrapped = true;
        const originalFetch = global.fetch.bind(global);
        global.fetch = async function wrappedFetch(input, init) {
            const requestUrl = typeof input === "string" ? input : (input && input.url ? input.url : "");
            const method = resolveRequestMethod(init, input && input.method);
            const startedAt = nowMs();
            try {
                const response = await originalFetch(input, init);
                const finishedAt = nowMs();
                const traceId = response && response.headers && typeof response.headers.get === "function"
                    ? trimText(response.headers.get("X-Trace-Id"))
                    : null;
                const responseEventUid = response && response.headers && typeof response.headers.get === "function"
                    ? normalizeEventUid(response.headers.get("X-Analytics-Event-Uid"))
                    : "";
                if (traceId) {
                    latestTraceId = traceId;
                }
                if (responseEventUid) {
                    currentEventUid = responseEventUid;
                }
                if (!shouldSkipApiTrack(requestUrl, init)) {
                    const afterResponse = nowMs();
                    global.requestAnimationFrame(() => {
                        global.requestAnimationFrame(() => {
                            const renderedAt = nowMs();
                            const metricsNum = {
                                FRONTEND_API_DURATION_MS: Number((finishedAt - startedAt).toFixed(2)),
                                FRONTEND_RENDER_AFTER_API_MS: Number((renderedAt - afterResponse).toFixed(2)),
                                FRONTEND_HTTP_STATUS: response.status
                            };
                            const metricsText = {
                                FRONTEND_API_URL: trimText(requestUrl),
                                FRONTEND_API_METHOD: method,
                                FRONTEND_TRACE_ID: traceId
                            };
                            enqueueEvent(buildEvent("FRONTEND_API_CALL", {
                                requestPath: trimText(global.location.pathname),
                                httpMethod: method,
                                parentEventUid: responseEventUid || currentEventUid,
                                traceId,
                                statusCode: response.status,
                                error: response.status >= 400,
                                errorMessage: response.status >= 400 ? "HTTP " + response.status : null,
                                metricsNum,
                                metricsText
                            }));
                        });
                    });
                }
                return response;
            } catch (error) {
                const finishedAt = nowMs();
                if (!shouldSkipApiTrack(requestUrl, init)) {
                    enqueueEvent(buildEvent("FRONTEND_API_CALL", {
                        requestPath: trimText(global.location.pathname),
                        httpMethod: method,
                        parentEventUid: currentEventUid,
                        statusCode: 0,
                        error: true,
                        errorMessage: trimText(error && error.message) || "Network error",
                        metricsNum: {
                            FRONTEND_API_DURATION_MS: Number((finishedAt - startedAt).toFixed(2)),
                            FRONTEND_HTTP_STATUS: 0
                        },
                        metricsText: {
                            FRONTEND_API_URL: trimText(requestUrl),
                            FRONTEND_API_METHOD: method,
                            FRONTEND_NETWORK_ERROR: trimText(error && error.name)
                        }
                    }));
                }
                throw error;
            }
        };
    }

    function wrapXhr() {
        if (xhrWrapped || !global.XMLHttpRequest) {
            return;
        }
        xhrWrapped = true;
        const originalOpen = global.XMLHttpRequest.prototype.open;
        const originalSend = global.XMLHttpRequest.prototype.send;

        global.XMLHttpRequest.prototype.open = function (method, url) {
            this.__analyticsMethod = method;
            this.__analyticsUrl = url;
            return originalOpen.apply(this, arguments);
        };

        global.XMLHttpRequest.prototype.send = function () {
            if (shouldSkipApiTrack(this.__analyticsUrl, {headers: {}})) {
                return originalSend.apply(this, arguments);
            }
            const startedAt = nowMs();
            this.addEventListener("loadend", () => {
                const finishedAt = nowMs();
                enqueueEvent(buildEvent("FRONTEND_API_CALL", {
                    requestPath: trimText(global.location.pathname),
                    httpMethod: resolveRequestMethod({method: this.__analyticsMethod}, "GET"),
                    statusCode: Number(this.status || 0),
                    error: Number(this.status || 0) >= 400 || Number(this.status || 0) === 0,
                    errorMessage: Number(this.status || 0) >= 400 ? "HTTP " + this.status : null,
                    metricsNum: {
                        FRONTEND_API_DURATION_MS: Number((finishedAt - startedAt).toFixed(2)),
                        FRONTEND_HTTP_STATUS: Number(this.status || 0)
                    },
                    metricsText: {
                        FRONTEND_API_URL: trimText(this.__analyticsUrl),
                        FRONTEND_API_METHOD: trimText(this.__analyticsMethod)
                    }
                }));
            });
            return originalSend.apply(this, arguments);
        };
    }

    function setupDeclarativeDomTracking() {
        global.document.addEventListener("click", (event) => {
            const target = event.target instanceof Element
                ? event.target.closest("[data-analytics-event]")
                : null;
            if (!target) {
                return;
            }
            const code = trimText(target.getAttribute("data-analytics-event"));
            if (!code) {
                return;
            }
            const attrs = {};
            for (const attr of target.attributes) {
                if (!attr || !attr.name || !attr.name.startsWith("data-analytics-attr-")) {
                    continue;
                }
                const key = attr.name.replace("data-analytics-attr-", "");
                if (!key) {
                    continue;
                }
                attrs[key] = attr.value;
            }
            enqueueEvent(buildEvent(code.toUpperCase().replace(/-/g, "_"), {
                requestPath: trimText(global.location.pathname),
                httpMethod: "GET",
                statusCode: 200,
                metricsText: {
                    FRONTEND_CUSTOM_ATTRS_JSON: Object.keys(attrs).length ? JSON.stringify(attrs) : null
                }
            }));
        }, {passive: true});
    }

    function init(userConfig) {
        if (initialized) {
            return;
        }
        initialized = true;
        currentEventUid = readMetaEventUid() || "";
        latestTraceId = readMetaTraceId() || "";
        config = Object.assign({
            endpoint: DEFAULT_ENDPOINT,
            batchSize: DEFAULT_BATCH_SIZE,
            flushIntervalMs: DEFAULT_FLUSH_INTERVAL_MS,
            trackPageLoad: true,
            trackVitals: true,
            trackApi: true,
            trackErrors: true,
            trackDomEvents: true,
            ignorePathPrefixes: ["/analytics", "/analytics-admin"]
        }, userConfig || {});

        if (shouldSkipByPath(global.location.pathname || "")) {
            return;
        }

        if (config.trackApi) {
            wrapFetch();
            wrapXhr();
        }

        if (config.trackErrors) {
            global.addEventListener("error", (event) => {
                const message = event && event.message ? event.message : "JavaScript error";
                const stack = event && event.error && event.error.stack ? event.error.stack : null;
                trackJsError("window.onerror", message, stack);
            });
            global.addEventListener("unhandledrejection", (event) => {
                const reason = event && event.reason ? event.reason : null;
                const message = reason && reason.message ? reason.message : String(reason || "Promise rejection");
                const stack = reason && reason.stack ? reason.stack : null;
                trackJsError("unhandledrejection", message, stack);
            });
        }

        if (config.trackPageLoad) {
            if (global.document.readyState === "complete") {
                global.setTimeout(collectPageLoadMetrics, 0);
            } else {
                global.addEventListener("load", collectPageLoadMetrics, {once: true});
            }
        }

        if (config.trackVitals) {
            setupWebVitalsObservers();
            global.addEventListener("visibilitychange", () => {
                if (global.document.visibilityState === "hidden") {
                    sendWebVitalsOnce();
                }
            });
            global.addEventListener("pagehide", sendWebVitalsOnce, {once: true});
        }

        if (config.trackDomEvents) {
            setupDeclarativeDomTracking();
        }
    }

    global.AnalyticsWeb = {
        init,
        flush: flushQueue,
        enqueue: enqueueEvent
    };
})(window);

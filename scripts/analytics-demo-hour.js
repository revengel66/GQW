import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const ADMIN_USERNAME = __ENV.ADMIN_USERNAME || "admin";
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || "admin";

const DURATION_BASELINE = __ENV.BASELINE_DURATION || "5m";
const DURATION_SPIKE = __ENV.SPIKE_DURATION || "5m";

const reqDuration = new Trend("shop_request_duration_ms", true);
const status2xx = new Counter("shop_status_2xx_count");
const status4xx = new Counter("shop_status_4xx_count");
const status5xx = new Counter("shop_status_5xx_count");
const businessErrors = new Counter("shop_business_errors_count");
const checkoutSuccessCount = new Counter("shop_checkout_success_count");

const vuState = {
    csrfToken: null,
    csrfHeader: "X-CSRF-TOKEN",
    loggedIn: false
};

export const options = {
    // Defaults are chosen to keep total concurrent arrival rate <= 50 req/s.
    scenarios: {
        browse_baseline: {
            executor: "constant-arrival-rate",
            exec: "baselineScenario",
            rate: Number(__ENV.BASELINE_RPS || "4"),
            timeUnit: "1s",
            duration: DURATION_BASELINE,
            preAllocatedVUs: Number(__ENV.BASELINE_VUS || "15"),
            maxVUs: Number(__ENV.BASELINE_MAX_VUS || "50")
        },
        browse_spike: {
            executor: "ramping-arrival-rate",
            exec: "spikeScenario",
            startTime: DURATION_BASELINE,
            startRate: Number(__ENV.SPIKE_START_RPS || "6"),
            timeUnit: "1s",
            preAllocatedVUs: Number(__ENV.SPIKE_VUS || "30"),
            maxVUs: Number(__ENV.SPIKE_MAX_VUS || "120"),
            stages: [
                { target: Number(__ENV.SPIKE_PEAK_RPS || "14"), duration: __ENV.SPIKE_RAMP_UP || "100s" },
                { target: Number(__ENV.SPIKE_PEAK_RPS || "14"), duration: __ENV.SPIKE_HOLD || "100s" },
                { target: Number(__ENV.SPIKE_END_RPS || "8"), duration: __ENV.SPIKE_RAMP_DOWN || "100s" }
            ]
        },
        hotspot_tail: {
            executor: "constant-arrival-rate",
            exec: "hotspotScenario",
            startTime: DURATION_BASELINE,
            rate: Number(__ENV.HOTSPOT_RPS || "4"),
            timeUnit: "1s",
            duration: DURATION_SPIKE,
            preAllocatedVUs: Number(__ENV.HOTSPOT_VUS || "20"),
            maxVUs: Number(__ENV.HOTSPOT_MAX_VUS || "80")
        },
        business_mix: {
            executor: "ramping-arrival-rate",
            exec: "businessMixScenario",
            startTime: addDurations(DURATION_BASELINE, DURATION_SPIKE),
            startRate: Number(__ENV.MIX_START_RPS || "9"),
            timeUnit: "1s",
            // Post-spike wave pattern for a 20-minute tail (total test ~=30m):
            // alternating lower/higher hills with varied widths.
            stages: [
                { target: Number(__ENV.MIX_WAVE_1_RPS || "9"), duration: __ENV.MIX_WAVE_1_DUR || "83s" },
                { target: Number(__ENV.MIX_WAVE_2_RPS || "12"), duration: __ENV.MIX_WAVE_2_DUR || "137s" },
                { target: Number(__ENV.MIX_WAVE_3_RPS || "10"), duration: __ENV.MIX_WAVE_3_DUR || "94s" },
                { target: Number(__ENV.MIX_WAVE_4_RPS || "14"), duration: __ENV.MIX_WAVE_4_DUR || "126s" },
                { target: Number(__ENV.MIX_WAVE_5_RPS || "11"), duration: __ENV.MIX_WAVE_5_DUR || "71s" },
                { target: Number(__ENV.MIX_WAVE_6_RPS || "13"), duration: __ENV.MIX_WAVE_6_DUR || "159s" },
                { target: Number(__ENV.MIX_WAVE_7_RPS || "9"), duration: __ENV.MIX_WAVE_7_DUR || "88s" },
                { target: Number(__ENV.MIX_WAVE_8_RPS || "15"), duration: __ENV.MIX_WAVE_8_DUR || "142s" },
                { target: Number(__ENV.MIX_WAVE_9_RPS || "10"), duration: __ENV.MIX_WAVE_9_DUR || "96s" },
                { target: Number(__ENV.MIX_WAVE_10_RPS || "12"), duration: __ENV.MIX_WAVE_10_DUR || "124s" },
                { target: Number(__ENV.MIX_WAVE_11_RPS || "9"), duration: __ENV.MIX_WAVE_11_DUR || "80s" }
            ],
            preAllocatedVUs: Number(__ENV.MIX_VUS || "30"),
            maxVUs: Number(__ENV.MIX_MAX_VUS || "110")
        }
    },
    thresholds: {
        "http_req_duration{phase:baseline}": ["p(95)<2500"],
        "http_req_duration{phase:spike}": ["p(95)<6500"],
        "http_req_duration{phase:mix}": ["p(95)<3000"],
        "http_req_failed{phase:mix}": ["rate<0.6"]
    }
};

export function setup() {
    const paths = discoverProductPaths();
    const products = buildProductCatalog(paths);
    const productPaths = products.map((it) => it.path);
    const availableProducts = products.filter((it) => it.inStock);
    const unavailableProducts = products.filter((it) => !it.inStock);
    const hotProduct = availableProducts.length > 0 ? availableProducts[0] : (products.length > 0 ? products[0] : null);

    return {
        products,
        availableProducts,
        unavailableProducts,
        productPaths,
        hotProduct
    };
}

export function baselineScenario(data) {
    if (Math.random() < 0.7) {
        const path = pickRandomPath(data.productPaths);
        viewProduct(path, "baseline");
    } else {
        viewCatalog("baseline");
    }
    sleep(randomBetween(0.04, 0.2));
}

export function spikeScenario(data) {
    if (Math.random() < 0.85) {
        const path = pickRandomPath(data.productPaths);
        viewProduct(path, "spike");
    } else {
        viewCatalog("spike");
    }
    sleep(randomBetween(0.01, 0.08));
}

export function hotspotScenario(data) {
    if (!data.hotProduct) {
        return;
    }
    viewProduct(data.hotProduct.path, "spike-hot");
    sleep(randomBetween(0.01, 0.05));
}

export function businessMixScenario(data) {
    const roll = Math.random();
    if (roll < 0.15) {
        viewProduct(pickRandomPath(data.productPaths), "mix");
    } else if (roll < 0.30) {
        viewCatalog("mix");
    } else if (roll < 0.50) {
        addToCartSuccess(data, "mix");
    } else if (roll < 0.58) {
        addToCartUnavailableFail(data, "mix");
    } else if (roll < 0.70) {
        addToWishlistSuccess(data, "mix");
    } else if (roll < 0.80) {
        checkoutError(data, "mix");
    } else if (roll < 0.88) {
        checkoutSuccess(data, "mix");
    } else if (roll < 0.94) {
        supportRequest("mix");
    } else if (roll < 0.98) {
        reviewAdd(data, "mix");
    } else {
        registerUser("mix");
    }
    sleep(randomBetween(0.03, 0.12));
}

function viewProduct(path, phase) {
    const traceId = buildTraceId(phase);
    const res = http.get(`${BASE_URL}${path}`, {
        headers: {
            "X-Trace-Id": traceId,
            "X-Load-Phase": phase.includes("spike") ? "spike" : "baseline"
        },
        tags: { phase, endpoint: "/product/{slug}" },
        timeout: "35s"
    });
    markResponse(res, phase);
    check(res, { "view product responded": (r) => r.status > 0 });
}

function viewCatalog(phase) {
    const res = http.get(`${BASE_URL}/catalog`, {
        headers: {
            "X-Trace-Id": buildTraceId(phase),
            "X-Load-Phase": phase.includes("spike") ? "spike" : "baseline"
        },
        tags: { phase, endpoint: "/catalog" },
        timeout: "25s"
    });
    markResponse(res, phase);
    check(res, { "view catalog responded": (r) => r.status > 0 });
}

function addToCartSuccess(data, phase) {
    const product = pickRandomProduct(data.availableProducts || data.products);
    if (!product) {
        return;
    }
    const response = postForm(
        "/api/cart/add",
        {
            productId: String(product.id),
            quantity: String(randomInt(1, 2))
        },
        phase
    );
    if (response && response.status >= 400) {
        businessErrors.add(1, { phase, action: "add_to_cart_success" });
    }
}

function addToCartUnavailableFail(data, phase) {
    const product = pickRandomProduct(data.unavailableProducts || []);
    if (!product) {
        checkoutError(data, phase);
        return;
    }
    const response = postForm(
        "/api/cart/add",
        {
            productId: String(product.id),
            quantity: "1"
        },
        phase
    );
    if (response && response.status >= 400) {
        businessErrors.add(1, { phase, action: "add_to_cart_unavailable_fail" });
    }
}

function addToWishlistSuccess(data, phase) {
    const product = pickRandomProduct(data.products);
    if (!product) {
        return;
    }
    const response = postForm(
        "/wishlist/add",
        {
            productId: String(product.id)
        },
        phase
    );
    if (response && response.status >= 400) {
        businessErrors.add(1, { phase, action: "add_to_wishlist_success" });
    }
}

function checkoutSuccess(data, phase) {
    if (!ensureLogin(phase)) {
        businessErrors.add(1, { phase, action: "login_for_checkout_success" });
        return;
    }
    const product = pickRandomProduct(data.availableProducts || data.products);
    if (!product) {
        return;
    }

    postForm(
        "/api/cart/add",
        {
            productId: String(product.id),
            quantity: "1"
        },
        phase
    );

    ensureCsrf("/checkout", phase);
    const response = postForm(
        "/checkout",
        {
            customerName: `Demo User ${__VU}`,
            customerEmail: `demo.user.${__VU}@example.com`,
            customerPhone: "+79990000000",
            deliveryType: "PICKUP",
            deliveryStreet: "",
            deliveryHouse: "",
            deliveryApartment: "",
            deliveryEntrance: "",
            deliveryFloor: "",
            deliveryIntercom: "",
            pickupDate: "",
            deliveryDate: "",
            deliveryTime: ""
        },
        phase
    );

    if (response && response.status >= 300 && response.status < 400) {
        checkoutSuccessCount.add(1, { phase, action: "checkout_success" });
    } else if (response && response.status >= 400) {
        businessErrors.add(1, { phase, action: "checkout_success_failed" });
    }
}

function checkoutError(data, phase) {
    if (!ensureLogin(phase)) {
        businessErrors.add(1, { phase, action: "login_for_checkout_error" });
        return;
    }
    const product = pickRandomProduct(data.availableProducts || data.products);
    if (product) {
        postForm(
            "/api/cart/add",
            {
                productId: String(product.id),
                quantity: "1"
            },
            phase
        );
    }

    ensureCsrf("/checkout", phase);
    const response = postForm(
        "/checkout",
        {
            customerName: `Broken Checkout ${__VU}`,
            customerEmail: `broken.checkout.${__VU}@example.com`,
            customerPhone: "+79991111111",
            deliveryType: "PICKUP",
            deliveryStreet: "",
            deliveryHouse: "",
            deliveryApartment: "",
            deliveryEntrance: "",
            deliveryFloor: "",
            deliveryIntercom: "",
            pickupDate: "",
            deliveryDate: "",
            deliveryTime: ""
        },
        phase,
        {
            "X-Demo-Fault": "CHECKOUT_RESERVATION_FAIL"
        }
    );

    if (response && response.status >= 300 && response.status < 500) {
        businessErrors.add(1, { phase, action: "checkout_error_expected" });
    }
}

function registerUser(phase) {
    ensureCsrf("/register", phase);
    const suffix = `${Date.now()}_${__VU}_${__ITER}_${Math.random().toString(36).slice(2, 7)}`;
    const response = postForm(
        "/register",
        {
            username: `demo_${suffix}`,
            fullName: `Demo User ${__VU}`,
            email: `demo_${suffix}@example.com`,
            password: "demoPass123",
            phone: "+79990000000"
        },
        phase
    );
    if (response && response.status >= 400) {
        businessErrors.add(1, { phase, action: "register_error" });
    }
}

function supportRequest(phase) {
    ensureCsrf("/support", phase);
    const response = postForm(
        "/support/request",
        {
            name: `Support Demo ${__VU}`,
            email: `support.demo.${__VU}@example.com`,
            phone: "+79991112233",
            message: `Тестовая заявка для аналитики. trace=${buildTraceId("support-note")}`
        },
        phase
    );
    if (response && response.status >= 400) {
        businessErrors.add(1, { phase, action: "support_request_error" });
    }
}

function reviewAdd(data, phase) {
    const product = pickRandomProduct(data.availableProducts || data.products);
    if (!product) {
        return;
    }
    ensureCsrf(product.path, phase);
    const response = postForm(
        "/review/add",
        {
            productId: String(product.id),
            rating: String(randomInt(3, 5)),
            text: `Тестовый отзыв для демо аналитики. Итерация ${__ITER}.`,
            pros: "Хорошая производительность",
            cons: "Нет",
            usagePeriod: "LT_MONTH",
            guestName: `Guest ${__VU}`,
            guestEmail: `guest.${__VU}.${__ITER}@example.com`
        },
        phase
    );
    if (response && response.status >= 400) {
        businessErrors.add(1, { phase, action: "review_add_error" });
    }
}

function discoverProductPaths() {
    const pages = ["/", "/catalog"];
    const out = new Set();
    for (const page of pages) {
        const res = http.get(`${BASE_URL}${page}`, {
            tags: { phase: "setup", endpoint: page },
            timeout: "20s"
        });
        if (!res || res.status >= 400 || !res.body) {
            continue;
        }
        const found = extractProductPaths(res.body);
        for (const path of found) {
            out.add(path);
        }
    }
    return Array.from(out);
}

function buildProductCatalog(paths) {
    const products = [];
    const uniqueIds = new Set();
    const maxProducts = Number(__ENV.MAX_PRODUCTS || "24");
    for (const path of paths.slice(0, maxProducts)) {
        const res = http.get(`${BASE_URL}${path}`, {
            tags: { phase: "setup", endpoint: "/product/{slug}" },
            timeout: "20s"
        });
        if (!res || res.status >= 400 || !res.body) {
            continue;
        }
        const productId = extractProductId(res.body);
        if (!productId || uniqueIds.has(productId)) {
            continue;
        }
        uniqueIds.add(productId);
        products.push({
            id: productId,
            path,
            inStock: extractInStock(res.body)
        });
    }
    return products;
}

function ensureLogin(phase) {
    if (vuState.loggedIn) {
        return true;
    }

    const loginPage = http.get(`${BASE_URL}/login`, {
        headers: {
            "X-Trace-Id": buildTraceId(`${phase}-login-page`)
        },
        tags: { phase, endpoint: "/login" },
        timeout: "20s"
    });
    markResponse(loginPage, phase);
    updateCsrfFromHtml(loginPage && loginPage.body ? loginPage.body : "");

    if (!vuState.csrfToken) {
        return false;
    }

    const payload = toFormBody({
        username: ADMIN_USERNAME,
        password: ADMIN_PASSWORD,
        _csrf: vuState.csrfToken
    });
    const headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "X-Trace-Id": buildTraceId(`${phase}-login`),
        "X-Load-Phase": phase.includes("spike") ? "spike" : "baseline"
    };
    if (vuState.csrfHeader && vuState.csrfToken) {
        headers[vuState.csrfHeader] = vuState.csrfToken;
    }
    const loginRes = http.post(`${BASE_URL}/login`, payload, {
        headers,
        redirects: 0,
        tags: { phase, endpoint: "/login" },
        timeout: "20s"
    });
    markResponse(loginRes, phase);

    if (loginRes && loginRes.status >= 300 && loginRes.status < 400) {
        vuState.loggedIn = true;
        ensureCsrf("/", phase);
        return true;
    }
    return false;
}

function postForm(path, formData, phase, extraHeaders) {
    if (!vuState.csrfToken) {
        ensureCsrf("/", phase);
    }

    let res = doPost(path, formData, phase, extraHeaders);
    if (res && res.status === 403) {
        ensureCsrf("/", phase);
        res = doPost(path, formData, phase, extraHeaders);
    }
    if (res) {
        markResponse(res, phase);
    }
    return res;
}

function doPost(path, formData, phase, extraHeaders) {
    const payload = toFormBody({
        ...formData,
        _csrf: vuState.csrfToken || ""
    });
    const headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "X-Trace-Id": buildTraceId(phase),
        "X-Load-Phase": phase.includes("spike") ? "spike" : "baseline"
    };
    if (vuState.csrfHeader && vuState.csrfToken) {
        headers[vuState.csrfHeader] = vuState.csrfToken;
    }
    if (extraHeaders && typeof extraHeaders === "object") {
        for (const [key, value] of Object.entries(extraHeaders)) {
            headers[key] = value;
        }
    }
    return http.post(`${BASE_URL}${path}`, payload, {
        headers,
        redirects: 0,
        tags: { phase, endpoint: path },
        timeout: "30s"
    });
}

function ensureCsrf(path, phase) {
    const res = http.get(`${BASE_URL}${path}`, {
        headers: {
            "X-Trace-Id": buildTraceId(`${phase}-csrf`)
        },
        tags: { phase, endpoint: path },
        timeout: "20s"
    });
    markResponse(res, phase);
    if (res && res.body) {
        updateCsrfFromHtml(res.body);
    }
}

function updateCsrfFromHtml(html) {
    const metaTokenMatch = html.match(/name="_csrf"[^>]*content="([^"]+)"/i);
    const metaHeaderMatch = html.match(/name="_csrf_header"[^>]*content="([^"]+)"/i);
    const hiddenTokenMatch = html.match(/name="_csrf"[^>]*value="([^"]+)"/i);

    const token = metaTokenMatch ? metaTokenMatch[1] : (hiddenTokenMatch ? hiddenTokenMatch[1] : null);
    if (token && token.trim().length > 0) {
        vuState.csrfToken = token.trim();
    }
    if (metaHeaderMatch && metaHeaderMatch[1] && metaHeaderMatch[1].trim()) {
        vuState.csrfHeader = metaHeaderMatch[1].trim();
    }
}

function markResponse(res, phase) {
    if (!res) {
        return;
    }
    reqDuration.add(res.timings.duration, { phase });
    if (res.status >= 500) {
        status5xx.add(1, { phase });
    } else if (res.status >= 400) {
        status4xx.add(1, { phase });
    } else if (res.status >= 200 && res.status < 300) {
        status2xx.add(1, { phase });
    }
}

function pickRandomPath(paths) {
    if (!paths || paths.length === 0) {
        return "/";
    }
    return paths[Math.floor(Math.random() * paths.length)];
}

function pickRandomProduct(products) {
    if (!products || products.length === 0) {
        return null;
    }
    return products[Math.floor(Math.random() * products.length)];
}

function extractProductPaths(html) {
    const matches = html.match(/\/product\/[A-Za-z0-9._~%-]+/g) || [];
    return Array.from(new Set(matches));
}

function extractProductId(html) {
    const match = html.match(/name="productId"[^>]*value="(\d+)"/i);
    if (!match) {
        return null;
    }
    const value = Number.parseInt(match[1], 10);
    return Number.isFinite(value) ? value : null;
}

function extractInStock(html) {
    if (!html) {
        return true;
    }
    if (/Товара нет в наличии/i.test(html)) {
        return false;
    }
    if (/class="btn btn-brand cart-add-btn"[^>]*disabled/i.test(html)) {
        return false;
    }
    return true;
}

function toFormBody(formData) {
    return Object.entries(formData)
        .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value == null ? "" : String(value))}`)
        .join("&");
}

function randomBetween(min, max) {
    return min + (max - min) * Math.random();
}

function randomInt(min, max) {
    return Math.floor(randomBetween(min, max + 1));
}

function buildTraceId(phase) {
    const rnd = Math.random().toString(36).slice(2, 12);
    return `dm-${phase}-${__VU}-${__ITER}-${rnd}`.replace(/[^A-Za-z0-9_.-]/g, "").slice(0, 64);
}

function addDurations(first, second) {
    const firstSec = parseDurationToSec(first);
    const secondSec = parseDurationToSec(second);
    return `${firstSec + secondSec}s`;
}

function parseDurationToSec(value) {
    const str = String(value || "").trim();
    const match = str.match(/^(\d+)(s|m|h)$/);
    if (!match) {
        return 0;
    }
    const amount = Number(match[1]);
    const unit = match[2];
    if (unit === "s") {
        return amount;
    }
    if (unit === "m") {
        return amount * 60;
    }
    return amount * 3600;
}

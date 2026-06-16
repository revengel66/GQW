import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate } from "k6/metrics";

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const TARGET_EVENTS_PER_MIN = Math.max(20, Number(__ENV.TARGET_EVENTS_PER_MIN || 100));
const ENABLE_FRONTEND_INGEST = String(__ENV.ENABLE_FRONTEND_INGEST || "true").toLowerCase() !== "false";
const BASE_ACTIONS_PER_MIN = Math.max(10, Math.round(TARGET_EVENTS_PER_MIN / (ENABLE_FRONTEND_INGEST ? 2 : 1)));
const PEAK_MULTIPLIER = Math.max(1, Number(__ENV.PEAK_MULTIPLIER || 2.4));
const ENABLE_SHOP_AUTH_FLOW = String(__ENV.ENABLE_SHOP_AUTH_FLOW || "true").toLowerCase() !== "false";
const SHOP_FLOW_RATIO = clamp(Number(__ENV.SHOP_FLOW_RATIO || 0.12), 0, 0.5);
const ERROR_EVENT_RATIO = clamp(Number(__ENV.ERROR_EVENT_RATIO || 0.02), 0, 0.2);
const SHOP_USERNAME = (__ENV.SHOP_USERNAME || "user").trim();
const SHOP_PASSWORD = (__ENV.SHOP_PASSWORD || "user").trim();
const SHOP_EMAIL = (__ENV.SHOP_EMAIL || "user@nexora.local").trim();
const SLOW_REQ_MS = Number(__ENV.SLOW_REQ_MS || 10000);
const DOWN_BACKOFF_SEC = Number(__ENV.DOWN_BACKOFF_SEC || 5);
const MAX_REPORTED_DURATION_MS = Number(__ENV.MAX_REPORTED_DURATION_MS || 15000);

const actionCounter = new Counter("shop_live_actions_total");
const frontendIngestCounter = new Counter("shop_frontend_ingest_batches_total");
const frontendIngestAcceptedRate = new Rate("shop_frontend_ingest_accepted_rate");
const backendHealthyRate = new Rate("shop_backend_healthy_rate");

export const options = {
  scenarios: {
    shop_live_user_actions: {
      executor: "ramping-arrival-rate",
      timeUnit: "1m",
      preAllocatedVUs: 20,
      maxVUs: 90,
      stages: [
        { duration: "10m", target: BASE_ACTIONS_PER_MIN },
        { duration: "6h", target: BASE_ACTIONS_PER_MIN },
        { duration: "4h", target: Math.round(BASE_ACTIONS_PER_MIN * 1.35) },
        { duration: "4h", target: Math.round(BASE_ACTIONS_PER_MIN * 1.8) },
        { duration: "3h", target: Math.round(BASE_ACTIONS_PER_MIN * PEAK_MULTIPLIER) },
        { duration: "3h", target: Math.round(BASE_ACTIONS_PER_MIN * 1.55) },
        { duration: "3h40m", target: BASE_ACTIONS_PER_MIN },
        { duration: "10m", target: 0 },
      ],
      gracefulStop: "15s",
    },
  },
  thresholds: {
    shop_backend_healthy_rate: ["rate>0.95"],
    shop_frontend_ingest_accepted_rate: ["rate>0.90"],
  },
};

export function setup() {
  const discovered = discoverCatalogLinks();
  return {
    products: discovered.products.length > 0 ? discovered.products : fallbackProducts(),
    categories: discovered.categories.length > 0 ? discovered.categories : fallbackCategories(),
  };
}

export default function (data) {
  const requestId = buildRequestId();
  const action = pickShopAction(data);
  const response = runShopAction(action, requestId);

  actionCounter.add(1, { action: action.name });

  if (!response || isBackendUnavailable(response) || isServerOverloaded(response)) {
    backendHealthyRate.add(false);
    sleep(DOWN_BACKOFF_SEC);
    return;
  }

  backendHealthyRate.add(true);
  check(response, {
    "shop request completed": (r) => r.status > 0,
  });

  if (ENABLE_FRONTEND_INGEST) {
    sendFrontendEvent(action, requestId, response);
  }

  if (ENABLE_SHOP_AUTH_FLOW && Math.random() < SHOP_FLOW_RATIO) {
    runOccasionalShopFormFlow(data);
  }

  sleep(randomFloat(0.2, 1.3));
}

function discoverCatalogLinks() {
  const products = new Set();
  const categories = new Set();
  ["/", "/catalog"].forEach((path) => {
    const response = http.get(url(path), {
      tags: { endpoint: path, action: "setup_discovery" },
      timeout: "20s",
    });
    if (response.status >= 200 && response.status < 500) {
      extractUniquePaths(response.body, /\/product\/[A-Za-z0-9._~%-]+/g).forEach((value) => products.add(value));
      extractUniquePaths(response.body, /\/category\/[A-Za-z0-9._~%-]+/g).forEach((value) => categories.add(value));
    }
  });
  return {
    products: Array.from(products),
    categories: Array.from(categories),
  };
}

function pickShopAction(data) {
  const productPaths = Array.isArray(data?.products) && data.products.length > 0 ? data.products : fallbackProducts();
  const categoryPaths = Array.isArray(data?.categories) && data.categories.length > 0 ? data.categories : fallbackCategories();
  const roll = Math.random();

  if (roll < 0.15) return { name: "home", method: "GET", path: "/", expectedCode: "HOME_VIEW" };
  if (roll < 0.29) return { name: "catalog", method: "GET", path: catalogQueryPath(), expectedCode: "CATALOG_VIEW" };
  if (roll < 0.43) return { name: "product", method: "GET", path: pickOne(productPaths), expectedCode: "PRODUCT_VIEW" };
  if (roll < 0.55) return { name: "category", method: "GET", path: pickOne(categoryPaths), expectedCode: "CATEGORY_VIEW" };
  if (roll < 0.62) return { name: "cart", method: "GET", path: "/cart", expectedCode: "CART_VIEW" };
  if (roll < 0.68) return { name: "wishlist", method: "GET", path: "/wishlist", expectedCode: "WISHLIST_VIEW" };
  if (roll < 0.74) return { name: "reviews", method: "GET", path: "/reviews", expectedCode: "REVIEWS_PAGE_VIEW" };
  if (roll < 0.80) return { name: "support", method: "GET", path: "/support", expectedCode: "SUPPORT_PAGE_VIEW" };
  if (roll < 0.85) return { name: "delivery", method: "GET", path: "/delivery", expectedCode: "DELIVERY_VIEW" };
  if (roll < 0.90) return { name: "contacts", method: "GET", path: "/contacts", expectedCode: "CONTACTS_VIEW" };
  if (roll < 0.94) return { name: "about", method: "GET", path: "/about", expectedCode: "ABOUT_VIEW" };
  if (roll < 0.99) return { name: "login", method: "GET", path: "/login", expectedCode: "LOGIN_VIEW" };
  return { name: "not_found_product", method: "GET", path: `/product/not-found-${randomInt(1000, 9999)}`, expectedCode: "PRODUCT_VIEW" };
}

function runShopAction(action, requestId) {
  return http.get(url(action.path), {
    headers: {
      "X-Trace-Id": requestId,
      "X-Load-Scenario": "shop-live-user-actions",
    },
    tags: {
      endpoint: normalizeEndpointTag(action.path),
      action: action.name,
    },
    timeout: "25s",
  });
}

function runOccasionalShopFormFlow(data) {
  const loginPage = http.get(url("/login"), {
    headers: { "X-Trace-Id": buildRequestId(), "X-Load-Scenario": "shop-live-user-actions" },
    tags: { endpoint: "/login", action: "shop_login_page" },
    timeout: "20s",
  });
  if (loginPage.status === 0 || loginPage.status >= 500) {
    return;
  }

  const csrf = extractCsrf(loginPage.body);
  postForm("/login", { username: SHOP_USERNAME, password: SHOP_PASSWORD }, csrf, "shop_login_submit");

  const productPath = pickOne(Array.isArray(data?.products) && data.products.length > 0 ? data.products : fallbackProducts());
  const productPage = http.get(url(productPath), {
    headers: { "X-Trace-Id": buildRequestId(), "X-Load-Scenario": "shop-live-user-actions" },
    tags: { endpoint: "/product/{slug}", action: "shop_product_before_cart" },
    timeout: "20s",
  });
  const productId = extractFirstInputValue(productPage.body, "productId");
  const productCsrf = extractCsrf(productPage.body);
  if (productId) {
    postForm("/api/cart/add", { productId, quantity: 1 }, productCsrf, "shop_add_to_cart");
    if (Math.random() < 0.45) {
      postForm("/api/wishlist/toggle", { productId }, productCsrf, "shop_wishlist_toggle");
    }
  }

  if (Math.random() < 0.35) {
    postForm(
      "/support/request",
      {
        name: "Load Test User",
        email: SHOP_EMAIL,
        phone: "+79990001111",
        message: "Synthetic support request from analytics live activity script.",
      },
      productCsrf,
      "shop_support_request"
    );
  }

  postForm("/logout", {}, csrf, "shop_logout");
}

function postForm(path, form, csrf, actionTag) {
  const payload = { ...(form || {}) };
  const headers = {
    "Content-Type": "application/x-www-form-urlencoded",
    "X-Trace-Id": buildRequestId(),
    "X-Load-Scenario": "shop-live-user-actions",
  };
  if (csrf?.token) {
    payload._csrf = csrf.token;
    headers[csrf.header || "X-CSRF-TOKEN"] = csrf.token;
  }
  return http.post(url(path), payload, {
    headers,
    tags: { endpoint: normalizeEndpointTag(path), action: actionTag },
    timeout: "25s",
  });
}

function sendFrontendEvent(action, requestId, response) {
  const event = buildFrontendEvent(action, requestId, response);
  const ingestResponse = http.post(
    url("/api/analytics/frontend/ingest"),
    JSON.stringify({ events: [event] }),
    {
      headers: {
        "Content-Type": "application/json",
        "X-Trace-Id": requestId,
        "X-Load-Scenario": "shop-live-user-actions",
      },
      tags: { endpoint: "/api/analytics/frontend/ingest", action: "frontend_ingest" },
      timeout: "20s",
    }
  );
  frontendIngestCounter.add(1);
  frontendIngestAcceptedRate.add(ingestResponse.status === 202);
}

function buildFrontendEvent(action, requestId, response) {
  const statusCode = Number(response?.status || 200);
  const durationMs = normalizeDurationMs(Number(response?.timings?.duration || randomInt(40, 480)));
  const error = statusCode >= 400 || Math.random() < ERROR_EVENT_RATIO;
  const path = action.path;

  if (error) {
    return {
      code: "FRONTEND_JS_ERROR",
      moduleCode: "DEFAULT",
      pagePath: path,
      requestPath: path,
      httpMethod: action.method,
      traceId: requestId,
      statusCode: statusCode || 500,
      error: true,
      errorMessage: statusCode >= 400 ? `HTTP ${statusCode}` : "Synthetic frontend error sample",
      metricsNum: {
        FRONTEND_HTTP_STATUS: statusCode || 500,
      },
      metricsText: {
        FRONTEND_PAGE_URL: path,
        FRONTEND_API_URL: path,
        FRONTEND_API_METHOD: action.method,
        FRONTEND_ERROR_MESSAGE: statusCode >= 400 ? `HTTP ${statusCode}` : "Synthetic frontend error sample",
        FRONTEND_TRACE_ID: requestId,
      },
    };
  }

  return {
    code: action.expectedCode || "FRONTEND_PAGE_LOAD",
    moduleCode: "DEFAULT",
    pagePath: path,
    requestPath: path,
    httpMethod: action.method,
    traceId: requestId,
    statusCode: statusCode || 200,
    error: false,
    errorMessage: null,
    metricsNum: {
      FRONTEND_HTTP_STATUS: statusCode || 200,
      FRONTEND_API_DURATION_MS: durationMs,
      FRONTEND_TTFB_MS: Math.max(1, Math.round(durationMs * randomFloat(0.18, 0.42))),
      FRONTEND_DOM_INTERACTIVE_MS: durationMs + randomInt(20, 180),
      FRONTEND_DOM_CONTENT_LOADED_MS: durationMs + randomInt(40, 260),
      FRONTEND_LOAD_EVENT_MS: durationMs + randomInt(80, 420),
      FRONTEND_TRANSFER_SIZE_BYTES: randomInt(25000, 850000),
    },
    metricsText: {
      FRONTEND_PAGE_URL: path,
      FRONTEND_API_URL: path,
      FRONTEND_API_METHOD: action.method,
      FRONTEND_NAV_TYPE: pickOne(["navigate", "reload", "back_forward"]),
      FRONTEND_TRACE_ID: requestId,
      FRONTEND_CUSTOM_ATTRS_JSON: JSON.stringify({
        action: action.name,
        zone: pickOne(["header", "catalog", "product-card", "filters", "cart", "footer"]),
        source: "k6-shop-live-user-actions",
      }),
    },
  };
}

function catalogQueryPath() {
  const params = [];
  if (Math.random() < 0.55) params.push(`query=${encodeURIComponent(pickOne(["phone", "tv", "laptop", "camera", "watch", "audio"]))}`);
  if (Math.random() < 0.45) params.push(`sort=${encodeURIComponent(pickOne(["priceAsc", "priceDesc", "nameAsc", "ratingDesc"]))}`);
  if (Math.random() < 0.35) params.push(`page=${randomInt(0, 4)}`);
  return params.length === 0 ? "/catalog" : `/catalog?${params.join("&")}`;
}

function fallbackProducts() {
  return ["/catalog", "/"];
}

function fallbackCategories() {
  return ["/catalog", "/"];
}

function extractCsrf(html) {
  const source = String(html || "");
  const tokenMatch = source.match(/name=["']_csrf["'][^>]*content=["']([^"']+)["']/i)
    || source.match(/name=["']_csrf["'][^>]*value=["']([^"']+)["']/i);
  const headerMatch = source.match(/name=["']_csrf_header["'][^>]*content=["']([^"']+)["']/i);
  return {
    token: tokenMatch ? tokenMatch[1] : "",
    header: headerMatch ? headerMatch[1] : "X-CSRF-TOKEN",
  };
}

function extractFirstInputValue(html, inputName) {
  const source = String(html || "");
  const safeName = String(inputName || "").replace(/[-/\\^$*+?.()|[\]{}]/g, "\\$&");
  const pattern = new RegExp(`<input[^>]*name=["']${safeName}["'][^>]*>`, "ig");
  let match = pattern.exec(source);
  while (match) {
    const valueMatch = String(match[0] || "").match(/value=["']([^"']+)["']/i);
    if (valueMatch && valueMatch[1]) {
      return valueMatch[1];
    }
    match = pattern.exec(source);
  }
  return "";
}

function normalizeEndpointTag(path) {
  const clean = normalizePathOnly(path);
  if (clean.startsWith("/product/")) return "/product/{slug}";
  if (clean.startsWith("/category/")) return "/category/{slug}";
  if (clean === "/api/cart/add") return "/api/cart/add";
  if (clean === "/api/wishlist/toggle") return "/api/wishlist/toggle";
  return clean;
}

function normalizePathOnly(value) {
  const raw = String(value || "").trim();
  const withoutQuery = raw.split("?")[0] || "/";
  return withoutQuery.startsWith("/") ? withoutQuery : `/${withoutQuery}`;
}

function url(path) {
  return `${BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}

function buildRequestId() {
  return `k6-shop-live-${__VU}-${__ITER}-${Math.random().toString(36).slice(2, 12)}`.slice(0, 64);
}

function extractUniquePaths(html, pattern) {
  return Array.from(new Set(String(html || "").match(pattern) || []));
}

function isBackendUnavailable(response) {
  return Number(response?.status || 0) === 0;
}

function isServerOverloaded(response) {
  const status = Number(response?.status || 0);
  if (status >= 500) {
    return true;
  }
  const durationMs = Number(response?.timings?.duration || 0);
  return durationMs >= SLOW_REQ_MS;
}

function normalizeDurationMs(rawValue) {
  const numeric = Number(rawValue);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return randomInt(40, 480);
  }
  return Math.max(1, Math.min(MAX_REPORTED_DURATION_MS, Math.round(numeric)));
}

function randomInt(min, max) {
  const lo = Math.floor(Math.min(min, max));
  const hi = Math.floor(Math.max(min, max));
  return Math.floor(Math.random() * (hi - lo + 1)) + lo;
}

function randomFloat(min, max) {
  return Math.min(min, max) + Math.random() * Math.abs(max - min);
}

function pickOne(items) {
  if (!Array.isArray(items) || items.length === 0) {
    return "/";
  }
  return items[Math.floor(Math.random() * items.length)];
}

function clamp(value, min, max) {
  if (!Number.isFinite(value)) {
    return min;
  }
  return Math.max(min, Math.min(max, value));
}

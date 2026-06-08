import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate } from "k6/metrics";

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const PROFILE = (__ENV.PROFILE || "safe").trim().toLowerCase();
const SAFE_PROFILE = PROFILE !== "aggressive";
const VUS = Number(__ENV.VUS || (SAFE_PROFILE ? 1 : 5));
const DURATION = __ENV.DURATION || "168h";
const PAUSE_MIN_MS = Number(__ENV.PAUSE_MIN_MS || (SAFE_PROFILE ? 1800 : 250));
const PAUSE_MAX_MS = Number(__ENV.PAUSE_MAX_MS || (SAFE_PROFILE ? 6500 : 1200));
const MIN_STEPS = Number(__ENV.MIN_STEPS || (SAFE_PROFILE ? 1 : 3));
const MAX_STEPS = Number(__ENV.MAX_STEPS || (SAFE_PROFILE ? 3 : 8));
const REAL_NAV_RATIO = Number(__ENV.REAL_NAV_RATIO || (SAFE_PROFILE ? 0.35 : 1));
const ADMIN_PATH_RATIO = Math.max(0, Math.min(0.95, Number(__ENV.ADMIN_PATH_RATIO || (SAFE_PROFILE ? 0.45 : 0.25))));
const ERROR_EVENT_RATIO = Number(__ENV.ERROR_EVENT_RATIO || 0);
const DOWN_BACKOFF_SEC = Number(__ENV.DOWN_BACKOFF_SEC || (SAFE_PROFILE ? 6 : 2));
const OVERLOAD_BACKOFF_SEC = Number(__ENV.OVERLOAD_BACKOFF_SEC || (SAFE_PROFILE ? 12 : 4));
const SLOW_REQ_MS = Number(__ENV.SLOW_REQ_MS || 6000);
const MAX_REPORTED_DURATION_MS = Number(__ENV.MAX_REPORTED_DURATION_MS || 15000);
const MAX_DOWN_LOGS_PER_VU = Number(__ENV.MAX_DOWN_LOGS_PER_VU || 20);
const ENABLE_SHOP_AUTH_FLOW = String(__ENV.ENABLE_SHOP_AUTH_FLOW || "true").toLowerCase() !== "false";
const ENABLE_ADMIN_AUTH_FLOW = String(__ENV.ENABLE_ADMIN_AUTH_FLOW || "true").toLowerCase() !== "false";
const ENABLE_ANALYTICS_ADMIN_FLOW = String(__ENV.ENABLE_ANALYTICS_ADMIN_FLOW || "true").toLowerCase() !== "false";
const SHOP_FLOW_RATIO = Math.max(0, Math.min(1, Number(__ENV.SHOP_FLOW_RATIO || (SAFE_PROFILE ? 0.75 : 0.9))));
const ADMIN_FLOW_RATIO = Math.max(0, Math.min(1, Number(__ENV.ADMIN_FLOW_RATIO || (SAFE_PROFILE ? 0.65 : 0.85))));
const ANALYTICS_ADMIN_FLOW_RATIO = Math.max(0, Math.min(1, Number(__ENV.ANALYTICS_ADMIN_FLOW_RATIO || (SAFE_PROFILE ? 0.55 : 0.8))));
const SHOP_USERNAME = (__ENV.SHOP_USERNAME || "user").trim();
const SHOP_PASSWORD = (__ENV.SHOP_PASSWORD || "user").trim();
const SHOP_EMAIL = (__ENV.SHOP_EMAIL || "user@nexora.local").trim();
const ADMIN_USERNAME = (__ENV.ADMIN_USERNAME || "admin").trim();
const ADMIN_PASSWORD = (__ENV.ADMIN_PASSWORD || "admin").trim();
const ANALYTICS_ADMIN_USERNAME = (__ENV.ANALYTICS_ADMIN_USERNAME || "admin").trim();
const ANALYTICS_ADMIN_PASSWORD = (__ENV.ANALYTICS_ADMIN_PASSWORD || "admin").trim();

const actionCounter = new Counter("ui_actions_total");
const ingestBatchCounter = new Counter("frontend_ingest_batches_total");
const ingestFailedCounter = new Counter("frontend_ingest_failed_total");
const ingestAcceptedRate = new Rate("frontend_ingest_accepted_rate");
const overloadBackoffCounter = new Counter("frontend_overload_backoff_total");
const authActionCounter = new Counter("auth_actions_total");

let backendDown = false;
let downLogs = 0;

export const options = {
  scenarios: {
    live_user_actions: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
      gracefulStop: "0s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    frontend_ingest_accepted_rate: ["rate>0.9"],
  },
};

export function setup() {
  const seedPages = ["/", "/catalog", "/analytics-admin/login"];
  const productPaths = new Set();
  const categoryPaths = new Set();
  const adminPages = [
    "/analytics-admin/login",
    "/analytics-admin",
    "/analytics-admin/dashboard",
    "/analytics-admin/dictionaries",
    "/admin",
    "/admin/products",
    "/admin/orders",
    "/admin/users",
    "/admin/reviews",
    "/admin/support",
    "/admin/files",
    "/admin/filters",
    "/admin/categories",
  ];
  const staticPages = [
    "/",
    "/catalog",
    "/about",
    "/contacts",
    "/delivery",
    "/support",
    "/login",
    "/register",
    "/cart",
    "/wishlist",
    "/analytics",
    ...adminPages,
  ];

  seedPages.forEach((path) => {
    const res = http.get(`${BASE_URL}${path}`, {
      tags: { type: "setup_seed", page: path },
      timeout: "20s",
    });
    if (res.status >= 200 && res.status < 500 && res.body) {
      extractUniquePaths(res.body, /\/product\/[A-Za-z0-9._~%-]+/g).forEach((p) => productPaths.add(p));
      extractUniquePaths(res.body, /\/category\/[A-Za-z0-9._~%-]+/g).forEach((p) => categoryPaths.add(p));
    }
  });

  return {
    staticPages,
    adminPages,
    productPaths: Array.from(productPaths),
    categoryPaths: Array.from(categoryPaths),
  };
}

export default function (data) {
  const steps = randomInt(MIN_STEPS, MAX_STEPS);
  const events = [];

  for (let i = 0; i < steps; i += 1) {
    const path = pickPath(data);
    const method = "GET";
    const requestId = buildRequestId();
    let res = null;
    const shouldNavigate = Math.random() < REAL_NAV_RATIO;
    if (shouldNavigate) {
      res = http.get(`${BASE_URL}${path}`, {
        headers: {
          "X-Trace-Id": requestId,
          "X-Load-Scenario": "live-user-actions",
        },
        tags: {
          endpoint: normalizeEndpointTag(path),
          action: "navigate",
        },
        timeout: "25s",
      });

      if (isBackendUnavailable(res)) {
        markBackendDown(`GET ${path}`);
        sleep(DOWN_BACKOFF_SEC);
        return;
      }

      if (isServerOverloaded(res)) {
        overloadBackoffCounter.add(1);
        markBackendDown(`GET ${path} (overloaded)`);
        sleep(OVERLOAD_BACKOFF_SEC);
        return;
      }

      markBackendUp();
    }

    actionCounter.add(1);
    if (res) {
      const ok = check(res, {
        "page request finished": (r) => r.status > 0,
      });
      if (!ok) {
        continue;
      }
    }

    events.push(buildFrontendEvent(path, method, requestId, res));
    sleep(randomPauseSeconds());
  }

  runAuthenticatedFlows(data, events);
  if (events.length === 0) {
    return;
  }

  const ingestRes = http.post(
    `${BASE_URL}/api/analytics/frontend/ingest`,
    JSON.stringify({ events }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { endpoint: "/api/analytics/frontend/ingest", action: "ingest" },
      timeout: "25s",
    }
  );

  if (isBackendUnavailable(ingestRes)) {
    ingestAcceptedRate.add(false);
    ingestBatchCounter.add(1);
    ingestFailedCounter.add(1);
    markBackendDown("POST /api/analytics/frontend/ingest");
    sleep(DOWN_BACKOFF_SEC);
    return;
  }

  if (isServerOverloaded(ingestRes)) {
    overloadBackoffCounter.add(1);
    ingestAcceptedRate.add(false);
    ingestBatchCounter.add(1);
    ingestFailedCounter.add(1);
    markBackendDown("POST /api/analytics/frontend/ingest (overloaded)");
    sleep(OVERLOAD_BACKOFF_SEC);
    return;
  }

  markBackendUp();

  const accepted = ingestRes.status === 202;
  ingestAcceptedRate.add(accepted);
  ingestBatchCounter.add(1);
  if (!accepted) {
    ingestFailedCounter.add(1);
    console.error(`ingest_failed status=${ingestRes.status} body=${String(ingestRes.body || "").slice(0, 200)}`);
  }
}

function runAuthenticatedFlows(data, events) {
  if (!Array.isArray(events)) {
    return;
  }
  if (ENABLE_SHOP_AUTH_FLOW && Math.random() <= SHOP_FLOW_RATIO) {
    runShopAuthorizedFlow(data, events);
  }
  if (ENABLE_ADMIN_AUTH_FLOW && Math.random() <= ADMIN_FLOW_RATIO) {
    runAdminAuthorizedFlow(data, events);
  }
  if (ENABLE_ANALYTICS_ADMIN_FLOW && Math.random() <= ANALYTICS_ADMIN_FLOW_RATIO) {
    runAnalyticsAdminFlow(events);
  }
}

function runShopAuthorizedFlow(data, events) {
  const loginPage = trackedGet("/login", events, "LOGIN_VIEW");
  if (!loginPage) {
    return;
  }
  const loginCsrf = extractCsrf(loginPage.body);
  const loginRes = trackedPostForm(
    "/login",
    {
      username: SHOP_USERNAME,
      password: SHOP_PASSWORD,
    },
    events,
    loginCsrf,
    "LOGIN_VIEW"
  );
  if (!loginRes) {
    return;
  }
  const accountPage = trackedGet("/account", events, "ACCOUNT_VIEW");
  if (!accountPage || isShopLoginPage(accountPage)) {
    return;
  }
  const accountCsrf = extractCsrf(accountPage.body);
  authActionCounter.add(1);
  trackedPostForm(
    "/account/profile",
    {
      fullName: "Тестовый пользователь",
      phone: "+79990001111",
      email: SHOP_EMAIL,
    },
    events,
    accountCsrf,
    "ACCOUNT_PROFILE_UPDATE"
  );
  authActionCounter.add(1);
  trackedPostForm(
    "/account/address",
    {
      addressStreet: "Тестовая улица",
      addressHouse: "1",
      addressApartment: "1",
      addressEntrance: "1",
      addressFloor: "1",
      addressIntercom: "101",
    },
    events,
    accountCsrf,
    "ACCOUNT_ADDRESS_UPDATE"
  );

  const productPath = pickOne(Array.isArray(data?.productPaths) && data.productPaths.length > 0 ? data.productPaths : ["/catalog"]);
  const productPage = trackedGet(productPath, events, productPath.startsWith("/product/") ? "PRODUCT_VIEW" : "CATALOG_VIEW");
  if (productPage) {
    const productId = extractFirstInputValue(productPage.body, "productId");
    const productCsrf = extractCsrf(productPage.body);
    if (productId) {
      authActionCounter.add(1);
      trackedPostForm(
        "/api/cart/add",
        { productId, quantity: 1 },
        events,
        productCsrf,
        "ADD_TO_CART"
      );
      authActionCounter.add(1);
      trackedPostForm(
        "/api/wishlist/toggle",
        { productId },
        events,
        productCsrf,
        "ADD_TO_WISHLIST"
      );
    }
  }

  const checkoutPage = trackedGet("/checkout", events, "CHECKOUT_VIEW");
  if (checkoutPage && !isShopLoginPage(checkoutPage) && responsePath(checkoutPage).startsWith("/checkout")) {
    const checkoutCsrf = extractCsrf(checkoutPage.body);
    authActionCounter.add(1);
    trackedPostForm(
      "/checkout",
      {
        customerName: "Тестовый пользователь",
        customerEmail: SHOP_EMAIL,
        customerPhone: "+79990001111",
        deliveryType: "PICKUP",
        pickupDate: formatDateOffsetDays(1),
      },
      events,
      checkoutCsrf,
      "CHECKOUT_SUBMIT"
    );
  }

  authActionCounter.add(1);
  trackedPostForm(
    "/account/support/create",
    {
      message: "Автотест: проверка формы обращения из личного кабинета",
    },
    events,
    accountCsrf,
    "ACCOUNT_SUPPORT_CREATE"
  );

  postFormRaw("/logout", {}, accountCsrf);
}

function runAdminAuthorizedFlow(data, events) {
  const loginPage = trackedGet("/login", events, "LOGIN_VIEW");
  if (!loginPage) {
    return;
  }
  const loginCsrf = extractCsrf(loginPage.body);
  const loginRes = trackedPostForm(
    "/login",
    {
      username: ADMIN_USERNAME,
      password: ADMIN_PASSWORD,
    },
    events,
    loginCsrf,
    "LOGIN_VIEW"
  );
  if (!loginRes) {
    return;
  }
  const adminHome = trackedGet("/admin", events, "DASHBOARD_VIEW");
  if (!adminHome || isShopLoginPage(adminHome)) {
    return;
  }
  const adminPages = (Array.isArray(data?.adminPages) ? data.adminPages : [])
    .filter((path) => path.startsWith("/admin"));
  const probes = Math.min(4, Math.max(2, adminPages.length));
  for (let i = 0; i < probes; i += 1) {
    const path = pickOne(adminPages.length > 0 ? adminPages : ["/admin"]);
    trackedGet(path, events);
  }
  const csrf = extractCsrf(adminHome.body);
  postFormRaw("/logout", {}, csrf);
}

function runAnalyticsAdminFlow(events) {
  const loginPage = trackedGet("/analytics-admin/login", events, "ANALYTICS_ADMIN_LOGIN_VIEW");
  if (!loginPage) {
    return;
  }
  if (responsePath(loginPage).startsWith("/analytics-admin/setup")) {
    return;
  }
  const loginCsrf = extractCsrf(loginPage.body);
  const loginRes = trackedPostForm(
    "/analytics-admin/login",
    {
      username: ANALYTICS_ADMIN_USERNAME,
      password: ANALYTICS_ADMIN_PASSWORD,
    },
    events,
    loginCsrf,
    "ANALYTICS_ADMIN_LOGIN_VIEW"
  );
  if (!loginRes) {
    return;
  }
  const dashboard = trackedGet("/analytics-admin/dashboard", events, "ANALYTICS_ADMIN_DASHBOARD_VIEW");
  if (!dashboard || responsePath(dashboard).startsWith("/analytics-admin/login")) {
    return;
  }
  trackedGet("/analytics-admin/dictionaries", events, "ANALYTICS_ADMIN_DICTIONARIES_VIEW");
  const range = buildIsoLast24h();
  trackedGet(`/analytics-admin/api/stages?from=${encodeURIComponent(range.from)}&to=${encodeURIComponent(range.to)}`, events, "ANALYTICS_ADMIN_DASHBOARD_VIEW");
  trackedGet(`/analytics-admin/api/universal?from=${encodeURIComponent(range.from)}&to=${encodeURIComponent(range.to)}&metricCode=FRONTEND_API_DURATION_MS&bucketMinutes=60`, events, "ANALYTICS_ADMIN_DASHBOARD_VIEW");
  const csrf = extractCsrf(dashboard.body);
  postFormRaw("/analytics-admin/logout", {}, csrf);
}

function trackedGet(path, events, forcedCode) {
  const requestId = buildRequestId();
  const res = http.get(`${BASE_URL}${path}`, {
    headers: {
      "X-Trace-Id": requestId,
      "X-Load-Scenario": "live-user-actions-auth",
    },
    tags: {
      endpoint: normalizeEndpointTag(path),
      action: "auth_get",
    },
    timeout: "25s",
  });
  actionCounter.add(1);
  if (isBackendUnavailable(res)) {
    markBackendDown(`GET ${path}`);
    sleep(DOWN_BACKOFF_SEC);
    return null;
  }
  if (isServerOverloaded(res)) {
    overloadBackoffCounter.add(1);
    markBackendDown(`GET ${path} (overloaded)`);
    sleep(OVERLOAD_BACKOFF_SEC);
    return null;
  }
  markBackendUp();
  if (Array.isArray(events)) {
    events.push(buildFrontendEvent(path, "GET", requestId, res, forcedCode));
  }
  sleep(randomPauseSeconds());
  return res;
}

function trackedPostForm(path, form, events, csrf, forcedCode) {
  const requestId = buildRequestId();
  const payload = { ...(form || {}) };
  const headers = {
    "Content-Type": "application/x-www-form-urlencoded",
    "X-Trace-Id": requestId,
    "X-Load-Scenario": "live-user-actions-auth",
  };
  if (csrf?.token) {
    payload._csrf = csrf.token;
    headers[csrf.header || "X-CSRF-TOKEN"] = csrf.token;
  }
  const res = http.post(`${BASE_URL}${path}`, payload, {
    headers,
    tags: {
      endpoint: normalizeEndpointTag(path),
      action: "auth_post",
    },
    timeout: "25s",
  });
  actionCounter.add(1);
  if (isBackendUnavailable(res)) {
    markBackendDown(`POST ${path}`);
    sleep(DOWN_BACKOFF_SEC);
    return null;
  }
  if (isServerOverloaded(res)) {
    overloadBackoffCounter.add(1);
    markBackendDown(`POST ${path} (overloaded)`);
    sleep(OVERLOAD_BACKOFF_SEC);
    return null;
  }
  markBackendUp();
  if (Array.isArray(events)) {
    events.push(buildFrontendEvent(path, "POST", requestId, res, forcedCode));
  }
  sleep(randomPauseSeconds());
  return res;
}

function postFormRaw(path, form, csrf) {
  const payload = { ...(form || {}) };
  const headers = {
    "Content-Type": "application/x-www-form-urlencoded",
    "X-Load-Scenario": "live-user-actions-auth",
  };
  if (csrf?.token) {
    payload._csrf = csrf.token;
    headers[csrf.header || "X-CSRF-TOKEN"] = csrf.token;
  }
  return http.post(`${BASE_URL}${path}`, payload, {
    headers,
    tags: {
      endpoint: normalizeEndpointTag(path),
      action: "auth_post_raw",
    },
    timeout: "20s",
  });
}

function extractCsrf(html) {
  const source = String(html || "");
  const tokenMatch = source.match(/name=["']_csrf["'][^>]*content=["']([^"']+)["']/i);
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
    const tag = match[0] || "";
    const valueMatch = tag.match(/value=["']([^"']+)["']/i);
    if (valueMatch && valueMatch[1]) {
      return valueMatch[1];
    }
    match = pattern.exec(source);
  }
  return "";
}

function responsePath(response) {
  const full = String(response?.url || "");
  if (!full) {
    return "/";
  }
  return full.replace(/^https?:\/\/[^/]+/i, "").split("?")[0] || "/";
}

function isShopLoginPage(response) {
  const path = responsePath(response);
  return path.startsWith("/login");
}

function formatDateOffsetDays(offsetDays) {
  const date = new Date();
  date.setDate(date.getDate() + Number(offsetDays || 0));
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function buildIsoLast24h() {
  const to = new Date();
  const from = new Date(to.getTime() - 24 * 60 * 60 * 1000);
  return {
    from: from.toISOString(),
    to: to.toISOString(),
  };
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
  if (durationMs >= SLOW_REQ_MS && status >= 200 && status < 500) {
    return true;
  }
  const body = String(response?.body || "");
  return body.includes("Connection is not available") || body.includes("Could not open JPA EntityManager");
}

function markBackendDown(action) {
  if (!backendDown) {
    backendDown = true;
    if (downLogs < MAX_DOWN_LOGS_PER_VU) {
      console.warn(`[k6-live] backend unavailable, will retry with backoff (${DOWN_BACKOFF_SEC}s), action=${action}`);
      downLogs += 1;
    }
  }
}

function markBackendUp() {
  if (backendDown) {
    backendDown = false;
    if (downLogs < MAX_DOWN_LOGS_PER_VU) {
      console.warn("[k6-live] backend is back, resuming normal traffic");
      downLogs += 1;
    }
  }
}

function pickPath(data) {
  const staticPages = Array.isArray(data?.staticPages) ? data.staticPages : ["/", "/catalog"];
  const adminPages = Array.isArray(data?.adminPages) ? data.adminPages : [];
  const productPaths = Array.isArray(data?.productPaths) ? data.productPaths : [];
  const categoryPaths = Array.isArray(data?.categoryPaths) ? data.categoryPaths : [];

  if (adminPages.length > 0 && Math.random() < ADMIN_PATH_RATIO) {
    return pickOne(adminPages);
  }

  const roll = Math.random();
  if (roll < 0.62) {
    return pickOne(staticPages);
  }
  if (roll < 0.86 && productPaths.length > 0) {
    return pickOne(productPaths);
  }
  if (roll < 0.96 && categoryPaths.length > 0) {
    return pickOne(categoryPaths);
  }

  const q = pickOne(["phone", "laptop", "audio", "gaming", "watch"]);
  const sort = pickOne(["price_asc", "price_desc", "new", "popular"]);
  return `/catalog?q=${encodeURIComponent(q)}&sort=${encodeURIComponent(sort)}`;
}

function buildFrontendEvent(path, method, traceId, response, forcedCode) {
  const syntheticDuration = randomInt(35, 420);
  const durationMs = normalizeDurationMs(Number(response?.timings?.duration || syntheticDuration));
  const statusCode = Number(response?.status || 200);
  const isError = statusCode >= 400;
  const emulateErrorEvent = isError || Math.random() < ERROR_EVENT_RATIO;
  const moduleCode = resolveModuleCode(path);
  const uiAction = pickUiAction(path);
  const resolvedCode = resolveEventCodeForPath(path, moduleCode);

  if (emulateErrorEvent) {
    return {
      code: "FRONTEND_JS_ERROR",
      moduleCode,
      pagePath: path,
      requestPath: path,
      httpMethod: method,
      traceId,
      statusCode,
      error: true,
      errorMessage: isError ? `HTTP ${statusCode} while opening page` : "Synthetic UI error sample",
      metricsNum: {
        FRONTEND_HTTP_STATUS: statusCode || 500,
      },
      metricsText: {
        FRONTEND_PAGE_URL: path,
        FRONTEND_ERROR_MESSAGE: isError ? `HTTP ${statusCode}` : "Synthetic UI error sample",
        FRONTEND_TRACE_ID: traceId,
      },
    };
  }

  const code = String(forcedCode || resolvedCode || "FRONTEND_PAGE_LOAD").trim();
  return {
    code,
    moduleCode,
    pagePath: path,
    requestPath: path,
    httpMethod: method,
    traceId,
    statusCode: statusCode || 200,
    error: false,
    errorMessage: null,
    metricsNum: {
      FRONTEND_HTTP_STATUS: statusCode || 200,
      FRONTEND_API_DURATION_MS: durationMs,
      FRONTEND_LOAD_EVENT_MS: durationMs + randomInt(8, 120),
      FRONTEND_DOM_CONTENT_LOADED_MS: Math.max(1, durationMs - randomInt(0, 20)),
    },
    metricsText: {
      FRONTEND_PAGE_URL: path,
      FRONTEND_API_URL: path,
      FRONTEND_API_METHOD: method,
      FRONTEND_TRACE_ID: traceId,
      FRONTEND_CUSTOM_ATTRS_JSON: JSON.stringify({
        action: uiAction,
        zone: pickOne(["header", "menu", "catalog", "card", "footer", "filters", "chart", "admin-panel"]),
        source: "k6-live-user-actions",
      }),
    },
  };
}

function resolveModuleCode(path) {
  const value = normalizePathOnly(path);
  if (value.startsWith("/analytics-admin") || value.startsWith("/admin")) {
    return "ADMIN";
  }
  if (value.startsWith("/catalog") || value.startsWith("/product/") || value.startsWith("/category/") || value.startsWith("/cart") || value.startsWith("/wishlist")) {
    return "SHOP";
  }
  return "DEFAULT";
}

function pickUiAction(path) {
  const value = normalizePathOnly(path);
  if (value.startsWith("/analytics-admin")) {
    return pickOne(["open-dashboard", "change-preset", "toggle-metric", "open-filter-panel", "switch-tab"]);
  }
  if (value.startsWith("/admin")) {
    return pickOne(["open-list", "open-details", "change-page", "use-search"]);
  }
  if (value.startsWith("/catalog")) {
    return pickOne(["search", "sort-change", "pagination", "open-card"]);
  }
  if (value.startsWith("/product/")) {
    return pickOne(["open-product", "open-reviews", "add-to-cart"]);
  }
  if (value.startsWith("/category/")) {
    return pickOne(["open-category", "apply-filter", "open-product-card"]);
  }
  return pickOne(["view", "scroll", "click"]);
}

function normalizeDurationMs(rawValue) {
  const numeric = Number(rawValue);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return randomInt(35, 420);
  }
  const rounded = Math.round(numeric);
  if (rounded < 1) {
    return 1;
  }
  if (rounded > MAX_REPORTED_DURATION_MS) {
    return MAX_REPORTED_DURATION_MS;
  }
  return rounded;
}

function resolveEventCodeForPath(path, moduleCode) {
  const normalizedPath = normalizePathOnly(path);

  const exact = {
    "/": "HOME_VIEW",
    "/catalog": "CATALOG_VIEW",
    "/about": "ABOUT_VIEW",
    "/contacts": "CONTACTS_VIEW",
    "/delivery": "DELIVERY_VIEW",
    "/support": "SUPPORT_PAGE_VIEW",
    "/login": "LOGIN_VIEW",
    "/register": "REGISTER_VIEW",
    "/logout": "LOGOUT",
    "/account": "ACCOUNT_VIEW",
    "/account/profile": "ACCOUNT_PROFILE_UPDATE",
    "/account/address": "ACCOUNT_ADDRESS_UPDATE",
    "/account/support/create": "ACCOUNT_SUPPORT_CREATE",
    "/checkout": "CHECKOUT_VIEW",
    "/api/cart/add": "ADD_TO_CART",
    "/api/cart/increment": "ADD_TO_CART",
    "/api/cart/decrement": "ADD_TO_CART",
    "/api/cart/toggle-one": "ADD_TO_CART",
    "/api/wishlist/toggle": "ADD_TO_WISHLIST",
    "/cart": "CART_VIEW",
    "/wishlist": "WISHLIST_VIEW",
    "/reviews": "REVIEWS_PAGE_VIEW",
    "/admin": "DASHBOARD_VIEW",
    "/admin/products": "PRODUCT_LIST_VIEW",
    "/admin/orders": "ORDER_LIST_VIEW",
    "/admin/users": "USER_LIST_VIEW",
    "/admin/reviews": "REVIEW_LIST_VIEW",
    "/admin/support": "SUPPORT_LIST_VIEW",
    "/admin/files": "FILE_LIST_VIEW",
    "/admin/filters": "FILTER_LIST_VIEW",
    "/admin/categories": "CATEGORY_LIST_VIEW",
  };

  if (exact[normalizedPath]) {
    return exact[normalizedPath];
  }
  if (normalizedPath.startsWith("/category/")) {
    return "CATEGORY_VIEW";
  }
  if (normalizedPath.startsWith("/product/")) {
    return "PRODUCT_VIEW";
  }
  if (normalizedPath.startsWith("/account/orders/") && normalizedPath.endsWith("/cancel")) {
    return "ACCOUNT_ORDER_CANCEL";
  }
  if (normalizedPath.startsWith("/account/orders/") && normalizedPath.endsWith("/update")) {
    return "ACCOUNT_ORDER_UPDATE";
  }
  if (normalizedPath.startsWith("/analytics-admin/dashboard")) {
    return "ANALYTICS_ADMIN_DASHBOARD_VIEW";
  }
  if (normalizedPath.startsWith("/analytics-admin/dictionaries")) {
    return "ANALYTICS_ADMIN_DICTIONARIES_VIEW";
  }
  if (normalizedPath.startsWith("/analytics-admin/login")) {
    return "ANALYTICS_ADMIN_LOGIN_VIEW";
  }
  if (moduleCode === "ADMIN") {
    return "ADMIN_PAGE_VIEW";
  }
  return "FRONTEND_PAGE_LOAD";
}

function normalizePathOnly(value) {
  const raw = String(value || "").trim();
  if (!raw) {
    return "/";
  }
  const withoutQuery = raw.split("?")[0] || "/";
  return withoutQuery.startsWith("/") ? withoutQuery : `/${withoutQuery}`;
}

function normalizeEndpointTag(path) {
  if (String(path).startsWith("/product/")) return "/product/{slug}";
  if (String(path).startsWith("/category/")) return "/category/{slug}";
  if (String(path).startsWith("/catalog?")) return "/catalog?*";
  return path;
}

function buildRequestId() {
  const randomSuffix = Math.random().toString(36).slice(2, 12);
  return `k6-live-${__VU}-${__ITER}-${randomSuffix}`.slice(0, 64);
}

function extractUniquePaths(html, pattern) {
  const matches = String(html || "").match(pattern) || [];
  return Array.from(new Set(matches));
}

function randomPauseSeconds() {
  const min = Math.min(PAUSE_MIN_MS, PAUSE_MAX_MS);
  const max = Math.max(PAUSE_MIN_MS, PAUSE_MAX_MS);
  const ms = min + Math.random() * (max - min);
  return ms / 1000;
}

function randomInt(min, max) {
  const lo = Math.floor(Math.min(min, max));
  const hi = Math.floor(Math.max(min, max));
  return Math.floor(Math.random() * (hi - lo + 1)) + lo;
}

function pickOne(items) {
  if (!Array.isArray(items) || items.length === 0) {
    return "/";
  }
  return items[Math.floor(Math.random() * items.length)];
}

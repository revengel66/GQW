import http from "k6/http";
import { sleep } from "k6";
import { Counter, Rate } from "k6/metrics";

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const VUS = Number(__ENV.VUS || 1);
const DURATION = __ENV.DURATION || "168h";
const MIN_PAUSE_SEC = Number(__ENV.MIN_PAUSE_SEC || 3);
const MAX_PAUSE_SEC = Number(__ENV.MAX_PAUSE_SEC || 10);
const PAGE_PING_RATIO = Number(__ENV.PAGE_PING_RATIO || 0.12);
const DOWN_BACKOFF_SEC = Number(__ENV.DOWN_BACKOFF_SEC || 5);

const ingestTotal = new Counter("lite_ingest_total");
const ingestFailed = new Counter("lite_ingest_failed_total");
const ingestAcceptedRate = new Rate("lite_ingest_accepted_rate");
const pagePingFailed = new Counter("lite_page_ping_failed_total");

const PAGES = [
  "/",
  "/catalog",
  "/about",
  "/contacts",
  "/delivery",
  "/support",
  "/cart",
  "/wishlist",
];

export const options = {
  scenarios: {
    background_lite: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
      gracefulStop: "0s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.20"],
    lite_ingest_accepted_rate: ["rate>0.70"],
  },
};

let backendDown = false;

export default function () {
  // Редкий «пинг» страницы, чтобы был минимальный реалистичный фон UI-трафика.
  if (Math.random() < PAGE_PING_RATIO) {
    const path = pickOne(PAGES);
    const res = http.get(`${BASE_URL}${path}`, {
      tags: { action: "lite_page_ping", endpoint: normalizeEndpointTag(path) },
      timeout: "20s",
    });
    if (isUnavailable(res)) {
      pagePingFailed.add(1);
      markDown();
      sleep(DOWN_BACKOFF_SEC);
      return;
    }
    markUp();
  }

  const payload = {
    events: [buildEvent()],
  };

  const ingestRes = http.post(
    `${BASE_URL}/api/analytics/frontend/ingest`,
    JSON.stringify(payload),
    {
      headers: { "Content-Type": "application/json" },
      tags: { action: "lite_ingest", endpoint: "/api/analytics/frontend/ingest" },
      timeout: "20s",
    }
  );

  if (isUnavailable(ingestRes)) {
    ingestTotal.add(1);
    ingestFailed.add(1);
    ingestAcceptedRate.add(false);
    markDown();
    sleep(DOWN_BACKOFF_SEC);
    return;
  }

  markUp();
  const accepted = ingestRes.status === 202;
  ingestTotal.add(1);
  ingestAcceptedRate.add(accepted);
  if (!accepted) {
    ingestFailed.add(1);
  }

  sleep(randomBetween(MIN_PAUSE_SEC, MAX_PAUSE_SEC));
}

function buildEvent() {
  const path = pickOne(PAGES);
  const traceId = buildTraceId();
  const durationMs = Math.floor(randomBetween(40, 450));
  const statusCode = 200;
  const eventCode = Math.random() < 0.6 ? "FRONTEND_PAGE_LOAD" : "FRONTEND_API_CALL";

  return {
    code: eventCode,
    moduleCode: "DEFAULT",
    pagePath: path,
    requestPath: path,
    httpMethod: "GET",
    traceId,
    statusCode,
    error: false,
    errorMessage: null,
    metricsNum: {
      FRONTEND_HTTP_STATUS: statusCode,
      FRONTEND_API_DURATION_MS: durationMs,
      FRONTEND_LOAD_EVENT_MS: durationMs + Math.floor(randomBetween(5, 80)),
      FRONTEND_DOM_CONTENT_LOADED_MS: Math.max(1, durationMs - Math.floor(randomBetween(0, 20))),
    },
    metricsText: {
      FRONTEND_PAGE_URL: path,
      FRONTEND_API_URL: path,
      FRONTEND_API_METHOD: "GET",
      FRONTEND_TRACE_ID: traceId,
      FRONTEND_CUSTOM_ATTRS_JSON: JSON.stringify({
        source: "k6-background-lite",
        action: pickOne(["view", "scroll", "click"]),
        zone: pickOne(["header", "menu", "catalog", "card", "footer"]),
      }),
    },
  };
}

function isUnavailable(response) {
  return Number(response?.status || 0) === 0;
}

function markDown() {
  backendDown = true;
}

function markUp() {
  backendDown = false;
}

function normalizeEndpointTag(path) {
  if (String(path).startsWith("/product/")) return "/product/{slug}";
  if (String(path).startsWith("/category/")) return "/category/{slug}";
  return path;
}

function buildTraceId() {
  const rnd = Math.random().toString(36).slice(2, 12);
  return `k6-lite-${__VU}-${__ITER}-${rnd}`.slice(0, 64);
}

function pickOne(items) {
  if (!Array.isArray(items) || items.length === 0) {
    return "/";
  }
  return items[Math.floor(Math.random() * items.length)];
}

function randomBetween(min, max) {
  const lo = Math.min(min, max);
  const hi = Math.max(min, max);
  return lo + Math.random() * (hi - lo);
}


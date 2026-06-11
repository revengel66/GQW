import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate } from "k6/metrics";

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const PAYLOAD_MODE = (__ENV.PAYLOAD_MODE || "safe").toLowerCase();

const START_VUS = Number(__ENV.START_VUS || 5);
const TARGET_VUS = Number(__ENV.TARGET_VUS || 30);
const RAMP_UP = __ENV.RAMP_UP || "1m";
const STEADY = __ENV.STEADY || "5m";
const RAMP_DOWN = __ENV.RAMP_DOWN || "1m";
const ITER_SLEEP_SEC = Number(__ENV.ITER_SLEEP_SEC || 0.2);

const FAIL_RATE_THRESHOLD = __ENV.FAIL_RATE_THRESHOLD || "rate<0.02";
const P95_MS_THRESHOLD = __ENV.P95_MS_THRESHOLD || "p(95)<800";
const P99_MS_THRESHOLD = __ENV.P99_MS_THRESHOLD || "p(99)<1500";

const ingestAccepted = new Counter("ingest_accepted_total");
const ingestRejected = new Counter("ingest_rejected_total");
const ingestAcceptedRate = new Rate("ingest_accepted_rate");

export const options = {
  scenarios: {
    analytics_ingest: {
      executor: "ramping-vus",
      startVUs: START_VUS,
      stages: [
        { duration: RAMP_UP, target: TARGET_VUS },
        { duration: STEADY, target: TARGET_VUS },
        { duration: RAMP_DOWN, target: 0 },
      ],
      gracefulRampDown: "30s",
    },
  },
  thresholds: {
    http_req_failed: [FAIL_RATE_THRESHOLD],
    http_req_duration: [P95_MS_THRESHOLD, P99_MS_THRESHOLD],
    ingest_accepted_rate: ["rate>0.95"],
  },
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
};

function buildPayload() {
  const suffix = `${__VU}-${__ITER}-${Date.now()}`;
  if (PAYLOAD_MODE === "safe") {
    // SAFE mode: payload is intentionally skipped by FrontendAnalyticsIngestService.shouldSkipFrontendPayload()
    // This allows stable endpoint load testing without writing analytics records.
    return JSON.stringify({
      events: [
        {
          code: "FRONTEND_API_CALL",
          pagePath: "/analytics-admin/dashboard",
          requestPath: "/analytics-admin/dashboard",
          httpMethod: "POST",
          traceId: `k6-trace-safe-${suffix}`,
          statusCode: 200,
          error: false,
          errorMessage: null,
          metricsNum: {
            FRONTEND_HTTP_STATUS: 202,
          },
          metricsText: {
            FRONTEND_API_URL: "/api/analytics/frontend/ingest",
            FRONTEND_API_METHOD: "POST",
          },
        },
      ],
    });
  }

  return JSON.stringify({
    events: [
      {
        code: "FRONTEND_JS_ERROR",
        pagePath: "/product/load-test",
        requestPath: "/product/load-test",
        httpMethod: "GET",
        traceId: `k6-trace-${suffix}`,
        statusCode: 500,
        error: true,
        errorMessage: "k6 synthetic frontend error",
        metricsNum: {
          FRONTEND_HTTP_STATUS: 500,
        },
        metricsText: {
          FRONTEND_ERROR_MESSAGE: "k6 synthetic frontend error",
        },
      },
    ],
  });
}

export default function () {
  const response = http.post(`${BASE_URL}/api/analytics/frontend/ingest`, buildPayload(), {
    headers: {
      "Content-Type": "application/json",
    },
    tags: {
      endpoint: "frontend_ingest",
      test_type: "analytics_stability",
    },
  });

  const accepted = response.status === 202;
  const ok = check(response, {
    "ingest status is 202": () => accepted,
  });

  ingestAcceptedRate.add(accepted);
  if (accepted) {
    ingestAccepted.add(1);
  } else {
    ingestRejected.add(1);
  }

  if (!ok) {
    console.error(
      `unexpected status=${response.status}, body=${String(response.body).slice(0, 300)}`
    );
  }

  sleep(ITER_SLEEP_SEC);
}

/*
Usage examples:

1) Baseline (5m steady):
k6 run ^
  -e BASE_URL=http://localhost:8080 ^
  -e PAYLOAD_MODE=safe ^
  --summary-export=reports/k6_ingest_baseline.json ^
  scripts/analytics-ingest-stability.js

2) Restart scenario (10m steady, restart app manually in the middle):
k6 run ^
  -e BASE_URL=http://localhost:8080 ^
  -e PAYLOAD_MODE=safe ^
  -e TARGET_VUS=40 ^
  -e STEADY=10m ^
  --summary-export=reports/k6_ingest_restart.json ^
  scripts/analytics-ingest-stability.js

3) Full ingest mode (writes analytics events, may expose server-side issues):
k6 run ^
  -e BASE_URL=http://localhost:8080 ^
  -e PAYLOAD_MODE=full ^
  --summary-export=reports/k6_ingest_full.json ^
  scripts/analytics-ingest-stability.js
*/

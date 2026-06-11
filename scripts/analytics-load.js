import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const ERROR_RATIO = Number(__ENV.ERROR_RATIO || "0.35");

const scenarioDuration = {
    baseline: __ENV.BASELINE_DURATION || "3m",
    spike: __ENV.SPIKE_DURATION || "2m",
    error: __ENV.ERROR_DURATION || "3m"
};

const reqDuration = new Trend("shop_request_duration_ms", true);
const status500 = new Counter("shop_status_500_count");
const status4xx = new Counter("shop_status_4xx_count");
const status2xx = new Counter("shop_status_2xx_count");

export const options = {
    scenarios: {
        baseline: {
            executor: "constant-arrival-rate",
            exec: "baselineScenario",
            rate: Number(__ENV.BASELINE_RPS || "10"),
            timeUnit: "1s",
            duration: scenarioDuration.baseline,
            preAllocatedVUs: Number(__ENV.BASELINE_VUS || "20"),
            maxVUs: Number(__ENV.BASELINE_MAX_VUS || "80")
        },
        spike: {
            executor: "ramping-arrival-rate",
            exec: "spikeScenario",
            startTime: scenarioDuration.baseline,
            startRate: Number(__ENV.SPIKE_START_RPS || "15"),
            timeUnit: "1s",
            preAllocatedVUs: Number(__ENV.SPIKE_VUS || "40"),
            maxVUs: Number(__ENV.SPIKE_MAX_VUS || "220"),
            stages: [
                { target: Number(__ENV.SPIKE_PEAK_RPS || "120"), duration: __ENV.SPIKE_RAMP_UP || "45s" },
                { target: Number(__ENV.SPIKE_PEAK_RPS || "120"), duration: __ENV.SPIKE_HOLD || "45s" },
                { target: Number(__ENV.SPIKE_END_RPS || "20"), duration: __ENV.SPIKE_RAMP_DOWN || "30s" }
            ]
        },
        error_wave: {
            executor: "constant-arrival-rate",
            exec: "errorScenario",
            startTime: addDurations(scenarioDuration.baseline, scenarioDuration.spike),
            rate: Number(__ENV.ERROR_RPS || "18"),
            timeUnit: "1s",
            duration: scenarioDuration.error,
            preAllocatedVUs: Number(__ENV.ERROR_VUS || "30"),
            maxVUs: Number(__ENV.ERROR_MAX_VUS || "120")
        }
    },
    thresholds: {
        "http_req_duration{phase:baseline}": ["p(95)<2500"],
        "http_req_duration{phase:spike}": ["p(95)<6000"],
        "http_req_failed{phase:error}": ["rate<0.8"]
    }
};

export function setup() {
    const pagesToScan = ["/", "/catalog"];
    const candidatePaths = new Set();
    const defaultPaths = ["/", "/catalog"];

    for (const path of pagesToScan) {
        const res = http.get(`${BASE_URL}${path}`, {
            tags: { phase: "setup", endpoint: path },
            timeout: "20s"
        });
        if (res.status >= 200 && res.status < 400 && res.body) {
            for (const productPath of extractProductPaths(res.body)) {
                candidatePaths.add(productPath);
            }
        }
    }

    const validProductPaths = Array.from(candidatePaths);
    return {
        validProductPaths,
        defaultPaths
    };
}

export function baselineScenario(data) {
    doRequest("baseline", data, false);
    sleep(randomBetween(0.05, 0.25));
}

export function spikeScenario(data) {
    doRequest("spike", data, false);
    sleep(randomBetween(0.01, 0.08));
}

export function errorScenario(data) {
    const forceInvalid = Math.random() < ERROR_RATIO;
    doRequest("error", data, forceInvalid);
    sleep(randomBetween(0.03, 0.15));
}

function doRequest(phase, data, forceInvalid) {
    const path = forceInvalid ? invalidProductPath() : pickPath(data);
    const traceId = buildTraceId(phase);

    const params = {
        headers: {
            "X-Trace-Id": traceId,
            "X-Load-Phase": phase
        },
        tags: {
            phase,
            endpoint: path.startsWith("/product/") ? "/product/{slug}" : path
        },
        timeout: "30s"
    };

    const res = http.get(`${BASE_URL}${path}`, params);
    reqDuration.add(res.timings.duration, { phase });

    if (res.status >= 500) {
        status500.add(1, { phase });
    } else if (res.status >= 400) {
        status4xx.add(1, { phase });
    } else if (res.status >= 200 && res.status < 300) {
        status2xx.add(1, { phase });
    }

    check(res, {
        "response arrived": (r) => r.status > 0
    });
}

function pickPath(data) {
    const valid = data.validProductPaths || [];
    if (valid.length > 0) {
        return valid[Math.floor(Math.random() * valid.length)];
    }
    const fallback = data.defaultPaths || ["/"];
    return fallback[Math.floor(Math.random() * fallback.length)];
}

function invalidProductPath() {
    const suffix = Math.random().toString(36).slice(2, 11);
    return `/product/not-found-${suffix}`;
}

function extractProductPaths(html) {
    const matches = html.match(/\/product\/[A-Za-z0-9._~%-]+/g) || [];
    return Array.from(new Set(matches));
}

function buildTraceId(phase) {
    const rnd = Math.random().toString(36).slice(2, 14);
    // Format is accepted by TraceIdFilter: letters/digits/_-. and length 8..64
    return `lt-${phase}-${__VU}-${__ITER}-${rnd}`.slice(0, 64);
}

function randomBetween(min, max) {
    return min + (max - min) * Math.random();
}

function addDurations(first, second) {
    // k6 accepts concatenated relative startTime in simple cases poorly,
    // so we convert only when both are minutes/seconds.
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

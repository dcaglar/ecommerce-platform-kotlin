import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';

// --- Custom Metrics for Grafana ---
const stepCreateDuration = new Trend('http_req_duration_create');
const stepAuthDuration = new Trend('http_req_duration_auth');
const error5xxCounter = new Counter('errors_5xx_total');
const fullFlowSuccess = new Counter('full_flow_success');

// --- Initialization ---
const ACCESS_TOKEN = open('../keycloak/output/jwt/payment-service.token').replace(/[\r\n]+$/, '');
const endpoints = JSON.parse(open('../infra/endpoints.json'));
const currentTestType = __ENV.TEST_TYPE || 'smoke_V1_D1';

// --- Constants ---
const ALLOWED_SELLERS = ['SELLER-111', 'SELLER-222', 'SELLER-333'];

// --- Scenario Definitions ---
const allScenarios = {
    smoke_V1_D1: {
        executor: 'constant-vus',
        vus: 1,
        duration: '1m',
        tags: { test_type: 'smoke_V1_D1' },
    },
    smoke_V5_D10: {
        executor: 'constant-vus',
        vus: 5,
        duration: '10m',
        tags: { test_type: 'smoke_V5_D10' },
    },
    load100_ramping_arrival: {
        executor: 'ramping-arrival-rate',
        startRate: 0,
        timeUnit: '1s',
        preAllocatedVUs: 100,
        maxVUs: 1500,
        stages: [
            { duration: '2m', target: 100 },
            { duration: '5m', target: 100 },
            { duration: '2m', target: 0 },
        ],
        tags: { test_type: 'load100_arrival' },
    },
    constant_rps_50: {
        executor: 'constant-arrival-rate',
        rate: 50,
        timeUnit: '1s',
        duration: '5m',
        preAllocatedVUs: 50,
        maxVUs: 500,
        tags: { test_type: 'constant_rps_50' },
    },
    stress_400_vus: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '2m', target: 200 },
            { duration: '5m', target: 400 },
            { duration: '2m', target: 0 },
        ],
        tags: { test_type: 'stress_400' },
    },
    fixed_work_1000_total: {
        executor: 'shared-iterations',
        vus: 50,
        iterations: 1000,
        maxDuration: '10m',
        tags: { test_type: 'shared_iter' },
    },
    data_seed_50_total: {
        executor: 'per-vu-iterations',
        vus: 10,
        iterations: 5,
        maxDuration: '5m',
        tags: { test_type: 'per_vu_iter' },
    },
    spike_1400: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '30s', target: 100 },
            { duration: '10s', target: 1400 },
            { duration: '2m', target: 1400 },
            { duration: '1m', target: 0 },
        ],
        tags: { test_type: 'spike_1400' },
    }
};

export const options = {
    scenarios: {
        [currentTestType]: allScenarios[currentTestType] || allScenarios.smoke_V1_D1,
    },
    thresholds: {
        'http_req_failed': ['rate <= 0.05'],
        'http_req_duration{name:CreateIntent}': ['p(95) <= 800'],
        'http_req_duration{name:Authorize}': ['p(95) <= 800'],
    },
};

// --- Helpers ---
function randomId(prefix) { return `${prefix}-${Math.floor(Math.random() * 1e12)}`; }

function getUniqueSellers(count) {
    const shuffled = [...ALLOWED_SELLERS].sort(() => 0.5 - Math.random());
    return shuffled.slice(0, count);
}

// --- API Steps ---

function createIntent(params) {
    const url = `${endpoints.base_url}/api/v1/payments`;

    // Pick 2 unique sellers and create payment orders for them
    const selectedSellers = getUniqueSellers(2);

    const payload = JSON.stringify({
        orderId: randomId('ORD'),
        buyerId: "BUYER-1450",
        totalAmount: { quantity: 2900, currency: "EUR" },
        paymentOrders: selectedSellers.map(sellerId => ({
            sellerId: sellerId,
            amount: { quantity: 1450, currency: "EUR" }
        }))
    });

    const res = http.post(url, payload, Object.assign({}, params, { tags: { name: 'CreateIntent' } }));

    if (res.status >= 500) error5xxCounter.add(1);

    const ok = check(res, { 'intent 201': (r) => r.status === 201 });
    if (ok) {
        stepCreateDuration.add(res.timings.duration);
        return res.json().paymentIntentId;
    }
    return null;
}

function authorizePayment(id, params) {
    const url = `${endpoints.base_url}/api/v1/payments/${id}/authorize`;
    const payload = JSON.stringify({
        paymentMethod: { type: "CardToken", token: "tok_visa", cvc: "123" }
    });

    const res = http.post(url, payload, Object.assign({}, params, { tags: { name: 'Authorize' } }));

    if (res.status >= 500) error5xxCounter.add(1);

    const ok = check(res, { 'auth 200/201': (r) => r.status === 200 || r.status === 201 });
    if (ok) {
        stepAuthDuration.add(res.timings.duration);
    }
    return ok;
}

// --- Main Journey ---

export default function () {
    const createParams = {
        headers: {
            'Host': `${endpoints.host_header}`,
            'Authorization': `Bearer ${ACCESS_TOKEN}`,
            'Idempotency-Key': randomId('IDEM-C'),
            'Content-Type': 'application/json'
        },
        timeout: '7s'
    };

    const paymentId = createIntent(createParams);

    if (paymentId) {
        // Human "Thinking Time" (Random 1-3 seconds)
        sleep(Math.random() * 2 + 1);

        const authParams = {
            headers: {
                'Host': `${endpoints.host_header}`,
                'Authorization': `Bearer ${ACCESS_TOKEN}`,
                'Idempotency-Key': randomId('IDEM-A'),
                'Content-Type': 'application/json'
            },
            timeout: '7s'
        };

        const success = authorizePayment(paymentId, authParams);

        if (success) {
            fullFlowSuccess.add(1);
        }
    }
}
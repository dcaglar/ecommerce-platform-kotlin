import http from 'k6/http';
import { check, sleep } from 'k6';

// --- 1. Load Configurations & Credentials ---
const ACCESS_TOKEN = open('../keycloak/output/jwt/payment-service.token').replace(/[\r\n]+$/, '');
const endpoints = JSON.parse(open('../infra/endpoints.json'));

// --- 2. Multi-Profile Workload Scenarios ---
const SCENARIOS = {
    // A. Smoke Test: Minimal load for validating that scripts and APIs work correctly
    smoke: {
        executor: 'constant-vus',
        vus: 1,
        duration: '1m',
        tags: { test_type: 'smoke' },
    },
    // B. Average Load Test: Simulates expected day-to-day typical user traffic
    average: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '2m', target: 20 },  // Warm-up to 20 users
            { duration: '5m', target: 20 },  // Maintain typical active traffic
            { duration: '1m', target: 0 },   // Cool-down
        ],
        tags: { test_type: 'average_load' },
    },
    // C. Stress Test: Push system past average limits to see how resources handle pressure
    stress: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '3m', target: 100 }, // Ramps up to a high load
            { duration: '10m', target: 100 },// Maintains sustained high load
            { duration: '2m', target: 0 },   // Cool-down
        ],
        tags: { test_type: 'stress' },
    },
    // D. Soak Test: Continuous moderate load over a long time to check memory leaks & slow degradation
    soak: {
        executor: 'constant-vus',
        vus: 30,
        duration: '1h',                     // Long running execution
        tags: { test_type: 'soak' },
    },
    // E. Spike Test: Sudden extreme burst of massive traffic to test caching, buffering, and fast autoscaling
    spike: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '10s', target: 5 },   // Normal baseline
            { duration: '10s', target: 400 }, // Sudden immediate spike to 400 VUs!
            { duration: '3m', target: 400 },  // Hold peak
            { duration: '1m', target: 0 },    // Ramp down
        ],
        tags: { test_type: 'spike' },
    },
    // F. Breakpoint Test: Step-wise steady ramp to identify the absolute limits of server failure
    breakpoint: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '15m', target: 1000 }, // Slowly ramp up to 1000 users until failure
        ],
        tags: { test_type: 'breakpoint' },
    }
};

// Select the profile using an environment variable (defaults to smoke)
// Example: k6 run -e PROFILE=stress k6-payment-flow.js
const PROFILE = __ENV.PROFILE || 'smoke';

export const options = {
    scenarios: {
        [PROFILE]: SCENARIOS[PROFILE] || SCENARIOS.smoke
    },
    thresholds: {
        // SLA Checks
        'http_req_failed': ['rate <= 0.05'],     // Under 5% fail rate
        'http_req_duration': ['p(95) <= 1000'],  // 95% of responses under 1 second
    }
};

// --- 3. Helper Functions ---
function randomId(prefix) {
    return `${prefix}-${Math.floor(Math.random() * 1e8)}`;
}

// --- 4. Main User Journey ---
export default function () {
    const baseUrl = endpoints.base_url;

    const headers = {
        'Host': endpoints.host_header,
        'Authorization': `Bearer ${ACCESS_TOKEN}`,
        'Content-Type': 'application/json',
    };

    // --- STEP A: Create a Payment Intent ---
    const createUrl = `${baseUrl}/api/v1/payments`;
    const createPayload = JSON.stringify({
        orderId: randomId('ORD'),
        buyerId: 'BUYER-1450',
        totalAmount: { quantity: 2900, currency: 'EUR' },
        paymentOrders: [
            { sellerId: 'SELLER-111', amount: { quantity: 1450, currency: 'EUR' } },
            { sellerId: 'SELLER-222', amount: { quantity: 1450, currency: 'EUR' } }
        ]
    });

    const createParams = {
        headers: Object.assign({ 'Idempotency-Key': randomId('IDEM-C') }, headers)
    };

    const createRes = http.post(createUrl, createPayload, createParams);
    const isCreated = check(createRes, {
        '1. Payment Intent created successfully (201)': (r) => r.status === 201
    });

    // --- STEP B: Authorize the Payment ---
    if (isCreated) {
        const paymentIntentId = createRes.json().paymentIntentId;

        // Realistic human pacing delay
        sleep(1.5);

        const authUrl = `${baseUrl}/api/v1/payments/${paymentIntentId}/authorize`;
        const authPayload = JSON.stringify({
            paymentMethod: { type: 'CardToken', token: 'tok_visa', cvc: '123' }
        });

        const authParams = {
            headers: Object.assign({ 'Idempotency-Key': randomId('IDEM-A') }, headers)
        };

        const authRes = http.post(authUrl, authPayload, authParams);
        check(authRes, {
            '2. Payment authorized successfully (200/201)': (r) => r.status === 200 || r.status === 201
        });
    }
}
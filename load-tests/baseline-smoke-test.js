import http from 'k6/http';
import { check } from 'k6';

// --- Configurable via env vars ---
const vus = __ENV.VUS ? parseInt(__ENV.VUS) : 5;
const duration = __ENV.DURATION ? __ENV.DURATION : '5m';
const rps = __ENV.RPS ? __ENV.RPS : '100';

export let options = {
    scenarios: {
        constant_request_rate: {
            executor: "constant-arrival-rate",
            rate: rps,
            timeUnit: "1s",
            duration: duration,
            preAllocatedVUs: vus,
            maxVUs: 300,
        }
    }
};

const BASE_URL = 'http://localhost:8081/payments';
const KEYCLOAK_URL = 'http://localhost:8082';
const REALM = 'ecommerce-platform';
const CLIENT_ID = 'payment-service';

// --- Get token (client_credentials) ---
const ACCESS_TOKEN = open('../keycloak/access.token').replace(/[\r\n]+$/, '');

if (!ACCESS_TOKEN) {
    console.error("access.token is empty! Please run get-token.sh before running k6.");
    throw new Error("Missing access token");
}

// --- k6 Setup: Get one token and pass it to all VUs ---
export function setup() {
    return { authToken: ACCESS_TOKEN };
}

function randomId(prefix) {
    return `${prefix}-${Math.floor(Math.random() * 1e12)}`;
}

function randomAmount(min, max) {
    return parseFloat((Math.random() * (max - min) + min).toFixed(2));
}

// --- k6 main function: makes payment requests ---
export default function (data) {
    const AUTH_TOKEN = data.authToken;
    const paymentOrderCount = Math.floor(Math.random() * 3) + 1; // 1-3 payment orders
    const paymentOrders = [];
    for (let i = 0; i < paymentOrderCount; i++) {
        paymentOrders.push({
            sellerId: randomId('SELLER'),
            amount: {
                value: randomAmount(10, 200),
                currency: 'EUR',
            },
        });
    }
    const totalAmount = paymentOrders.reduce((sum, po) => sum + po.amount.value, 0);
    const payload = JSON.stringify({
        orderId: randomId('ORDER'),
        buyerId: randomId('BUYER'),
        totalAmount: {
            value: parseFloat(totalAmount.toFixed(2)),
            currency: 'EUR',
        },
        paymentOrders,
    });
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${AUTH_TOKEN}`
        },
    };
    const res = http.post(BASE_URL, payload, params);

    // --- Log for 401/debug ---
    if (res.status !== 200) {
        console.error('Token used:', AUTH_TOKEN);
        console.error('Request payload:', payload);
        console.error('Response status:', res.status);
        console.error('Response body:', res.body);
    }
    check(res, {
        'status is 200': (r) => r.status === 200
    });
}
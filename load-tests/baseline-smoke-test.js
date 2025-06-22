import http from 'k6/http';
import { check, sleep } from 'k6';

const vus = __ENV.VUS ? parseInt(__ENV.VUS) : 5;
const duration = __ENV.DURATION ? __ENV.DURATION : '5m';
const rps = __ENV.RPS ? __ENV.RPS : '100';

export let options = {
    /*
    vus: vus, // Number of virtual users (can be set via env var VUS)
    duration: duration, // Test duration (can be set via env var DURATION)
    */
    scenarios: {
            constant_request_rate: {
              executor: "constant-arrival-rate",
              rate: rps, // requests per second
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

// Read secret from file
const secretsFile = open('../keycloak/secrets.txt');
const PAYMENT_SERVICE_CLIENT_SECRET = secretsFile.match(/PAYMENT_SERVICE_CLIENT_SECRET=(.*)/)[1];

// Get token
function getToken() {
    const res = http.post(`${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
        {
            grant_type: 'client_credentials',
            client_id: CLIENT_ID,
            client_secret: PAYMENT_SERVICE_CLIENT_SECRET
        },
        { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    );
    return res.json('access_token');
}

export function setup() {
    const token = getToken();
    return { authToken: token };
}

function randomId(prefix) {
    return `${prefix}-${Math.floor(Math.random() * 1e12)}`;
}

function randomAmount(min, max) {
    return parseFloat((Math.random() * (max - min) + min).toFixed(2));
}

export default function (data) {
    const AUTH_TOKEN = data.authToken;
    const paymentOrderCount = Math.floor(Math.random() * 3) + 1; // 1-3 payment orders
    const paymentOrders = [];
    for (let i = 0; i < paymentOrderCount; i++) {
        paymentOrders.push({
            sellerId: randomId('SELLER'),
            amount: {
                value: randomAmount(10, 200), // random amount between 10 and 200
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
    // Debug: log token and response for troubleshooting 401
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

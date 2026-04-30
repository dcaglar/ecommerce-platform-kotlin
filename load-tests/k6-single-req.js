import http from 'k6/http';
import { check, sleep } from 'k6';

// 1. INIT STAGE
const ACCESS_TOKEN = open('../keycloak/output/jwt/payment-service.token').replace(/[\r\n]+$/, '');
const endpoints = JSON.parse(open('../infra/endpoints.json'));

export const options = {
    vus: 1,
    iterations: 1,
};

// Helper: Shuffles an array and returns the first N elements to ensure uniqueness
function getUniqueSellers(count) {
    const sellers = ["SELLER-111", "SELLER-222", "SELLER-333"];
    // Shuffle using sort and random
    const shuffled = sellers.sort(() => 0.5 - Math.random());
    return shuffled.slice(0, count);
}

const randomId = (prefix) => `${prefix}-${Math.floor(Math.random() * 1e12)}`;

// --- API Methods ---

function createPaymentIntent() {
    const url = `${endpoints.base_url}/api/v1/payments`;

    // Pick 2 UNIQUE sellers
    const selectedSellers = getUniqueSellers(2);

    const payload = JSON.stringify({
        orderId: randomId('ORDER'),
        buyerId: randomId('BUYER'),
        totalAmount: { quantity: 2000, currency: "EUR" },
        paymentOrders: [
            { sellerId: selectedSellers[0], amount: { quantity: 1000, currency: "EUR" } },
            { sellerId: selectedSellers[1], amount: { quantity: 1000, currency: "EUR" } }
        ]
    });

    const params = {
        headers: {
            'Host': `${endpoints.host_header}`,
            'Authorization': `Bearer ${ACCESS_TOKEN}`,
            'Idempotency-Key': randomId('IDEM'),
            'Content-Type': 'application/json'
        },
    };

    return http.post(url, payload, params);
}

function authorizePayment(paymentId) {
    const url = `${endpoints.base_url}/api/v1/payments/${paymentId}/authorize`;

    const payload = JSON.stringify({
        paymentMethod: {
            type: "CardToken", // Matches your Sealed Class @JsonTypeInfo
            token: "tok_visa",
            cvc: "123"
        }
    });

    const params = {
        headers: {
            'Host': `${endpoints.host_header}`,
            'Authorization': `Bearer ${ACCESS_TOKEN}`,
            'Content-Type': 'application/json'
        },
    };

    return http.post(url, payload, params);
}

// --- Main Scenario ---

export default function () {
    // --- STEP 1: Create Intent ---
    const intentRes = createPaymentIntent();

    const intentCheck = check(intentRes, {
        'intent created (201)': (r) => r.status === 201,
    });

    if (!intentCheck) {
        console.error(`Intent failed with status ${intentRes.status}: ${intentRes.body}`);
        return;
    }

    const body = intentRes.json();
    const paymentId = body.paymentIntentId; // Corrected from your DTO

    // --- STEP 2: Thinking Time (2-5s) ---
    sleep(Math.random() * (5 - 2) + 2);

    // --- STEP 3: Authorize ---
    const authRes = authorizePayment(paymentId);

    check(authRes, {
        'authorized (200/201)': (r) => r.status === 200 || r.status === 201,
    });

    console.log(`Success! Order ${body.orderId} processed via Payment ${paymentId}`);
}
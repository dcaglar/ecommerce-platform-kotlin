import http from 'k6/http';
import { check, sleep } from 'k6';

// 1. INIT STAGE: Load the token and config from disk
// We use open() here because it only runs once when the script starts.
const ACCESS_TOKEN = open('../keycloak/output/jwt/payment-service.token').replace(/[\r\n]+$/, '');
const endpoints = JSON.parse(open('../infra/endpoints.json'));

export const options = {
  //vus: 10,
  //duration: '30s',
};

export default function () {
  // 2. SETUP: Define the URL and Payload
  const url = `${endpoints.base_url}/api/v1/payments`;
  const payload = JSON.stringify({
    orderId: "ORDER-1450",
    buyerId: "BUYER-1450",
    totalAmount: { quantity: 2900, currency: "EUR" },
    paymentOrders: [
      { sellerId: "SELLER-111", amount: { quantity: 1450, currency: "EUR" }},
      { sellerId: "SELLER-222", amount: { quantity: 1450, currency: "EUR" }}
    ]
  });

  // 3. HEADERS: We need to translate your curl headers here
   const idempotencyKey = randomId('IDEM');
  const params = {
    headers: {
      'Host': `${endpoints.host_header}`,
       'Authorization': `Bearer ${ACCESS_TOKEN}` ,
       'Idempotency-Key': `${idempotencyKey}`,
      'Content-Type': 'application/json'
          },
  };

  const res = http.post(url, payload, params);
console.log(`Total Duration: ${res.timings.duration} ms`);
  console.log(`  - Blocked (DNS/Queue): ${res.timings.blocked} ms`);
  console.log(`  - Connecting (TCP):    ${res.timings.connecting} ms`);
  console.log(`  - TLS Handshake:       ${res.timings.tls_handshaking} ms`);
  console.log(`  - Sending (Upload):    ${res.timings.sending} ms`);
  console.log(`  - Waiting (TTFB):      ${res.timings.waiting} ms`); // <--- Critical: Server processing time
  console.log(`  - Receiving (Download):${res.timings.receiving} ms`);
  check(res, {
    'is status 201': (r) => r.status === 201,
  });

  sleep(1);
}

function randomId(prefix) { return `${prefix}-${Math.floor(Math.random() * 1e12)}`; }

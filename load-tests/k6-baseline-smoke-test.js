import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution'; // 1. Add this import


const ACCESS_TOKEN = open('../keycloak/output/jwt/payment-service.token').replace(/[\r\n]+$/, '');
const endpoints = JSON.parse(open('../infra/endpoints.json'));

export const options = {
  thresholds: {
    http_req_duration: ['p(95)<600'],s
  },
  scenarios: {
    'warmup_scenario': {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 10,
      maxVUs: 100,
      stages: [{ target: 100, duration: '3m' }],
      startTime: '0s',
    },//starts at t, ends at t+3m
    'human_scenario': { //starts at t+3m ends at t+5m
      executor: 'constant-vus',
      vus: 300,
      duration: '2m',
      startTime: '3m',
    },
    'robot_scenario': { //t+5 and t+7
      executor: 'constant-vus',
      vus: 300,
      duration: '2m',
      startTime: '5m',
    },
    'rps_scenario': { //t+7 between t+17
      executor: 'constant-arrival-rate',
      rate: 300,
      timeUnit: '1s',
      duration: '10m',
      preAllocatedVUs: 100,
      maxVUs: 400,
      startTime: '7m',
    },
  },
};


function randomId(prefix) { return `${prefix}-${Math.floor(Math.random() * 1e12)}`; }


export default function () {
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
  if(res.status != 201){
   console.log(`Request: ${payload}`);
    console.log(`Http status: ${res.status}   Response: ${res.body}`);
/*console.log(`Total Duration: ${res.timings.duration} ms`);
  console.log(`  - Blocked (DNS/Queue): ${res.timings.blocked} ms`);
  console.log(`  - Connecting (TCP):    ${res.timings.connecting} ms`);
  console.log(`  - TLS Handshake:       ${res.timings.tls_handshaking} ms`);
  console.log(`  - Sending (Upload):    ${res.timings.sending} ms`);
  console.log(`  - Waiting (TTFB):      ${res.timings.waiting} ms`); // <--- Critical: Server processing time
  console.log(`  - Receiving (Download):${res.timings.receiving} ms`);
  */

  }
  check(res, {
    'is status 201': (r) => r.status === 201,
  });



  // 2. Use exec.scenario.name to check the scenario
  if (exec.scenario.name === 'human_scenario') {
    sleep(1);
  }
}
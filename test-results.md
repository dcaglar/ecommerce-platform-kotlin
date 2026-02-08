import http from 'k6/http';
import { check, sleep } from 'k6';

// 1. INIT STAGE: Load the token and config from disk
// We use open() here because it only runs once when the script starts.
const ACCESS_TOKEN = open('../keycloak/output/jwt/payment-service.token').replace(/[\r\n]+$/, '');
const endpoints = JSON.parse(open('../infra/endpoints.json'));


export const options = {
vus: 1,
// iteration:2
duration: '10s',
};

/*
export const options = {
stages: [
{ duration: '30s', target: 10 }, // Ramp up from 1 to 10 users over 30 seconds
{ duration: '1m',  target: 10 }, // Stay at 10 users for 1 minute (steady state)
{ duration: '30s', target: 0 },  // Ramp down to 0 users (cleanup)
],
};
*/
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

sleep(0.1);
}

function randomId(prefix) { return `${prefix}-${Math.floor(Math.random() * 1e12)}`; }



dogancaglar@Dogans-MacBook-Pro ecommerce-platform-kotlin % k6 run load-tests/k6_1.js

         /\      Grafana   /‾‾/                                                                                                                                                                                                          
    /\  /  \     |\  __   /  /                                                                                                                                                                                                           
/  \/    \    | |/ /  /   ‾‾\                                                                                                                                                                                                         
/          \   |   (  |  (‾)  |                                                                                                                                                                                                        
/ __________ \  |_|\_\  \_____/

     execution: local
        script: load-tests/k6_1.js
        output: -

     scenarios: (100.00%) 1 scenario, 1 max VUs, 40s max duration (incl. graceful stop):
              * default: 1 looping VUs for 10s (gracefulStop: 30s)

INFO[0000] Total Duration: 756.233 ms                    source=console
INFO[0000]   - Blocked (DNS/Queue): 1.077 ms             source=console
INFO[0000]   - Connecting (TCP):    0.254 ms             source=console
INFO[0000]   - TLS Handshake:       0 ms                 source=console
INFO[0000]   - Sending (Upload):    0.356 ms             source=console
INFO[0000]   - Waiting (TTFB):      714.864 ms           source=console
INFO[0000]   - Receiving (Download):41.013 ms            source=console
INFO[0001] Total Duration: 354.081 ms                    source=console
INFO[0001]   - Blocked (DNS/Queue): 0.004 ms             source=console
INFO[0001]   - Connecting (TCP):    0 ms                 source=console
INFO[0001]   - TLS Handshake:       0 ms                 source=console
INFO[0001]   - Sending (Upload):    0.036 ms             source=console
INFO[0001]   - Waiting (TTFB):      353.941 ms           source=console
INFO[0001]   - Receiving (Download):0.104 ms             source=console
INFO[0001] Total Duration: 511.147 ms                    source=console
INFO[0001]   - Blocked (DNS/Queue): 0.005 ms             source=console
INFO[0001]   - Connecting (TCP):    0 ms                 source=console
INFO[0001]   - TLS Handshake:       0 ms                 source=console
INFO[0001]   - Sending (Upload):    0.035 ms             source=console
INFO[0001]   - Waiting (TTFB):      511.05 ms            source=console
INFO[0001]   - Receiving (Download):0.062 ms             source=console
INFO[0002] Total Duration: 411.773 ms                    source=console
INFO[0002]   - Blocked (DNS/Queue): 0.003 ms             source=console
INFO[0002]   - Connecting (TCP):    0 ms                 source=console
INFO[0002]   - TLS Handshake:       0 ms                 source=console
INFO[0002]   - Sending (Upload):    0.028 ms             source=console
INFO[0002]   - Waiting (TTFB):      411.689 ms           source=console
INFO[0002]   - Receiving (Download):0.056 ms             source=console
INFO[0002] Total Duration: 415.134 ms                    source=console
INFO[0002]   - Blocked (DNS/Queue): 0.003 ms             source=console
INFO[0002]   - Connecting (TCP):    0 ms                 source=console
INFO[0002]   - TLS Handshake:       0 ms                 source=console
INFO[0002]   - Sending (Upload):    0.028 ms             source=console
INFO[0002]   - Waiting (TTFB):      415.055 ms           source=console
INFO[0002]   - Receiving (Download):0.051 ms             source=console
INFO[0003] Total Duration: 356.248 ms                    source=console
INFO[0003]   - Blocked (DNS/Queue): 0.003 ms             source=console
INFO[0003]   - Connecting (TCP):    0 ms                 source=console
INFO[0003]   - TLS Handshake:       0 ms                 source=console
INFO[0003]   - Sending (Upload):    0.018 ms             source=console
INFO[0003]   - Waiting (TTFB):      356.097 ms           source=console
INFO[0003]   - Receiving (Download):0.133 ms             source=console
INFO[0003] Total Duration: 461.866 ms                    source=console
INFO[0003]   - Blocked (DNS/Queue): 0.004 ms             source=console
INFO[0003]   - Connecting (TCP):    0 ms                 source=console
INFO[0003]   - TLS Handshake:       0 ms                 source=console
INFO[0003]   - Sending (Upload):    0.034 ms             source=console
INFO[0003]   - Waiting (TTFB):      461.775 ms           source=console
INFO[0003]   - Receiving (Download):0.057 ms             source=console
INFO[0004] Total Duration: 413.005 ms                    source=console
INFO[0004]   - Blocked (DNS/Queue): 0.004 ms             source=console
INFO[0004]   - Connecting (TCP):    0 ms                 source=console
INFO[0004]   - TLS Handshake:       0 ms                 source=console
INFO[0004]   - Sending (Upload):    0.019 ms             source=console
INFO[0004]   - Waiting (TTFB):      412.811 ms           source=console
INFO[0004]   - Receiving (Download):0.175 ms             source=console
INFO[0004] Total Duration: 377.772 ms                    source=console
INFO[0004]   - Blocked (DNS/Queue): 0.008 ms             source=console
INFO[0004]   - Connecting (TCP):    0 ms                 source=console
INFO[0004]   - TLS Handshake:       0 ms                 source=console
INFO[0004]   - Sending (Upload):    0.043 ms             source=console
INFO[0004]   - Waiting (TTFB):      377.593 ms           source=console
INFO[0004]   - Receiving (Download):0.136 ms             source=console
INFO[0005] Total Duration: 379.874 ms                    source=console
INFO[0005]   - Blocked (DNS/Queue): 0.003 ms             source=console
INFO[0005]   - Connecting (TCP):    0 ms                 source=console
INFO[0005]   - TLS Handshake:       0 ms                 source=console
INFO[0005]   - Sending (Upload):    0.018 ms             source=console
INFO[0005]   - Waiting (TTFB):      379.81 ms            source=console
INFO[0005]   - Receiving (Download):0.046 ms             source=console
INFO[0005] Total Duration: 367.875 ms                    source=console
INFO[0005]   - Blocked (DNS/Queue): 0.003 ms             source=console
INFO[0005]   - Connecting (TCP):    0 ms                 source=console
INFO[0005]   - TLS Handshake:       0 ms                 source=console
INFO[0005]   - Sending (Upload):    0.018 ms             source=console
INFO[0005]   - Waiting (TTFB):      367.698 ms           source=console
INFO[0005]   - Receiving (Download):0.159 ms             source=console
INFO[0006] Total Duration: 415.859 ms                    source=console
INFO[0006]   - Blocked (DNS/Queue): 0.008 ms             source=console
INFO[0006]   - Connecting (TCP):    0 ms                 source=console
INFO[0006]   - TLS Handshake:       0 ms                 source=console
INFO[0006]   - Sending (Upload):    0.018 ms             source=console
INFO[0006]   - Waiting (TTFB):      415.795 ms           source=console
INFO[0006]   - Receiving (Download):0.046 ms             source=console
INFO[0006] Total Duration: 407.923 ms                    source=console
INFO[0006]   - Blocked (DNS/Queue): 0.003 ms             source=console
INFO[0006]   - Connecting (TCP):    0 ms                 source=console
INFO[0006]   - TLS Handshake:       0 ms                 source=console
INFO[0006]   - Sending (Upload):    0.018 ms             source=console
INFO[0006]   - Waiting (TTFB):      407.837 ms           source=console
INFO[0006]   - Receiving (Download):0.068 ms             source=console
INFO[0007] Total Duration: 305.807 ms                    source=console
INFO[0007]   - Blocked (DNS/Queue): 0.003 ms             source=console
INFO[0007]   - Connecting (TCP):    0 ms                 source=console
INFO[0007]   - TLS Handshake:       0 ms                 source=console
INFO[0007]   - Sending (Upload):    0.034 ms             source=console
INFO[0007]   - Waiting (TTFB):      305.702 ms           source=console
INFO[0007]   - Receiving (Download):0.071 ms             source=console
INFO[0007] Total Duration: 368.415 ms                    source=console
INFO[0007]   - Blocked (DNS/Queue): 0.003 ms             source=console
INFO[0007]   - Connecting (TCP):    0 ms                 source=console
INFO[0007]   - TLS Handshake:       0 ms                 source=console
INFO[0007]   - Sending (Upload):    0.017 ms             source=console
INFO[0007]   - Waiting (TTFB):      368.332 ms           source=console
INFO[0007]   - Receiving (Download):0.066 ms             source=console
INFO[0008] Total Duration: 560.93 ms                     source=console
INFO[0008]   - Blocked (DNS/Queue): 0.004 ms             source=console
INFO[0008]   - Connecting (TCP):    0 ms                 source=console
INFO[0008]   - TLS Handshake:       0 ms                 source=console
INFO[0008]   - Sending (Upload):    0.02 ms              source=console
INFO[0008]   - Waiting (TTFB):      560.68 ms            source=console
INFO[0008]   - Receiving (Download):0.23 ms              source=console
INFO[0008] Total Duration: 414.94 ms                     source=console
INFO[0008]   - Blocked (DNS/Queue): 0.011 ms             source=console
INFO[0008]   - Connecting (TCP):    0 ms                 source=console
INFO[0008]   - TLS Handshake:       0 ms                 source=console
INFO[0008]   - Sending (Upload):    0.029 ms             source=console
INFO[0008]   - Waiting (TTFB):      414.263 ms           source=console
INFO[0008]   - Receiving (Download):0.648 ms             source=console
INFO[0009] Total Duration: 327.576 ms                    source=console
INFO[0009]   - Blocked (DNS/Queue): 0.005 ms             source=console
INFO[0009]   - Connecting (TCP):    0 ms                 source=console
INFO[0009]   - TLS Handshake:       0 ms                 source=console
INFO[0009]   - Sending (Upload):    0.025 ms             source=console
INFO[0009]   - Waiting (TTFB):      327.498 ms           source=console
INFO[0009]   - Receiving (Download):0.053 ms             source=console
INFO[0009] Total Duration: 385.522 ms                    source=console
INFO[0009]   - Blocked (DNS/Queue): 0.003 ms             source=console
INFO[0009]   - Connecting (TCP):    0 ms                 source=console
INFO[0009]   - TLS Handshake:       0 ms                 source=console
INFO[0009]   - Sending (Upload):    0.022 ms             source=console
INFO[0009]   - Waiting (TTFB):      385.262 ms           source=console
INFO[0009]   - Receiving (Download):0.238 ms             source=console
INFO[0010] Total Duration: 411.886 ms                    source=console
INFO[0010]   - Blocked (DNS/Queue): 0.003 ms             source=console
INFO[0010]   - Connecting (TCP):    0 ms                 source=console
INFO[0010]   - TLS Handshake:       0 ms                 source=console
INFO[0010]   - Sending (Upload):    0.021 ms             source=console
INFO[0010]   - Waiting (TTFB):      411.808 ms           source=console
INFO[0010]   - Receiving (Download):0.057 ms             source=console


█ TOTAL RESULTS

    checks_total.......................: 20      1.917422/s
    checks_succeeded...................: 100.00% 20 out of 20
    checks_failed......................: 0.00%   0 out of 20

    ✓ is status 201

    HTTP
    http_req_duration.......................................................: avg=420.19ms min=305.8ms med=409.84ms max=756.23ms p(90)=516.12ms p(95)=570.69ms
      { expected_response:true }............................................: avg=420.19ms min=305.8ms med=409.84ms max=756.23ms p(90)=516.12ms p(95)=570.69ms
    http_req_failed.........................................................: 0.00% 0 out of 20
    http_reqs...............................................................: 20    1.917422/s

    EXECUTION
    iteration_duration......................................................: avg=521.49ms min=406.2ms med=511.21ms max=861.31ms p(90)=616.9ms  p(95)=672.06ms
    iterations..............................................................: 20    1.917422/s
    vus.....................................................................: 1     min=1       max=1
    vus_max.................................................................: 1     min=1       max=1

    NETWORK
    data_received...........................................................: 13 kB 1.2 kB/s
    data_sent...............................................................: 36 kB 3.4 kB/s




---


export const options = {
stages: [
{ duration: '30s', target: 10 }, // Ramp up from 1 to 10 users over 30 seconds
{ duration: '1m',  target: 10 }, // Stay at 10 users for 1 minute (steady state)
{ duration: '30s', target: 0 },  // Ramp down to 0 users (cleanup)
],
};


running (10.4s), 0/1 VUs, 20 complete and 0 interrupted iterations
default ✓ [======================================] 1 VUs  10s






    ✓ is status 201

    HTTP
    http_req_duration.......................................................: avg=449.95ms min=286.82ms med=436.22ms max=798.81ms p(90)=568.97ms p(95)=595.98ms
      { expected_response:true }............................................: avg=449.95ms min=286.82ms med=436.22ms max=798.81ms p(90)=568.97ms p(95)=595.98ms
    http_req_failed.........................................................: 0.00%  0 out of 1662
    http_reqs...............................................................: 1662   13.82914/s

    EXECUTION
    iteration_duration......................................................: avg=551.62ms min=388.08ms med=537.66ms max=899.41ms p(90)=670.51ms p(95)=698ms   
    iterations..............................................................: 1662   13.82914/s
    vus.....................................................................: 1      min=1         max=10
    vus_max.................................................................: 10     min=10        max=10

    NETWORK
    data_received...........................................................: 1.1 MB 8.8 kB/s
    data_sent...............................................................: 3.0 MB 25 kB/s




running (2m00.2s), 00/10 VUs, 1662 complete and 0 interrupted iterations
default ✓ [======================================] 00/10 VUs  2m0s
dogancaglar@Dogans-MacBook-Pro ecommerce-platform-kotlin % 

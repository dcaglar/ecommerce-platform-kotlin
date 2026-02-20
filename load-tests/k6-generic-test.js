import http from 'k6/http';
import { Counter } from 'k6/metrics'; // 1. Import Counte
import { check, sleep } from 'k6';
import exec from 'k6/execution'; // 1. Add this import
/*
TEST_TYPE='load150' k6 run \
  -o experimental-prometheus-rw \
  -e K6_PROMETHEUS_RW_SERVER_URL='http://localhost:9090/api/v1/write' \
  -e K6_PROMETHEUS_RW_TREND_STATS='p(50),p(95),p(99),avg,min,max' \
  load-tests/k6-generic-test.js
  */

const error5xxCounter = new Counter('errors_5xx_total');
const ACCESS_TOKEN = open('../keycloak/output/jwt/payment-service.token').replace(/[\r\n]+$/, '');
const endpoints = JSON.parse(open('../infra/endpoints.json'));
const currentTestType = __ENV.TEST_TYPE || 'smoke'; // Default to smoke
// 2. DEFINE ALL SCENARIOS
const allScenarios = {
    // A. Smoke Test (Sanity) 💨
    smoke: {
        executor: 'constant-vus',
        vus: 1,
        duration: '60s', // Quick check
        tags: { test_type: 'smoke' },
    },

    // B. Load Test (Baseline) 📉
    load50: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '2m', target: 75 },
            { duration: '5m', target: 75 },
            { duration: '2m', target: 0 },
        ],
        tags: { test_type: 'load50' },
    },

     load75: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '2m', target: 75 },
                { duration: '5m', target: 75 },
                { duration: '2m', target: 0 },
            ],
            tags: { test_type: 'load75' },
        },


            load75_ramping_arrival_rate: {
                    executor: 'ramping-arrival-rate',
                                startRate: 0,              // Start with 50 iterations per second
                                timeUnit: '1s',             // 50 requests per 1 second
                                preAllocatedVUs: 100,       // How many VUs to "warm up" (keep this high)
                                maxVUs: 1500,               // Allow up to 1500 VUs if things get slow
                    stages: [
                        { duration: '2m', target: 75 },
                        { duration: '5m', target: 75 },
                        { duration: '2m', target: 0 },
                    ],
                    tags: { test_type: 'load75_ramping_arrival_rate' },
                },

     load100: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                    { duration: '2m', target: 100 },
                    { duration: '5m', target: 100 },
                    { duration: '2m', target: 0 },
                ],
                tags: { test_type: 'load100' },
            },
  load100_ramping_arrival_rate: {
                    executor: 'ramping-arrival-rate',
                                startRate: 0,              // Start with 50 iterations per second
                                timeUnit: '1s',             // 50 requests per 1 second
                                preAllocatedVUs: 100,       // How many VUs to "warm up" (keep this high)
                                maxVUs: 1500,               // Allow up to 1500 VUs if things get slow
                    stages: [
                        { duration: '2m', target: 100 },
                        { duration: '5m', target: 100 },
                        { duration: '2m', target: 0 },
                    ],
                    tags: { test_type: 'load100_ramping_arrival_rate' },
                },

          load125: {
                 executor: 'ramping-vus',
                 startVUs: 0,
                 stages: [
                         { duration: '2m', target: 125 },
                         { duration: '5m', target: 125 },
                         { duration: '2m', target: 0 },
                     ],
                     tags: { test_type: 'load125' },
                 },
  load125_ramping_arrival_rate: {
                    executor: 'ramping-arrival-rate',
                                startRate: 0,              // Start with 50 iterations per second
                                timeUnit: '1s',             // 50 requests per 1 second
                                preAllocatedVUs: 200,       // How many VUs to "warm up" (keep this high)
                                maxVUs: 1500,               // Allow up to 1500 VUs if things get slow
                    stages: [
                        { duration: '2m', target: 200 },
                        { duration: '5m', target: 200 },
                        { duration: '2m', target: 0 },
                    ],
                    tags: { test_type: 'load125_ramping_arrival_rate' },
                },
               load150: {
                      executor: 'ramping-vus',
                      startVUs: 0,
                      stages: [
                              { duration: '2m', target: 150 },
                              { duration: '5m', target: 150 },
                              { duration: '2m', target: 0 },
                          ],
                          tags: { test_type: 'load150' },
                      },
 load150_ramping_arrival_rate: {
                    executor: 'ramping-arrival-rate',
                                startRate: 0,              // Start with 50 iterations per second
                                timeUnit: '1s',             // 150 requests per 1 second
                                preAllocatedVUs: 200,       // How many VUs to "warm up" (keep this high)
                                maxVUs: 1500,               // Allow up to 1500 VUs if things get slow
                    stages: [
                        { duration: '2m', target: 150 },
                        { duration: '5m', target: 150 },
                        { duration: '2m', target: 0 },
                    ],
                    tags: { test_type: 'load150_ramping_arrival_rate' },
                },
                    load175: {
                               executor: 'ramping-vus',
                               startVUs: 0,
                               stages: [
                                       { duration: '2m', target: 175 },
                                       { duration: '40m', target: 175 },
                                       { duration: '2m', target: 0 },
                                   ],
                                   tags: { test_type: 'load175' },
                               },


                       load175_ramping_arrival_rate: {
                                          executor: 'ramping-arrival-rate',
                                                      startRate: 0,              // Start with 50 iterations per second
                                                      timeUnit: '1s',             // 50 requests per 1 second
                                                      preAllocatedVUs: 200,       // How many VUs to "warm up" (keep this high)
                                                      maxVUs: 1500,               // Allow up to 1500 VUs if things get slow
                                          stages: [
                                              { duration: '2m', target: 175 },
                                              { duration: '5m', target: 175 },
                                              { duration: '2m', target: 0 },
                                          ],
                                          tags: { test_type: 'load175_ramping_arrival_rate' },
                                      },
                                load200: {
                               executor: 'ramping-vus',
                               startVUs: 0,
                               stages: [
                                       { duration: '2m', target: 200 },
                                       { duration: '60m', target: 200 },
                                       { duration: '2m', target: 0 },
                                   ],
                                   tags: { test_type: 'load200' },
                               },

    load200_ramping_arrival_rate: {
        executor: 'ramping-arrival-rate',
        startRate: 0,
        timeUnit: '1s',
        preAllocatedVUs: 200,
        maxVUs: 1500,
        stages: [
            { duration: '2m', target: 200 },
            { duration: '5m', target: 200 },
            { duration: '2m', target: 0 },
        ],
        tags: { test_type: 'load200_ramping_arrival_rate' },
    },


    load250_ramping_arrival_rate: {
        executor: 'ramping-arrival-rate',
        startRate: 0,
        timeUnit: '1s',
        preAllocatedVUs: 250,
        maxVUs: 1500,
        stages: [
            { duration: '2m', target: 250 },
            { duration: '5m', target: 250 },
            { duration: '2m', target: 0 },
        ],
        tags: { test_type: 'load250_ramping_arrival_rate' },
    },


    load275_ramping_arrival_rate: {
         executor: 'ramping-arrival-rate',
         startRate: 0,
         timeUnit: '1s',
         preAllocatedVUs: 300,
         maxVUs: 1500,
         stages: [
             { duration: '2m', target: 275 },
             { duration: '5m', target: 275 },
             { duration: '2m', target: 0 },
         ],
         tags: { test_type: 'load275_ramping_arrival_rate' },
     },

    load300_ramping_arrival_rate: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: 300,
            maxVUs: 1500,
            stages: [
                { duration: '2m', target: 300 },
                { duration: '5m', target: 300 },
                { duration: '2m', target: 0 },
            ],
            tags: { test_type: 'load300_ramping_arrival_rate' },
        },


   load350_ramping_arrival_rate: {
           executor: 'ramping-arrival-rate',
           startRate: 0,
           timeUnit: '1s',
           preAllocatedVUs: 350,
           maxVUs: 1500,
           stages: [
               { duration: '2m', target: 350 },
               { duration: '5m', target: 350 },
               { duration: '2m', target: 0 },
           ],
           tags: { test_type: 'load350_ramping_arrival_rate' },
       },


   load400_ramping_arrival_rate: {
           executor: 'ramping-arrival-rate',
           startRate: 0,
           timeUnit: '1s',
           preAllocatedVUs: 400,
           maxVUs: 1500,
           stages: [
               { duration: '2m', target: 400 },
               { duration: '50m', target: 400 },
               { duration: '2m', target: 0 },
           ],
           tags: { test_type: 'load400_ramping_arrival_rate' },
       },

  load450_ramping_arrival_rate: {
          executor: 'ramping-arrival-rate',
          startRate: 0,
          timeUnit: '1s',
          preAllocatedVUs: 450,
          maxVUs: 1500,
          stages: [
              { duration: '2m', target: 450 },
              { duration: '5m', target: 450 },
              { duration: '2m', target: 0 },
          ],
          tags: { test_type: 'load450_ramping_arrival_rate' },
      },


  load500_ramping_arrival_rate: {
          executor: 'ramping-arrival-rate',
          startRate: 0,
          timeUnit: '1s',
          preAllocatedVUs: 500,
          maxVUs: 1500,
          stages: [
              { duration: '2m', target: 500 },
              { duration: '5m', target: 500 },
              { duration: '2m', target: 0 },
          ],
          tags: { test_type: 'load500_ramping_arrival_rate' },
      },


  load550_ramping_arrival_rate: {
          executor: 'ramping-arrival-rate',
          startRate: 0,
          timeUnit: '1s',
          preAllocatedVUs: 550,
          maxVUs: 1500,
          stages: [
              { duration: '2m', target: 550 },
              { duration: '5m', target: 550 },
              { duration: '2m', target: 0 },
          ],
          tags: { test_type: 'load550_ramping_arrival_rate' },
      },
  load600_ramping_arrival_rate: {
          executor: 'ramping-arrival-rate',
          startRate: 0,
          timeUnit: '1s',
          preAllocatedVUs: 550,
          maxVUs: 1500,
          stages: [
              { duration: '2m', target: 600 },
              { duration: '5m', target: 600 },
              { duration: '2m', target: 0 },
          ],
          tags: { test_type: 'load600_ramping_arrival_rate' },
      },




  stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
          // 1. Ramp to the "Edge" (What we know works)
          { duration: '2m', target: 200 },

          // 2. The "Saturation" Phase (Hitting the 250-Thread Ceiling)
          // At 300 VUs, 50 users MUST wait in the Tomcat queue.
          { duration: '5m', target: 300 },

          // 3. The "Kill Shot" Phase (Full Pressure on the 12 DB Connections)
          // This will likely trigger the 1.5s DB Acquire Time plateau.
          { duration: '5m', target: 400 },

          // 4. Recovery Phase
          // To see if the app recovers or stays "dead" (Zombie threads).
          { duration: '5m', target: 0 },
      ],
      tags: { test_type: 'stress' },
  },

  stress_cold_spike_600: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
          // 1. The "Cold Spike" (Aggressive 30s jump to 600)
          // No warm-up. Just raw pressure from 0 to 600.
          { duration: '30s', target: 600 },

          // 2. The "Saturation Hold"
          // Hold at 600 for 5 minutes to see if the system
          // can "warm up" under fire or if it just collapses.
          { duration: '5m', target: 600 },

          // 3. Recovery Phase
          { duration: '5m', target: 0 },
      ],
      tags: { test_type: 'stress_cold_spike_600' },
  },

    stress_arrival_rate: {
            executor: 'ramping-arrival-rate',
            startRate: 50,              // Start with 50 iterations per second
            timeUnit: '1s',             // 50 requests per 1 second
            preAllocatedVUs: 200,       // How many VUs to "warm up" (keep this high)
            maxVUs: 1500,               // Allow up to 1500 VUs if things get slow
            stages: [
                { duration: '2m', target: 150 },
                { duration: '7m', target: 150 },
                { duration: '2m', target: 200 },
                { duration: '7m', target: 200 },
                { duration: '5m', target: 250 },
                { duration: '30m', target: 250 },
                { duration: '5m', target: 300 },
                { duration: '30m', target: 300 },
                { duration: '5m', target: 350 },
                { duration: '30m', target: 350 },
                { duration: '5m', target: 400 },
                { duration: '30m', target: 400 },

                { duration: '5m', target: 450 }, // Ramp to 200 RPS
                { duration: '30m', target: 450 },
                ],
            tags: { test_type: 'stress_arrival_rate' },
        },
       spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                // 1. The "Cold Spike" (Aggressive 30s jump to 600)
                // No warm-up. Just raw pressure from 0 to 600.
                { duration: '10s', target: 100 },

                // 2. The "Saturation Hold"
                // Hold at 600 for 5 minutes to see if the system
                // can "warm up" under fire or if it just collapses.
                { duration: '1m', target: 100 },

                // 3. Recovery Phase
                { duration: '10s', target: 1400 },
                { duration: '3m', target: 1400 },
                { duration: '10s', target: 100 },
                { duration: '3m', target: 100 },
                { duration: '10s', target: 0 },

            ],
            tags: { test_type: 'spike' },
        }

};

// 3. EXPORT OPTIONS WITH ONLY THE SELECTED SCENARIO
// This is the magic fix: We only put ONE scenario into the config.
export const options = {
    scenarios: {
        [currentTestType]: allScenarios[currentTestType],
    },
    thresholds: {

    /*
        http_req_failed: [{
                    threshold: 'rate <= 0.05',
                    abortOnFail: true,
                    delayAbortEval: '10s'
                }],
       http_req_duration: ['p(95)<750'],

       // 2. THE QUALITY GATE (Stop if the system is "unusable")
       // If 95% of users wait over 2 seconds, the test is no longer useful.
       http_req_duration: [{
          threshold: 'p(95) <= 2000',
           abortOnFail: true,
           delayAbortEval: '10s'
         }],

      // 3. THE "ZOMBIE" DETECTOR (Stop if the system stops responding)
   // If the server takes > 5s to send the first byte, it's deadlocked.
      http_req_waiting: [{
      threshold: 'p(99) <= 5000',
      abortOnFail: true
      }],
      */

      'http_req_failed': ['rate <= 0.05'],
              'http_req_duration': ['p(95) <= 750'],
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
/*
k6 timeout 7 olabilir.
coneectionalive : 120000
server.tomcat.connectiontimeout:130000 
nginx.ingress.kubernetes.io/limit-connections: "350"
*/
   const idempotencyKey = randomId('IDEM');
    const params = {
    timeout: '7s',
      headers: {
        'Host': `${endpoints.host_header}`,
         'Authorization': `Bearer ${ACCESS_TOKEN}` ,
         'Idempotency-Key': `${idempotencyKey}`,
        'Content-Type': 'application/json'
            },
    };
    const res = http.post(url, payload, params);

    // 3. Increment the counter if status is 500-599
        if (res.status >= 500 && res.status <= 599) {
            error5xxCounter.add(1);
        }
  if(res.status != 201){
 //  console.log(`Request: ${payload}`);
  //  console.log(`Http status: ${res.status}   Response: ${res.body}`);
/*console.log(`Total Duration: ${res.timings.duration} ms`);
  console.log(`  - Blocked (DNS/Queue): ${res.timings.blocked} ms`);
  console.log(`  - Connecting (TCP):    ${res.timings.connecting} ms`);
  console.log(`  - TLS Handshake:       ${res.timings.tls_handshaking} ms`);
  console.log(`  - Sending (Upload):    ${res.timings.sending} ms`);
  console.log(`  - Waiting (TTFB):      ${res.timings.waiting} ms`); // <--- Critical: Server processing time
  console.log(`  - Receiving (Download):${res.timings.rseceiving} ms`);
  */

  }
  check(res, {
    'is status 201': (r) => r.status === 201 || r.status === 200,
  });


//k6 run -e TEST_TYPE=load --include-scenarios load load-test.js
  // 2. Use exec.scenario.name to check the scenario
  /*
  if (exec.scenario.name === 'human_scenario') {
    sleep(1);
  }
  */
}
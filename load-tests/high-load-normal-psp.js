import http from 'k6/http';
import { sleep, check } from 'k6';

export let options = {
  vus: 500, // lighter load
  duration: '15m', // longer duration
};

function randomId(prefix) {
  return `${prefix}-${Math.floor(Math.random() * 1e12)}`;
}

const url = 'http://localhost:8081/payments';

export default function () {
  const payload = JSON.stringify({
    orderId: randomId('ORDER'),
    buyerId: randomId('BUYER'),
    totalAmount: {
      value: 199.49,
      currency: 'EUR',
    },
    paymentOrders: [
      {
        sellerId: 'SELLER-001',
        amount: {
          value: 49.99,
          currency: 'EUR',
        },
      },
      {
        sellerId: 'SELLER-002',
        amount: {
          value: 29.50,
          currency: 'EUR',
        },
      },
      {
        sellerId: 'SELLER-003',
        amount: {
          value: 120.00,
          currency: 'EUR',
        },
      },
    ],
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer <YOUR_TOKEN_HERE>',
    },
  };

  const res = http.post(url, payload, params);
  check(res, {
    'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
  });
  sleep(Math.random() * 2); // Random sleep between 0 and 2 seconds
}

// To run this scenario with PSP in NORMAL mode, set scenario in application.yml or use env var:
// psp.simulation.scenario=NORMAL
// k6 run high-load-normal-psp.js



//03:37 started


// just started for 30mins -05:05
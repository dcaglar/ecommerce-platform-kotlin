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
//const AUTH_TOKEN = 'eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJHNWVXMkZrZXJOWk85MU5NQlE1dk5rQXcyNFVIT09NbHBJUzJrMDFKSnZJIn0.eyJleHAiOjE3NDcyMTcxNzksImlhdCI6MTc0NzE4MTE3OSwianRpIjoiZGQ3NTEzZTQtM2FmOC00YjRmLWIwMjktNGY0NDc2ZTNjMmM2IiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDgyL3JlYWxtcy9lY29tbWVyY2UtcGxhdGZvcm0iLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiODAwMmEyMGMtMTRhZi00N2NmLWEzOTQtMjYzODIzZTIwNDk5IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoicGF5bWVudC1zZXJ2aWNlIiwic2Vzc2lvbl9zdGF0ZSI6IjA4ZTdmYmVlLTM4OTgtNGZmNC1iNDhiLTk3YWYyMDVmZjcyZiIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiLyoiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbInBheW1lbnQ6d3JpdGUiLCJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZXMtZWNvbW1lcmNlLXBsYXRmb3JtIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6ImVtYWlsIHByb2ZpbGUiLCJzaWQiOiIwOGU3ZmJlZS0zODk4LTRmZjQtYjQ4Yi05N2FmMjA1ZmY3MmYiLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwibmFtZSI6IkRvZ2FuIENhZ2xhciIsInByZWZlcnJlZF91c2VybmFtZSI6ImRvZ2FuIiwiZ2l2ZW5fbmFtZSI6IkRvZ2FuIiwiZmFtaWx5X25hbWUiOiJDYWdsYXIiLCJlbWFpbCI6ImRjYWdsYXIxOTg3QGdtYWlsLmNvbSJ9.dlQemSwXd3GK8aYes8_fEBSDeymbXnBticpawD-1lkFjccu9cvV-VoilU_iyLzVvVodvYHZTwJunRBANR7S7MoL-FS9X12dsryf036h-D3Pi2AyDSziDWItb2joclw41Vn1HAQFKKh3HqPJlc78ezCJNhrhWsGAED2I3Qcz-Wa8j1THzZgGmTPef5wK8dLGOAISAVvLB_m9XwncP6zpXN8V13-jptO2k6lYiOoysJjnOYFSF5YJpKLqZa1brXSyxrdodfZp8ViX-a6CConTUThe_CK8vWsHXRWVYmGiJ7vLDHMH9IUVCZd8NQoEyJ-3CsUW4Vxfg-PvYcN1UpSPW9w';

function randomId(prefix) {
    return `${prefix}-${Math.floor(Math.random() * 1e12)}`;
}

function randomAmount(min, max) {
    return parseFloat((Math.random() * (max - min) + min).toFixed(2));
}

export default function () {
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
        },
    };
    const res = http.post(BASE_URL, payload, params);
  check(res, {
    'status is 200': (r) => {
      if (r.status !== 200) {
        console.error(`Request failed: ${r.status} - ${r.body}`);
      }
      return r.status === 200;
    }
  });
}

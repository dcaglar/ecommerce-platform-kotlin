import http from 'k6/http';
import { sleep, check } from 'k6';

export let options = {
  stages: [
    { duration: '10s', target: 0 },   // Ramp-down to 0 users
    { duration: '10s', target: 300 }, // Spike to 300 users
    { duration: '30s', target: 300 }, // Stay at 300 users
    { duration: '10s', target: 0 },   // Ramp-down to 0 users
  ],
};

const url = 'http://localhost:8081/payments';
const payload = JSON.stringify({
  orderId: 'ORDER-20240508-XYZ',
  buyerId: 'BUYER-123',
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
    'Authorization': 'Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJHNWVXMkZrZXJOWk85MU5NQlE1dk5rQXcyNFVIT09NbHBJUzJrMDFKSnZJIn0.eyJleHAiOjE3NDcyMTcxNzksImlhdCI6MTc0NzE4MTE3OSwianRpIjoiZGQ3NTEzZTQtM2FmOC00YjRmLWIwMjktNGY0NDc2ZTNjMmM2IiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDgyL3JlYWxtcy9lY29tbWVyY2UtcGxhdGZvcm0iLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiODAwMmEyMGMtMTRhZi00N2NmLWEzOTQtMjYzODIzZTIwNDk5IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoicGF5bWVudC1zZXJ2aWNlIiwic2Vzc2lvbl9zdGF0ZSI6IjA4ZTdmYmVlLTM4OTgtNGZmNC1iNDhiLTk3YWYyMDVmZjcyZiIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiLyoiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbInBheW1lbnQ6d3JpdGUiLCJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZXMtZWNvbW1lcmNlLXBsYXRmb3JtIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6ImVtYWlsIHByb2ZpbGUiLCJzaWQiOiIwOGU3ZmJlZS0zODk4LTRmZjQtYjQ4Yi05N2FmMjA1ZmY3MmYiLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwibmFtZSI6IkRvZ2FuIENhZ2xhciIsInByZWZlcnJlZF91c2VybmFtZSI6ImRvZ2FuIiwiZ2l2ZW5fbmFtZSI6IkRvZ2FuIiwiZmFtaWx5X25hbWUiOiJDYWdsYXIiLCJlbWFpbCI6ImRjYWdsYXIxOTg3QGdtYWlsLmNvbSJ9.dlQemSwXd3GK8aYes8_fEBSDeymbXnBticpawD-1lkFjccu9cvV-VoilU_iyLzVvVodvYHZTwJunRBANR7S7MoL-FS9X12dsryf036h-D3Pi2AyDSziDWItb2joclw41Vn1HAQFKKh3HqPJlc78ezCJNhrhWsGAED2I3Qcz-Wa8j1THzZgGmTPef5wK8dLGOAISAVvLB_m9XwncP6zpXN8V13-jptO2k6lYiOoysJjnOYFSF5YJpKLqZa1brXSyxrdodfZp8ViX-a6CConTUThe_CK8vWsHXRWVYmGiJ7vLDHMH9IUVCZd8NQoEyJ-3CsUW4Vxfg-PvYcN1UpSPW9w',
  },
};

export default function () {
  const res = http.post(url, payload, params);
  check(res, {
    'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
  });
  sleep(0.1);
}

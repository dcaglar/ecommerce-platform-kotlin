# 🚀 How to Start


## 1️⃣ connect to db

```bash
psql -h localhost -d payment_db -p 5432 -U payment
```

```bash
./keycloak/provision-keycloak.sh
```



## 5️⃣ Generate Access Token for Payment Service

Generate an OAuth access token for the payment-service client:

```bash
./keycloak/get-token.sh
```

## 6️⃣ How to Test Locally

You can call the API using the saved token. Prefer the dynamic example to avoid hardcoded IPs.

- Dynamic (uses infra/endpoints.json):
```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json)
HOST=$(jq -r .host_header infra/endpoints.json)
curl -i -X POST "$BASE_URL/payments" \
  -H "Host: $HOST" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/access.token)" \
  -d '{
    "orderId": "ORDER-20240508-XYZ",
    "buyerId": "BUYER-123",
    "totalAmount": { "value": 199.49, "currency": "EUR" },
    "paymentOrders": [
      { "sellerId": "SELLER-111", "amount": { "value": 49.99, "currency": "EUR" }},
      { "sellerId": "SELLER-222", "amount": { "value": 29.50, "currency": "EUR" }},
      { "sellerId": "SELLER-333", "amount": { "value": 120.00, "currency": "EUR" }}
    ]
  }'
```

- Static example (replace host/IP if different):
```bash
curl -i -X POST http://127.0.0.1/payments \
  -H "Host: payment.192.168.49.2.nip.io" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/access.token)" \
  -d '{
    "orderId": "ORDER-20240508-XYZ",
    "buyerId": "BUYER-123",
    "totalAmount": { "value": 199.49, "currency": "EUR" },
    "paymentOrders": [
      { "sellerId": "SELLER-111", "amount": { "value": 49.99, "currency": "EUR" }},
      { "sellerId": "SELLER-222", "amount": { "value": 29.50, "currency": "EUR" }},
      { "sellerId": "SELLER-333", "amount": { "value": 120.00, "currency": "EUR" }}
    ]
  }'
```




## 7️⃣ Run Load Tests

From project root, run:

```bash 
CLIENT_TIMEOUT=3100ms MODE=constant RPS=10 PRE_VUS=40 MAX_VUS=160 k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms MODE=constant RPS=10 PRE_VUS=40 MAX_VUS=160  DURATION=20m k6 run load-tests/baseline-smoke-test.js 
CLIENT_TIMEOUT=3100ms MODE=constant RPS=40 PRE_VUS=40 MAX_VUS=160 k6 run load-tests/baseline-smoke-test.js 
CLIENT_TIMEOUT=3100ms MODE=constant RPS=60 PRE_VUS=40 MAX_VUS=160 k6 run load-tests/baseline-smoke-test.js 
CLIENT_TIMEOUT=3100ms MODE=constant RPS=80 PRE_VUS=40 MAX_VUS=160 DURATION=100m k6 run load-tests/baseline-smoke-test.js 
```

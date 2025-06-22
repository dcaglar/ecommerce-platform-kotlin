# 🚀 How to Start

This guide walks you through starting up the full stack, getting tokens, and running load tests.

---

## 1️⃣ Clone and Prepare

```bash
git clone https://github.com/your-org/ecommerce-platform-kotlin.git
cd ecommerce-platform-kotlin
```

---

## 2️⃣ Start Infrastructure

Spin up all dependencies (Keycloak, Kafka, Redis, Postgres, etc.):

```bash
./scripts/infra-up.sh
```

---

## 3️⃣ Start the Application

```bash
./scripts/app-up.sh
```

---

## 4️⃣ Get a Service Access Token

Obtain a fresh access token for service-to-service calls:

```bash
./keycloak/get-token.sh
```

The token will be saved to: `keycloak/access.token`

---

## 5️⃣ Test the Payment API

Use the token from above for a sample request:

```bash
curl -i -X POST http://localhost:8081/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/access.token)" \
  -d '{
    "orderId": "ORDER-20240508-XYZ",
    "buyerId": "BUYER-123",
    "totalAmount": { "value": 199.49, "currency": "EUR" },
    "paymentOrders": [
      { "sellerId": "SELLER-001", "amount": { "value": 49.99, "currency": "EUR" }},
      { "sellerId": "SELLER-002", "amount": { "value": 29.50, "currency": "EUR" }},
      { "sellerId": "SELLER-003", "amount": { "value": 120.00, "currency": "EUR" }}
    ]
  }'
```

---

## 6️⃣ Run Load Tests

From project root, run:

```bash
k6 run load-tests/baseline-smoke-test.js
```

---

## 🔗 Useful URLs

- Keycloak Admin: [http://localhost:8082/admin/](http://localhost:8082/admin/)
- Payment API: [http://localhost:8081/payments](http://localhost:8081/payments)
- Kafka UI: [http://localhost:8088/](http://localhost:8088/)
- Grafana: [http://localhost:3000/](http://localhost:3000/) (admin/admin)
- Kibana: [http://localhost:5601/](http://localhost:5601/)

---

## 📝 Notes

- **No manual Keycloak setup required** (realm, client, roles provisioned automatically).
- Tokens/secrets are handled by scripts in `keycloak/`.
- If you change configs, re-run `infra-up.sh` and `get-token.sh`.


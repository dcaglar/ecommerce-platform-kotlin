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

## 4️⃣ Get a Service Access Token (for local dev & load testing)

**Option 1: Port-forward Keycloak and generate token locally**

1. Port-forward Keycloak:
   ```bash
   kubectl port-forward svc/keycloak 8080:8080 -n payment
   ```
2. In a new terminal, set the Keycloak URL in your token script:
   ```bash
   export KC_URL="http://localhost:8080"
   ./keycloak/get-token.sh
   ```
   The token will be saved to: `keycloak/access.token`

**Option 2: Generate token from inside the cluster**

1. Start a debug pod:
   ```bash
   kubectl run -it --rm --restart=Never debug --image=alpine --namespace=payment -- sh
   apk add --no-cache curl bash jq
   # Copy get-token.sh and secrets.txt into the pod, then run:
   export KC_URL="http://keycloak:8080"
   bash /get-token.sh
   ```
2. Copy the generated token to your local machine if needed.

---

## 5️⃣ Test the Payment API

Use the token from above for a sample request:

> **Note:** Make sure you have run `sudo minikube tunnel` in a separate terminal and
> added  `127.0.0.1 payment.local` `127.0.0.1 keyclock`
> your `/etc/hosts` file.

```bash
curl -i -X POST http://payment.local/payments \
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

- Keycloak Admin: [http://localhost:8080/](http://localhost:8080/) (if port-forwarded)
- Payment API: [http://payment.local/payments](http://payment.local/payments)
- Kafka UI: [http://localhost:8088/](http://localhost:8088/)
- Grafana: [http://localhost:3000/](http://localhost:3000/) (admin/admin)
- Kibana: [http://localhost:5601/](http://localhost:5601/)

---

## 📝 Notes

- **No manual Keycloak setup required** (realm, client, roles provisioned automatically).
- Tokens/secrets are handled by scripts in `keycloak/`.
- If you change configs, re-run `infra-up.sh` and `get-token.sh`.
- For load testing, always generate tokens using the same Keycloak URL as your payment-service expects (see above for
  port-forwarding).

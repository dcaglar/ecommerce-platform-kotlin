# 🚀 How to Start

This guide walks you through starting up the full stack, getting tokens, and running load tests.

---

## 1️⃣ Start All App & Infra Services

Deploy all services and infrastructure to your local Kubernetes cluster (namespace: `payment`):

```bash
./infra/scripts/kubernetes/deploy-k8s-overlay.sh local all payment
```

This will:

- Create the namespace if it doesn't exist
- Apply all manifests for all services and infrastructure
- Apply any secrets in the overlay

---

## 2️⃣ Port-forward All Infra Services

To access infrastructure UIs (Keycloak, Kibana, Grafana, Prometheus, etc.) from your local machine, run:

```bash
./infra/scripts/kubernetes/port-forward-infra.sh
```

This will open a new Terminal tab for each service (on macOS) or run in the background (on Linux).

Default ports:

- Keycloak: http://localhost:8080
- Payment Service: http://localhost:8081
- Kibana: http://localhost:5601
- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090

---

## 3️⃣ Add Keycloak to /etc/hosts

To ensure JWT authentication works (issuer URL comparison), add the Keycloak domain to your `/etc/hosts`:

```bash
sudo ./infra/scripts/kubernetes/add-local-domains.sh
```

This will map `keycloak` to `127.0.0.1`.

---

## 4️⃣ Provision Keycloak Credentials

Run the following to create realms, roles, clients, and their configurations in Keycloak:

```bash
./keycloak/provision-keycloak.sh
```

---

## 5️⃣ Generate Access Token for Payment Service

Generate an OAuth access token for the payment-service client:

```bash
./keycloak/get-token.sh
```

The token will be saved to: `keycloak/access.token`

---

## 6️⃣ Test the Payment API

Use the generated access token to call the Payment API:

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

## 7️⃣ Run Load Tests

From project root, run:

```bash
RPS=10 DURATION=10m k6 run load-tests/baseline-smoke-test.js
```

---s

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
- If you change Keycloak configs, re-run `deploy-k8s-overlay.sh local keycloak payment` to apply changes.
- For load testing, always generate tokens using the same Keycloak URL as your payment-service expects (see above for
  port-forwarding).

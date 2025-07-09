# 🚀 How to Start

This guide walks you through starting up the full stack, getting tokens, and running load tests.

---

## 1️⃣ Clone and Prepare

```bash
git clone https://github.com/your-org/ecommerce-platform-kotlin.git
cd ecommerce-platform-kotlin
```

---

## 2️⃣ Start Local Kubernetes Cluster

Spin up all dependencies (Keycloak, Kafka, Redis, Postgres and payment-service , payment-consumer etc.):

```bash
 k8s-deploy-apps.sh local all payment
```

---

## 3️⃣ Start the Application

```bash
./scripts/app-up.sh
```

---

## 4️⃣ Get a Service Access Token (for local dev & load testing)

**Option 1: Port-forward Keycloak and generate token locally**

1. Start all app and infra services:
   ```bash
      `infra/scripts/kubernetes`/k8s-deploy-apps.sh local all payment
   ```


2. Port-forward Keycloak and or other infra you wnat to access so that you can access it locally
   ```bash
   kubectl port-forward svc/keycloak 8080:8080 -n payment
   kubectl port-forward svc/payment-service 8081:8080 -n payment
   kubectl port-forward svc/kibana 5601:5601 -n payment
   kubectl port-forward svc/grafana-service 3000:3000 -n payment
   ```
3. Add the keycloak and payment.local entries to your `/etc/hosts` file via following script
   ```bash
    infra/scripts/kubernetes/add-local-domains.sh 
   ```
4. In a new terminal,run 1st commmand for creation of realms, roles, clients, and their configurations,
   and second one to generate oauth access token for the payment service client
   so that you can generate a token for the payment service to authenticate your requests:
   ```bash
    keycloak/provision-keycloak.sh 
    keycloak get_token.sh
   ```

The token will be saved to: `keycloak/access.token`

5. Test the Payment API

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
RPS=10 DURATION=10m k6 run load-tests/baseline-smoke-test.js
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
- If you change keycloak configs, re-run `deploy-apps.sh local keyclok payment` to apply changes.
- For load testing, always generate tokens using the same Keycloak URL as your payment-service expects (see above for
  port-forwarding).

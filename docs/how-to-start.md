# 🚀 How to Start

This guide walks you through starting up the full stack, getting tokens, and running load tests.

---

## 1️⃣ create jfr record manually

kubectl exec -n payment -- jcmd 1 JFR.dump name=payment-service filename=/var/log/jfr/pay.jfr
payment-service-5c4c5b74-podname

grep 'POST /payments'

## 1️⃣ connect to be

```bash
psql -h localhost -d payment_db -p 5432 -U payment
```

```bash
./keycloak/provision-keycloak.sh
```

---

## 5️⃣ Generate Access Token for Payment Service

Generate an OAuth access token for the payment-service client:

```bash
./keycloak/get-token.sh
```

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

kubectl top pods -A

kubectl top node

kubectl top pods -n payment

kubectl top pods -n monitoring --sort-by=memory

kubectl top pods -n logging

kubectl logs -n payment payment-service | grep 'POST /payments'

kubectl exec -n payment payment-service- -- ls -lh /var/log/jfr
kubectl exec -n payment payment-service- -- jcmd 1 JFR.dump name=payment-service

find / -name 'access_log*.log' 2>/dev/null

kubectl cp payment/payment-service-:/var/log/jfr/pay.jfr ./pay.jfr

stern -n payment 'payment-service'| grep 'POST'

---d\

## 7️⃣ Run Load Tests

From project root, run:

```bash 
VUS=10  RPS=10 DURATION=1m k6 run load-tests/baseline-smoke-test.js
VUS=10  RPS=5 DURATION=50m k6 run load-tests/baseline-smoke-test.js
VUS=20  RPS=20 DURATION=50m k6 run load-tests/baseline-smoke-test.js
VUS=40 RPS=40 DURATION=20m k6 run load-tests/baseline-smoke-test.js
```

connect to db after port-forwarding:

```bash
RPS=50 DURATION=10m k6 run load-tests/baseline-smoke-test.js
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

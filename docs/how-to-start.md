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
VUS=10  RPS=10 DURATION=2m k6 run load-tests/baseline-smoke-test.js
VUS=15  RPS=15 DURATION=30m k6 run load-tests/baseline-smoke-test.js
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
  export BS="kafka.payment.svc.cluster.local:9092"
  BS_BROKER=kafka-controller-0.kafka-controller-headless.payment.svc.cluster.local:9092
  BS_CONSUMER=kafka.payment.svc.cluster.local

# __consumer_offsets (matches OFFSETS_TOPIC_NUM_PARTITIONS=1 and RF=1)

export BS="kafka.payment.svc.cluster.local:9092"
kafka-topics.sh --bootstrap-server "$BS" --create \
--topic __consumer_offsets \
--partitions 1 \
--replication-factor 1 \
--config cleanup.policy=compact \
--config compression.type=producer \
--config segment.bytes=104857600

# __transaction_state (matches TRANSACTION_STATE_LOG_* = 1)

v

kafka-topics.sh
--bootstrap-server "$BS" --describe --topic __consumer_offsets | egrep 'Topic:|PartitionCount|ReplicationFactor'
kafka-topics.sh --bootstrap-server "$BS" --describe --topic __transaction_state | egrep 'Topic: \
|PartitionCount|ReplicationFactor'

/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server "$BS" --list --exclude-internal false
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server "$BS" --describe --topic __consumer_offsets
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server "$BS" --describe --topic __transaction_state

Topic: __consumer_offsets TopicId: dxyj3OlSTFCD5dRVZ4NOLw PartitionCount: 3 ReplicationFactor: 1 Configs:
cleanup.policy=compact
Topic: __consumer_offsets Partition: 0 Leader: 0 Replicas: 0 Isr: 0 Elr:    LastKnownElr:
Topic: __consumer_offsets Partition: 1 Leader: 100 Replicas: 100 Isr: 100 Elr:    LastKnownElr:
Topic: __consumer_offsets Partition: 2 Leader: 100 Replicas: 100 Isr: 100 Elr:    LastKnownElr:

opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server "$BS" --describe --topic __consumer_offsets

kubectl -n payment exec -it kafka-client -- bash

--remove kafka ,payment-consumer,paymeny-service

# __consumer_offsets (matches OFFSETS_TOPIC_NUM_PARTITIONS=1 and RF=1)

kafka-topics.sh --bootstrap-server "$BS" --create \
--topic __consumer_offsets \
--partitions 1 \
--replication-factor 1 \
--config cleanup.policy=compact \
--config compression.type=producer \
--config segment.bytes=104857600

# __transaction_state (matches TRANSACTION_STATE_LOG_* = 1)

kafka-topics.sh --bootstrap-server "$BS" --create \
--topic __transaction_state \
--partitions 1 \
--replication-factor 1 \
--config cleanup.policy=compact

✅ Uninstalled kafka, and deleted pvc from namespace: payment
dogancaglar@Dogans-MacBook-Pro ecommerce-platform-kotlin % helm uninstall -n payment payment-service
release "payment-service" uninstalled
dogancaglar@Dogans-MacBook-Pro ecommerce-platform-kotlin % helm uninstall -n payment payment-consumers
release "payment-consumers" uninstalled

--remove exporter
helm -n payment uninstall kafka-exporter

--sanity checks and set bootstrap server url
export BS="kafka-broker-0.kafka-broker-headless.payment.svc.cluster.local:9092"
--create consumer-fofset and transaction stane on kafka-client
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server "$BS" \
--create --topic __consumer_offsets --replica-assignment 100,100,100 \
--config cleanup.policy=compact

/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server "$BS" \
--create --topic __transaction_state --replica-assignment 100 \
--config cleanup.policy=compact

kubectl -n payment run kafka-client --restart=Never \
--image docker.io/bitnami/kafka:4.0.0-debian-12-r10 --command -- sleep infinity













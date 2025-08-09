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

I have no name!@kafka-client:/$ /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server "$BS" --list
--exclude-internal false
payment_order_created_topic
payment_order_created_topic.DLQ
payment_order_retry_request_topic
payment_order_retry_request_topic.DLQ
payment_order_succeeded_topic
payment_order_succeeded_topic.DLQ
payment_status_check_scheduler_topic
payment_status_check_scheduler_topic.DLQ
test
I have no name!@kafka-client:/$ /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server "$BS" --describe --topic __
consumer_offsets
Topic: __consumer_offsets TopicId: dxyj3OlSTFCD5dRVZ4NOLw PartitionCount: 3 ReplicationFactor: 1 Configs:
cleanup.policy=compact
Topic: __consumer_offsets Partition: 0 Leader: 0 Replicas: 0 Isr: 0 Elr:    LastKnownElr:
Topic: __consumer_offsets Partition: 1 Leader: 100 Replicas: 100 Isr: 100 Elr:    LastKnownElr:
Topic: __consumer_offsets Partition: 2 Leader: 100 Replicas: 100 Isr: 100 Elr:    LastKnownElr:
I have no name!@kafka-client:/$ /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server "$BS" --describe --topic __
consumer_offsets

dogancaglar@Dogans-MacBook-Pro ecommerce-platform-kotlin % kubectl -n payment exec -it kafka-controller-0 -- \
sh -lc 'env | egrep "OFFSETS|TRANSACTION_STATE|DEFAULT_REPLICATION|MIN_INSYNC|AUTO_CREATE"'
Defaulted container "kafka" out of: kafka, jmx-exporter, prepare-config (init)
KAFKA_CFG_OFFSETS_TOPIC_NUM_PARTITIONS=3
KAFKA_CFG_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1
KAFKA_CFG_OFFSETS_TOPIC_REPLICATION_FACTOR=1
KAFKA_CFG_DEFAULT_REPLICATION_FACTOR=1
KAFKA_CFG_TRANSACTION_STATE_LOG_MIN_ISR=1
KAFKA_CFG_MIN_INSYNC_REPLICAS=1
dogancaglar@Dogans-MacBook-Pro ecommerce-platform-kotlin %

--connect kafka client to be able run kafka bin shell commands
kubectl -n payment exec -it kafka-client -- bash

--remove kafka ,payment-consumer,paymeny-service

dogancaglar@Dogans-MacBook-Pro ecommerce-platform-kotlin % delete-kafka-local.sh
release "kafka" uninstalled
persistentvolumeclaim "data-kafka-controller-0" deleted
persistentvolumeclaim "data-kafka-broker-0" deleted
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

kubectl -n payment wait --for=condition=Ready pod/kafka-client --timeout=90s












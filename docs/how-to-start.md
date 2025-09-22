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

kubectl top pods -A

kubectl top node

kubectl top pods -n payment

kubectl top pods -n monitoring --sort-by=memory

kubectl get pods -A \                                                                                                                                   
-o custom-columns='NS:.metadata.namespace,NAME:.metadata.name,REQ_CPU:.spec.containers[*].resources.requests.cpu,LIM_CPU:.spec.containers[*].resources.limits.cpu,REQ_MEM:.spec.containers[*].resources.requests.memory,LIM_MEM:.spec.containers[*].resources.limits.memory' \
| column -t | less


# Per-pod requests/limits at a glance
kubectl get pods -A \
-o custom-columns='NS:.metadata.namespace,NAME:.metadata.name,REQ_CPU:.spec.containers[*].resources.requests.cpu,LIM_CPU:.spec.containers[*].resources.limits.cpu,REQ_MEM:.spec.containers[*].resources.requests.memory,LIM_MEM:.spec.containers[*].resources.limits.memory' \
| column -t | less

# Sort by biggest memory limit (first container per pod)
kubectl get pods -A \
--sort-by='.spec.containers[0].resources.limits.memory' \
-o custom-columns='NS:.metadata.namespace,NAME:.metadata.name,LIM_MEM:.spec.containers[0].resources.limits.memory'

# Live usage (needs metrics-server)
kubectl top pods -A | sort -k3 -h



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
CLIENT_TIMEOUT=3100ms VUS=1  RPS=1 DURATION=20m k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms VUS=5  RPS=5 DURATION=20m k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms VUS=10  RPS=10 DURATION=20m k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms VUS=15  RPS=15 DURATION=10m k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms VUS=20  RPS=20 DURATION=50m k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms VUS=40 RPS=40 DURATION=20m k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms VUS=80 RPS=80 DURATION=20m k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms VUS=100 RPS=100 DURATION=20m k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms VUS=120 RPS=100 DURATION=20m k6 run load-tests/baseline-smoke-test.js

```

connect to db after port-forwarding:

```bash
RPS=50 DURATION=10m k6 run load-tests/baseline-smoke-test.js
```
health
```bash
curl -v \
  -H "Host: payment.192.168.49.2.nip.io" \
  http://127.0.0.1/actuator/health/liveness
  
curl -v \
  -H "Host: payment.192.168.49.2.nip.io" \
  http://127.0.0.1/actuator/prometheus
  
curl -v \
  -H "Host: payment.192.168.49.2.nip.io" \
  http://127.0.0.1/actuator


curl -v \
  -H "Host: payment.192.168.49.2.nip.io" \
  http://127.0.0.1/actuator
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










```
kubectl get nodes -o custom-columns=NAME:.metadata.name,ALLOCATABLE:.status.allocatable.memory
kubectl get nodes -o custom-columns=NAME:.metadata.name,ALLOCATABLE:.status.allocatable.cpu   
```

Example
 this is total allocatable memory/cpu on the node
 Example output:NAME       ALLOCATABLE
 minikube   11978768Ki

 (NAME       ALLOCATABLE)

 (minikube   7)


# # Describe get to see resource requests/limits on the node
```
kubectl describe $(kubectl get nodes -o name | head -n1) \
| sed -n '/Allocated resources:/,/Events:/p'
```

Example

Allocated resources:
(Total limits may be over 100 percent, i.e., overcommitted.)
Resource           Requests      Limits
  --------           --------      ------
cpu                4005m (57%)   11140m (159%)
memory             6504Mi (55%)  11302Mi (96%)
ephemeral-storage  50Mi (0%)     1Gi (0%)
hugepages-1Gi      0 (0%)        0 (0%)
hugepages-2Mi      0 (0%)        0 (0%)
hugepages-32Mi     0 (0%)        0 (0%)
hugepages-64Ki     0 (0%)        0 (0%






# #cpu/memorty resource request/limit on cpu/memory per pod instance(NOT ACTUAL USAGE)


```
kubectl get pods -A -o \
custom-columns="NAMESPACE:.metadata.namespace,NAME:.metadata.name,CPU_REQUEST:.spec.containers[*].resources.requests.cpu,MEM_REQUEST:.spec.containers[*].resources.requests.memory,CPU_LIMIT:.spec.containers[*].resources.limits.cpu,MEM_LIMIT:.spec.containers[*].resources.limits.memory"
```

```
kubectl get pods -n payment -o \
custom-columns="NAMESPACE:.metadata.namespace,NAME:.metadata.name,CPU_REQUEST:.spec.containers[*].resources.requests.cpu,MEM_REQUEST:.spec.containers[*].resources.requests.memory,CPU_LIMIT:.spec.containers[*].resources.limits.cpu,MEM_LIMIT:.spec.containers[*].resources.limits.memory" -n payment
```
Example
NAMESPACE       NAME                                                        CPU_REQUEST   MEM_REQUEST   CPU_LIMIT   MEM_LIMIT
ingress-nginx   ingress-nginx-controller-69c6c964ff-rzmms                   100m          90Mi          300m        256Mi
kube-system     coredns-674b8bbfcf-56xxh                                    100m          70Mi          <none>      170Mi
kube-system     etcd-minikube                                               100m          100Mi         <none>      <none>
kube-system     kube-apiserver-minikube                                     250m          <none>        <none>      <none>
kube-system     kube-controller-manager-minikube                            200m          <none>        <none>      <none>





# # Current live usage per pod in the node sort by memory or cpu
```
kubectl top pods -A --containers --sort-by=memory
kubectl top pods -A --containers --sort-by=cpu
```


Example:
NAMESPACE       POD                                                         NAME                        CPU(cores)   MEMORY(bytes)   
payment         kafka-controller-0                                          kafka                       530m         1245Mi          
payment         payment-consumers-75c989db5-nqc2g                           payment-consumers           399m         574Mi           
payment         payment-consumers-75c989db5-bcbnk                           payment-consumers           398m         543Mi           
kube-system     kube-apiserver-minikube                                     kube-apiserver              260m         528Mi           
payment         payment-consumers-75c989db5-h2mql                           payment-consumers           437m         521Mi           
payment         payment-consumers-75c989db5-wn6kw                           payment-consumers           422m         490Mi           
payment         payment-consumers-75c989db5-n6qnx                           payment-consumers           373m         489Mi





# # get event log of kubecluster , what happened, scaleup,kill,restart etc

```
kubectl get events -A --sort-by=.lastTimestamp | tail -n 60
```

Example Output
kube-system     13m         Warning   Unhealthy                      pod/metrics-server-7fbb699795-nft6c                        Readiness probe failed: HTTP probe failed with statuscode: 500
payment         13m         Warning   Unhealthy                      pod/payment-consumers-75c989db5-nqc2g                      Startup probe failed: Get "http://10.244.0.34:8080/actuator/health/liveness": dial tcp 10.244.0.34:8080: connect: connection refused
payment         13m         Warning   Unhealthy                      pod/payment-consumers-75c989db5-bcbnk                      Startup probe failed: Get "http://10.244.0.36:8080/actuator/health/liveness": dial tcp 10.244.0.36:8080: connect: connection refused
payment         13m         Warning   Unhealthy                      pod/payment-consumers-75c989db5-w7k44                      Startup probe failed: Get "http://10.244.0.35:8080/actuator/health/liveness": dial tcp 10.244.0.35:8080: connect: connection refused
payment         12m         Warning   Unhealthy                      pod/kafka-controller-0                                     Liveness probe failed: command timed out: "pgrep -f kafka" timed out after 5s
monitoring      12m         Warning   Unhealthy                      pod/prometheus-stack-prometheus-node-exporter-rpm2r        Liveness probe failed: Get "http://192.168.49.2:9100/": context deadline exceeded (Client.Timeout exceeded while awaiting headers)
payment         8m10s       Warning   Unhealthy                      pod/payment-service-7dcdc89f9-jpxtm                        Startup probe failed: Get "http://10.244.0.37:9000/actuator/health/liveness": dial tcp 10.244.0.37:9000: connect: connection refused
payment         8m10s       Warning   Unhealthy                      pod/payment-db-postgresql-0                                Liveness probe failed: command timed out: "/bin/sh -c exec pg_isready -U \"postgres\" -d \"dbname=payment_db\" -h 127.0.0.1 -p 5432" timed out after 10s


# Is an External Metrics API registered?
kubectl get apiservices | grep external.metrics

# What external metrics are available?
kubectl get --raw /apis/external.metrics.k8s.io/v1beta1 | jq .

# Do you have a metrics adapter (KEDA or Prometheus Adapter)?
kubectl get deploy -A | egrep 'keda|adapter|metrics-api'

# Do you have a Kafka lag exporter?
kubectl get deploy -A | egrep 'kafka.*exporter|burrow'




kubectl get --raw \
"/apis/external.metrics.k8s.io/v1beta1/namespaces/payment/kafka_consumer_group_lag_worst2"


# 1) Start a client pod (no restart policy)
kubectl run -n payment kafka-client \
--restart=Never \
--image=docker.io/bitnami/kafka:4.0.0-debian-12-r10 \
--command -- sleep infinity

# 2) Shell into it
kubectl exec -it -n payment kafka-client -- bash

# 3) Use the tools (bootstrap is the in-cluster DNS your chart exposed)
kafka-topics.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 --list
kafka-consumer-groups.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 --list

kafka-consumer-groups.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
--group payment-order-psp-call-executor-consumer-group --describe

kafka-topics.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
--topic payment_order_psp_call_requested_topic --describe

kafka-topics.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
--topic payment_order_created_topic --describe


/opt/bitnami/kafka/bin/kafka-topics.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
--describe --topic __transaction_state


/opt/bitnami/kafka/bin/kafka-get-offsets.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
--topic payment_order_created_topic \
--time -1


/opt/bitnami/kafka/bin/kafka-console-consumer.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
--topic payment_order_created_topic \
--partition 2 --offset 46538> \
--max-messages 1 --timeout-ms 10000 \
--property print.partition=true --property print.key=true --property print.timestamp=true



I have no name!@kafka-client:/$ /opt/bitnami/kafka/bin/kafka-transactions.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
describe --transactional-id business-tx-payment-consumers-75c989db5-8c4bh-1
CoordinatorId	TransactionalId                                	ProducerId	ProducerEpoch	TransactionState	TransactionTimeoutMs	CurrentTransactionStartTimeMs	TransactionDurationMs	TopicPartitions     	
0            	business-tx-payment-consumers-75c989db5-8c4bh-1	3546      	0            	PrepareCommit   	60000               	1758202920271                	5055486              	__consumer_offsets-0
I have no name!@kafka-client:/$

kafka-consumer-groups.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
--describe --group payment-order-psp-*



# 4) When done
kubectl delete pod -n payment kafka-client

# 0) Set a throwaway topic name
export TOPIC=_ping_$(date +%s)

# 1) Create the topic (clusters often disable auto-create)
/opt/bitnami/kafka/bin/kafka-topics.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
--create --topic "$TOPIC" --partitions 1 --replication-factor 1

# 2) (optional) Describe it
/opt/bitnami/kafka/bin/kafka-topics.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
--describe --topic "$TOPIC"

# 3) Start a consumer (leave running)
/opt/bitnami/kafka/bin/kafka-console-consumer.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
--topic "$TOPIC" --from-beginning

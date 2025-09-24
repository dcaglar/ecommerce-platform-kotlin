# 🚀 How to Start


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


CLIENT_TIMEOUT=3100ms MODE=constant RPS=10 PRE_VUS=40 MAX_VUS=160 k6 run load-tests/baseline-smoke-test.js 

CLIENT_TIMEOUT=3100ms MODE=constant RPS=20 PRE_VUS=40 MAX_VUS=160 k6 run load-tests/baseline-smoke-test.js 
CLIENT_TIMEOUT=3100ms MODE=constant RPS=40 PRE_VUS=40 MAX_VUS=160 k6 run load-tests/baseline-smoke-test.js 
CLIENT_TIMEOUT=3100ms MODE=constant RPS=60 PRE_VUS=40 MAX_VUS=160 k6 run load-tests/baseline-smoke-test.js 

CLIENT_TIMEOUT=3100ms MODE=constant RPS=100 PRE_VUS=40 MAX_VUS=160 k6 run load-tests/baseline-smoke-test.js 

```

connect to db after port-forwarding:



---s



## 📝 Notes

- **No manual Keycloak setup required** (realm, client, roles provisioned automatically).
- Tokens/secrets are handled by scripts in `keycloak/`.
- If you change Keycloak configs, re-run `deploy-k8s-overlay.sh local keycloak payment` to apply changes.
- For load testing, always generate tokens using the same Keycloak URL as your payment-service expects (see above for
  port-forwarding).
  export BS="kafka.payment.svc.cluster.local:9092"
  BS_BROKER=kafka-controller-0.kafka-controller-headless.payment.svc.cluster.local:9092
  BS_CONSUMER=kafka.payment.svc.cluster.local


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
kubectl top pods -n payment --containers --sort-by=memory
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



dogancaglar@Dogans-MacBook-Pro ecommerce-platform-kotlin % helm template payment-consumers charts/payment-consumers \                                                     
-n payment \
-f infra/helm-values/payment-consumers-values-local.yaml  --debug > rendered.yaml



APP_NS=payment
METRIC=kafka_consumer_group_lag_worst2
GROUP="payment-order-psp-call-executor-consumer-group"

# URL-encode the group (only needed if it contains special chars)
ENC_GROUP=$(python3 -c 'import urllib.parse,os;print(urllib.parse.quote(os.environ["GROUP"]))' 2>/dev/null || echo "$GROUP")

kubectl get --raw "/apis/external.metrics.k8s.io/v1beta1/namespaces/${APP_NS}/${METRIC}?labelSelector=consumergroup%3D${ENC_GROUP}" | jq .


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


# 3) REad from a topic by creating  a seperate consumer group,start from beginning
kafka-console-consumer.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
--topic payment_order_psp_call_requested_topic.DLQ \
--from-beginning \
--group dlq-debug-psp-$(date +%s) \
--property print.headers=true \
--property print.timestamp=true \
--max-messages 20


kafka-console-consumer.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
--topic payment_order_psp_result_updated_topic.DLQ \
--from-beginning \
--group dlq-debug-psp-result-$(date +%s) \
--property print.headers=true \
--property print.timestamp=true \
--max-messages 20

# 3) To see the total message in a topic per paartiton.(use consumer group you created above)
I have no name!@kafka-client:/$ kafka-consumer-groups.sh   --bootstrap-server kafka.payment.svc.cluster.local:9092   --group dlq-debug-psp-1758716393   --describe

Consumer group 'dlq-debug-psp-1758716393' has no active members.

GROUP                    TOPIC                                      PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG             CONSUMER-ID     HOST            CLIENT-ID
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 7          60              103             43              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 38         38              133             95              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 9          27              105             78              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 40         32              75              43              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 11         31              98              67              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 42         32              125             93              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 13         28              63              35              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 44         27              99              72              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 15         20              72              52              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 46         16              22              6               -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 17         33              91              58              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 19         37              135             98              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 21         35              113             78              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 22         44              108             64              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 24         36              106             70              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 26         37              113             76              -               -               -


#  Just watch new DLQ messages (don’t touch old ones)
kafka-console-consumer.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
--topic payment_order_psp_call_requested_topic.DLQ \
--group dlq-watch-$(date +%s) \
--property print.headers=true \
--property print.timestamp=true \
--consumer-property auto.offset.reset=latest


kafka-console-consumer.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
--topic payment_order_psp_result_updated_topic.DLQ \
--group dlq-watch-$(date +%s) --property print.headers=true \
--property print.timestamp=true \
--consumer-property auto.offset.reset=latest




kafka-topics.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
--topic payment_order_created_topic --describe


/opt/bitnami/kafka/bin/kafka-topics.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
--describe --topic __transaction_state



I have no name!@kafka-client:/$ /opt/bitnami/kafka/bin/kafka-transactions.sh \
--bootstrap-server kafka.payment.svc.cluster.local:9092 \
describe --transactional-id business-tx-payment-consumers-75c989db5-8c4bh-1
CoordinatorId	TransactionalId                                	ProducerId	ProducerEpoch	TransactionState	TransactionTimeoutMs	CurrentTransactionStartTimeMs	TransactionDurationMs	TopicPartitions     	
0            	business-tx-payment-consumers-75c989db5-8c4bh-1	3546      	0            	PrepareCommit   	60000               	1758202920271                	5055486              	__consumer_offsets-0
I have no name!@kafka-client:/$

kafka-consumer-groups.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
--describe --group payment-order-psp-*


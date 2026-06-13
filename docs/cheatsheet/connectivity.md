# Connectivity Troubleshooting (Kafka, DB, Ingress)

Use this page to quickly diagnose and fix local connectivity problems after bringing up the stack.

Quick triage
- Check pods and services:
```bash
kubectl get pods -A
kubectl get svc -A
```
- Verify service endpoints exist:
```bash
kubectl -n payment get endpoints kafka
kubectl -n payment get endpoints payment-db-postgresql
```
- With OrbStack, LoadBalancer IPs and native routing (`*.k8s.orb.local`) are available by default. No tunnels or port-forwarding needed!
- Confirm payment-service base URL and host:
```bash

```

Kafka connectivity
1) Verify Kafka service resolves and has endpoints
```bash
kubectl -n payment get svc kafka -o wide
kubectl -n payment get endpoints kafka
```

2) Exec a Kafka client inside the cluster and test
- Start a client pod:
```bash
kubectl run -n payment kafka-client \
  --restart=Never \
  --image=docker.io/bitnamilegacy/kafka:4.0.0-debian-12-r10 \
  --command -- sleep infinity
```
- Shell into it:
```bash
kubectl exec -it -n payment kafka-client -- bash
```
- List topics and consumer groups:
```bash
kafka-topics.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 --list
kafka-consumer-groups.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 --list
```
- Describe a consumer group:
```bash
kafka-consumer-groups.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --group payment-order-psp-call-executor-consumer-group --describe
```

3) Inspect Kafka Exporter and lag metrics
```bash
kubectl -n payment get deploy | grep kafka-exporter
```
- If Prometheus is forwarded (9090), query in UI:
  - kafka_consumergroup_lag
  - kafka_consumergroup_current_offset
- Verify external metric registered:
```bash
kubectl get --raw \
  "/apis/external.metrics.k8s.io/v1beta1/namespaces/payment/kafka_consumer_group_lag_worst2" | jq .
```

4) Common issues and fixes
- No endpoints for kafka service: wait for pod ready or check pod logs:
```bash
kubectl -n payment logs statefulset/kafka-controller -c kafka --tail=200
```
- DNS issue from host: use the in-cluster DNS name from inside a pod, or port‑forward a bootstrap port if needed.
- Connection refused from app: verify KAFKA bootstrap address matches the service DNS and port (PLAINTEXT 9092), and the env is wired in the chart values.

Database (PostgreSQL) connectivity
1) Verify service and endpoints
```bash
kubectl -n payment get svc payment-db-postgresql -o wide
kubectl -n payment get endpoints payment-db-postgresql
```

2) Connect from host via OrbStack NodePort
```bash
psql -h 127.0.0.1 -p 32032 -U payment -d payment_db
```
*(Verify port 32032 via `kubectl get svc`)*

3) Connect from inside the cluster
```bash
kubectl -n payment exec -it deploy/payment-service -- sh -c 'apk add --no-cache postgresql-client || true; psql -h payment-db-postgresql -U payment -d payment_db -c "select 1"'
```

4) Common issues and fixes
- Auth failures: verify DB credentials secret and env in deployment; recreate via infra/scripts/create-app-db-credentials-local.sh if needed.
- Pending endpoints: wait for StatefulSet ready:
```bash
kubectl -n payment rollout status statefulset/payment-db-postgresql
```
- Connection refused: ensure port-forwarding or service/Endpoints are correct; check pod logs for readiness probe failures.

Ingress and service access
- Ensure ingress controller is running
- On OrbStack, the ingress LoadBalancer automatically gets an IP and `.k8s.orb.local` DNS routing works out of the box.
```bash
# Verify base URL natively responds:
curl -I http://payment.k8s.orb.local
```

Keycloak and tokens
- Set KEYCLOAK_URL/KC_URL to Keycloak's native NodePort `32080`:
```bash
export KEYCLOAK_URL=http://127.0.0.1:32080
export KC_URL=http://127.0.0.1:32080
```
- Provision realm/clients:
```bash
KEYCLOAK_URL=http://keycloak.payment.svc.cluster.local:8080 ./keycloak/provision-keycloak.sh
```
- Get token (saved at `keycloak/output/jwt/payment-service.token`):
```bash
./keycloak/get-token.sh
# Optional: ./keycloak/get-token.sh http://keycloak.payment.svc.cluster.local:8080 6   # request 6-hour token
```

Helpful references (avoid duplication)
- docs/cheatsheet/kafka-local-connection.md
- docs/cheatsheet/database.md
- docs/cheatsheet/promotheus-grafana-monitoring.md

If problems persist
- Describe events:
```bash
kubectl get events -A --sort-by=.lastTimestamp | tail -n 60
```
- Check resource pressure:
```bash
kubectl top nodes
kubectl top pods -A --containers --sort-by=memory
```
- Verify HPA/external metrics:
```bash
kubectl get apiservices | grep external.metrics
```

# Infra scripts — simple local bring-up guide

This is a short, practical “start order” for spinning up the stack on minikube, with one‑line notes on what each script does.

Prereqs (once):
- Docker Desktop (or Docker Engine) running
- minikube, kubectl, helm installed
- Troubleshooting connectivity? See docs/troubleshooting/connectivity.md

Recommended order
1) Bootstrap a local Kubernetes cluster
   - What: Creates/uses a minikube profile sized from your Docker resources and enables metrics-server.
   - Run:
```bash
./bootstrap-minikube-cluster.sh
```

2) Deploy core dependencies
   - What: Config, Keycloak, Postgres, Redis, and Kafka in the payment namespace.
   - Run:
```bash
./deploy-all-local.sh
```

3) Monitoring stack (Prometheus + Grafana)
   - What: Installs kube-prometheus-stack into monitoring.
   - Run:
```bash
./deploy-monitoring-stack.sh
```

4) Kafka Exporter (Prometheus metrics for Kafka)
   - What: Exposes Kafka consumer lag, offsets, etc. for Prometheus.
   - Run:
```bash
./deploy-kafka-exporter-local.sh
```

5) Payment Service (Ingress, endpoints.json)
   - What: Deploys the payment-service chart and sets up ingress. Writes infra/endpoints.json.
   - Tip: For a LoadBalancer IP, run in a separate terminal:
```bash
minikube -p newprofile tunnel
```
   - Run:
```bash
./deploy-payment-service-local.sh
```

6) Payment Consumers
   - What: Deploys the Kafka consumer workers for payment flows.
   - Run:
```bash
./deploy-payment-consumers-local.sh
```

7) Expose consumer lag as an external metric (for HPA)
   - What: Installs/promotes prometheus-adapter with a rule that surfaces worst consumer-lag per group.
   - Run:
```bash
./add-consumer-lag-metric.sh
```

8) Local access via port-forwarding (optional, recommended before Keycloak provisioning)
   - What: Opens local ports to Keycloak, Postgres, Prometheus, Grafana, etc. Press Ctrl+C to stop.
   - Run:
```bash
./port-forwarding.sh
```

9) Provision Keycloak realm and clients
   - What: Creates realm, role, and OIDC confidential clients; writes secrets to keycloak/output/secrets.txt.
   - Tip: If you aren’t using port-forwarding, set KEYCLOAK_URL to your reachable Keycloak base.
   - Run:
```bash
KEYCLOAK_URL=http://127.0.0.1:8080 ./keycloak/provision-keycloak.sh
```

10) Generate an access token for payment-service
    - What: Uses the client secret to get a JWT; saves it to keycloak/access.token.
    - Tip: KC_URL in the script defaults to http://keycloak:8080; override if needed.
    - Run:
```bash
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token.sh
```

11) Call the API with the token (optional smoke test)
    - What: Use the saved token and the host header from endpoints.json.
    - Example:
```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json); HOST=$(jq -r .host_header infra/endpoints.json); curl -i -H "Host: $HOST" -H "Authorization: Bearer $(cat keycloak/access.token)" "$BASE_URL/actuator/health"
```

What each script does (quick reference)
- bootstrap-minikube-cluster.sh
  - Starts minikube with a docker driver, CPU/RAM auto-detected, enables metrics-server, sets kubectl context.
- deploy-all-local.sh
  - Applies platform config, then deploys Keycloak, Postgres (payment-db), Redis, and Kafka to the payment namespace.
- deploy-monitoring-stack.sh
  - Installs kube-prometheus-stack with values under infra/helm-values; waits for core components to be ready.
- deploy-kafka-exporter-local.sh
  - Installs prometheus-kafka-exporter (Helm) into payment; provides kafka_consumergroup_* metrics to Prometheus.
- deploy-payment-service-local.sh
  - Installs ingress-nginx (disables minikube addon to avoid duplicates), deploys payment-service, computes endpoint base URL, and writes infra/endpoints.json. Supports LoadBalancer via minikube tunnel or falls back to NodePort.
- deploy-payment-consumers-local.sh
  - Deploys the payment-consumers Helm chart.
- add-consumer-lag-metric.sh
  - Installs prometheus-adapter and configures an external metric (kafka_consumer_group_lag_worst2) derived from Prometheus data, so HPAs can scale on consumer lag.
- port-forwarding.sh
  - Resilient port-forward loops to: Keycloak (8080), Postgres (5432), Prometheus (9090), Grafana (3000), and payment-service mgmt (9000). Optionally ingress if PF_INGRESS=true.
- keycloak/provision-keycloak.sh
  - Provisions Keycloak (realm, role, clients), writes client secrets to keycloak/output/secrets.txt.
- keycloak/get-token.sh
  - Fetches a client-credentials access token and saves it to keycloak/access.token.

Name hints vs. your list
- deploy-monitoring-stack  → deploy-monitoring-stack.sh
- deploy-kafka-exporter    → deploy-kafka-exporter-local.sh
- deploy-payment-sercice   → deploy-payment-service-local.sh
- deploy-payment-consumers → deploy-payment-consumers-local.sh
- add-consumer-lag         → add-consumer-lag-metric.sh

Troubleshooting quick tips
- If payment-service Ingress says pending, run:
```bash
minikube -p newprofile tunnel
```
- If Grafana/Prometheus aren’t reachable, run port-forwarding and open the UIs:
```bash
./port-forwarding.sh
```
- Keycloak scripts use http://keycloak:8080 by default. If running from your host, either port-forward or override URLs:
```bash
KEYCLOAK_URL=http://127.0.0.1:8080
KC_URL=http://127.0.0.1:8080
```
- To verify the external metric appears:
```bash
kubectl get --raw "/apis/external.metrics.k8s.io/v1beta1/namespaces/payment/kafka_consumer_group_lag_worst2" | jq .
```
- More: docs/troubleshooting/connectivity.md

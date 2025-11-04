# 🚀 How to Start

Follow these steps to provision auth, get a token, test the API, and run load tests locally.

This is a short, practical “start order” for spinning up the stack on minikube, with one‑line notes on what each script does.

Prereqs (once):
- Docker Desktop (or Docker Engine) running
- minikube, kubectl, helm installed
- Troubleshooting connectivity? See docs/troubleshooting/connectivity.md

## 0️⃣ Start the infrastructure and services

Pre-step: switch to the project root directory and make scripts executable
```bash
cd /path/to/ecommerce-platform-kotlin
chmod +x infra/scripts/*.sh
```

Recommended order

1) Bootstrap a local Kubernetes cluster
- What: Creates/uses a minikube profile sized from your Docker resources and enables metrics-server.
- Run:
```bash
infra/scripts/bootstrap-minikube-cluster.sh
```

2) Deploy core infrastructure
- What: Config, Keycloak, Postgres, Redis, and Kafka in the payment namespace.
- Run:
```bash
infra/scripts/deploy-all-local.sh
```

3) Monitoring stack (Prometheus + Grafana)
- What: Installs kube-prometheus-stack into monitoring.
- Run:
```bash
infra/scripts/deploy-monitoring-stack.sh
```

4) Kafka Exporter (Prometheus metrics for Kafka)
- What: Exposes Kafka consumer lag, offsets, etc. for Prometheus.
- Run:
```bash
infra/scripts/deploy-kafka-exporter-local.sh
```

5) Payment Service (Ingress, endpoints.json)
- What: Deploys the payment-service chart and sets up ingress. Writes infra/endpoints.json.
- Tip: For a LoadBalancer IP, run in a separate terminal:
```bash
sudo -E minikube -p newprofile tunnel
```
- Then run:
```bash
infra/scripts/deploy-payment-service-local.sh
```

6) Payment Consumers
- What: Deploys the Kafka consumer workers for payment flows.
- Run:
```bash
infra/scripts/deploy-payment-consumers-local.sh
```

7) Expose consumer lag as an external metric (for HPA)
- What: Installs/promotes prometheus-adapter with a rule that surfaces worst consumer-lag per group.
- Run:
```bash
infra/scripts/add-consumer-lag-metric.sh
```

8) Local access via port-forwarding (optional, recommended before Keycloak provisioning)
- What: Opens local ports to Keycloak, Postgres, Prometheus, Grafana, etc. Press Ctrl+C to stop.
- Run:
```bash
infra/scripts/port-forwarding.sh
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

11) Elasticsearch/Logstash/Kibana stack (OPTIONAL)
- What: Installs ELK stack for log aggregation and searching.
- Run:
```bash
infra/scripts/deploy-observability-stack.sh
```

4) Send test payment request
- Use the saved token to call the API. Prefer the dynamic example to avoid hardcoded IPs.

- Dynamic (reads host and base URL from infra/endpoints.json):
```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json)
HOST=$(jq -r .host_header infra/endpoints.json)

echo "Using BASE_URL=$BASE_URL"
echo "Using Host header=$HOST"

curl -i -X POST "$BASE_URL/payments" \
  -H "Host: $HOST" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/access.token)" \
  -d '{
    "orderId": "ORDER-20240508-XYZ",
    "buyerId": "BUYER-123",
    "totalAmount": { "quantity": 19949, "currency": "EUR" },
    "paymentOrders": [
      { "sellerId": "SELLER-111", "amount": { "quantity": 4999, "currency": "EUR" }},
      { "sellerId": "SELLER-222", "amount": { "quantity": 2950, "currency": "EUR" }},
      { "sellerId": "SELLER-333", "amount": { "quantity": 12000, "currency": "EUR" }}
    ]
  }'
```

- Static example (replace host/IP if different):
```bash
curl -i -X POST http://127.0.0.1/payments \
  -H "Host: payment.192.168.49.2.nip.io" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/access.token)" \
  -d '{
    "orderId": "ORDER-20240508-XYZ",
    "buyerId": "BUYER-123",
    "totalAmount": { "quantity": 19949, "currency": "EUR" },
    "paymentOrders": [
      { "sellerId": "SELLER-111", "amount": { "quantity": 4999, "currency": "EUR" }},
      { "sellerId": "SELLER-222", "amount": { "quantity": 2950, "currency": "EUR" }},
      { "sellerId": "SELLER-333", "amount": { "quantity": 12000, "currency": "EUR" }}
    ]
  }'
```

## 2️⃣ Run Unit & Integration Tests

- Why: Verify the codebase using MockK and Testcontainers.
- What: Tests cover domain logic, application services, and infrastructure adapters.

```bash
# Run only unit tests (fast, excludes integration tests by filename pattern)
mvn clean test

# Run only integration tests (slower, uses TestContainers, runs via Failsafe)
mvn -B clean verify -DskipUnitTests=true

# Run both unit and integration tests (requires both commands)
mvn  clean verify

# Run tests for specific modules (unit tests only)
mvn clean test -pl payment-application,payment-infrastructure,payment-domain,common

# Run integration tests for specific modules
mvn clean verify -DskipUnitTests=true -pl payment-infrastructure

# Run specific test classes (unit tests)
mvn test -Dtest=CreatePaymentServiceTest,ProcessPaymentServiceTest

# Run specific integration test classes
mvn verify -DskipUnitTests=true -Dtest=AccountBalanceMapperIntegrationTest 
```

**Test Organization:**
- **Unit Tests** (`*Test.kt`): Use mocks only, no external dependencies
  - **Execution**: Run with `mvn test` (Maven Surefire plugin, `test` phase)
  - **Design**: Fast tests run on every build for quick feedback
  - **Configuration**: Surefire excludes `*IntegrationTest.kt` files by filename pattern
- **Integration Tests** (`*IntegrationTest.kt`): Use real external dependencies via TestContainers, all tagged with `@Tag("integration")`
  - **Execution**: Run with `mvn verify` (Maven Failsafe plugin, `integration-test` + `verify` phases)
  - **Design**: Slower tests run before release/deployment for comprehensive validation
  - **Configuration**: Failsafe includes `*IntegrationTest.kt` files by filename pattern
  - **Lifecycle Separation**: Surefire and Failsafe complement each other - unit tests provide fast feedback, integration tests provide comprehensive validation before releases
- **No Hanging Tests**: All MockK syntax issues resolved for reliable test execution
- **Type Inference Fixed**: Resolved MockK type inference issues in `OutboxDispatcherJobTest.kt` with explicit type hints and Jackson JSR310 module configuration

## 3️⃣ Run Load Tests

- Why: Exercise the system under constant RPS profiles (run from project root).
```bash
CLIENT_TIMEOUT=3100ms MODE=constant RPS=10 PRE_VUS=40 MAX_VUS=160 DURATION=20m k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms MODE=constant RPS=10 PRE_VUS=40 MAX_VUS=160  DURATION=20m k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms MODE=constant RPS=40 PRE_VUS=40 MAX_VUS=160 DURATION=20m  k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms MODE=constant RPS=60 PRE_VUS=40 MAX_VUS=160 DURATION=20m  k6 run load-tests/baseline-smoke-test.js
CLIENT_TIMEOUT=3100ms MODE=constant RPS=80 PRE_VUS=40 MAX_VUS=160 DURATION=100m DURATION=20m k6 run load-tests/baseline-smoke-test.js
```

## Local Kubernetes Deployment Scripts

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
sudo -E minikube -p newprofile tunnel
```
- If Grafana/Prometheus aren’t reachable, run port-forwarding and open the UIs:
```bash
./infra/scripts/port-forwarding.sh
```
- Keycloak scripts use http://keycloak:8080 by default. If running from your host, either port-forward or override URLs:
```bash
export KEYCLOAK_URL=http://127.0.0.1:8080
export KC_URL=http://127.0.0.1:8080
```
- To verify the external metric appears:
```bash
kubectl get --raw "/apis/external.metrics.k8s.io/v1beta1/namespaces/payment/kafka_consumer_group_lag_worst2" | jq .
```
- More: docs/troubleshooting/connectivity.md


---

## ✅ Summary

You now have:

- 🟢 A fully running stack: Keycloak, Postgres, Kafka, Redis, payment-service, and payment-consumers.
- 📊 Monitoring (Prometheus + Grafana): [http://localhost:3000](http://localhost:3000)
- 🔍 Observability (ELK): [http://localhost:5601](http://localhost:5601)
- 🔐 Auth via Keycloak: [http://localhost:8080](http://localhost:8080)

If everything’s green in Grafana and Kibana, your platform is ready for testing.

For deep architecture or flow diagrams, see:
- [`docs/architecture.md`](architecture-internal-reader.md)
- [`README.md`](../README.md)

---
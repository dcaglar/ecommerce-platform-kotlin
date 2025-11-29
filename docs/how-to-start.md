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

10) Generate access tokens
- What: Get JWT tokens for different use cases; saves tokens under `keycloak/output/jwt`.
- Tip: KC_URL defaults to http://keycloak:8080; override if needed (e.g., when port-forwarding).
- Default validity is ~1 hour. Append a TTL (hours) as the final argument to keep a test token alive longer (e.g., `... 6` for 6 h).
- Reuse the saved token across requests until it expires; no need to regenerate for every call.

**For Payment Creation (Service Account with payment:write):**
```bash
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token.sh
# Token saved to: keycloak/output/jwt/payment-service.token
# Claims saved to: keycloak/output/jwt/payment-service.claims.json

# Request a 6-hour token via CLI override:
./keycloak/get-token.sh http://127.0.0.1:8080 6
```
> Optional CLI override (with TTL): `./keycloak/get-token.sh http://my-keycloak:8080 6`

**For Balance Queries - Finance/Admin (Backoffice user with FINANCE role):**
```bash
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-finance.sh
# Token saved to: keycloak/output/jwt/finance-<username>.token
# Claims saved to: keycloak/output/jwt/finance-<username>.claims.json

# Request a 4-hour token for finance-ops:
./keycloak/get-token-finance.sh finance-ops finance123 http://127.0.0.1:8080 4
```
> Example: running without arguments issues a token for the `finance-ops / finance123` user.
> Optional CLI override (with TTL): `./keycloak/get-token-finance.sh finance-ops finance123 http://my-keycloak:8080 6`

**For Balance Queries - Seller User (User Account with SELLER role, Case 1):**
```bash
# Default: seller-111 (SELLER-111)
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-seller.sh

# Or specify a different seller
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-seller.sh seller-222 seller123
# Token saved to: keycloak/output/jwt/seller-<username>.token
# Claims saved to: keycloak/output/jwt/seller-<username>.claims.json

# Request an 8-hour token for seller-222:
./keycloak/get-token-seller.sh seller-222 seller123 http://127.0.0.1:8080 8
```
> Example: running without arguments produces `keycloak/output/jwt/seller-seller-111.token`.
> Optional CLI override: third argument can pass Keycloak URL; append a fourth argument for TTL (hours).

**For Balance Queries - Merchant API (M2M with SELLER_API role, Case 3):**
```bash
# Default: SELLER-111
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-merchant-api.sh

# Or specify a different merchant
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-merchant-api.sh SELLER-222
# Token saved to: keycloak/output/jwt/merchant-api-<SELLER_ID>.token
# Claims saved to: keycloak/output/jwt/merchant-api-<SELLER_ID>.claims.json

# Request a 12-hour token for SELLER-222:
./keycloak/get-token-merchant-api.sh SELLER-222 http://127.0.0.1:8080 12
```
> Example: `SELLER_ID=SELLER-111` writes to `keycloak/output/jwt/merchant-api-SELLER-111.token`.
> Optional CLI override: second argument can pass Keycloak URL; append a third argument for TTL (hours).

Each script issues a token from a different principal:
- **payment-service** → service account with `payment:write`
- **finance-* user** → backoffice user with `FINANCE`
- **seller-* user** → interactive user with `SELLER`
- **merchant-api-* client** → machine-to-machine client with `SELLER_API`

Tokens are intentionally scoped to those roles so you can exercise each endpoint with the appropriate identity.

11) Elasticsearch/Logstash/Kibana stack (OPTIONAL)
- What: Installs ELK stack for log aggregation and searching.
- Run:
```bash
infra/scripts/deploy-observability-stack.sh
```

4) Send test payment request

You can test payment creation in two ways:

### Option A: Using curl (Command Line)

Use the saved token to call the API. Prefer the dynamic example to avoid hardcoded IPs.

- Dynamic (reads host and base URL from infra/endpoints.json):
```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json)
HOST=$(jq -r .host_header infra/endpoints.json)

echo "Using BASE_URL=$BASE_URL"
echo "Using Host header=$HOST"

curl -i -X POST "$BASE_URL/api/v1/payments" \
  -H "Host: $HOST" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/payment-service.token)" \
  -d '{
    "orderId": "ORDER-1755",
    "buyerId": "BUYER-1755",
    "totalAmount": { "quantity": 3510, "currency": "EUR" },
    "paymentOrders": [
      { "sellerId": "SELLER-111", "amount": { "quantity": 1755, "currency": "EUR" }},
      { "sellerId": "SELLER-222", "amount": { "quantity":1755, "currency": "EUR" }}
    ]
  }'
```

- Static example (replace host/IP if different):
```bash
curl -i -X POST http://127.0.0.1/api/v1/payments \
  -H "Host: payment.192.168.49.2.nip.io" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/payment-service.token)" \
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

### Option B: Using Checkout Demo Page (Interactive UI)

A developer-friendly web interface for testing payment creation without managing tokens manually.

**Prerequisites:**
- Node.js 18+ installed
- Steps 1-9 completed (infrastructure, Keycloak provisioning)

**Setup:**

1. Install dependencies:
```bash
cd checkout-demo
npm install
```

2. Generate environment configuration:
```bash
# Make sure you've run step 9 (provision-keycloak.sh) first
npm run setup-env
```

This automatically:
- Reads client secret from `keycloak/output/secrets.txt`
- Reads API endpoints from `infra/endpoints.json`
- Creates `.env` file with all configuration

3. Start the demo:
```bash
npm run dev
```

This starts both:
- Frontend server at `http://localhost:3000` (Vite)
- Backend proxy server at `http://localhost:3001` (simulates order-service/checkout-service)

The app will automatically open at `http://localhost:3000`

**Usage:**

1. Fill the payment form:
   - Order ID (e.g., `ORDER-TEST-001`)
   - Buyer ID (e.g., `BUYER-123`)
   - Total amount (in smallest currency unit, e.g., cents)
   - Select currency
   - Add one or more payment orders with seller IDs and amounts

2. Click "Send Payment Request"

3. View the response and equivalent curl command

The backend proxy automatically handles token acquisition and payment-service calls - no manual token management needed!

**Architecture:**

The checkout demo uses a production-like flow:
- **Frontend** (React) → calls **Backend Proxy** (Node.js/Express)
- **Backend Proxy** → gets token from Keycloak (server-to-server)
- **Backend Proxy** → calls payment-service with token (server-to-server)
- **Frontend** ← receives payment response

> 💡 **Note**: The backend proxy simulates a production backend (order-service/checkout-service) and needs CORS enabled because the browser calls it directly (browser → proxy is cross-origin).

**Troubleshooting:**

- **"Client secret not found"**: Run `npm run setup-env` after provisioning Keycloak
- **"Cannot reach Keycloak"**: Ensure Keycloak port-forwarding is active: `kubectl port-forward -n payment svc/keycloak 8080:8080`
- **Payment request errors**: Check proxy console for detailed error messages (it logs token and payment-service call errors)
- **Network errors**: Verify payment service is running and `infra/endpoints.json` is correct

For more details, see `checkout-demo/README.md`.

## 5️⃣ Query Balance Endpoints

Balance endpoints support three authentication scenarios (matching real-world marketplace patterns):

### Case 1: Seller User via Customer Area Frontend
- **Endpoint**: `GET /api/v1/sellers/me/balance`
- **Authorization**: Requires `SELLER` role and `seller_id` claim in JWT
- **Token Type**: User token (OIDC Authorization Code flow or Direct Access Grants for testing)
- **Use Case**: Merchant logs into customer-area web app and views their balance
- **Client**: `customer-area-frontend`

**Steps:**
1. Get a token for a seller user:
```bash
# Default: seller-111 (SELLER-111)
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-seller.sh

# Or specify a different seller
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-seller.sh seller-222 seller123
# Token saved to: keycloak/output/jwt/seller-<username>.token
```

2. Query your own balance (dynamic):
```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json)
HOST=$(jq -r .host_header infra/endpoints.json)

echo "Using BASE_URL=$BASE_URL"
echo "Using Host header=$HOST"

curl -i -X GET "$BASE_URL/api/v1/sellers/me/balance" \
  -H "Host: $HOST" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/seller-SELLER-111.token)" 
```

### Case 2: Finance/Admin User via Backoffice
- **Endpoint**: `GET /api/v1/sellers/{sellerId}/balance`
- **Authorization**: Requires `FINANCE` or `ADMIN` role
- **Token Type**: User token (OIDC Authorization Code flow / Direct Access Grants for testing)
- **Use Case**: Internal finance or support staff log into backoffice app and check any seller's balance
- **Client**: `backoffice-ui`

**Steps:**
1. Get a token with FINANCE role (backoffice user for testing):
```bash
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-finance.sh
# Token saved to: keycloak/output/jwt/finance-<username>.token
```

2. Query balance for any seller (dynamic):
```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json)
HOST=$(jq -r .host_header infra/endpoints.json)

echo "Using BASE_URL=$BASE_URL"
echo "Using Host header=$HOST"
curl -i -X GET "$BASE_URL/api/v1/sellers/SELLER-111/balance" \
  -H "Host: $HOST" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/finance-finance-ops.token)"
```


### Case 3: Merchant API (Machine-to-Machine)
- **Endpoint**: `GET /api/v1/sellers/me/balance`
- **Authorization**: Requires `SELLER_API` role and `seller_id` claim in JWT
- **Token Type**: Machine token (Client Credentials flow)
- **Use Case**: Merchant's system (ERP, OMS) calls Seller API directly to retrieve balances
- **Client**: `merchant-api-{SELLER_ID}` (one per merchant)

**Steps:**
1. Get a token for merchant API (M2M):
```bash
# Default: SELLER-111
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-merchant-api.sh

# Or specify a different merchant
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-merchant-api.sh SELLER-222
# Token saved to: keycloak/output/jwt/merchant-api-<SELLER_ID>.token
```

2. Query balance via merchant API (dynamic):
```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json)
HOST=$(jq -r .host_header infra/endpoints.json)

curl -i -X GET "$BASE_URL/api/v1/sellers/me/balance" \
  -H "Host: $HOST" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/merchant-api-SELLER-111.token)"  
```


**Expected Response (all cases):**
```json
{
  "balance": 50000,
  "currency": "EUR",
  "accountCode": "MERCHANT_ACCOUNT.SELLER-111.EUR",
  "sellerId": "SELLER-111"
}
```

**Test Credentials Created by Provisioning:**
- **User Accounts (Case 1):**
  - `seller-111` / `seller123` → seller_id: `SELLER-111`
  - `seller-222` / `seller123` → seller_id: `SELLER-222`
  - `seller-333` / `seller123` → seller_id: `SELLER-333`
- **Merchant API Clients (Case 3):**
  - `merchant-api-SELLER-111` → Client Credentials, SELLER_API role
  - `merchant-api-SELLER-222` → Client Credentials, SELLER_API role
  - `merchant-api-SELLER-333` → Client Credentials, SELLER_API role

**Authentication Flow Summary:**

| Case | Endpoint | Role | Client | Grant Type | Token Type |
|------|----------|------|--------|------------|------------|
| 1 | `/api/v1/sellers/me/balance` | `SELLER` | `customer-area-frontend` | OIDC Auth Code / Password | User token |
| 2 | `/api/v1/sellers/{sellerId}/balance` | `FINANCE`/`ADMIN` | `backoffice-ui` | OIDC Auth Code / Direct Access (testing) | User token |
| 3 | `/api/v1/sellers/me/balance` | `SELLER_API` | `merchant-api-{SELLER_ID}` | Client Credentials | Machine token |

**HTTP Status Codes:**
- `200 OK` - Success
- `400 Bad Request` - Invalid request (e.g., missing seller_id claim)
- `401 Unauthorized` - Missing or invalid JWT token
- `403 Forbidden` - Valid token but insufficient permissions
- `404 Not Found` - Seller account not found

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
    - Fetches a client-credentials access token for payment-service and saves it to `keycloak/output/jwt/payment-service.token`. Pass an optional TTL (hours) as the final argument.
- keycloak/get-token-seller.sh
    - Fetches a user token with SELLER role via Direct Access Grants (password grant) and saves it to `keycloak/output/jwt/seller-<username>.token`. CLI order: username, password, Keycloak URL, TTL (hours).
- keycloak/get-token-finance.sh
    - Fetches a user token with FINANCE role (backoffice) and saves it to `keycloak/output/jwt/finance-<username>.token`. CLI order: username, password, Keycloak URL, TTL.
- keycloak/get-token-merchant-api.sh
    - Fetches a client-credentials access token for merchant API clients with SELLER_API role and saves it to `keycloak/output/jwt/merchant-api-<SELLER_ID>.token`. CLI order: SELLER_ID, Keycloak URL, TTL.

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
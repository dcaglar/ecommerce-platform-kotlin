# 🚀 How to Start

A step-by-step guide to get the ecommerce payment platform running locally on minikube.

## Prerequisites

- Docker Desktop (or Docker Engine) running
- minikube, kubectl, and helm installed
- jq installed (for parsing JSON)

For troubleshooting connectivity issues, see `docs/troubleshooting/connectivity.md`.

## Step 1: Prepare Scripts

Make all deployment scripts executable:

```bash
cd /path/to/ecommerce-platform-kotlin
chmod +x infra/scripts/*.sh
```

## Step 2: Start Kubernetes Cluster

Bootstrap a local minikube cluster:

```bash
infra/scripts/bootstrap-minikube-cluster.sh
```

This creates a minikube profile with auto-detected CPU/RAM resources and enables metrics-server.

## Step 3: Deploy Core Infrastructure

Deploy Keycloak, PostgreSQL, Redis, and Kafka:

```bash
infra/scripts/deploy-all-local.sh
```

This deploys all core infrastructure components to the `payment` namespace.

## Step 4: Deploy Monitoring Stack (Optional)

Install Prometheus and Grafana for monitoring:

```bash
infra/scripts/deploy-monitoring-stack.sh\
```

After deployment, access Grafana at `http://localhost:3000` (requires port-forwarding).


## Deployment
run the scripts below for payment-service 
This scripts:
- build docker image and commit to remote docker repo
- Deploys the payment-service Helm chart
- Sets up ingress routing
- Writes API endpoints to `infra/endpoints.json`
```bash
build-and-push-payment-service-docker-repo.sh 
```

```bash
# Optional: For LoadBalancer access, run minikube tunnel in a separate terminal:
# sudo -E minikube -p newprofile tunnel
infra/scripts/deploy-payment-service-local.sh
```


This scripts:
- build docker image and commit to` remote docker repo`
- Deploys the payment-consumers Helm chart

```bash
build-and-push-payment-consumers-docker-repo.sh 
```

```bash
infra/scripts/deploy-payment-consumers-local.sh 
```



## Step 6: Deploy Payment Consumers

Deploy the Kafka consumer workers that process payment operations:

```bash
infra/scripts/deploy-payment-consumers-local.sh
```

## Step 7: Set Up Port Forwarding (Recommended)

Enable local access to services:

```bash
infra/scripts/port-forwarding.sh
```

This forwards:
- Keycloak: `http://localhost:8080`
- PostgreSQL: `localhost:5432`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

Keep this terminal running. Press Ctrl+C to stop.

## Step 8: Provision Keycloak

Create Keycloak realm, roles, and OIDC clients:

```bash
KEYCLOAK_URL=http://127.0.0.1:8080 ./keycloak/provision-keycloak.sh
```

This creates:
- Realm: `ecommerce-platform`
- Service account clients (payment-service, order-service, finance-service)
- User accounts for testing (seller-111, seller-222, seller-333, finance-ops)
- Merchant API clients
- Writes client secrets to `keycloak/output/secrets.txt`

> **Note**: If not using port-forwarding, set `KEYCLOAK_URL` to your reachable Keycloak endpoint.

## Step 9: Generate Access Tokens

Get JWT tokens for API authentication. Tokens are saved to `keycloak/output/jwt/`.

### For Payment API (Service Account)

```bash
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token.sh

# Optional: Request a longer-lived token (6 hours)
./keycloak/get-token.sh http://127.0.0.1:8080 6
```

Token saved to: `keycloak/output/jwt/payment-service.token`

### For Finance/Admin Access

```bash
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-finance.sh

# Optional: Specify user and TTL
./keycloak/get-token-finance.sh finance-ops finance123 http://127.0.0.1:8080 4
```

Token saved to: `keycloak/output/jwt/finance-<username>.token`

### For Seller User Access

```bash
# Default: seller-111 (SELLER-111)
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-seller.sh

# Specify different seller
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-seller.sh seller-222 seller123
```

Token saved to: `keycloak/output/jwt/seller-<username>.token`

### For Merchant API (M2M)

```bash
# Default: SELLER-111
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-merchant-api.sh

# Specify different merchant
KC_URL=http://127.0.0.1:8080 ./keycloak/get-token-merchant-api.sh SELLER-222
```

Token saved to: `keycloak/output/jwt/merchant-api-<SELLER_ID>.token`

## Step 10: Test Payment API

### Option A: Using curl

**1. Create a Payment Intent**

```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json)
HOST=$(jq -r .host_header infra/endpoints.json)
IDEMPOTENCY_KEY="idem-$(date +%s)-$RANDOM"

curl -i -X POST "$BASE_URL/api/v1/payments" \
  -H "Host: $HOST" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/payment-service.token)" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "orderId": "ORDER-001",
    "buyerId": "BUYER-001",
    "totalAmount": { "quantity": 2900, "currency": "EUR" },
    "paymentOrders": [
      { "sellerId": "SELLER-111", "amount": { "quantity": 1450, "currency": "EUR" }},
      { "sellerId": "SELLER-222", "amount": { "quantity": 1450, "currency": "EUR" }}
    ]
  }'
```

**Expected Response (201 Created):**
```json
{
  "paymentIntentId": "pi_xxxxx",
  "clientSecret": "pi_xxxxx_secret_xxxxx",
  "status": "CREATED",
  "orderId": "ORDER-001",
  "buyerId": "BUYER-001",
  "totalAmount": { "quantity": 2900, "currency": "EUR" },
  "createdAt": "2024-01-01T12:00:00Z"
}
```

**2. Authorize Payment Intent**

```bash
PAYMENT_ID="pi_xxxxx"  # Use the paymentIntentId from step 1

curl -i -X POST "$BASE_URL/api/v1/payments/$PAYMENT_ID/authorize" \
  -H "Host: $HOST" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/payment-service.token)" \
  -d '{}'
```

> **Note**: The `Idempotency-Key` header is required for payment creation. Use the same key for retries to ensure idempotent behavior.

### Option B: Using Checkout Demo UI

See `checkout-demo/README.md` for the interactive web interface that handles the complete payment flow with Stripe Payment Element.

## Step 11: Query Seller Balances

The platform supports three authentication scenarios for balance queries:

### Case 1: Seller Views Own Balance

**Endpoint:** `GET /api/v1/sellers/me/balance`  
**Role:** `SELLER`

```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json)
HOST=$(jq -r .host_header infra/endpoints.json)

curl -i -X GET "$BASE_URL/api/v1/sellers/me/balance" \
  -H "Host: $HOST" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/seller-seller-111.token)"
```

### Case 2: Finance/Admin Views Any Seller Balance

**Endpoint:** `GET /api/v1/sellers/{sellerId}/balance`  
**Role:** `FINANCE` or `ADMIN`

```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json)
HOST=$(jq -r .host_header infra/endpoints.json)

curl -i -X GET "$BASE_URL/api/v1/sellers/SELLER-111/balance" \
  -H "Host: $HOST" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/finance-finance-ops.token)"
```

### Case 3: Merchant API (M2M) Views Own Balance

**Endpoint:** `GET /api/v1/sellers/me/balance`  
**Role:** `SELLER_API`

```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json)
HOST=$(jq -r .host_header infra/endpoints.json)

curl -i -X GET "$BASE_URL/api/v1/sellers/me/balance" \
  -H "Host: $HOST" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/merchant-api-SELLER-111.token)"
```

**Expected Response:**
```json
{
  "balance": 50000,
  "currency": "EUR",
  "accountCode": "MERCHANT_ACCOUNT.SELLER-111.EUR",
  "sellerId": "SELLER-111"
}
```

## Step 12: Run Tests

### Unit Tests

```bash
mvn clean test
```

### Integration Tests

```bash
mvn clean verify -DskipUnitTests=true
```

### Run Both

```bash
mvn clean verify
```

## Step 13: Run Load Tests (Optional)

Exercise the system under load:

```bash
CLIENT_TIMEOUT=3100ms MODE=constant RPS=10 PRE_VUS=40 MAX_VUS=160 DURATION=20m \
  k6 run load-tests/baseline-smoke-test.js
```

## Deployment Scripts Reference

| Script | Purpose |
|--------|---------|
| `bootstrap-minikube-cluster.sh` | Creates minikube cluster with metrics-server |
| `deploy-all-local.sh` | Deploys Keycloak, Postgres, Redis, Kafka |
| `deploy-monitoring-stack.sh` | Installs Prometheus + Grafana |
| `deploy-kafka-exporter-local.sh` | Exposes Kafka metrics for Prometheus |
| `deploy-payment-service-local.sh` | Deploys payment-service with ingress |
| `deploy-payment-consumers-local.sh` | Deploys Kafka consumer workers |
| `add-consumer-lag-metric.sh` | Configures HPA metric for consumer lag |
| `port-forwarding.sh` | Forwards local ports to services |

## Test Credentials

Created by Keycloak provisioning:

**User Accounts:**
- `seller-111` / `seller123` → SELLER-111
- `seller-222` / `seller123` → SELLER-222
- `seller-333` / `seller123` → SELLER-333
- `finance-ops` / `finance123` → FINANCE role

**Merchant API Clients:**
- `merchant-api-SELLER-111` → SELLER_API role
- `merchant-api-SELLER-222` → SELLER_API role
- `merchant-api-SELLER-333` → SELLER_API role

## Troubleshooting

**Payment Service Ingress Pending:**
```bash
sudo -E minikube -p newprofile tunnel
```

**Services Not Reachable:**
- Run `infra/scripts/port-forwarding.sh` to forward ports
- Verify services are running: `kubectl get pods -n payment`

**Keycloak Connection Issues:**
```bash
export KEYCLOAK_URL=http://127.0.0.1:8080
export KC_URL=http://127.0.0.1:8080
```

**Verify External Metrics:**
```bash
kubectl get --raw "/apis/external.metrics.k8s.io/v1beta1/namespaces/payment/kafka_consumer_group_lag_worst2" | jq .
```

## Summary

You should now have:
- ✅ Kubernetes cluster running
- ✅ Core infrastructure deployed (Keycloak, Postgres, Redis, Kafka)
- ✅ Payment service and consumers running
- ✅ Keycloak provisioned with test users and clients
- ✅ Access tokens generated for testing

For more details:
- Architecture: `docs/architecture/architecture.md`
- Checkout Demo: `checkout-demo/README.md`
- Troubleshooting: `docs/troubleshooting/connectivity.md`

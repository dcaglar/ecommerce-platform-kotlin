# 🚀 How to Start

Follow these steps to provision auth, get a token, test the API, and run load tests locally.

This is a short, practical “start order” for spinning up the stack on minikube, with one‑line notes on what each script does.

Prereqs (once):
- Docker Desktop (or Docker Engine) running
- minikube, kubectl, helm installed
- Troubleshooting connectivity? See docs/troubleshooting/connectivity.md

## ⚠️ The Golden Rules (Immutable Constraints)

When working on or extending this system, the following rules are absolute and must never be violated:

1. **The Separation of Powers (Consumers vs. Relays):**
   - **OutboxRelayJob**: The only component allowed to publish to Kafka. It reads the outbox table and routes messages. It must never update operational database state.
   - **Consumers (e.g., PspResultConsumer, CaptureCommandExecutor)**: The only components allowed to mutate the core database and ledger. They must never publish to Kafka directly. They communicate their results by appending new events to the Outbox.

2. **No Kafka Transactions:**
   - We do not use Kafka exactly-once semantics (EOS) or Kafka transactions. Instead, we rely purely on the Two-Stage Outbox Pattern to guarantee message durability and linearizability.

3. **Stateless Network Workers:**
   - Any worker interacting with the outside world (e.g., `CaptureCommandExecutor` calling the PSP) must do exactly one thing: execute the network call and append the result to the Outbox. It must not touch the ledger or alter core Payment domains.

---

## 0️⃣ Start the infrastructure and services

Pre-step: switch to the project root directory and make scripts executable
```bash
cd /path/to/ecommerce-platform-kotlin
chmod +x infra/scripts/*.sh
```

Recommended order

0) Nuke existing cluster (Optional but recommended)
- What: Completely destroys the existing minikube cluster to ensure a completely fresh state.
- Run:
```bash
infra/scripts/minikube-nuke-dev.sh
```

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

3) Monitoring stack (Prometheus + Grafana) (Optional)
- What: Installs kube-prometheus-stack into monitoring.
- Run:
```bash
infra/scripts/deploy-monitoring-stack.sh
```

4) Kafka  and Postgresql Exporter (Prometheus metrics for Kafka) (Optional)
- What: Exposes Kafka consumer lag, offsets, etc. for Prometheus.
- Run:
```bash
infra/scripts/deploy-kafka-exporter-local.sh
infra/scripts/deploy-postgresql-exporter-local.sh
```

4.5) Build Docker Images
- What: Builds the latest source code and updates the local Minikube docker registry so the pods pull the latest code.
- Run:
```bash
infra/scripts/build-and-push-payment-service-docker-repo.sh
infra/scripts/build-and-push-payment-edge-workers-docker-repo.sh
infra/scripts/build-and-push-payment-consumers-docker-repo.sh
```

5) Payment Edge Cell
- What: Deploys the complete Atomic Edge Cell (REST API, Local DB, and Local Forwarder) and sets up ingress. Writes infra/endpoints.json.
- Tip: For a LoadBalancer IP, run in a separate terminal:
```bash
tunnel.sh
```
- Then run:
```bash
infra/scripts/deploy-payment-edge-cell-local.sh
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


## 1️⃣ Create Realms,Authentication, and Roles for test user


1)  Provision Keycloak realm and clients
- What: Creates realm, role, and OIDC confidential clients; writes secrets to keycloak/output/secrets.txt.
- Tip: If you aren’t using port-forwarding, set KEYCLOAK_URL to your reachable Keycloak base.
- Run:
```bash
KEYCLOAK_URL=http://127.0.0.1:8080 ./keycloak/provision-keycloak.sh
```
2) Generate access tokens for different operations
- What: Get JWT tokens for different use cases; saves tokens under `keycloak/output/jwt`.
- Tip: KC_URL defaults to http://keycloak:8080; override if needed (e.g., when port-forwarding).
- Default validity is ~1 hour. Append a TTL (hours) as the final argument to keep a test token alive longer (e.g., `... 6` for 6 h).
- Reuse the saved token across requests until it expires; no need to regenerate for every call.

**Generate token For Payment Creation (Service Account with payment:write):**
```bash
get-token.sh
# Token saved to: keycloak/output/jwt/payment-service.token
# Claims saved to: keycloak/output/jwt/payment-service.claims.json

# Request a 6-hour token via CLI override:
get-token.sh http://127.0.0.1:8080 6
```

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

##   2️⃣ Test the 2 step authorization flow 


**Step 1: Create Payment Intent**

- Dynamic (reads host and base URL from infra/endpoints.json):
```bash
BASE_URL=$(jq -r .base_url infra/endpoints.json)
HOST=$(jq -r .host_header infra/endpoints.json)
IDEMPOTENCY_KEY="idem-$(date +%s)-$RANDOM"

echo "Using BASE_URL=$BASE_URL"
echo "Using Host header=$HOST"
echo "Using Idempotency-Key=$IDEMPOTENCY_KEY"

curl -i -X POST "$BASE_URL/api/v1/payments" \
  -H "Host: $HOST" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/payment-service.token)" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "orderId": "ORDER-1450",
    "buyerId": "BUYER-1450",
    "merchantAccountId": "MARKETPLACE-1",
    "processingModel": "MARKETPLACE",
    "totalAmount": { "quantity": 3000, "currency": "EUR" },
    "splits": [
      { "targetAccountType": "MARKETPLACE_SUB_SELLER", "targetEntityId": "SELLER-1-1", "amount": { "quantity": 1400, "currency": "EUR" }},
      { "targetAccountType": "MARKETPLACE_SUB_SELLER", "targetEntityId": "SELLER-1-2", "amount": { "quantity": 1400, "currency": "EUR" }},
      { "targetAccountType": "PLATFORM_COMMISSION_ESCROW", "targetEntityId": "MARKETPLACE-1", "amount": { "quantity": 200, "currency": "EUR" }}
    ]
  }'
```

**Expected Response (200 OK):**
```json
{
  "paymentIntentId": "pi_AcqzYyHCcAA",
  "clientSecret": "pi_AcqzYyHCcAA_secret_xyz123",
  "status": "CREATED"
}
```

**Pending Response (202 Accepted):**
If Stripe API call is still processing, you'll receive:
```json
{
  "paymentIntentId": "pi_AcqzYyHCcAA",
  "status": "CREATED_PENDING"
}
```

> **Note on Idempotency-Key**: The header is required for all payment intent creation requests. Use the same key for retries of the same payment request to ensure idempotent behavior. Generate a unique UUID for each new payment request.

**Step 2: Authorize Payment Intent**

> **Note**: In production, payment details are collected by Stripe Payment Element (browser → Stripe). The authorize endpoint doesn't require payment method details - it uses the stored PaymentIntent ID. For testing with curl, you can send an empty body or omit paymentMethod:

```bash
# Step 2: Authorize the payment intent
curl -i -X POST "$BASE_URL/api/v1/payments/pi_Ap60WDQCAAA/authorize" \
  -H "Host: $HOST" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ./keycloak/output/jwt/payment-service.token)" \
  -d '{}'
```

> **Production Flow**: In the actual checkout flow, Stripe Payment Element collects payment details client-side and attaches the payment method to the PaymentIntent. The backend then confirms the payment using the stored PaymentIntent ID without receiving any card data.

### Option B: Using Checkout Demo Page (Interactive UI)

A developer-friendly web interface for testing the complete end-to-end payment flow with Stripe Payment Element.

**Prerequisites:**
- Node.js 18+ installed
- Steps 1-9 completed (infrastructure, Keycloak provisioning)
- Stripe account (for Stripe publishable key)

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

3. Configure Stripe publishable key:
```bash
# Add your Stripe publishable key to .env file
echo "VITE_STRIPE_PUBLISHABLE_KEY=pk_test_your_key_here" >> checkout-demo/.env
```

4. Start the demo:
```bash
npm run dev
```

This starts both:
- Frontend server at `http://localhost:3000` (Vite)
- Backend proxy server at `http://localhost:3001` (simulates order-service/checkout-service)

The app will automatically open at `http://localhost:3000`

**Usage:**

The payment flow follows the complete end-to-end Stripe Payment Element flow:

1. **Fill Order Details:**
    - Order ID (e.g., `ORDER-TEST-001`)
    - Buyer ID (e.g., `BUYER-123`)
    - Total amount (in smallest currency unit, e.g., cents)
    - Select currency
    - Add one or more payment splits with target entity IDs and amounts
    - Click "Proceed to Checkout"

2. **Payment Creation:**
    - Frontend calls your backend to create payment
    - Backend creates payment record and calls Stripe to create PaymentIntent
    - Backend returns `paymentIntentId` and `clientSecret`
    - If Stripe call is pending (202), frontend polls for client secret

3. **Payment Details Collection (Stripe Payment Element):**
    - Stripe Payment Element is initialized with `clientSecret`
    - Shopper enters card details in Payment Element
    - **Card data goes directly to Stripe** (never touches your servers)
    - Click "Pay Now" to submit payment details to Stripe

4. **Payment Authorization:**
    - Frontend calls authorize endpoint with payment ID only
    - **No payment details are sent** - backend uses stored PaymentIntent ID
    - Backend confirms payment with Stripe
    - Frontend displays success/error result

The backend proxy automatically handles token acquisition and payment-service calls - no manual token management needed!

**Architecture:**

The checkout demo uses a production-like flow with Stripe Payment Element:
- **Frontend** (React) → calls **Backend Proxy** (Node.js/Express) to create payment
- **Backend Proxy** → gets token from Keycloak (server-to-server)
- **Backend Proxy** → calls payment-service with token (server-to-server)
- **Frontend** ← receives `paymentIntentId` and `clientSecret`
- **Frontend** → initializes Stripe Payment Element with `clientSecret`
- **Shopper** → enters card details in Payment Element
- **Payment Element** → sends card data directly to Stripe (browser → Stripe, never touches your servers)
- **Frontend** → calls **Backend Proxy** to authorize payment (no payment details sent)
- **Backend Proxy** → calls payment-service authorize endpoint
- **Backend** → confirms payment with Stripe using stored PaymentIntent ID
- **Frontend** ← receives authorization result

**Data Flow:**
- **Card data**: Browser → Stripe (never touches your servers)
- **Order data**: Browser → Your Backend → Database
- **Payment control**: Browser → Your Backend → Stripe → Your Backend → Browser

> 💡 **Note**: The backend proxy simulates a production backend (order-service/checkout-service) and needs CORS enabled because the browser calls it directly (browser → proxy is cross-origin).




12) Elasticsearch/Logstash/Kibana stack (OPTIONAL)
- What: Installs ELK stack for log aggregation and searching.
- Run:
```bash
infra/scripts/deploy-observability-stack.sh
```


**Troubleshooting:**

- **"Client secret not found"**: Run `npm run setup-env` after provisioning Keycloak
- **"Cannot reach Keycloak"**: Ensure Keycloak port-forwarding is active: `kubectl port-forward -n payment svc/keycloak 8080:8080`
- **"Stripe publishable key not configured"**: Add `VITE_STRIPE_PUBLISHABLE_KEY` to `.env` file
- **Payment request errors**: Check proxy console for detailed error messages (it logs token and payment-service call errors)
- **Network errors**: Verify payment service is running and `infra/endpoints.json` is correct
- **Payment Element not loading**: Check browser console for Stripe.js errors and verify publishable key is correct
- **Payment pending (202 status)**: This is normal - the frontend will automatically poll for client secret when payment creation is pending

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
  "accountCode": "MARKETPLACE_SUB_SELLER.SELLER-1-1.EUR",
  "sellerId": "SELLER-1-1"
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
- **Type Inference Fixed**: Resolved MockK type inference issues in `OutboxRelayJobTest.kt` with explicit type hints and Jackson JSR310 module configuration

For deep architecture or flow diagrams, see:
- [`docs/architecture.md`](./architecture/architecture.md)
- [`README.md`](../README.md)

---
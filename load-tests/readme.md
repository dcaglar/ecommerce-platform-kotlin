# Payment Platform Load Testing Guide

This directory contains the performance and load testing scripts for the payment platform, utilizing [k6](https://k6.io/). 

The primary test script is `k6-payment-flow.js`, which simulates a complete end-to-end user payment journey:
1. Creating a multi-seller Payment Intent.
2. A realistic human pacing delay (1.5 seconds).
3. Authorizing the payment.

## How to Run

Tests are executed using the `k6 run` command. You can choose different workload profiles by passing the `PROFILE` environment variable.

```bash
# Example: Running the stress test profile
k6 run -e PROFILE=stress k6-payment-flow.js
```

If no profile is specified, it defaults to the `smoke` profile.

---

## Workload Scenarios (Profiles)

The script comes pre-configured with 6 distinct workload profiles tailored for different performance testing goals:

### 1. Smoke Test (`smoke`)
* **Execution:** 1 Virtual User (VU) for 1 minute.
* **Purpose:** Minimal load designed purely for validation. Use this to quickly verify that the APIs are up, the authentication token is valid, and the script logic works without throwing errors.

### 2. Average Load Test (`average`)
* **Execution:** Ramps up to 20 VUs over 2m, holds for 5m, then ramps down.
* **Purpose:** Simulates standard, day-to-day production traffic. Use this to ensure the system behaves normally under expected operational conditions and meets baseline SLAs.

### 3. Stress Test (`stress`)
* **Execution:** Ramps up to 100 VUs over 3m, holds for 10m, then ramps down.
* **Purpose:** Pushes the system past its average limits. Use this to observe how the database connection pools (like the web-pool and outbox-pool), Kafka consumers, and memory limits handle sustained, high-pressure traffic.

### 4. Soak Test (`soak`)
* **Execution:** Constant 30 VUs for 1 full hour.
* **Purpose:** A long-running endurance test. Use this to identify slow-burning issues that don't appear in short tests, such as memory leaks, database connection leaks, or unacknowledged Kafka message pile-ups.

### 5. Spike Test (`spike`)
* **Execution:** Baseline of 5 VUs, suddenly spiking to 400 VUs instantly, holding for 3m, then dropping.
* **Purpose:** Simulates a sudden, massive burst of traffic (e.g., a flash sale or viral event). Use this to test the system's buffering capabilities, caching resilience, and how quickly horizontal pod autoscalers (HPA) can react.

### 6. Breakpoint Test (`breakpoint`)
* **Execution:** Slowly ramps up to 1000 VUs over 15 minutes.
* **Purpose:** Designed to push the system until it completely fails. Use this to identify the absolute maximum capacity of the current infrastructure and pinpoint exactly which component breaks first (e.g., database connections maxing out, CPU throttling, etc.).

---

## Service Level Agreements (SLAs)

Regardless of the profile chosen, the test automatically enforces strict performance SLAs. If the platform fails to meet these thresholds, k6 will mark the test run as **FAILED**:

1. **Error Rate:** `< 5%` of all HTTP requests can fail (`rate <= 0.05`).
2. **Latency:** `95%` of all requests must complete in under `1000ms` (`p(95) <= 1000`).

## Prerequisites
Before running the tests, ensure that:
1. `keycloak/output/jwt/payment-service.token` contains a valid access token.
2. `infra/endpoints.json` contains the correct `base_url` and `host_header` mapping for the test environment.

---

## 🛠️ Deep Dive: Test Data & Workflow Mechanics

### Payload Generation & Idempotency
To ensure the backend processes every virtual user's request as a unique transaction (rather than rejecting it as a duplicate), the script dynamically generates data for every iteration:
* **Idempotency Keys:** Every request uses a highly randomized key (`IDEM-C-12345678` for Creation, `IDEM-A-12345678` for Authorization). This prevents the backend Idempotency interceptor from returning cached responses.
* **Order IDs:** A random order ID is generated per basket (`ORD-12345678`).
* **Sellers & Amounts:** The test hardcodes a standard **multi-seller basket** payload. It simulates a total order of 2900 EUR split evenly across two specific sellers (`SELLER-111` and `SELLER-222`), each receiving 1450 EUR. 

### The 2-Step Endpoint Journey
The script does not just hammer a single endpoint; it simulates realistic stateful choreography between two separate endpoints:

1. **Step 1: Create Intent (`POST /api/v1/payments`)**
   * Sends the multi-seller basket payload.
   * K6 explicitly checks that the backend returns `201 Created`.
   * The script parses the returned JSON to extract the `paymentIntentId`.
2. **Human Pacing (`sleep`)**
   * The script intentionally pauses for `1.5` seconds to simulate the shopper entering their card details on the frontend.
3. **Step 2: Authorize (`POST /api/v1/payments/{paymentIntentId}/authorize`)**
   * Only executes if Step 1 succeeded.
   * Passes the extracted `paymentIntentId` directly into the URL path.
   * K6 checks that the backend returns a successful `200` or `201`.

### How Metrics are Measured
Because the script hits two different endpoints, it's important to understand how K6 tracks the Service Level Agreements (SLAs):
* **Global Aggregation:** By default, K6 aggregates metrics (`http_req_duration` and `http_req_failed`) globally. 
* This means the SLA constraint `p(95) <= 1000` requires that 95% of **all combined requests** (both the fast Intent Creations and the potentially slower PSP Authorizations) must complete in under 1 second. 
* If you want to measure the endpoints separately in the future, you can tag the HTTP requests in the script (e.g., `tags: { name: 'create_intent' }`) and define SLAs per tag.

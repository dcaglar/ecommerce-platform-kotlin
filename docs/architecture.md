# ecommerce-platform-kotlin · Architecture Overview

## 1. Purpose

This document provides a high-level overview of the ecommerce-platform-kotlin backend and aligns it with the clarified Merchant-of-Record scope. The platform acts as the Merchant of Record for multi-seller checkouts: it owns shopper payment authorization, expands orders into seller obligations, records every financial movement in a double-entry ledger, and prepares the data needed for settlement and payouts. The sections below explain how the system structures domain boundaries (Payment, PaymentOrder, Ledger), coordinates synchronous PSP authorization with asynchronous capture and settlement flows, and ensures reliability through transactional outbox, idempotent processing, and event-driven orchestration across modular Kotlin services.
---

## 2. Architectural Principles

| Principle                                 | Description                                                                                     |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------- |
| **Domain-Driven Design (DDD)**            | Core business logic modeled as aggregates and value objects within bounded contexts.            |
| **Hexagonal Architecture**                | Clear separation between domain logic, application services, and infrastructure adapters.       |
| **Event-Driven Processing**               | Kafka used as the backbone for asynchronous, decoupled communication between services.          |
| **Outbox Pattern**                        | Reliable event publication via transactional outbox tables to prevent message loss.             |
| **Idempotency & Exactly-Once Processing** | Combined use of Kafka transactions and database constraints ensures no duplicate state changes. |
| **Resilience by Design**                  | Retry and backoff strategies, DLQs, and fault isolation at flow boundaries.                     |
| **Observability**                         | Structured JSON logging, Prometheus metrics, and Grafana dashboards for visibility.             |

---

## 3. System Overview

The platform manages payments for multi-seller checkouts, processes them asynchronously, records financial movements in a double-entry ledger, and will later aggregate balances per merchant.

### 3.1 Merchant-of-Record Scope Snapshot

- **Lifecycle Ownership:** One shopper authorization per order at the PSP, automatic expansion into seller-level PaymentOrders, capture orchestration, and ledger postings for every state change.
- **Accounting Guarantees:** Double-entry ledger enforced by domain invariants and database constraints; every posting is auditable and traceable back to the originating payment order.
- **Operational Guardrails:** Exactly-once processing (Kafka transactions + outbox), idempotent command handling, and balance surfaces (real-time vs strong consistency) keep financial data trustworthy.
- **In-Scope Today:** PSP authorization against a mocked connector, PaymentOrder capture pipeline, ledger recording, Redis-backed balance deltas, and Keycloak-secured read APIs.
- **Not Yet Implemented (Roadmap):** Real PSP integrations, automated settlements/payout instructions, refunds/cancellations, and external webhooks — the ledger and event model already anticipate these additions.

```mermaid

```mermaid
flowchart LR
    subgraph API[Payment Service]
        REST[POST /payments]
        OUTBOX[Outbox Dispatcher]
    end

    subgraph Messaging[Kafka Topics]
        CREATED[payment_order_created]
        PSP_REQ[payment_order_psp_call_requested]
        RESULT[payment_order_psp_result_updated]
        FINAL[payment_order_finalized]
        LEDGER_REQ[ledger_record_request_queue]
        LEDGER_REC[ledger_entries_recorded]
    end

    subgraph Processing[Payment Consumers]
        ENQ[PaymentOrderEnqueuer]
        EXEC[PaymentOrderPspCallExecutor]
        APPLY[PaymentOrderPspResultApplier]
        LEDGER_DISP[LedgerRecordingRequestDispatcher]
        LEDGER_CONS[LedgerRecordingConsumer]
        BAL[AccountBalanceConsumer (planned)]
    end

    subgraph Storage[State Stores]
        DB[(PostgreSQL)]
        REDIS[(Redis)]
    end

    REST -->|Persist Payment + Orders| DB
    REST -->|202 Accepted| Client
    OUTBOX -->|Publish| CREATED

    CREATED --> ENQ --> PSP_REQ
    PSP_REQ --> EXEC --> RESULT
    RESULT --> APPLY --> FINAL
    FINAL --> LEDGER_DISP --> LEDGER_REQ --> LEDGER_CONS --> LEDGER_REC --> BAL

    ENQ --> DB
    EXEC -->|PSP Call| PSP[External PSP]
    APPLY --> DB
    APPLY --> REDIS
    LEDGER_CONS --> DB
```

---

## 4. Module Breakdown

### 4.1 payment-service

* REST API for creating payments and querying balances.
* Writes `Payment` and `PaymentOrder` aggregates to PostgreSQL.
* Inserts corresponding `OutboxEvent` rows atomically.
* Returns `202 Accepted` immediately (no PSP calls in request thread).
* Outbox dispatcher asynchronously publishes domain events to Kafka.
* Payment aggregate enforces lifecycle transitions (`AUTHORIZED → PARTIALLY_CAPTURED → CAPTURED_FINAL`) and is kept in sync via the planned `PaymentCaptureAggregator` consumer that reacts to `PaymentOrder` success events.
* **Balance Endpoints** with three authentication scenarios:
  * **Case 1**: Seller user via customer-area frontend (`GET /api/v1/sellers/me/balance`) - requires `SELLER` role, OIDC Authorization Code flow
  * **Case 2**: Finance/Admin user via backoffice (`GET /api/v1/sellers/{sellerId}/balance`) - requires `FINANCE`/`ADMIN` role, OIDC Authorization Code flow
  * **Case 3**: Merchant API M2M (`GET /api/v1/sellers/me/balance`) - requires `SELLER_API` role, Client Credentials flow
* **Security**: OAuth2 Resource Server with JWT validation (Keycloak), role-based access control

### 4.2 payment-consumers

* Dedicated asynchronous processing workers consuming from Kafka.
* Organized into independent flows:

    * **PSP Flow:** enqueuer → executor → result applier.
    * **Ledger Flow:** dispatcher → ledger consumer.
    * **Future Balance Flow:** will consume ledger entries for merchant-level aggregation.
* Each flow has its own topics and consumer groups for isolated scaling.

### 4.3 payment-domain

* Defines aggregates: `Payment`, `PaymentOrder`, and `JournalEntry`.
* Implements business invariants and state transitions through factory-enforced object creation (e.g., `Payment.authorize()`, `Payment.capture()` guarding lifecycle, `PaymentOrder.markCaptured()` enforcing terminal transitions).
* Core domain classes (`Account`, `Amount`, `JournalEntry`, `Posting`) use private constructors with validated factory methods to enforce invariants.
* Domain events include `PaymentAuthorized`, `PaymentOrderCreated`, `PaymentOrderSucceeded`, and `PaymentCaptured`, providing traceability across asynchronous flows.
* Contains value objects (`PaymentId`, `SellerId`, `Amount`, `Currency`, etc.) and factory helpers such as `Account.create()`, `Amount.of()`, `Posting.Debit.create()`, `Posting.Credit.create()`.

### 4.4 payment-application

* Implements use cases and orchestration logic.
* Services include:

    * `CreatePaymentService`
    * `ProcessPaymentService`
    * `RequestLedgerRecordingService`
    * `RecordLedgerEntriesService`

### 4.5 payment-infrastructure

* Shared infrastructure auto-configuration for Redis, Kafka, and MyBatis.
* Implements persistence adapters and ledger mappers.
* Provides transactional utilities like `KafkaTxExecutor` for atomic consume-produce-commit behavior.

### 4.6 common-test

* Shared test utilities module providing test helpers across all payment modules.
* Exposes test classes via test-jar artifact (Maven test-jar).
* Contains test helpers like `LedgerEntriesRecordedTestHelper` for generating test events.
* Used by both `payment-application` and `payment-consumers` test suites for consistent test data generation.

---

## 5. Data Flow Summary

1. **Payment creation:**

    * `POST /payments` → synchronous transaction persists the `Payment` aggregate, executes PSP authorization, and stores an `OutboxEvent<PaymentRequestDTO>` for downstream expansion (PaymentOrders are created later).
    * Returns `202 Accepted`.

2. **Outbox dispatch (recursive):**

    * `OutboxDispatcherJob` polls unsent events in small batches.
    * For `PaymentRequestDTO` rows it:
        * Hydrates the envelope, expands the payload into seller-level `PaymentOrders`, and inserts them.
        * Persists nested `OutboxEvent<PaymentOrderCreated>` entries (one per seller) with parent/trace metadata.
        * Publishes `PaymentAuthorized` for downstream listeners and marks the original row as sent.
    * For the derived `PaymentOrderCreated` rows it publishes the PSP orchestration event and marks each row as sent.

3. **Authorization ledger posting:**

    * `PaymentAuthorizedConsumer` consumes `PaymentAuthorized` events and records the `AUTH_HOLD` journal entry so the ledger mirrors the authorization lifecycle.

4. **PSP processing:**

    * `PaymentOrderEnqueuer` consumes `PaymentOrderCreated`, republishes `PaymentOrderPspCallRequested`.
    * `PaymentOrderPspCallExecutor` calls PSP with timeout, publishes `PaymentOrderPspResultUpdated`.
    * `PaymentOrderPspResultApplier` applies results to DB and schedules retries via Redis if transient.
    * `PaymentCaptureAggregator` (planned) reacts to successful payment orders to advance the parent `Payment` aggregate and emit `PaymentCaptured` when fully complete.

5. **Ledger recording:**

    * `PaymentOrderFinalized` events trigger `LedgerRecordingRequestDispatcher`.
    * Publishes `LedgerRecordingCommand` (partitioned by sellerId) to `ledger_record_request_queue_topic`.
    * `LedgerRecordingConsumer` writes double-entry records to `journal_entries` and `postings` tables.
    * Publishes confirmation `LedgerEntriesRecorded`.

6. **Balance aggregation:**

    * `AccountBalanceConsumer` processes `LedgerEntriesRecorded` events in batches (100-500 events, partitioned by `sellerId`, concurrency=4).
    * **Two-Tier Storage Architecture**:
        * **Hot Layer (Redis)**: Accumulates balance deltas with TTL (5 minutes) and watermarking. Deltas are atomically updated via Lua scripts (HINCRBY + HSET watermark + SADD dirty set).
        * **Cold Layer (PostgreSQL)**: Stores durable snapshots with `last_applied_entry_id` watermark. UPSERT includes watermark guard (`WHERE last_applied_entry_id < EXCLUDED.last_applied_entry_id`) preventing concurrent overwrites.
    * **Idempotency (Two-Level Protection)**:
        * **Consumer-Level**: Filters postings by watermark (`ledgerEntryId > lastAppliedEntryId`) before computing deltas, preventing duplicate accumulation.
        * **Database-Level**: Watermark guard ensures snapshots only advance monotonically, protecting against concurrent job executions.
    * **Batch Processing**: Consumer extracts postings from ledger entries, batch loads snapshots from PostgreSQL (`findByAccountCodes`), computes signed amounts per account, filters by watermark, and updates Redis deltas atomically.
    * **Scheduled Merge Job**: `AccountBalanceSnapshotJob` (runs every 1 minute, configurable) reads dirty accounts from Redis, atomically gets and resets deltas (`getAndResetDeltaWithWatermark`), merges to snapshots, and saves with watermark guard.
    * **Read Patterns**:
        * **Real-Time Read**: `getRealTimeBalance()` returns `snapshot + redis.delta` (fast, non-consuming, eventual consistency).
        * **Strong Consistency Read**: `getStrongBalance()` atomically resets delta, merges to snapshot, saves, and returns (slower, consuming, guaranteed accuracy).

---

## 6. Reliability and Idempotency

* **Kafka Transactions:** Offset commit and downstream event publication occur atomically.
* **Database Idempotency:** `ON CONFLICT DO NOTHING` for ledger inserts; idempotent `UPDATE ... RETURNING` for payment orders.
* **Retry Queue:** Redis ZSet ensures atomic scheduling and replay prevention.
* **Outbox Pattern:** Prevents message loss between DB and Kafka.

---

## 7. Observability

* **Metrics:** Custom Micrometer meters for PSP latency, retry throughput, outbox backlog, and consumer lag.
* **Logging:** Structured JSON logs with `traceId`, `parentEventId`, and `aggregateId` for causal tracing.
* **Dashboards:** Grafana visualizations for PSP success rates, ledger throughput, and retry queue size.

---

## 8. Scalability & Deployment

* Kubernetes deployment via Helm charts (`payment-service`, `payment-consumers`).
* Kafka partitioning:

    * PSP topics keyed by `paymentOrderId`.
    * Ledger topics keyed by `sellerId`.
* Independent consumer groups for each flow.
* Horizontal Pod Autoscaler configured on **consumer lag**.

---

## 9. Security & Authentication

The platform implements OAuth2/JWT-based authentication with Keycloak, supporting three real-world authentication scenarios:

| Case | Endpoint | Role | Client | Grant Type | Use Case |
|------|----------|------|--------|------------|----------|
| 1 | `GET /api/v1/sellers/me/balance` | `SELLER` | `customer-area-frontend` | OIDC Authorization Code / Direct Access Grants | Seller logs into customer-area web app |
| 2 | `GET /api/v1/sellers/{sellerId}/balance` | `FINANCE`/`ADMIN` | `backoffice-ui` / `finance-service` | OIDC Authorization Code / Client Credentials | Finance/admin staff in backoffice app |
| 3 | `GET /api/v1/sellers/me/balance` | `SELLER_API` | `merchant-api-{SELLER_ID}` | Client Credentials | Merchant's ERP/OMS system (M2M) |

**Implementation Details:**
- **Payment Endpoint**: `POST /api/v1/payments` requires `payment:write` authority (service-to-service via Client Credentials)
- **Balance Endpoints**: Role-based access control with `seller_id` claim extraction from JWT tokens
- **Keycloak Integration**: Clients configured with appropriate OIDC flows (Authorization Code for production frontends, Direct Access Grants for testing, Client Credentials for M2M)
- **Token Validation**: Spring Security OAuth2 Resource Server validates JWT issuer and extracts roles/claims

---

## 10. Future Extensions

| Area                    | Description                                                                     |
| ----------------------- | ------------------------------------------------------------------------------- |
| **Refunds & Captures**  | Introduce reversal and partial capture flows with corresponding ledger entries. |
| **External PSPs**       | Replace mock PSP with real connectors (Adyen, Stripe, etc.).                    |
| **Settlement Batching** | Implement merchant payout aggregation.                                          |
| **Webhooks**            | Expose payment and ledger events to merchants in real time.                     |

---

## 11. Summary

The `ecommerce-platform-kotlin` backend demonstrates a production-grade event-driven architecture applying DDD, SOLID, and cloud-native principles. The system decouples user-facing APIs from external dependencies, achieves exactly-once delivery across asynchronous flows, and enforces domain invariants through factory-enforced object creation. Core domain classes (`Account`, `Amount`, `JournalEntry`, `Posting`) use private constructors with validated factory methods, ensuring all objects are created in valid states and preventing invalid domain modeling. The platform provides a foundation for extending into full financial operations — including ledger reconciliation, balance tracking, and settlements.

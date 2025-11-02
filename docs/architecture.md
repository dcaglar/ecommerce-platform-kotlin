# ecommerce-platform-kotlin · Architecture Overview

## 1. Purpose

This document summarizes the high-level architecture of the `ecommerce-platform-kotlin` backend. It provides an overview for engineers and contributors to understand how the system is structured, how data flows through it, and how it achieves reliability and scalability through modular design and event-driven principles.

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

* REST API for creating payments.
* Writes `Payment` and `PaymentOrder` aggregates to PostgreSQL.
* Inserts corresponding `OutboxEvent` rows atomically.
* Returns `202 Accepted` immediately (no PSP calls in request thread).
* Outbox dispatcher asynchronously publishes domain events to Kafka.

### 4.2 payment-consumers

* Dedicated asynchronous processing workers consuming from Kafka.
* Organized into independent flows:

    * **PSP Flow:** enqueuer → executor → result applier.
    * **Ledger Flow:** dispatcher → ledger consumer.
    * **Future Balance Flow:** will consume ledger entries for merchant-level aggregation.
* Each flow has its own topics and consumer groups for isolated scaling.

### 4.3 payment-domain

* Defines aggregates: `Payment`, `PaymentOrder`, and `JournalEntry`.
* Implements business invariants and state transitions through factory-enforced object creation.
* Core domain classes (`Account`, `Amount`, `JournalEntry`, `Posting`) use private constructors with validated factory methods to enforce invariants.
* Contains value objects (`PaymentId`, `SellerId`, `Amount`, `Currency`, etc.) and domain events.
* Factory methods: `Account.create()`, `Amount.of()`, `Posting.Debit.create()`, `Posting.Credit.create()`.

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

    * `POST /payments` → DB transaction saves `Payment`, `PaymentOrders`, and `OutboxEvent`.
    * Returns `202 Accepted`.

2. **Outbox dispatch:**

    * Scheduled dispatcher reads unsent events, publishes `PaymentOrderCreated` to Kafka, marks as sent.

3. **PSP processing:**

    * `PaymentOrderEnqueuer` consumes `PaymentOrderCreated`, republishes `PaymentOrderPspCallRequested`.
    * `PaymentOrderPspCallExecutor` calls PSP with timeout, publishes `PaymentOrderPspResultUpdated`.
    * `PaymentOrderPspResultApplier` applies results to DB and schedules retries via Redis if transient.

4. **Ledger recording:**

    * `PaymentOrderFinalized` events trigger `LedgerRecordingRequestDispatcher`.
    * Publishes `LedgerRecordingCommand` (partitioned by sellerId) to `ledger_record_request_queue_topic`.
    * `LedgerRecordingConsumer` writes double-entry records to `journal_entries` and `postings` tables.
    * Publishes confirmation `LedgerEntriesRecorded`.

5. **Balance aggregation (planned):**

    * `AccountBalanceConsumer` will process `LedgerEntriesRecorded` sequentially per merchant partition.
    * Updates `account_balances` table and Redis cache for fast balance queries.

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

## 9. Future Extensions

| Area                    | Description                                                                     |
| ----------------------- | ------------------------------------------------------------------------------- |
| **Account Balances**    | Aggregate per-merchant balances from ledger entries.                            |
| **Refunds & Captures**  | Introduce reversal and partial capture flows with corresponding ledger entries. |
| **External PSPs**       | Replace mock PSP with real connectors (Adyen, Stripe, etc.).                    |
| **Settlement Batching** | Implement merchant payout aggregation.                                          |
| **Webhooks**            | Expose payment and ledger events to merchants in real time.                     |

---

## 10. Summary

The `ecommerce-platform-kotlin` backend demonstrates a production-grade event-driven architecture applying DDD, SOLID, and cloud-native principles. The system decouples user-facing APIs from external dependencies, achieves exactly-once delivery across asynchronous flows, and enforces domain invariants through factory-enforced object creation. Core domain classes (`Account`, `Amount`, `JournalEntry`, `Posting`) use private constructors with validated factory methods, ensuring all objects are created in valid states and preventing invalid domain modeling. The platform provides a foundation for extending into full financial operations — including ledger reconciliation, balance tracking, and settlements.

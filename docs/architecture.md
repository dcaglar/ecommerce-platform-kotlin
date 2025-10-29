# 💳 Payment Platform – System Architecture

---

## 1. Overview

This document describes the architecture of the **Payment Platform**, a modular Kotlin + Spring Boot system that models an event-driven, fault-tolerant **Payment and Ledger processing pipeline** inspired by real-world PSPs (like Adyen or Stripe).

The platform demonstrates **Domain-Driven Design (DDD)**, **Hexagonal Architecture**, and **exactly-once event processing** using Kafka transactions, PostgreSQL, and Redis.

**Key Terminology:**
- **PSP (Payment Service Provider):** External payment gateway (simulated in this platform)
- **DDD (Domain-Driven Design):** Software development approach focusing on business domains
- **Exactly-Once Semantics:** Guarantee that events are processed exactly one time (via Kafka transactions + idempotent handlers)
- **Outbox Pattern:** Reliable event publishing by storing events in DB within same transaction as business data
- **Double-Entry Accounting:** Bookkeeping method where every transaction affects two accounts (debit + credit balance)

---

## 2. Modules

| Module | Responsibility |
|--------|----------------|
| **payment-service** | Ingests payment requests via REST, persists state + outbox, and publishes domain events. |
| **payment-consumers** | Kafka-based worker application handling asynchronous processing (PSP calls, ledger recording, balance updates). |
| **payment-domain** | Core domain models, aggregates, events, and value objects. |
| **payment-application** | Use cases and orchestration logic (e.g., `RecordLedgerEntriesService`, `ProcessPaymentService`). |
| **payment-infrastructure** | Shared adapters (Kafka, DB, Redis, Liquibase migrations, metrics). |
| **common** | Shared DTOs, event envelopes, logging and tracing utilities. |

---

## 3. Functional Requirements

1. Users can initiate payments containing multiple sellers (order-split).
2. Each `PaymentOrder` is processed independently for PSP authorization/capture.
3. PSP responses drive domain events (`Succeeded`, `Failed`, `RetryRequested`).
4. Successful payments are journaled into a double-entry **ledger**.
5. Account balances are periodically derived from ledger entries.
6. All events are traceable end-to-end using `traceId`, `eventId`, and `parentEventId`.

---

## 4. Non-Functional Requirements

| Aspect | Requirement |
|--------|--------------|
| **Reliability** | Exactly-once DB ↔ Kafka delivery via outbox & transactions. |
| **Scalability** | Horizontal scaling of consumers per topic/partition. |
| **Observability** | Full ELK pipeline with traceable JSON logs (Filebeat → Logstash → Elasticsearch → Kibana). |
| **Monitoring** | Prometheus + Grafana dashboards for lag, throughput, PSP latency, and retry metrics. |
| **Idempotency** | DB-level unique constraints + updateReturningIdempotent pattern. |
| **Consistency** | Eventual consistency across ledger and balance projections. |
| **Security** | OAuth2 (Keycloak), role-based access, signed JWT validation. |

---

## 5. Core Entities

| Entity | Description |
|--------|--------------|
| **Payment** | Parent aggregate representing user payment intent. |
| **PaymentOrder** | Per-seller sub-transaction derived from a Payment. |
| **LedgerEntry** | Immutable journal entry with debit/credit postings. |
| **AccountBalance** | Derived projection (snapshot) of account-level balances. |
| **OutboxEvent** | Pending domain event stored transactionally with business data. |

---

## 6. High-Level Design

### Sequence

**A. Initial Payment Creation**
1. **PaymentController**  
   Receives REST request → persists `Payment`, `PaymentOrders`, and `OutboxEvent` in one DB transaction.

2. **OutboxDispatcherJob**  
   Periodically polls unsent outbox rows and publishes wrapped `PaymentOrderCreated` events to `payment_order_created_topic`.

**B. PSP Call Orchestration**
3. **PaymentOrderEnqueuer**  
   Consumes from `payment_order_created_topic` → creates and publishes `PaymentOrderPspCallRequested` (attempt=0) to `payment_order_psp_call_requested_topic`.

**C. PSP Execution & Result Handling**
4. **PaymentOrderPspCallExecutor**
   - Consumes from `payment_order_psp_call_requested_topic`
   - Invokes the PSP simulator asynchronously (with 500ms timeout)
   - Publishes PSP response (`PaymentOrderPspResultUpdated`) to `payment_order_psp_result_updated_topic`

5. **PaymentOrderPspResultApplierConsumer**
   - Consumes from `payment_order_psp_result_updated_topic`
   - Persists PSP result via `ProcessPaymentService`
   - Handles retry logic (Redis-backed with exponential backoff)
   - Emits domain events such as:
      - `PaymentOrderFinalized` to `payment_order_finalized_topic`
      - Triggers ledger recording flow (if successful)

**D. Ledger Recording**
6. **LedgerRecordingRequestDispatcher**
   - Consumes from `payment_order_finalized_topic`
   - Transforms payment events → `LedgerRecordingCommand`
   - Publishes to `ledger_record_request_queue_topic`

7. **LedgerRecordingConsumer**
   - Consumes from `ledger_record_request_queue_topic`
   - Creates **double-entry journals** atomically (e.g., PSP_RECEIVABLE → MERCHANT_PAYABLE)
   - Persists via `RecordLedgerEntriesService`
   - Publishes `LedgerEntriesRecorded` to `ledger_entries_recorded_topic`

**E. Account Balance Aggregation** *(Planned - Not Yet Implemented)*
8. **AccountBalanceConsumer**
   - Will consume from `ledger_entries_recorded_topic`
   - Will aggregate debits/credits per account into the `account_balances` table
   - Will optionally update a **real-time balance cache** for in-flight adjustments

9. **AccountBalanceCache (optional)** *(Planned)*
   - Will hold uncommitted deltas (recent ledger updates) in Redis or in-memory buffer
   - Will enable low-latency "real-time balance" API by merging DB snapshot + cache
   - **Note:** Current implementation records ledger entries but does not yet maintain account balance projections

---

## 7. Data Flow Summary

```
HTTP → DB (Payment + Outbox)
↓
OutboxDispatcher → Kafka (payment_order_created_topic)
↓
PaymentOrderEnqueuer → payment_order_psp_call_requested_topic
↓
PaymentOrderPspCallExecutor → payment_order_psp_result_updated_topic
↓
PaymentOrderPspResultApplier → payment_order_finalized_topic
↓
LedgerRecordingRequestDispatcher → ledger_record_request_queue_topic
↓
LedgerRecordingConsumer → ledger_entries_recorded_topic
↓
AccountBalanceConsumer (planned) → account_balances table
```

**Key Topics:**
- `payment_order_created_topic` - Initial payment requests
- `payment_order_psp_call_requested_topic` - Work queue for PSP interactions
- `payment_order_psp_result_updated_topic` - PSP response results
- `payment_order_finalized_topic` - Terminal payment states (success/failure)
- `ledger_record_request_queue_topic` - Commands to record ledger entries
- `ledger_entries_recorded_topic` - Confirmation of persisted journals
- Each topic has a corresponding `.DLQ` topic for dead letter handling


---

## 8. Key Design Patterns

- **Outbox Pattern:** Reliable DB → Kafka publishing.
- **Event Choreography:** Each consumer triggers the next domain step.
- **Double-Entry Accounting:** Enforced balance between debits and credits.
- **CQRS Projection:** AccountBalance table acts as a derived read model.
- **Idempotency:** Ledger insertions are deduplicated via unique journalId.
- **Observability:** MDC-based trace context across all event hops.

---

## 9. Resilience & Retry Strategy

### 9.1 Partition-Aligned Retry Handling
- All retry events (`PAYMENT_ORDER_PSP_CALL_REQUESTED`) are **republished to the same Kafka partition** as the original message.
- Guarantees ordering and avoids concurrent reprocessing of the same payment order.

### 9.2 Exponential Backoff with Equal Jitter
- Retry delays are computed via:
  ```
  delay = min(
    random_between(
      base_delay * 2^(attempt - 1) / 2,
      base_delay * 2^(attempt - 1)
    ),
    max_delay
  )
  ```
  Where `base_delay = 2,000ms` and `max_delay = 60,000ms` (60 seconds).

- **Example delays:**
  - Attempt 1: 1,000 - 2,000ms
  - Attempt 2: 2,000 - 4,000ms  
  - Attempt 3: 4,000 - 8,000ms
  - Attempt 4: 8,000 - 16,000ms
  - Attempt 5: 16,000 - 60,000ms (capped at max_delay)

- Ensures retries spread evenly to prevent thundering herds by randomizing within the exponential window.

### 9.3 Redis-Based Retry Queue
- Transient failures queued in **Redis ZSET** keyed by `retryAt`.
- `RetryDispatcherScheduler` periodically pops due items and republishes via transactional Kafka send.

### 9.4 Dead Letter Queue (DLQ)
- After **MAX_RETRIES = 5**, unrecoverable messages go to the topic-specific DLQ (format: `${topic}.DLQ`).
  - Example: `payment_order_psp_call_requested_topic.DLQ`
- DLQs are automatically created for all topics (via `createDlq: true` in configuration).
- **Monitoring:**
  - Grafana dashboards track DLQ message counts per topic
  - Alert threshold: > 100 messages in any DLQ over 5 minutes
  - Daily reconciliation scripts verify DLQ contents against source events
- **Handling:**
  - Manual inspection via Kafka console consumers (see `docs/cheatsheet/kafka.md`)
  - Root cause analysis using traceId from DLQ messages
  - Manual replay possible after fixing underlying issue (e.g., DB connectivity, PSP API)

---

## 10. Monitoring & Observability

### 10.1 Monitoring Stack (📈 Prometheus + Grafana)
- Deployed via Helm chart (`infra/scripts/deploy-monitoring-stack.sh`).
- **Prometheus Operator** scrapes:
- Kafka consumer lag
- PSP call latency
- Retry queue size
- DB connection pool usage
- **Grafana Dashboards**:
- *Payment Processing Overview*
- *Consumer Lag*
- *Outbox Throughput*
- *PSP Latency & Error Rates*
- HPA autoscaling based on consumer lag, not CPU.

### 10.2 Observability Stack (🔎 ELK: Filebeat + Logstash + Elasticsearch + Kibana)
- Structured JSON logs emitted via Logback + MDC context.
- Forwarded by **Filebeat** to **Logstash**, then indexed in **Elasticsearch**.
- **Kibana** provides searchable visualization:
- Filter by `traceId`, `eventId`, or `paymentOrderId`.
- Reconstruct end-to-end payment flow:
  ```kibana
  traceId:"abc123" AND eventMeta:"PaymentOrderPspCallRequested"
  ```
- Enables fast RCA (root-cause analysis) across all services.

---

## 11. Future Extensions

### Short Term (Planned)
- Implement **AccountBalanceConsumer** to maintain balance projections from ledger entries
- Add **AccountBalanceCache** in Redis for real-time balance lookups
- Create **LedgerReconciliationJob** to verify journal integrity daily

### Medium Term
- Introduce **SettlementBatching** for merchant payouts
- Integrate **external PSP connectors** (Adyen, Stripe) beyond the current simulator
- Add real-time **webhooks** for merchants to receive payment status updates

### Long Term
- Enable **OpenTelemetry spans** for distributed tracing (currently uses MDC for trace IDs)
- Implement **multi-currency support** with FX conversion
- Add **fraud detection** integration points
- Support for **refund workflows** with full ledger recording

---

*See [`how-to-start.md`](./how-to-start.md) for setup and deployment steps.*  
*See [`architecture-internal-reader.md`](./architecture-internal-reader.md) for detailed implementation notes and diagrams.*
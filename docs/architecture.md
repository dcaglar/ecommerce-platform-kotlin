# 💳 Payment Platform – System Architecture

---

## 1. Overview

This document describes the architecture of the **Payment Platform**, a modular Kotlin + Spring Boot system that models an event-driven, fault-tolerant **Payment and Ledger processing pipeline** inspired by real-world PSPs (like Adyen or Stripe).

The platform demonstrates **Domain-Driven Design (DDD)**, **Hexagonal Architecture**, and **exactly-once event processing** using Kafka transactions, PostgreSQL, and Redis.

**Key Design Principles:**

**1. API Isolation from External Dependencies**
- **PSP calls are completely isolated from the web layer** - `payment-service` returns `202 Accepted` immediately after persisting payment state
- **Actual PSP calls happen asynchronously** in `payment-consumers` workers, completely decoupled from HTTP request lifecycle
- **Benefits**: Even if external PSP is slow or down, user-facing API remains fast and responsive; users get immediate acceptance, processing continues in background
- **Fault Tolerance**: PSP failures, timeouts, or outages never cause user-facing API timeouts or rejections

**2. Independent Flow Separation for Independent Scaling**
- **PSP Flow** (topics: `payment_order_created_topic` → `payment_order_psp_call_requested_topic` → `payment_order_psp_result_updated_topic` → `payment_order_finalized_topic`) uses separate consumer groups with concurrency=8
- **Ledger Flow** (topics: `payment_order_finalized_topic` → `ledger_record_request_queue_topic` → `ledger_entries_recorded_topic`) uses separate consumer groups with concurrency=4
- **Balance Generation** *(Planned)* will consume from `ledger_entries_recorded_topic` with its own consumer group
- **Benefits**: 
  - PSP processing performance is never impacted by ledger generation bottlenecks
  - Ledger processing performance is never impacted by PSP call latency
  - Each flow can scale independently based on its own consumer lag
  - Different concurrency settings optimize each flow's throughput needs
- **Event-Driven Decoupling**: PSP flow completes and publishes to `payment_order_finalized_topic`; ledger flow consumes independently, allowing parallel processing

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
| **payment-service** | Ingests payment requests via REST, persists state + outbox, and returns `202 Accepted` immediately. **No PSP calls in request thread** - API isolated from external dependencies. |
| **payment-consumers** | Kafka-based worker application with 6 specialized consumers: PSP orchestration (3), status checks (1), and ledger recording (2). Executes all PSP calls asynchronously, decoupled from HTTP request lifecycle. |
| **payment-domain** | Core domain models, aggregates, events, and value objects. |
| **payment-application** | Use cases and orchestration logic (e.g., `RecordLedgerEntriesService`, `ProcessPaymentService`). |
| **payment-infrastructure** | Shared adapters (Kafka, DB, Redis, Liquibase migrations, metrics). |
| **common** | Shared DTOs, event envelopes, logging and tracing utilities. |

---

## 3. Functional Requirements

1. **Multi-Seller Payments**: Users can initiate payments containing multiple sellers (order-split scenarios).
2. **Independent Processing**: Each `PaymentOrder` is processed independently for PSP authorization/capture.
3. **Event-Driven Flow**: PSP responses drive domain events (`Succeeded`, `Failed`, `RetryRequested`) through Kafka.
4. **Double-Entry Ledger**: Successful and failed payments are journaled into a double-entry **ledger** with balanced debits and credits.
5. **Balance Aggregation** *(Planned)*: Account balances will be periodically derived from ledger entries, processing entries sequentially per merchant.
6. **End-to-End Tracing**: All events are traceable using `traceId`, `eventId`, and `parentEventId` across the entire pipeline.
7. **Retry & Recovery**: Transient PSP failures are automatically retried with exponential backoff (MAX_RETRIES = 5).

---

## 4. Non-Functional Requirements

| Aspect | Requirement |
|--------|--------------|
| **Reliability** | Exactly-once DB ↔ Kafka delivery via outbox & transactions. |
| **Scalability** | Horizontal scaling of consumers per topic/partition. |
| **Observability** | Full ELK pipeline with traceable JSON logs (Filebeat → Logstash → Elasticsearch → Kibana). |
| **Monitoring** | Prometheus + Grafana dashboards for lag, throughput, PSP latency, and retry metrics. |
| **Idempotency** | Exactly-once processing via Kafka transactions + idempotent handlers (updateReturningIdempotent, ON CONFLICT DO NOTHING). |
| **Consistency** | Eventual consistency across ledger and balance projections. |
| **Security** | OAuth2 (Keycloak), role-based access, signed JWT validation. |

---

## 5. Core Entities

| Entity | Description |
|--------|--------------|
| **Payment** | Coordination aggregate representing a multi-seller checkout (e.g., Amazon shopping cart). When a shopper checks out with products from different sellers, one `Payment` contains multiple `PaymentOrder` objects (1:N), one per seller. Tracks overall payment completion. When all `PaymentOrder` objects are finalized (succeed/fail), `Payment` status is updated and can optionally emit `PaymentCompleted` event for overall order tracking. |
| **PaymentOrder** | Processing aggregate for PSP interactions. Represents payment for a single seller's products. Each `PaymentOrder` is processed independently with separate PSP calls, retry logic, and event emission (`PaymentOrderCreated`, `PaymentOrderSucceeded`, `PaymentOrderFailed`). When `PaymentOrderSucceeded` is emitted for a seller, **Shipment domain immediately initiates shipment for that seller's products** (doesn't wait for other sellers). Processed via Kafka consumers using `paymentOrderId` as partition key. Completion of all `PaymentOrder` objects triggers optional `Payment` status evaluation. |
| **LedgerEntry** | Immutable journal entry with debit/credit postings. |
| **AccountBalance** | Derived projection (snapshot) of account-level balances. |
| **OutboxEvent** | Pending domain event stored transactionally with business data. |

---

## 6. High-Level Design

### Sequence

**A. Initial Payment Creation** *(API Isolation - Fast Response)*
1. **PaymentController**  
   - Receives REST request → persists `Payment`, `PaymentOrders`, and `OutboxEvent` in one DB transaction
   - **Returns `202 Accepted` immediately** - No PSP calls happen in request thread
   - **Critical Design**: User-facing API is completely isolated from external PSP latency/availability
   - Payment state is persisted; actual PSP processing happens asynchronously via Kafka

2. **OutboxDispatcherJob**  
   Periodically polls unsent outbox rows and publishes wrapped `PaymentOrderCreated` events to `payment_order_created_topic` (partitioned by `paymentOrderId`).

**B. PSP Call Orchestration**
3. **PaymentOrderEnqueuer**  
   - Consumes from `payment_order_created_topic` (partitioned by `paymentOrderId`)
   - Creates and publishes `PaymentOrderPspCallRequested` (attempt=0) to `payment_order_psp_call_requested_topic`
   - Maintains same partition key (`paymentOrderId`) for ordering

**C. PSP Execution & Result Handling**
4. **PaymentOrderPspCallExecutor**
   - Consumes from `payment_order_psp_call_requested_topic` (partitioned by `paymentOrderId`)
   - Invokes the PSP simulator asynchronously (with 500ms timeout)
   - Publishes PSP response (`PaymentOrderPspResultUpdated`) to `payment_order_psp_result_updated_topic`
   - Maintains partition alignment for ordering

5. **PaymentOrderPspResultApplierConsumer**
   - Consumes from `payment_order_psp_result_updated_topic` (partitioned by `paymentOrderId`)
   - Persists PSP result via `ProcessPaymentService`
   - Handles retry logic (Redis-backed with exponential backoff, MAX_RETRIES = 5)
   - Emits domain events:
      - **Success/Failure** → `PaymentOrderSucceeded` / `PaymentOrderFailed` to `payment_order_finalized_topic` (both events use same unified topic)
      - **Retry** → Schedules retry in Redis ZSet with exponential backoff
      - **Status Check** → Triggers `ScheduledPaymentStatusCheckExecutor` for async status verification

6. **ScheduledPaymentStatusCheckExecutor**
   - Consumes from `payment_status_check_scheduler_topic` (1 partition for sequential processing)
   - Performs delayed PSP status checks for pending payments
   - Updates payment status based on PSP response

**D. Ledger Recording** *(Independent Flow - Partition Key Switch: `paymentOrderId` → `sellerId`)*

7. **LedgerRecordingRequestDispatcher** *(Separate from PSP Flow)*
   - Consumes from `payment_order_finalized_topic` (partitioned by `paymentOrderId`) - **Same topic as PSP output, different consumer group**
   - Transforms `PaymentOrderEvent` → `LedgerRecordingCommand`
   - **Critical Design**: Publishes to `ledger_record_request_queue_topic` with **partition key = `sellerId`** (NOT `paymentOrderId`)
   - **Why?** Ensures all ledger commands for the same merchant route to the same partition
   - **Independent Scaling**: Uses consumer group `ledger-recording-request-dispatcher-consumer-group` (concurrency=4), separate from PSP consumers
   - Executes within Kafka transaction boundary for atomicity
   - **Flow Isolation**: PSP processing continues unaffected even if ledger recording is slow or backlogged

8. **LedgerRecordingConsumer** *(Completely Separate Flow)*
   - Consumes from `ledger_record_request_queue_topic` (24 partitions, partitioned by `sellerId`) - **Different topic from PSP flow**
   - Uses consumer group `ledger-recording-consumer-group` (concurrency=4) - **Independent scaling from PSP consumers**
   - Creates **double-entry journals** atomically via `RecordLedgerEntriesService`
   - Validates debits = credits before persistence
   - Persists journals and postings with idempotency (`ON CONFLICT DO NOTHING`)
   - Publishes `LedgerEntriesRecorded` to `ledger_entries_recorded_topic` with **key = `sellerId`**
   - Maintains partition alignment: all entries for a merchant stay in the same partition
   - **Decoupling Benefit**: Ledger processing bottlenecks (e.g., DB writes, journal calculations) never slow down PSP call execution or status updates

**E. Account Balance Aggregation** *(Planned - Not Yet Implemented - Separate from Ledger Flow)*
9. **AccountBalanceConsumer** *(Completely Independent Flow)*
   - Will consume from `ledger_entries_recorded_topic` (24 partitions, partitioned by `sellerId`) - **Same topic as ledger output, different consumer group**
   - Will use its own consumer group `account-balance-consumer-group` - **Independent scaling from ledger consumers**
   - **Flow Isolation**: Balance aggregation lag/bottlenecks never impact ledger recording performance
   - **Sequential Processing Guarantee**: All ledger entries for merchant "seller-123" arrive in the same partition
   - **Benefits**:
     - Processes entries in chronological order per merchant
     - Prevents race conditions when aggregating debits/credits
     - Ensures balance calculations are consistent
   - Will aggregate debits/credits per account into the `account_balances` table
   - Will optionally update a **real-time balance cache** in Redis for low-latency queries
   - **Decoupling Benefit**: Balance calculations (read-heavy queries, aggregations) won't slow down ledger journal entry persistence

10. **AccountBalanceCache (optional)** *(Planned)*
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
- `payment_order_created_topic` - Initial payment requests (partitioned by `paymentOrderId`)
- `payment_order_psp_call_requested_topic` - Work queue for PSP interactions (partitioned by `paymentOrderId`)
- `payment_order_psp_result_updated_topic` - PSP response results (partitioned by `paymentOrderId`)
- `payment_order_finalized_topic` - Terminal payment states (success/failure, partitioned by `paymentOrderId`)
- `ledger_record_request_queue_topic` - Commands to record ledger entries (**partitioned by `sellerId`**)
- `ledger_entries_recorded_topic` - Confirmation of persisted journals (**partitioned by `sellerId`**)
- Each topic has a corresponding `.DLQ` topic for dead letter handling

**Partitioning Strategy:**

| Topic Category | Partition Key | Partitions | Consumer Groups | Concurrency | Purpose |
|---------------|--------------|------------|-----------------|-------------|---------|
| **PSP Flow** | `paymentOrderId` | 48 | `payment-order-enqueuer-consumer-group`, `payment-order-psp-call-executor-consumer-group`, `payment-order-psp-result-applier-consumer-group` | 8 | Independent scaling; sequential processing per payment order |
| **Ledger Flow** | `sellerId` | 24 | `ledger-recording-request-dispatcher-consumer-group`, `ledger-recording-consumer-group` | 4 | Independent scaling; sequential processing per merchant |
| **Balance Flow** *(Planned)* | `sellerId` | 24 | `account-balance-consumer-group` | TBD | Will scale independently from ledger flow |

**Why Different Partition Keys?**

- **Payment Flow** (`paymentOrderId`): Each payment order must be processed sequentially to maintain state consistency through PSP calls, retries, and status updates.

- **Ledger Flow** (`sellerId`): After PSP flow completes, the system switches partition keys. This enables:
  - **Sequential Merchant Processing**: All payment orders from merchant "seller-123" are processed sequentially in the same partition
  - **Race Condition Prevention**: Balance updates for the same merchant won't conflict
  - **Correct Aggregation**: Future AccountBalanceConsumer processes entries in chronological order per merchant
  - **Scalability**: Different merchants are distributed across 24 partitions

**Implementation Note:**
- `RequestLedgerRecordingService` switches from `paymentOrderId` to `sellerId` when publishing `LedgerRecordingCommand`
- `RecordLedgerEntriesService` maintains `sellerId` as partition key when publishing `LedgerEntriesRecorded`
- Kafka's default partitioner (`hash(key) % partitions`) ensures consistent partition assignment


---

## 8. Key Design Patterns

- **Outbox Pattern:** Reliable DB → Kafka publishing.
- **Event Choreography:** Each consumer triggers the next domain step.
- **Double-Entry Accounting:** Enforced balance between debits and credits.
- **CQRS Projection:** AccountBalance table acts as a derived read model.
- **Idempotency:** Ledger insertions are deduplicated via unique journalId.
- **Observability:** MDC-based trace context across all event hops.
- **Merchant-Level Partitioning:** Ledger topics use `sellerId` (merchantId) as partition key instead of `paymentOrderId` to ensure all payment orders from the same merchant are processed sequentially for accurate balance aggregation.
- **API Isolation from External Dependencies:** PSP calls are completely separated from the web layer. `payment-service` returns `202 Accepted` immediately after persisting state; actual PSP calls execute asynchronously in `payment-consumers`. This ensures user-facing API performance is never impacted by external PSP latency, timeouts, or outages. Users get immediate acceptance, while processing continues in background.

- **Independent Flow Separation:** PSP flow, ledger flow, and balance generation (planned) operate independently with separate Kafka topics and consumer groups. This enables:
  - **Independent Scaling**: Each flow scales based on its own consumer lag (e.g., PSP concurrency=8, ledger concurrency=4)
  - **Performance Isolation**: PSP processing never slowed by ledger generation bottlenecks; ledger recording never slowed by balance calculations
  - **Fault Isolation**: Problems in one flow (e.g., ledger DB writes) don't impact other flows (e.g., PSP status updates)
  - **Different Throughput Needs**: Each flow optimized for its specific workload characteristics

---

## 9. Exactly-Once Processing & Duplicate Prevention

### 9.1 Exactly-Once Processing Strategy

**Architecture**: **At-Least-Once Delivery with Idempotent Handlers**

All consumers use **Kafka transactions** to ensure atomic operations:
- Consumer offset commit
- Database writes (payment status, ledger entries)
- Downstream event publishing

**Transaction Boundary**: `KafkaTxExecutor.run()` wraps operations in `kafkaTemplate.executeInTransaction()`:
- **On Success**: Offset committed + DB writes + events published (all atomic)
- **On Failure**: Transaction aborts → offset not committed → event retried → no partial state

**Consumer Configuration**:
- `isolation-level: read_committed` - Only reads committed messages
- `enable-auto-commit: false` - Manual offset management via transactions

### 9.2 Idempotency Mechanisms

**PSP Flow**:
- **`updateReturningIdempotent()`**: SQL prevents overwriting terminal states
  - `WHERE status NOT IN ('SUCCESSFUL_FINAL','FAILED_FINAL','DECLINED_FINAL')`
  - Uses `GREATEST()` for timestamps/retry counts to handle concurrent updates
- **Stale Event Filtering**: Drops retry attempts where `dbRetryCount > eventRetryCount`
- **Terminal State Check**: Skips processing if payment already finalized

**Ledger Flow**:
- **Database Constraints**: `ON CONFLICT (id) DO NOTHING` for journal entries
- **Batch Processing**: Stops immediately on first duplicate detection
- **Posting Protection**: `ON CONFLICT (journal_id, account_code) DO NOTHING`

**Result**: Duplicate events are safely handled - no duplicate payment status updates, no duplicate ledger entries, no duplicate event publications.

## 10. Resilience & Retry Strategy

### 10.1 Partition-Aligned Retry Handling
- All retry events (`PAYMENT_ORDER_PSP_CALL_REQUESTED`) are **republished to the same Kafka partition** as the original message.
- Guarantees ordering and avoids concurrent reprocessing of the same payment order.

### 10.2 Exponential Backoff with Equal Jitter
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

### 10.3 Redis-Based Retry Queue
- Transient failures queued in **Redis ZSET** keyed by `retryAt`.
- `RetryDispatcherScheduler` periodically pops due items and republishes via transactional Kafka send.

### 10.4 Dead Letter Queue (DLQ)
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

## 11. Monitoring & Observability

### 11.1 Monitoring Stack (📈 Prometheus + Grafana)

**Deployment:** Helm chart (`infra/scripts/deploy-monitoring-stack.sh`)

**Prometheus Metrics Collected:**
- **Kafka**: Consumer lag per partition, topic throughput, broker metrics
- **PSP Calls**: `psp_calls_total{result}` (counter), `psp_call_latency_seconds` (histogram)
- **Retry Queue**: `redis_retry_zset_size` (gauge), `redis_retry_batch_size` (gauge), `redis_retry_events_total{result}` (counter)
- **Outbox**: `outbox_event_backlog` (gauge), `outbox_dispatched_total{worker}` (counter), `outbox_dispatcher_duration_seconds` (histogram)
- **Database**: Connection pool usage, transaction counts
- **Application**: JVM heap/memory, thread pool sizes, HTTP request rates

**Grafana Dashboards:**
- *Payment Processing Overview* - Throughput, success rates, error breakdown
- *Consumer Lag* - Lag per consumer group and partition (critical for autoscaling decisions)
- *Outbox Throughput* - Events dispatched per second by worker thread
- *PSP Latency & Error Rates* - Response times (p50/p95/p99) and failure analysis
- *Retry Queue Monitoring* - Queue size, dispatch rate, batch processing metrics

**Autoscaling:**
- **HPA** (Horizontal Pod Autoscaler) scales based on **consumer lag**, not CPU
- Reacts to backpressure: When lag grows, replicas scale out; when queue drains, scale back in
- Targets `payment-order-psp-call-executor-consumer-group` lag for `payment_order_psp_call_requested_topic`
- **Lag-Based Scaling**: More accurate than CPU-based scaling for event-driven workloads

### 11.2 Observability Stack (🔎 ELK: Filebeat + Logstash + Elasticsearch + Kibana)

**Deployment:** `infra/scripts/deploy-observability-stack.sh`

**Logging Pipeline:**
1. **Application** → Structured JSON logs with MDC context (Logback)
2. **Filebeat** → Collects logs from container filesystem
3. **Logstash** → Parses, enriches, and routes to Elasticsearch
4. **Elasticsearch** → Indexed and searchable storage
5. **Kibana** → Visualization and search interface

**Trace Context Fields:**
- `traceId` - End-to-end request tracing (propagated via MDC)
- `eventId` - Unique event identifier
- `parentEventId` - Event causality chain
- `aggregateId` - Payment order ID or seller ID (depending on topic)
- `logger_name`, `level`, `message`, `timestamp`

**Kibana Search Examples:**
```kibana
# Follow a payment through the entire flow
traceId:"abc123"

# Find all events for a payment order
aggregateId:"paymentorder-123"

# Find all ledger entries for a merchant
aggregateId:"seller-789" AND eventMeta:"LedgerEntriesRecorded"

# Find retry events
eventMeta:"PaymentOrderPspCallRequested" AND message:"retry"
```

**Benefits:**
- **End-to-End Tracing**: Reconstruct complete payment flow across all services
- **Root Cause Analysis**: Quickly identify where failures occurred
- **Performance Analysis**: Track latency at each processing stage
- **Audit Trail**: Full event history for compliance and debugging

---

## 12. Future Extensions

### Short Term (Planned)
- Implement **AccountBalanceConsumer** and **AccountBalanceCache** - Aggregate balances from ledger entries with Redis caching ([Issue #119](https://github.com/dcaglar/ecommerce-platform-kotlin/issues/119))
- Create **Balance API** endpoints for querying account balances after AccountBalanceConsumer generates balance data ([Issue #119](https://github.com/dcaglar/ecommerce-platform-kotlin/issues/119))
- **Fast Path Optimization** - Introduce a synchronous fast path in payment-api layer where PSP is called for each payment order with a short timeout (100ms). If PSP responds within the timeout, return immediate result; otherwise, fall back to current async flow

### Medium Term
- Support for **refund and capture workflows** with full ledger recording ([Issue #106](https://github.com/dcaglar/ecommerce-platform-kotlin/issues/106))
- Create **LedgerReconciliationJob** to verify journal integrity daily
- Implement **CI/CD Pipeline** - Automate build, test, and deployment pipelines with GitHub Actions for code quality and safe feature integration ([Issue #117](https://github.com/dcaglar/ecommerce-platform-kotlin/issues/117))
- Introduce **SettlementBatching** for merchant payouts
- Integrate **external PSP connectors** (Adyen, Stripe) beyond the current simulator
- Add real-time **webhooks** for merchants to receive payment status updates

### Long Term
- Implement **multi-currency support** with FX conversion

---

*See [`how-to-start.md`](./how-to-start.md) for setup and deployment steps.*  
*See [`architecture-internal-reader.md`](./architecture-internal-reader.md) for detailed implementation notes and diagrams.*
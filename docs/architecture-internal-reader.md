# ecommerce-platform-kotlin · Architecture Guide

*Last updated: **2025‑01‑15** – maintained by **Doğan Çağlar***

---

## Table of Contents

1. [Purpose & Audience](#1--purpose--audience)
2. [System Context](#2--system-context)  
   2.1 [High‑Level Context Diagram](#21-highlevel-context-diagram)  
   2.2 [Bounded Context Map](#22-bounded-context-map)
3. [Core Design Principles](#3--core-design-principles)
4. [Architectural Overview](#4--architectural-overview)  
   4.1 [Layering & Hexagonal Architecture](#41-layering--hexagonal-architecture)  
   4.2 [Service & Executor Landscape](#42-service--executor-landscape)  
   4.3 [Payment Flow Architecture](#43-payment-flow-architecture)  
   4.4 [Event-Driven Flow](#44-event-driven-flow)
   4.5 [Ledger Recording Architecture](#45-ledger-recording-architecture)
5. [Cross‑Cutting Concerns](#5--crosscutting-concerns)  
   5.1 [Outbox Pattern](#51-outbox-pattern)  
   5.2 [Retry & Status‑Check Strategy](#52-retry--statuscheck-strategy)  
   5.3 [Idempotency](#53-idempotency)  
   5.4 [Unique ID Generation](#54-unique-id-generation)
6. [Data & Messaging Design](#6--data--messaging-design)  
   6.1 [PostgreSQL Outbox Partitioning](#61-postgresql-outbox-partitioning)  
   6.2 [Kafka Partitioning by `paymentOrderId`](#62-kafka-partitioning-by-paymentorderid)  
   6.3 [EventEnvelope Contract](#63-eventenvelope-contract)
7. [Infrastructure & Deployment (Helm/K8s)](#7--infrastructure--deployment-helmk8s)  
   7.1 [Helm Charts Overview](#71-helm-charts-overview)  
   7.2 [Environments & Values](#72-environments--values)  
   7.3 [Kubernetes Objects (Deployments, Services, HPA)](#73-kubernetes-objects-deployments-services-hpa)  
   7.4 [Lag‑Based Autoscaling (consumer lag)](#74-lagbased-autoscaling-consumer-lag)  
   7.5 [CI/CD & Scripts](#75-cicd--scripts)
8. [Observability & Operations](#8--observability--operations)  
   8.1 [Metrics (Micrometer → Prometheus)](#81-metrics-micrometer--prometheus)  
   8.2 [Dashboards (Grafana)](#82-dashboards-grafana)  
   8.3 [Logging & Tracing (JSON, OTel)](#83-logging--tracing-json-otel)  
   8.4 [ElasticSearch Search Keys](#84-elasticsearch-search-keys)
9. [Module Structure](#9--module-structure)  
   9.1 [`common`](#91-common)  
   9.2 [`payment-domain`](#92-payment-domain)  
   9.3 [`payment-application`](#93-payment-application)  
   9.4 [`payment-infrastructure` (Auto‑config)](#94-payment-infrastructure-autoconfig)  
   9.5 [Deployables: `payment-service` & `payment-consumers`](#95-deployables-payment-service--payment-consumers)
10. [Testing & Quality Assurance](#10--testing--quality-assurance)  
    10.1 [Testing Strategy](#101-testing-strategy)  
    10.2 [Test Coverage Results](#102-test-coverage-results)
11. [Quality Attributes](#11--quality-attributes)  
    11.1 [Reliability & Resilience](#111-reliability--resilience)  
    11.2 [Security](#112-security)  
    11.3 [Cloud‑Native & Deployment](#113-cloudnative--deployment)  
    11.4 [Performance & Scalability](#114-performance--scalability)
12. [Roadmap](#12--roadmap)
13. [Glossary](#13--glossary)
14. [References](#14--references)
15. [Changelog](#15--changelog)

---

## 1 · Purpose & Audience

This document is the **single source of truth** for the architectural design of the `ecommerce-platform-kotlin` backend.
It captures **why** and **how** we build a modular, event‑driven, cloud‑native platform that can scale to multi‑seller,
high‑throughput workloads while remaining observable, resilient, and easy to evolve.

- **Audience**: Backend engineers, SREs, architects, and contributors who need to understand the big picture.
- **Scope**: JVM services (REST APIs and async executors) plus the infrastructure they rely on.

---

## 2 · System Context

### 2.1 High‑Level Context Diagram

```mermaid
flowchart LR
subgraph Users
U1([Browser / Mobile App])
U2([Back‑office Portal])
end
U1 -->|REST/GraphQL|GW["🛡️ API Gateway / Ingress"]
U2 -->|REST|GW
GW --> PAY[(payment-service API)]
PAY --> K((Kafka))
K -->|events|CONS[(payment-consumers)]
K --> ANA[(Analytics / BI)]
PAY --> DB[(PostgreSQL Cluster)]
PAY --> REDIS[(Redis)]
subgraph Cloud
PAY
CONS
ANA
K
DB
REDIS
end
```

### 2.2 Bounded Context Map

```mermaid
flowchart TD
    classDef ctx fill: #fef7e0, stroke: #FBBC05, stroke-width: 2px;
    Payment[[Payment]]:::ctx
    Wallet[[Wallet]]:::ctx
    Shipment[[Shipment]]:::ctx
    Support[[Support]]:::ctx
    Analytics[[Analytics]]:::ctx
    Payment -- " PaymentResult events " --> Shipment
    Payment -- " PaymentResult events " --> Wallet
    Payment -- " PaymentResult events " --> Support
    Payment -- streams --> Analytics
```

---

## 3 · Core Design Principles

| Principle                  | Application in the Codebase                                                                                                       |
|----------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| **Domain‑Driven Design**   | Clear bounded contexts (`payment`, `wallet`, `shipment`, …) with domain, application, adapter, and config layers in every module. |
| **Hexagonal Architecture** | Domain code depends on *ports* (interfaces); adapters implement them (JPA, Kafka, Redis, PSP, …).                                 |
| **Event‑Driven**           | Kafka is the backbone; every state change is emitted as an `EventEnvelope<T>`.                                                    |
| **Outbox Pattern**         | Events are written atomically with DB changes and reliably published by dispatchers.                                              |
| **Observability First**    | JSON logs with `traceId`, Prometheus metrics, and OpenTelemetry (planned) tracing.                                                |
| **Cloud‑Native**           | Containerized apps, Helm charts, Kubernetes HPA, externalized configuration.                                                      |

---

## 4 · Architectural Overview

### 4.1 Layering & Hexagonal Architecture

```
┌───────────────────────────┐
│        Config Layer       │  ➜ Spring Boot wiring, profiles, auto‑config
├───────────────────────────┤
│      Adapter Layer        │  ➜ JPA, Kafka, Redis, PSP, REST controllers
├───────────────────────────┤
│    Application Layer      │  ➜ Orchestration services, schedulers, dispatchers
├───────────────────────────┤
│       Domain Layer        │  ➜ Aggregates, value objects, domain services, ports
└───────────────────────────┘
```

*Only the Domain layer knows nothing about Spring, databases, or Kafka.*

### 4.2 Service & Executor Landscape

```mermaid
%%{init:{'theme':'default','flowchart':{'nodeSpacing':70,'rankSpacing':70}}}%%
flowchart LR
    classDef service fill: #e6f5ea, stroke: #34A853, stroke-width: 3px;
    classDef adapter fill: #f3e8fd, stroke: #A142F4, stroke-width: 3px;
    classDef infra fill: #fde8e6, stroke: #EA4335, stroke-width: 3px;
    A["payment-service<br/>REST + Outbox Dispatcher"]:::service
    B["payment-consumers<br/>Enqueuer + PSP Call Executor + Result Applier"]:::service
    K((Kafka)):::infra
    DB[(PostgreSQL)]:::infra
    R[(Redis)]:::infra
    PSP[(Mock PSP)]:::infra
    A -->|Outbox →| K
    B -->|Consumes| K
    A --> DB
    A --> R
    B --> R
    B --> PSP
```

> **Current Architecture (Oct‑2025):** `payment-consumers` contains three specialized components:
> - **PaymentOrderEnqueuer** *(reads `payment_order_created` and enqueues PSP call tasks)*
> - **PaymentOrderPspCallExecutor** *(performs PSP calls and publishes results)*
> - **PaymentOrderPspResultApplier** *(applies PSP results and manages retries)*
    > This enables independent scaling of PSP work and clear separation of concerns.

### 4.3 Payment Flow Architecture

```mermaid
sequenceDiagram
    participant Client
    participant PaymentService
    participant DB
    participant OutboxDispatcher
    participant Kafka
    participant Enqueuer
    participant PspCallExecutor
    participant PspResultApplier
    participant PSP

    Client->>PaymentService: POST /payments
    PaymentService->>DB: Save Payment + PaymentOrders + OutboxEvent
    PaymentService-->>Client: 202 Accepted
    
    OutboxDispatcher->>DB: Read NEW outbox events
    OutboxDispatcher->>Kafka: Publish PaymentOrderCreated
    OutboxDispatcher->>DB: Mark events as SENT
    
    Enqueuer->>Kafka: Consume PaymentOrderCreated
    Enqueuer->>Kafka: Publish PaymentOrderPspCallRequested
    
    PspCallExecutor->>Kafka: Consume PaymentOrderPspCallRequested
    PspCallExecutor->>PSP: Call PSP.charge()
    PspCallExecutor->>Kafka: Publish PaymentOrderPspResultUpdated
    
    PspResultApplier->>Kafka: Consume PaymentOrderPspResultUpdated
    alt PSP Success
        PspResultApplier->>DB: Update PaymentOrder status
        PspResultApplier->>Kafka: Publish PaymentOrderSucceeded
    else PSP Retryable Failure
        PspResultApplier->>Redis: Schedule retry
        PspResultApplier->>DB: Update retry count
    else PSP Final Failure
        PspResultApplier->>DB: Mark as FINAL_FAILED
        PspResultApplier->>Kafka: Publish PaymentOrderFailed
    end
```

### 4.4 Event-Driven Flow

```mermaid
flowchart TD
    A1["POST /payments"] --> A2["CreatePaymentService"]
    A2 --> B1["DB Tx:<br/>• Save Payment/Orders<br/>• Insert Outbox (PaymentOrderCreated)"]
    B1 --> B2["202 Accepted"]
    B1 --> C1["OutboxDispatcherJob ➜ Kafka: payment_order_created"]
    
    C1 --> D1["PaymentOrderEnqueuer"]
    D1 --> E1["Kafka: payment_order_psp_call_requested"]
    E1 --> F1["PaymentOrderPspCallExecutor"]
    F1 --> G1["PSP.charge()"]
    G1 --> H1["Kafka: payment_order_psp_result_updated"]
    
    H1 --> I1["PaymentOrderPspResultApplier"]
    I1 -->|SUCCESS| J1["Kafka: payment_order_succeeded"]
    I1 -->|RETRY| K1["Redis ZSet retry + Scheduler ➜ PSP_CALL_REQUESTED"]
    I1 -->|STATUS_CHECK| L1["Scheduler ➜ payment_status_check"]
```
### 4.5 Ledger Recording Architecture

#### Purpose

Adds a complete **double‑entry accounting subsystem** for reliable financial recordkeeping. Every `PaymentOrderFinalized` event (consolidating `PaymentOrderSucceeded` and `PaymentOrderFailed`) triggers a new flow that writes balanced journal entries into a ledger table and publishes a confirmation event `LedgerEntriesRecorded`.

**Note:** Account balance aggregation from ledger entries is planned but not yet implemented (future: `AccountBalanceConsumer`).

#### Sequence Flow

```mermaid
sequenceDiagram
    participant Kafka
    participant Dispatcher as LedgerRecordingRequestDispatcher
    participant Command as LedgerRecordingCommand
    participant Consumer as LedgerRecordingConsumer
    participant Service as RecordLedgerEntriesService
    participant LedgerDB as Ledger Table

    Kafka->>Dispatcher: Consume PaymentOrderFinalized
    Dispatcher->>Kafka: Publish LedgerRecordingCommand
    Kafka->>Consumer: Consume LedgerRecordingCommand
    Consumer->>Service: recordLedgerEntries()
    Service->>LedgerDB: Append JournalEntries
    Service->>Kafka: Publish LedgerEntriesRecorded
```

#### Components

- **LedgerRecordingRequestDispatcher**: Kafka consumer for `payment_order_finalized_topic` (unified topic). Delegates to `RequestLedgerRecordingService`.
- **RequestLedgerRecordingService**: Transforms `PaymentOrderEvent` → `LedgerRecordingCommand` with trace and parent event propagation.
- **LedgerRecordingConsumer**: Listens on `ledger-record-request-queue`, invokes `RecordLedgerEntriesService`.
- **RecordLedgerEntriesService**: Creates balanced `JournalEntry` objects via `JournalEntryFactory`, persists via `LedgerEntryPort`, emits `LedgerEntriesRecorded`.

#### Domain Types

| Type                               | Role                                                                                |
| ---------------------------------- | ----------------------------------------------------------------------------------- |
| `LedgerRecordingCommand`           | Command issued per PaymentOrder after finalization (success or failure)             |
| `LedgerEntriesRecorded`            | Event confirming persisted journal entries                                          |
| `JournalEntry`                     | Balanced accounting record containing multiple postings                             |
| `Posting.Debit` / `Posting.Credit` | Represent atomic movements per account                                              |
| `AccountType`                      | Defines normal balance side and grouping (e.g., PSP\_RECEIVABLE, MERCHANT\_ACCOUNT) |

#### Example Journal Flow (SUCCESSFUL\_FINAL)

| Step       | Debit                                              | Credit                              |
| ---------- | -------------------------------------------------- | ----------------------------------- |
| AUTH\_HOLD | AUTH\_RECEIVABLE                                   | AUTH\_LIABILITY                     |
| CAPTURE    | AUTH\_LIABILITY, PSP\_RECEIVABLE                   | AUTH\_RECEIVABLE, MERCHANT\_ACCOUNT |
| SETTLEMENT | SCHEME\_FEES, INTERCHANGE\_FEES, ACQUIRER\_ACCOUNT | PSP\_RECEIVABLE                     |
| PSP\_FEE   | MERCHANT\_ACCOUNT                                  | PROCESSING\_FEE\_REVENUE            |
| PAYOUT     | MERCHANT\_ACCOUNT                                  | ACQUIRER\_ACCOUNT                   |

#### Idempotency & Replay Handling

**LedgerRecordingRequestDispatcher** → **RequestLedgerRecordingService**:
- Executes within `KafkaTxExecutor` transactional boundary
- Atomicity: Offset commit + `LedgerRecordingCommand` publish are committed together
- If `publishSync()` succeeds: Offset committed, no replay
- If `publishSync()` fails: Offset not committed, retried by consumer
- No explicit idempotency needed: Relies on downstream `RecordLedgerEntriesService` for DB-level deduplication
- Replayed PaymentOrderFinalized events will generate new `LedgerRecordingCommand` messages (expected behavior)

**LedgerRecordingConsumer** → **RecordLedgerEntriesService**:
- Executes within `KafkaTxExecutor` transactional boundary for offset + event commit
- **Batch Processing**: All journal entries for a payment order are processed atomically via `postLedgerEntriesAtomic()`
- Database-level idempotency via `ON CONFLICT` in `journal_entries` table
- When `insertJournalEntry()` returns `0` (duplicate detected):
  - **All remaining entries in the batch are skipped** - return statement stops batch processing
  - No postings are inserted for the duplicate entry
  - No exception thrown - graceful no-op
- **First execution** (`insertJournalEntry` returns `1`):
  - Entry and all postings inserted successfully
  - Processing continues with next entry in batch
- **Consequences of replays**:
  - Duplicate journal entries silently ignored (idempotent)
  - Entire batch processing stops on first duplicate detection
  - `LedgerEntriesRecorded` event will contain `0L` IDs for duplicates
  - External systems can detect replays via event deduplication logic

#### Exception Handling & Failure Modes

**LedgerRecordingRequestDispatcher Failure Scenarios**:
1. **Publish exception**: If `RequestLedgerRecordingService.publishSync()` throws (Kafka unavailable, serialization error)
   - Exception propagates up to `KafkaTxExecutor`
   - **Kafka transaction aborts**: Consumer offset not committed, `LedgerRecordingCommand` not published
   - Event will be retried automatically by Kafka consumer
   - No state change in ledger system
2. **Repeated failures**: If publish consistently fails, event will retry indefinitely until fixed or DLQ configured

**LedgerRecordingConsumer Failure Scenarios**:
1. **Persistence exception** (`postLedgerEntriesAtomic` throws during processing):
   - Exception propagates before `LedgerEntriesRecorded` event publishing
   - `KafkaTxExecutor` aborts transaction: Consumer offset not committed
   - `LedgerRecordingCommand` will be retried
   - **State**: Entries processed before exception may already be in DB (within transaction timeout boundary)
   - No event published
   - **On retry**: Idempotency protects against double-posting for successfully inserted entries
2. **Duplicate entry detected during batch** (entry N is duplicate):
   - `insertJournalEntry()` returns `0` for entry N
   - **Processing stops immediately** - `return` statement exits batch processing
   - Remaining entries (N+1..M) are not processed
   - **On retry**: 
     - Entries 0..N-1: Already persisted, `ON CONFLICT` detects duplicates, skips
     - Entries N..M: Fresh attempts to insert
     - Same behavior: stops on first duplicate
3. **Publish exception** (after successful persistence of all entries):
   - Exception propagates to `KafkaTxExecutor`, transaction aborts
   - Kafka offset not committed - command retried
   - **Critical**: All ledger entries already persisted from first attempt
   - **On retry**: `ON CONFLICT` prevents duplicate entries, processing stops on first duplicate
   - Event only published after successful persistence (will succeed on retry)
4. **Status handling**:
   - `SUCCESSFUL_FINAL`: Creates 5 journal entries (fullFlow) processed as batch
   - `FAILED_FINAL`/`FAILED`: Returns empty list, no persistence (early return)
   - Unknown status: Early return, no processing (idempotent no-op)

**Transaction Boundaries & Consistency**:
- **Kafka Transaction Boundary**: Each consumer invocation is wrapped in `KafkaTxExecutor`
  - Commit: Offset + all published events (atomic)
  - Abort: Offset not committed, all published events not visible
- **Journal Entry Persistence**: Entries processed sequentially within `postLedgerEntriesAtomic()` with `@Transactional`
  - Transaction wrapping controlled by Spring `@Transactional` on adapter
  - **Duplicate Detection Behavior**: When duplicate is detected, processing stops for entire batch
  - Rationale: Batch integrity maintained - either all entries succeed or processing stops on duplicate
- **Batch Failure Behavior**: If duplicate detected at entry N, entries 0 to N-1 were already persisted
  - Cannot be rolled back (already committed)
  - On retry: Duplicate entries detected, processing stops at first duplicate
  - Remaining entries from original batch are not processed
- **Event Consistency**: `LedgerEntriesRecorded` only published after batch processing completes (success or duplicate detection)

#### Traceability & Observability

- All ledger flows reuse `traceId` and `parentEventId` from `PaymentOrder` context.
- `LedgerEntriesRecorded` events maintain lineage to their original `PaymentOrderEvent`.
- Logs include: `traceId`, `eventId`, `parentEventId`, `aggregateId`, `ledgerBatchId`.
- Each ledger batch is identified by `ledger-batch-<UUID>` for auditability.
- Failed persistence attempts are logged with full context for debugging.

---

---

## 5 · Cross‑Cutting Concerns

### 5.1 Outbox Pattern

- Atomic write of domain state **and** outbox rows inside the same DB transaction.
- **OutboxDispatcherJob** (scheduled workers) reads `NEW` rows, publishes to Kafka, marks them `SENT`.
- Metrics: `outbox_event_backlog` (gauge), `outbox_dispatched_total`, `outbox_dispatch_failed_total`,
  `outbox_dispatcher_duration_seconds{worker=…}`.

### 5.2 Retry & Status‑Check Strategy

#### Retry Scheduling with Redis ZSet

- Retryable PSP results are **not** retried inline. We schedule retries in **Redis ZSet** with equal‑jitter backoff.
- A **RetryDispatcherScheduler** polls due items (every 5 seconds) and republishes `payment_order_psp_call_requested`.
- Non‑retryable outcomes are marked final and emitted; status‑check path is scheduled separately.
- Uses **atomic Redis operations** (ZPOPMIN) to prevent duplicate retry processing in multi-instance deployments.

#### Exponential Backoff with Equal Jitter

**Formula:**
```
delay = min(
  random_between(
    base_delay * 2^(attempt - 1) / 2,
    base_delay * 2^(attempt - 1)
  ),
  max_delay
)
```

Where:
- `base_delay = 2,000ms` (2 seconds)
- `max_delay = 60,000ms` (60 seconds)

**Delay Examples:**
| Attempt | Range (ms) | Example |
|---------|-----------|---------|
| 1 | 1,000 - 2,000 | ~1.5s |
| 2 | 2,000 - 4,000 | ~3s |
| 3 | 4,000 - 8,000 | ~6s |
| 4 | 8,000 - 16,000 | ~12s |
| 5 | 16,000 - 60,000 | ~30s (capped) |

**Why Equal Jitter?**
- Prevents thundering herd problems by randomizing retry timing
- Ensures retries spread evenly across the exponential window
- Reduces contention when multiple payment orders fail simultaneously

#### Retry Limits & Dead Letter Queue

- **MAX_RETRIES = 5**: After 5 failed attempts, payment order is marked `FINAL_FAILED` and routed to Dead Letter Queue
- **Dead Letter Queues**: Every Kafka topic has a corresponding `.DLQ` (e.g., `payment_order_psp_call_requested_topic.DLQ`)
- **DLQ Handling**: 
  - Monitored via Grafana dashboards
  - Alert threshold: > 100 messages in any DLQ over 5 minutes
  - Manual replay possible after fixing root cause
  - Daily reconciliation scripts verify DLQ contents

#### Retry Flow

1. **PSP Failure** → `PaymentOrderPspResultApplier` processes retryable status
2. **Retry Scheduling** → Retry event scheduled in Redis ZSet with computed backoff
3. **Dispatcher Poll** → `RetryDispatcherScheduler` polls due items every 5 seconds
4. **Republish** → Events republished to original Kafka topic with same partition key
5. **Max Attempts** → If `retryCount >= MAX_RETRIES`, mark final failed and send to DLQ

#### Metrics

- `redis_retry_zset_size` - Current size of retry queue
- `redis_retry_batch_size` - Size of last processed batch
- `redis_retry_events_total{result=processed|failed}` - Retry event counts
- `redis_retry_dispatch_batch_seconds` - Batch processing duration
- `redis_retry_dispatch_event_seconds` - Per-event processing duration

### 5.3 Idempotency

- Kafka processing is idempotent per `EventEnvelope.eventId` and domain keys; transactional producer/consumer
  co‑ordination where needed.
- Outbox + envelope ensure exactly‑once publish semantics (DB → Kafka).

### 5.4 Unique ID Generation

- Prefer domain‑level identifiers over DB sequences where practical; ID generator is encapsulated behind a port.

---

## 6 · Data & Messaging Design

### 6.1 PostgreSQL Database Structure

#### Ledger Tables

The ledger subsystem uses two tables for double-entry bookkeeping:

**`journal_entries`** - Transaction metadata
- `id` VARCHAR(128) PRIMARY KEY - Unique transaction identifier (e.g., "CAPTURE:paymentorder-123")
- `tx_type` VARCHAR(32) NOT NULL - Transaction type (AUTH_HOLD, CAPTURE, SETTLEMENT, FEE, PAYOUT)
- `name` VARCHAR(128) - Human-readable description
- `reference_type` VARCHAR(64) - Optional reference type
- `reference_id` VARCHAR(64) - Optional reference identifier
- `created_at` TIMESTAMP - Creation timestamp

**`postings`** - Debit/credit entries
- `id` BIGSERIAL PRIMARY KEY - Auto-incrementing posting ID
- `journal_id` VARCHAR(128) NOT NULL - FK to journal_entries
- `account_code` VARCHAR(128) NOT NULL - Account identifier (e.g., "PSP.AUTH_RECEIVABLE")
- `account_type` VARCHAR(64) NOT NULL - Account type enum
- `amount` BIGINT NOT NULL - Amount in minor currency units
- `direction` VARCHAR(8) NOT NULL - "DEBIT" or "CREDIT"
- `currency` VARCHAR(3) NOT NULL - ISO currency code
- `created_at` TIMESTAMP - Creation timestamp

**Constraints & Indexes**:
- Foreign key `fk_postings_journal` with `ON DELETE CASCADE`
- Unique constraint on `journal_entries(id)` for idempotency
- Unique constraint on `postings(journal_id, account_code)` to prevent duplicate postings
- Indexes on `postings.journal_id` and `postings.account_code` for query performance

**Idempotency**: Journal entries use `ON CONFLICT (id) DO NOTHING` to handle duplicate ledger recording requests gracefully.

#### PostgreSQL Outbox Partitioning

**Why**: very high write/scan volume; partition pruning keeps index/heap scans fast; cheap retention by dropping
partitions.

**How**: Time‑based **range partitions**, 30‑minute slices (examples seen in prod/test):
`outbox_event_20250813_2000`, `outbox_event_20250813_2030`, `outbox_event_20250813_2130`.

**DDL (illustrative)**:

```sql
-- Parent outbox table
CREATE TABLE outbox_event (
  oeid           BIGSERIAL PRIMARY KEY,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  status         TEXT NOT NULL CHECK (status IN ('NEW','SENT','FAILED')),
  payload        JSONB NOT NULL,
  key_hash       BIGINT,            -- optional for routing / maintenance
  published_at   TIMESTAMPTZ,
  error_message  TEXT
) PARTITION BY RANGE (created_at);

-- Partition helper (30‑minute buckets)
-- You may use pg_partman in real life; here is a manual pattern:
CREATE TABLE outbox_event_20250813_2000 PARTITION OF outbox_event
FOR VALUES FROM ('2025-08-13 20:00:00+00') TO ('2025-08-13 20:30:00+00');

-- ... and so on per 30‑minute window.
```

**Maintenance**:

- A nightly maintenance task pre‑creates the next N partitions and drops expired ones beyond retention.
- Indexes are local to partitions (e.g., `(status, created_at)`), drastically reducing bloat.
- Queries and the dispatcher job always filter by `status='NEW'` and current time window.

### 6.2 Kafka Partitioning by `paymentOrderId`

- Topics (examples):
    - `payment_order_created_topic` (p=8)
    - `payment_order_psp_call_requested_topic` (p=8)
    - `payment_status_check_scheduler_topic` (p=1)
    - `payment_order_succeeded_topic` (p=8)

- **Partitioning strategy**: the **message key = `paymentOrderId`**. This guarantees **ordering per aggregate** and
  naturally fans out load over partitions.

- **Consumer groups & concurrency** (current defaults):
    - `payment-order-enqueuer-consumer-group` → concurrency 4
    - `payment-order-psp-call-executor-consumer-group` → concurrency 8
    - `payment-order-psp-result-applier-consumer-group` → concurrency 8
    - `payment-status-check-scheduler-consumer-group` → concurrency 1

### 6.3 EventEnvelope Contract

```json
{
  "eventId": "4ca349b7-...",
  "aggregateId": "paymentOrderId-or-paymentId",
  "parentEventId": "optional-parent-id",
  "traceId": "w3c-or-custom-trace-id",
  "data": {
    "...": "domain-specific payload"
  }
}
```

- **Search keys** (also in logs): `eventId`, `traceId`, `parentEventId`, `aggregateId` (e.g., `paymentOrderId`).
- JSON logging + Elastic make it trivial to traverse causality chains across services.

---

## 7 · Infrastructure & Deployment (Helm/K8s)

### 7.1 Helm Charts Overview

Project charts:

```
charts/
├── payment-service
│   ├── Chart.yaml
│   ├── templates/
│   │   ├── _helpers.tpl
│   │   ├── configmap.yaml
│   │   ├── create-app-db-credentials-job.yaml
│   │   ├── deployment.yaml
│   │   ├── grant-app-db-privileges-job.yaml
│   │   ├── hpa.yaml
│   │   ├── pvc.yaml
│   │   ├── service-monitor.yaml
│   │   └── service.yaml
│   └── values.yaml
├── payment-consumers
│   ├── Chart.yaml
│   ├── templates/
│   │   ├── _helpers.tpl
│   │   ├── deployment.yaml
│   │   ├── hpa.yaml
│   │   ├── service-monitor.yaml
│   │   └── service.yaml
│   └── values.yaml
└── payment-platform-config
    ├── Chart.yaml
    └── templates/
        ├── configmap.yaml
        ├── redis-configmap.yaml
        └── secret.yaml
```

### 7.2 Environments & Values

`infra/helm-values/` contains opinionated defaults for local/dev:

```
infra/helm-values/
├── elasticsearch-values-local.yaml
├── filebeat-values-local.yaml
├── kafka-defaults.yaml
├── kafka-exporter-values-local.yaml
├── kafka-values-local.yaml
├── keycloak-values-local.yaml
├── kibana-values-local.yaml
├── monitoring-stack-values-local.yaml
├── my-postgres-defaults.yaml
├── payment-consumers-values-local.yaml
├── payment-db-values-local.yaml
├── payment-platform-config-values-local.yaml
├── payment-service-values-local.yaml
└── redis-values-local.yaml
```

- `payment-platform-config` ships shared ConfigMaps/Secrets for the platform.
- `payment-service-values-local.yaml` & `payment-consumers-values-local.yaml` configure images, env, resources,
  autoscaling, probes, and Micrometer exposure.

### 7.3 Kubernetes Objects (Deployments, Services, HPA)

- **Deployments** for each app with rolling updates.
- **ServiceMonitor** (Prometheus Operator) exposes `/actuator/prometheus` for scraping.
- **PVC** (for payment-service) optional if you persist local artifacts (e.g., JFR).
- **ConfigMap** templates wire Spring profiles and override app properties.

### 7.4 Lag‑Based Autoscaling (consumer lag)

- `payment-consumers` **does NOT** scale by CPU. It scales by **Kafka consumer lag** (cool!).
- Implementation options: KEDA with Kafka Scaler, or Prometheus Adapter + HPA with `kafka_consumergroup_lag` metric.
- Policy targets the **`payment-order-psp-call-executor-consumer-group`** lag for topic
  `payment_order_psp_call_requested_topic`.

> Result: When PSP is slow and lag grows, replicas scale out automatically; when the queue drains, they scale back.

### 7.5 CI/CD & Scripts

Key helpers under `infra/scripts/` (local/dev convenience):

- `deploy-*` scripts to stand up Kafka, Redis, Postgres, monitoring stack, ELK, and the two apps.
- `kubernetes/build-and-push-payment-*.sh` to produce/push images.
- `port-forward-*.sh` to reach cluster services locally.

---

## 8 · Observability & Operations

### 8.1 Metrics (Micrometer → Prometheus)

**Custom meters** (non‑exhaustive):

- **PSP**
    - `psp_calls_total{result=SUCCESSFUL|FAILED|DECLINED|TIMEOUT}`
    - `psp_call_latency_seconds` (histogram)

- **Redis retry**
    - `redis_retry_zset_size` (gauge)
    - `redis_retry_batch_size` (gauge)
    - `redis_retry_events_total{result=processed|failed}` (counter)
    - `redis_retry_dispatch_batch_seconds` / `redis_retry_dispatch_event_seconds` (timers → histograms)

- **Outbox**
    - `outbox_event_backlog` (gauge)
    - `outbox_dispatched_total` / `outbox_dispatch_failed_total` (counters; tagged `worker`)
    - `outbox_dispatcher_duration_seconds{worker}` (histogram)

- **Schedulers / Pools**
    - `scheduler_outbox_active_threads` / `scheduler_outbox_pool_size_threads` / `scheduler_outbox_queue_size` (gauges)

**Built‑ins enabled**: `http.server.requests`, `jvm`, `process`, `kafka`.

### 8.2 Dashboards (Grafana)

A curated set of graphs highlights: PSP success ratio & p95 latency, outbox backlog, dispatched/sec, consumer lag vs.
replicas, Redis retry throughput, JVM heap %, and HTTP RPS per pod.

**Examples (PromQL snippets)**

```promql
-- PSP success rate (5m)
sum by (result) (rate(psp_calls_total[5m]))
/ ignoring(result) group_left
sum (rate(psp_calls_total[5m]))

-- PSP p95 latency (5m)
histogram_quantile(0.95, sum by (le) (rate(psp_call_latency_seconds_bucket[5m])))

-- Outbox backlog (single authoritative gauge per pod; display without sum)
outbox_event_backlog

-- Outbox dispatch rate by worker (1m)
sum by (worker) (rate(outbox_dispatched_total[1m]))

-- JVM Heap % per pod (service & consumers)
100 * sum by (application, pod) (jvm_memory_used_bytes{area="heap",application=~"payment-(service|consumers)"})
  / sum by (application, pod) (jvm_memory_max_bytes{area="heap",application=~"payment-(service|consumers)"})

-- HTTP RPS per pod for POST /payments (1m)
sum by (pod) (rate(http_server_requests_seconds_count{uri="/payments",method="POST"}[1m]))
```

### 8.3 Logging & Tracing (JSON, OTel)

- **Structured JSON** logs everywhere; MDC propagated via `MdcTaskDecorator` so async tasks (schedulers, thread pools)
  keep context.
- Fields include: `eventId`, `traceId`, `parentEventId`, `aggregateId` (`paymentOrderId`), and domain metadata.
- Designed for **searchability** and correlation in Elastic/Kibana.
- OpenTelemetry integration is on the roadmap for distributed traces; envelope IDs already bridge most hops well.

### 8.4 ElasticSearch Search Keys

Common queries you can paste into Kibana:

```
eventId: "4ca349b7-*"            # exact or prefix
traceId: "7b0d0e..."             # follow the entire request
parentEventId: "*" AND aggregateId: "P-2025-08-..." 
logger_name: "*OutboxDispatcherJob*" AND level: ERROR
```

---

## 9 · Module Structure

We performed a **comprehensive restructuring** into clear modules plus two deployables.

### 9.1 `common`

- Shared utilities, event envelope infrastructure, logging helpers, and ID generation.
- Used by all other modules for consistent event handling and cross-cutting concerns.
- Contains `EventEnvelope<T>` wrapper, `LogContext` helpers, and common DTOs.

### 9.2 `payment-domain`

- Domain entities (`Payment`, `PaymentOrder`, value objects), domain services, and **ports**.
- Core business logic with no external dependencies.
- Value objects: `PaymentId`, `PaymentOrderId`, `Amount`, `BuyerId`, `SellerId`, `OrderId`
- Domain events: `PaymentOrderCreated`, `PaymentOrderSucceeded`, `PaymentOrderFailed`
- Status enums: `PaymentStatus`, `PaymentOrderStatus`

### 9.3 `payment-application`

- Use‑cases, orchestrators, schedulers (e.g., `RetryDispatcherScheduler`), and application‑level services.
- Depends on `payment-domain` and defines the **inbound/outbound ports** it needs.
- Services: `CreatePaymentService`, `ProcessPaymentService`
- Schedulers: `OutboxDispatcherJob`, `RetryDispatcherScheduler`

### 9.4 `payment-infrastructure` (Auto‑config)

- New **auto‑configurable** module consumed by both deployables.
- Provides Spring Boot auto‑configs for: Micrometer registry, Kafka factory/serializers, Redis/Lettuce beans, task
  schedulers/executors (with gauges), and common Jackson config.
- Houses adapters: JPA repos, Kafka publishers/consumers, Redis ZSet retry cache, PSP client, **MyBatis ledger mappers**.
- **Ledger Persistence**: MyBatis-based adapters for double-entry accounting
    - `LedgerMapper`: Interface for journal entries and postings CRUD operations
    - `LedgerEntryTxAdapter` (in `payment-consumers`): Implements `LedgerEntryPort` with batch processing
    - Database tables: `journal_entries` (transaction metadata) and `postings` (debit/credit entries)
    - Uses `ON CONFLICT` for idempotent inserts (duplicate journal entry detection)
    - **Batch Processing**: `postLedgerEntriesAtomic()` processes all entries for a payment order atomically
    - **Duplicate Handling**: When duplicate detected, batch processing stops immediately (no remaining entries processed)

### 9.5 Deployables: `payment-service` & `payment-consumers`

- **payment-service**: REST API, DB writes, **OutboxDispatcherJob**.
- **payment-consumers**:
    - `PaymentOrderEnqueuer` → reads `payment_order_created`, prepares PSP call requests.
    - `PaymentOrderPspCallExecutor` → performs PSP calls and publishes results.
    - `PaymentOrderPspResultApplier` → applies PSP results and manages retries/status checks.
    - `ScheduledPaymentStatusCheckExecutor` → handles status check requests.
    - `LedgerRecordingRequestDispatcher` → consumes finalized payment orders, publishes ledger recording commands.
    - `LedgerRecordingConsumer` → consumes ledger commands, persists entries via `LedgerEntryTxAdapter`.
    - `LedgerEntryTxAdapter` → implements `LedgerEntryPort` for batch journal entry persistence.
- Both depend on `payment-infrastructure` for shared wiring.

---

## 10 · Testing & Quality Assurance

### 10.1 Testing Strategy

The project employs a comprehensive testing strategy with **297 tests** achieving 100% pass rate across all modules.

#### Test Organization & Separation

**Unit Tests (`*Test.kt`)** - ✅ **PROPERLY CONFIGURED**
- **Naming Convention**: All follow `*Test.kt` pattern
- **External Dependencies**: ❌ **NONE** - All use mocks only
- **TestContainers**: ❌ **NONE** - No real external services
- **Spring Boot Tests**: ❌ **NONE** - No `@SpringBootTest`, `@DataRedisTest`, etc.
- **Maven Plugin**: **Surefire** - Runs with `mvn test`

**Integration Tests (`*IntegrationTest.kt`)** - ✅ **PROPERLY CONFIGURED**
- **Naming Convention**: All follow `*IntegrationTest.kt` pattern
- **External Dependencies**: ✅ **REAL** - Use TestContainers for Redis & PostgreSQL
- **TestContainers**: ✅ **YES** - `@Container`, `RedisContainer`, `PostgreSQLContainer`
- **Spring Boot Tests**: ✅ **YES** - `@SpringBootTest`, `@DataRedisTest`, etc.
- **Maven Plugin**: **Failsafe** - Runs with `mvn verify`

#### Unit Testing with MockK

- **MockK** (v1.13.8) is used project-wide for Kotlin-native mocking
- Replaced Mockito to resolve limitations with Kotlin value classes
- Clean, idiomatic Kotlin syntax: `every { }` and `verify { }` blocks
- Proper handling of Kotlin-specific features (value classes, inline classes)
- **Fixed MockK Syntax Issues**: Resolved hanging tests by correcting `just Awaits` to `returns 1` for methods returning `Int`

#### Explicit Testing Pattern & Avoiding Self-Reinforcing Mocks

All tests follow a strict pattern to ensure they verify **actual behavior**, not just mock configuration:

**Pattern Applied**:
1. **`every { }` blocks**: Use `any()` for parameters to stub behavior without restrictions
2. **`verify { }` blocks**: Use explicit `match { }` to verify the actual parameters passed
3. **Avoid Self-Reinforcing Mocks**: Never use the same explicit criteria in both `every` and `verify`

**Example (Correct Pattern)**:
```kotlin
// ✅ CORRECT: Stub accepts any input, verify checks actual values
every { eventPublisherPort.publishSync(any(), any(), any(), any(), any()) } returns mockk()
service.processEvent(event)
verify(exactly = 1) {
    eventPublisherPort.publishSync(
        eventMetaData = EventMetadatas.MyEventMetadata,
        aggregateId = event.id,
        data = match { it is MyEvent && it.amount == 10000L },
        parentEventId = expectedEventId,
        traceId = expectedTraceId
    )
}
```

**Anti-Pattern (Self-Reinforcing)**:
```kotlin
// ❌ WRONG: Same criteria in both stub and verify - test will always pass
every { 
    eventPublisherPort.publishSync(
        eventMetaData = EventMetadatas.MyEventMetadata,
        aggregateId = event.id,
        data = match { it.amount == 10000L },
        parentEventId = any(),
        traceId = any()
    )
} returns mockk()
verify(exactly = 1) {
    eventPublisherPort.publishSync(
        eventMetaData = EventMetadatas.MyEventMetadata,
        aggregateId = event.id,
        data = match { it.amount == 10000L }
    )
}
```

**Minimizing `capture()` Usage**: 
- Use `capture()` **only** when you need to inspect data before an exception is thrown
- For normal verification, prefer explicit `match {}` checks in `verify` blocks
- This makes tests more explicit about what they're testing

**Example modules with unit tests:**
- `common`: 3 tests (pure utility functions)
- `payment-domain`: 89 tests (pure domain logic, no mocking needed)
- `payment-application`: 22 unit tests with MockK
    - `CreatePaymentServiceTest`: 4 tests
    - `ProcessPaymentServiceTest`: 14 tests (includes retry logic, backoff calculations)
    - `RequestLedgerRecordingServiceTest`: 4 tests (tests status-based routing with explicit parameter verification)
    - `RecordLedgerEntriesServiceLedgerContentTest`: 5 tests (tests ledger entry persistence with detailed posting verification)
- `payment-infrastructure`: 178 unit tests + 6 integration tests with MockK
    - `PaymentOutboundAdapterTest`: 14 tests
    - `PaymentOrderOutboundAdapterTest`: 20 tests
    - `OutboxBufferAdapterTest`: 21 tests
    - `PaymentOrderStatusCheckAdapterTest`: 9 tests
    - `PaymentOrderStatusCheckAdapterEdgeCasesTest`: 6 tests
    - `PaymentOrderStatusCheckAdapterMappingTest`: 4 tests
    - `LedgerEntryAdapterTest`: 3 tests (tests ledger entry persistence with explicit posting verification)
    - `LedgerMapperIntegrationTest`: 6 integration tests (Testcontainers PostgreSQL, tests actual DB persistence)
    - Plus Redis, serialization, and entity mapper tests
- `payment-service`: 29 tests (REST controllers, services)
- `payment-consumers`: 40 tests (Kafka consumers, PSP adapters)

#### Integration Testing with TestContainers

- **PostgreSQL Integration Tests**: Real database with partitioned outbox tables and ledger tables
    - `LedgerMapperIntegrationTest`: Tests journal entries and postings persistence with Testcontainers PostgreSQL
    - Validates MyBatis mapper queries (`LedgerMapper`), duplicate handling via `ON CONFLICT`, and foreign key constraints
    - Verifies actual data is persisted correctly, not just return counts
- **Redis Integration Tests**: Real Redis instances for caching and retry mechanisms
- **Kafka Integration Tests**: Real Kafka clusters for event publishing/consuming
- Ensures realistic end-to-end behavior
- Validates outbox pattern, event publishing, and retry mechanisms

### 10.2 Test Coverage Results

**Platform-Wide Coverage Summary:**
- **Total Tests**: 297 tests (291 unit + 6 integration)
- **Success Rate**: 100% pass rate
- **Coverage**: Comprehensive coverage across all modules

| Module | Unit Tests | Integration Tests | Total | Status |
|--------|------------|-------------------|-------|--------|
| `common` | 3 | 0 | 3 | ✅ |
| `payment-domain` | 89 | 0 | 89 | ✅ |
| `payment-application` | 22 | 0 | 22 | ✅ |
| `payment-infrastructure` | 172 | 6 | 178 | ✅ |
| `payment-service` | 29 | 0 | 29 | ✅ |
| `payment-consumers` | 40 | 0 | 40 | ✅ |
| **TOTAL** | **355** | **6** | **361** | ✅ **100%** |

#### Key Testing Principles

1. **Proper Separation**: Unit tests use only mocks, integration tests use real external dependencies
2. **Isolation**: Domain tests are pure; application tests mock ports
3. **Realistic Integration**: TestContainers provide real infrastructure
4. **Value Class Safety**: MockK handles Kotlin value classes correctly
5. **Idempotency**: Tests verify event deduplication and idempotent processing
6. **Timing Assertions**: Retry scheduler tests validate backoff timing bounds
7. **No Hanging Tests**: All MockK syntax issues resolved for reliable test execution
8. **Explicit Verification**: Tests verify actual parameters passed to mocked methods, not just that methods were called
9. **No Self-Reinforcing Mocks**: Stubs use `any()`, verifications use explicit criteria to catch real bugs
10. **Capturing Only When Necessary**: `capture()` reserved for exception scenarios; normal verification uses explicit `match {}`

---

## 11 · Quality Attributes

### 11.1 Reliability & Resilience

- Outbox + event keys keep publishing safe.
- Retries with jitter and fenced attempts avoid duplicate external actions.
- Circuit breakers and timeout handling for external PSP calls.

### 11.2 Security

- Resource server with JWT (Keycloak in local dev). Secrets delivered via Kubernetes Secrets/values.
- Input validation and sanitization at API boundaries.

### 11.3 Cloud‑Native & Deployment

- Config externalized via Helm values and ConfigMaps; rolling updates; liveness/readiness probes; ServiceMonitor for
  metrics.
- Containerized applications with health checks and graceful shutdown.

### 11.4 Performance & Scalability

- Three‑stage consumer split enables independent scaling of PSP load.
- **Lag‑based autoscaling** reacts to backpressure instead of CPU heuristics.
- Partitioning (DB & Kafka) keeps hot paths fast.
- Thread pool management with metrics and monitoring.

---

## 12 · Roadmap

### Short Term
- Implement **AccountBalanceConsumer** to maintain balance projections from ledger entries
- Add **AccountBalanceCache** in Redis for real-time balance lookups
- Create **LedgerReconciliationJob** to verify journal integrity daily

### Medium Term
- Introduce **SettlementBatching** for merchant payouts
- Integrate **external PSP connectors** (Adyen, Stripe) beyond the current simulator
- Add real-time **webhooks** for merchants to receive payment status updates
- Automated outbox partition management (e.g., pg_partman)

### Long Term
- End‑to‑end **OpenTelemetry tracing** (currently uses MDC for trace IDs)
- Autoscaling policies per topic (fine‑grained)
- Blue/green deploy strategy for consumers during topic migrations
- Additional bounded contexts (wallet, shipment, order)
- Multi-currency support with FX conversion
- Fraud detection integration points
- Refund workflows with full ledger recording

---

## 13 · Glossary

- **Aggregate**: Consistency boundary (e.g., `PaymentOrder`).
- **Envelope**: Our event wrapper with IDs and tracing fields.
- **Outbox**: Table where events are first written before being published.
- **MockK**: Kotlin-native mocking library for unit tests.
- **SpringMockK**: Spring Boot integration for MockK (replaces Spring's Mockito support).
- **PSP (Payment Service Provider)**: External payment gateway (simulated in this platform).
- **EventEnvelope**: Standardized event wrapper with metadata for tracing and idempotency.
- **DDD (Domain-Driven Design)**: Software development approach focusing on business domains.
- **Exactly-Once Semantics**: Guarantee that events are processed exactly one time via Kafka transactions + idempotent handlers.
- **Hexagonal Architecture**: Architectural pattern where domain code depends on ports (interfaces), adapters implement them.
- **Double-Entry Accounting**: Bookkeeping method where every transaction affects two accounts (debit + credit balance).
- **DLQ (Dead Letter Queue)**: Kafka topic for unrecoverable messages after max retries.
- **Equal Jitter Backoff**: Exponential backoff strategy with randomization to prevent thundering herds.

---

## 14 · References

- Micrometer & Spring Boot Actuator docs.
- Kafka design patterns (compaction, partitioning, consumer groups).
- PostgreSQL partitioning best practices.
- MockK documentation: https://mockk.io/
- SpringMockK: https://github.com/Ninja-Squad/springmockk
- Domain-Driven Design patterns and hexagonal architecture.

---

## 15 · Changelog

- **2025-01-15**: **Architecture Documentation Enhancement**
    - **Comprehensive Architecture Review**: Updated both `architecture.md` and `architecture-internal-reader.md` for consistency and completeness
    - **Retry Strategy Documentation**: Added detailed exponential backoff formula with equal jitter, including delay examples for each attempt (1-5)
    - **MAX_RETRIES Clarification**: Documented that MAX_RETRIES = 5 and explained DLQ routing after exhaustion
    - **Dead Letter Queue Details**: Enhanced DLQ documentation with monitoring thresholds, alerting, and manual replay procedures
    - **AccountBalanceConsumer Status**: Clearly marked as "Planned - Not Yet Implemented" to avoid confusion
    - **Glossary Enhancement**: Added 8 new terms (PSP, DDD, Exactly-Once, Hexagonal Architecture, Double-Entry, DLQ, Equal Jitter Backoff)
    - **Roadmap Reorganization**: Restructured into Short Term, Medium Term, and Long Term categories for better planning clarity
    - **Ledger Recording Updates**: Corrected to reference unified `payment_order_finalized_topic` instead of separate succeeded/failed topics
    - **Technical Accuracy**: All formulas, examples, and implementation details now match the codebase exactly
- **2025-10-28**: **Ledger Batch Processing Implementation**
    - **Refactored Ledger Entry Processing**: Changed from individual `appendLedgerEntry()` calls to batch `postLedgerEntriesAtomic()` method
    - **Behavior Change**: When duplicate journal entry detected, batch processing stops immediately (remaining entries skipped)
    - **New Component**: `LedgerEntryTxAdapter` in `payment-consumers` module implements `LedgerEntryPort` with batch processing
    - **Testing**: Created comprehensive `LedgerEntryTxAdapterTest` with 11 tests covering batch operations, duplicate handling, and exception scenarios
    - **Updated Tests**: Refactored `RecordLedgerEntriesServiceLedgerContentTest` to use batch method
    - **Removed**: Old `LedgerEntryAdapterTest` from `payment-infrastructure` module
- **2025-10-28**: **Ledger Infrastructure & Testing Improvements**
    - **Database Tables**: Created `journal_entries` and `postings` tables for double-entry accounting via Liquibase
    - **MyBatis Mapper**: Implemented `LedgerMapper` interface and XML mapper for journal entry persistence with `ON CONFLICT` idempotency
    - **Adapter Implementation**: Created `LedgerEntryAdapter` implementing `LedgerEntryPort` with duplicate detection and posting skip logic
    - **Integration Tests**: Added `LedgerMapperIntegrationTest` (6 tests) using Testcontainers to verify actual DB persistence
    - **Factory-Enforced Object Creation**:
        - Made `JournalEntry` constructor `internal` and moved factory methods to companion object
        - Made `LedgerEntry` constructor `internal` and created dedicated `LedgerEntryFactory`
        - Prevents unvalidated object creation, enforcing business invariants (balanced entries, valid postings)
    - **Idempotency Design**: Database-level duplicate detection via `ON CONFLICT (id) DO NOTHING` prevents double-posting on replay
    - **Exception Handling**: Documented failure modes and retry behavior for both `RequestLedgerRecordingService` and `RecordLedgerEntriesService`
    - **Testing Refactoring**:
        - Refactored `RequestLedgerRecordingServiceTest` to follow explicit testing pattern (4 focused tests)
        - Refactored `RecordLedgerEntriesServiceLedgerContentTest` to verify all 5 ledger entries with detailed postings
        - Removed redundant tests and self-reinforcing mock patterns throughout test suite
        - Implemented strict pattern: `every` blocks use `any()`, `verify` blocks use explicit `match {}`
        - Minimized `capture()` usage - only in exception tests where needed to inspect data before error
        - Tests now explicitly verify each ledger entry's ID, txType, name, and all posting details (account types, amounts, directions)
- **2025-10-27 (evening update)**: **Finalized Payment → Ledger Event Flow**
    - Introduced unified topic `payment_order_finalized_topic` consolidating `PaymentOrderSucceeded` and `PaymentOrderFailed` events.
    - Updated `EventMetadatas` to route both success and failure events to `PAYMENT_ORDER_FINALIZED`.
    - `LedgerRecordingRequestDispatcher` now consumes from this topic and publishes `LedgerRecordingCommand` to `LEDGER_RECORD_REQUEST_QUEUE`.
    - Added consistent `traceId` and `parentEventId` propagation across the payment→ledger boundary for observability.
    - Introduced new constants in `Topics.kt` and `EVENT_TYPE.kt` (`PAYMENT_ORDER_FINALIZED`, `LEDGER_RECORDING_REQUESTED`, `LEDGER_ENTRIES_RECORDED`).
    - Updated `ProcessPaymentService` to publish `PaymentOrderSucceeded` / `PaymentOrderFailed` to the finalized topic.
    - Ensured dispatcher and consumer use `KafkaTxExecutor` for atomic offset commits.
    - Added recommendation to maintain one unified “finalized” topic instead of separate succeeded/failed queues for simplicity and scalability.
- **2025‑10‑27**: **Ledger Recording Subsystem Added** — Introduced `LedgerRecordingCommand`, `LedgerEntriesRecorded`, `LedgerRecordingRequestDispatcher`, `LedgerRecordingConsumer`, and `RecordLedgerEntriesService`. Added domain model for `JournalEntry`, `Posting`, and `Account`. Updated testing strategy to validate ledger entry persistence and event publication. Maintains trace propagation with `traceId` and `parentEventId`.
- **2025‑10‑19**: **Architecture Documentation Update** — Updated architecture documentation to reflect current project state. Added comprehensive testing strategy section with MockK migration details. Updated module structure to include `common` module. Added detailed payment flow architecture diagrams. Updated test coverage results showing 361 total tests with 100% pass rate. Enhanced event-driven flow documentation with current consumer architecture.
- **2025‑10‑16**: **Testing Infrastructure Upgrade** — Migrated entire project from Mockito to **MockK** (v1.13.8) and **SpringMockK** (v4.0.2). Resolves Kotlin value class limitations, improves test reliability, and provides idiomatic Kotlin testing syntax. Fixed MockK syntax issues that were causing test hangs. **Resolved type inference issues** in `OutboxDispatcherJobTest.kt` by adding explicit type hints for MockK matchers and fixing Jackson serialization configuration. All 297 tests now passing with 100% success rate. Proper test separation implemented: unit tests (`*Test.kt`) use mocks only, integration tests (`*IntegrationTest.kt`) use real external dependencies via TestContainers.
- **2025‑10‑09**: Refactored consumer design — split `PaymentOrderPspCallExecutor` into two specialized consumers: `PaymentOrderPspCallExecutor` (PSP call) and `PaymentOrderPspResultApplier` (result application). Introduced two types of Kafka transactional producers with their own custom processing logic (consume→produce→commit and producer-only transactional modes).
- **2025‑08‑14**: Major refresh. Added infra/Helm sections, DB/Kafka partitioning details, EventEnvelope,
  logging/Elastic search keys, and **lag‑based autoscaling**. Documented module split and the new
  `payment-infrastructure` auto‑config module.
- **2025‑06‑21**: Previous revision.

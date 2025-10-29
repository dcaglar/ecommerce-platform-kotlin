# 🛒 ecommerce-platform-kotlin

A **modular**, **event-driven**, and **resilient** eCommerce backend prototype built with **Kotlin + Spring Boot**, demonstrating how to design a high-throughput payment and ledger system using **Domain-Driven Design (DDD)**, **Hexagonal Architecture**, and **exactly-once event flows**.

> 🧩 Completed main `payment-service`, `payment-consumers`, and `ledger` flows.  
> 🔨 Currently working on **AccountBalanceConsumer and AccountBalanceCache** - Aggregate balances from ledger entries with Redis caching ([Issue #119](https://github.com/dcaglar/ecommerce-platform-kotlin/issues/119))  
> 🔜 Future modules: `accounting-service`, `wallet-service`, `order`, `shipment`

---

## 🚀 Quick Start

For local setup and deployment on Minikube:  
👉 **[docs/how-to-start.md](./docs/how-to-start.md)**

---

## 📌 Overview

This project simulates a real-world multi-seller eCommerce platform where:

- A single order may contain products from multiple sellers
- Each seller is paid independently (one `PaymentOrder` per seller)
- Payments flow through a PSP simulation with retries and exponential backoff (equal jitter)
- Successful PSP results trigger **double-entry ledger postings** with full audit trail
- All communication is **decoupled via Kafka** using transactional producers/consumers
- **Observability** (Prometheus + Grafana, ELK Stack) and **fault tolerance** (Outbox pattern, DLQ handling) are built in from day one
- **Account balance aggregation** is planned for near-term implementation

---

## 🧩 Highlights

### ✨ Implemented

- **Event-Driven Architecture** - Complete PSP → Ledger pipeline with strict ordering per payment (partitioned by `paymentOrderId`)
- **Outbox Pattern** - PostgreSQL-partitioned outbox for reliable DB→Kafka publishing with exactly-once semantics
- **Retry Mechanism** - Redis ZSet-backed retry scheduler with exponential backoff and equal jitter (MAX_RETRIES = 5)
- **Double-Entry Ledger** - `LedgerRecordingConsumer` enforces balanced accounting (debits = credits) with idempotency via `ON CONFLICT` database constraints
- **Ledger Dispatcher** - `LedgerRecordingRequestDispatcher` routes finalized payments to ledger recording queue
- **Monitoring & Observability** - Unified Prometheus + Grafana dashboards + ELK stack for logs and tracing
- **Dead Letter Queues** - Automatic DLQ routing for unrecoverable messages with reconciliation workflows

### 🔜 Planned

- **AccountBalanceConsumer** - Aggregate balances from ledger entries for real-time queries and analytics
- **AccountBalanceCache** - Redis-backed cache for low-latency balance lookups
- **LedgerReconciliationJob** - Daily integrity checks and balance verification
- **Accounting Domain Separation** - Move ledger-related components into dedicated `accounting-domain`, `accounting-application`, and `accounting-service` modules
- **Wallet Service Separation** - Move balance-related functionality into dedicated `wallet-service` module

---

## 📚 Documentation

- **[Architecture Guide](./docs/architecture.md)** - High-level system design and patterns
- **[Architecture Details](./docs/architecture-internal-reader.md)** - Deep dive into implementation
- **[How to Start](./docs/how-to-start.md)** - Local setup and Minikube deployment
- **[Folder Structure](./docs/folder-structure.md)** - Module organization and naming conventions

---

## 🏗️ High-Level Architecture Flow

This diagram focuses on the **payment-service**, **payment-consumers**, and **ledger** flows that are currently implemented.

```mermaid
graph TB
    subgraph API["payment-service REST API"]
        REST["POST /payments<br/>Returns 202 Accepted<br/>API isolated from PSP calls"]
        OUTBOX["OutboxDispatcherJob<br/>Publishes events from DB"]
    end

    subgraph DB_LAYER["PostgreSQL"]
        DB[("payment_orders<br/>outbox_event<br/>journal_entries<br/>postings")]
    end

    subgraph REDIS_LAYER["Redis"]
        RETRY["Retry Queue<br/>ZSet with Exponential Backoff"]
    end

    subgraph PSP_FLOW["PSP Flow payment-consumers"]
        T1["payment_order_created_topic<br/>key: paymentOrderId"]
        ENQ["PaymentOrderEnqueuer<br/>concurrency: 8"]
        T2["payment_order_psp_call_requested_topic<br/>key: paymentOrderId"]
        EXEC["PaymentOrderPspCallExecutor<br/>concurrency: 8"]
        PSP["PSP Client Mock Simulator"]
        T3["payment_order_psp_result_updated_topic<br/>key: paymentOrderId"]
        APPLY["PaymentOrderPspResultApplier<br/>concurrency: 8"]
        T4["payment_order_finalized_topic<br/>key: paymentOrderId<br/>Success/Failure unified"]
    end

    subgraph LEDGER_FLOW["Ledger Flow payment-consumers"]
        DISPATCH["LedgerRecordingRequestDispatcher<br/>concurrency: 4<br/>Switch: paymentOrderId to sellerId"]
        T5["ledger_record_request_queue_topic<br/>key: sellerId"]
        LEDGER["LedgerRecordingConsumer<br/>concurrency: 4"]
        T6["ledger_entries_recorded_topic<br/>key: sellerId"]
    end

    CLIENT["Client POST /payments"] --> REST
    REST -->|"1. Save Payment + Orders + Outbox Event"| DB
    REST -->|"2. 202 Accepted"| CLIENT
    OUTBOX -->|"3. Poll NEW events"| DB
    OUTBOX -->|"4. Publish"| T1
    T1 --> ENQ
    ENQ --> T2
    T2 --> EXEC
    EXEC -->|"PSP Call Async"| PSP
    PSP -.->|"Response"| EXEC
    EXEC --> T3
    T3 --> APPLY
    APPLY -->|"Success/Failed"| T4
    APPLY -->|"Retry"| RETRY
    RETRY -->|"Due Items Every 5s"| SCHEDULER["RetryDispatcherScheduler"]
    SCHEDULER -->|"Republish"| T2
    T4 -->|"Key Switch"| DISPATCH
    DISPATCH -->|"Transform + Publish sellerId"| T5
    T5 --> LEDGER
    LEDGER -->|"Persist Journals Batch"| DB
    LEDGER --> T6
```

**Key Features:**
- **API Isolation** - `payment-service` returns `202 Accepted` immediately; PSP calls happen asynchronously
- **Independent Flows** - PSP flow (concurrency=8) and Ledger flow (concurrency=4) scale independently
- **Partition Key Strategy** - Payment flow uses `paymentOrderId`, Ledger flow switches to `sellerId`
- **Exactly-Once Processing** - Kafka transactions + idempotent DB operations prevent duplicates
- **Retry Mechanism** - Redis ZSet with exponential backoff handles transient failures

---

## 🎯 Key Design Decisions

### Event Flow

```
HTTP Request → DB (Payment + Outbox) 
  → OutboxDispatcher → payment_order_created_topic
  → PaymentOrderEnqueuer → payment_order_psp_call_requested_topic
  → PaymentOrderPspCallExecutor → payment_order_psp_result_updated_topic
  → PaymentOrderPspResultApplier → payment_order_finalized_topic
  → LedgerRecordingRequestDispatcher → ledger_record_request_queue_topic
  → LedgerRecordingConsumer → ledger_entries_recorded_topic
  → AccountBalanceConsumer (planned) → account_balances table
```

1. **Outbox Pattern** - Events written atomically with domain state in PostgreSQL
2. **Partition-by-Aggregate** - All events keyed by `paymentOrderId` for ordering guarantees
3. **Redis Retry Queue** - Exponential backoff with equal jitter prevents thundering herds
4. **Double-Entry Ledger** - Every transaction balances (debits = credits) with audit trail
5. **Unified Finalized Topic** - `payment_order_finalized_topic` consolidates both success and failure events
6. **Dead Letter Queues** - Unrecoverable failures routed to `${topic}.DLQ` topics for manual handling

### Resilience & Reliability

- **Exactly-Once Semantics** - Kafka transactions + idempotent handlers ensure no duplicate processing
- **Retry Strategy** - MAX_RETRIES = 5 with exponential backoff (1s → 30s capped at 60s)
- **Observability** - Structured JSON logs with `traceId`, `eventId`, and `parentEventId` propagation (MDC-based)
- **Partitioning** - PostgreSQL outbox uses 30-minute range partitions for scalability
- **Kafka Transactions** - Producers and consumers use transactional boundaries for exactly-once semantics
- **Idempotency** - Database-level unique constraints and `ON CONFLICT` handling prevent duplicate processing

---

## 🧪 Testing

- **361 tests** with 100% pass rate (355 unit + 6 integration)
- **Unit tests** use MockK for Kotlin-native mocking
- **Integration tests** use Testcontainers for PostgreSQL and Redis
- **Separation** - `*Test.kt` for unit tests, `*IntegrationTest.kt` for integration tests
- **Modules**: `common` (3), `payment-domain` (89), `payment-application` (22), `payment-infrastructure` (178), `payment-service` (29), `payment-consumers` (40)

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin 1.9 |
| **Framework** | Spring Boot 3.2 |
| **Database** | PostgreSQL 15 with partitioning |
| **Cache** | Redis 7 |
| **Messaging** | Apache Kafka 3.5 |
| **Monitoring** | Prometheus + Grafana |
| **Logging** | ELK Stack (Elasticsearch, Logstash, Kibana) |
| **Auth** | Keycloak (OAuth2/JWT) |
| **Orchestration** | Kubernetes + Helm |
| **Testing** | MockK, SpringMockK, Testcontainers |

---

## 📊 Metrics & Monitoring

**Key Metrics:**
- `outbox_event_backlog` - Pending events to publish (gauge)
- `redis_retry_zset_size` - Current retry queue size (gauge)
- `redis_retry_batch_size` - Last processed retry batch size (gauge)
- `redis_retry_events_total{result=processed|failed}` - Retry event counts (counter)
- `psp_calls_total{result}` - PSP call success/failure rates (counter)
- `psp_call_latency_seconds` - PSP response time histogram (p50, p95, p99)
- `kafka_consumergroup_lag` - Consumer lag per partition (gauge)
- `outbox_dispatched_total{worker}` - Events successfully published (counter)
- `outbox_dispatch_failed_total{worker}` - Failed event dispatches (counter)

**Grafana Dashboards:**
- Payment Processing Overview
- Consumer Lag Tracking
- Outbox Throughput
- PSP Latency & Error Rates

**Kibana Search:**
```kibana
traceId:"abc123" AND eventMeta:"PaymentOrderPspCallRequested"
```

---

## 🔄 Retry & Resilience Strategy

**Exponential Backoff Formula:**
```
delay = min(
  random_between(
    base_delay * 2^(attempt - 1) / 2,
    base_delay * 2^(attempt - 1)
  ),
  max_delay
)
```
Where `base_delay = 2,000ms` and `max_delay = 60,000ms`.

**Example Delays:**
- Attempt 1: 1,000 - 2,000ms (~1.5s)
- Attempt 2: 2,000 - 4,000ms (~3s)
- Attempt 3: 4,000 - 8,000ms (~6s)
- Attempt 4: 8,000 - 16,000ms (~12s)
- Attempt 5: 16,000 - 60,000ms (~30s capped at 60s)

**Dead Letter Queue:**
- After **MAX_RETRIES = 5**, unrecoverable messages routed to `${topic}.DLQ` (e.g., `payment_order_psp_call_requested_topic.DLQ`)
- Monitored via Grafana dashboards with alert threshold: > 100 messages in any DLQ over 5 minutes
- Daily reconciliation scripts verify DLQ contents against source events
- Manual replay possible after fixing root cause (see `docs/cheatsheet/kafka.md`)

---

## 🚀 Getting Started

For detailed setup instructions, local deployment, testing, and troubleshooting:  
👉 **[docs/how-to-start.md](./docs/how-to-start.md)**

---

## 📄 License

This project is a demonstration/learning prototype. See [LICENSE](./LICENSE) for details.

---

**Built with ❤️ using Kotlin, Spring Boot, and Domain-Driven Design**
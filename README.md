# 🛒 ecommerce-platform-kotlin

# 📦 ecommerce-platform-kotlin
### Event-Driven Payments & Ledger Infrastructure for Multi-Seller Platforms

This project is a **technical showcase** demonstrating how large multi-entity platforms (Uber, bol.com, Amazon Marketplace, Airbnb) structure their payment and accounting flows. The system models the financial primitives that appear in every Merchant-of-Record (MoR) or marketplace environment, where all business events reduce to three fundamental money movements:

- **Pay-ins** — shopper → platform (authorization + capture)
- **Internal reallocations** — platform → internal accounts (fees, commissions, settlements)
- **Pay-outs** — platform → sellers or external beneficiaries

Rather than simulating a single business model, the platform implements a **small but realistic subset** of the flows used in production systems: synchronous authorization, multi-seller decomposition, asynchronous capture pipelines, idempotent state transitions, retries, and **double-entry ledger recording**. The goal is not to be feature-complete, but to demonstrate **sound architectural design**, correctness guarantees, and event-driven coordination across bounded contexts.

At the domain layer, the system follows **DDD principles** with clear aggregate boundaries (`Payment`, `PaymentOrder`, `Ledger`). Each event (authorization, capture request, PSP result, finalization, journal posting) is immutable and drives the next step in the workflow. At the architecture level, the system uses **hexagonal architecture**, the **outbox pattern**, **Kafka-based orchestration**, and **idempotent command/event handlers** to guarantee exactly-once processing across distributed components. Payment and ledger flows are completely asynchronous, partition-aligned, and fault-tolerant by design.

From an engineering standpoint, the project demonstrates how to structure a modern, cloud-ready financial system using a production-grade stack: **Kotlin**, **Spring Boot**, **Kafka**, **PostgreSQL**, **Redis**, **Liquibase**, **Docker**, and **Kubernetes**. It highlights practical system-design concerns such as resiliency, retries with jitter, consumer lag scaling, partitioning strategy, deterministic Snowflake-style ID generation, and observability through Prometheus/Grafana and structured JSON logs.

This repository is intended for **backend engineers, architects, and SREs** who want to understand how MoR platforms implement correct financial flows, balance eventual consistency with strict accounting rules, and design event-driven systems that scale under real-world load.


#### Architecture Diagram

```mermaid
flowchart TB
    classDef api fill:#e3f2fd,stroke:#1976D2,stroke-width:3px,color:#000
    classDef db fill:#fff8e1,stroke:#FBC02D,stroke-width:2px,color:#000
    classDef kafka fill:#f3e5f5,stroke:#8E24AA,stroke-width:2px,color:#000
    classDef svc fill:#e8f5e9,stroke:#388E3C,stroke-width:2px,color:#000
    classDef ext fill:#ffebee,stroke:#C62828,stroke-width:2px,color:#000
    classDef redis fill:#fff3e0,stroke:#FB8C00,stroke-width:2px,color:#000

    %% API + DB
    subgraph API["payment-service (REST API)"]
      REST["PaymentController / PaymentService\nPOST /api/v1/payments"]:::api
      OutboxJob["OutboxDispatcherJob\n(batch poller)"]:::svc
    end

    subgraph DB["Payment DB (PostgreSQL)"]
      PaymentTbl["payments\n(Payment aggregate)"]:::db
      PaymentOrderTbl["payment_orders\n(PaymentOrder aggregate)"]:::db
      OutboxTbl["outbox_event\n(partitioned)"]:::db
    end

    %% Kafka backbone
    subgraph Backbone["Kafka (event backbone)"]
      PO_CREATED["payment_order_created_topic\nkey = paymentOrderId"]:::kafka
      PSP_CAPTURE_CALL_REQ["payment_order_capture_request_queue_topic\nkey = paymentOrderId"]:::kafka
      PSP_RESULT["payment_order_psp_result_updated_topic\nkey = paymentOrderId"]:::kafka
      PO_FINAL["payment_order_finalized_topic\nkey = paymentOrderId"]:::kafka
      LEDGER_REQ["ledger_record_request_queue_topic\nkey = sellerId"]:::kafka
      LEDGER_REC["ledger_entries_recorded_topic\nkey = sellerId"]:::kafka
    end

    %% PSP flow (payment-consumers)
    subgraph PSP_FLOW["payment-consumers · PSP flow"]
      Enqueuer["PaymentOrderEnqueuer\n(consumes PO_CREATED,\nproduces PSP_CALL_REQ)"]:::svc
      PspExec["PaymentOrderCaptureExecutor\n(consumes PSP_CAPTURE_CALL_REQ,\ncalls PSP capture,\nproduces PSP_RESULT)"]:::svc
      PspApply["PaymentOrderPspResultApplier\n(consumes PSP_RESULT,\nupdates DB, schedules retry,\nproduces PO_FINAL)"]:::svc
    end

    subgraph RetryFlow["Redis retry"]
      RetryZSet["Redis ZSet\nretry queue (backoff)"]:::redis
      RetrySched["RetryDispatcherScheduler\n(polls ZSet,\nrepublishes PSP_CALL_REQ)"]:::svc
    end

    %% Ledger flow (payment-consumers)
    subgraph LedgerFlow["payment-consumers · ledger flow"]
      LedgerDisp["LedgerRecordingRequestDispatcher\n(consumes PO_FINAL,\nproduces LEDGER_REQ\nkey = sellerId)"]:::svc
      LedgerCons["LedgerRecordingConsumer\n(consumes LEDGER_REQ,\nappends journal entries,\nproduces LEDGER_REC)"]:::svc
    end

    subgraph LedgerDB["Ledger DB (PostgreSQL)"]
      Journal["journal_entries"]:::db
      Postings["postings"]:::db
    end

    PSP["External PSP\n(auth + capture)"]:::ext

    %% API -> DB
    REST -->|"POST /payments\n(auth + persist)"| PaymentTbl
    REST -->|"TX: Payment + Outbox"| OutboxTbl

    %% Outbox dispatcher
    OutboxTbl -->|"poll NEW"| OutboxJob
    OutboxJob -->|"expand Payment → PaymentOrders\n+ nested outbox rows"| PaymentOrderTbl
    OutboxJob -->|"publish PaymentOrderCreated"| PO_CREATED
    OutboxJob -->|"publish PaymentAuthorized"| Backbone

    %% PSP flow wiring
    PO_CREATED -->|"consume"| Enqueuer
    Enqueuer -->|"publish PSP call request"| PSP_CALL_REQ

    PSP_CALL_REQ -->|"consume"| PspExec
    PspExec -->|"capture() call"| PSP
    PSP -->|"result"| PspExec
    PspExec -->|"publish PSP result"| PSP_RESULT

    PSP_RESULT -->|"consume"| PspApply
    PspApply -->|"update PaymentOrder\nstatus + retryCount"| PaymentOrderTbl
    PspApply -->|"finalized"| PO_FINAL
    PspApply -->|"schedule retry"| RetryZSet

    %% Retry pipeline
    RetryZSet -->|"due items"| RetrySched
    RetrySched -->|"republish PSP call request"| PSP_CALL_REQ

    %% Ledger flow wiring
    PO_FINAL -->|"consume"| LedgerDisp
    LedgerDisp -->|"publish LedgerRecordingCommand\n(key = sellerId)"| LEDGER_REQ

    LEDGER_REQ -->|"consume"| LedgerCons
    LedgerCons -->|"append journal entries"| Journal
    Journal --> Postings
    LedgerCons -->|"publish LedgerEntriesRecorded"| LEDGER_REC
```
#### Payment  Flow Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant PaymentService as payment-service (API)
    participant PSPAuth as PSP Authorization API
    participant PaymentDB as Payment DB (Payment + Outbox)
    participant OutboxJob as OutboxDispatcherJob
    participant Kafka as Kafka
    participant Enqueuer as PaymentOrderEnqueuer
    participant PspExec as PaymentOrderPspCallExecutor
    participant PspApply as PaymentOrderPspResultApplier
    participant Retry as Redis RetryQueue (ZSET)

    %% 1. Synchronous shopper authorization
    Client->>PaymentService: POST /api/v1/payments\n{buyerId, orderId, totalAmount, paymentOrders}
    PaymentService->>PSPAuth: authorize(totalAmount, cardInfo)
    PSPAuth-->>PaymentService: authResult(APPROVED / DECLINED)

    alt Approved
        PaymentService->>PaymentDB: TX: persist Payment(PENDING_AUTH→AUTHORIZED)\n+ outbox<Payment* event>
        PaymentService-->>Client: 202 Accepted (auth ok, seller legs async)
    else Declined
        PaymentService->>PaymentDB: TX: persist Payment(DECLINED)
        PaymentService-->>Client: 402 Payment Required
    end

    %% 2. Outbox → Payment + seller legs expansion
    OutboxJob->>PaymentDB: poll NEW outbox events
    OutboxJob->>OutboxJob: expand Payment* event → N PaymentOrders (one per seller)
    OutboxJob->>PaymentDB: insert PaymentOrders + nested outbox<PaymentOrderCreated[]>
    OutboxJob->>Kafka: publish PaymentAuthorized
    OutboxJob->>Kafka: publish PaymentOrderCreated (per seller)
    OutboxJob->>PaymentDB: mark outbox rows SENT

    %% 3. PSP capture flow per seller leg
    Kafka->>Enqueuer: consume PaymentOrderCreated
    Enqueuer->>Kafka: publish PaymentOrderPspCallRequested

    Kafka->>PspExec: consume PaymentOrderPspCallRequested
    PspExec->>PSPAuth: capture(sellerAmount, authRef)
    PSPAuth-->>PspExec: captureResult
    PspExec->>Kafka: publish PaymentOrderPspResultUpdated

    Kafka->>PspApply: consume PaymentOrderPspResultUpdated
    alt Retryable PSP status
        PspApply->>PaymentDB: update PaymentOrder status + retryCount
        PspApply->>Retry: ZADD retryQueue(dueAt, paymentOrderId)
    else Final (CAPTURED or FINAL_FAILED)
        PspApply->>PaymentDB: update PaymentOrder terminal status
        PspApply->>Kafka: publish PaymentOrderFinalized
    end

    %% 4. Retry dispatcher (loop)
    Retry->>Kafka: publish PaymentOrderPspCallRequested\n(for due retry items)
```



#### Ledger Record Sequence Flow

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

#### Balance Flow Sequence


```mermaid
sequenceDiagram
    participant Ledger as LedgerRecordingConsumer
    participant Kafka as ledger_entries_recorded_topic
    participant Consumer as AccountBalanceConsumer
    participant Service as AccountBalanceService
    participant Redis as Redis (Deltas)
    participant Job as AccountBalanceSnapshotJob
    participant DB as PostgreSQL (Snapshots)

    Ledger->>Kafka: Publish LedgerEntriesRecorded (sellerId key)
    Kafka->>Consumer: Consume batch (100-500 events)
    Consumer->>Service: updateAccountBalancesBatch(ledgerEntries)
    Service->>Service: Extract postings, compute signed amounts per account
    Service->>DB: Load current snapshots (batch query: findByAccountCodes)
    Service->>Service: Filter postings by watermark (ledgerEntryId > lastAppliedEntryId)
    Service->>Service: Compute delta = sum(signed_amounts) for filtered postings
    Service->>Redis: addDeltaAndWatermark (Lua: HINCRBY delta + HSET watermark + SADD dirty)
    Note over Redis: TTL set on hash (5 min), dirty set marked
    
    Note over Job: Every 1 minute (configurable)
    Job->>Redis: getDirtyAccounts() (reads from dirty set)
    loop For each dirty account
        Job->>Redis: getAndResetDeltaWithWatermark (Lua: HGET delta+watermark, then HSET delta=0)
        alt Delta != 0
            Job->>DB: Load current snapshot (or create default)
            Job->>Service: Compute: newBalance = snapshot.balance + delta
            Job->>Service: Compute: newWatermark = maxOf(current.lastAppliedEntryId, upToEntryId)
            Job->>DB: saveSnapshot (UPSERT with WHERE watermark guard)
            Note over DB: Only updates if new watermark > current watermark
        end
    end
```

---

## 🚀 Quick Start

For local setup and deployment on Minikube:  
👉 **[docs/how-to-start.md](https://github.com/dcaglar/ecommerce-platform-kotlin/blob/main/docs/how-to-start.md)**


## 📚 Documentation

- **[Architecture Details](./docs/architecture-internal-reader.md)** – Deep implementation guide
- **[How to Start](./docs/how-to-start.md)** – Local setup and Minikube deployment
- **[Folder Structure](./docs/folder-structure.md)** – Module organization and naming conventions


**Built with ❤️ using Kotlin, Spring Boot, and Domain-Driven Design.**

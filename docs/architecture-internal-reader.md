# Payment & Ledger Platform — Architecture Documentation
**Version:** 1.0  
**Audience:** Engineers, Interviewers, Architects, Auditors  
**Scope:** Payment orchestration, PSP integration, payment order processing, retries, and double-entry ledger.

---

# 0. Introduction

## 0.1 What is this system?
From this platform’s point of view, all business flows are expressed as combinations of:

- **Pay-ins**: money entering the platform from external parties (e.g., riders/shoppers)
- **Internal reallocations**: money moving between internal entities and accounts
- **Pay-outs**: money leaving the platform to external beneficiaries (e.g., drivers, sellers, tax authorities)

The platform does not encode any single marketplace or Merchant-of-Record model.
Instead, it provides a small and realistic set of financial primitives that large
multi-entity platforms (e.g., Uber, bol.com, Airbnb, Amazon Marketplace) commonly
use: authorization, capture, asynchronous processing, idempotent state transitions,
and double-entry ledger recording.

Only a representative subset is implemented — enough to demonstrate architectural
thinking, correctness guarantees, and event-driven workflow design without trying
to recreate a complete enterprise system.

- **Audience**: Backend engineers, SREs, architects, and contributors who need to
  understand the big picture.
- **Scope**: Payment + ledger infrastructure for multi-entity, MoR-style platforms,
  where external PSPs are used as gateways for pay-ins and pay-outs, and all
  flows are eventually captured in the ledger.
-



## 2 · Functional Requirements
This platform models **multi-seller Merchant-of-Record** financial flows. All business operations reduce to a combination of **pay-ins, internal reallocations, and pay-outs**, governed by strict financial invariants.

**Core Functional Invariants:**
1. **Single Shopper Authorization**
    - One PSP authorization (`pspAuthRef`) per shopper.
    - PSP never sees internal seller structure.

2. **Multi‑Seller Decomposition**
    - One `Payment` decomposes into multiple `PaymentOrder`s (one per seller).

3. **Independent Capture Pipeline**
    - Each seller capture runs asynchronously (auto‑capture or manual).
    - PSP only receives: `pspAuthRef + amount`.

4. **Seller‑Level Payout Responsibility**
    - Platform ensures receivable/payable correctness per seller.

5. **Double‑Entry Ledger as Source of Truth**
    - Every financial event (auth, capture, settlement, fees, commissions, payouts) must produce balanced journal entries.

6. **Payout‑Safe Accounting**
    - Sellers can only be paid out after required journal posting.


## 0.2 Goals
- High reliability and correctness
- Guaranteed consistency (no double-charging, no double-posting)
- Event-driven decoupling
- Payment-order level sharding
- Secure PSP integration
- Transparent observability

## 0.3 Out of Scope / Non-Goals
- Not a PSP
- Not a returns/refund platform
- Not a KYC/AML system
- Not a general marketplace backend

---

# 1. System Overview

## 1.1 High-Level Summary
- `/payments` → sync authorization
- Outbox ensures DB→Kafka consistency
- PaymentAuthorized expands into PaymentOrders
- PaymentOrder processing performed asynchronously via Kafka
- Retry logic uses Redis ZSET
- Finalization triggers ledger posting
- Ledger (double-entry) updates account balances

## 1.2 Key Design Principles
- Hexagonal architecture
- Domain-Driven Design
- Event-driven state transitions
- Outbox Pattern
- Double-entry ledger (append-only)
- Exactly-once / idempotent mutation everywhere
- Observability via trace IDs & event envelopes

---

# 2. C4 Model — Structural Views


%% RELATIONSHIPS

%% Client → payment-service
## 2.1 C1 — System Context Diagram
*Diagrams:*
- Actors (Shopper, Ops, Finance)
- Client/Merchant App
- Payment & Ledger Platform (this system)
- External PSP
- Keycloak
- Finance/Reporting (optional future)

```mermaid
%%{init:{'theme':'default','flowchart':{'nodeSpacing':65,'rankSpacing':70}}}%%
flowchart LR

%% STYLES
classDef person fill:#FFF3E0,stroke:#FB8C00,stroke-width:2px,color:#000;
classDef system fill:#E3F2FD,stroke:#1565C0,stroke-width:2px,color:#000;
classDef external fill:#FFEBEE,stroke:#C62828,stroke-width:2px,color:#000;

%% PEOPLE (Actors)
Shopper["Shopper / End User"]:::person
MerchantOp["Merchant Ops User"]:::person
FinanceUser["Finance / Accounting User  
(Future External Consumer)"]:::person

%% CLIENT / FRONTEND SYSTEMS
ClientApp["Merchant Site / Client App  
(Web / Mobile)"]:::system
BackofficeUI["Backoffice / Ops Dashboard"]:::system

%% CORE SYSTEM (THIS SYSTEM)
subgraph YourCompany["Your Company"]
    PaymentPlatform["Payment & Ledger Platform  
    (This System)"]:::system

    Keycloak["Identity Provider  
(Keycloak / OAuth2)"]:::system

    FinanceSystem["Finance / Reporting System  
(Future External Consumer)"]:::system
end

%% EXTERNAL PSP
PSP["External PSP  
(Authorization & Capture APIs)"]:::external

%% RELATIONSHIPS

%% User interactions
Shopper -->|"Makes purchases"| ClientApp
MerchantOp -->|"Monitor payments, balances"| BackofficeUI
FinanceUser -->|"Views reports"| FinanceSystem

%% Client systems → Payment Platform
ClientApp -->|"HTTPS (Payment APIs)"| PaymentPlatform
BackofficeUI -->|"HTTPS (Backoffice APIs)"| PaymentPlatform

%% Authentication
PaymentPlatform -->|"OAuth2 / SSO"| Keycloak
ClientApp -->|"Login / SSO"| Keycloak
BackofficeUI -->|"Login / SSO"| Keycloak

%% PSP integration
PaymentPlatform -->|"PSP Integration  
(Authorization & Capture)"| PSP

%% Finance & reporting
PaymentPlatform -->|"Ledger & Balance Exports"| FinanceSystem
FinanceSystem -->|"Reports / Dashboards"| FinanceUser
```

---

## 2.2 C2 — Container Diagram (Runtime Architecture)
Containers included:
- **payment-service** (HTTP)
- **payment-consumers** (Kafka Workers)
- **Kafka Cluster**
- **Redis Cluster**
- **PaymentDB**
- **LedgerDB** (still same physical DB)
- **Keycloak**
- **PSP (external)**

```mermaid
%%{init:{'theme':'default','flowchart':{'nodeSpacing':65,'rankSpacing':75}}}%%
flowchart TB

%% STYLES
classDef container fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px,color:#000;
classDef infra fill:#ECEFF1,stroke:#455A64,stroke-width:2px,color:#000;
classDef ext fill:#FFEBEE,stroke:#C62828,stroke-width:2px,color:#000;
classDef db fill:#E3F2FD,stroke:#1565C0,stroke-width:2px,color:#000;

%% CLIENT SYSTEMS (same as C1)
ClientApp["Client App / Merchant Site"]:::ext
BackofficeUI["Backoffice / Ops Dashboard"]:::ext
Keycloak["Keycloak / OAuth2"]:::ext
PSP["External PSP  
(Auth, Capture APIs)"]:::ext
FinanceSystem["Finance / Reporting System  
(Future external consumer)"]:::ext

%% SYSTEM BOUNDARY — Payment Platform
subgraph PaymentPlatform["Payment & Ledger Platform (This System)"]

    %% payment-service container
    PaymentService["payment-service (HTTP API)  
    • POST /payments  
    • Idempotent Payment creation  
    • Writes Payments + Outbox  
    • Runs OutboxDispatcher"]:::container

    %% payment-consumers container
    PaymentConsumers["payment-consumers (Kafka Workers)  
    • PaymentOrderEnqueuer  
    • PaymentOrderPspExecutor  
    • PSP Result Applier  
    • RetryDispatcherScheduler  
    • LedgerRecordingRequestDispatcher  
    • LedgerRecordingConsumer  
    • AccountBalanceConsumer"]:::container

end

%% INFRASTRUCTURE
Kafka["Kafka Cluster  
• payment events  
• ledger events"]:::infra

Redis["Redis Cluster  
• PSP capture retries  
• Account-balance delta cache"]:::infra

PaymentDB["Payment DB (Postgres)  
• payments  
• payment_orders  
• outboxevent"]:::db

LedgerDB["Ledger DB (Postgres)  
• journal_entries  
• postings  
• account_balances  
• snapshots"]:::db

ClientApp -->|"HTTPS (Payment APIs)"| PaymentService
BackofficeUI -->|"HTTPS (Backoffice APIs)"| PaymentService

%% Auth
PaymentService -->|"OAuth2 / JWT"| Keycloak

%% payment-service → DB
PaymentService -->|"JDBC/MyBatis"| PaymentDB

%% payment-service → Kafka (via Outbox)
PaymentService -->|"Writes Outbox rows (store intent)"| PaymentDB

PaymentService -->|"OutboxDispatcher delivers DB events  
to Kafka (event fan-out + publication)"| Kafka

%% payment-consumers → Kafka
Kafka -->|"Consumes events"| PaymentConsumers
PaymentConsumers -->|"Publishes events"| Kafka

%% payment-consumers → DB
PaymentConsumers -->|"JDBC/MyBatis (payment orders)"| PaymentDB
PaymentConsumers -->|"Ledger writes"| LedgerDB

%% Redis (retry + balance deltas)
PaymentConsumers -->|"Retry scheduling & balance deltas"| Redis
Redis -->|"Retry events to Kafka"| Kafka

%% PSP calls
PaymentConsumers -->|"authorize(), capture()"| PSP

%% Finance system
Kafka -->|"Ledger events"| FinanceSystem
```

---




### 2.3.1 payment-service (HTTP)
- `PaymentController`
- `PaymentApplicationService`
- `OutboxWriter`
- `OutboxDispatcher` (Scheduled Job)
- Repositories

## 2.3 C3 — Component Diagrams
payment-service
```mermaid
%%{init:{'theme':'default','flowchart':{'nodeSpacing':70,'rankSpacing':80}}}%%
flowchart TB

%% STYLES
classDef adapter fill:#FFFDE7,stroke:#FBC02D,stroke-width:2px,color:#000;
classDef app fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px,color:#000;
classDef port fill:#E3F2FD,stroke:#1565C0,stroke-width:2px,color:#000;
classDef job fill:#F3E5F5,stroke:#7B1FA2,stroke-width:2px,color:#000;
classDef infra fill:#ECEFF1,stroke:#455A64,stroke-width:2px,color:#000;

%% Container Boundary
subgraph PaymentService["payment-service (HTTP API)"]
direction TB

    %% Inbound Adapter
    PaymentController["PaymentController  
    (REST adapter)"]:::adapter

    %% Application Layer
    PaymentServiceBean["PaymentService  
    (Application Orchestrator)"]:::app

    PaymentValidator["PaymentValidator"]:::app

    AuthorizePaymentService["AuthorizePaymentService  
    (implements AuthorizePaymentUseCase)"]:::app

    %% Inbound Port
    AuthorizePaymentUseCase["AuthorizePaymentUseCase  
    (inbound port)"]:::port

    %% Outbound Ports
    subgraph OutboundPorts["Outbound Ports"]
    direction TB
        PaymentRepository["PaymentRepository"]:::port
        PaymentOrderRepository["PaymentOrderRepository"]:::port
        OutboxRepository["OutboxEventRepository"]:::port
        PspAuthGatewayPort["PspAuthGatewayPort"]:::port
        IdGeneratorPort["IdGeneratorPort"]:::port
        SerializationPort["SerializationPort"]:::port
        EventPublisherPort["EventPublisherPort"]:::port
    end

    %% Outbound Adapters
    subgraph OutboundAdapters["Outbound Adapters"]
    direction TB
        PaymentAdapter["PaymentOutboundAdapter"]:::adapter
        PaymentOrderAdapter["PaymentOrderOutboundAdapter"]:::adapter
        OutboxAdapter["OutboxOutboundAdapter"]:::adapter
        PspAdapter["PspAuthorizationGatewayAdapter"]:::adapter
        IdGenAdapter["SnowflakeIdGeneratorAdapter"]:::adapter
        SerializationAdapter["JacksonSerializationAdapter"]:::adapter
        EventPublisherAdapter["PaymentEventPublisher  
        (Kafka Producer)"]:::adapter
    end

    %% Scheduled Job
    OutboxDispatcher["OutboxDispatcherJob  
• Poll NEW outbox events  
• Expand domain events into downstream events  
• Persist fan-out artifacts (e.g., child outbox rows)  
• Publish via EventPublisherPort  
• Mark outbox rows as SENT"]:::job

end

%% External Dependencies
PaymentDB["PaymentDB (Postgres)"]:::infra
Kafka["Kafka Cluster"]:::infra
PSP["External PSP"]:::infra
ClientApp["Client App (Merchant Site)"]:::infra

%% Flows
ClientApp -. "POST /payments" .-> PaymentController

PaymentController --> PaymentServiceBean
PaymentServiceBean --> PaymentValidator
PaymentServiceBean --> AuthorizePaymentUseCase
AuthorizePaymentUseCase --> AuthorizePaymentService

%% Application → outbound ports
AuthorizePaymentService --> PaymentRepository
AuthorizePaymentService --> PaymentOrderRepository
AuthorizePaymentService --> OutboxRepository
AuthorizePaymentService --> PspAuthGatewayPort
AuthorizePaymentService --> IdGeneratorPort
AuthorizePaymentService --> SerializationPort

%% Adapters wiring
PaymentRepository --> PaymentAdapter --> PaymentDB
PaymentOrderRepository --> PaymentOrderAdapter --> PaymentDB
OutboxRepository --> OutboxAdapter --> PaymentDB

PspAuthGatewayPort --> PspAdapter --> PSP
IdGeneratorPort --> IdGenAdapter
SerializationPort --> SerializationAdapter

%% Event Publishing path
EventPublisherPort --> EventPublisherAdapter --> Kafka

%% Outbox Job (NO direct Kafka call)
OutboxDispatcher --> OutboxRepository
OutboxDispatcher --> PaymentOrderRepository
OutboxDispatcher --> EventPublisherPort
```


paymentorder processing pipeline

┌────────────────────────┐
│ Topic: PAYMENT_ORDER_CREATED
└──────────────┬─────────┘
│
▼
┌──────────────────────────────┐
│ PaymentOrderEnqueuer         │
│  - uses SerializationPort    │
│  - uses JacksonAdapter       │
└──────────────┬───────────────┘
│ emits CAPTURE_REQUEST
▼
┌────────────────────────┐
│ Topic: PAYMENT_ORDER_CAPTURE_REQUEST
└──────────────┬─────────┘
│
▼
┌────────────────────────────────────┐
│ PaymentOrderCaptureExecutor        │
│  - PspCaptureGatewayPort → Adapter │
│  - EventPublisherPort → Adapter    │
└──────────────┬────────────────────┘
│ calls PSP
▼
┌───────────────┐
│ External PSP   │
└──────┬────────┘
│ PSP result
▼
┌────────────────────────┐
│ Topic: PSP_RESULT_UPDATED
└──────────────┬─────────┘
│
▼
┌─────────────────────────────────────────────┐
│ PaymentOrderPspResultApplier                │
│  - PaymentOrderModificationPort → Adapter   │
│  - RetryQueuePort → RedisAdapter            │
│  - EventPublisherPort → KafkaAdapter        │
└──────────────┬───────────────┬─────────────┘
│final           │retry
│                │
▼                ▼
┌────────────────┐   ┌───────────────┐
│ FINALIZED event│   │ Redis ZSet     │
└───────┬────────┘   │ (retry queue) │
│            └───────┬────────┘
▼                    │
┌─────────────────────────────┐
│ LedgerRecordingDispatcher   │
│  - EventPublisherPort       │
└───────┬────────────────────┘
▼
┌────────────────────────────────────┐
│ Topic: PAYMENT_ORDER_FINALIZED     │
└────────────────────────────────────┘


                 Redis retry loop
                 ─────────────────

Redis ZSet (retryAt)
│
▼
┌──────────────────────────┐
│ RetryDispatcherScheduler │
│  - RetryQueuePort        │
│  - EventPublisherPort    │
└───────────────┬──────────┘
│ emits CAPTURE_REQUEST again
▼
Topic: PAYMENT_ORDER_CAPTURE_REQUEST







### 2.3.2 payment-consumers (Kafka Workers)
- `PaymentOrderEnqueuer`
- `PaymentOrderCaptureExecutor`
- `PaymentOrderPspResultApplier`
- `RetryDispatcherScheduler`
- `StatusCheckScheduler` (if used)
- `LedgerRecordingRequestDispatcher`
- `LedgerRecordingConsumer`
- `AccountBalanceConsumer`
- Shared components:
    - `KafkaTxExecutor`
    - `IdGenerator`
    - `EventEnvelopeFactory`

### 2.3.3 ledger module components (optional future)
- `LedgerPostingService`
- `AccountBalanceService`

> Insert per-container component diagrams

---

# 3. Domain Model (DDD)

## 3.1 Aggregates

### **Payment**
- id, buyerId, orderId, totalAmount
- state: `PENDING_AUTH → AUTHORIZED/DECLINED`
- emits: `PaymentAuthorized`

### **PaymentOrder**
- one per seller
- states:
    - `INITIATED_PENDING`
    - `CAPTURE_REQUESTED`
    - `RETRY_PENDING`
    - `PENDING_STATUS_CHECK`
    - `CAPTURE_SUCCEEDED_FINAL`
    - `CAPTURE_FAILED_FINAL`
- emits:
    - `PaymentOrderCreated`
    - `PaymentOrderPspResultUpdated`
    - `PaymentOrderFinalized`

### **LedgerEntry & JournalEntry**
- Debit/Credit posting
- Append-only
- idempotent posting logic

### **AccountBalance**
- seller-level
- stored snapshots
- Redis deltas

---

## 3.2 Domain Events
- `PaymentAuthorized`
- `PaymentOrderCreated`
- `PaymentOrderCaptureCommand`
- `PaymentOrderPspResultUpdated`
- `PaymentOrderFinalized`
- `LedgerRecordingCommand`
- `LedgerEntriesRecorded`

---

## 3.3 Value Objects
- `Amount`
- `Currency`
- `SellerId`
- `PaymentId`, `PaymentOrderId`
- `RetryAttempt`
- `TraceId`

---

# 4. Consistency & Transaction Boundaries

## 4.1 Payment Creation (ACID)
- Payment + Outbox<PaymentAuthorized> inserted atomically

## 4.2 Outbox Dispatch (At-Least-Once → Idempotent)
- Outbox table polled by OutboxDispatcher
- Each event published to Kafka exactly once
- Outbox row marked SENT after Kafka TX commit

## 4.3 Payment Order State Updates
- All via `updateReturningIdempotent`
- No double-finalization
- No state rollback

## 4.4 Ledger Posting
- Append-only
- Each JournalEntry validated (sum debits == sum credits)
- Idempotent posting with conflict detection

---

# 5. Dynamic Behavior — System Flows

## 5.1 Payment Authorization Flow
- `/payments` → PSP.authorize()
- update Payment
- Outbox<PaymentAuthorized>

## 5.2 Outbox Event Processing
### PaymentAuthorized:
- expand into PaymentOrders
- create Outbox<PaymentOrderCreated>
- publish `payment_authorized`

### PaymentOrderCreated:
- publish event for each seller order

### PaymentOrderCaptureCommand:
- update PaymentOrder → `CAPTURE_REQUESTED`
- publish capture request

---

## 5.3 Payment Order Processing Flow
- PaymentOrderCreated → Enqueuer
- publish CaptureRequest
- PSP call
- PSP result → PspResultApplier
- update PaymentOrder (idempotent)
- finalize or retry

---

## 5.4 Retry Flow
- Redis ZSet schedules
- backoff
- requeue PaymentOrderPspCallRequested

---

## 5.5 Ledger Recording Flow
- PaymentOrderFinalized → `LedgerRecordingCommand`
- Ledger consumes and posts entries
- AccountBalanceConsumer updates balances

> Insert single canonical sequence diagram

---

# 6. Data Architecture

## 6.1 Payment DB Schema
- tables: payments, payment_orders, outbox

## 6.2 Ledger DB Schema
- journal_entries
- postings
- account_balances
- balance_snapshots

## 6.3 Kafka Topics
- payment_order_created_topic
- payment_order_capture_request_queue_topic
- payment_order_psp_result_updated_topic
- payment_order_finalized_topic
- ledger_record_request_queue_topic
- ledger_entries_recorded_topic

---

# 7. Operational Architecture

## 7.1 Observability
- traceId & parentEventId
- structured JSON logs
- Prometheus metrics
- Outbox lag monitoring
- Retry queue depth metrics

## 7.2 Scalability
- horizontal scaling of payment-consumers
- vertical scaling of PSP executor
- seller-sharded Kafka partitions

## 7.3 Fault Tolerance
- idempotent DB updates
- DLQ logic
- retry and backoff
- outbox consistency

---

# 8. Future / Target Architecture (Optional)
- independent ledger-service
- PSP webhook integration
- multi-region partitioning
- dynamic Kafka consumer configuration
- Temporal-based workflows (optional future)

---

# 9. Glossary
- Payment
- PaymentOrder
- Outbox
- PSP
- Ledger
- Posting
- JournalEntry
- Capture
- Retry
- Finalization

---

# 10. References
- C4 Model (Simon Brown)
- Domain-Driven Design (Evans)
- Outbox Pattern (Fowler)
- Kafka Exactly-Once Semantics
- Double-Entry Bookkeeping Principles  
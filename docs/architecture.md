# Architecture Overview · `ecommerce-platform-kotlin`

_Last updated: 2025-06-01 – maintained by **Doğan Çağlar**_

---

## 1 · Purpose & Scope
This document is the single source of truth for the technical design of **ecommerce-platform-kotlin**.  
It explains **why** and **how** we build a modular, event-driven, cloud-ready backend that can scale to multi-seller, high-throughput workloads while remaining observable, resilient, and easy to extend.

---

## 2 · Key Design Principles

| Principle | How it’s applied |
|-----------|-----------------|
| **Domain-Driven Design** | Clear bounded contexts (`payment-service`, `wallet-service`, `shipment-service`, …). Domain, application, adapter, config layers in every module. |
| **Hexagonal Architecture** | Domain code depends on _ports_ (interfaces); adapters implement them (JPA, Kafka, Redis, Outbox, PSP, …). |
| **Event-Driven** | Kafka is the backbone; every state change is emitted as an event wrapped in `EventEnvelope<T>`. |
| **Outbox Pattern** | Events are written to an outbox table in the same TX as the aggregate change and reliably published. |
| **Observability First** | JSON logs with traceId/eventId, Prometheus metrics, (planned) OpenTelemetry tracing. |
| **Cloud-Native Readiness** | Early containerization, Kubernetes manifests, profile-based config, secret management. |

---

## 3 · Service Landscape (Target)

## Bounded Contexts Diagram

```mermaid
flowchart LR
subgraph Client Layer
A["REST Controller<br/>(PaymentController)"]:::controller
end

subgraph Application Layer
B["PaymentService<br/>(Orchestrator)"]:::service
C[DomainEventEnvelopeFactory]:::service
D[PaymentOrderOutboxDispatcherScheduler]:::service
E[PaymentOrderEventPublisher]:::service
end

subgraph Domain Layer
F["Domain Models<br/>• Payment  • PaymentOrder"]:::domain
G["Ports / Interfaces<br/>• PaymentOutboundPort<br/>• PaymentOrderOutboundPort<br/>• OutboxEventPort<br/>• IdGeneratorPort"]:::domain
H["Retry Logic & Backoff<br/>(encapsulated in PaymentOrder)"]:::domain
end

subgraph Adapter Layer
I["Persistence Adapters<br/>• JPA Repositories"]:::adapter
J["Redis Adapters<br/>• ID Generator  • Retry ZSet"]:::adapter
K["Kafka Consumer<br/>(PaymentOrderExecutor)"]:::adapter
M["Retry Scheduler Job<br/>(Redis → PaymentOrderRetryRequested)"]:::adapter
N["PSP Client<br/>(Mock PSP)"]:::adapter
end

subgraph Infrastructure
DB[(PostgreSQL)]:::infra
REDIS[(Redis)]:::infra
KAFKA[(Kafka)]:::infra
PSP_API[(Mock PSP Endpoint)]:::infra
end

%% Relationships
A --> B
B --> F
B --> J
B --> I
B --> G
B --> C
B --> D
D --> E
E --> KAFKA
M --> E
KAFKA --> K
K --> N
K --> H
H --> J
I --> DB
J --> REDIS
N --> PSP_API

%% Styling
classDef controller   fill:#e8f0fe, stroke:#4b7bec, stroke-width:1px;
classDef service      fill:#e6ffe6, stroke:#43a047, stroke-width:1px;
classDef domain       fill:#fff4e6, stroke:#f39c12, stroke-width:1px;
classDef adapter      fill:#f3e5f5, stroke:#8e24aa, stroke-width:1px;
classDef infra        fill:#fce4ec, stroke:#d81b60, stroke-width:1px;

class A controller
class B,C,D,E service
class F,G,H domain
class I,J,K,M,N adapter
class DB,REDIS,KAFKA,PSP_API infra
```

~~## 4 · Outbox Pattern – Dual Tables

| Table | Example Events                                          | Dispatcher                      |
|-------|---------------------------------------------------------|---------------------------------|
| `outbox_payment` | `PaymentCreated`,                                       | **PaymentOutboxDispatcher**     |
| `outbox_payment_order` | `PaymentOrderCreated`, | **PaymentCreatedEventConsumer** |

```mermaid
flowchart TD
%% Database Tables
  A1["Payment Table"]
  A2["PaymentOrder Table"]
  O1["Outbox_Payment"]
  O2["Outbox_PaymentOrder"]

%% Application Nodes
  P1["PaymentService (persist Payment & PaymentCreated)"]
  D1["PaymentOutboxDispatcher (publish PaymentCreated)"]
  C1["PaymentCreated Consumer (Kafka Listener)"]
  P2["PaymentOrderService (persist PaymentOrders & PaymentOrderCreated)"]
  D2["PaymentOrderOutboxDispatcher (publish PaymentOrderCreated)"]

%% Kafka Topics
  K1["Kafka: payment_created_topic"]
  K2["Kafka: payment_order_created_topic"]

%% Flows
  P1 --> A1
  P1 --> O1
  D1 --> O1
  D1 --> K1
  K1 --> C1
  C1 --> A1
  C1 --> P2
  P2 --> A2
  P2 --> O2
  D2 --> O2
  D2 --> K2

%% Styling
  classDef outbox fill:#fce4ec,stroke:#d81b60;
  class O1,O2 outbox
  classDef kafka fill:#e1f5fe,stroke:#0288d1;
  class K1,K2 kafka
  classDef db fill:#f3e5f5,stroke:#8e24aa;
  class A1,A2 db
  classDef app fill:#e6ffe6,stroke:#43a047;
  class P1,P2,D1,D2,C1 app
```
---

## 5 · Unique ID Generation

All aggregates receive a **sequential, cluster-unique ID _before_ persistence** via `Redis INCR`.  
If Redis restarts, an init routine seeds the counter from the DB’s current max to guarantee no gaps or duplicates.

**Benefits**

* Stable ID available for logs, foreign keys, and event keys.
* Enables idempotent upserts.
* Allows clients to receive a public ID immediately after creation.

---

## 6 · Retry & Status-Check Strategy

| Scenario | Storage | Trigger | Notes |
|----------|---------|---------|-------|
| **Transient PSP error** | Redis ZSet (`payment:retry`) | PaymentOrderExecutor schedules retry | Exponential back-off, polling job republishes `PaymentOrderRetryRequested`. |
| **Pending PSP status** | Postgres table (`payment_order_status_check`) | Scheduled job queries due rows | Publishes `PaymentOrderStatusCheckRequested`. |
| **Max retries exceeded** | DLQ Topic | – | PaymentOrder marked `FAILED_FINALIZED`, alert emitted. |

---

## 7 · Idempotency

Processing the same event/request twice must have no side-effects.

* _Outbox Dispatcher_ – safe re-publish if state not marked `SENT`.
* _Kafka Consumers_ – skip update if aggregate already in expected state or if `eventId` seen.
* _DB Constraints_ – natural keys unique; duplicates rejected.

```kotlin
if (order.status == SUCCESSFUL) {
    log.info("Duplicate PaymentOrderSucceeded – skipping")
    return
}
```

---

## 8 · Observability Stack

| Layer | Tool |
|-------|------|
| Logs | Logback JSON → Filebeat → Elasticsearch → **Kibana** |
| Metrics | Micrometer → **Prometheus** → Grafana |
| Tracing | OpenTelemetry → **Jaeger / Tempo** (planned) |

Structured log fields: `traceId`, `eventId`, `parentEventId`, `aggregateId`.

---

## 9 · Security & Profiles

* Spring profiles: `local`, `k8s`, `prod`.
* Secrets: `.gitignored` `secrets-local.yml` (dev) vs. Kubernetes Secrets (k8s/prod).
* **OAuth2 / Keycloak** integration planned for all APIs.

---

## 10 · Roadmap (June 2025)

| # | Task | Key Deliverable |
|---|------|-----------------|
| 0 | ✅ Structured logging & ELK | JSON logs, Kibana |
| 1 | Retry logic refactor | Domain-owned; Redis ZSet + job |
| 2 | Elasticsearch read model | `/payments/search` |
| 3 | Monitoring & metrics | Prometheus, Grafana |
| 4 | Containerize services | Dockerfile, Compose |
| 5 | Dual outbox tables | Payment & PaymentOrder flows |
| 6 | Local Kubernetes deploy | Deployment & Service YAML |
| 7 | Dummy wallet/shipment | Event choreography demo |
| 8 | EventEnvelope factory enforcement | No direct constructors |
| 9 | OAuth2 security | Keycloak / Auth0 |
| 10 | Retry & DLQ hardening | DLQ topic + alerts |
| 11 | K8s node affinity & resources | Tuned limits/requests |
| 12 | Alerting & advanced monitoring | Slack/email alerts |
| 13 | Kafka consumer scaling | Concurrency & replicas |

---

## 11 · References
* Eric Evans, **Domain-Driven Design**
* Spring Boot, Spring Kafka, Micrometer documentation
* Apache Kafka & Kubernetes references
* OpenTelemetry specification
* Red Hat, **Building Event‑Driven Microservices**

# ecommerce-platform-kotlin · Architecture Guide

*Last updated: **2025‑08‑14** – maintained by **Doğan Çağlar***

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
   4.3 [Payment‑Service Layer Diagram](#43-payment-service-layer-diagram)  
   4.4 [Payment‑Service Layer Diagram (Alt)](#44-payment-service-layer-diagram-alt)
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
   9.1 [`payment-domain`](#91-payment-domain)  
   9.2 [`payment-application`](#92-payment-application)  
   9.3 [`payment-infrastructure` (Auto‑config)](#93-payment-infrastructure-autoconfig)  
   9.4 [Deployables: `payment-service` & `payment-consumers`](#94-deployables-payment-service--payment-consumers)
10. [Quality Attributes](#10--quality-attributes)  
    10.1 [Reliability & Resilience](#101-reliability--resilience)  
    10.2 [Security](#102-security)  
    10.3 [Cloud‑Native & Deployment](#103-cloudnative--deployment)  
    10.4 [Performance & Scalability](#104-performance--scalability)
11. [Roadmap](#11--roadmap)
12. [Glossary](#12--glossary)
13. [References](#13--references)
14. [Changelog](#14--changelog)

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
    B["payment-consumers<br/>Enqueuer + PSP Call Executor"]:::service
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

> **Split confirmed (Aug‑2025):** `PaymentOrderExecutor` was split into  
> **Enqueuer** *(reads `payment_order_created` and enqueues PSP call tasks)* and  
> **PaymentOrderPspCallExecutor** *(isolates PSP calling/latency).*  
> This lets us scale PSP work separately and observe it clearly.

### 4.3 Payment‑Service Layer Diagram

```mermaid
flowchart TD
    A1["POST /payments"] --> A2["Application Service"]
    A2 --> B1["DB Tx:\n• Save Payment/Orders\n• Insert Outbox (PaymentOrderCreated)"]
    B1 --> B2["202 Accepted"]
    B1 --> C1["Outbox Dispatcher ➜ Kafka: payment_order_created"]
```

### 4.4 Payment‑Service Layer Diagram (Alt)

```mermaid
flowchart TD
    C1["payment_order_created"] --> D1["Enqueuer (consumer)"]
    D1 --> E1["Kafka: payment_order_psp_call_requested"]
    E1 --> F1["PaymentOrderPspCallExecutor"]
    F1 -->|SUCCESS| G1["Kafka: payment_order_succeeded"]
    F1 -->|RETRY| H1["Redis ZSet retry + Scheduler ➜ PSP_CALL_REQUESTED"]
    F1 -->|STATUS CHECK| I1["Scheduler ➜ payment_status_check"]
```

---

## 5 · Cross‑Cutting Concerns

### 5.1 Outbox Pattern

- Atomic write of domain state **and** outbox rows inside the same DB transaction.
- **OutboxDispatcherJob** (scheduled workers) reads `NEW` rows, publishes to Kafka, marks them `SENT`.
- Metrics: `outbox_event_backlog` (gauge), `outbox_dispatched_total`, `outbox_dispatch_failed_total`,
  `outbox_dispatcher_duration_seconds{worker=…}`.

### 5.2 Retry & Status‑Check Strategy

- Retryable PSP results are **not** retried inline. We schedule retries in **Redis ZSet** with equal‑jitter backoff.
- A **RetryDispatcherScheduler** polls due items and republishes `payment_order_psp_call_requested`.
- Non‑retryable outcomes are marked final and emitted; status‑check path is scheduled separately.

### 5.3 Idempotency

- Kafka processing is idempotent per `EventEnvelope.eventId` and domain keys; transactional producer/consumer
  co‑ordination where needed.
- Outbox + envelope ensure exactly‑once publish semantics (DB → Kafka).

### 5.4 Unique ID Generation

- Prefer domain‑level identifiers over DB sequences where practical; ID generator is encapsulated behind a port.

---

## 6 · Data & Messaging Design

### 6.1 PostgreSQL Outbox Partitioning

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
├── jfr-debug.yaml
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

### 9.1 `payment-domain`

- Domain entities (`Payment`, `PaymentOrder`, value objects), domain services, and **ports**.
- No Spring or infra dependencies.

### 9.2 `payment-application`

- Use‑cases, orchestrators, schedulers (e.g., `RetryDispatcherScheduler`), and application‑level services.
- Depends on `payment-domain` and defines the **inbound/outbound ports** it needs.

### 9.3 `payment-infrastructure` (Auto‑config)

- New **auto‑configurable** module consumed by both deployables.
- Provides Spring Boot auto‑configs for: Micrometer registry, Kafka factory/serializers, Redis/Lettuce beans, task
  schedulers/executors (with gauges), and common Jackson config.
- Houses adapters: JPA repos, Kafka publishers/consumers, Redis ZSet retry cache, PSP client.

### 9.4 Deployables: `payment-service` & `payment-consumers`

- **payment-service**: REST API, DB writes, **OutboxDispatcherJob**.
- **payment-consumers**:
    - `PaymentOrderEnqueuer` → reads `payment_order_created`, prepares PSP call requests.
    - `PaymentOrderPspCallExecutor` → isolates external PSP calls; retry/status‑check are scheduled out of band.
- Both depend on `payment-infrastructure` for shared wiring.

---

## 10 · Quality Attributes

### 10.1 Reliability & Resilience

- Outbox + event keys keep publishing safe.
- Retries with jitter and fenced attempts avoid duplicate external actions.

### 10.2 Security

- Resource server with JWT (Keycloak in local dev). Secrets delivered via Kubernetes Secrets/values.

### 10.3 Cloud‑Native & Deployment

- Config externalized via Helm values and ConfigMaps; rolling updates; liveness/readiness probes; ServiceMonitor for
  metrics.

### 10.4 Performance & Scalability

- Two‑stage consumer split enables independent scaling of PSP load.
- **Lag‑based autoscaling** reacts to backpressure instead of CPU heuristics.
- Partitioning (DB & Kafka) keeps hot paths fast.

---

## 11 · Roadmap

- End‑to‑end OpenTelemetry tracing.
- Autoscaling policies per topic (fine‑grained).
- Automated outbox partition management (e.g., pg_partman).
- Blue/green deploy strategy for consumers during topic migrations.

---

## 12 · Glossary

- **Aggregate**: Consistency boundary (e.g., `PaymentOrder`).
- **Envelope**: Our event wrapper with IDs and tracing fields.
- **Outbox**: Table where events are first written before being published.

---

## 13 · References

- Micrometer & Spring Boot Actuator docs.
- Kafka design patterns (compaction, partitioning, consumer groups).
- PostgreSQL partitioning best practices.

---

## 14 · Changelog

- **2025‑08‑14**: Major refresh. Added infra/Helm sections, DB/Kafka partitioning details, EventEnvelope,
  logging/Elastic search keys, and **lag‑based autoscaling**. Documented module split and the new
  `payment-infrastructure` auto‑config module.
- **2025‑06‑21**: Previous revision.

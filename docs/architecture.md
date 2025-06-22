# ecommerce-platform-kotlin · Architecture Guide

\*Last updated: 2025‑06‑21 – maintained by \****Doğan Çağlar***

---

## Table of Contents

1. [Purpose & Audience](#1--purpose--audience)
2. [System Context](#2--system-context)
    1. [High‑Level Context Diagram](#21-highlevel-context-diagram)
    2. [Bounded Context Map](#22-bounded-context-map)
3. [Core Design Principles](#3--core-design-principles)
4. [Architectural Overview](#4--architectural-overview)
    1. [Layering & Hexagonal Architecture](#41-layering--hexagonal-architecture)
    2. [Service & Executor Landscape](#42-service--executor-landscape)
    3. [Payment‑Service Layer Diagram](#43-payment-service-layer-diagram)
    4. [Payment‑Service Layer Diagram (Alt)](#44-payment-service-layer-diagram-alt)
5. [Cross‑Cutting Concerns](#5--crosscutting-concerns)
    1. [Outbox Pattern](#51-outbox-pattern)
    2. [Retry & Status‑Check Strategy](#52-retry--statuscheck-strategy)
    3. [Idempotency](#53-idempotency)
    4. [Unique ID Generation](#54-unique-id-generation)
6. [Quality Attributes](#6--quality-attributes)
    1. [Observability](#61-observability)
    2. [Security](#62-security)
    3. [Cloud‑Native & Deployment](#63-cloudnative--deployment)
7. [Roadmap](#7--roadmap)
8. [Glossary](#8--glossary)
9. [References](#9--references)
10. [Changelog](#10--changelog)

---

## 1 · Purpose & Audience

This document is the **single source of truth** for the architectural design of the `ecommerce-platform-kotlin` backend.
It succinctly captures **why** and **how** we build a modular, event‑driven, cloud‑native platform that can scale to
multi‑seller, high‑throughput workloads while remaining observable, resilient, and easy to evolve.

- **Audience**: Backend engineers, SREs, architects, and any contributor who needs to understand the big picture.
- **Scope**: Everything that runs in the JVM, from REST APIs to async executors, and the infrastructure they rely on.

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
GW --> PAY[(Payment API)]
PAY --> K((Kafka))
K -->|events|SHIP[(Shipment Exec)]
K --> WAL[(WalletExec)]
K --> ANA[(Analytics)]
PAY --> DB[(PostgreSQL Cluster)]
PAY --> REDIS[(Redis)]
subgraph Cloud
PAY
SHIP
WAL
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
%% Relations (event‑driven)
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
| **Event‑Driven**           | Kafka is the backbone; every state change is emitted as an envelope `EventEnvelope<T>`.                                           |
| **Outbox Pattern**         | Events are written atomically with DB changes and reliably published by dispatchers.                                              |
| **Observability First**    | JSON logs with `traceId`, Prometheus metrics, and OpenTelemetry tracing (in progress).                                            |
| **Cloud‑Native Readiness** | Container images, Kubernetes manifests, profile‑based config, secrets management.                                                 |

---

## 4 · Architectural Overview

### 4.1 Layering & Hexagonal Architecture

All modules share a consistent 4‑layer structure:

```text
┌───────────────────────────┐
│        Config Layer       │  ➜ Spring Boot wiring, profiles, config classes
├───────────────────────────┤
│      Adapter Layer        │  ➜ JPA, Kafka, Redis, PSP, REST controllers
├───────────────────────────┤
│    Application Layer      │  ➜ Orchestration services, schedulers, dispatchers
├───────────────────────────┤
│       Domain Layer        │  ➜ Aggregates, value objects, domain services, ports
└───────────────────────────┘
```

*Only the Domain layer is allowed to know nothing about Spring, databases, or Kafka.*

### 4.2 Service & Executor Landscape

```mermaid
%%{init: { 
  "themeVariables": { "fontSize": "32px", "nodeTextSize": "32px" }, 
  "flowchart": { "nodeSpacing": 80, "rankSpacing": 90 },
  "theme": "default"
}}%%
flowchart LR
%% SRE-Style Custom Palette
    classDef controller fill: #e3f0fd, stroke: #4285F4, stroke-width: 3px;
    classDef service fill: #e6f5ea, stroke: #34A853, stroke-width: 3px;
    classDef domain fill: #fef7e0, stroke: #FBBC05, stroke-width: 3px;
    classDef adapter fill: #f3e8fd, stroke: #A142F4, stroke-width: 3px;
    classDef infra fill: #fde8e6, stroke: #EA4335, stroke-width: 3px;
    classDef legend fill: #fff, stroke: #aaa, stroke-width: 1px;
    subgraph Legend [Legend: Layer Color Coding]
        L1[Controller: Blue]:::controller
        L2[Service: Green]:::service
        L3[Domain: Yellow]:::domain
        L4[Adapter: Purple]:::adapter
        L5[Infra: Red]:::infra
    end

    subgraph Client_Layer ["Client Layer"]
        A["REST Controller<br/>(PaymentController)"]:::controller
    end

    subgraph Application_Layer ["Application Layer"]
        B["PaymentService<br/>(Orchestrator)"]:::service
        C[DomainEventEnvelopeFactory]:::service
        D[PaymentOrderOutboxDispatcherScheduler]:::service
        E[PaymentOrderEventPublisher]:::service
    end

    subgraph Domain_Layer ["Domain Layer"]
        F["Domain Models<br/>• Payment • PaymentOrder"]:::domain
        G["Ports / Interfaces<br/>• PaymentOutboundPort<br/>• PaymentOrderOutboundPort<br/>• OutboxEventPort<br/>• IdGeneratorPort"]:::domain
        H["Retry Logic & Backoff<br/>(in PaymentOrder)"]:::domain
    end

    subgraph Adapter_Layer ["Adapter Layer"]
        I["Persistence Adapters<br/>• JPA Repositories"]:::adapter
        J["Redis Adapters<br/>• ID Generator • Retry ZSet"]:::adapter
        K["Kafka Consumer<br/>(PaymentOrderExecutor)"]:::adapter
        M["Retry Scheduler Job<br/>(Redis → PaymentOrderRetryRequested)"]:::adapter
        N["PSP Client<br/>(Mock PSP)"]:::adapter
    end

subgraph Infrastructure_Layer ["Infrastructure"]
DB[(🗄️ PostgreSQL)]:::infra
REDIS[(📦 Redis)]:::infra
KAFKA[(🟪 Kafka)]:::infra
PSP_API[(💳 Mock PSP Endpoint)]:::infra
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

Legend --- Client_Layer
```



> **Target Evolution**: Each executor becomes an independently deployable Spring Boot app. All share the
`payment-domain` library to avoid code duplication and network latency.

### 4.3 Payment‑Service Layer Diagram

Paymentorder life cycle in the payment-service

```mermaid
flowchart TD
%% User initiates a payment
    A1["User/API\nPOST /payments"] --> A2["PaymentService\nReceive PaymentRequest"]
%% Store payment state and outbox event
    A2 --> B1["DB Transaction:\n• Save Payment\n• Save PaymentOrder(s)\n• Outbox: PaymentOrderCreated"]
    B1 --> B2["Respond 202 Accepted"]
    B1 -->|Outbox Dispatcher| C1["Kafka Topic:\npayment_order_created"]
%% Consumers of payment_order_created
    C1 -->|For each PaymentOrder| D1["PaymentOrderExecutor\nHandles PSP Call"]
    C1 --> D2["EmailService\nSend Confirmation"]
%% PSP execution, produces success/failure events
    D1 -->|Success| E1["Kafka Topic:\npayment_order_succeeded"]
    D1 -->|Retryable Failure| F1["Kafka Topic:\npayment_order_retry_requested"]
    D1 -->|Non - Retryable Failure| E2["Kafka Topic:\npayment_order_failed"]
%% Retry logic
    F1 --> G1["PaymentOrderRetryExecutor"]
    G1 -->|Success| E1
    G1 -->|Still Failing| E2
%% Status check scheduling
    D1 -->|Needs Status Check| H1["Kafka Topic:\npayment_order_status_check"]
    H1 --> H2["PaymentStatusCheckExecutor"]
    H2 -->|Final Result| E1
    H2 -->|Failure| E2
%% All events flow into a central topic for projection/aggregation
    E1 --> Z1["Kafka Topic:\npayment_order_events"]
    E2 --> Z1
    F1 --> Z1
%% Aggregator consumer projects overall payment result
    Z1 --> Y1["PaymentResultAggregatorConsumer\nAggregates per paymentId"]
%% Aggregator emits the final payment result
    Y1 -->|All SUCCEEDED| Y2["Kafka Topic:\npayment_succeeded"]
    Y1 -->|Any FAILED| Y3["Kafka Topic:\npayment_failed"]
%% Downstream consumers react to aggregate payment result
    Y2 --> K1["ShipmentService\nStart Fulfillment"]
    Y2 --> K2["WalletService\nUpdate Balance"]
    Y3 --> K3["Support/Analytics\nAlert or Compensate"]
```

# MoR-Payment-Platform

> Event-Driven Payment Infrastructure for Merchant-of-Record Platforms

**MoR-Payment-Platform** is a technical showcase of how large marketplace platforms
— Uber, bol.com, Amazon Marketplace, Airbnb — structure their payment and accounting flows
under a Merchant-of-Record model.

The system models the three fundamental money movements that every MoR environment reduces to:

- **Pay-ins** — shopper → platform (authorization + capture)
- **Internal reallocations** — platform → internal accounts (fees, commissions, settlements)
- **Pay-outs** — platform → sellers or external beneficiaries

Rather than simulating a complete product, the platform implements a realistic subset of
production-grade flows: synchronous authorization, multi-seller decomposition, asynchronous
capture pipelines, idempotent state transitions, retries, and double-entry ledger recording.
The goal is not feature completeness — it is **correctness, architectural integrity, and
fault-tolerant coordination across bounded contexts**.

At the domain layer, the system follows **DDD principles** with clear aggregate boundaries
(`PaymentIntent`, `Payment`, `PaymentOrder`, `Ledger`). Each event — authorization, capture
request, PSP result, finalization, journal posting — is immutable and drives the next step
in the workflow. At the infrastructure layer, the system relies on **hexagonal architecture**,
the **outbox pattern**, **Kafka-based orchestration**, and **idempotent command/event handlers**
to guarantee exactly-once processing across distributed components. All payment and ledger
flows are asynchronous, partition-aligned, and fault-tolerant by design.

From an engineering standpoint, the project demonstrates how to structure a modern, cloud-ready
financial system on a production-grade stack: **Kotlin**, **Spring Boot**, **Kafka**,
**PostgreSQL**, **Redis**, **Liquibase**, **Docker**, and **Kubernetes**. It surfaces practical
system-design choices: resiliency patterns, retries with jitter, consumer lag–driven scaling,
Kafka partitioning strategy, deterministic Snowflake-style ID generation, and observability
via **Prometheus/Grafana** and structured JSON logging.

This repository is written for **backend engineers, architects, and SREs** who want to
understand how MoR platforms implement correct financial flows, reconcile eventual consistency
with strict accounting rules, and build event-driven systems that hold up under real-world load.

---

## Engineering Deep Dives

The design decisions in this platform don't fit in a README.
Two companion articles go deeper on the problems that matter most in production financial systems:

- **[Architecture of Financial Integrity](YOUR_MEDIUM_URL_1)** — How DDD enforces that invalid
  domain states are not just detected, but made structurally impossible. Covers private
  constructors as invariant enforcers, immutable state machines that reject illegal transitions,
  ghost-payment prevention via lifecycle bridges, and a framework-free double-entry ledger
  where the accounting rules live in the domain — not the database.

- **[Idempotency and the Outbox Pattern](https://medium.com/@dcaglar1987/building-for-failures-payment-apis-idempotency-and-the-outbox-pattern-c50847f92ee4)** — How a payment API stays correct
  under network retries, race conditions, and infrastructure outages. Covers the four idempotency
  scenarios (first request, concurrent race, valid retry, key-reuse attack), the dual-write trap,
  and the transactional outbox as the reliability edge between your database and Kafka.

> Every pattern described in these articles is implemented in this codebase.

---

Please check [here](docs/architecture/architecture.md) for detailed architecture details.



#### High Level Overview


```mermaid
graph TB
    subgraph "Users"
        Shopper[👤 Shopper<br/>End-user making purchases<br/>across multiple sellers]
        Seller[👤 Seller<br/>Marketplace participant<br/>receiving payments]
    end

    subgraph "Internal Systems"
        CheckoutService[Checkout Service<br/>Handles shopper checkout flow]
        OrderService[Order Service<br/>Manages order lifecycle]
        FinanceService[Finance Service<br/>Financial reporting & payouts]
    end

    subgraph "Payment Platform"
        PaymentService[Payment Service<br/>REST API Application<br/>Manages payment lifecycle:<br/>authorization, payment intent creation,<br/>seller balance tracking]
        PaymentConsumers[Payment Consumers<br/>Kafka Consumer Application<br/>Asynchronous payment processing:<br/>capture operations, event handling,<br/>retry logic]
    end

    subgraph "External Systems"
        PSPGateway[PSP Gateway<br/>Payment Service Provider<br/>Authorization & Capture]
    end

    %% User interactions
    Shopper -->|Initiates checkout| CheckoutService
    
    %% Internal system interactions
    CheckoutService -->|Creates payment intents<br/>Authorizes payments<br/>REST API| PaymentService
    OrderService -->|Queries payment status<br/>REST API| PaymentService
    FinanceService -->|Queries seller balances<br/>REST API| PaymentService
    
    %% Payment Platform internal interactions
    PaymentService -.->|Publishes events<br/>Kafka| PaymentConsumers
    
    %% External system interactions
    PaymentService -->|Authorizes payments<br/>HTTPS| PSPGateway
    PaymentConsumers -->|Captures payments<br/>HTTPS| PSPGateway
    
    %% Indirect user interactions
    FinanceService -.->|Provides balance info| Seller

    %% Styling
    style Shopper fill:#e1f5ff,stroke:#1976D2,stroke-width:2px
    style Seller fill:#e1f5ff,stroke:#1976D2,stroke-width:2px
    style PaymentService fill:#fff4e1,stroke:#FF9800,stroke-width:3px
    style PaymentConsumers fill:#fff4e1,stroke:#FF9800,stroke-width:3px
    style CheckoutService fill:#f0e1ff,stroke:#8E24AA,stroke-width:2px
    style OrderService fill:#f0e1ff,stroke:#8E24AA,stroke-width:2px
    style FinanceService fill:#f0e1ff,stroke:#8E24AA,stroke-width:2px
    style PSPGateway fill:#ffe1e1,stroke:#C62828,stroke-width:2px
```



```mermaid
graph TD
    Start([Checkout Service<br/>Creates Payment]) --> PI1[PaymentIntent<br/>Status: CREATED<br/>Total: 2900 EUR<br/>Lines: SELLER-111: 1450<br/>SELLER-222: 1450]

    PI1 -->|POST /authorize| PI2[PaymentIntent<br/>Status: PENDING_AUTH<br/>Authorization in progress]

    PI2 -->|PSP Call| PSP{PSP Response}
    
    PSP -->|AUTHORIZED| PI3[PaymentIntent<br/>Status: AUTHORIZED]
    PSP -->|DECLINED| PI4[PaymentIntent<br/>Status: DECLINED<br/>END]
    PSP -->|TIMEOUT| PI2

    PI3 -->|Create Payment| P1[Payment<br/>Status: NOT_CAPTURED<br/>Total: 2900 EUR<br/>Captured: 0 EUR<br/>Refunded: 0 EUR]

    P1 -->|Fork into N Orders| PO1[PaymentOrder 1<br/>SELLER-111<br/>Status: INITIATED_PENDING<br/>Amount: 1450 EUR<br/>Retry: 0]
    P1 -->|Fork into N Orders| PO2[PaymentOrder 2<br/>SELLER-222<br/>Status: INITIATED_PENDING<br/>Amount: 1450 EUR<br/>Retry: 0]

    %% PaymentOrder 1 State Machine
    PO1 -->|Enqueued| PO1A[PaymentOrder 1<br/>Status: CAPTURE_REQUESTED<br/>Retry: 0]
    PO1A -->|PSP Capture Call| PSP1{PSP Result}
    PSP1 -->|SUCCESS| PO1B[PaymentOrder 1<br/>Status: CAPTURED<br/>TERMINAL]
    PSP1 -->|FAILED| PO1C[PaymentOrder 1<br/>Status: CAPTURE_FAILED<br/>TERMINAL]
    PSP1 -->|TIMEOUT| PO1D[PaymentOrder 1<br/>Status: PENDING_CAPTURE<br/>Retry: 1]
    PO1D -->|Retry| PO1A

    %% PaymentOrder 2 State Machine
    PO2 -->|Enqueued| PO2A[PaymentOrder 2<br/>Status: CAPTURE_REQUESTED<br/>Retry: 0]
    PO2A -->|PSP Capture Call| PSP2{PSP Result}
    PSP2 -->|SUCCESS| PO2B[PaymentOrder 2<br/>Status: CAPTURED<br/>TERMINAL]
    PSP2 -->|FAILED| PO2C[PaymentOrder 2<br/>Status: CAPTURE_FAILED<br/>TERMINAL]
    PSP2 -->|TIMEOUT| PO2D[PaymentOrder 2<br/>Status: PENDING_CAPTURE<br/>Retry: 1]
    PO2D -->|Retry| PO2A

    %% Payment Status Updates
    PO1B -->|Update Payment| P2[Payment<br/>Status: PARTIALLY_CAPTURED<br/>Captured: 1450 EUR]
    PO2B -->|Update Payment| P3[Payment<br/>Status: CAPTURED<br/>Captured: 2900 EUR]

    %% Refund Flow (optional)
    PO1B -.->|Refund Request| PO1E[PaymentOrder 1<br/>Status: REFUNDED]
    PO1E -->|Update Payment| P4[Payment<br/>Status: PARTIALLY_REFUNDED<br/>Refunded: 1450 EUR]

    %% Styling
    style PI1 fill:#e1f5ff,stroke:#1976D2,stroke-width:2px
    style PI2 fill:#fff4e1,stroke:#FF9800,stroke-width:2px
    style PI3 fill:#e1ffe1,stroke:#388E3C,stroke-width:2px
    style PI4 fill:#ffe1e1,stroke:#C62828,stroke-width:2px
    style P1 fill:#f0e1ff,stroke:#8E24AA,stroke-width:2px
    style P2 fill:#f0e1ff,stroke:#8E24AA,stroke-width:2px
    style P3 fill:#e1ffe1,stroke:#388E3C,stroke-width:2px
    style P4 fill:#fff4e1,stroke:#FF9800,stroke-width:2px
    style PO1 fill:#fff4e1,stroke:#FF9800,stroke-width:2px
    style PO1A fill:#fff4e1,stroke:#FF9800,stroke-width:2px
    style PO1B fill:#e1ffe1,stroke:#388E3C,stroke-width:2px
    style PO1C fill:#ffe1e1,stroke:#C62828,stroke-width:2px
    style PO1D fill:#fff4e1,stroke:#FF9800,stroke-width:2px
    style PO1E fill:#ffe1e1,stroke:#C62828,stroke-width:2px
    style PO2 fill:#fff4e1,stroke:#FF9800,stroke-width:2px
    style PO2A fill:#fff4e1,stroke:#FF9800,stroke-width:2px
    style PO2B fill:#e1ffe1,stroke:#388E3C,stroke-width:2px
    style PO2C fill:#ffe1e1,stroke:#C62828,stroke-width:2px
    style PO2D fill:#fff4e1,stroke:#FF9800,stroke-width:2px
    style PSP fill:#ffebee,stroke:#C62828,stroke-width:2px
    style PSP1 fill:#ffebee,stroke:#C62828,stroke-width:2px
    style PSP2 fill:#ffebee,stroke:#C62828,stroke-width:2px
```


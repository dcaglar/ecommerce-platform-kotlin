# 🛒 ecommerce-platform-kotlin

> 💳 **Event-Driven Payments Platform Prototype**
>
> A high-throughput, resilient, Kotlin + Spring Boot system inspired by PSPs like Adyen and Stripe.
> Featuring outbox pattern, double-entry ledger, retries, observability, and Domain-Driven modular design.

---

A **modular**, **event-driven**, and **resilient** eCommerce backend prototype built with **Kotlin + Spring Boot**, demonstrating how to design a high-throughput payment and ledger system using **Domain-Driven Design (DDD)**, **Hexagonal Architecture**, and **exactly-once event flows**.

- A single order may contain products from multiple sellers
- Each seller is paid independently (one `PaymentOrder` per seller)
- Payments flow through a PSP simulation with retries and exponential backoff (equal jitter)
- Successful PSP results trigger **double-entry ledger postings** with full audit trail
- All communication is **decoupled via Kafka** using transactional producers/consumers
- **Observability** (Prometheus + Grafana, ELK Stack) and **fault tolerance** (Outbox pattern, DLQ handling) are built in from day one
- **Account balance aggregation** is planned for near-term implementation

> 🧩 Completed main `payment-service`, `payment-consumers`, and `ledger` flows.  
> 🔨 Currently working on **AccountBalanceConsumer and AccountBalanceCache** - Aggregate balances from ledger entries with Redis caching ([Issue #119](https://github.com/dcaglar/ecommerce-platform-kotlin/issues/119))  
> 🔜 Future modules: `accounting-service`, `wallet-service`, `order`, `shipment`

---

```mermaid
%%{init: {'theme':'default','flowchart':{'curve':'basis','nodeSpacing':70,'rankSpacing':80}}}%%
flowchart TB
    subgraph Client["Client Apps"]
        USER[User / Mobile App]
        MERCHANT[Merchant Portal]
    end

    subgraph API["API Layer"]
        REST[payment-service<br/>REST API<br/>Returns 202 Accepted]
    end

    subgraph PROCESSING["Async Processing"]
        CONSUMERS[payment-consumers<br/>Kafka Workers<br/>6 specialized consumers]
        PSP_CALL[PSP Calls<br/>Payment Gateway Integration]
    end

    subgraph MESSAGING["Event Bus"]
        KAFKA[Kafka<br/>Event-Driven Architecture<br/>Transactional messaging]
    end

    subgraph DATA["Data Layer"]
        DB[(PostgreSQL<br/>Payments, Orders<br/>Outbox, Ledger)]
        REDIS[(Redis<br/>Retry Queue<br/>Cache)]
    end

    subgraph LEDGER["Ledger System"]
        LEDGER_FLOW[Ledger Recording<br/>Double-Entry Accounting<br/>Balanced Debits/Credits]
    end

    subgraph OBS["Observability"]
        PROM[Prometheus<br/>Metrics]
        GRAFANA[Grafana<br/>Dashboards]
        ELK[ELK Stack<br/>Logs + Traces]
    end

    USER -->|1. POST /payments| REST
    MERCHANT -->|Query Balances| REST
    REST -->|2. Save + Outbox| DB
    REST -->|3. 202 Accepted| USER

    DB -->|4. Outbox Polling| KAFKA
    KAFKA -->|5. Payment Events| CONSUMERS
    CONSUMERS -->|6. PSP Integration| PSP_CALL
    PSP_CALL -.->|7. Response| CONSUMERS
    CONSUMERS -->|8. Results| KAFKA
    KAFKA -->|9. Finalized Events| LEDGER_FLOW
    LEDGER_FLOW -->|10. Journal Entries| DB

    CONSUMERS -->|Retry Scheduling| REDIS
    REDIS -->|Retry Events| KAFKA

    REST -.->|Metrics + Logs| PROM
    CONSUMERS -.->|Metrics + Logs| PROM
    PROM --> GRAFANA

    REST -.->|Structured Logs| ELK
    CONSUMERS -.->|Structured Logs| ELK

    style API fill:#e3f2fd,stroke:#1976D2,stroke-width:3px
    style PROCESSING fill:#fff9c4,stroke:#F57F17,stroke-width:3px
    style LEDGER fill:#e8f5e9,stroke:#388E3C,stroke-width:3px
    style DATA fill:#fef7e0,stroke:#FBBC05,stroke-width:3px
    style MESSAGING fill:#f3e8fd,stroke:#A142F4,stroke-width:3px
```

---

## 🚀 Quick Start

For local setup and deployment on Minikube:  
👉 **[docs/how-to-start.md](https://github.com/dcaglar/ecommerce-platform-kotlin/blob/main/docs/how-to-start.md)**

---

## 📚 Documentation

- **[Architecture Guide](./docs/architecture.md)** – System design overview
- **[Architecture Details](./docs/architecture-internal-reader.md)** – Deep implementation guide
- **[How to Start](./docs/how-to-start.md)** – Local setup and Minikube deployment
- **[Folder Structure](./docs/folder-structure.md)** – Module organization and naming conventions

---

## 🗂️ Repository Layout

```bash
ecommerce-platform-kotlin/
├── payment-domain/           # Core domain model, value objects, events
├── payment-application/      # Application services, schedulers, orchestrations
├── payment-infrastructure/   # Adapters (Kafka, Redis, DB, PSP) + auto-config
├── payment-service/          # REST API, Outbox Dispatcher
├── payment-consumers/        # Kafka consumers (Enqueuer, Executor, Ledger, Retry)
├── common/                   # Shared contracts, event envelope, logging
├── charts/                   # Helm charts for deployment
├── infra/                    # Local infra scripts (Minikube, monitoring, Keycloak)
└── docs/                     # Architecture & how-to guides
```

---

## 📊 Observability Highlights

- **Prometheus metrics** for latency, retries, PSP call success rates, and consumer lag
- **Grafana dashboards** preconfigured in `infra/helm-values/`
- **ELK stack integration** for JSON-structured logs, searchable by `traceId`, `eventId`, or `paymentOrderId`
- Example Kibana query:
```kibana
traceId:"abc123" AND eventMeta:"PaymentOrderPspCallRequested"
```

---

## 📄 License

This project is a demonstration/learning prototype.  
See [LICENSE](./LICENSE) for details.

---

**Built with ❤️ using Kotlin, Spring Boot, and Domain-Driven Design.**

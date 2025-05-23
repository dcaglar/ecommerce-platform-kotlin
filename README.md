# 🛍️ ecommerce-platform-kotlin

A modular, event-driven ecommerce platform prototype designed to demonstrate production-grade architectural principles using Kotlin, Spring Boot, and Kafka. Inspired by the needs of high-throughput marketplaces like Amazon or bol.com.

---

## 🧱 Architecture Overview

This project uses:

- **Domain-Driven Design (DDD)** to model the business domain clearly and explicitly
- **Hexagonal Architecture** to decouple domain logic from external technologies
- **Event-Driven Design** with Kafka to enable loose coupling and reactive flows
- **Resilient patterns** like retries, scheduled status checks, and dead letter queues
- **Observability** via structured logging with MDC trace IDs sent to Elasticsearch + Kibana

---

## 🧩 Modules (WIP)

- ✅ `payment-service`: handles full payment lifecycle, retry, and PSP interaction
- ⏳ `order-service`: planned
- ⏳ `wallet-service`: planned
- ⏳ `shipment-service`: planned
- ✅ `common`: shared event envelope, logging, and metadata abstractions

---

## 🔁 Key Event Flow (Payment)

```text
[OrderService] → POST /payments
      ↓
[PaymentController] → [PaymentOrderService] → [PostgreSQL + OutboxEvent]
      ↓
[OutboxDispatcher] → Kafka: payment_order_created_queue
      ↓
[PaymentOrderExecutor] → [PSPClient]
        ├── onSuccess: Kafka → payment_order_success
        ├── onFailure: Redis → payment_retry_queue
        └── onPending: Redis → payment_status_check_queue
```

---

## 📦 Kafka Topics

| Domain Event                   | Kafka Topic                           |
|--------------------------------|----------------------------------------|
| `PaymentOrderCreated`          | `payment_order_created_queue`         |
| `PaymentOrderRetryRequested`   | `payment_order_retry_request_topic`   |
| `PaymentOrderStatusScheduled`  | `payment_status_check_scheduler_topic`|
| `DuePaymentOrderStatusCheck`   | `due_payment_status_check_topic`      |
| `PaymentOrderSucceeded`        | `payment_order_success`               |

---

## 🧠 Observability

- **Structured JSON logs** via `logstash-logback-encoder`
- **MDC-based traceId + parentEventId** for full event traceability
- Logs sent to **Filebeat → Elasticsearch → Kibana**
- Future: Grafana dashboards for retry rates, PSP success, DLQs

---

## ⚙️ Tech Stack

- Kotlin 1.9+, Spring Boot 3.x
- Kafka (event backbone)
- PostgreSQL + Liquibase
- Redis (retry queues)
- Keycloak (OAuth2 Resource Server)
- Docker Compose for local dev
- Micrometer + ELK Stack for observability

---

## 🚀 Getting Started

```bash
git clone https://github.com/dcaglar/ecommerce-platform-kotlin.git
cd ecommerce-platform-kotlin
docker-compose up -d
cd payment-service
./mvnw spring-boot:run
```

---

## 📜 License

MIT — use, fork, and contribute freely.

---


👨‍💻 Developed by **Doğan Çağlar** to demonstrate how to build secure, fault-tolerant, and observable systems using Kotlin + Spring Boot.

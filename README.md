# 🧾 Payment Service
> 📦 This is the `payment-service` module of the [`ecommerce-platform-kotlin`](https://github.com/dcaglar/ecommerce-platform-kotlin) monorepo.
This service is part of the **ecommerce-platform-kotlin** monorepo and is responsible for managing the payment lifecycle using a resilient, event-driven architecture with Domain-Driven Design (DDD) principles.
![Architecture](https://dcaglar.github.io/ecommerce-platform-kotlin/docs/architecture/payment-service/payment_service_architecture.png)
---

## 🧩 Services

📦 **Payment Service** — handles all payment lifecycle operations, retries, and integrations with external PSPs using DDD and event-driven architecture.

---

## 🚀 Responsibilities

- Handle `payment_order_created` events
- Interact with external PSP (sync + async)
- Retry failed payments using Redis
- Defer status checks via Kafka delay queue
- Emit `payment_order_success` events on success

---

## 📬 Kafka Topics & Domain Events


| Domain Event                   | Kafka Topic                         |
|--------------------------------|-------------------------------------|
| `PaymentOrderCreated`          | `payment_order_created_queue`             |
| `PaymentOrderRetryRequested`   | `payment_order_retry_request_topic` |
| `PaymentOrderStatusScheduled ` | `payment_status_check_scheduler_topic`            |
| `DuePaymentOrderStatusCheck`   | `due_payment_status_check_topic`             |
| `PaymentOrderSucceededEvent`   | `payment_order_success`             |

---

## ✨ Dynamic Kafka Consumer Support

This service supports dynamic consumer registration via `application.yml`.  
You can add a new consumer **without modifying any Java/Kotlin code**.

```yaml
kafka:
  dynamic-consumers:
    - id: payment-order-executor
      topic: payment_order_created_queue
      group-id: payment-executor-group
      class-name: com.dogancaglar.paymentservice.kafka.PaymentOrderExecutor
```

Each dynamic consumer must implement a `handle(EventEnvelope<T>)` method.

---

## 📦 Kafka Message Format Example

Topic: `payment_order_created`

```json
{
  "eventId": "uuid-1234",
  "eventType": "payment_order_created",
  "aggregateId": "payment-987",
  "data": {
    "paymentOrderId": "payment-987",
    "sellerId": "seller-123",
    "amountValue": 1000,
    "currency": "EUR"
  },
"parentEventId": "payment-987",
"traceId": "123121"
}
```

---

## 🔐 Security

- OAuth2 Resource Server with Keycloak
- JWT-based authentication

---

## 🧠 Design Highlights

- DDD + Hexagonal Architecture
- Resilient retry mechanism (Redis & Kafka)
- Exponential backoff on failure
- Minimal coupling between consumers and PSP logic

---

## TODO
 - add retrystatus count and retryStatusReason to paymet order
 - Observability: logs include eventId, retries, order status
 - idempotency checks.
 - maybe put paymentOrder,not paymentOrderId in redis queue.since we always check the order
 - redis evict data after they are retried.
 - and payment result is pending,do not schedule for hours later, first checkStatus for 5th and 10th minute other wise schedule for 1 hour later
 -  how to manage kafka queue or increase consumers for same queue for consumer per topic
 - config server
 - externalize configuration based on env
## 🛠 Tech Stack

- Kotlin 1.9+
- Spring Boot 3.x
- Kafka
- Redis
- PostgreSQL + Liquibase
- OAuth2 (Keycloak)
- Micrometer

---

## 🧪 Testing

- Unit testing with JUnit & MockK
- Testcontainers (planned) for Kafka, Redis, PostgreSQL

---

## 🧱 Architecture Diagram

```text
[Kafka: payment_order_created]
       ↓
[PaymentOrderExecutor] ---> [PSPClient] ---> [Kafka: payment_order_success]
       ↓
   [RedisRetryQueue] ---> [Kafka: payment_order_retry] ---> [PaymentRetryExecutor]
```

---

## ⚙️ Getting Started

### Prerequisites

- Java 17+
- Docker
- Maven or IntelliJ

### Local Setup

```bash
git clone https://github.com/yourusername/ecommerce-platform-kotlin.git
cd payment-service
docker-compose up -d
./mvnw spring-boot:run
```

---

## 👨‍💻 Author

Developed with ❤️ by **Doğan Çağlar**  
Designed to showcase production-grade architecture with resilience, clean code, and testability in mind.

---

_Last updated: 2025-05-16_

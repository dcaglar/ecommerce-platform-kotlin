
## 🧩 Services

- [📦 Payment Service](payment-service/README.md) — handles all payment lifecycle operations, retries, and integrations with external PSPs using DDD and event-driven architecture.
---

## 🚀 Responsibilities

- Handle new `payment_order_created` events
- Interact with external PSP (sync + async)
- Retry failed payments using Redis
- Defer status checks via Kafka delay queue
- Emit `payment_order_success` events on success

---

## 🧩 Key Interfaces

| Interface                 | Implementation                   |
|--------------------------|------------------------------------|
| `PaymentOrderRepository` | `JpaPaymentOrderAdapter`           |
| `RetryQueuePort`         | `RedisRetryQueue`, Kafka Delay     |
| `PSPClient`              | `MockPspClient`, Stripe adapter    |
| `PaymentEventPublisher`  | `KafkaEventPublisher`              |

---

## 📬 Kafka Topics

| Domain Event                        | Kafka Topic                   |
|-------------------------------------|-------------------------------|
| PaymentOrderCreated                 | `payment_order_created`       |
| PaymentOrderRetryRequested          | `payment_order_retry`         |
| PaymentOrderStatusCheckRequested    | `payment_status_check`        |
| PaymentOrderSucceededEvent          | `payment_order_success`       |

---

## 🔐 Security

- OAuth2 Resource Server using Keycloak
- JWT-based authentication for REST APIs

---

## 🧠 Design Highlights

- DDD + Hexagonal Architecture
- Resilient retry mechanism (Redis & Kafka)
- Observability: logs include `eventId`, retries, order status
- Exponential backoff on failure
- Minimal coupling between consumers and PSP logic

---

## 🛠 Technologies

- Kotlin 1.9+
- Spring Boot 3.x
- Kafka
- Redis
- PostgreSQL + Liquibase
- Temporal Workflows
- OAuth2 (Keycloak)
- Micrometer

---

## 🧪 Testing

- JUnit & MockK for unit tests
- Testcontainers planned for Kafka, Redis, and PostgreSQL

---

## 👨‍💻 Author

Developed by **Doğan Çağlar**  
Designed to showcase production-level microservice architecture with a focus on **resilience**, **observability**, and **clean code**.

---

## 🧱 Architecture Summary
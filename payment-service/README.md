# Payment Platform (Java + Spring Boot)

This project is a **modular, secure, event-driven microservice** built with **Spring Boot**, **Java**, **Kafka**, **Liquibase**, **PostgreSQL**, and **Keycloak** for OAuth2-based authentication.

---

## ✅ Functional Summary

### `payment-service`
- `POST /payments`
    - Authenticated via Keycloak (JWT)
    - Accepts a payment request with multiple paymentOrder from different seller, and send those requests ascyncronosly to queue
    - Outbox Pattern and Hexa architecture is used
    - Paymentflow starts with calling create method in Controller, and payment service immediately persists payment and payment orders and also serialized OrderCreatedEventoutboxevents in Transactional Block
    - OutboxDispatcherService retrieves and push  EnvelopeWrap<WPAYMENTORDERREATED> events  to payment_order_created topic with via
    - Then PAymentOrderExecutor indepently consumes from this queue and call PSP, this way we  are not blocking shopper,if payment fails or get timeout our exception,we emit payment_retry_event to another queue
      Use a Redis sorted set to delay actual Kafka re-send based on retry count

### `OutboxEvent`
- Stores serialized  and paymentorderevent
- To be processed by a Kafka publisher (in a scheduled background job)
- 

### `Validation & Error Handling`
- DTOs are validated with annotations (`@NotBlank`, `@Email`)
- Global exception handler returns structured JSON errors
- `Location: /customers/{id}` header is included in creation responses

---

## 🚀 Getting Started

### Prerequisites
- Kotlin
- JDK 17+
- Maven
- Docker + Docker Compose
- IntelliJ IDEA (recommended)
- Reddis
- Postgre


## 🚀 Docker redis connect
	•	Use a Redis sorted set or in-memory scheduler to delay actual Kafka re-send based on retry count

### 🔧 Setup Steps

```bash
# 1. Clone the repo
$ git clone https://github.com/yourname/customer-platform
$ cd customer-platform

# 2. Start PostgreSQL, Kafka, Zookeeper, Keycloak
$ docker-compose up -d

Keycloak
docker run -d --name keycloak \
  -p 8082:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:24.0.3 \
  start-dev
  
  
  Step 2: Log in to Keycloak Admin Console

Open: http://localhost:8081
Log in with:
	•	Username: admin
	•	Password: admin

⸻

🔧 Step 3: Create a Realm
	1.	Click the Realm dropdown (top left) → Create Realm
	2.	Name it: customer-platform

⸻

🔧 Step 4: Create a Client
	1.	Go to Clients → Create Client
	2.	Name: customer-service
	3.	Client type: Bearer-only
	4.	Save

⸻

🔧 Step 5: Create a Role
	1.	Go to Roles → Add Role
	2.	Name: customer:write
	3.	Save

⸻

🔧 Step 6: Create a User
	1.	Go to Users → Add User
	2.	Username: dogan
	3.	Set a password:
	•	Click Credentials
	•	Set password to password, toggle Temporary = OFF
	•	Click Set Password
	4.	Assign role:
	•	Go to Role Mappings
	•	Select customer:write and click Add selected


# 3. Run DB migrations via Liquibase (done automatically)
# OR apply manually by connecting to the DB if needed

# 4. Start the Spring Boot app
$ ./gradlew :customer-service:bootRun
```

---

## 🐳 Docker Compose Services

- PostgreSQL (port 5432)
- Kafka (port 9092) + Zookeeper (2181)
- Keycloak (port 8082)

### 🗝️ Keycloak Setup
1. Visit: `http://localhost:8081`
2. Create Realm: `ecommerce-platform`
3. Create Client: `payment-service`
    - Type: `confidential`
    - Auth flow: `standard` with Direct Access Grants enabled
    - Add `payment:write` role under client
    - 
4. Create User:
    - Username: dogan / password: password
    - Assign `payment:write` role
    - Ensure `email_verified = true`

### 🧪 Get Token
```bash
  curl -X POST http://localhost:8082/realms/ecommerce-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=payment-service" \
  -d "client_secret=OvX9LSkmwLr1ewOV5X1k5JUsSH7R7HxE" \
  -d "grant_type=password" \
  -d "username=dogan" \
  -d "password=dogan"
  
  
  curl -i POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJHNWVXMkZrZXJOWk85MU5NQlE1dk5rQXcyNFVIT09NbHBJUzJrMDFKSnZJIn0.eyJleHAiOjE3NDcyMTcxNzksImlhdCI6MTc0NzE4MTE3OSwianRpIjoiZGQ3NTEzZTQtM2FmOC00YjRmLWIwMjktNGY0NDc2ZTNjMmM2IiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDgyL3JlYWxtcy9lY29tbWVyY2UtcGxhdGZvcm0iLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiODAwMmEyMGMtMTRhZi00N2NmLWEzOTQtMjYzODIzZTIwNDk5IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoicGF5bWVudC1zZXJ2aWNlIiwic2Vzc2lvbl9zdGF0ZSI6IjA4ZTdmYmVlLTM4OTgtNGZmNC1iNDhiLTk3YWYyMDVmZjcyZiIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiLyoiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbInBheW1lbnQ6d3JpdGUiLCJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZXMtZWNvbW1lcmNlLXBsYXRmb3JtIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6ImVtYWlsIHByb2ZpbGUiLCJzaWQiOiIwOGU3ZmJlZS0zODk4LTRmZjQtYjQ4Yi05N2FmMjA1ZmY3MmYiLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwibmFtZSI6IkRvZ2FuIENhZ2xhciIsInByZWZlcnJlZF91c2VybmFtZSI6ImRvZ2FuIiwiZ2l2ZW5fbmFtZSI6IkRvZ2FuIiwiZmFtaWx5X25hbWUiOiJDYWdsYXIiLCJlbWFpbCI6ImRjYWdsYXIxOTg3QGdtYWlsLmNvbSJ9.dlQemSwXd3GK8aYes8_fEBSDeymbXnBticpawD-1lkFjccu9cvV-VoilU_iyLzVvVodvYHZTwJunRBANR7S7MoL-FS9X12dsryf036h-D3Pi2AyDSziDWItb2joclw41Vn1HAQFKKh3HqPJlc78ezCJNhrhWsGAED2I3Qcz-Wa8j1THzZgGmTPef5wK8dLGOAISAVvLB_m9XwncP6zpXN8V13-jptO2k6lYiOoysJjnOYFSF5YJpKLqZa1brXSyxrdodfZp8ViX-a6CConTUThe_CK8vWsHXRWVYmGiJ7vLDHMH9IUVCZd8NQoEyJ-3CsUW4Vxfg-PvYcN1UpSPW9w" \
  -d '{
  "orderId": "ORDER-20240508-XYZ",
  "buyerId": "BUYER-123",
  "totalAmount": {
    "value": 199.49,
    "currency": "EUR"
  },
  "paymentOrders": [
    {
      "sellerId": "SELLER-001",
      "amount": {
        "value": 49.99,
        "currency": "EUR"
      }
    },
    {
      "sellerId": "SELLER-002",
      "amount": {
        "value": 29.50,
        "currency": "EUR"
      }
    },
    {
      "sellerId": "SELLER-003",
      "amount": {
        "value": 120.00,
        "currency": "EUR"
      }
    }
  ]
}'
  
  

  
```
eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJHNWVXMkZrZXJOWk85MU5NQlE1dk5rQXcyNFVIT09NbHBJUzJrMDFKSnZJIn0.eyJleHAiOjE3NDcxMjMyOTgsImlhdCI6MTc0NzA4NzI5OCwianRpIjoiNjYzYThmYjEtMTNiNi00Zjc1LWJhY2EtODllNWEyZDMzNTg4IiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDgyL3JlYWxtcy9lY29tbWVyY2UtcGxhdGZvcm0iLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiODAwMmEyMGMtMTRhZi00N2NmLWEzOTQtMjYzODIzZTIwNDk5IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoicGF5bWVudC1zZXJ2aWNlIiwic2Vzc2lvbl9zdGF0ZSI6IjFjMDFmNWQ4LTk5OTEtNDAxYi1hMmFhLWQwM2JlZGYzNmMyNyIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiLyoiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbInBheW1lbnQ6d3JpdGUiLCJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZXMtZWNvbW1lcmNlLXBsYXRmb3JtIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6ImVtYWlsIHByb2ZpbGUiLCJzaWQiOiIxYzAxZjVkOC05OTkxLTQwMWItYTJhYS1kMDNiZWRmMzZjMjciLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwibmFtZSI6IkRvZ2FuIENhZ2xhciIsInByZWZlcnJlZF91c2VybmFtZSI6ImRvZ2FuIiwiZ2l2ZW5fbmFtZSI6IkRvZ2FuIiwiZmFtaWx5X25hbWUiOiJDYWdsYXIiLCJlbWFpbCI6ImRjYWdsYXIxOTg3QGdtYWlsLmNvbSJ9.1wV0YH2d_8Ms1kW-3VSy_5VN3QHQfrz2fhwjf7YJuBmu7YtGXZQN8h4hLIBHeCJVREJJjR5SSsBjjbT7Rj_T8_q-2Mbou8T166tYnvLC-E3DtGO88zyMfxayYu90EwGwRQM32F822kBEumpFxAwv07F7B_SCbEPbU4bE2LxfXlUOKdtR9YJfn2eOwA73dJblE15NZBUBBECDyoFs0u1YBlK30xoM1naM_G32kI0pNWdwqWIlTzUDiLcE7AzSCPbISmfOu6qKYDp4_AD8zzDNlhiiEttz1oOwaii1JA3FjgYJQR9XA6G9h6q1Kw9bvtkICq3DypgdqtxGQwMgfddvBg
---

## 📦 Connect kafka and see the consumer
if you want to see currentofset and last offset
```bash
kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group payment-status-check-executor-group
  
  
  kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group payment-order-group
  
    kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group payment-retry-executor-group
  
      kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group payment-status-executor-group
  
```
if you want to skip corrupt messages
```bash

kafka-consumer-groups \
--bootstrap-server localhost:9092 \
--group payment-order-group \
--topic payment_order_created \
--reset-offsets --to-latest --execute

kafka-consumer-groups \
--bootstrap-server localhost:9092 \
--group payment-retry-executor-group \
--topic payment_order_retry_request_topic \
--reset-offsets --to-latest --execute


kafka-consumer-groups \
--bootstrap-server localhost:9092 \
--group payment-status-executor-group \
--topic scheduled_status_check \
--reset-offsets --to-latest --execute



kafka-consumer-groups \
--bootstrap-server localhost:9092 \
--group payment-status-check-executor-group \
--topic payment_status_check \
--reset-offsets --to-latest --execute
```
Returns:

DATABASE ACCESSE
This connects inside the running payment-postgres container and opens a psql session using the payment user and database without having anything postgre
docker exec -it payment-postgres psql -U payment -d payment

```

---

## 📋 What’s Done

- ✅ Kotlin multi-module setup (Maven)
- ✅ PostgreSQL with Liquibase migrations
- ✅ Domain + DTO separation (records)
- ✅ OAuth2 integration with Keycloak

---

## ⏭️ Next Steps / TODOs

-Add liquibase scripts

- [ ] 🔁 Implement Kafka OutboxPublisher (scheduled job)
- Separate DTO and Domain and Entity se[eration]
- [ ] 🪦 Add Dead Letter Topic support
- [ ] 🧪 Add integration tests with Testcontainers
- performance test
-  Domain + DTO separation (records)
- ✅ Validations + Global error handler
- - ✅ Outbox pattern for event storage


---

## 👨‍💻 Author
Doğan Çağlar


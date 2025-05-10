x# Payment Platform (Java + Spring Boot)

This project is a **modular, secure, event-driven microservice** built with **Spring Boot**, **Java**, **Kafka**, **Liquibase**, **PostgreSQL**, and **Keycloak** for OAuth2-based authentication.

---

## ✅ Functional Summary

### `payment-service`
- `POST /payments`
    - Authenticated via Keycloak (JWT)
    - Accepts a payment reqiest
    - Emits events
s
### `OutboxEvent`
- Stores serialized customer creation events
- To be processed by a Kafka publisher (in a scheduled background job)

### `Validation & Error Handling`
- DTOs are validated with annotations (`@NotBlank`, `@Email`)
- Global exception handler returns structured JSON errors
- `Location: /customers/{id}` header is included in creation responses

---

## 🚀 Getting Started

### Prerequisites
- JDK 17+
- Maven
- Docker + Docker Compose
- IntelliJ IDEA (recommended)

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
 curl -X POST http://localhost:8082/realms/ecommerce-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=payment-service" \
  -d "client_secret=OvX9LSkmwLr1ewOV5X1k5JUsSH7R7HxE" \
  -d "grant_type=password" \
  -d "username=dogan" \
  -d "password=dogan"
  
  
  
  curl -i POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJHNWVXMkZrZXJOWk85MU5NQlE1dk5rQXcyNFVIT09NbHBJUzJrMDFKSnZJIn0.eyJleHAiOjE3NDY4NTAwMjYsImlhdCI6MTc0NjgxNDAyNiwianRpIjoiNGZlMDU1YWYtZTZkMy00MjhlLWJiZmQtMjQ4NjI5ZGMxOGY4IiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDgyL3JlYWxtcy9lY29tbWVyY2UtcGxhdGZvcm0iLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiODAwMmEyMGMtMTRhZi00N2NmLWEzOTQtMjYzODIzZTIwNDk5IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoicGF5bWVudC1zZXJ2aWNlIiwic2Vzc2lvbl9zdGF0ZSI6IjZmMTBjMjk4LTlmZDAtNDY5Ni1iMWM0LWE4NTgwZTBiNWE4ZiIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiLyoiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbInBheW1lbnQ6d3JpdGUiLCJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZXMtZWNvbW1lcmNlLXBsYXRmb3JtIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6ImVtYWlsIHByb2ZpbGUiLCJzaWQiOiI2ZjEwYzI5OC05ZmQwLTQ2OTYtYjFjNC1hODU4MGUwYjVhOGYiLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwibmFtZSI6IkRvZ2FuIENhZ2xhciIsInByZWZlcnJlZF91c2VybmFtZSI6ImRvZ2FuIiwiZ2l2ZW5fbmFtZSI6IkRvZ2FuIiwiZmFtaWx5X25hbWUiOiJDYWdsYXIiLCJlbWFpbCI6ImRjYWdsYXIxOTg3QGdtYWlsLmNvbSJ9.eyrTRQu959qGAiCcIRWuDrucVY8ooos4nZbiq__6-An5YrrJfbGxBqjZtiQuvVwKqRc4KPTip7FSOlV3DQ5XgPyTShDlWpSx2jXUyjSzSk-bhfdXNiliEfS-OB-__zOmQyBUSkY0U3ah3sFuraRb1ullhzJ4VPqArEePKuT3GHcv08ShXCowPPaeZ9rO4PQn2zyP6FEFP1y4gfTX--DnxWEu1IR6u4tCzldk20ij1ucEfH29rnwdKcFeloJ4FeAcsN57cg0P1IDI3ZzJPYCJE2RXwj-8Mt1hzEHo5LEkZf7ngOnZV55Alsp4uNSRYffwl8xZcP58LoJ6Isdpezwlmw" \
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
4. Create User:
    - Username: dogan / password: password
    - Assign `payment:write` role
    - Ensure `email_verified = true`

### 🧪 Get Token
```bash
 curl -X POST http://localhost:8082/realms/ecommerce-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=payment-service" \
  -d "client_secret=rCdBBSxTe7w6P5Ts7CssmvVX37YM9wkf" \
  -d "grant_type=password" \
  -d "username=dogan" \
  -d "password=password"
```

---

## 📦 Example API Request

```bash
curl -X POST http://localhost:8080/customers \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{
        "firstName": "Ada",
        "lastName": "Lovelace",
        "email": "ada@example.com"
      }'
```

Returns:
```json
HTTP/1.1 201 Created
Location: /customers/{uuid}
{
  "id": "...",
  "firstName": "Ada",
  "lastName": "Lovelace",
  "email": "ada@example.com",
  "status": "PENDING"
}

DATABASE ACCESS
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


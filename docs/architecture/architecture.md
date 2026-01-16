# 🟦 Event-Driven Payments & Ledger Infrastructure for Multi-Seller Platforms

This project represents a backend **payment platform for a Merchant-of-Record (MoR) environment**.  
Think of a multi-seller e-commerce marketplace where shoppers can buy items from different sellers in a single checkout.  
The platform manages the **full payment lifecycle**: synchronous authorization, multi-seller decomposition, seller-level operations, and internal financial accounting.

---

# 🟩 Key Clarifications (MoR Model)

### **1. Is the payment platform internal?**
Yes. The payment platform is an **internal backend domain service**, not exposed to shoppers directly.  
Checkout / Order Service calls it to create payments, decompose them into seller-specific PaymentOrders, and initiate payment authorization.

---

### **2. Do we perform authorization ourselves?**
No. We delegate authorization, capture, refund, and cancel operations to an external PSP via our gateway.  
From the PSP’s perspective, we appear as a **single merchant-of-record**; seller details remain internal.

---

### **3. Do we distribute funds to sellers internally?**
Yes. As the MoR, the platform manages all **fund allocation**, applies platform fees, credits seller balances, and schedules payouts.  
The PSP simply transfers funds into the MoR account.

---

### **4. Why authorize once but capture/refund per PaymentOrder?**
Authorization happens **once for the entire basket**, matching shopper intent.  
Captures, cancels, and refunds happen **per PaymentOrder**, since each seller’s fulfillment lifecycle is independent.

### **5. Why do we have payment intent, and payment seperately**

Separating PaymentIntent from Payment lets our system handle user interaction safely,money movement correctly,retry safely.


# 🟧 Functional Requirements
*(written using Shopper, Seller, and Internal Services as actors)*

## **For Shoppers**

### **FR1 — Shoppers should be able to make a payment for a multi-seller basket.**
- A shopper must be able to confirm checkout and initiate a payment for the total amount of their order.

### **FR2 — Shoppers should be able to see accurate payment authorization status.**
- Shoppers should be able to view whether their payment is authorized or declined

---

## **For Sellers**

### **FR3 — Sellers should be able to receive their portion of a shopper’s payment.**
- Each seller must receive the correctly allocated share of the total payment based on the items purchased from them.

### **FR4 — Sellers should be able to view their financial state.**
- Sellers should be able to access their balances, payable amounts, and payout summaries.

---

## **For Internal Services (Checkout / Order / Finance / Payouts)**

### **FR5 — Checkout/Order Service should be able to create a Payment.**
- It must be possible for the Order Service to create a Payment and obtain the generated Payment along with its seller-level PaymentOrders.

### **FR6 — Checkout/Order Service should be able to trigger authorization via PSP.**
- The system must allow Checkout to authorize the total payment amount through an external PSP.

### **FR7 — Internal services should be able to perform seller-level operations.**
- Internal services must be able to request captures, cancellations, and refunds *per PaymentOrder*.

### **FR8 — The system must maintain internal fund distribution for reporting and payouts.**
- Internal components (Finance, Payouts) must be able to retrieve seller payables, platform fees, and other financial allocations.

### **FR9 — Internal services should be able to retrieve real-time payment and ledger state.**
- Order, Finance, Risk, and Payout subsystems must be able to query payment status, PSP results, seller balances, and ledger entries.

### **FR10 — Treasury/Payout services should be able to receive payout instructions.**
- Out of Scope

---

# 🟥 Non-Functional Requirements
*(written using “The system should be…” statements)*

### **NFR1 — The system should be highly available.**
Payment creation and authorization must remain available during peak checkout traffic.

### **NFR2 — The system should ensure strong consistency for financial data.**
State transitions must never lead to incorrect balances or double charges.

### **NFR3 — The system should be secure.**
Sensitive financial data must be protected using proper authentication, authorization, and encryption.

### **NFR4 — The system should be observable.**
Logs, metrics, and tracing must allow operators to understand system behavior and diagnose issues.

### **NFR5 — The system should be scalable.**
It must support increasing transaction volumes, sellers, and asynchronous workflows without degradation.

### **NFR6 — The system must be correct under retries and failures.**
Even under retries, restarts, and network issues, financial outcomes must remain correct.

---

# 🟦 Architecture Summary (Non-Functional / Implementation Section)

The platform internally uses:
- **Event-driven architecture** for asynchronous flows for payment order and ledger
- **Kafka topics** for PaymentOrder creation, PSP calls, and ledger events
- **Idempotent state transitions** to ensure correctness under retries
- **Double-entry ledger** for immutable financial history
- **PSP gateway client** for authorization, capture, refund, and cancel operations
- **Internal balance tracking** for seller payables and platform revenues

---

🟦 Core Entities (Domain-Level)

These represent the nouns your system uses to satisfy the functional requirements.  
They define the **data model**, the **API vocabulary**, and the **business language** of the Merchant-of-Record payment platform.

---

## 🧍 Actors

### **Shopper**
The end-user making a purchase across one or multiple sellers.

### **Seller**
A marketplace participant who receives part of the shopper’s payment and later receives payouts.

### **Internal Services**
- Checkout / Order Service
- Finance

These actors perform operations on payments, orders, balances, and payouts.

---

# 🟩 Core Business Entities
These are the fundamental nouns of out Merchant-of-Record payment platform.

---

## **1. PaymentIntent**

Represents the **shopper's intent to pay** for a multi-seller basket.

**When it's created:**
- Step 1: Shopper initiates checkout → `POST /api/v1/payments` endpoint
- Created with status `CREATED_PENDING` (initially), then transitions to `CREATED` once Stripe ID is obtained.
- Contains: `buyerId`, `orderId`, `totalAmount`, `paymentOrderLines` (seller breakdown)

**Why it exists:**
- Separates "intent to pay" from "actual payment transaction"
- Allows authorization workflow to happen before committing to financial records
- Enables idempotent authorization attempts (prevents duplicate PSP calls)
- Supports retry logic for transient PSP failures


## **2. Payment**

Represents the **actual financial transaction** created after authorization succeeds

**When it's created:**
- when psp authorization response is authorized.

**Why it exists:**
- Models actual money movement (vs. PaymentIntent which is just "intent")
- Tracks aggregate-level financial state across all sellers
- Provides aggregate view: total captured, total refunded
- Links to `PaymentIntent` via `paymentIntentId` for traceability
- Enables financial reporting and reconciliation



## **3. PaymentOrder**
Represents the **per-seller financial component** of a Payment.

**When it's created:**
  When `Payment` is created
- One `PaymentOrder` per seller (from `Payment.paymentOrderLines`)
- Initial status: `INITIATED_PENDING`

**Why it exists:**
- Each seller has independent fulfillment lifecycle
- Sellers can be captured, refunded, or cancelled independently
- Enables per-seller financial tracking and payouts
- Supports retry logic per seller (if one seller's capture fails, others continue)
- Maps directly to seller-level accounting entries

---

## **4. LedgerEntry**

Represents a **single double-entry journal entry**.

Contains:
- Debit postings
- Credit postings
- JournalId
- Timestamp
- Business context (paymentOrderId, sellerId, etc.)

**Why it exists:**  
Ensures financial correctness, auditability, and immutable accounting history.

---

## **5. Posting (Debit / Credit)**

A component of a LedgerEntry.

- Refers to an account
- Contains a signed amount
- Reflects accounting direction (DR/CR)

**Why it exists:**  
LedgerEntries consist of multiple postings — always balanced.

---

## **6. Account**

Represents a **financial account** in the internal ledger, such as:

- PSP_RECEIVABLE
- SELLER_PAYABLE
- PLATFORM_FEE_REVENUE
- SCHEME_FEE_EXPENSE
- PLATFORM_CASH

**Why it exists:**  
Money moves internally between accounts, not as free-form variables.

---

## **7. Balance**

Represents the **current financial standing** of an account (e.g., seller’s accrued revenue).  
Derived from applied LedgerEntries.

**Why it exists:**  
Used for reporting, analytics, payouts, and consistency validation.




# 🟩 C4 Architecture Diagrams


This document contains C4 model diagrams for the payment service system at different levels of abstraction.

## Level 1: System Context Diagram

The System Context diagram shows the payment service system in its environment, illustrating users and external systems it interacts with.

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

### System Context Description

**Payment Platform** is an internal backend domain service that manages the complete payment lifecycle for a multi-seller marketplace platform. It operates as a Merchant-of-Record (MoR), handling all financial transactions between shoppers, sellers, and the platform. The platform consists of two main applications:

- **Payment Service**: REST API application that handles synchronous operations (payment intent creation, authorization, queries)
- **Payment Consumers**: Kafka consumer application that handles asynchronous operations (capture operations, event processing, retry logic)


### End to End payment flow

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

### Authorization/Idempotency Sequence Diagram

```mermaid
sequenceDiagram
    autonumber

    actor Shopper

    participant Browser as Shopper's Browser<br/>(React App @ :3000)
    participant Proxy as Backend Proxy<br/>(Node.js @ :3001)
    participant Keycloak
    participant PaymentSvc as payment-service<br/>(REST API)
    participant IdemSvc as IdempotencyService
    participant Stripe

    %% Step 1: Create Payment Intent
    Note over Shopper, Stripe: Phase 1: Create Payment Intent & Prepare Checkout Form

    Shopper->>Browser: Fills cart details, clicks "Proceed to Checkout"
    Browser->>Proxy: POST /api/checkout/process-payment<br/>(with cart data & Idempotency-Key)
    
    Proxy->>Keycloak: Request service token (client_credentials)
    Keycloak-->>Proxy: Return JWT Access Token

    Proxy->>PaymentSvc: POST /api/v1/payments<br/>(with JWT & Idempotency-Key)
    
    %% --- IDEMPOTENCY FLOW ---
    PaymentSvc->>IdemSvc: checkKey(idempotencyKey)
    alt First Request (Key is new)
        IdemSvc->>PaymentSvc: Proceed
        PaymentSvc->>PaymentSvc: Create PaymentIntent (status=CREATED_PENDING)
        note right of PaymentSvc: DB: INSERT payment_intents
        
        par Async Stripe Call
            PaymentSvc->>Stripe: Create PaymentIntent (API Call)
        and Wait for Result
            PaymentSvc->>PaymentSvc: Wait up to 3 seconds
        end

        alt Stripe Responds < 3s
            Stripe-->>PaymentSvc: Return { id, clientSecret }
            PaymentSvc->>PaymentSvc: Update PaymentIntent (status=CREATED)
            PaymentSvc->>IdemSvc: storeResponse(key, response)
            PaymentSvc-->>Proxy: 201 Created<br/>{ paymentIntentId, clientSecret }
        else Timeout (> 3s)
            PaymentSvc-->>Proxy: 202 Accepted (Retry-After: 2s)<br/>{ paymentIntentId, clientSecret: null }
            
            Note over Proxy, PaymentSvc: Client enters polling loop
            
            loop Polling
                Proxy->>PaymentSvc: GET /payments/{id}
                PaymentSvc-->>Proxy: 200 OK { ... }
            end

            Note right of PaymentSvc: Background Thread
            Stripe-->>PaymentSvc: Return { id, clientSecret } (Delayed)
            PaymentSvc->>PaymentSvc: Update PaymentIntent (status=CREATED)
        end

    else Retry (Key already processed)
        IdemSvc->>PaymentSvc: Return stored response
        note right of IdemSvc: DB: SELECT response FROM idempotency_keys
        PaymentSvc-->>Proxy: 200 OK (Replayed)<br/>{ paymentIntentId, clientSecret }
    end
    %% --- END IDEMPOTENCY FLOW ---

    Proxy-->>Browser: Return { clientSecret }

    %% Step 2: Collect Card Details via Stripe Element
    Note over Shopper, Stripe: Phase 2: Securely Collect Card Details

    Browser->>Stripe: Stripe.js initializes Payment Element using clientSecret
    Stripe-->>Browser: Renders secure card input form (iframe)
    
    Shopper->>Browser: Enters card details into Stripe's form
    Note right of Shopper: Card data goes directly to Stripe,<br/>never touching any of our servers.

    %% Step 3: Confirm Payment with Stripe and Authorize Internally
    Note over Shopper, Stripe: Phase 3: Confirm Payment & Finalize State

    Shopper->>Browser: Clicks "Pay Now"
    Browser->>Stripe: stripe.confirmPayment()
    Stripe-->>Browser: Payment successfully confirmed by Stripe

    Browser->>Proxy: POST /api/checkout/authorize-payment/{paymentId}
    
    Proxy->>Keycloak: Request service token (can be cached)
    Keycloak-->>Proxy: Return JWT Access Token

    Proxy->>PaymentSvc: POST /api/v1/payments/{paymentId}/authorize
    
    rect rgb(230, 240, 255)
        note over PaymentSvc: @Transactional
        PaymentSvc->>PaymentSvc: Update PaymentIntent status to AUTHORIZED
        PaymentSvc->>PaymentSvc: Create Payment & PaymentOrder entities
        PaymentSvc->>PaymentSvc: Save PaymentAuthorizedEvent to Outbox table
    end

    PaymentSvc-->>Proxy: 200 OK { status: 'AUTHORIZED' }
    Proxy-->>Browser: Return final success status
    Browser->>Shopper: Display "Payment Successful" message
```

#### Ledger Record Sequence Flow

```mermaid
sequenceDiagram
    participant Kafka
    participant Dispatcher as LedgerRecordingRequestDispatcher
    participant Command as LedgerRecordingCommand
    participant Consumer as LedgerRecordingConsumer
    participant Service as RecordLedgerEntriesService
    participant LedgerDB as Ledger Table

    Kafka->>Dispatcher: Consume PaymentOrderFinalized
    Dispatcher->>Kafka: Publish LedgerRecordingCommand
    Kafka->>Consumer: Consume LedgerRecordingCommand
    Consumer->>Service: recordLedgerEntries()
    Service->>LedgerDB: Append JournalEntries
    Service->>Kafka: Publish LedgerEntriesRecorded
```

#### Balance Flow Sequence


```mermaid
sequenceDiagram
    participant Ledger as LedgerRecordingConsumer
    participant Kafka as ledger_entries_recorded_topic
    participant Consumer as AccountBalanceConsumer
    participant Service as AccountBalanceService
    participant Redis as Redis (Deltas)
    participant Job as AccountBalanceSnapshotJob
    participant DB as PostgreSQL (Snapshots)

    Ledger->>Kafka: Publish LedgerEntriesRecorded (sellerId key)
    Kafka->>Consumer: Consume batch (100-500 events)
    Consumer->>Service: updateAccountBalancesBatch(ledgerEntries)
    Service->>Service: Extract postings, compute signed amounts per account
    Service->>DB: Load current snapshots (batch query: findByAccountCodes)
    Service->>Service: Filter postings by watermark (ledgerEntryId > lastAppliedEntryId)
    Service->>Service: Compute delta = sum(signed_amounts) for filtered postings
    Service->>Redis: addDeltaAndWatermark (Lua: HINCRBY delta + HSET watermark + SADD dirty)
    Note over Redis: TTL set on hash (5 min), dirty set marked
    
    Note over Job: Every 1 minute (configurable)
    Job->>Redis: getDirtyAccounts() (reads from dirty set)
    loop For each dirty account
        Job->>Redis: getAndResetDeltaWithWatermark (Lua: HGET delta+watermark, then HSET delta=0)
        alt Delta != 0
            Job->>DB: Load current snapshot (or create default)
            Job->>Service: Compute: newBalance = snapshot.balance + delta
            Job->>Service: Compute: newWatermark = maxOf(current.lastAppliedEntryId, upToEntryId)
            Job->>DB: saveSnapshot (UPSERT with WHERE watermark guard)
            Note over DB: Only updates if new watermark > current watermark
        end
    end
```


## 🟦 Outbox Pattern Implementation

The system uses the **Transactional Outbox Pattern** to ensure reliable event publishing. When payment-related entities are created or updated within a database transaction, corresponding events are written to an `outbox_events` table atomically. The `OutboxDispatcherJob` then publishes these events to Kafka asynchronously.

### **OutboxDispatcherJob**

The `OutboxDispatcherJob` is a scheduled service that runs in the `payment-service` application and is responsible for reliably dispatching outbox events to Kafka.

**Key Responsibilities:**
- **Claims batches of outbox events** from the database (status: `NEW`)
- **Publishes events to Kafka** based on event type:
    - `payment_authorized` → Publishes `PaymentAuthorized` event to Kafka
    - `payment_order_created` → Publishes `PaymentOrderCreated` event to Kafka
- **Updates event status** to `SENT` after successful publication
- **Handles failures** by unclaiming failed events for retry
- **Reclaims stuck events** that were claimed but never completed (runs every 2 minutes)

**Operational Characteristics:**
- **Scheduled execution**: Runs every 5 seconds with configurable thread count (default: 2 workers)
- **Batch processing**: Processes configurable batch size (default: 250 events per batch)
- **Idempotent publishing**: Uses Kafka transactions to ensure exactly-once delivery
- **Metrics tracking**: Monitors backlog size, dispatch duration, success/failure counts
- **Backlog monitoring**: Maintains in-memory backlog counter, resyncs from database periodically (every 5 minutes)

**Workflow:**
1. **Claim**: Selects up to N `NEW` events and marks them as `PROCESSING` (atomically)
2. **Publish**: Deserializes event payloads and publishes to Kafka using atomic transactions
3. **Persist**: Updates successfully published events to `SENT` status
4. **Unclaim**: Returns failed events to `NEW` status for retry
5. **Reclaim**: Background job resets events stuck in `PROCESSING` status (older than 10 minutes)

**Why This Pattern:**
- **Guarantees at-least-once delivery**: Events are never lost even if Kafka is temporarily unavailable
- **Database consistency**: Event creation is part of the same transaction as domain changes
- **Decouples publishing**: Main request path doesn't wait for Kafka availability
- **Retry safety**: Failed publications are automatically retried without duplicate processing

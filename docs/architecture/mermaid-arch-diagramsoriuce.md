payment platform context diafram
```mermaid
graph TD
    %% Styles
    classDef edgeCell fill:#fff0f0,stroke:#ffbaba,stroke-width:2px,color:#333
    classDef internalHost fill:#f0f8ff,stroke:#baddff,stroke-width:2px,color:#333
    classDef db fill:#e2f0d9,stroke:#70ad47,stroke-width:2px,color:#333
    classDef service fill:#fff2cc,stroke:#ffc000,stroke-width:2px,color:#333
    classDef job fill:#e1dfdd,stroke:#a6a6a6,stroke-width:2px,color:#333
    classDef topic fill:#e8d1ff,stroke:#b160ff,stroke-width:2px,color:#333
    classDef consumer fill:#ffe6cc,stroke:#f4b183,stroke-width:2px,color:#333

    subgraph ExternalLayer["EXTERNAL HOSTS (Edge Layer)"]
        direction LR
        
        subgraph Edge1["Edge Cell 1"]
            direction TB
            API1["Payment Acceptance Service"]
            IdemDB1[("Idempotency DB<br/>IdempotencyRecord")]
            EdgeDB1[("Edge Local DB<br/>PaymentIntent<br/>OutboxEvents")]
            Fwd1[["LocalOutboxStoreAndForwardJob"]]
            PSP_Edge1["External PSP API<br/>(Synchronous Auth)"]
            
            %% Flow Splits inside Edge
            API1 -.->|1. Idempotency Check| IdemDB1
            API1 ===>|2a. Synchronous Auth Pass<br/>Shopper Present| PSP_Edge1
            PSP_Edge1 ===>|2b. Persist Auth Response to local Outbox  as  EventEnvelope &lt;PaymentAuthorized&gt; | EdgeDB1
            API1 -->|3.  Capture / Refund received from merchant <br/>Persisted to Outbox as  EventEnvelope &lt;CaptureRequested&gt; in edge db  without any psp interaction| EdgeDB1
            EdgeDB1 --> Fwd1
        end

        subgraph Edge2["Edge Cell 2"]
            direction TB
            API2["Payment Acceptance Service"]
            IdemDB2[("Idempotency DB<br/>IdempotencyRecord")]
            EdgeDB2[("Edge Local DB<br/>PaymentIntent<br/>OutboxEvents")]
            Fwd2[["LocalOutboxStoreAndForwardJob"]]
            PSP_Edge2["External PSP API<br/>(Synchronous Auth)"]
            
            %% Flow Splits inside Edge
            API2 -.->|1. Idempotency Check| IdemDB2
            API2 ===>|2a. Synchronous Auth Pass<br/>Shopper Present| PSP_Edge2
            PSP_Edge2 ===>|2b. Persist Auth Response to local Outbox  as  EventEnvelope &lt;PaymentAuthorized&gt; | EdgeDB2
            API2 -->|3.  Capture / Refund received from merchant <br/>Persisted to  local Outbox as  EventEnvelope &lt;CaptureRequested&gt; in edge db  without any psp interaction| EdgeDB2
            EdgeDB2 --> Fwd2
        end
    end

    subgraph InternalLayer["INTERNAL HOST (Central Cluster)"]
        direction TB
        
        CentralDB[("Central DB<br/>OutboxEvent, Payment, PaymentTx,<br/>LedgerEntry, JournalEntry, Postings")]
        
        Relay[["OutboxRelayJob<br/>(payment-central-relay)"]]
        
        subgraph Topics["Kafka Topics"]
            direction LR
            CAPTURE_COMMANDS_TOPIC>"gateway.capture.commands<br/>(Accepted: EventEnvelope &lt;CaptureRequested&gt;)"]
            JOURNAL_ENTRIES_RECORDED_TOPIC>"journal.entries.recorded<br/>(Accepted: EventEnvelope &lt;JournalEntriesRecorded&gt;)"]
            CAPTURE_SUBMITTED_ACKS_TOPIC>"gateway.capture.submitted<br/>(Accepted: EventEnvelope &lt;CaptureSubmitted&gt;)"]
            PSP_RESULTS_TOPIC>"payment.psp.results<br/>(Accepted: EventEnvelope &lt;PaymentAuthorized&gt;, &lt;CaptureConfirmed&gt;, &lt;InternalTransferCommand&gt;)"]
        end
        
        CentralDB -->|Polls OutboxEvents| Relay
        
        Relay -->|EventEnvelope &lt;CaptureRequested&gt;| CAPTURE_COMMANDS_TOPIC
        Relay -->|EventEnvelope &lt;CaptureSubmitted&gt;| CAPTURE_SUBMITTED_ACKS_TOPIC
        Relay -->|EventEnvelope &lt;PaymentAuthorized&gt;| PSP_RESULTS_TOPIC
        Relay -->|EventEnvelope &lt;CaptureConfirmed&gt;| PSP_RESULTS_TOPIC
        Relay -->|EventEnvelope &lt;JournalEntriesRecorded&gt;| JOURNAL_ENTRIES_RECORDED_TOPIC
        Relay -->|EventEnvelope &lt;InternalTransferCommand&gt;| PSP_RESULTS_TOPIC

        
        subgraph Consumers["Payment Consumers (payment-consumers)"]
            direction TB
            CaptureCommandExecutor("CaptureCommandExecutor<br/> Consumes EventEnvelope &lt;CaptureRequested&gt;<br/>Calls psp.capture() async endpoint<br/>Stores OutboxEvent EventEnvelope&lt;CaptureSubmitted&gt;")
            GrossCaptureAllocationConsumer("GrossCaptureAllocationConsumer<br/> Consumes EventEnvelope &lt;JournalEntriesRecorded&gt;<br/>Checks for CAPTURE entries, and creates an OutboxEvent EventEnvelope &lt;InternalTransferCommand&gt; for splits")
            AccountBalanceConsumer("AccountBalanceConsumer<br/> Consumes EventEnvelope &lt;JournalEntriesRecorded&gt;<br/>Updates account balances in Redis caching layer")
            CapturePspPerformedConsumer("CapturePspPerformedConsumer<br/> Consumes EventEnvelope &lt;CaptureSubmitted&gt;")
     
            PspResultConsumer("PspResultConsumer<br/> Consumes &lt;PaymentAuthorized&gt;, &lt;CaptureConfirmed&gt; and &lt;InternalTransferCommand&gt;<br/><b>if &lt;PaymentAuthorized&gt;</b> -> creates Payment, AuthTx, JournalEntry (AuthHold), appends &lt;JournalEntriesRecorded&gt;<br/><b>if &lt;CaptureConfirmed&gt;</b> -> updates to CAPTURED, updates CaptureTx, JournalEntry (Capture), appends &lt;JournalEntriesRecorded&gt;<br/><b>if &lt;InternalTransferCommand&gt;</b> -> updates InternalTransferTx, JournalEntry (InternalTransfer)")
        end
        
        CAPTURE_COMMANDS_TOPIC --> CaptureCommandExecutor
        CAPTURE_SUBMITTED_ACKS_TOPIC --> CapturePspPerformedConsumer
        JOURNAL_ENTRIES_RECORDED_TOPIC --> GrossCaptureAllocationConsumer 
        JOURNAL_ENTRIES_RECORDED_TOPIC --> AccountBalanceConsumer 
        PSP_RESULTS_TOPIC --> PspResultConsumer
        
        CaptureCommandExecutor -->|Calls external async psp capture, Writes Outbox EventEnvelope &lt;CaptureSubmitted&gt; | CentralDB
        GrossCaptureAllocationConsumer -->|Writes Outbox EventEnvelope &lt;InternalTransferCommand&gt;| CentralDB
        AccountBalanceConsumer -.->|Updates Cache| CentralDB
        PspResultConsumer -->|Upserts Txs & JournalEntries, appends Outbox EventEnvelope &lt;JournalEntriesRecorded&gt; | CentralDB
        CapturePspPerformedConsumer -->|Writes Result State| CentralDB

    end

    %% Network Links
    Fwd1 ===>|Forwards OutboxEvents Asynchronously| CentralDB
    Fwd2 ===>|Forwards OutboxEvents Asynchronously| CentralDB

    %% Assign Classes
    class Edge1,Edge2 edgeCell
    class InternalLayer internalHost
    class IdemDB1,EdgeDB1,IdemDB2,EdgeDB2,CentralDB db
    class API1,API2 service
    class Fwd1,Fwd2,Relay job
    class CapTopic,TransTopic,ResTopic topic
    class CapCons,TransCons,ResCons consumer

 ### End to End payment flow



IDEMPOTENCY HANDLING

```mermaid
sequenceDiagram
    autonumber

    box rgb(240, 248, 255) "Client Layer"
        actor Shopper
        participant Browser as Shopper's Browser<br/>(React App)
    end

    box rgb(255, 240, 245) "Gateway Layer"
        participant Proxy as Backend Proxy<br/>(Node.js)
        participant Keycloak
    end

    box rgb(255, 244, 225) "Payment Edge Cell"
        participant PaymentSvc as payment-service<br/>(REST API)
        participant IdemSvc as IdempotencyService
    end

    box rgb(255, 235, 238) "External Systems"
        participant Stripe
    end

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
    Browser->>Stripe: elements.submit() (Tokenize & Associate)
    Note right of Browser: Stripe JS sends card data,<br/>creates PaymentMethod,<br/>links it to PaymentIntent
    Stripe-->>Browser: Validation OK
    Browser->>Proxy: POST /api/checkout/authorize-payment/{paymentId}
    
    Proxy->>Keycloak: Request service token (can be cached)
    Keycloak-->>Proxy: Return JWT Access Token

    Proxy->>PaymentSvc: POST /api/v1/payments/{paymentId}/authorize
    
    PaymentSvc->>Stripe: paymentIntents.confirm(id)
    Stripe-->>PaymentSvc: SUCCEEDED

    rect rgb(230, 240, 255)
        note over PaymentSvc: @Transactional
        PaymentSvc->>PaymentSvc: Update PaymentIntent status to AUTHORIZED
        PaymentSvc->>PaymentSvc: Save PaymentAuthorized to Outbox table
    end

    PaymentSvc-->>Proxy: 200 OK { status: 'AUTHORIZED' }
    Proxy-->>Browser: Return final success status
    Browser->>Shopper: Display "Payment Successful" message
```

#### Ledger Finalization & Split Execution Flow (NOT UPTODATE KAFKA FLOW)

```mermaid
sequenceDiagram
    autonumber
    
    box rgb(230, 230, 250) "Message Broker (Kafka)"
        participant psp_result as Topic: psp-result-queue
        participant transfer_topic as Topic: internal-transfer
    end

    box rgb(255, 250, 240) "Payment Consumers"
        participant PspResult as PspResultConsumer
        participant Transfer as InternalTransferRequestExecutor
    end

    box rgb(245, 245, 245) "Central Relay Node"
        participant Relay as OutboxRelayJob
    end

    box rgb(240, 255, 240) "Database"
        participant DB as Central Database
    end

    %% Step 1: Gross Capture
    psp_result->>PspResult: 1. Consume: CaptureSuccessful (from Edge Webhook)
    
    Note over PspResult, DB: --- Phase A: Gross Settlement ---
    PspResult->>DB: 2. Update Payment Status to CAPTURED
    PspResult->>DB: 3. Insert JournalEntry (Gross Asset Capture)
    
    %% Step 2: Staging Splits
    Note over PspResult, DB: --- Phase B: Atomic Split Staging ---
    PspResult->>DB: 4. Check PaymentSplits (Marketplace Logic)
    PspResult->>DB: 5. Insert OutboxEvent<InternalTransferRequest> (One per seller)
    PspResult-->>psp_result: 6. ACK Kafka Message
    
    %% Step 3: Relay Sweeping
    Note over Relay, DB: --- Phase C: Outbox Sweep ---
    Relay->>DB: 7. Polls Outbox table
    DB-->>Relay: Returns new InternalTransferRequest events
    Relay->>transfer_topic: 8. Publish to internal-transfer-queue
    
    %% Step 4: Split Execution
    Note over Transfer, DB: --- Phase D: Split Ledger Execution ---
    transfer_topic->>Transfer: 9. Consume: InternalTransferRequest
    Transfer->>DB: 10. Insert JournalEntry (INTERNAL_TRANSFER)<br/>Credits Seller, Debits Gross Pool
    Transfer-->>transfer_topic: 11. ACK Kafka Message
```

#### Balance Flow Sequence


```mermaid
sequenceDiagram
    box rgb(255, 250, 240) "Payment Consumers (Central)"
        participant Consumer as AccountBalanceConsumer
        participant Service as AccountBalanceService
        participant Job as AccountBalanceSnapshotJob
    end

    box rgb(230, 230, 250) "Message Broker"
        participant Kafka as journal_entries_recorded_topic
    end

    box rgb(255, 228, 225) "In-Memory Store"
        participant Redis as Redis (Deltas)
    end

    box rgb(240, 255, 240) "Database"
        participant DB as PostgreSQL (Snapshots)
    end

    PspResultConsumer->>Kafka: Publish JournalEntriesRecorded
    Kafka->>Consumer: Consume batch (100-500 events)
    Consumer->>Service: updateAccountBalancesBatch(journalEntries)
    Service->>Service: Extract postings, compute signed amounts per account
    Service->>DB: Load current snapshots (batch query: findByAccountCodes)
    Service->>Service: Filter postings by watermark (journalEntryId > lastAppliedEntryId)
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

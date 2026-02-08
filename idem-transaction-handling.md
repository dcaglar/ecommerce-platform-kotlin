# Proposal: Idempotent Payment Intent Creation & Transactional Integrity

## 1. Problem Definition
In our **Merchant-of-Record (MoR)** environment, creating a `PaymentIntent` requires coordinating internal state with external PSP (Stripe) calls. We face three primary risks:
*   **Duplicate Charges**: Network retries or double-clicks causing duplicate `PaymentIntent` creation at the PSP.
*   **Transaction Leakage (NFR1 Risk)**: Holding a database transaction open during a slow external API call (3s timeout) exhausts the connection pool.
*   **Consistency Gap (NFR2 Risk)**: Crashing between the PSP call and the database update leaves the system in an inconsistent state.

## 2. Goals
*   **Enforce "Lock-Action-Unlock"**: Use idempotency to lock the request, perform the action, and then unlock it.
*   **Protect High Availability (NFR1)**: Ensure **zero active DB transactions** during external Stripe API calls.
*   **Ensure Strong Consistency (NFR2)**: Use atomic multi-phase updates to manage the `PaymentIntent` lifecycle.

## 3. Proposed Implementation
The implementation follows the **Modular Architecture** established in `architecture.md`.

### `payment-application` (Orchestration)
*   **[NEW] [CreatePaymentIntentTransactionalPort](file:///absolute/path/to/port)**: Interface defining atomic persistence phases.
*   **[MODIFY] [CreatePaymentIntentService](file:///absolute/path/to/service)**: Refactored to orchestrate the "Split Transaction" flow.

### `payment-service` (Composition Root)
*   **[NEW] [CreatePaymentIntentCoordinator](file:///absolute/path/to/coordinator)**: Implementation using `@Transactional(transactionManager = "webTxManager")`.
*   **[MODIFY] [PaymentServiceConfig](file:///absolute/path/to/config)**: Wires the coordinator as the implementation for the application port.

---

## 4. Technical Design

### A. The Transactional Port (`payment-application`)
We define the atomic operations required for the two-phase commit.

```kotlin
interface CreatePaymentIntentTransactionalPort {
    /** ATOMIC: Phase 1 — Create intent (CREATED_PENDING) and lock idempotency key. */
    fun registerInitialIntent(intent: PaymentIntent, key: String, hash: String)

    /** ATOMIC: Phase 2 — Update intent (CREATED) and complete idempotency record. */
    fun finalizeSuccess(intent: PaymentIntent, key: String, responseBody: String)
}
```

### B. The Use Case Orchestrator (`payment-application`)
The `CreatePaymentIntentService` orchestrates the flow outside of any global transaction:

1.  **Check Idempotency**: If `COMPLETED`, return replayed response. If `PENDING`, return `409 Conflict`.
2.  **Phase 1 (Atomic)**: Call `txPort.registerInitialIntent(...)`. Transition to `CREATED_PENDING`. **DB Tx Ends.**
3.  **PSP Action**: Call `StripeClient.createIntent(...)`. **No DB Tx held.**
4.  **Phase 2 (Atomic)**: Call `txPort.finalizeSuccess(...)`. Transition to `CREATED`. **DB Tx Ends.**

### C. The Coordinator Implementation (`payment-service`)
Implements the port as a `@Component` in the service layer to handle the technical transaction details.

```kotlin
@Component
class CreatePaymentIntentCoordinator(...) : CreatePaymentIntentTransactionalPort {
    @Transactional(transactionManager = "webTxManager", timeout = 2)
    override fun registerInitialIntent(...) {
        intentRepo.save(intent)
        idempotencyStore.tryInsertPending(key, hash)
    }
}
```

---

## 5. Architectural Alignment (per `architecture.md`)
*   **Entity Lifecycle**: Respects the `PaymentIntent` transition: `CREATED_PENDING` (Phase 1) -> `CREATED` (Phase 2).
*   **NFR Compliance**: Directly addresses **NFR1 (Availability)** by splitting transactions and **NFR2 (Consistency)** through atomic persistence.
*   **Hexagonal Cleanliness**: Keeps all SQL and `@Transactional` details in the `payment-service` (Adapter) while keeping logic in `payment-application` (Policy).

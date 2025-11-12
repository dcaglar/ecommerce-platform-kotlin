user story -1
Merchant-of-Record Payment Domain with Full Financial Lifecycl

Business Goal

As a Merchant of Record, our platform must own the full lifecycle of a shopper payment — from authorization to payout — while maintaining a double-entry ledger and event-driven consistency across the system.

When a shopper pays for a multi-seller order (e.g., Uber Eats with multiple restaurants), the platform must:
1.	Authorize a single payment with the PSP (e.g., Adyen).
2.	After authorization, asynchronously create internal PaymentOrders (one per seller).
3.	For each order:
•	Capture funds from PSP.
•	Record journal entries.
•	Handle refunds or cancellations.
•	Settle incoming funds.
•	Payout the merchant.

All accounting must be idempotent and auditable.

⸻

🧱 Domain Model Overview

Core Aggregates


Aggregate
Purpose
Payment
Represents one shopper authorization. Bound 1-to-1 with PSP.
PaymentOrder
Represents one internal seller’s payable portion. Captured asynchronously.
JournalEntry
Represents one atomic financial event (balanced debits/credits).
Account
Represents a logical book account (e.g., PSP_RECEIVABLE, MERCHANT_PAYABLE).
Posting
One side of a JournalEntry (debit or credit).
Amount
Value object for smallest currency units.


PaymentOrder ───> generates JournalEntries:
AUTH_HOLD
CAPTURE
REFUND
SETTLEMENT
PAYOUT

Payment
Represents authorization scope at the PSP.
•	Never directly references sellers — that’s handled post-authorization.

PaymentOrder
	•	Represents an internal payable obligation to one seller.
	•	Created only after PSP authorization succeeds.
	•	Each PaymentOrder has its own lifecycle (capture → settle → payout).



Some Pseudocode for Core Entities

Our payment system currently models Payment (buyer-level authorization) and PaymentOrder (seller-level capture).
Each PaymentOrder is processed asynchronously via Kafka — going through PSP capture, retries, and finalization.

However, the Payment aggregate itself should reflect its true lifecycle:
• From AUTHORIZED → PARTIALLY_CAPTURED → CAPTURED_FINAL
• Enforcing domain invariants between Payment and its child PaymentOrders.

⸻

Goal

Ensure that:
1. Each Payment enforces correct domain invariants between authorization and capture stages.
2. A new consumer automatically aggregates capture progress (sum of captured amounts across PaymentOrders).
3. The Payment status transitions only when business rules are met.
4. The system emits a PaymentCaptured domain event when the Payment reaches its final captured state.

Business Scenario

As a Platform Operator,
I want the system to automatically update a Payment’s state once all its PaymentOrders have completed capture,
so that accounting and payouts can proceed only for finalized payments.

Business Scenario

As a Platform Operator,
I want the system to automatically update a Payment’s state once all its PaymentOrders have completed capture,
so that accounting and payouts can proceed only for finalized payments.

class Payment(
val id: PaymentId,
val buyerId: BuyerId,
val totalAmount: Amount,
var authorized: Boolean = false,
var capturedAmount: Amount = Amount.zero(totalAmount.currency),
var status: PaymentStatus = PaymentStatus.INITIATED,
val paymentOrders: MutableList = mutableListOf()
) {

fun authorize() {
check(!authorized) { "Payment already authorized" }
authorized = true
status = PaymentStatus.AUTHORIZED
}

fun capture(amount: Amount, orderId: PaymentOrderId) {
check(authorized) { "Cannot capture before authorization" }
check(status != PaymentStatus.CAPTURED_FINAL) { "Payment already fully captured" }
check(!paymentOrders.contains(orderId)) { "Duplicate capture for PaymentOrder $orderId" }

    capturedAmount += amount
    paymentOrders += orderId

    status = when {
        capturedAmount == totalAmount -> PaymentStatus.CAPTURED_FINAL
        capturedAmount < totalAmount -> PaymentStatus.PARTIALLY_CAPTURED
        else -> throw IllegalStateException("Captured amount exceeds authorized amount")
    }
}
}

class PaymentOrder(
val id: PaymentOrderId,
val paymentId: PaymentId,
val sellerId: SellerId,
val amount: Amount,
var status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING
) {
fun markCaptured() {
check(status != PaymentOrderStatus.SUCCESSFUL_FINAL) { "Order already captured" }
status = PaymentOrderStatus.SUCCESSFUL_FINAL
}

fun markFailed(reason: String? = null) {
check(!status.isTerminal()) { "Cannot mark failed after final state" }
status = PaymentOrderStatus.FAILED_FINAL
}

fun isTerminal(): Boolean =
status in setOf(PaymentOrderStatus.SUCCESSFUL_FINAL, PaymentOrderStatus.FAILED_FINAL)
}

@component
class PaymentCaptureAggregator(
@param:Qualifier("syncPaymentTx") private val kafkaTx: KafkaTxExecutor,
private val paymentRepository: PaymentRepository,
@param:Qualifier("syncPaymentEventPublisher") private val publisher: EventPublisherPort
) {
private val logger = LoggerFactory.getLogger(javaClass)

@KafkaListener(
topics = [Topics.PAYMENT_ORDER_SUCCEEDED],
containerFactory = "${Topics.PAYMENT_ORDER_SUCCEEDED}-factory",
groupId = CONSUMER_GROUPS.PAYMENT_CAPTURE_AGGREGATOR
)
fun onPaymentOrderSucceeded(
record: ConsumerRecord<String, EventEnvelope<PaymentOrderSucceeded>>,
consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
) {
val env = record.value()
val event = env.data
val tp = TopicPartition(record.topic(), record.partition())
val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
val groupMeta = consumer.groupMetadata()

    LogContext.with(env) {
        kafkaTx.run(offsets, groupMeta) {
            val payment = paymentRepository.findByPaymentId(event.paymentId)
                ?: return@run

            val amount = Amount.of(event.amountValue, Currency(event.currency))
            payment.capture(amount, PaymentOrderId(event.paymentOrderId))

            paymentRepository.save(payment)

            if (payment.status == PaymentStatus.CAPTURED_FINAL) {
                val capturedEvent = PaymentCaptured.create(
                    paymentId = event.paymentId,
                    buyerId = payment.buyerId.value,
                    totalAmount = payment.totalAmount,
                    capturedAt = Instant.now(),
                    traceId = env.traceId,
                    parentEventId = env.eventId
                )
                publisher.publishSync(
                    eventMetaData = EventMetadatas.PaymentCapturedMetadata,
                    aggregateId = payment.id.value.toString(),
                    data = capturedEvent,
                    traceId = env.traceId,
                    parentEventId = env.eventId
                )
                logger.info("💰 Payment {} fully captured, event published", payment.id)
            } else {
                logger.info(
                    "↔️ Payment {} partially captured {}/{}",
                    payment.id, payment.capturedAmount, payment.totalAmount
                )
            }
        }
    }
}
}

data class PaymentCaptured(
val paymentId: String,
val buyerId: String,
val totalAmount: Amount,
val capturedAt: Instant,
val traceId: String?,
val parentEventId: String?
) : PaymentEvent {
companion object {
fun create(
paymentId: String,
buyerId: String,
totalAmount: Amount,
capturedAt: Instant,
traceId: String?,
parentEventId: String?
) = PaymentCaptured(paymentId, buyerId, totalAmount, capturedAt, traceId, parentEventId)
}
}

CREATE TABLE payments (
id BIGSERIAL PRIMARY KEY,
public_payment_id VARCHAR(64) NOT NULL UNIQUE,
buyer_id VARCHAR(64) NOT NULL,
total_amount BIGINT NOT NULL CHECK (total_amount > 0),
currency CHAR(3) NOT NULL,
authorized BOOLEAN NOT NULL DEFAULT FALSE,
captured_amount BIGINT NOT NULL DEFAULT 0 CHECK (captured_amount >= 0),
status VARCHAR(32) NOT NULL,
created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

-- Domain invariant at DB level: can't capture beyond total
CONSTRAINT chk_captured_within_total CHECK (captured_amount <= total_amount)
);

CREATE INDEX idx_payments_buyer_id ON payments(buyer_id);
CREATE INDEX idx_payments_status ON payments(status);

CREATE TABLE payment_orders (
id BIGSERIAL PRIMARY KEY,
public_payment_order_id VARCHAR(64) NOT NULL UNIQUE,
payment_id BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
seller_id VARCHAR(64) NOT NULL,
amount BIGINT NOT NULL CHECK (amount > 0),
currency CHAR(3) NOT NULL,
status VARCHAR(32) NOT NULL,
retry_count INT NOT NULL DEFAULT 0,
last_error TEXT,
created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

-- Prevent inserting duplicate seller lines per payment
CONSTRAINT uq_payment_order_per_seller UNIQUE (payment_id, seller_id)
);

CREATE INDEX idx_payment_orders_payment_id ON payment_orders(payment_id);
CREATE INDEX idx_payment_orders_status ON payment_orders(status);

CREATE TABLE payment_captured_events (
id BIGSERIAL PRIMARY KEY,
payment_id BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
total_amount BIGINT NOT NULL,
currency CHAR(3) NOT NULL,
captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
trace_id VARCHAR(64),
parent_event_id VARCHAR(64),
created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import java.time.OffsetDateTime

data class PaymentEntity(
val id: Long? = null,
val publicPaymentId: String,
val buyerId: String,
val totalAmount: Long,
val currency: String,
val authorized: Boolean = false,
val capturedAmount: Long = 0,
val status: String,
val createdAt: OffsetDateTime = OffsetDateTime.now(),
val updatedAt: OffsetDateTime = OffsetDateTime.now()
)

package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import java.time.OffsetDateTime

data class PaymentOrderEntity(
val id: Long? = null,
val publicPaymentOrderId: String,
val paymentId: Long,
val sellerId: String,
val amount: Long,
val currency: String,
val status: String,
val retryCount: Int = 0,
val lastError: String? = null,
val createdAt: OffsetDateTime = OffsetDateTime.now(),
val updatedAt: OffsetDateTime = OffsetDateTime.now()
)

<resultMap id="PaymentResultMap" type="com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity">
    <id column="id" property="id"/>
    <result column="public_payment_id" property="publicPaymentId"/>
    <result column="buyer_id" property="buyerId"/>
    <result column="total_amount" property="totalAmount"/>
    <result column="currency" property="currency"/>
    <result column="authorized" property="authorized"/>
    <result column="captured_amount" property="capturedAmount"/>
    <result column="status" property="status"/>
    <result column="created_at" property="createdAt"/>
    <result column="updated_at" property="updatedAt"/>
</resultMap>

<!-- Insert new Payment -->
<insert id="insertPayment" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO payments
    (public_payment_id, buyer_id, total_amount, currency, authorized, captured_amount, status, created_at, updated_at)
    VALUES
    (#{publicPaymentId}, #{buyerId}, #{totalAmount}, #{currency},
     #{authorized}, #{capturedAmount}, #{status}, NOW(), NOW())
</insert>

<!-- Find payment by public ID -->
<select id="findByPublicPaymentId" resultMap="PaymentResultMap">
    SELECT * FROM payments WHERE public_payment_id = #{publicPaymentId}
</select>

<!-- Update captured amount atomically -->
<update id="updateCapturedAmountIfAuthorized">
    UPDATE payments
    SET captured_amount = captured_amount + #{capturedIncrement},
        status = #{status},
        updated_at = NOW()
    WHERE public_payment_id = #{publicPaymentId}
      AND authorized = TRUE
      AND captured_amount + #{capturedIncrement} <= total_amount
</update>

<!-- Mark authorized -->
<update id="markAuthorized">
    UPDATE payments
    SET authorized = TRUE,
        status = 'AUTHORIZED',
        updated_at = NOW()
    WHERE public_payment_id = #{publicPaymentId}
      AND authorized = FALSE
</update>
<resultMap id="PaymentOrderResultMap" type="com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentOrderEntity">
    <id column="id" property="id"/>
    <result column="public_payment_order_id" property="publicPaymentOrderId"/>
    <result column="payment_id" property="paymentId"/>
    <result column="seller_id" property="sellerId"/>
    <result column="amount" property="amount"/>
    <result column="currency" property="currency"/>
    <result column="status" property="status"/>
    <result column="retry_count" property="retryCount"/>
    <result column="last_error" property="lastError"/>
    <result column="created_at" property="createdAt"/>
    <result column="updated_at" property="updatedAt"/>
</resultMap>

<!-- Insert PaymentOrder -->
<insert id="insertPaymentOrder" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO payment_orders
    (public_payment_order_id, payment_id, seller_id, amount, currency, status, retry_count, last_error, created_at, updated_at)
    VALUES
    (#{publicPaymentOrderId}, #{paymentId}, #{sellerId}, #{amount}, #{currency},
     #{status}, #{retryCount}, #{lastError}, NOW(), NOW())
    ON CONFLICT (payment_id, seller_id)
    DO NOTHING
</insert>

<!-- Update status idempotently -->
<update id="updateStatusIfNotFinal">
    UPDATE payment_orders
    SET status = #{status},
        updated_at = NOW()
    WHERE public_payment_order_id = #{publicPaymentOrderId}
      AND status NOT IN ('SUCCESSFUL_FINAL', 'FAILED_FINAL')
</update>

<!-- Find all by Payment ID -->
<select id="findByPaymentId" resultMap="PaymentOrderResultMap">
    SELECT * FROM payment_orders WHERE payment_id = #{paymentId}
</select>

<!-- Find by ID -->
<select id="findByPublicPaymentOrderId" resultMap="PaymentOrderResultMap">
    SELECT * FROM payment_orders WHERE public_payment_order_id = #{publicPaymentOrderId}
</select>
package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity

interface PaymentRepository {
fun insert(payment: PaymentEntity): PaymentEntity
fun findByPublicPaymentId(publicPaymentId: String): PaymentEntity?
fun markAuthorized(publicPaymentId: String)
fun updateCapturedAmountIfAuthorized(publicPaymentId: String, capturedIncrement: Long, newStatus: String): Int

package com.dogancaglar.paymentservice.adapter.outbound.persistence

import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mapper.PaymentMapper
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import org.springframework.stereotype.Repository

@repository
class PaymentRepositoryAdapter(
private val mapper: PaymentMapper
) : PaymentRepository {

override fun insert(payment: PaymentEntity): PaymentEntity {
mapper.insertPayment(payment)
return payment
}

override fun findByPublicPaymentId(publicPaymentId: String): PaymentEntity? =
mapper.findByPublicPaymentId(publicPaymentId)

override fun markAuthorized(publicPaymentId: String) =
mapper.markAuthorized(publicPaymentId)

override fun updateCapturedAmountIfAuthorized(publicPaymentId: String, capturedIncrement: Long, newStatus: String): Int =
mapper.updateCapturedAmountIfAuthorized(publicPaymentId, capturedIncrement, newStatus)
}

package com.dogancaglar.paymentservice.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity
import org.apache.ibatis.annotations.*

@Mapper
interface PaymentMapper {
fun insertPayment(payment: PaymentEntity)
fun findByPublicPaymentId(publicPaymentId: String): PaymentEntity?
fun markAuthorized(publicPaymentId: String)
fun updateCapturedAmountIfAuthorized(publicPaymentId: String, capturedIncrement: Long, status: String): Int
}

package com.dogancaglar.paymentservice.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentOrderEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface PaymentOrderMapper {
fun insertPaymentOrder(order: PaymentOrderEntity)
fun updateStatusIfNotFinal(publicPaymentOrderId: String, status: String): Int
fun findByPaymentId(paymentId: Long): List
fun findByPublicPaymentOrderId(publicPaymentOrderId: String): PaymentOrderEntity?
}
}





user story -2 update use cases

Main Use Cases

1️⃣ CreatePaymentService

Goal: Handles synchronous authorization and outbox emission.

Responsibilities:
•	Create and persist a new Payment.
•	Call PaymentGatewayPort.authorize().
•	If successful, mark authorized and persist OutboxEvent<EventEnvelope<PaymentRequestDTO>>.
•	Commit DB transaction atomically.




As the payment platform, I want one unified OutboxDispatcherJob to process both PaymentRequestDTO and PaymentOrderCreated outbox entries.

This ensures:
• clean one-way event expansion (Payment → PaymentOrders → Events)
• atomic creation of downstream outboxes
• predictable replay behavior (idempotent and retry-safe)
• zero circular dependency between services.



Implementation Outline

@Service class OutboxDispatcherJob( private val outboxEventPort:OutboxEventPort,`
private val serializationPort: SerializationPort,
private val paymentOrderRepository: PaymentOrderRepository,
private val paymentOrderFactory: PaymentOrderFactory,
private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper,
private val eventPublisherPort: EventPublisherPort,
private val clock: Clock
) {

@Scheduled(fixedDelayString = "\${outbox.dispatcher.interval-ms:5000}")
fun pollAndDispatch() {
val unsent = outboxEventPort.fetchUnsentBatch(limit = 50)
unsent.forEach { process(it) }
}

private fun process(outbox: OutboxEvent) {
when (outbox.eventType) {
EventMetadatas.PaymentAuthorizedMetadata.eventType ->
handlePaymentRequestOutbox(outbox)
EventMetadatas.PaymentOrderCreatedMetadata.eventType ->
handlePaymentOrderOutbox(outbox)
else -> logger.warn("Skipping unknown outbox type=${outbox.eventType}")
}
}

@transactional
fun handlePaymentRequestOutbox(outbox: OutboxEvent) {
val envelope = serializationPort.fromJson<EventEnvelope>(outbox.payload)
val dto = envelope.data
val paymentId = dto.orderId

// 1️⃣ Expand into PaymentOrders
val orders = dto.paymentOrders.map { line ->
paymentOrderFactory.create(
paymentId = PaymentId(paymentId),
sellerId = SellerId(line.sellerId),
amount = line.amount.toDomain(),
createdAt = Instant.now(clock)
)
}
paymentOrderRepository.insertAll(orders)

// 2️⃣ Create nested Outbox<PaymentOrderCreated>
val orderOutboxes = orders.map { order ->
val event = paymentOrderDomainEventMapper.toPaymentOrderCreated(order)
OutboxEvent.createNew(
oeid = order.id.value,
eventType = EventMetadatas.PaymentOrderCreatedMetadata.eventType,
aggregateId = order.id.value.toString(),
payload = serializationPort.toJson(
DomainEventEnvelopeFactory.envelopeFor(
data = event,
traceId = envelope.traceId,
parentEventId = envelope.eventId,
aggregateId = order.id.value.toString()
)
),
createdAt = Instant.now(clock)
)
}
outboxEventPort.saveAll(orderOutboxes)

// 3️⃣ Publish PaymentAuthorized for ledger
eventPublisherPort.publishSync(
eventMetaData = EventMetadatas.PaymentAuthorizedMetadata,
aggregateId = paymentId,
data = dto,
parentEventId = envelope.eventId,
traceId = envelope.traceId
)

outboxEventPort.markSent(outbox.id)
logger.info("Expanded Payment {} into {} orders + PaymentAuthorized published.", paymentId, orders.size)
}
`

`
fun handlePaymentOrderOutbox(outbox: OutboxEvent) {
val envelope = serializationPort.fromJson<EventEnvelope>(outbox.payload)
eventPublisherPort.publishSync(
eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
aggregateId = envelope.aggregateId,
data = envelope.data,
parentEventId = envelope.parentEventId,
traceId = envelope.traceId
)

outboxEventPort.markSent(outbox.id)
logger.info("Published PaymentOrderCreated for {}", envelope.aggregateId)
}
`

Recursion Principle

The same OutboxDispatcherJob:
• handles both the “root” (PaymentRequestDTO) and “child” (PaymentOrderCreated) outboxes,
• enabling a cascade from Payment → PaymentOrders → downstream events,
• without requiring multiple schedulers or service dependencies.

This pattern ensures idempotency:
• Each outbox row is only marked sent after success.
• Retries re-enter the same handler safely.

Acceptance Criteria •	OutboxDispatcherJob handles both PaymentRequestDTO and PaymentOrderCreated types. •	OutboxDispatcher creates nested outbox entries transactionally. •	Recursive outbox expansion is idempotent and replay-safe. •	PaymentAuthorized event is always emitted once per payment. •	PaymentOrderCreated events are emitted once per seller. •	Ledger AUTH_HOLD is triggered from PaymentAuthorizedConsumer.
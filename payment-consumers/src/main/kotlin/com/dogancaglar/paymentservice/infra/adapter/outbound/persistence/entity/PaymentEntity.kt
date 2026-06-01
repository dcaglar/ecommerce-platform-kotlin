package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity

import java.time.Instant

/**
 * PaymentEntity
 *
 * Infrastructure POJO — the flat, column-by-column representation of the
 * Payment aggregate as stored in the 'payments' table in the Central DB.
 *
 * Lives in payment-consumers because the Payment aggregate is a Central Core
 * concern, instantiated by PspResultConsumer from a PaymentAuthorized Kafka event.
 * It is NEVER written by the Edge Cell (payment-service).
 *
 * Design rules for this layer (strictly enforced):
 *  - NO domain types. All fields are JVM primitives, String, or Instant.
 *  - NO business logic. This is a dumb data bag.
 *  - NO Jackson / Spring annotations.
 *  - Conversion to/from the domain [Payment] aggregate happens exclusively in
 *    [PaymentEntityMapper].
 *
 * Column mapping:
 *  paymentId           → payments.payment_id           (Snowflake Long, PK)
 *  paymentIntentId     → payments.payment_intent_id    (traceability FK to edge payment_intents)
 *  buyerId             → payments.buyer_id
 *  merchantAccountId   → payments.merchant_account_id  (primary MoR merchant entity)
 *  processingModel     → payments.processing_model     (DIRECT_MERCHANT | MARKETPLACE)
 *  totalAmountValue    → payments.total_amount_value   (smallest unit, e.g. cents)
 *  currency            → payments.currency             (ISO 4217, e.g. "EUR")
 *  capturedAmountValue → payments.captured_amount_value
 *  refundedAmountValue → payments.refunded_amount_value
 *  status              → payments.status               (PaymentStatus.name)
 *  splitsJson          → payments.splits_json          (JSON array of PaymentSplit routing instructions)
 *  createdAt           → payments.created_at
 *  updatedAt           → payments.updated_at
 */
data class PaymentEntity(
    val paymentId: Long,
    val paymentIntentId: Long,
    val buyerId: String,
    val merchantAccountId: String,
    val processingModel: String,
    val totalAmountValue: Long,
    val currency: String,
    val capturedAmountValue: Long,
    val refundedAmountValue: Long,
    val status: String,
    val splitsJson: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

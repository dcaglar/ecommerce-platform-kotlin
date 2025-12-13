package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import java.time.Instant

data class PaymentEntity(
    val paymentId: Long,
    val paymentIntentId: Long,
    val buyerId: String,
    val orderId: String,
    val totalAmountValue: Long,
    val currency: String,
    val capturedAmountValue: Long,
    val refundedAmountValue: Long,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val paymentLinesJson: String    // ← NEW JSON column
)
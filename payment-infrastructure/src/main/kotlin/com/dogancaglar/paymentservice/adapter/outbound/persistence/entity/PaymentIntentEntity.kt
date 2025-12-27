package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import java.time.Instant

data class PaymentIntentEntity(
    val paymentIntentId: Long,
    val pspReference: String?,
    val buyerId: String,
    val orderId: String,
    val totalAmountValue: Long,
    val currency: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val paymentLinesJson: String    // ← NEW JSON column
)
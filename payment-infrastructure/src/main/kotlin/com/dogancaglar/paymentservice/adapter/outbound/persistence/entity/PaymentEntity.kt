package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import java.time.Instant

data class PaymentEntity(
    val paymentId: Long,
    val buyerId: String,
    val orderId: String,
    val totalAmountValue: Long,
    val currency: String,
    val capturedAmountValue: Long,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
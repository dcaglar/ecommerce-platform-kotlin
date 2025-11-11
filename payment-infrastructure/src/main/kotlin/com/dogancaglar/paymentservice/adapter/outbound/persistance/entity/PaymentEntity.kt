package com.dogancaglar.paymentservice.adapter.outbound.persistance.entity

import java.time.LocalDateTime

data class PaymentEntity(
    val paymentId: Long,
    val buyerId: String,
    val orderId: String,
    val totalAmountValue: Long,
    val currency: String,
    val capturedAmountValue: Long,
    val status: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
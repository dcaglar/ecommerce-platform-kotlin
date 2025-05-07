package com.dogancaglar.paymentservice.domain.model

import java.time.LocalDateTime

data class PaymentRequestDto(
    val id: String?,
    val buyerId: String,
    val orderId: String,
    val totalAmount: Amount,
    val status: PaymentStatus,
    val createdAt: LocalDateTime,
    val paymentOrders: List<PaymentOrder>
)
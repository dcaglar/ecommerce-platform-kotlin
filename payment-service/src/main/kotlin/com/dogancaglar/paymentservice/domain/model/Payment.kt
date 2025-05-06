package com.dogancaglar.paymentservice.domain.model

import java.time.LocalDateTime

data class Payment(
    val id: String?,                       // Optional before persistence
    val buyerId: String,                   // Who is paying
    val orderId: String,                   // Order being paid for
    val totalAmount: Amount,               // Amount object with value + currency
    val status: PaymentStatus,             // INITIATED, SUCCESS, FAILED
    val createdAt: LocalDateTime,          // Timestamp of creation
    val paymentOrders: List<PaymentOrder>  // Breakdown per seller
)
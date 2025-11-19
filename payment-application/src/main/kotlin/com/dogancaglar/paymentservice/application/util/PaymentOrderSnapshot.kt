package com.dogancaglar.paymentservice.application.util

import java.time.LocalDateTime

data class PaymentOrderSnapshot(
    val paymentOrderId: String,
    val paymentId: String,

    val sellerId: String,
    val amountValue: Long,
    val currency: String,
    val timestamp: LocalDateTime
)
package com.dogancaglar.paymentservice.domain.event

data class PaymentOrderRetryEvent(
    val paymentOrderId: String,
    val paymentId: String,
    val sellerId: String,
    val amountValue: java.math.BigDecimal,
    val currency: String,
    val attempt: Int
)
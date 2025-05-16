package com.dogancaglar.paymentservice.domain.event

data class PaymentOrderSucceeded(
    val paymentOrderId: String,
    val sellerId: String,
    val amountValue: java.math.BigDecimal,
    val currency: String
)
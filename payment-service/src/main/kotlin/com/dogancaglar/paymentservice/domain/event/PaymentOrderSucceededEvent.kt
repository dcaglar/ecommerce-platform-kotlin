package com.dogancaglar.paymentservice.domain.event

data class PaymentOrderSucceededEvent(
    val paymentOrderId: String,
    val sellerId: String,
    val amountValue: java.math.BigDecimal,
    val currency: String
)
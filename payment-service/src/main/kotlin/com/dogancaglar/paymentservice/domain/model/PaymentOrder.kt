package com.dogancaglar.paymentservice.domain.model

data class PaymentOrder(
    val sellerId: String,
    val amount: Amount
)
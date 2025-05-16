package com.dogancaglar.paymentservice.domain.event

data class PaymentOrderStatusCheckRequested(
    val paymentOrderId: String,
    val attempt: Int
)
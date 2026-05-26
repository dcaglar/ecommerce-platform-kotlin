package com.dogancaglar.paymentservice.domain.model.payment

//status of an (Payment aggregate)
enum class PaymentIntentStatus {
    CREATED_PENDING,
    CREATED,
    PENDING_AUTH,
    AUTHORIZED,
    DECLINED,
    CANCELLED
}





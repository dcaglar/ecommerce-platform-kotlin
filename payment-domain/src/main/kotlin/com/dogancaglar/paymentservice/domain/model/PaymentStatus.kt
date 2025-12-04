package com.dogancaglar.paymentservice.domain.model

//status of an (Payment aggregate)
enum class PaymentStatus {
    CREATED,
    PENDING_AUTH,
    AUTHORIZED,
    DECLINED,
    PARTIALLY_CAPTURED,
    CAPTURED
}
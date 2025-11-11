package com.dogancaglar.paymentservice.domain.model

//status of an (Payment aggregate)
enum class PaymentStatus {
    PENDING_AUTH,
    AUTHORIZED,
    DECLINED,
    CAPTURED_PARTIALLY,
    CAPTURED
}
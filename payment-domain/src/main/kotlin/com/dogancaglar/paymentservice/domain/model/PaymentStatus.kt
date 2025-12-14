package com.dogancaglar.paymentservice.domain.model

//status of an (Payment aggregate)
enum class PaymentStatus {
    NOT_CAPTURED,
    PARTIALLY_CAPTURED,
    CAPTURED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    VOIDED
}
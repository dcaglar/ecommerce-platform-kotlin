package com.dogancaglar.paymentservice.domain.model

//status of PaymentOrder(captures)
enum class PaymentOrderStatus {
    // Initial state upon creation
    INITIATED_PENDING,

    //probably set this in Enqueuer
    CAPTURE_REQUESTED,

    //if psp call returned declined,set final response
    CAPTURE_FAILED,
    //if psp call hypotehtically approved then update to this
    CAPTURED, // final respinse
    REFUND_REQUESTED,
    REFUND_FAILED,
    REFUNDED,


    // RETRYABLE FAILURES
    PENDING_CAPTURE,
    TIMEOUT_EXCEEDED_1S_TRANSIENT,                // PSP call timeout
    PSP_UNAVAILABLE_TRANSIENT,

}

fun PaymentOrderStatus.isExternalCapturePspResponse(): Boolean =
    isRetryablePspResponse() ||  isTerminalPspResponse()


fun PaymentOrderStatus.isTerminalPspResponse(): Boolean=
    this in setOf(PaymentOrderStatus.CAPTURE_FAILED, PaymentOrderStatus.CAPTURED)


fun PaymentOrderStatus.isRetryablePspResponse(): Boolean=
    this in setOf(PaymentOrderStatus.PENDING_CAPTURE, PaymentOrderStatus.CAPTURED)
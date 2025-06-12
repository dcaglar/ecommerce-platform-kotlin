package com.dogancaglar.paymentservice.adapter.psp

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus

object PSPStatusMapper {
    fun fromPspStatus(code: String): PaymentOrderStatus = when (code.uppercase()) {
        // Final successful state success
        "SUCCESSFUL" -> PaymentOrderStatus.SUCCESSFUL //mark payment as paids and emit to SUCCESS_QUEUE and  consumers of queue persist to db(or we could actually batch like 20 of them teporarily buffer in memoert)
        // Finalized failure states(non-retryable failures)
        "FINALIZED_FAILED" -> PaymentOrderStatus.FINALIZED_FAILED //mark payment as FINALIZED_FAILED and emit FAILURE_QUEUE and FAILURE_QUEUE consumers persist to db (or we could actually batch like 20 of them teporarily buffer in memoert)
        "DECLINED" -> PaymentOrderStatus.DECLINED    //mark payment as DECLINED   andemit FAILURE_QUEUE and FAILURE_QUEUE consumers persist to db (or we could actually batch like 20 of them teporarily buffer in
        //retryalbe failures
        "FAILED" -> PaymentOrderStatus.FAILED            //mark payment as failed, add payment retry queue sue backof delay with
        "PSP_UNAVAILABLE" -> PaymentOrderStatus.PSP_UNAVAILABLE
        "TIMEOUT" -> PaymentOrderStatus.TIMEOUT
        ///statuscheck
        "AUTH_NEEDED" -> PaymentOrderStatus.AUTH_NEEDED
        "CAPTURE_PENDING" -> PaymentOrderStatus.CAPTURE_PENDING
        else -> PaymentOrderStatus.UNKNOWN
    }

    fun requiresRetryPayment(status: PaymentOrderStatus): Boolean {
        return status in retryPaymentStatus
    }

    fun requiresStatusCheck(status: PaymentOrderStatus): Boolean {
        return status in scheduleCheckPaymentStatus
    }

    fun isPaymentFailedFinally(status: PaymentOrderStatus): Boolean {
        return status in finalFailStatus
    }


    private val retryPaymentStatus = setOf(
        PaymentOrderStatus.FAILED, PaymentOrderStatus.PSP_UNAVAILABLE, PaymentOrderStatus.TIMEOUT
    )

    private val scheduleCheckPaymentStatus = setOf(
        PaymentOrderStatus.AUTH_NEEDED, PaymentOrderStatus.UNKNOWN
    )

    private val finalFailStatus = setOf(
        PaymentOrderStatus.DECLINED, PaymentOrderStatus.FINALIZED_FAILED
    )

}



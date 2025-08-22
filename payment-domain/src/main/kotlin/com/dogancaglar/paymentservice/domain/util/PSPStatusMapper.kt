package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus


object PSPStatusMapper {
    fun fromPspStatus(code: String): PaymentOrderStatus = when (code.uppercase()) {
        // Final successful state success
        "SUCCESSFUL_FINAL" -> PaymentOrderStatus.SUCCESSFUL_FINAL //mark payment as paids and emit to SUCCESS_QUEUE and  consumers of queue persist to db(or we could actually batch like 20 of them teporarily buffer in memoert)
        // Finalized failure states(non-retryable failures)
        "FAILED_FINAL" -> PaymentOrderStatus.FAILED_FINAL //mark payment as FINALIZED_FAILED and emit FAILURE_QUEUE and FAILURE_QUEUE consumers persist to db (or we could actually batch like 20 of them teporarily buffer in memoert)
        "DECLINED_FINAL" -> PaymentOrderStatus.DECLINED_FINAL    //mark payment as DECLINED   andemit FAILURE_QUEUE and FAILURE_QUEUE consumers persist to db (or we could actually batch like 20 of them teporarily buffer in
        //retryalbe failures
        "FAILED_TRANSIENT_ERROR" -> PaymentOrderStatus.FAILED_TRANSIENT_ERROR            //mark payment as failed, add payment retry queue sue backof delay with
        "PSP_UNAVAILABLE_TRANSIENT" -> PaymentOrderStatus.PSP_UNAVAILABLE_TRANSIENT
        "TIMEOUT_EXCEEDED_1S_TRANSIENT" -> PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        ///statuscheck
        "AUTH_NEEDED_STAUS_CHECK_LATER" -> PaymentOrderStatus.AUTH_NEEDED_STAUS_CHECK_LATER
        "CAPTURE_PENDING_STATUS_CHECK_LATER" -> PaymentOrderStatus.CAPTURE_PENDING_STATUS_CHECK_LATER
        else -> PaymentOrderStatus.UNKNOWN_FINAL
    }

    fun requiresRetryPayment(status: PaymentOrderStatus): Boolean {
        return status in retryPaymentStatus
    }

    fun requiresStatusCheck(status: PaymentOrderStatus): Boolean {
        return status in scheduleCheckPaymentStatus
    }


    private val retryPaymentStatus = setOf(
        PaymentOrderStatus.FAILED_TRANSIENT_ERROR,
        PaymentOrderStatus.PSP_UNAVAILABLE_TRANSIENT,
        PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
    )

    private val scheduleCheckPaymentStatus = setOf(
        PaymentOrderStatus.AUTH_NEEDED_STAUS_CHECK_LATER, PaymentOrderStatus.UNKNOWN_FINAL
    )

}
package com.dogancaglar.paymentservice.psp

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus

object PSPStatusMapper {
    /**
     * Maps PSP status codes to internal domain PaymentOrderStatus.
     */
    fun fromPspStatus(code: String): PaymentOrderStatus = when (code.uppercase()) {
        "AUTH_NEEDED"        -> PaymentOrderStatus.AUTH_NEEDED
        "CAPTURE_PENDING"    -> PaymentOrderStatus.CAPTURE_PENDING
        "DECLINED"           -> PaymentOrderStatus.DECLINED
        "INSUFFICIENT_FUNDS" -> PaymentOrderStatus.DECLINED
        "PENDING"            -> PaymentOrderStatus.PENDING
        "SUCCESSFUL"            -> PaymentOrderStatus.SUCCESSFUL
        else                 -> PaymentOrderStatus.UNKNOWN
    }
    fun requiresRetryPayment(status: PaymentOrderStatus): Boolean { return status in retryPaymentStatus}

    fun requiresStatusCheck(status: PaymentOrderStatus): Boolean {
        return status in scheduleCheckPaymentStatus
    }
}

private val retryPaymentStatus = setOf(
        PaymentOrderStatus.DECLINED,
    )

private val scheduleCheckPaymentStatus = setOf(
    PaymentOrderStatus.INSUFFICIENT_FUNDS,
    PaymentOrderStatus.AUTH_NEEDED,
    PaymentOrderStatus.CAPTURE_PENDING,
    PaymentOrderStatus.PENDING
)

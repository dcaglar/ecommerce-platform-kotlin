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
        "SUCCESS"            -> PaymentOrderStatus.SUCCESSFUL
        else                 -> PaymentOrderStatus.UNKNOWN
    }

    private val retryableStatuses = setOf(
        PaymentOrderStatus.DECLINED,
        PaymentOrderStatus.PENDING,
        PaymentOrderStatus.AUTH_NEEDED
    )

    fun isRetryable(status: PaymentOrderStatus): Boolean = status in retryableStatuses

    fun requiresStatusCheck(status: PaymentOrderStatus): Boolean =
        status == PaymentOrderStatus.CAPTURE_PENDING
}
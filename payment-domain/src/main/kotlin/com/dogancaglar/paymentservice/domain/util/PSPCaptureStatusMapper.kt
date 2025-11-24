package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus

/**
 * Maps PSP capture (seller-level) responses to internal PaymentOrderStatus.
 * Used by PaymentOrderPspCallExecutor and PaymentOrderPspResultApplier.
 */
object PSPCaptureStatusMapper {

    fun fromPspCaptureResponseCode(code: String): PaymentOrderStatus = when (code.uppercase()) {
        // Final successful state
        "CAPTURE_SUCCESS", "SUCCESSFUL_FINAL" -> PaymentOrderStatus.CAPTURED

        // Final non-retryable failure states
        "CAPTURE_FAILED_FINAL", "CAPTURE_DECLINED_FINAL" -> PaymentOrderStatus.CAPTURE_FAILED

        // Transient (retryable) errors
        "TRANSIENT_NETWORK_ERROR",
            "PENDING_CAPTURE",
        "TIMEOUT_EXCEEDED_1S_TRANSIENT" -> PaymentOrderStatus.PENDING_CAPTURE // will be retried


        else -> PaymentOrderStatus.CAPTURE_FAILED
    }

    fun requiresRetry(status: PaymentOrderStatus): Boolean {
        return status in retryableStatuses
    }

    private val retryableStatuses = setOf(
        PaymentOrderStatus.PENDING_CAPTURE // transient retry bucket
    )

}
package com.dogancaglar.paymentservice.domain.model

import java.time.LocalDateTime

data class PaymentOrder(
    val paymentOrderId: Long,
    val publicPaymentOrderId: String,
    val paymentId: Long,
    val publicPaymentId: String,
    val sellerId: String,
    val amount: Amount,
    val status: PaymentOrderStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = LocalDateTime.now(),
    var retryCount: Int = 0,
    var retryReason: String? = "",
    var lastErrorMessage: String? = ""
) {

    fun markAsFailed(): PaymentOrder {
        return this.copy(status = PaymentOrderStatus.FAILED)
    }

    fun markAsPaid(): PaymentOrder {
        return this.copy(status = PaymentOrderStatus.SUCCESSFUL)
    }

    fun markAsPending(): PaymentOrder {
        return this.copy(status = PaymentOrderStatus.PENDING)
    }

    fun markAsFinalizedFailed(): PaymentOrder {
        return this.copy(status = PaymentOrderStatus.FINALIZE_FAILED)
    }

    fun incrementRetry(): PaymentOrder {
        return this.copy(retryCount = this.retryCount + 1)
    }

    fun withRetryReason(reason: String?): PaymentOrder {
        return this.copy(retryReason = reason)
    }

    fun withLastError(error: String?): PaymentOrder {
        return this.copy(lastErrorMessage = error)
    }

    fun updatedAt(now: LocalDateTime): PaymentOrder {
        return this.copy(updatedAt = now)
    }
}


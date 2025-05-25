package com.dogancaglar.paymentservice.domain.model

import java.time.LocalDateTime

data class PaymentOrder(
    val paymentOrderId: Long,
    val paymentId: Long,
    val publicId: String,
    val sellerId: String,
    val amount: Amount,
    val status: PaymentOrderStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = LocalDateTime.now(),
    var retryCount: Int = 0,
    var retryReason: String? = "",
    var lastErrorMessage: String? = ""

) {


    fun markAsPaid(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.SUCCESSFUL)

    fun markAsFailed(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.DECLINED)

    fun markAsPending(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.PENDING)

    fun markAsFinalizedFailed(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.FINALIZE_FAILED)

    fun incrementRetry(): PaymentOrder =
        this.copy(retryCount = retryCount + 1)

    fun withLastError(message: String?): PaymentOrder =
        this.copy(lastErrorMessage = message)

    fun withRetryReason(message: String?): PaymentOrder =
        this.copy(retryReason = message)

    fun updatedAt(updateAt: LocalDateTime): PaymentOrder =
        this.copy(updatedAt = updateAt)

}


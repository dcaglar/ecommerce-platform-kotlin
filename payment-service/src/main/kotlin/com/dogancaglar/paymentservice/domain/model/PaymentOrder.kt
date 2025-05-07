package com.dogancaglar.paymentservice.domain.model

import java.time.LocalDateTime

data class PaymentOrder(
    val paymentOrderId: String,
    val paymentId: String,
    val sellerId: String,
    val amount: Amount,
    val status: PaymentOrderStatus,
    val retryCount: Int,
    val createdAt: LocalDateTime
) {
    fun markAsPaid(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.SUCCESS)

    fun markAsFailed(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.FAILED)

    fun incrementRetry(): PaymentOrder =
        this.copy(retryCount = this.retryCount + 1)
}
package com.dogancaglar.paymentservice.domain.model

import java.time.LocalDateTime

data class PaymentOrder(
    val paymentOrderId: String,
    val paymentId: String,
    val sellerId: String,
    val amount: Amount,
    val status: PaymentOrderStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = LocalDateTime.now()

) {
    fun markAsPaid(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.SUCCESS)

    fun markAsFailed(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.FAILED)



    fun markAsFinalizedFailed(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.FAILED_FINAL)
}
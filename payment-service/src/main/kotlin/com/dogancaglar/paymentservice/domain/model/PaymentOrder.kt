package com.dogancaglar.paymentservice.domain.model

import java.time.LocalDateTime

data class PaymentOrder(
    val paymentOrderId: String,
    val paymentId: String,
    val sellerId: String,
    val amount: Amount,
    val status: PaymentOrderStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = LocalDateTime.now(),
    var retryCount: Int=0,
    var retryReason :String?="",
    var lastErrorMessage :String?=""

) {




    fun markAsPaid(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.SUCCESSFUL)

    fun markAsFailed(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.DECLINED)

    fun markAsFinalizedFailed(): PaymentOrder =
        this.copy(status = PaymentOrderStatus.DECLINED)



    fun incrementRetry(): PaymentOrder =
        this.copy(retryCount = retryCount+1)

    fun withLastError(message: String?): PaymentOrder {
        this.lastErrorMessage = message
        return this
    }

    fun withRetryReason(message: String?): PaymentOrder {
        this.retryReason = message
        return this
    }


    fun hasExceededMaxRetries(  maxRetries :Int = 5): Boolean =  this.retryCount>=5

}


package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderStatusCheckRequested constructor(
    val paymentOrderId: String,
    val paymentId: String,
    val sellerId: String,
    val amountValue: BigDecimal,
    val currency: String,
    val retryCount: Int,
    val status : String,
    val createdAt: LocalDateTime,
    val updatedAt : LocalDateTime? = LocalDateTime.now(),
    val retryReason: String? = null,
    val lastErrorMessage: String? = null
)


fun PaymentOrder.toRetryStatusEvent(retryReason:String?="Unknown reason",lastErrorMessage:String?="no error message"): PaymentOrderStatusCheckRequested {
    return PaymentOrderStatusCheckRequested(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amountValue = this.amount.value,
        currency = this.amount.currency,
        status = this.status.name,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        retryCount = this.retryCount,
        retryReason = this.retryReason,
        lastErrorMessage = this.lastErrorMessage
    )
}
fun PaymentOrderStatusCheckRequested.toDomain(): PaymentOrder {
    return toPaymentOrderDomain(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amountValue = this.amountValue.setScale(2, RoundingMode.HALF_DOWN),
        currency = this.currency,
        status = this.status,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt!!,
        retryCount = this.retryCount,
        retryReason = this.retryReason!!,
        lastErrorMessage = this.lastErrorMessage!!
    )
}

private fun toPaymentOrderDomain(
        paymentOrderId: String,
        paymentId: String,
        sellerId: String,
        amountValue: BigDecimal,
        currency: String,
        status: String,
        createdAt: LocalDateTime,
        updatedAt: LocalDateTime,
        retryCount: Int,
        retryReason: String,
        lastErrorMessage : String,
    ): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = sellerId,
            amount = Amount(amountValue,currency),
            status = PaymentOrderStatus.valueOf(status),
            createdAt = createdAt,
            updatedAt = updatedAt,
            retryCount = retryCount,
            retryReason = retryReason,
            lastErrorMessage = lastErrorMessage
        )
    }



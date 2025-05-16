package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderRetryRequested @JsonCreator constructor(
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



fun PaymentOrderRetryRequested.toDomain(): PaymentOrder {
    return toPaymentOrderDomain(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amountValue = this.amountValue.setScale(2, RoundingMode.HALF_DOWN),
        currency = this.currency,
        status = this.status,
        createdAt=  this.createdAt,
        updatedAt = LocalDateTime.now(),
        retryCount = retryCount

    )
}



fun toPaymentOrderDomain(
    paymentOrderId: String,
    paymentId: String,
    sellerId: String,
    amountValue: BigDecimal,
    currency: String,
    status: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime = LocalDateTime.now(),
    retryCount: Int
): PaymentOrder {
    return PaymentOrder(
        paymentOrderId = paymentOrderId,
        paymentId = paymentId,
        sellerId = sellerId,
        amount = Amount(amountValue,currency),
        status = PaymentOrderStatus.valueOf(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
        retryCount = retryCount
    )
}
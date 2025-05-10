package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)

data class PaymentOrderRetryEvent @JsonCreator constructor(
    val paymentOrderId: String,
    val paymentId: String,
    val sellerId: String,
    val amountValue: java.math.BigDecimal,
    val currency: String,
    val retryCount: Int,
    val status : String,
    val createdAt: LocalDateTime,
    val updatedAt : LocalDateTime? = LocalDateTime.now()
)


fun PaymentOrderRetryEvent.toDomain(): PaymentOrder {
    return PaymentOrder(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amount = Amount(this.amountValue, this.currency),
        status = PaymentOrderStatus.valueOf(this.status),
        createdAt=  this.createdAt,
        updatedAt = LocalDateTime.now()
    )
}

fun PaymentOrderRetryEvent.toIncremented(): PaymentOrderRetryEvent {
    return PaymentOrderRetryEvent(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amountValue=this.amountValue,
        currency = this.currency,
        createdAt=  this.createdAt,
        retryCount = this.retryCount+1,
        status = "FAILED",



    )
}
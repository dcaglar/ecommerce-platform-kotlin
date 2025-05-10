package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderCreatedEvent @JsonCreator constructor(
    val paymentOrderId: String = "",
    val paymentId: String = "",
    val sellerId: String = "",
    val amountValue: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val currency: String = "",
    val status : String = "",
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = LocalDateTime.now()

)

fun PaymentOrderCreatedEvent.toDomain(): PaymentOrder {
    return PaymentOrder(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amount = Amount(this.amountValue, this.currency),
        status = PaymentOrderStatus.valueOf(this.status),
        createdAt =this.createdAt,
        updatedAt = LocalDateTime.now()
    )
}

fun PaymentOrder.toCreatedEvent(): PaymentOrderCreatedEvent {
    return PaymentOrderCreatedEvent(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amountValue = this.amount.value,
        currency = this.amount.currency,
        status =this.status.name,
        createdAt = this.createdAt
        )
}




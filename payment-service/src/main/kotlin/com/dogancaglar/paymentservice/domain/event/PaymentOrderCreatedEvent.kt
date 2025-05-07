package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.paymentservice.domain.model.PaymentOrder

data class PaymentOrderCreatedEvent(
    val paymentOrderId: String,
    val paymentId: String,
    val sellerId: String,
    val amountValue: java.math.BigDecimal,
    val currency: String
)

fun PaymentOrder.toCreatedEvent(): PaymentOrderCreatedEvent {
    return PaymentOrderCreatedEvent(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amountValue = this.amount.value,
        currency = this.amount.currency

        )

}
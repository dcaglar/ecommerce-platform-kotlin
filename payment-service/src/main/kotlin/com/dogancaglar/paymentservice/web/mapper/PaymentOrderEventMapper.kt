package com.dogancaglar.paymentservice.web.mapper

import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreatedEvent
import java.time.LocalDateTime
import kotlin.String

fun PaymentOrder.toCreatedEvent(): PaymentOrderCreatedEvent {
    return PaymentOrderCreatedEvent(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amountValue = this.amount.value,
        currency = this.amount.currency,
        status =this.status.name,
        createdAt= this.createdAt
        )

}
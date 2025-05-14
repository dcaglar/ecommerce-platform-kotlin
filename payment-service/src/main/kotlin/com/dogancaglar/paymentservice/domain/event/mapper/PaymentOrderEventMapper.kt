package com.dogancaglar.paymentservice.domain.event.mapper

import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreatedEvent
import com.dogancaglar.paymentservice.domain.event.PaymentOrderRetryEvent
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import java.time.LocalDateTime

fun PaymentOrder.toCreatedEvent(): PaymentOrderCreatedEvent {
    return PaymentOrderCreatedEvent(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amountValue = this.amount.value,
        currency = this.amount.currency,
        status = this.status.name,
        createdAt = this.createdAt,
        retryCount = this.retryCount
    )
}

    fun PaymentOrder.toRetryEvent(retryReason:String?="Unknown reason",lastErrorMessage:String?="no error message"): PaymentOrderRetryEvent {
        return PaymentOrderRetryEvent(
            paymentOrderId = this.paymentOrderId,
            paymentId = this.paymentId,
            sellerId = this.sellerId,
            amountValue =  this.amount.value,
            currency = this.amount.currency,
            status = this.status.name,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            retryCount = this.retryCount,
            retryReason = this.retryReason,
            lastErrorMessage =this.lastErrorMessage
        )


    }

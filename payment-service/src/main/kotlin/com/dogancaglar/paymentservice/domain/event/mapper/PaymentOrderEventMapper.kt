package com.dogancaglar.paymentservice.domain.event.mapper

import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusScheduled
import com.dogancaglar.paymentservice.domain.model.PaymentOrder

fun PaymentOrder.toCreatedEvent(): PaymentOrderCreated {
    return PaymentOrderCreated(
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

    fun PaymentOrder.toRetryEvent(retryReason:String?="Unknown reason",lastErrorMessage:String?="no error message"): PaymentOrderRetryRequested {
        return PaymentOrderRetryRequested(
            paymentOrderId = this.paymentOrderId,
            paymentId = this.paymentId,
            sellerId = this.sellerId,
            amountValue = this.amount.value,
            currency = this.amount.currency,
            status = this.status.name,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            retryCount = this.retryCount,
            retryReason = retryReason,
            lastErrorMessage = lastErrorMessage
        )
    }


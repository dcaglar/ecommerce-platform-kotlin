package com.dogancaglar.paymentservice.application.mapper

import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.event.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.application.event.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import java.time.LocalDateTime

object PaymentOrderEventMapper {
    fun toPaymentOrderRetryRequestEvent(
        order: PaymentOrder,
        newRetryCount: Int,
        retryReason: String? = "UNKNOWN",
        lastErrorMessage: String? = "N/A"
    ): PaymentOrderRetryRequested {
        return PaymentOrderRetryRequested(
            paymentOrderId = order.paymentOrderId.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.toString(),
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId,
            retryCount = newRetryCount,
            retryReason = retryReason,
            lastErrorMessage = lastErrorMessage,
            createdAt = LocalDateTime.now(),
            status = order.status.name,
            updatedAt = LocalDateTime.now(),
            amountValue = order.amount.value,
            currency = order.amount.currency,
        )
    }

    fun toPaymentOrderCreatedEvent(order: PaymentOrder): PaymentOrderCreated {
        return PaymentOrderCreated(
            paymentOrderId = order.paymentOrderId.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.toString(),
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId,
            retryCount = 0,
            createdAt = LocalDateTime.now(),
            status = order.status.name,
            amountValue = order.amount.value,
            currency = order.amount.currency,
        )
    }


    fun toPaymentOrderSuccededEvent(order: PaymentOrder): PaymentOrderSucceeded {
        return PaymentOrderSucceeded(
            paymentOrderId = order.paymentOrderId.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.toString(),
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId,
            amountValue = order.amount.value,
            currency = order.amount.currency,
        )
    }


    fun toPaymentOrderStatusCheckRequested(order: PaymentOrder): PaymentOrderStatusCheckRequested {
        return PaymentOrderStatusCheckRequested(
            paymentOrderId = order.paymentOrderId.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.toString(),
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId,
            retryCount = order.retryCount,
            retryReason = order.retryReason,
            createdAt = LocalDateTime.now(),
            status = order.status.name,
            updatedAt = LocalDateTime.now(),
            amountValue = order.amount.value,
            currency = order.amount.currency,
        )
    }
}
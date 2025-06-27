package com.dogancaglar.payment.application.mapper

import com.dogancaglar.application.PaymentOrderCreated
import com.dogancaglar.application.PaymentOrderRetryRequested
import com.dogancaglar.application.PaymentOrderStatusCheckRequested
import com.dogancaglar.application.PaymentOrderSucceeded
import com.dogancaglar.payment.domain.model.PaymentOrder
import java.time.LocalDateTime

object PaymentOrderEventMapper {
    fun toPaymentOrderRetryRequestEvent(
        order: PaymentOrder,
        newRetryCount: Int,
        retryReason: String? = "UNKNOWN",
        lastErrorMessage: String? = "N/A"
    ): PaymentOrderRetryRequested {
        return PaymentOrderRetryRequested(
            paymentOrderId = order.paymentOrderId.value.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.toString(),
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId.value,
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
            paymentOrderId = order.paymentOrderId.value.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.value.toString(),
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId.value,
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
            sellerId = order.sellerId.value,
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
            sellerId = order.sellerId.value,
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
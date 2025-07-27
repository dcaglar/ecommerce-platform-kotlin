package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.PaymentOrderEvent
import com.dogancaglar.paymentservice.domain.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.domain.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import java.time.LocalDateTime

object PaymentOrderDomainEventMapper {
    fun toPaymentOrderRetryRequestEvent(
        order: PaymentOrder,
        newRetryCount: Int,
        retryReason: String? = "UNKNOWN",
        lastErrorMessage: String? = "N/A"
    ): PaymentOrderRetryRequested {
        return PaymentOrderRetryRequested(
            paymentOrderId = order.paymentOrderId.value.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.value.toString(),
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


    fun fromEvent(event: PaymentOrderEvent): PaymentOrder =
        PaymentOrder.reconstructFromPersistence(
            paymentOrderId = PaymentOrderId(event.paymentOrderId.toLong()),
            publicPaymentOrderId = event.publicPaymentOrderId,
            paymentId = PaymentId(event.paymentId.toLong()),
            publicPaymentId = event.publicPaymentId,
            sellerId = SellerId(event.sellerId),
            amount = Amount(event.amountValue, event.currency),
            status = PaymentOrderStatus.valueOf(event.status),
            createdAt = event.createdAt,
            updatedAt = event.updatedAt,
            retryCount = event.retryCount,
            retryReason = event.retryReason,
            lastErrorMessage = event.lastErrorMessage
        )


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

    fun copyWithStatus(event: PaymentOrderCreated, newStatus: String): PaymentOrderCreated {
        return event.copy(
            status = newStatus,
            // Optionally update other fields if needed, e.g., updatedAt = LocalDateTime.now()
        )
    }


}
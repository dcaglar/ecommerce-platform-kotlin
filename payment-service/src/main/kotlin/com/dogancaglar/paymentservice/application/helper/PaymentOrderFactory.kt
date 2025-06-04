package com.dogancaglar.paymentservice.application.helper

import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.application.event.PaymentOrderEvent
import java.time.LocalDateTime

class PaymentOrderFactory {
    fun fromEvent(event: PaymentOrderEvent): PaymentOrder =
        PaymentOrder.reconstructFromPersistence(
            paymentOrderId = event.paymentOrderId.toLong(),
            publicPaymentOrderId = event.publicPaymentOrderId,
            paymentId = event.paymentId.toLong(),
            publicPaymentId = event.publicPaymentId,
            sellerId = event.sellerId,
            amount = Amount(event.amountValue, event.currency),
            status = PaymentOrderStatus.valueOf(event.status),
            createdAt = event.createdAt,
            updatedAt = event.updatedAt,
            retryCount = event.retryCount,
            retryReason = event.retryReason,
            lastErrorMessage = event.lastErrorMessage
        )
}
package com.dogancaglar.paymentservice.application.helper

import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class PaymentOrderReconstructor {

    fun fromCreatedEvent(event: PaymentOrderCreated): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = event.paymentOrderId.toLong(),
            publicPaymentOrderId = event.publicPaymentOrderId,
            paymentId = event.paymenId.toLong(),
            publicPaymentId = event.publicPaymentId,
            sellerId = event.sellerId,
            amount = Amount(event.amountValue, event.currency),
            status = PaymentOrderStatus.valueOf(event.status),
            createdAt = event.createdAt,
            updatedAt = LocalDateTime.now(), // or event.updatedAt if you trust it
            retryCount = event.retryCount
        )
    }

    fun fromRetryRequestedEvent(event: PaymentOrderRetryRequested): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = event.paymentOrderId.toLong(),
            publicPaymentOrderId = event.publicPaymentOrderId,
            paymentId = event.paymenId.toLong(),
            publicPaymentId = event.publicPaymentId,
            sellerId = event.sellerId,
            amount = Amount(event.amountValue, event.currency),
            status = PaymentOrderStatus.valueOf(event.status),
            createdAt = event.createdAt,
            updatedAt = LocalDateTime.now(), // or event.updatedAt if you trust it
            retryCount = event.retryCount
        )
    }
}
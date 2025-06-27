package com.dogancaglar.paymentservice.application.mapper

import com.dogancaglar.payment.application.events.PaymentOrderEvent
import com.dogancaglar.payment.domain.model.Amount
import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.PaymentOrderStatus
import com.dogancaglar.payment.domain.model.vo.PaymentId
import com.dogancaglar.payment.domain.model.vo.PaymentOrderId
import com.dogancaglar.payment.domain.model.vo.SellerId
import org.springframework.stereotype.Component

@Component
class PaymentOrderEventMapper {
    fun mapEventToDomain(event: PaymentOrderEvent): PaymentOrder {
        return fromEvent(event)
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
}
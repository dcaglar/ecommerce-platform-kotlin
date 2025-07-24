package com.dogancaglar.payment.domain.factory

import com.dogancaglar.payment.domain.PaymentOrderEvent
import com.dogancaglar.payment.domain.model.Amount
import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.PaymentOrderStatus
import com.dogancaglar.payment.domain.model.vo.PaymentId
import com.dogancaglar.payment.domain.model.vo.PaymentOrderId
import com.dogancaglar.payment.domain.model.vo.SellerId

class PaymentOrderFactory {
    fun fromEvent(event: PaymentOrderEvent): PaymentOrder {
        return PaymentOrder(
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
}
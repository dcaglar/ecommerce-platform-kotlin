package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.paymentservice.domain.event.PaymentOrderEvent
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId

class PaymentOrderFactory {

    fun fromEvent(event: PaymentOrderEvent): PaymentOrder =
        PaymentOrder.rehydrate(
            paymentOrderId = PaymentOrderId(event.paymentOrderId.toLong()),
            paymentId = PaymentId(event.paymentId.toLong()),
            sellerId = SellerId(event.sellerId),
            amount = Amount.of(event.amountValue, Currency(event.currency)),
            status = PaymentOrderStatus.valueOf(event.status),
            retryCount = event.retryCount,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt
        )


}
package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.event.PaymentOrderEvent
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId

class PaymentOrderFactory {

    fun fromEvent(event: PaymentOrderEvent): PaymentOrder =
        PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(event.paymentOrderId.toLong()))
            .publicPaymentOrderId(event.publicPaymentOrderId)
            .paymentId(PaymentId(event.paymentId.toLong()))
            .publicPaymentId(event.publicPaymentId)
            .sellerId(SellerId(event.sellerId))
            .amount(Amount(event.amountValue, event.currency))
            .status(PaymentOrderStatus.valueOf(event.status))
            .createdAt(event.createdAt)
            .updatedAt(event.updatedAt)
            .retryCount(event.retryCount)
            .retryReason(event.retryReason)
            .lastErrorMessage(event.lastErrorMessage)
            .buildFromPersistence()
}
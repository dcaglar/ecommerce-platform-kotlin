package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import java.time.Clock
import java.time.LocalDateTime

class PaymentFactory(
    private val clock: Clock
) {

    fun createPayment(
        cmd: CreatePaymentCommand,
        paymentId: PaymentId,
        paymentOrderIds: List<PaymentOrderId>
    ): Payment {
        val now = LocalDateTime.now(clock)
        val publicPaymentId = "payment-${paymentId.value}"

        val orders = cmd.paymentLines.zip(paymentOrderIds).map { (line, orderId) ->
            PaymentOrder.builder()
                .paymentOrderId(orderId)
                .publicPaymentOrderId("paymentorder-${orderId.value}")
                .paymentId(paymentId)
                .publicPaymentId(publicPaymentId)
                .sellerId(line.sellerId)
                .amount(line.amount)
                .createdAt(now)
                .buildNew()
        }

        return Payment.builder()
            .paymentId(paymentId)
            .publicPaymentId(publicPaymentId)
            .buyerId(cmd.buyerId)
            .orderId(cmd.orderId)
            .totalAmount(cmd.totalAmount)
            .createdAt(now)
            .paymentOrders(orders)
            .buildNew()
    }
}
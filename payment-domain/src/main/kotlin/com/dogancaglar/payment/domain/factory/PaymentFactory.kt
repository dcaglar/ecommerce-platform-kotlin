package com.dogancaglar.payment.domain.factory

import com.dogancaglar.payment.domain.model.CreatePaymentCommand
import com.dogancaglar.payment.domain.model.Payment
import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.vo.PaymentId
import com.dogancaglar.payment.domain.model.vo.PaymentOrderId
import java.time.Clock
import java.time.LocalDateTime

class PaymentFactory(
    private val clock: Clock
) {


    fun createPayment(
        cmd: CreatePaymentCommand,
        paymentId: PaymentId,
        paymentOrderIdList: List<PaymentOrderId>
    ): Payment {
        val now = LocalDateTime.now(clock)
        val publicPaymentId = "payment-${paymentId.value}"
        val orders = cmd.paymentLines.zip(paymentOrderIdList).map { (line, paymentOrderId) ->
            PaymentOrder.createNew(
                paymentOrderId = paymentOrderId,
                publicPaymentOrderId = "paymentorder-${paymentOrderId.value}",
                paymentId = paymentId,
                publicPaymentId = publicPaymentId,
                sellerId = line.sellerId,
                amount = line.amount,
                createdAt = now
            )
        }
        return Payment.createNew(
            paymentId = paymentId,
            publicPaymentId = publicPaymentId,
            orderId = cmd.orderId,
            buyerId = cmd.buyerId,
            totalAmount = cmd.totalAmount,
            paymentOrders = orders,
            createdAt = now
        )
    }
}
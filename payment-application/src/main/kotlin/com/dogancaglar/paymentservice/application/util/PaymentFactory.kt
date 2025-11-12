package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.event.PaymentAuthorized
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import java.time.Clock

class PaymentFactory(
    private val clock: Clock
) {

    fun createPayment(
        cmd: CreatePaymentCommand,
        paymentId: PaymentId,
    ): Payment {
        return Payment.createNew(paymentId = paymentId,
            buyerId = cmd.buyerId,
            orderId = cmd.orderId,
            totalAmount = cmd.totalAmount,
            clock = clock
            )

    }

    fun createPayment(paymentAuthorized: PaymentAuthorized): Payment{
        return Payment.createNew(PaymentId(paymentAuthorized.paymentId.toLong()),
            buyerId = BuyerId(paymentAuthorized.buyerId),
            orderId= OrderId(paymentAuthorized.orderId),
            totalAmount = Amount.of(paymentAuthorized.totalAmountValue, Currency(paymentAuthorized.currency)),
            clock =clock)
    }
}
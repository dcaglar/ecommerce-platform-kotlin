package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Payment
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
}
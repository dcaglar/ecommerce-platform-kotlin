package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.domain.commands.GetPaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.PaymentIntent

interface GetPaymentIntentUseCase {
    fun getPaymentIntent(cmd: GetPaymentIntentCommand): PaymentIntent
}


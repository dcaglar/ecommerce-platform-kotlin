package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.domain.commands.AuthorizePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentIntent

interface AuthorizePaymentIntentUseCase {
    fun authorize(cmd: AuthorizePaymentIntentCommand): PaymentIntent
}
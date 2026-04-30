package com.dogancaglar.paymentservice.ports.inbound.usecases

import com.dogancaglar.paymentservice.application.command.AuthorizePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.PaymentIntent

interface AuthorizePaymentIntentUseCase {
    fun authorize(cmd: AuthorizePaymentIntentCommand): PaymentIntent
}
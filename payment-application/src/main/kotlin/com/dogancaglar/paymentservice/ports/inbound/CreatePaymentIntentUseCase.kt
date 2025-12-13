package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentIntent

interface CreatePaymentIntentUseCase {
    fun create(cmd: CreatePaymentIntentCommand): PaymentIntent
}
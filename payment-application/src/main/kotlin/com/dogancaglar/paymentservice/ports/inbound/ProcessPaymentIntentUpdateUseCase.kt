package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.domain.commands.ProcessPaymentIntentUpdateCommand
import com.dogancaglar.paymentservice.domain.model.PaymentIntent

interface ProcessPaymentIntentUpdateUseCase {
    fun processUpdate(cmd: ProcessPaymentIntentUpdateCommand): PaymentIntent
}
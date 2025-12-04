package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Payment

interface CreatePaymentUseCase {
    fun create(cmd: CreatePaymentCommand): Payment
}
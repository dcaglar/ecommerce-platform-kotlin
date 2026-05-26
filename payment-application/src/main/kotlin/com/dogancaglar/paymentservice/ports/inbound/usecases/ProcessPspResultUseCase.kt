package com.dogancaglar.paymentservice.ports.inbound.usecases

import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder


interface ProcessPspResultUseCase {
    fun processPspResult(event: PaymentOrderPspResultUpdated, paymentOrder: PaymentOrder)
}
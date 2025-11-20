package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus


interface ProcessPspResultUseCase {
    fun processPspResult(event: PaymentOrderPspResultUpdated, paymentOrder: PaymentOrder)
}
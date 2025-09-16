package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.domain.event.PaymentOrderEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus


interface ProcessPspResultUseCase {
    fun processPspResult(event: PaymentOrderEvent, pspStatus: PaymentOrderStatus)
}
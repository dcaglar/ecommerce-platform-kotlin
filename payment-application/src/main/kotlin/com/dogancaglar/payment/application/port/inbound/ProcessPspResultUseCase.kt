package com.dogancaglar.payment.application.port.outbound

import com.dogancaglar.payment.application.events.PaymentOrderEvent
import com.dogancaglar.payment.domain.model.PaymentOrderStatus


interface ProcessPspResultUseCase {
    fun processPspResult(event: PaymentOrderEvent, pspStatus: PaymentOrderStatus)
}
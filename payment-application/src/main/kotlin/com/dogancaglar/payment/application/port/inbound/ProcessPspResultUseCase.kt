package com.dogancaglar.payment.application.port.inbound

import com.dogancaglar.payment.domain.PaymentOrderEvent
import com.dogancaglar.payment.domain.model.PaymentOrderStatus


interface ProcessPspResultUseCase {
    fun processPspResult(event: PaymentOrderEvent, pspStatus: PaymentOrderStatus)
}
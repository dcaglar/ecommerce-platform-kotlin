package com.dogancaglar.paymentservice.ports.inbound.usecases

import com.dogancaglar.paymentservice.application.events.CaptureConfirmed
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized

interface ProcessPspResultUseCase {

    fun processAuthorized(event: PaymentAuthorized)

    fun processCaptureConfirmed(event: CaptureConfirmed)

}
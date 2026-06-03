package com.dogancaglar.paymentservice.ports.inbound.usecases

import com.dogancaglar.paymentservice.application.events.CaptureRequested

interface ExecuteCaptureUseCase {
    fun execute(captureRequested: CaptureRequested)
}

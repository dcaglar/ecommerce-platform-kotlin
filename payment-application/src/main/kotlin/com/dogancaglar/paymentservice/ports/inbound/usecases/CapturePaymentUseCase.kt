package com.dogancaglar.paymentservice.ports.inbound.usecases

import com.dogancaglar.paymentservice.application.command.CapturePaymentCommand
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder

interface CapturePaymentUseCase {
     fun capture(cmd: CapturePaymentCommand): PaymentOrder?

}
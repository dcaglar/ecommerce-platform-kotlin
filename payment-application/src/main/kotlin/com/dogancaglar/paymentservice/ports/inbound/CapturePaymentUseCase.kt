package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.domain.commands.CapturePaymentCommand
import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder

interface CapturePaymentUseCase {
     fun capture(cmd: CapturePaymentCommand): PaymentOrder?

}
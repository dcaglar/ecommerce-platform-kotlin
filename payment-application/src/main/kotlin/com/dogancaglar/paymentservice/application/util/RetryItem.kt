package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.command.PaymentOrderCaptureCommand

data class RetryItem(
    val envelope: EventEnvelope<PaymentOrderCaptureCommand>,
    val raw: ByteArray
)
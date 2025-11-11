package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.commands.PaymentOrderCaptureCommand

data class RetryItem(
    val envelope: EventEnvelope<PaymentOrderCaptureCommand>,
    val raw: ByteArray
)
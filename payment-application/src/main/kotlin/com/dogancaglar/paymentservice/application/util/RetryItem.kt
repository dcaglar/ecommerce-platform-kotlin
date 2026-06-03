package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.events.CaptureReceived

data class RetryItem(
    val envelope: EventEnvelope<CaptureReceived>,
    val raw: ByteArray
)
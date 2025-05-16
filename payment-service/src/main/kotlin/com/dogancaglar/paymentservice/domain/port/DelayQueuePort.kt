package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.common.event.EventEnvelope
import java.time.Duration

interface DelayQueuePort {
    fun <T> schedule(eventEnvelope: EventEnvelope<T>, delay: Duration)
}
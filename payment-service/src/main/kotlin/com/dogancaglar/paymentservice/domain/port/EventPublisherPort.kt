package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.common.event.EventEnvelope


interface EventPublisherPort {
    fun <T> publish(
        event: String,
        aggregateId: String,
        data: T,
        parentEnvelope: EventEnvelope<*>? = null
    ): EventEnvelope<T>
}

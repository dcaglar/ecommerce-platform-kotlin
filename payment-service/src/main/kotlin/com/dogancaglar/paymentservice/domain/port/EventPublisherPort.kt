package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata


interface EventPublisherPort {
    fun <T> publish(
        event: EventMetadata<T>,
        aggregateId: String,
        data: T,
        parentEnvelope: EventEnvelope<*>?=null
    ): EventEnvelope<T>
}

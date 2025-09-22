package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import java.time.Duration
import java.util.*

interface EventPublisherPort {
    fun <T> publish(
        preSetEventIdFromCaller: UUID? = null,
        aggregateId: String,
        eventMetaData: EventMetadata<T>,
        data: T,
        traceId: String? = null,
        parentEventId: UUID? = null
    ): EventEnvelope<T>


    fun <T> publishBatchAtomically(
        envelopes: List<EventEnvelope<*>>,
        eventMetaData: EventMetadata<T>,
        timeout: Duration = Duration.ofSeconds(30)
    ): Boolean
    /**
     * Publishes an event synchronously (blocking until confirmation).
     */
    fun <T> publishSync(
        preSetEventIdFromCaller: UUID? = null,
        aggregateId: String,
        eventMetaData: EventMetadata<T>,
        data: T,
        traceId: String? = null,
        parentEventId: UUID? = null,
        timeoutSeconds: Long = 5
    ): EventEnvelope<T>

}


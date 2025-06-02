package com.dogancaglar.common.event

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import org.slf4j.MDC
import java.util.*


object DomainEventEnvelopeFactory {
    fun <T> envelopeFor(
        data: T,
        eventType: EventMetadata<T>,
        aggregateId: String,
        parentEventId: UUID? = null,
        traceId :String,
    ): EventEnvelope<T> {
        val eventId = UUID.randomUUID()
        return EventEnvelope(
            traceId = traceId,
            eventId = eventId,
            parentEventId = parentEventId,
            eventType = eventType.eventType,
            aggregateId = aggregateId,
            data = data
        )
    }
}
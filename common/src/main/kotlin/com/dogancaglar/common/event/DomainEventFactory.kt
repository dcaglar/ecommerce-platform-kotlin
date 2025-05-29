package com.dogancaglar.common.event

import com.dogancaglar.common.logging.LogFields
import org.slf4j.MDC
import java.util.*

object DomainEventFactory {

    fun <T> envelopeFor(
        event: T,
        eventType: String,
        aggregateId: String,
        traceId: String? = null,
        parentEventId: UUID? = null
    ): EventEnvelope<T> {
        val resolvedTraceId = traceId ?: MDC.get(LogFields.TRACE_ID) ?: UUID.randomUUID().toString()
        val eventId = UUID.randomUUID()

        return EventEnvelope(
            traceId = resolvedTraceId,
            eventId = eventId,
            parentEventId = parentEventId,
            eventType = eventType,
            aggregateId = aggregateId,
            data = event
        )
    }
}
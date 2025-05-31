package com.dogancaglar.common.event

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import org.slf4j.MDC
import java.util.*


/*
/**
 * Creates a new EventEnvelope with optional trace context.
 *
 * @param traceId Optional trace ID; falls back to MDC or random UUID.
 * @param parentEventId Optional parent event ID for event chaining.
 */
 */
object DomainEventEnvelopeFactory {
    fun <T> envelopeFor(
        data: T,
        eventType: EventMetadata<T>,
        aggregateId: String,
        parentEventId: UUID? = null
    ): EventEnvelope<T> {
        val eventId = UUID.randomUUID()
        val traceId = LogContext.getTraceId() ?: error("Missing traceId")
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
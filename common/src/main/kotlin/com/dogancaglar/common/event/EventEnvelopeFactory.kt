package com.dogancaglar.common.event

import java.time.LocalDateTime
import java.util.UUID

object EventEnvelopeFactory {

    fun <T : Event> envelopeFor(
        data: T,
        aggregateId: String,
        traceId: String,
        parentEventId: String? = null,
        timestamp: LocalDateTime = LocalDateTime.now()
    ): EventEnvelope<T> {
        val id = data.deterministicEventId()
        val resolvedParent = parentEventId ?: id

        return EventEnvelope(
            eventId = id,
            eventType = data.eventType,
            aggregateId = aggregateId,
            data = data,
            timestamp = timestamp,
            traceId = traceId,
            parentEventId = resolvedParent
        )
    }

    /**
     * Fallback factory when you explicitly do NOT want deterministic IDs
     * (e.g. fire-and-forget events).
     */
    fun <T : Event> envelopeWithRandomId(
        data: T,
        aggregateId: String,
        traceId: String,
        parentEventId: String? = null,
        timestamp: LocalDateTime = LocalDateTime.now()
    ): EventEnvelope<T> {
        val id = UUID.randomUUID().toString()
        val resolvedParent = parentEventId ?: id

        return EventEnvelope(
            eventId = id,
            eventType = data.eventType,
            aggregateId = aggregateId,
            data = data,
            timestamp = timestamp,
            traceId = traceId,
            parentEventId = resolvedParent
        )
    }
}
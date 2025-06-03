package com.dogancaglar.common.event

import java.util.*


object DomainEventEnvelopeFactory {
    fun <T> envelopeFor(
        preSetEventId: UUID?=null,
        data: T,
        eventMetaData: EventMetadata<T>,
        aggregateId: String,
        parentEventId: UUID?=null,
        traceId :String,
    ): EventEnvelope<T> {
        val eventId = preSetEventId ?: UUID.randomUUID()
        val resolvedParentId = parentEventId ?: eventId //if parent not passed then its the initiator i.e When we first emit PAymentOrderCreated there is no parent so we assing eventid
        return EventEnvelope(
            traceId = traceId,
            eventId = eventId,
            parentEventId = resolvedParentId,
            eventType = eventMetaData.eventType,
            aggregateId = aggregateId,
            data = data
        )
    }
}
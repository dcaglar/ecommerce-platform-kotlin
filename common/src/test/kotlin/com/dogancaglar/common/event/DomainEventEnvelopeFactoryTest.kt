package com.dogancaglar.common.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.*

class DomainEventEnvelopeFactoryTest {
    class DummyClass

    val dummyEventMetadata = object : EventMetadata<DummyClass> {
        override val topic = "dummy-topic"
        override val eventType = "TestEvent"
        override val clazz = DummyClass::class.java
        override val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<EventEnvelope<DummyClass>>() {}
    }

    @Test
    fun `envelopeFor uses provided eventId and parentEventId`() {
        val eventId = UUID.randomUUID()
        val parentEventId = UUID.randomUUID()
        val data = DummyClass()
        val aggregateId = "agg-1"
        val traceId = "trace-1"

        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = eventId,
            data = data,
            eventMetaData = dummyEventMetadata,
            aggregateId = aggregateId,
            parentEventId = parentEventId,
            traceId = traceId
        )

        assertEquals(eventId, envelope.eventId)
        assertEquals(parentEventId, envelope.parentEventId)
        assertEquals(dummyEventMetadata.eventType, envelope.eventType)
        assertEquals(aggregateId, envelope.aggregateId)
        assertEquals(data, envelope.data)
        assertEquals(traceId, envelope.traceId)
    }

    @Test
    fun `envelopeFor generates eventId and uses it as parentEventId if not provided`() {
        val data = DummyClass()
        val aggregateId = "agg-2"
        val traceId = "trace-2"

        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = data,
            eventMetaData = dummyEventMetadata,
            aggregateId = aggregateId,
            traceId = traceId
        )

        assertNotNull(envelope.eventId)
        assertEquals(envelope.eventId, envelope.parentEventId)
        assertEquals(dummyEventMetadata.eventType, envelope.eventType)
        assertEquals(aggregateId, envelope.aggregateId)
        assertEquals(data, envelope.data)
        assertEquals(traceId, envelope.traceId)
    }
}

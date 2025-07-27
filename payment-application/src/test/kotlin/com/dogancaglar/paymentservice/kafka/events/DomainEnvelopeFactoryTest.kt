package com.dogancaglar.common.event

import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.*

class DummyClass


class DomainEventEnvelopeFactoryTest {
    @Test
    fun `envelopeFor uses provided eventId and parentEventId`() {
        val eventId = UUID.randomUUID()
        val parentEventId = UUID.randomUUID()
        val meta = object : EventMetadata<DummyClass> {
            override val topic = "dummy-topic"
            override val eventType = "TestEvent"
            override val clazz = DummyClass::class.java
            override val typeRef =
                object : TypeReference<EventEnvelope<DummyClass>>() {}
        }
        val data = DummyClass()
        val aggregateId = "agg-1"
        val traceId = "trace-1"

        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = eventId,
            data = data,
            eventMetaData = meta,
            aggregateId = aggregateId,
            parentEventId = parentEventId,
            traceId = traceId
        )

        assertEquals(eventId, envelope.eventId)
        assertEquals(parentEventId, envelope.parentEventId)
        assertEquals(meta.eventType, envelope.eventType)
        assertEquals(aggregateId, envelope.aggregateId)
        assertEquals(data, envelope.data)
        assertEquals(traceId, envelope.traceId)
    }

    @Test
    fun `envelopeFor generates eventId and uses it as parentEventId if not provided`() {
        val meta = object : EventMetadata<DummyClass> {
            override val topic = "dummy-topic"
            override val eventType = "TestEvent"
            override val clazz = DummyClass::class.java
            override val typeRef =
                object : TypeReference<EventEnvelope<DummyClass>>() {}
        }
        val data = DummyClass()
        val aggregateId = "agg-2"
        val traceId = "trace-2"

        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = data,
            eventMetaData = meta,
            aggregateId = aggregateId,
            traceId = traceId
        )

        assertNotNull(envelope.eventId)
        assertEquals(envelope.eventId, envelope.parentEventId)
        assertEquals(meta.eventType, envelope.eventType)
        assertEquals(aggregateId, envelope.aggregateId)
        assertEquals(data, envelope.data)
        assertEquals(traceId, envelope.traceId)
    }
}

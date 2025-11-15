package com.dogancaglar.common.event

import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DummyClass


private const val DUMMY_TOPIC = "dummy-topic"

private const val DUMMY_EVENT = "dummy-event"

object DummyClassEventMetadata : EventMetadata<DummyClass> {
    override val topic = DUMMY_TOPIC
    override val eventType = DUMMY_EVENT   // ← FIX: use EVENT_TYPE
    override val clazz = DummyClass::class.java
    override val typeRef = object : TypeReference<EventEnvelope<DummyClass>>() {}
    override val partitionKeyExtractor = { evt: DummyClass ->
        "dummy-partition-key"
    }
}

class DummyClassEventMetadataTest {
    @Test
    fun `DummyClassEventMetadata has correct topic,partitionkey and eventType`() {
        val meta = DummyClassEventMetadata
        assertEquals(DUMMY_TOPIC, meta.topic)
        assertEquals(DUMMY_EVENT, meta.eventType) // ← FIX
    }

    @Test
    fun `DummyClassEventMetadata typeRef returns correct type`() {
        val meta = DummyClassEventMetadata
        val envelope = EventEnvelope(
            eventId = UUID.randomUUID(),
            parentEventId = UUID.randomUUID(),
            eventType = meta.eventType,
            aggregateId = "agg-1",
            data = DummyClass(),
            traceId = "trace-1"
        )
        val typeRef = meta.typeRef
        assertTrue(typeRef.type.typeName.contains("EventEnvelope"))
        assertTrue(typeRef.type.typeName.contains("DummyClass"))
        assertTrue(typeRef.type.typeName.contains("EventEnvelope"))
    }
}
package com.dogancaglar.paymentservice.kafka.events

import com.dogancaglar.common.event.EVENT_TYPE
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.common.event.Topics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DummyClass

object DummyClassEventMetadata : EventMetadata<DummyClass> {
    override val topic = Topics.PAYMENT_ORDER_CREATED
    override val eventType = EVENT_TYPE.PAYMENT_ORDER_CREATED   // ← FIX: use EVENT_TYPE
    override val clazz = DummyClass::class.java
    override val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<EventEnvelope<DummyClass>>() {}
}

class DummyClassEventMetadataTest {
    @Test
    fun `DummyClassEventMetadata has correct topic and eventType`() {
        val meta = DummyClassEventMetadata
        assertEquals(Topics.PAYMENT_ORDER_CREATED, meta.topic)
        assertEquals(EVENT_TYPE.PAYMENT_ORDER_CREATED, meta.eventType) // ← FIX
    }

    @Test
    fun `DummyClassEventMetadata typeRef returns correct type`() {
        val meta = DummyClassEventMetadata
        val envelope = EventEnvelope(
            eventId = java.util.UUID.randomUUID(),
            parentEventId = java.util.UUID.randomUUID(),
            eventType = meta.eventType,
            aggregateId = "agg-1",
            data = DummyClass(),
            traceId = "trace-1"
        )
        val typeRef = meta.typeRef
        assertTrue(typeRef.type.typeName.contains("EventEnvelope"))
        assertTrue(typeRef.type.typeName.contains("DummyClass"))
    }
}
package com.dogancaglar.paymentservice.kafka.events

import com.dogancaglar.common.event.CONSUMER_GROUPS.PAYMENT_ORDER_CREATED
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.common.event.TOPICS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test


class DummyClass

object DummyClassEventMetadata : EventMetadata<DummyClass> {
    override val topic = TOPICS.PAYMENT_ORDER_CREATED
    override val eventType = PAYMENT_ORDER_CREATED
    override val clazz = DummyClass::class.java
    override val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<EventEnvelope<DummyClass>>() {}
}

class DummyClassEventMetadataTest {
    @Test
    fun `DummyClassEventMetadata has correct topic and eventType`() {
        val meta = DummyClassEventMetadata
        assertEquals(TOPICS.PAYMENT_ORDER_CREATED, meta.topic)
        assertEquals(PAYMENT_ORDER_CREATED, meta.eventType)
    }

    @Test
    fun `DummyClassEventMetadata typeRef returns correct type`() {
        val meta = DummyClassEventMetadata
        val typeRef = meta.typeRef
        val envelope = EventEnvelope(
            eventId = java.util.UUID.randomUUID(),
            parentEventId = java.util.UUID.randomUUID(),
            eventType = meta.eventType,
            aggregateId = "agg-1",
            data = DummyClass(),
            traceId = "trace-1"
        )
        assertTrue(typeRef.type.typeName.contains("EventEnvelope"))
        assertTrue(typeRef.type.typeName.contains("DummyClass"))
    }
}


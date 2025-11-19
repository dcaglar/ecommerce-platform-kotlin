package com.dogancaglar.common.event.metadata

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelope
import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventMetadataRegistryTest {

    data class TestEvent(
        override val eventType: String,
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : Event {
        override fun deterministicEventId() = "id-$eventType"
    }

    object TestMetadataA : EventMetadata<TestEvent> {
        override val topic = "topic-a"
        override val eventType = "a"
        override val clazz = TestEvent::class.java
        override val typeRef = object : TypeReference<EventEnvelope<TestEvent>>() {}
        override val partitionKey = { evt: TestEvent -> "key-${evt.eventType}" }
    }

    object TestMetadataB : EventMetadata<TestEvent> {
        override val topic = "topic-b"
        override val eventType = "b"
        override val clazz = TestEvent::class.java
        override val typeRef = object : TypeReference<EventEnvelope<TestEvent>>() {}
        override val partitionKey = { evt: TestEvent -> "key2-${evt.eventType}" }
    }

    @Test
    fun `metadataFor returns correct metadata`() {
        val reg = EventMetadataRegistry(listOf(TestMetadataA, TestMetadataB))
        assertEquals("topic-a", reg.metadataFor<TestEvent>("a").topic)
    }

    @Test
    fun `metadataForEvent resolves via event`() {
        val evt = TestEvent(eventType = "b")
        val reg = EventMetadataRegistry(listOf(TestMetadataA, TestMetadataB))
        assertEquals("topic-b", reg.metadataForEvent(evt).topic)
    }

    @Test
    fun `metadataFor throws if type missing`() {
        val reg = EventMetadataRegistry(listOf(TestMetadataA))
        assertThrows<IllegalStateException> {
            reg.metadataFor<TestEvent>("missing")
        }
    }

    @Test
    fun `partitionKey function works`() {
        val evt = TestEvent("a")
        val reg = EventMetadataRegistry(listOf(TestMetadataA))
        val m = reg.metadataForEvent(evt)

        assertEquals("key-a", m.partitionKey(evt))
    }

    @Test
    fun `registry all returns all metadata`() {
        val reg = EventMetadataRegistry(listOf(TestMetadataA, TestMetadataB))
        val all = reg.all().map { it.eventType }

        assertTrue(all.contains("a"))
        assertTrue(all.contains("b"))
    }
}
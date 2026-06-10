package com.dogancaglar.common.kafka.serde

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.kafka.metadata.PaymentEventMetadataCatalog
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer

class EventEnvelopeKafkaDeserializer : Deserializer<EventEnvelope<*>> {

    val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val eventTypeMap: Map<String, TypeReference<out EventEnvelope<*>>> =
        PaymentEventMetadataCatalog.all.associate { it.eventType to it.typeRef }

    override fun deserialize(topic: String?, data: ByteArray?): EventEnvelope<*>? {
        return deserialize(topic, null, data)
    }

    override fun deserialize(topic: String?, headers: Headers?, data: ByteArray?): EventEnvelope<*>? {
        if (data == null || data.isEmpty()) return null

        val jsonNode = objectMapper.readTree(data)
        val eventType = jsonNode.get("eventType")?.asText()
            ?: throw IllegalArgumentException("Missing 'eventType' in EventEnvelope JSON for topic: $topic")

        val typeRef = eventTypeMap[eventType]
            ?: throw IllegalArgumentException("No EventMetadata mapping found for eventType: $eventType")

        return objectMapper.treeToValue(jsonNode, typeRef)
    }

    override fun close() {
        /*
         * EventEnvelopeDeserializer implements the Deserializer interface, which requires
         * the close method to be present. Even if the method body is empty, it must be
         * overridden to fulfil the interface contract.
         */
    }
}

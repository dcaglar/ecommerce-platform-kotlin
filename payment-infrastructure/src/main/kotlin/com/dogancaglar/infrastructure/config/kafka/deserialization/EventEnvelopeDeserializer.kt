package com.dogancaglar.infrastructure.config.kafka.deserialization

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.payment.domain.model.EventMetadatas
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer

class EventEnvelopeDeserializer : Deserializer<EventEnvelope<*>> {


    val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule()).registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private val topicTypeMap: Map<String, TypeReference<out EventEnvelope<*>>> =
        EventMetadatas.all.associate { it.topic to it.typeRef }


    override fun deserialize(topic: String?, data: ByteArray?): EventEnvelope<*>? {
        return deserialize(topic, null, data)
    }

    override fun deserialize(topic: String?, headers: Headers?, data: ByteArray?): EventEnvelope<*>? {
        if (data == null || data.isEmpty()) return null

        val typeRef = topicTypeMap[topic]
            ?: throw IllegalArgumentException("No EventMetadata mapping found for topic: $topic")

        return objectMapper.readValue(data, typeRef)
    }

    override fun close() {
        /*
        because EventEnvelopeDeserializer implements the Deserializer interface,
        which requires the close method to be present. Even if the method body is empty,
        it must be overridden to fulfill the interface contract.
         */
    }
}
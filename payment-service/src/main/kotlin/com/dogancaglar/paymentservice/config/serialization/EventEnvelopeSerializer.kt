package com.dogancaglar.paymentservice.config.serialization

import com.dogancaglar.common.event.EventEnvelope
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.common.serialization.Serializer

class EventEnvelopeSerializer : Serializer<EventEnvelope<*>> {
    val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule()).registerKotlinModule()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    override fun serialize(topic: String?, data: EventEnvelope<*>?): ByteArray? {
        return if (data == null) null else objectMapper.writeValueAsBytes(data)
    }
}
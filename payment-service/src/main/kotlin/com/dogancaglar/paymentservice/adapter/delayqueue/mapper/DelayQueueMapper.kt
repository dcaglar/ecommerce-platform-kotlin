package com.dogancaglar.paymentservice.adapter.delayqueue.mapper

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.delayqueue.DelayedKafkaMessageEntity
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class DelayQueueMapper(val objectMapper: ObjectMapper) {

    fun  toEntity(
        envelopeJson: String,
        topic: String,
        key: String,
        delayMillis: Long
    ): DelayedKafkaMessageEntity {
        // Deserialize the envelope to extract the eventId
        val envelope = objectMapper.readValue(
            envelopeJson,
            object : com.fasterxml.jackson.core.type.TypeReference<EventEnvelope<Any>>() {}
        )

        return DelayedKafkaMessageEntity(
            id = envelope.eventId,
            topic = topic,
            key = key,
            payload = envelopeJson,
            sendAfter = Instant.now().plusMillis(delayMillis),
            createdAt = Instant.now()
        )
    }
}
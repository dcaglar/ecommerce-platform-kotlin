package com.dogancaglar.paymentservice.adapter.kafka.producers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.common.logging.LogContext
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component


@Component
class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("myObjectMapper")
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Publishes an event with optional parent envelope for traceability.
     * val envelope = EventEnvelope.wrap(
     *     eventType = event.eventType,
     *     aggregateId = aggregateId,
     *     data = data,
     *     traceId = parentEnvelope?.traceId,
     *     parentEventId = parentEnvelope?.eventId
     * )
     */
    fun <T> publish(
        aggregateId: String,
        event: EventMetadata<T>,
        data: T,
        parentEventId: java.util.UUID? = null
    ) : EventEnvelope<T> {
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = data,
            eventType = event,
            aggregateId = aggregateId,
            parentEventId = parentEventId
        )

        LogContext.with(envelope) {
            val payload = objectMapper.writeValueAsString(envelope)
            logger.info("Publishing eventType={}, eventId={}, traceId={}, aggregateId={}",
                envelope.eventType, envelope.eventId, envelope.traceId, envelope.aggregateId
            )
            kafkaTemplate.send(event.topic, envelope.aggregateId, payload)
        }
        return envelope;
    }

}


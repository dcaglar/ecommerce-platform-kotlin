package com.dogancaglar.paymentservice.adapter.kafka.producers

import com.dogancaglar.common.event.DomainEventFactory
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
        event: EventMetadata<T>,
        aggregateId: String,
        data: T,
        parentEnvelope: EventEnvelope<*>?  // optional parent context
    ): EventEnvelope<T> {
        val envelope = DomainEventFactory.envelopeFor(
            event = data,
            eventType = event.eventType,
            aggregateId = aggregateId,
            parentEventId = parentEnvelope?.eventId
        )
        LogContext.with(envelope) {
            logger.info(
                "Publishing event: traceId={} eventType={}, aggregateId={}, eventId={}, parentEventId={}",
                envelope.traceId,
                envelope.eventType,
                envelope.aggregateId,
                envelope.eventId,
                envelope.parentEventId,

                )
            try {
                val json = objectMapper.writeValueAsString(envelope)
                kafkaTemplate.send(event.topic, aggregateId, json)
            } catch (e: Exception) {
                logger.error("❌ Failed to publish event to topic=${event.topic}, key=$aggregateId", e)
                throw RuntimeException("Failed to publish event to Kafka", e)
            }

        }
        return envelope;

    }

}


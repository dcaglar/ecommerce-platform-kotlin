package com.dogancaglar.paymentservice.adapter.kafka.producers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component



@Component
class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("myObjectMapper")
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(PaymentEventPublisher::class.java)

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
        parentEnvelope: EventEnvelope<*>? = null // optional parent context
    ) : EventEnvelope<T>{
        MDC.put("traceId", parentEnvelope?.traceId ?: "generated-${System.currentTimeMillis()}")
        MDC.put("eventType", event.eventType)
        MDC.put("aggregateId", aggregateId)

        val envelope = EventEnvelope.wrap(
            eventType = event.eventType,
            aggregateId = aggregateId,
            data = data,
            traceId = parentEnvelope?.traceId,
            parentEventId = parentEnvelope?.eventId
        )

        try {
            val json = objectMapper.writeValueAsString(envelope)
            kafkaTemplate.send(event.topic, aggregateId, json)
            logger.info(
                "📦 Published event to topic='${event.topic}', key='$aggregateId', type='${event.eventType}', traceId='${envelope.traceId}'"
            )
            return envelope;
        } catch (e: Exception) {
            logger.error("❌ Failed to publish event to topic=${event.topic}, key=$aggregateId", e)
            throw RuntimeException("Failed to publish event to Kafka", e)
        } finally {
            MDC.clear()
        }
    }
}
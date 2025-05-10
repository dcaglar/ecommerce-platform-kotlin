package com.dogancaglar.paymentservice.adapter.kafka

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(PaymentEventPublisher::class.java)

    fun <T> publish(topic: String, aggregateId: String, eventType: String, data: T) {
        val envelope = EventEnvelope.wrap(
            eventType = eventType,
            aggregateId = aggregateId,
            data = data // <-- keep this as the raw object
        )
          envelope.data
        try {
            logger.info("Event JSON: $envelope")
            kafkaTemplate.send(topic, aggregateId, objectMapper.writeValueAsString(envelope))
            logger.info("Published event to topic=$topic, key=$aggregateId, type=$eventType")
        } catch (e: Exception) {
            logger.error("Failed to publish event to topic=$topic, key=$aggregateId", e)
            throw RuntimeException("Failed to publish event to Kafka", e)
        }
    }

    fun <T> publishWithOutWrap(topic: String, aggregateId: String, eventType: String, data: T) {
        val envelope = EventEnvelope.wrap(
            eventType = eventType,
            aggregateId = aggregateId,
            data = data // <-- keep this as the raw object
        )

        try {
            val payload = objectMapper.writeValueAsString(envelope.data)
            logger.info("Event JSON: $payload")
            kafkaTemplate.send(topic, aggregateId, payload)
            logger.info("Published event to topic=$topic, key=$aggregateId, type=$eventType")
        } catch (e: Exception) {
            logger.error("Failed to publish event to topic=$topic, key=$aggregateId", e)
            throw RuntimeException("Failed to publish event to Kafka", e)
        }
    }
}
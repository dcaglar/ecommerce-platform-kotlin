package com.dogancaglar.paymentservice.adapter.kafka

import com.dogancaglar.common.event.EventEnvelope
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
            data = data
        )

        try {
            val payload = objectMapper.writeValueAsString(envelope)
            kafkaTemplate.send(topic, aggregateId, payload)
            logger.info("Published event to topic=$topic, key=$aggregateId, type=$eventType")
        } catch (e: Exception) {
            logger.error("Failed to publish event to topic=$topic, key=$aggregateId", e)
            throw RuntimeException("Failed to publish event to Kafka", e)
        }
    }
}
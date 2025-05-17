package com.dogancaglar.paymentservice.adapter.kafka

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("myObjectMapper")
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(PaymentEventPublisher::class.java)

    fun <T> publish(event: EventMetadata<T>, aggregateId: String, data: T) {
        val envelope = EventEnvelope.wrap(
            eventType = event.eventType,
            aggregateId = aggregateId,
            data = data
        )
        try {
            val json = objectMapper.writeValueAsString(envelope)
            kafkaTemplate.send(event.topic, aggregateId, json)
            logger.info("📦 Published event to topic=${event.topic}, key=$aggregateId, type=${event.eventType}")
        } catch (e: Exception) {
            logger.error("❌ Failed to publish event to topic=${event.topic}, key=$aggregateId", e)
            throw RuntimeException("Failed to publish event to Kafka", e)
        }
    }
}
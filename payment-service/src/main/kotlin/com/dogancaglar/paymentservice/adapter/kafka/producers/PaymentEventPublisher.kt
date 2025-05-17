package com.dogancaglar.paymentservice.adapter.kafka.producers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
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
    private val logger = LoggerFactory.getLogger(PaymentEventPublisher::class.java)

    fun <T> publish(event: EventMetadata<T>, aggregateId: String, data: T,) {
        val envelope = EventEnvelope.Companion.wrap(
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
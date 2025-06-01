package com.dogancaglar.paymentservice.adapter.kafka.producers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.common.logging.LogContext
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Publishes any domain event wrapped in [EventEnvelope] to Kafka,
 * adding correlation headers so consumer interceptors can propagate
 * trace information to the MDC before logging.
 */
@Component
class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("myObjectMapper")
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun <T> publish(
        aggregateId: String,
        event: EventMetadata<T>,
        data: T,
        parentEventId: UUID? = null
    ): EventEnvelope<T> {

        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            traceId = MDC.get("traceId") ?: UUID.randomUUID().toString(),
            data = data,
            eventType = event,
            aggregateId = aggregateId,
            parentEventId = parentEventId
        )

        LogContext.with(envelope) {
            val payload = objectMapper.writeValueAsString(envelope)
            logger.info(
                "Publishing eventType={}, eventId={}, traceId={}, aggregateId={}",
                envelope.eventType,
                envelope.eventId,
                envelope.traceId,
                envelope.aggregateId
            )
            val record = ProducerRecord<String, String>(
                event.topic,
                envelope.aggregateId,  // key
                payload
            ).apply {
                headers().add(
                    RecordHeader(
                        "traceId",
                        envelope.traceId.toByteArray(StandardCharsets.UTF_8)
                    )
                )
                headers().add(
                    RecordHeader(
                        "eventId",
                        envelope.eventId.toString().toByteArray(StandardCharsets.UTF_8)
                    )
                )
                envelope.parentEventId?.let {
                    headers().add(
                        RecordHeader(
                            "parentEventId",
                            it.toString().toByteArray(StandardCharsets.UTF_8)
                        )
                    )
                }
            }
            val future = kafkaTemplate.send(record)
            future.whenComplete { _, ex ->
                if (ex == null) {
                    logger.info("📨 Event published to topic={} eventId={}", event.topic, envelope.eventId)
                } else {
                    logger.error(
                        "❌ Failed to publish eventId={} to topic={}: {}",
                        envelope.eventId,
                        event.topic,
                        ex.message,
                        ex
                    )
                }
            }
        }
        return envelope
    }
}


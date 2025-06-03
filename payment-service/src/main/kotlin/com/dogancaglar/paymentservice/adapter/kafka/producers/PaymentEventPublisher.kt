package com.dogancaglar.paymentservice.adapter.kafka.producers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.common.logging.LogContext
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
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
        //if we are publishing a follwup event, then just call  with dont set trace or parentEventId they are already in predecesor evet
        //if parentid is not being passsed that means its the outboxenvelope intiating the transaction
    fun <T> publish(
        preSetEventIdFromCaller:UUID?=null,
        aggregateId: String,
        event: EventMetadata<T>,
        data: T,
        traceId:String?=null,
        parentEventId:UUID?=null
    ): EventEnvelope<T> {
        val preSetEventId = preSetEventIdFromCaller
        val resolvedTraceId = traceId ?: LogContext.getTraceId() ?: error("Missing traceId: either pass explicitly or set via LogContext.with(...)")
        val resolvedParentId = parentEventId ?: LogContext.getEventId()
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId= preSetEventId,
            traceId = resolvedTraceId,
            data = data,
            eventMetaData = event,
            aggregateId = aggregateId,
            parentEventId = resolvedParentId
        )

        LogContext.with(envelope) {
            val payload = objectMapper.writeValueAsString(envelope)
            logger.info("Published EventEnvelop {\n $payload + \n")
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


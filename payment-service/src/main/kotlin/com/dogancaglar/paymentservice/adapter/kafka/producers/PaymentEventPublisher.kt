package com.dogancaglar.paymentservice.adapter.kafka.producers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.common.logging.LogContext
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Publishes any domain event wrapped in [EventEnvelope] to Kafka,
 * adding correlation headers so consumer interceptors can propagate
 * trace information to the MDC before logging.
 */
@Component
class PaymentEventPublisher(
    @Qualifier("paymentOrderEventKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>,
    private val meterRegistry: MeterRegistry    // Inject here!
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    //if we are publishing a follwup event, then just call  with dont set trace or parentEventId they are already in predecesor evet
    //if parentid is not being passsed that means its the outboxenvelope intiating the transaction
    fun <T> publish(
        preSetEventIdFromCaller: UUID? = null,
        aggregateId: String,
        eventMetaData: EventMetadata<T>,
        data: T,
        traceId: String? = null,
        parentEventId: UUID? = null
    ): EventEnvelope<T> {
        val preSetEventId = preSetEventIdFromCaller
        val resolvedTraceId = traceId ?: LogContext.getTraceId()
        ?: error("Missing traceId: either pass explicitly or set via LogContext.with(...)")
        val resolvedParentId = parentEventId ?: LogContext.getEventId()
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = preSetEventId,
            traceId = resolvedTraceId,
            data = data,
            eventMetaData = eventMetaData,
            aggregateId = aggregateId,
            parentEventId = resolvedParentId
        )

        LogContext.with(envelope) {
            logger.info(
                envelope.eventType,
                envelope.eventId,
                envelope.parentEventId,
                envelope.traceId,
                envelope.aggregateId
            )
            val record = ProducerRecord<String, EventEnvelope<*>>(
                eventMetaData.topic,
                envelope.aggregateId,  // key
                envelope
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
            logger.debug("Sending payment $record.")
            val future = kafkaTemplate.send(record)
            future.whenComplete { _, ex ->
                if (ex == null) {
                    logger.debug("📨 Event published to topic={} eventId={}", eventMetaData.topic, envelope.eventId)
                } else {
                    logger.error(
                        "❌ Failed to publish eventId={} to topic={}: {}",
                        envelope.eventId,
                        eventMetaData.topic,
                        ex.message,
                        ex
                    )
                }
            }
        }
        return envelope
    }
}

package com.dogancaglar.paymentservice.adapter.outbound.kafka

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Publishes any domain event wrapped in [EventEnvelope] to Kafka,
 * adding correlation headers so consumer interceptors can propagate
 * trace information to the MDC before logging.
 */

@Component
class PaymentEventPublisher(
    @Qualifier("paymentOrderEventKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>,
    private val meterRegistry: MeterRegistry
) : EventPublisherPort {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun <T> publish(
        preSetEventIdFromCaller: UUID?,
        aggregateId: String,
        eventMetaData: EventMetadata<T>,
        data: T,
        traceId: String?,
        parentEventId: UUID?
    ): EventEnvelope<T> {
        val envelope = buildEnvelope(
            preSetEventIdFromCaller, aggregateId, eventMetaData, data, traceId, parentEventId
        )
        LogContext.with(envelope) {
            logger.info(
                envelope.eventType,
                envelope.eventId,
                envelope.parentEventId,
                envelope.traceId,
                envelope.aggregateId
            )
            val record = buildRecord(eventMetaData, envelope)
            logger.info("PUBLISHING EVENT")
            val future = kafkaTemplate.send(record)
            future.whenComplete { _, ex ->
                if (ex == null) {
                    logger.info(
                        "📨 Event published to traceid logcontext.traceid=${LogContext.getTraceId()} traceIdFromEnvelope=${envelope.traceId},parentEventId=${envelope.parentEventId}"
                    )
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

    override fun <T> publishSync(
        preSetEventIdFromCaller: UUID?,
        aggregateId: String,
        eventMetaData: EventMetadata<T>,
        data: T,
        traceId: String?,
        parentEventId: UUID?,
        timeoutSeconds: Long
    ): EventEnvelope<T> {
        val envelope = buildEnvelope(
            preSetEventIdFromCaller, aggregateId, eventMetaData, data, traceId, parentEventId
        )
        val record = buildRecord(eventMetaData, envelope)
        try {
            val future = kafkaTemplate.send(record)
            // This will throw if Kafka send fails or times out
            future.get(timeoutSeconds, TimeUnit.SECONDS)
            logger.info(
                "📨 PUBLISH SYNC SUCCEDED"
            )
        } catch (ex: Exception) {
            logger.error(
                "❌ [SYNC] Failed to publish eventId={} to topic={}: {}",
                envelope.eventId,
                eventMetaData.topic,
                ex.message,
                ex
            )
            throw ex // bubble up to handler, causing a retry
        }

        return envelope
    }

    private fun <T> buildEnvelope(
        preSetEventIdFromCaller: UUID?,
        aggregateId: String,
        eventMetaData: EventMetadata<T>,
        data: T,
        traceId: String?,
        parentEventId: UUID?
    ): EventEnvelope<T> {
        val preSetEventId = preSetEventIdFromCaller
        val resolvedTraceId = traceId ?: LogContext.getTraceId()
        ?: error("Missing traceId: either pass explicitly or set via LogContext.with(...)")
        val resolvedParentId = parentEventId ?: LogContext.getEventId()
        return DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = preSetEventId,
            traceId = resolvedTraceId,
            data = data,
            eventMetaData = eventMetaData,
            aggregateId = aggregateId,
            parentEventId = resolvedParentId
        )
    }

    private fun <T> buildRecord(
        eventMetaData: EventMetadata<T>,
        envelope: EventEnvelope<T>
    ): ProducerRecord<String, EventEnvelope<*>> =
        ProducerRecord<String, EventEnvelope<*>>(
            eventMetaData.topic,
            envelope.aggregateId,
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
}
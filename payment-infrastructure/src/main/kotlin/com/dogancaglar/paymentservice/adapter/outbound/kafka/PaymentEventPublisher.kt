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
import java.util.concurrent.TimeoutException

/**
 * Publishes any domain event wrapped in [EventEnvelope] to Kafka,
 * adding correlation headers so consumer interceptors can propagate
 * trace information to the MDC before logging.
 */

@Component
class PaymentEventPublisher(
    @Qualifier("businessEventKafkaTemplate")
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
            logger.debug(
                "Publishing event type={} id={} parentId={} traceId={} agg={}",
                envelope.eventType,
                envelope.eventId,
                envelope.parentEventId,
                envelope.traceId,
                envelope.aggregateId
            )
            val record = buildRecord(eventMetaData, envelope)
            val future = kafkaTemplate.send(record)
            future.whenComplete { _, ex ->
                if (ex == null) {
                    logger.debug(
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
        val envelope = buildEnvelope(preSetEventIdFromCaller, aggregateId, eventMetaData, data, traceId, parentEventId)
        val record = buildRecord(eventMetaData, envelope)

        LogContext.with(envelope) {
            val fut = kafkaTemplate.send(record)
            try {
                fut.get(timeoutSeconds, TimeUnit.SECONDS)
                logger.debug("📨 publishSync OK topic={} key={} eventId={}",
                    eventMetaData.topic, envelope.aggregateId, envelope.eventId)
            } catch (ex: TimeoutException) {
                logger.warn("⏱️ publishSync TIMEOUT {}s topic={} key={} eventId={}",
                    timeoutSeconds, eventMetaData.topic, envelope.aggregateId, envelope.eventId)
                throw ex
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.warn("🛑 publishSync INTERRUPTED topic={} key={} eventId={}",
                    eventMetaData.topic, envelope.aggregateId, envelope.eventId, ex)
                throw ex
            } catch (ex: java.util.concurrent.ExecutionException) {
                val rc = ex.cause ?: ex
                when (rc) {
                    is org.apache.kafka.common.errors.RetriableException,
                    is org.apache.kafka.common.errors.TransactionAbortedException -> {
                        logger.warn("🔁 RETRIABLE publishSync failure topic={} key={} eventId={} cause={}",
                            eventMetaData.topic, envelope.aggregateId, envelope.eventId, rc::class.simpleName, rc)
                    }
                    else -> {
                        logger.error("❌ NON-RETRIABLE publishSync failure topic={} key={} eventId={} cause={}: {}",
                            eventMetaData.topic, envelope.aggregateId, envelope.eventId, rc::class.simpleName, rc.message, rc)
                    }
                }
                throw rc
            }
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
            headers().add(
                RecordHeader(
                    "eventType", envelope.eventType.toByteArray(StandardCharsets.UTF_8)
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




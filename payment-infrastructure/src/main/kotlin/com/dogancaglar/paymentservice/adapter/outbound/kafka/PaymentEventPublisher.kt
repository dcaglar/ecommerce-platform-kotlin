package com.dogancaglar.paymentservice.adapter.outbound.kafka

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.metadata.EventMetadata
import com.dogancaglar.common.event.metadata.EventMetadataRegistry
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>,
    private val eventMetadataRegistry: EventMetadataRegistry,
    private val meterRegistry: MeterRegistry,
) : EventPublisherPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    // ======================================================================
    // SYNC PUBLISH
    // ======================================================================
    override fun <T : Event> publishSync(
        aggregateId: String,
        data: T,
        traceId: String,
        parentEventId: String?,
        timeoutSeconds: Long
    ): EventEnvelope<T> {
        val eventMetaData = eventMetadataRegistry.metadataForEvent(data)
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = data,
            aggregateId = aggregateId,
            traceId = traceId,
            parentEventId = parentEventId
        )

        val record = buildRecord(eventMetaData, envelope)

        EventLogContext.with(envelope) {
            try {
                kafkaTemplate.send(record).get(timeoutSeconds, TimeUnit.SECONDS)
                logger.debug(
                    "📨 publishSync OK topic={} key={} eventId={}",
                    eventMetaData.topic, envelope.aggregateId, envelope.eventId
                )
            } catch (e: TimeoutException) {
                logger.warn(
                    "⏱️ publishSync TIMEOUT {}s topic={} key={} eventId={}",
                    timeoutSeconds, eventMetaData.topic, envelope.aggregateId, envelope.eventId
                )
                throw e
            }
        }

        return envelope
    }

    // ======================================================================
    // BATCH PUBLISH
    // ======================================================================
    override fun <T : Event> publishBatchAtomically(
        envelopes: List<EventEnvelope<T>>, timeout: Duration
    ): Boolean {
        if (envelopes.isEmpty()) return true

        return try {
            kafkaTemplate.executeInTransaction<Unit> { kt ->
                val futures = envelopes.map { env ->
                    val eventMetaData = eventMetadataRegistry.metadataForEvent(env.data)
                    kt.send(buildRecord(eventMetaData, env))
                }

                java.util.concurrent.CompletableFuture
                    .allOf(*futures.toTypedArray())
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS)

                Unit
            }
            true
        } catch (t: Throwable) {
            logger.warn("❌ batch publish aborted for {} events: {}", envelopes.size, t.toString())
            false
        }
    }

    // ======================================================================
    // BUILD RECORD
    // ======================================================================
    // ======================================================================
    private fun <T : Event> buildRecord(
        metadata: EventMetadata<T>,
        envelope: EventEnvelope<T>
    ): ProducerRecord<String, EventEnvelope<*>> {

        val key: String = metadata.partitionKey(envelope.data)

        @Suppress("UNCHECKED_CAST")
        val anyEnvelope: EventEnvelope<*> = envelope as EventEnvelope<*>

        return ProducerRecord(
            metadata.topic,
            key,
            anyEnvelope
        ).apply {
            headers().addString("traceId", envelope.traceId)
            headers().addString("eventId", envelope.eventId)
            headers().addString("eventType", envelope.eventType)
            envelope.parentEventId?.let { headers().addString("parentEventId", it) }
        }
    }

    // Helper for adding string headers safely
    private fun org.apache.kafka.common.header.Headers.addString(key: String, value: String) {
        add(RecordHeader(key, value.toByteArray(StandardCharsets.UTF_8)))
    }
}
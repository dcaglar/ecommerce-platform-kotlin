package com.dogancaglar.common.kafka.publisher

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.metadata.EventMetadata
import com.dogancaglar.common.event.metadata.EventMetaDataRegistry
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
import java.util.concurrent.CompletableFuture
import org.apache.kafka.common.header.Headers

class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>,
    private val eventMetaDataRegistry: EventMetaDataRegistry,
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
        val eventMetaData = eventMetaDataRegistry.metadataForEvent(data)
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = data,
            aggregateId = aggregateId,
            traceId = traceId,
            parentEventId = parentEventId
        )

        val record = buildRecord(eventMetaData, envelope)

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

        return envelope
    }

    // ======================================================================
    // ASYNC PUBLISH
    // ======================================================================
    override fun <T : Event> publishAsync(
        envelope: EventEnvelope<T>
    ): CompletableFuture<EventEnvelope<T>> {
        val eventMetaData = eventMetaDataRegistry.metadataForEvent(envelope.data)
        val record = buildRecord(eventMetaData, envelope)

        val future = CompletableFuture<EventEnvelope<T>>()

        kafkaTemplate.send(record).whenComplete { result, ex ->
            if (ex == null) {
                logger.debug(
                    "📨 publishAsync OK topic={} key={} eventId={}",
                    eventMetaData.topic, envelope.aggregateId, envelope.eventId
                )
                future.complete(envelope)
            } else {
                logger.warn(
                    "❌ publishAsync ERROR topic={} key={} eventId={} error={}",
                    eventMetaData.topic, envelope.aggregateId, envelope.eventId, ex.message
                )
                future.completeExceptionally(ex)
            }
        }

        return future
    }

    // ======================================================================
    // BUILD RECORD
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
    private fun Headers.addString(key: String, value: String) {
        add(RecordHeader(key, value.toByteArray(StandardCharsets.UTF_8)))
    }
}

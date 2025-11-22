package com.dogancaglar.paymentservice.port.inbound.consumers.base

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory

abstract class AbstractKafkaConsumer<T : Event> {
    private val logger = LoggerFactory.getLogger(javaClass)

    abstract fun filter(envelope: EventEnvelope<T>): Boolean

    open fun extractEnvelope(record: ConsumerRecord<String, EventEnvelope<T>>): EventEnvelope<T> {
        try {
            return record.value()
        } catch (ex: Exception) {
            logger.error("Failed to deserialize record with key: ${record.key()}", ex)
            throw IllegalStateException("Failed to deserialize record with key: ${record.key()}", ex)
        }
    }

    fun withLogContext(
        envelope: EventEnvelope<T>,
        additionalContext: Map<String, String> = emptyMap(),
        block: () -> Unit
    ) {
        logger.info("Setting log context for event: ${envelope.eventType}, traceId: ${envelope.traceId}, eventId: ${envelope.eventId}")
        EventLogContext.with(envelope, additionalContext, block)
    }
}
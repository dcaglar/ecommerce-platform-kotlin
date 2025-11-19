package com.dogancaglar.paymentservice.port.inbound.consumers.base

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.GenericLogFields
import com.dogancaglar.common.logging.EventLogContext
import org.apache.kafka.clients.consumer.ConsumerRecord

abstract class BaseSingleKafkaConsumer<T : Event> {
    abstract fun filter(envelope: EventEnvelope<T>): Boolean
    abstract fun consume(
        envelope: EventEnvelope<T>,
        record: ConsumerRecord<String, EventEnvelope<T>>
    )

    fun handle(record: ConsumerRecord<String, EventEnvelope<T>>) {
        val envelope = record.value()
        if (filter(envelope)) {
            withLogContext(
                envelope, mapOf(
                    // Add standard fields, can be extended in subclass if needed
                    GenericLogFields.TOPIC_NAME to record.topic(),
                    GenericLogFields.EVENT_ID to envelope.eventId,
                    GenericLogFields.AGGREGATE_ID to envelope.aggregateId,
                    GenericLogFields.TRACE_ID to envelope.traceId,
                    GenericLogFields.EVENT_TYPE to envelope.eventType
                )
            ) {
                consume(envelope, record)
            }
        }
    }

    fun withLogContext(
        envelope: EventEnvelope<T>,
        additionalContext: Map<String, String> = emptyMap(),
        block: () -> Unit
    ) {
        EventLogContext.with(envelope, additionalContext, block)
    }

    protected open fun buildLogContext(
        envelope: EventEnvelope<T>,
        record: ConsumerRecord<String, EventEnvelope<T>>
    ): Map<String, String> {
        // Compose generic + domain-specific context
        return genericLogContext(envelope, record) + domainContext(envelope, record)
    }

    protected open fun genericLogContext(
        envelope: EventEnvelope<T>,
        record: ConsumerRecord<String, EventEnvelope<T>>
    ): Map<String, String> = mapOf(
        GenericLogFields.TOPIC_NAME to record.topic(),
        GenericLogFields.EVENT_ID to envelope.eventId.toString(),
        GenericLogFields.AGGREGATE_ID to envelope.aggregateId,
        GenericLogFields.TRACE_ID to envelope.traceId
        // add more here if you want (partition, offset, etc)
    )

    /** Override in concrete consumer for domain-specific context */
    protected open fun domainContext(
        envelope: EventEnvelope<T>,
        record: ConsumerRecord<String, EventEnvelope<T>>
    ): Map<String, String> = emptyMap()
}
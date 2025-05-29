package com.dogancaglar.common.logging


import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.PublicAggregateEvent
import org.slf4j.MDC

object LogContext {
    fun <T> with(
        envelope: EventEnvelope<T>,
        additionalContext: Map<String, String> = emptyMap(),
        block: () -> Unit
    ) {
        try {
            MDC.put(LogFields.TRACE_ID, envelope.traceId)
            MDC.put(LogFields.EVENT_ID, envelope.eventId.toString())
            MDC.put(LogFields.AGGREGATE_ID, envelope.aggregateId)
            MDC.put(LogFields.EVENT_TYPE, envelope.eventType)
            // 👇 Conditionally inject publicPaymentId
            (envelope.data as? PublicAggregateEvent)?.let {
                MDC.put(LogFields.PUBLIC_ID, it.publicId)
            }
            additionalContext.forEach { (k, v) -> MDC.put(k, v) }
            block()
        } finally {
            MDC.clear()
        }
    }
}
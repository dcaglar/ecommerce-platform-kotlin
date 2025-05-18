package com.dogancaglar.common.logging


import com.dogancaglar.common.event.EventEnvelope
import org.slf4j.MDC

object LogContext {
    fun <T> with(envelope: EventEnvelope<T>, block: () -> Unit) {
        try {
            MDC.put(LogFields.TRACE_ID, envelope.traceId)
            MDC.put(LogFields.EVENT_ID, envelope.eventId.toString())
            MDC.put(LogFields.AGGREGATE_ID, envelope.aggregateId)
            MDC.put(LogFields.EVENT_TYPE, envelope.eventType)
            block()
        } finally {
            MDC.clear()
        }
    }
}
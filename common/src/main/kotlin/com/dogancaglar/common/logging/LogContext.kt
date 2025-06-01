package com.dogancaglar.common.logging


import com.dogancaglar.common.event.EventEnvelope
import org.slf4j.LoggerFactory
import org.slf4j.MDC

object LogContext {
    private val logger = LoggerFactory.getLogger(LogContext::class.java)



    fun <T> with(
        envelope: EventEnvelope<T>,
        additionalContext: Map<String, String> = emptyMap(),
        block: () -> Unit
    ) {
        // capture outer MDC (if any)
        val previous = MDC.getCopyOfContextMap()
        try {
            MDC.put(LogFields.TRACE_ID, envelope.traceId)
            MDC.put(LogFields.EVENT_ID, envelope.eventId.toString())
            MDC.put(LogFields.AGGREGATE_ID, envelope.aggregateId)
            MDC.put(LogFields.EVENT_TYPE, envelope.eventType)
            envelope.parentEventId?.let {
                MDC.put(LogFields.PARENT_EVENT_ID, it.toString())
            }
            additionalContext.forEach(MDC::put)
            block()
        } finally {
            // restore outer context
            if (previous != null) MDC.setContextMap(previous) else MDC.clear()
        }
    }
}
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
        try {
            MDC.put(LogFields.TRACE_ID, envelope.traceId)
            MDC.put(LogFields.EVENT_ID, envelope.eventId.toString())
            MDC.put(LogFields.AGGREGATE_ID, envelope.aggregateId)
            MDC.put(LogFields.EVENT_TYPE, envelope.eventType)
            MDC.put(LogFields.PARENT_EVENT_ID, envelope.parentEventId?.toString() ?: "")
            //MDC.put(LogFields.PUBLIC_PAYMENT_ID, envelope.data?.toString() ?: "")
            MDC.put(LogFields.PUBLIC_PAYMENT_ORDER_ID, envelope.aggregateId?.toString() ?: "")

            additionalContext.forEach { (k, v) -> MDC.put(k, v) }
            block()
        } finally {
            MDC.clear()
        }
    }

    fun <R> withTrace(
        traceId: String,
        contextLabel: String,
        additionalContext: Map<String, String> = emptyMap(),
        block: () -> R
    ): R {
        try {
            MDC.put(LogFields.TRACE_ID, traceId)
            additionalContext.forEach { (k, v) -> MDC.put(k, v) }

            logger.debug("Initialized MDC context [$contextLabel] with traceId=$traceId")

            return block()
        } finally {
            MDC.clear()
        }
    }
}
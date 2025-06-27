package com.dogancaglar.common.logging


import com.dogancaglar.common.event.EventEnvelope
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.*

object LogContext {
    private val logger = LoggerFactory.getLogger(LogContext::class.java)
    fun getTraceId(): String? = MDC.get(GenericLogFields.TRACE_ID)
    fun getEventId(): UUID? = UUID.fromString(MDC.get(GenericLogFields.EVENT_ID))

    fun <T> with(
        envelope: EventEnvelope<T>,
        additionalContext: Map<String, String> = emptyMap(),
        block: () -> Unit
    ) {
        // capture outer MDC (if any)
        val previous = MDC.getCopyOfContextMap()
        try {
            MDC.put(GenericLogFields.TRACE_ID, envelope.traceId)
            MDC.put(GenericLogFields.EVENT_ID, envelope.eventId.toString())
            MDC.put(GenericLogFields.AGGREGATE_ID, envelope.aggregateId)
            MDC.put(GenericLogFields.EVENT_TYPE, envelope.eventType)
            envelope.parentEventId?.let {
                MDC.put(GenericLogFields.PARENT_EVENT_ID, it.toString())
            }
            additionalContext.forEach(MDC::put)
            block()
        } finally {
            // restore outer context
            if (previous != null) MDC.setContextMap(previous) else MDC.clear()
        }
    }


    // New: Fully explicit context map
    fun with(
        context: Map<String, String>,
        block: () -> Unit
    ) {
        val previous = MDC.getCopyOfContextMap()
        try {
            context.forEach(MDC::put)
            block()
        } finally {
            if (previous != null) MDC.setContextMap(previous) else MDC.clear()
        }
    }


    fun withRetryFields(
        retryCount: Int,
        retryReason: String? = null,
        lastErrorMessage: String? = null,
        backOffInMillis: Long,
        block: () -> Unit
    ) {
        val previous = MDC.getCopyOfContextMap()?.let { Collections.unmodifiableMap(it) }
        try {
            MDC.put(GenericLogFields.RETRY_COUNT, retryCount.toString())
            retryReason?.let { MDC.put(GenericLogFields.RETRY_REASON, it) }
            lastErrorMessage?.let { MDC.put(GenericLogFields.RETRY_ERROR_MESSAGE, it) }
            MDC.put(GenericLogFields.RETRY_BACKOFF_MILLIS, backOffInMillis.toString())
            block()
        } finally {
            MDC.setContextMap(previous ?: emptyMap())
        }
    }


}
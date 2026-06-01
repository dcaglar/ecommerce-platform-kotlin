package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelope
import java.time.Duration
import java.util.concurrent.CompletableFuture


interface EventPublisherPort {

    /**
     * Publish a single event synchronously.
     * @return The produced EventEnvelope<T>
     */
    fun <T : Event> publishSync(
        aggregateId: String,
        data: T,
        traceId: String,
        parentEventId: String? = null,
        timeoutSeconds: Long = 5
    ): EventEnvelope<T>

    /**
     * Publish an envelope asynchronously.
     */
    fun <T : Event> publishAsync(
        envelope: EventEnvelope<T>
    ): CompletableFuture<EventEnvelope<T>>
}


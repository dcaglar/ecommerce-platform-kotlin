package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelope
import java.time.Duration


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
     * Publish a batch inside one Kafka transaction (exactly-once).
     */
    fun <T : Event> publishBatchAtomically(
        envelopes: List<EventEnvelope<T>>,
        timeout: Duration = Duration.ofSeconds(30)
    ): Boolean
}


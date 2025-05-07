package com.dogancaglar.common.event

import java.time.Instant

data class EventEnvelope<T>(
    val eventType: String,
    val aggregateId: String,
    val data: T,
    val timestamp: Instant = Instant.now()
) {
    companion object {
        fun <T> wrap(eventType: String, aggregateId: String, data: T): EventEnvelope<T> {
            return EventEnvelope(
                eventType = eventType,
                aggregateId = aggregateId,
                data = data,
                timestamp = Instant.now()
            )
        }
    }
}
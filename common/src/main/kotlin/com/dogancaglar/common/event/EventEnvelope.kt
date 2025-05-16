package com.dogancaglar.common.event

import java.time.Instant
import java.util.UUID

data class EventEnvelope<T>(
    val eventId: UUID = UUID.randomUUID(),  // ← this!
    val eventType: String,
    val aggregateId: String,  // ← this is payment_order_id
    val data: T,
    val timestamp: Instant = Instant.now()
){
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
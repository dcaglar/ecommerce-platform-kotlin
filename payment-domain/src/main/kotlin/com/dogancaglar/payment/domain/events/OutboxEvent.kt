package com.dogancaglar.payment.domain.events

import java.time.Clock
import java.time.LocalDateTime


class OutboxEvent
private constructor(
    val oeid: Long,
    val eventType: String,
    val aggregateId: String,
    val payload: String,
    var status: String,
    val createdAt: LocalDateTime,
) {
    fun markAsProcessing() {
        require(status == "NEW") { "Expected status NEW, was $status" }
        status = "PROCESSING"
    }

    fun markAsSent() {
        require(status == "PROCESSING" || status == "NEW")
        status = "SENT"
    }


    companion object {
        fun createNew(
            oeid: Long,
            eventType: String,
            aggregateId: String,
            payload: String,
            clock: Clock? = Clock.systemUTC()
        ) = OutboxEvent(
            oeid = oeid,
            eventType = eventType,
            aggregateId = aggregateId,
            payload = payload,
            status = "NEW",
            createdAt = LocalDateTime.now(clock)
        )

        fun restore(
            oeid: Long,
            eventType: String,
            aggregateId: String,
            payload: String,
            status: String,
            createdAt: LocalDateTime
        ) = OutboxEvent(oeid, eventType, aggregateId, payload, status, createdAt)
    }
}
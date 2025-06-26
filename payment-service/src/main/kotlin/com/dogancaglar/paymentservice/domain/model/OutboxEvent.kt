package com.dogancaglar.paymentservice.domain.model

import java.time.Clock
import java.time.LocalDateTime
import java.util.*

class OutboxEvent private constructor(
    val eventId: UUID,
    val eventType: String,
    val aggregateId: String,
    val payload: String,
    private var status: String,
    val createdAt: LocalDateTime
) {

    fun markAsProcessing() {
        require(status == "NEW") {
            "OutboxEvent must be NEW to mark as PROCESSING, was $status"
        }
        status = "PROCESSING"
    }

    fun markAsSent() {
        // ↑ was `require(status == "NEW")`
        require(status == "PROCESSING" || status == "NEW") {
            "OutboxEvent must be PROCESSING/NEW to mark as SENT, was $status"
        }
        status = "SENT"
    }

    fun getStatus(): String = status

    companion object {
        fun createNew(
            eventType: String,
            aggregateId: String,
            payload: String,
            createdAt: LocalDateTime = LocalDateTime.now(Clock.systemUTC())
        ): OutboxEvent {
            return OutboxEvent(
                eventId = UUID.randomUUID(),
                eventType = eventType,
                aggregateId = aggregateId,
                payload = payload,
                status = "NEW",
                createdAt = createdAt
            )
        }

        fun restoreFromPersistence(
            eventId: UUID,
            eventType: String,
            aggregateId: String,
            payload: String,
            status: String,
            createdAt: LocalDateTime
        ): OutboxEvent {
            return OutboxEvent(
                eventId = eventId,
                eventType = eventType,
                aggregateId = aggregateId,
                payload = payload,
                status = status,
                createdAt = createdAt
            )
        }
    }
}
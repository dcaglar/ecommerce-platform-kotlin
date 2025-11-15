package com.dogancaglar.paymentservice.domain.model

import java.time.LocalDateTime


/**
 * Represents a durable outbox event entry.
 * Created atomically with domain changes to ensure reliable async publication.
 */class OutboxEvent private constructor(
    val oeid: Long,
    val eventType: String,
    val aggregateId: String,
    val payload: String,
    val status: Status,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {

    /** Domain-safe transitions */
    fun markAsProcessing(): OutboxEvent {
        require(status == Status.NEW) {
            "Invalid transition from $status to ${Status.PROCESSING}"
        }
        return copy(status = Status.PROCESSING)
    }

    fun markAsSent(): OutboxEvent {
        require(status == Status.NEW || status == Status.PROCESSING) {
            "Invalid transition from $status to ${Status.SENT}"
        }
        return copy(status = Status.SENT)
    }

    /** Functional immutability */
    private fun copy(
        status: Status = this.status,
        updatedAt: LocalDateTime = LocalDateTime.now()
    ): OutboxEvent = OutboxEvent(
        oeid = oeid,
        eventType = eventType,
        aggregateId = aggregateId,
        payload = payload,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    /** Marker enum for outbox lifecycle */
    enum class Status { NEW, PROCESSING, SENT }

    companion object {

        /** 🔹 Create brand new event for persistence */
        fun createNew(
            oeid: Long,
            eventType: String,
            aggregateId: String,
            payload: String,
            createdAt: LocalDateTime = LocalDateTime.now()
        ): OutboxEvent = OutboxEvent(
            oeid = oeid,
            eventType = eventType,
            aggregateId = aggregateId,
            payload = payload,
            status = Status.NEW,
            createdAt = createdAt,
            updatedAt = createdAt
        )

        /** 🔹 Rehydrate from persistence row */
        fun rehydrate(
            oeid: Long,
            eventType: String,
            aggregateId: String,
            payload: String,
            status: String,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime
        ): OutboxEvent = OutboxEvent(
            oeid = oeid,
            eventType = eventType,
            aggregateId = aggregateId,
            payload = payload,
            status = Status.valueOf(status.uppercase()), // safe parse
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
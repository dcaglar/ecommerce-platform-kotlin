package com.dogancaglar.paymentservice.domain.event

import java.time.LocalDateTime

class OutboxEvent private constructor(
    val oeid: Long,
    val eventType: String,
    val aggregateId: String,
    val payload: String,
    private var _status: Status,
    val createdAt: LocalDateTime
) {

    /** Public read-only accessor for status */
    val status: Status get() = _status

    fun markAsProcessing() {
        require(_status == Status.NEW) { "Expected status NEW, was $_status" }
        _status = Status.PROCESSING
    }

    fun markAsSent() {
        require(_status == Status.PROCESSING || _status == Status.NEW) {
            "Expected status NEW or PROCESSING, was $_status"
        }
        _status = Status.SENT
    }

    enum class Status { NEW, PROCESSING, SENT }

    companion object {

        /** 🔹 For newly created Outbox events before first persistence */
        fun createNew(
            oeid: Long,
            eventType: String,
            aggregateId: String,
            payload: String,
            createdAt: LocalDateTime
        ): OutboxEvent = OutboxEvent(
            oeid = oeid,
            eventType = eventType,
            aggregateId = aggregateId,
            payload = payload,
            _status = Status.NEW,
            createdAt = createdAt
        )

        /** 🔹 For restoring from persistence (e.g., mapper or database row) */
        fun restore(
            oeid: Long,
            eventType: String,
            aggregateId: String,
            payload: String,
            status: String,
            createdAt: LocalDateTime
        ): OutboxEvent = OutboxEvent(
            oeid = oeid,
            eventType = eventType,
            aggregateId = aggregateId,
            payload = payload,
            _status = Status.valueOf(status),
            createdAt = createdAt
        )
    }
}
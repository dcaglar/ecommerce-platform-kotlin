package com.dogancaglar.infrastructure.persistence.entity

import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class OutboxEventEntity(
    val oeid: Long,
    val eventType: String,
    val aggregateId: String,
    val payload: String,
    var status: String = "NEW",
    val createdAt: LocalDateTime = LocalDateTime.now(UTC)
) {
    constructor(
        oeid: Long,
        eventType: String,
        aggregateId: String,
        payload: String,
        status: String,
        createdAt: java.sql.Timestamp
    ) : this(
        oeid,
        eventType,
        aggregateId,
        payload,
        status,
        createdAt.toInstant().atOffset(UTC).toLocalDateTime()
    )


    /*
     constructor(
        eventId: UUID?,
        eventType: String,
        aggregateId: String,
        payload: String,
        status: String,
        createdAt: java.sql.Timestamp
    ) : this(
        eventId,
        eventType,
        aggregateId,
        payload,
        status,
        createdAt.toInstant().atOffset(UTC).toLocalDateTime()
    )

    constructor(
        eventId: String?,
        eventType: String,
        aggregateId: String,
        payload: String,
        status: String,
        createdAt: java.sql.Timestamp
    ) : this(
        eventId?.let { UUID.fromString(it) },
        eventType,
        aggregateId,
        payload,
        status,
        createdAt.toInstant().atOffset(UTC).toLocalDateTime()
    )
     */

    fun markAsSent() {
        this.status = "SENT"
    }
}
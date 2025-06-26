package com.dogancaglar.paymentservice.adapter.persistence.entity

import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.util.*

class OutboxEventEntity(
    val eventId: UUID? = null,
    val eventType: String,
    val aggregateId: String,
    val payload: String,
    var status: String = "NEW",
    val createdAt: LocalDateTime = LocalDateTime.now(UTC)
) {
    fun markAsSent() {
        this.status = "SENT"
    }
}
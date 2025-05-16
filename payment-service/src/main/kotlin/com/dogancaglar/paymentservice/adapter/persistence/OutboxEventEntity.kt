package com.dogancaglar.paymentservice.adapter.persistence

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "outbox_event")
class OutboxEventEntity(
    @Id
    val eventId: UUID? = null,

    @Column(name = "event_type", nullable = false)
    val eventType: String,

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: String,

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    val payload: String,

    @Column(name = "status", nullable = false)
    var status: String = "NEW",

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun markAsSent() {
        this.status = "SENT"
    }
}
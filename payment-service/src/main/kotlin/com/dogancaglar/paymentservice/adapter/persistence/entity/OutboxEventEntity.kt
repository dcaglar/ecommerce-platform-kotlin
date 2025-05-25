package com.dogancaglar.paymentservice.adapter.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "outbox_event")
class OutboxEventEntity(
    @Id
    @Column(name = "id", columnDefinition = "UUID", nullable = false)
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
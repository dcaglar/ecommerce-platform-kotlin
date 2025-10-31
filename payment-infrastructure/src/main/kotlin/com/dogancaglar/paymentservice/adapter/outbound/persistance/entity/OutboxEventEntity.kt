package com.dogancaglar.paymentservice.adapter.outbound.persistance.entity

import java.time.LocalDateTime

class OutboxEventEntity internal constructor(
    val oeid: Long,
    val eventType: String,
    val aggregateId: String,
    val payload: String,
    var status: String = "NEW",
    val createdAt: LocalDateTime
) {
    fun markAsSent() {
        this.status = "SENT"
    }
}
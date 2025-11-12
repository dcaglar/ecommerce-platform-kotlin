package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import java.time.LocalDateTime

data class OutboxEventEntity(
    val oeid: Long,
    val eventType: String,
    val aggregateId: String,
    val payload: String,
    var status: String = "NEW",
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val claimedAt: LocalDateTime? = null,
    val claimedBy: String? = null
) {
    fun markAsSent() {
        this.status = "SENT"
    }
}
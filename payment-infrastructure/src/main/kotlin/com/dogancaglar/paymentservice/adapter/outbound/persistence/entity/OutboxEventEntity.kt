package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import java.time.Instant

data class OutboxEventEntity(
    val oeid: Long,
    val eventType: String,
    val aggregateId: String,
    val payload: String,
    var status: String = "NEW",
    val createdAt: Instant,
    val updatedAt: Instant,
    val claimedAt: Instant? = null,
    val claimedBy: String? = null
) {
    fun markAsSent() {
        this.status = "SENT"
    }
}
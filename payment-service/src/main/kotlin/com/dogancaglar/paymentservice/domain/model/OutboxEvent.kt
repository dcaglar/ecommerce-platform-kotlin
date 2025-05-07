package com.dogancaglar.paymentservice.domain.model

import java.time.LocalDateTime
import java.util.*

data class OutboxEvent(
    val id: UUID? = null,
    val eventType: String,
    val aggregateId: String,
    val payload: String,
    var status: String = "NEW",
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun markAsSent() {
        this.status = "SENT"
    }
}
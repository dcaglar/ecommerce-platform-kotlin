package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import java.time.Instant
import java.time.LocalDateTime

interface OutboxEventPort {
    fun findByStatus(status: String): List<OutboxEvent>
    fun saveAll(events: List<OutboxEvent>): List<OutboxEvent>
    fun save(event: OutboxEvent): OutboxEvent
    fun countByStatus(status: String): Long
    fun findByStatusWithLimit(status: String, limit: Int): List<OutboxEvent>
    fun findBatchAfter(cursor: LocalDateTime, batch: Int): List<OutboxEvent>
    fun read(): Instant
    fun write(ts: Instant)
}

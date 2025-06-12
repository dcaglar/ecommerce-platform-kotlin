package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.domain.model.OutboxEvent

interface OutboxEventPort {
    fun findByStatus(status: String): List<OutboxEvent>
    fun saveAll(events: List<OutboxEvent>): List<OutboxEvent>
    fun save(event: OutboxEvent): OutboxEvent
    fun countByStatus(status: String): Long
}




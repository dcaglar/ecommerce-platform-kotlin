package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.domain.model.OutboxEvent

interface OutboxEventRepository {
    fun findByStatus(status: String): List<OutboxEvent>
    fun saveAll(orders: List<OutboxEvent>)
    fun save(outboxEvent: OutboxEvent)

}

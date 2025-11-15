package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.OutboxEvent


interface OutboxEventRepository {
    fun findByStatus(status: String): List<OutboxEvent>
    fun saveAll(events: List<OutboxEvent>): List<OutboxEvent>
    fun save(event: OutboxEvent): OutboxEvent
    fun updateAll(events: List<OutboxEvent>)
    fun countByStatus(status: String): Long
    fun findBatchForDispatch(batchSize: Int,workerId: String): List<OutboxEvent>
    fun reclaimStuckClaims(olderThanSeconds: Int): Int
    fun unclaimSpecific(workerId: String, oeids: List<Long>): Int
    }
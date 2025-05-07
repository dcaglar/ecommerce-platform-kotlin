package com.dogancaglar.paymentservice.adapter.persistance

import java.util.*
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SpringDataOutboxEventJpaRepository : JpaRepository<OutboxEventEntity, UUID> {
    fun findByStatus(status: String): List<OutboxEventEntity>
}
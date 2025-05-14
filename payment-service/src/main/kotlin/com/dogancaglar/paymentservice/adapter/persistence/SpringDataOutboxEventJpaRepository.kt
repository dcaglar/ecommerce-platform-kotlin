package com.dogancaglar.paymentservice.adapter.persistence

import java.util.*
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataOutboxEventJpaRepository : JpaRepository<OutboxEventEntity, UUID> {
    fun findByStatus(status: String): List<OutboxEventEntity>
}
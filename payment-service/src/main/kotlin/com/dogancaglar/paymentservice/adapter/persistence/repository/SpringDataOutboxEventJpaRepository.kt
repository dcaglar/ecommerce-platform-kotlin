package com.dogancaglar.paymentservice.adapter.persistence.repository

import com.dogancaglar.paymentservice.adapter.persistence.entity.OutboxEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SpringDataOutboxEventJpaRepository : JpaRepository<OutboxEventEntity, UUID> {
    fun findByStatus(status: String): List<OutboxEventEntity>
}
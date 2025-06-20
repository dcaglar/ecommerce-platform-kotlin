package com.dogancaglar.paymentservice.adapter.persistence.repository

import com.dogancaglar.paymentservice.adapter.persistence.entity.OutboxEventEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface SpringDataOutboxEventJpaRepository : JpaRepository<OutboxEventEntity, UUID> {
    fun findByStatus(status: String): List<OutboxEventEntity>
    fun countByStatus(status: String): Long
    fun findByStatus(status: String, pageable: Pageable): List<OutboxEventEntity>
    fun findByStatusOrderByCreatedAtAsc(status: String, pageable: Pageable): List<OutboxEventEntity>
    fun deleteByStatus(status: String): Long


    // OutboxEventRepository.kt
    @Query(
        """
    SELECT * FROM outbox_event
    WHERE status = :status
    ORDER BY created_at
    FOR UPDATE SKIP LOCKED
    LIMIT :batchSize
    """,
        nativeQuery = true
    )
    fun findBatchForDispatch(
        @Param("status") status: String,
        @Param("batchSize") batchSize: Int
    ): List<OutboxEventEntity>
}
package com.dogancaglar.infrastructure.persistence.repository

import com.dogancaglar.infrastructure.persistence.entity.OutboxEventEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface OutboxEventMapper {
    fun findByStatus(status: String): List<OutboxEventEntity>
    fun countByStatus(status: String): Long
    fun findByStatusWithLimit(status: String, limit: Int): List<OutboxEventEntity>
    fun findByStatusOrderByCreatedAtAsc(status: String, limit: Int): List<OutboxEventEntity>
    fun deleteByStatus(status: String): Int

    // Removed @Select annotation to avoid duplicate mapping with XML
    fun findBatchForDispatch(
        @Param("batchSize") batchSize: Int
    ): List<OutboxEventEntity>

    fun batchInsert(events: List<OutboxEventEntity>): Int
    fun batchUpsert(events: List<OutboxEventEntity>): Int
    fun insert(event: OutboxEventEntity): Int
}
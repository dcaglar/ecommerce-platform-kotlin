package com.dogancaglar.paymentservice.adapter.outbound.persistance


import com.dogancaglar.infrastructure.persistence.entity.OutboxEventEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface OutboxEventMapper {
    fun findByStatus(status: String): List<OutboxEventEntity>
    fun countByStatus(status: String): Long

    // Removed @Select annotation to avoid duplicate mapping with XML
    fun findBatchForDispatch(
        @Param("batchSize") batchSize: Int
    ): List<OutboxEventEntity>

    fun batchUpsert(events: List<OutboxEventEntity>): Int
    fun insert(event: OutboxEventEntity): Int
}